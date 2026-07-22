#!/usr/bin/env python3
"""
Import Shopee scraped data into ecommerce-platform database.
Reads from scripts/shopee-crawler/output/shopee_scraped_products.csv
"""

import csv
import json
import mysql.connector
from mysql.connector import Error
from datetime import datetime
from urllib.parse import urlparse
from urllib.request import Request, urlopen

# Database configuration
DB_CONFIG = {
    'host': 'localhost',
    'port': 3306,
    'database': 'ecommerce_platform',
    'user': 'root',
    'password': 'password',
    'charset': 'utf8mb4',
    'collation': 'utf8mb4_unicode_ci',
    'use_unicode': True,
}

REQUIRE_PRODUCT_IMAGE = True
MAX_IMAGE_BYTES = 2 * 1024 * 1024
ALLOWED_IMAGE_HOST_SUFFIX = '.susercontent.com'

# Default password hash for new sellers (password: password123)
DEFAULT_PASSWORD_HASH = '$2b$12$OWoy989wBVvz1dSrd.0KH.nyhOeBN8Z23R4GC8apNC0sQOPqmUdzm'

# Category mapping from Shopee categories to our categories
CATEGORY_MAP = {
    '': 'General',
    'towel': 'Towels and Linens',
    'khăn': 'Towels and Linens',
    'phone case': 'Phone Accessories',
    'ốp lưng': 'Phone Accessories',
    'case': 'Phone Accessories',
    'shoe': 'Footwear',
    'dép': 'Footwear',
    'giày': 'Footwear',
    'baby': 'Baby & Kids',
    'bé': 'Baby & Kids',
    'fashion': 'Fashion',
    'thời trang': 'Fashion',
    'home': 'Home & Living',
    'đời sống': 'Home & Living',
}

def parse_price(price_str):
    """Parse price string to float."""
    if not price_str or price_str.strip() == '':
        return 0.0
    try:
        cleaned = price_str.replace(',', '').strip()
        return float(cleaned)
    except:
        return 0.0

def parse_discount(discount_str):
    """Parse discount percentage."""
    if not discount_str or discount_str.strip() == '':
        return 0
    try:
        cleaned = discount_str.replace('%', '').replace('-', '').strip()
        return int(float(cleaned))
    except:
        return 0

def parse_rating(rating_str):
    """Parse rating to float."""
    if not rating_str or rating_str.strip() == '':
        return 0.0
    try:
        return float(rating_str.strip())
    except:
        return 0.0

def parse_sold_count(sold_str):
    """Parse sold count string like '700k+', '10k+', '50k+' to integer."""
    if not sold_str or sold_str.strip() == '' or sold_str.strip().lower() == 'n/a':
        return 0
    try:
        cleaned = sold_str.strip().lower().replace('+', '').replace(',', '')
        if 'k' in cleaned:
            return int(float(cleaned.replace('k', '')) * 1000)
        return int(float(cleaned))
    except:
        return 0

def download_product_image(image_url):
    """Download a Shopee product image for storing in the product photo blob."""
    if not image_url or image_url.strip() == '':
        return None

    parsed = urlparse(image_url.strip())
    if parsed.scheme != 'https' or not parsed.hostname or not parsed.hostname.endswith(ALLOWED_IMAGE_HOST_SUFFIX):
        return None

    try:
        request = Request(
            image_url.strip(),
            headers={
                'User-Agent': 'Mozilla/5.0',
                'Accept': 'image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8',
            },
        )
        with urlopen(request, timeout=15) as response:
            content_type = response.headers.get('Content-Type', '')
            if not content_type.startswith('image/'):
                return None

            image = response.read(MAX_IMAGE_BYTES + 1)
            if len(image) > MAX_IMAGE_BYTES:
                return None
            return image
    except Exception as e:
        print(f"Could not download image '{image_url}': {e}")
        return None
def get_category_name(shop_name, product_name):
    """Determine category based on shop/product names."""
    combined = (shop_name + ' ' + product_name).lower()
    for keyword, category in CATEGORY_MAP.items():
        if keyword and keyword in combined:
            return category
    return 'General'

def get_or_create_category(cursor, category_name):
    """Get existing category or create new one."""
    cursor.execute("SELECT id FROM categories WHERE name = %s", (category_name,))
    result = cursor.fetchone()
    if result:
        return result[0]

    cursor.execute("INSERT INTO categories (name, created_at, created_by) VALUES (%s, %s, %s)",
                   (category_name, datetime.now(), 'import_script'))
    return cursor.lastrowid

def get_or_create_seller(cursor, shop_data):
    """Get existing seller (user) or create new one."""
    username = shop_data['shop_username']
    cursor.execute("SELECT id FROM users WHERE username = %s", (username,))
    result = cursor.fetchone()
    if result:
        user_id = result[0]
        cursor.execute("""
            UPDATE users
            SET first_name = %s, email = %s, updated_at = %s, updated_by = %s
            WHERE id = %s
        """, (
            shop_data['shop_name'],
            f"{username}@shopee.local",
            datetime.now(),
            'import_script',
            user_id,
        ))
        ensure_user_role(cursor, user_id, 'ROLE_SELLER')
        ensure_user_role(cursor, user_id, 'ROLE_PARTNER')
        return user_id

    # Create new seller
    # First create a cart
    cursor.execute("INSERT INTO carts (created_at, created_by) VALUES (%s, %s)",
                   (datetime.now(), 'import_script'))
    cart_id = cursor.lastrowid

    cursor.execute("""
        INSERT INTO users (first_name, last_name, email, username, password, 
                          cart_id, created_at, created_by)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
    """, (
        shop_data['shop_name'],
        '',
        f"{username}@shopee.local",
        username,
        DEFAULT_PASSWORD_HASH,
        cart_id,
        datetime.now(),
        'import_script'
    ))
    user_id = cursor.lastrowid

    # Assign SELLER role
    ensure_user_role(cursor, user_id, 'ROLE_SELLER')
    ensure_user_role(cursor, user_id, 'ROLE_PARTNER')

    return user_id

def get_role_id(cursor, role_name):
    """Find a role by the app's ROLE_* convention, with legacy fallback."""
    candidates = [role_name]
    if role_name.startswith('ROLE_'):
        candidates.append(role_name.replace('ROLE_', '', 1))

    for candidate in candidates:
        cursor.execute("SELECT id FROM roles WHERE name = %s", (candidate,))
        result = cursor.fetchone()
        if result:
            return result[0]

    raise RuntimeError(f"Missing role: {role_name}")

def ensure_user_role(cursor, user_id, role_name):
    """Assign a role to a user if it is not already present."""
    role_id = get_role_id(cursor, role_name)
    cursor.execute("""
        INSERT IGNORE INTO user_roles (user_id, role_id)
        VALUES (%s, %s)
    """, (user_id, role_id))

def get_or_create_partner(cursor, shop_data, user_id):
    """Get or create an approved marketplace partner for the Shopee shop."""
    username = shop_data['shop_username']
    shop_name = shop_data['shop_name'] or username
    address = shop_data.get('shop_location') or ''

    cursor.execute("SELECT id FROM partners WHERE code = %s", (username,))
    result = cursor.fetchone()
    if result:
        partner_id = result[0]
        cursor.execute("""
            UPDATE partners
            SET name = %s,
                business_name = %s,
                email = %s,
                address = %s,
                status = 'APPROVED',
                applicant_user_id = %s,
                approved_at = COALESCE(approved_at, %s),
                updated_at = %s,
                version = version + 1
            WHERE id = %s
        """, (
            shop_name,
            shop_name,
            f"{username}@shopee.local",
            address,
            user_id,
            datetime.now(),
            datetime.now(),
            partner_id,
        ))
    else:
        cursor.execute("""
            INSERT INTO partners (code, name, business_name, email, phone, address, status,
                                  applicant_user_id, approved_at, tax_code,
                                  created_at, updated_at, version)
            VALUES (%s, %s, %s, %s, %s, %s, 'APPROVED', %s, %s, %s, %s, %s, 0)
        """, (
            username,
            shop_name,
            shop_name,
            f"{username}@shopee.local",
            None,
            address,
            user_id,
            datetime.now(),
            None,
            datetime.now(),
            datetime.now(),
        ))
        partner_id = cursor.lastrowid

    cursor.execute("""
        INSERT IGNORE INTO partner_members (partner_id, user_id, role, status, joined_at,
                                            created_at, updated_at, version)
        VALUES (%s, %s, 'OWNER', 'ACTIVE', %s, %s, %s, 0)
    """, (partner_id, user_id, datetime.now(), datetime.now(), datetime.now()))

    return partner_id

def ensure_partner_offer(cursor, partner_id, product_id, shop_username, price, sold):
    """Create or refresh the approved partner offer for an imported product."""
    sku = f"{shop_username.upper().replace('.', '-').replace('_', '-')}-{product_id}"
    on_hand_quantity = max(int(sold), 100)

    cursor.execute("SELECT id FROM partner_offers WHERE product_id = %s", (product_id,))
    result = cursor.fetchone()
    if result:
        cursor.execute("""
            UPDATE partner_offers
            SET partner_id = %s,
                partner_sku = %s,
                price = %s,
                currency = 'VND',
                on_hand_quantity = %s,
                status = 'APPROVED',
                approved_at = COALESCE(approved_at, %s),
                updated_at = %s,
                version = version + 1
            WHERE id = %s
        """, (partner_id, sku, price, on_hand_quantity, datetime.now(), datetime.now(), result[0]))
        return

    cursor.execute("""
        INSERT INTO partner_offers (partner_id, product_id, partner_sku, price, currency,
                                    on_hand_quantity, reserved_quantity, status,
                                    submitted_at, approved_at, created_at, updated_at, version)
        VALUES (%s, %s, %s, %s, 'VND', %s, 0, 'APPROVED', %s, %s, %s, %s, 0)
    """, (
        partner_id,
        product_id,
        sku,
        price,
        on_hand_quantity,
        datetime.now(),
        datetime.now(),
        datetime.now(),
        datetime.now(),
    ))

def import_products():
    csv_path = '/home/hoangnh/ecommerce-platform/scripts/shopee-crawler/output/shopee_scraped_products.csv'
    json_path = '/home/hoangnh/ecommerce-platform/scripts/shopee-crawler/output/shopee_scraped_data.json'

    imported = 0
    updated = 0
    skipped = 0
    errors = 0

    try:
        conn = mysql.connector.connect(**DB_CONFIG)
        cursor = conn.cursor()

        print("Reading CSV file...")
        with open(csv_path, 'r', encoding='utf-8') as f:
            reader = csv.DictReader(f)
            rows = list(reader)

        shop_metadata = {}
        try:
            with open(json_path, 'r', encoding='utf-8') as f:
                scraped_data = json.load(f)
            shop_metadata = {
                shop.get('username'): shop
                for shop in scraped_data.get('shops', [])
                if shop.get('username')
            }
        except FileNotFoundError:
            print("JSON shop metadata file not found. Continuing with CSV data only.")

        print(f"Found {len(rows)} products to import")

        for row in rows:
            try:
                # Skip header-like rows
                if row.get('shop_product_rank', '').strip() == 'shop_product_rank':
                    continue

                shop_username = row['shop_username'].strip()
                if not shop_username:
                    print(f"Skipping row - no shop username: {row.get('product_name', 'unknown')}")
                    skipped += 1
                    continue

                # Prepare shop data
                shop_data = {
                    'shop_id': row['shop_id'].strip(),
                    'shop_username': shop_username,
                    'shop_name': row['shop_name'].strip() if row['shop_name'] else shop_username,
                    'shop_location': shop_metadata.get(shop_username, {}).get('shop_location', ''),
                }

                # Get or create seller
                user_id = get_or_create_seller(cursor, shop_data)
                partner_id = get_or_create_partner(cursor, shop_data, user_id)

                # Determine category
                category_name = get_category_name(
                    shop_data['shop_name'],
                    row['product_name']
                )
                category_id = get_or_create_category(cursor, category_name)

                # Parse product data
                name = row['product_name'].strip() if row['product_name'] else 'Unknown Product'
                if not name or name == 'Unknown Product':
                    skipped += 1
                    continue

                price = parse_price(row['price_vnd'])
                if price <= 0:
                    price = 1000  # Minimum price

                photo = download_product_image(row.get('image_url', ''))
                if REQUIRE_PRODUCT_IMAGE and not photo:
                    print(f"Skipping row - no usable image: {name}")
                    skipped += 1
                    continue

                original_price = parse_price(row['original_price_vnd']) if row['original_price_vnd'] else price
                if original_price <= 0:
                    original_price = price

                discount = parse_discount(row['discount_percent']) if row['discount_percent'] else 0
                rating = parse_rating(row['rating']) if row['rating'] else 0.0
                sold = parse_sold_count(row['sold']) if row['sold'] else 0

                # Build description
                description_parts = []
                if row['note']:
                    description_parts.append(f"Note: {row['note']}")
                if row['data_source']:
                    description_parts.append(f"Source: {row['data_source']}")
                if row['category']:
                    description_parts.append(f"Category: {row['category']}")
                if row['source_url']:
                    description_parts.append(f"Shop: {row['source_url']}")
                if row['product_url']:
                    description_parts.append(f"Product URL: {row['product_url']}")

                description = ' | '.join(description_parts) if description_parts else f"Imported from Shopee shop {shop_username}"

                cursor.execute("""
                    SELECT id FROM products
                    WHERE user_id = %s AND name = %s
                    ORDER BY id
                    LIMIT 1
                """, (user_id, name))
                existing_product = cursor.fetchone()

                if existing_product:
                    product_id = existing_product[0]
                    cursor.execute("""
                        UPDATE products
                        SET price = %s,
                            description = %s,
                            on_hand_quantity = %s,
                            active = true,
                            updated_at = %s,
                            updated_by = %s,
                            photo = %s,
                            version = version + 1
                        WHERE id = %s
                    """, (
                        price,
                        description,
                        sold,
                        datetime.now(),
                        'import_script',
                        photo,
                        product_id,
                    ))
                    updated += 1
                else:
                    cursor.execute("""
                        INSERT INTO products (name, price, description, on_hand_quantity, reserved_quantity,
                                             active, user_id, version, created_at, created_by, photo)
                        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                    """, (
                        name, price, description, sold, 0, True, user_id, 0, datetime.now(), 'import_script', photo
                    ))
                    product_id = cursor.lastrowid
                    imported += 1

                cursor.execute("""
                    INSERT IGNORE INTO products_categories (product_id, categories_id)
                    VALUES (%s, %s)
                """, (product_id, category_id))

                ensure_partner_offer(cursor, partner_id, product_id, shop_username, price, sold)

                if (imported + updated) % 20 == 0:
                    conn.commit()
                    print(f"Processed {imported + updated} products...")

            except Exception as e:
                errors += 1
                product_name = row.get('product_name', 'unknown')
                print(f"Error importing product '{product_name}': {e}")
                conn.rollback()

        conn.commit()
        print(f"\n=== Import Complete ===")
        print(f"Successfully imported: {imported} products")
        print(f"Updated: {updated} products")
        print(f"Skipped: {skipped}")
        print(f"Errors: {errors}")

    except Error as e:
        print(f"Database error: {e}")
        conn.rollback()
    except Exception as e:
        print(f"Unexpected error: {e}")
    finally:
        if 'cursor' in locals():
            cursor.close()
        if 'conn' in locals() and conn.is_connected():
            conn.close()

if __name__ == '__main__':
    print("Starting Shopee data import...")
    import_products()
    print("Done!")
