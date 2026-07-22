import pymysql
import json
import csv
import os
from datetime import datetime

DB_HOST = os.environ.get('DB_HOST', 'localhost')
DB_PORT = int(os.environ.get('DB_PORT', '3306'))
DB_USER = os.environ.get('DB_USER', 'root')
DB_PASS = os.environ.get('DB_PASSWORD', 'password')
DB_NAME = os.environ.get('DB_NAME', 'ecommerce_platform')

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
OUTPUT_DIR = os.path.join(BASE_DIR, 'shopee-crawler', 'output')

conn = pymysql.connect(host=DB_HOST, port=DB_PORT, user=DB_USER, password=DB_PASS, database=DB_NAME)
cur = conn.cursor()

print("Đọc dữ liệu shop...")
with open(os.path.join(OUTPUT_DIR, 'lotustowel_shop.json'), 'r', encoding='utf-8') as f:
    shop = json.load(f)

print(f"  Shop: {shop['name']} (username: {shop['username']})")

print("Đọc dữ liệu sản phẩm...")
products = []
with open(os.path.join(OUTPUT_DIR, 'lotustowel_products.csv'), 'r', encoding='utf-8') as f:
    reader = csv.DictReader(f)
    for row in reader:
        products.append(row)

print(f"  {len(products)} sản phẩm")

USER_ID = 1003  
LOTUSTOWEL_USER_ID = 1003

cur.execute("SELECT id FROM categories WHERE name = 'Khăn & Đồ dùng phòng tắm'")
row = cur.fetchone()
if row:
    category_id = row[0]
    print(f"  Danh mục đã tồn tại: id={category_id}")
else:
    cur.execute("""
        INSERT INTO categories (name, created_at, updated_at, created_by, updated_by)
        VALUES (%s, NOW(), NOW(), %s, %s)
    """, ('Khăn & Đồ dùng phòng tắm', 'import', 'import'))
    category_id = cur.lastrowid
    print(f"  Đã tạo danh mục: id={category_id}")

cur.execute("SELECT id FROM partners WHERE code = 'LOTUSTOWEL'")
row = cur.fetchone()
if row:
    partner_id = row[0]
    print(f"  Partner đã tồn tại: id={partner_id}")
else:
    cur.execute("""
        INSERT INTO partners (code, name, business_name, email, phone, address, status,
                              applicant_user_id, approved_at, tax_code,
                              created_at, updated_at, version)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), %s, NOW(), NOW(), 0)
    """, (
        'LOTUSTOWEL',
        shop['name'],
        shop['name'],
        f"{shop['username']}@shopee.vn",
        None,
        shop.get('shop_location', ''),
        'APPROVED',
        LOTUSTOWEL_USER_ID,
        None,
    ))
    partner_id = cur.lastrowid
    print(f"  Đã tạo partner: id={partner_id}")

    cur.execute("""
        INSERT INTO partner_members (partner_id, user_id, role, status, joined_at,
                                     created_at, updated_at, version)
        VALUES (%s, %s, %s, %s, NOW(), NOW(), NOW(), 0)
    """, (partner_id, LOTUSTOWEL_USER_ID, 'OWNER', 'ACTIVE'))
    print(f"  Đã thêm owner vào partner_members")

print(f"\nImport {len(products)} sản phẩm...")
inserted = 0
for p in products:
    name = p['ProductName'].strip()
    price_str = p['PriceVND'].strip().replace(',', '')
    price = float(price_str) if price_str else 0
    note = p.get('Note', '').strip()

    desc = f"Shopee shop {shop['name']}"
    if note:
        desc = note

    cur.execute("""
        INSERT INTO products (name, price, description, on_hand_quantity, reserved_quantity,
                              active, version, created_at, updated_at, created_by, updated_by, user_id)
        VALUES (%s, %s, %s, %s, %s, %s, %s, NOW(), NOW(), %s, %s, %s)
    """, (name, price, desc, 100, 0, True, 0, 'import', 'import', LOTUSTOWEL_USER_ID))
    product_id = cur.lastrowid

    cur.execute("INSERT INTO products_categories (product_id, categories_id) VALUES (%s, %s)",
                (product_id, category_id))

    sku = f"LOTUS-{product_id:04d}"
    cur.execute("""
        INSERT INTO partner_offers (partner_id, product_id, partner_sku, price, currency,
                                    on_hand_quantity, reserved_quantity, status,
                                    approved_at, created_at, updated_at, version)
        VALUES (%s, %s, %s, %s, %s, %s, %s, %s, NOW(), NOW(), NOW(), 0)
    """, (partner_id, product_id, sku, price, 'VND', 100, 0, 'APPROVED'))

    inserted += 1
    if inserted % 5 == 0:
        print(f"  ... {inserted}/{len(products)}")

conn.commit()

print(f"\nKiểm tra dữ liệu:")
cur.execute("SELECT COUNT(*) FROM products WHERE user_id = %s", (LOTUSTOWEL_USER_ID,))
print(f"  Sản phẩm: {cur.fetchone()[0]}")

cur.execute("SELECT COUNT(*) FROM partner_offers WHERE partner_id = %s", (partner_id,))
print(f"  Partner offers: {cur.fetchone()[0]}")

cur.execute("SELECT COUNT(*) FROM products_categories WHERE categories_id = %s", (category_id,))
print(f"  Product-category mappings: {cur.fetchone()[0]}")

cur.execute("SELECT COUNT(*) FROM partners WHERE code = 'LOTUSTOWEL'")
print(f"  Partners: {cur.fetchone()[0]}")

cur.close()
conn.close()
print("\nImport hoàn tất!")
