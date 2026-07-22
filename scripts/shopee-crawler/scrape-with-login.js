import { chromium } from 'playwright';
import fs from 'fs';
import os from 'os';
import path from 'path';

const OUTPUT_DIR = path.resolve('output');
const CSV_PATH = path.join(OUTPUT_DIR, 'shopee_scraped_products.csv');
const JSON_PATH = path.join(OUTPUT_DIR, 'shopee_scraped_data.json');
const PROFILE_DIR = process.env.SHOPEE_PROFILE_DIR
  || path.join(os.homedir(), '.cache', 'shopee-crawler-profile');
const PRODUCTS_PER_SHOP = Number.parseInt(process.env.PRODUCTS_PER_SHOP || '20', 10);
const REQUIRE_IMAGES = process.env.REQUIRE_IMAGES !== 'false';

const SHOPS = [
  { username: 'lotustowel', shopId: '233958535', url: 'https://shopee.vn/lotustowel' },
  { username: 'phukienkoreacase', shopId: '89827191', url: 'https://shopee.vn/phukienkoreacase' },
  { username: 'nestyofficialstore', shopId: '1113586002', url: 'https://shopee.vn/nestyofficialstore' },
  { username: 'coolcrew.stu', shopId: '1388112438', url: 'https://shopee.vn/coolcrew.stu' },
  { username: 'topick_global', shopId: '196261835', url: 'https://shopee.vn/topick_global' },
  { username: 'socialbean', shopId: '1360341342', url: 'https://shopee.vn/socialbean' },
];

fs.mkdirSync(OUTPUT_DIR, { recursive: true });

function toVnd(value) {
  return Number.isFinite(value) && value > 0 ? value / 100000 : null;
}

function imageUrl(imageId) {
  if (!imageId) return '';
  if (String(imageId).startsWith('http')) return imageId;
  return `https://down-vn.img.susercontent.com/file/${imageId}`;
}

function normalizeItem(raw, shop, sourceUrl) {
  const item = raw?.item_basic || raw?.item || raw;
  if (!item?.itemid || !item?.shopid) return null;

  const images = Array.isArray(item.images) ? item.images : [];
  const mainImage = imageUrl(item.image || images[0]);
  if (REQUIRE_IMAGES && !mainImage) return null;

  return {
    shop_product_rank: null,
    source_url: sourceUrl,
    category_id: '',
    shop_id: String(item.shopid || shop.shopId),
    shop_username: shop.username,
    shop_name: shop.name || '',
    requested_item_id: '',
    resolved_item_id: String(item.itemid),
    product_name: item.name || '',
    category: '',
    price_vnd: toVnd(item.price || item.price_min),
    original_price_vnd: toVnd(item.price_before_discount),
    discount_percent: item.raw_discount || item.discount || null,
    rating: item.item_rating?.rating_star || item.rating_star || null,
    sold: item.historical_sold || item.sold || null,
    image_url: mainImage,
    product_url: `https://shopee.vn/-i.${item.shopid || shop.shopId}.${item.itemid}`,
    data_source: 'authenticated_browser_api',
    note: '',
    scraped_at: new Date().toISOString(),
  };
}

function mergeProducts(target, incoming) {
  for (const product of incoming) {
    if (!product?.resolved_item_id) continue;
    const key = `${product.shop_id}:${product.resolved_item_id}`;
    if (!target.has(key)) target.set(key, product);
  }
}

async function autoScroll(page, rounds = 10) {
  for (let i = 0; i < rounds; i += 1) {
    await page.mouse.wheel(0, 1200);
    await page.waitForTimeout(900);
  }
}

async function collectFromShopPage(context, shop) {
  const products = new Map();
  const page = await context.newPage();

  page.on('response', async (response) => {
    const url = response.url();
    if (!/api\/v4\/(shop\/search_items|shop\/rcmd_items|search\/search_items|shop\/get_shop_seo)/.test(url)) {
      return;
    }

    try {
      const json = await response.json();
      const rawItems = [
        ...(Array.isArray(json.items) ? json.items : []),
        ...(Array.isArray(json.data?.items) ? json.data.items : []),
        ...(Array.isArray(json.data?.item_cards) ? json.data.item_cards : []),
      ];
      const normalized = rawItems
        .map((item) => normalizeItem(item, shop, url))
        .filter(Boolean);
      mergeProducts(products, normalized);
    } catch {
      // Shopee sometimes returns an anti-bot JSON shape or an empty body. Ignore and keep browsing.
    }
  });

  console.log(`\n[shop] ${shop.username}`);
  await page.goto(shop.url, { waitUntil: 'domcontentloaded', timeout: 90000 });
  await page.waitForTimeout(5000);

  const loginText = await page.locator('text=/Đăng nhập|Log in/i').count();
  if (loginText) {
    console.log('[login] Nếu Chrome đang yêu cầu đăng nhập, hãy đăng nhập Shopee trong cửa sổ vừa mở.');
    console.log('[login] Script sẽ chờ tối đa 120 giây rồi tiếp tục.');
    await page.waitForTimeout(120000);
  }

  await autoScroll(page, 14);

  const sortUrls = [
    `${shop.url}?sortBy=pop`,
    `${shop.url}?sortBy=sales`,
    `${shop.url}?sortBy=ctime`,
  ];

  for (const url of sortUrls) {
    if (products.size >= PRODUCTS_PER_SHOP) break;
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 90000 });
    await page.waitForTimeout(2500);
    await autoScroll(page, 10);
  }

  await page.close();

  return [...products.values()]
    .slice(0, PRODUCTS_PER_SHOP)
    .map((product, index) => ({ ...product, shop_product_rank: index + 1 }));
}

async function getShopDetails() {
  const shops = [];
  for (const shop of SHOPS) {
    const response = await fetch(
      `https://shopee.vn/api/v4/shop/get_shop_detail?username=${encodeURIComponent(shop.username)}`,
      {
        headers: {
          accept: 'application/json, text/plain, */*',
          'user-agent': 'Mozilla/5.0',
        },
      },
    );
    const json = await response.json();
    const data = json.data || {};
    const account = data.account || {};
    shops.push({
      shop_id: String(data.shopid || shop.shopId),
      user_id: data.userid != null ? String(data.userid) : null,
      username: shop.username,
      shop_name: account.username || data.name || shop.username,
      display_name: data.name || account.username || shop.username,
      item_count: data.item_count ?? null,
      rating_star: data.rating_star ?? null,
      follower_count: data.follower_count ?? null,
      response_rate: data.response_rate ?? null,
      response_time: data.response_time ?? null,
      shop_location: data.shop_location || null,
      description: data.description || null,
      is_official_shop: Boolean(data.is_official_shop),
      is_shopee_verified: Boolean(data.is_shopee_verified),
      is_preferred_plus_seller: Boolean(data.is_preferred_plus_seller),
      cover_image_url: imageUrl(data.cover),
      avatar_url: imageUrl(account.portrait),
      source_urls: [shop.url],
      scraped_at: new Date().toISOString(),
    });
    shop.name = data.name || account.username || shop.username;
  }
  return shops;
}

function csvEscape(value) {
  if (value === null || value === undefined) return '';
  const text = String(value);
  return /[",\n\r]/.test(text) ? `"${text.replace(/"/g, '""')}"` : text;
}

function writeOutputs(shops, products) {
  const scrapedAt = new Date().toISOString();
  const rowsPerShop = {};
  for (const shop of SHOPS) {
    rowsPerShop[shop.username] = products.filter((product) => product.shop_username === shop.username).length;
  }

  const output = {
    scraped_at: scrapedAt,
    source: 'shopee.vn authenticated browser session',
    summary: {
      target_products_per_shop: PRODUCTS_PER_SHOP,
      require_images: REQUIRE_IMAGES,
      unique_shops: shops.length,
      product_rows: products.length,
      rows_per_shop: rowsPerShop,
      final_files: ['shopee_scraped_products.csv', 'shopee_scraped_data.json'],
    },
    shops,
    products,
  };

  const columns = [
    'shop_product_rank',
    'source_url',
    'category_id',
    'shop_id',
    'shop_username',
    'shop_name',
    'requested_item_id',
    'resolved_item_id',
    'product_name',
    'category',
    'price_vnd',
    'original_price_vnd',
    'discount_percent',
    'rating',
    'sold',
    'image_url',
    'product_url',
    'data_source',
    'note',
    'scraped_at',
  ];
  const csv = [
    columns.join(','),
    ...products.map((row) => columns.map((column) => csvEscape(row[column])).join(',')),
  ].join('\n') + '\n';

  fs.writeFileSync(CSV_PATH, csv, 'utf8');
  fs.writeFileSync(JSON_PATH, JSON.stringify(output, null, 2), 'utf8');

  for (const entry of fs.readdirSync(OUTPUT_DIR)) {
    const fullPath = path.join(OUTPUT_DIR, entry);
    if (fullPath !== CSV_PATH && fullPath !== JSON_PATH && fs.statSync(fullPath).isFile()) {
      fs.unlinkSync(fullPath);
    }
  }
}

const browser = await chromium.launchPersistentContext(PROFILE_DIR, {
  headless: false,
  executablePath: process.env.CHROME_PATH || '/usr/bin/google-chrome',
  args: [
    '--disable-blink-features=AutomationControlled',
    '--disable-dev-shm-usage',
    '--no-sandbox',
  ],
  locale: 'vi-VN',
  timezoneId: 'Asia/Ho_Chi_Minh',
  viewport: { width: 1365, height: 950 },
});

try {
  const shops = await getShopDetails();
  const products = [];
  for (const shop of SHOPS) {
    const shopProducts = await collectFromShopPage(browser, shop);
    products.push(...shopProducts);
    console.log(`[result] ${shop.username}: ${shopProducts.length}/${PRODUCTS_PER_SHOP} sản phẩm có ảnh`);
  }
  writeOutputs(shops, products);
  console.log(`\n[done] CSV: ${CSV_PATH}`);
  console.log(`[done] JSON: ${JSON_PATH}`);
} finally {
  await browser.close();
}
