import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';
import { execSync } from 'child_process';

// ─── Cấu hình ─────────────────────────────────────────────
const KEYWORD      = process.env.KEYWORD      || 'bàn phím cơ';
const LIMIT_PAGES  = parseInt(process.env.LIMIT_PAGES  || '1', 10);
const OUTPUT_DIR   = './output';
const DB_PATH      = path.join(OUTPUT_DIR, 'shopee_data.db');

if (!fs.existsSync(OUTPUT_DIR)) fs.mkdirSync(OUTPUT_DIR, { recursive: true });

// ─── Khởi tạo SQLite bằng CLI (không cần npm package) ─────
function sqliteRun(sql) {
  try {
    execSync(`sqlite3 "${DB_PATH}" "${sql.replace(/"/g, '\\"')}"`, { stdio: 'pipe' });
  } catch (e) {
    // ignore
  }
}

function sqliteRunFile(sqlFile) {
  try {
    execSync(`sqlite3 "${DB_PATH}" < "${sqlFile}"`, { stdio: 'pipe', shell: true });
  } catch (e) { /* ignore */ }
}

function sqliteInsert(sql) {
  const escaped = sql.replace(/\\/g, '\\\\');
  try {
    execSync(`sqlite3 "${DB_PATH}"`, {
      input: sql,
      stdio: ['pipe', 'pipe', 'pipe'],
      shell: false
    });
  } catch(e) { /* ignore dup */ }
}

// ─── Tạo bảng ─────────────────────────────────────────────
function initDB() {
  const schema = `
CREATE TABLE IF NOT EXISTS shops (
  shop_id           INTEGER PRIMARY KEY,
  name              TEXT,
  username          TEXT,
  item_count        INTEGER DEFAULT 0,
  rating_star       REAL,
  response_rate     INTEGER DEFAULT 0,
  follower_count    INTEGER DEFAULT 0,
  is_verified       INTEGER DEFAULT 0,
  is_official       INTEGER DEFAULT 0,
  location          TEXT,
  description       TEXT,
  cover_image       TEXT,
  ctime             TEXT,
  scraped_at        TEXT DEFAULT (datetime('now','localtime'))
);

CREATE TABLE IF NOT EXISTS products (
  product_id        INTEGER,
  shop_id           INTEGER,
  name              TEXT,
  price             REAL,
  price_min         REAL,
  price_max         REAL,
  price_original    REAL,
  historical_sold   INTEGER DEFAULT 0,
  stock             INTEGER DEFAULT 0,
  liked_count       INTEGER DEFAULT 0,
  rating_star       REAL,
  location          TEXT,
  discount_pct      INTEGER DEFAULT 0,
  image_url         TEXT,
  brand             TEXT,
  keyword           TEXT,
  ctime             TEXT,
  scraped_at        TEXT DEFAULT (datetime('now','localtime')),
  PRIMARY KEY (product_id, shop_id),
  FOREIGN KEY (shop_id) REFERENCES shops(shop_id)
);

CREATE TABLE IF NOT EXISTS product_images (
  id          INTEGER PRIMARY KEY AUTOINCREMENT,
  product_id  INTEGER,
  shop_id     INTEGER,
  image_url   TEXT,
  UNIQUE(product_id, image_url)
);
`;
  const schemaFile = path.join(OUTPUT_DIR, '_schema.sql');
  fs.writeFileSync(schemaFile, schema, 'utf-8');
  execSync(`sqlite3 "${DB_PATH}" < "${schemaFile}"`, { shell: true });
  console.log(`[🗄️  DB] Đã khởi tạo SQLite: ${DB_PATH}`);
}

// ─── Hàm escape an toàn cho SQL string ────────────────────
function esc(v) {
  if (v === null || v === undefined) return 'NULL';
  if (typeof v === 'number')         return isNaN(v) ? 'NULL' : String(v);
  if (typeof v === 'boolean')        return v ? '1' : '0';
  return `'${String(v).replace(/'/g, "''")}'`;
}

// ─── Ghi batch qua file SQL tạm ───────────────────────────
function batchInsertProducts(products) {
  if (!products.length) return;
  const tmpFile = path.join(OUTPUT_DIR, '_products_batch.sql');
  const lines = ['BEGIN;'];
  for (const p of products) {
    lines.push(`INSERT OR REPLACE INTO products
      (product_id, shop_id, name, price, price_min, price_max, price_original,
       historical_sold, stock, liked_count, rating_star, location, discount_pct,
       image_url, brand, keyword, ctime)
    VALUES (${esc(p.productId)}, ${esc(p.shopId)}, ${esc(p.name)},
      ${esc(p.price)}, ${esc(p.priceMin)}, ${esc(p.priceMax)}, ${esc(p.priceBeforeDiscount)},
      ${esc(p.historicalSold)}, ${esc(p.stock)}, ${esc(p.likedCount)},
      ${esc(p.ratingStar)}, ${esc(p.location)}, ${esc(p.discount)},
      ${esc(p.imageUrl)}, ${esc(p.brand)}, ${esc(KEYWORD)}, ${esc(p.ctime)});`);
    for (const img of (p.images || [])) {
      lines.push(`INSERT OR IGNORE INTO product_images (product_id, shop_id, image_url)
        VALUES (${esc(p.productId)}, ${esc(p.shopId)}, ${esc(img)});`);
    }
  }
  lines.push('COMMIT;');
  fs.writeFileSync(tmpFile, lines.join('\n'), 'utf-8');
  execSync(`sqlite3 "${DB_PATH}" < "${tmpFile}"`, { shell: true });
  console.log(`[💾 DB] Đã ghi ${products.length} sản phẩm vào DB`);
}

function batchInsertShops(shops) {
  if (!shops.length) return;
  const tmpFile = path.join(OUTPUT_DIR, '_shops_batch.sql');
  const lines = ['BEGIN;'];
  for (const s of shops) {
    lines.push(`INSERT OR REPLACE INTO shops
      (shop_id, name, username, item_count, rating_star, response_rate,
       follower_count, is_verified, is_official, location, description, cover_image, ctime)
    VALUES (${esc(s.shopId)}, ${esc(s.name)}, ${esc(s.username)},
      ${esc(s.itemCount)}, ${esc(s.ratingStar)}, ${esc(s.responseRate)},
      ${esc(s.followerCount)}, ${esc(s.isShopeeVerified ? 1 : 0)},
      ${esc(s.isOfficialShop ? 1 : 0)}, ${esc(s.location)},
      ${esc(s.description)}, ${esc(s.coverImage)}, ${esc(s.ctime)});`);
  }
  lines.push('COMMIT;');
  fs.writeFileSync(tmpFile, lines.join('\n'), 'utf-8');
  execSync(`sqlite3 "${DB_PATH}" < "${tmpFile}"`, { shell: true });
  console.log(`[💾 DB] Đã ghi ${shops.length} shop vào DB`);
}

// ─── Main ──────────────────────────────────────────────────
(async () => {
  console.log(`\n[🚀 CRAWLER] Từ khóa: "${KEYWORD}" | LIMIT_PAGES: ${LIMIT_PAGES}`);
  initDB();

  const browser = await chromium.launch({
    headless: true,
    args: [
      '--no-sandbox', '--disable-setuid-sandbox',
      '--disable-blink-features=AutomationControlled',
      '--window-size=1280,800'
    ]
  });

  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 800 },
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
  });
  await context.addInitScript(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
  });

  const page = await context.newPage();
  const productsBuffer = [];
  const shopsSet = new Map();

  // ── Bắt API Shopee search ──────────────────────────────
  page.on('response', async response => {
    const url = response.url();
    if (!url.includes('api/v4/search/search_items')) return;

    console.log(`[🔍 API] ${url.substring(0, 90)}...`);
    try {
      const text = await response.text();
      if (!text) return;
      const json = JSON.parse(text);

      let items = [];
      if (Array.isArray(json.items))                      items = json.items;
      else if (json.data?.item_cards)                      items = json.data.item_cards;
      else if (Array.isArray(json.data?.sections)) {
        for (const sec of json.data.sections)
          if (Array.isArray(sec.data?.item)) items.push(...sec.data.item);
      }

      console.log(`[📦] Nhận ${items.length} sản phẩm từ API`);
      const batch = [];
      for (const item of items) {
        const d = item.item_basic || item;
        if (!d?.itemid || !d?.shopid) continue;
        const toVND = v => (v ? v / 100000 : null);
        batch.push({
          productId:          d.itemid,
          shopId:             d.shopid,
          name:               d.name,
          price:              toVND(d.price),
          priceMin:           toVND(d.price_min),
          priceMax:           toVND(d.price_max),
          priceBeforeDiscount: toVND(d.price_before_discount),
          historicalSold:     d.historical_sold || d.sold || 0,
          stock:              d.stock || 0,
          likedCount:         d.liked_count || 0,
          ratingStar:         d.item_rating?.rating_star || null,
          location:           d.shop_location || d.location || '',
          discount:           d.raw_discount || 0,
          imageUrl:           d.image ? `https://down-vn.img.susercontent.com/file/${d.image}` : null,
          images:             (d.images || []).map(img => `https://down-vn.img.susercontent.com/file/${img}`),
          brand:              d.brand || '',
          ctime:              d.ctime ? new Date(d.ctime * 1000).toISOString() : null,
        });
        if (!shopsSet.has(d.shopid)) shopsSet.set(d.shopid, null);
      }
      if (batch.length > 0) {
        productsBuffer.push(...batch);
        batchInsertProducts(batch); // ghi ngay vào DB
      }
    } catch (err) {
      console.error('[⚠️ PARSE ERROR]', err.message);
    }
  });

  try {
    const searchUrl = `https://shopee.vn/search?keyword=${encodeURIComponent(KEYWORD)}`;
    console.log(`[🌐] Mở trình duyệt: ${searchUrl}`);
    await page.goto(searchUrl, { waitUntil: 'networkidle', timeout: 60000 });
    await new Promise(r => setTimeout(r, 5000));

    // Cuộn nhiều vòng để trigger lazy load
    for (let pageIdx = 0; pageIdx < LIMIT_PAGES; pageIdx++) {
      console.log(`[📜] Cuộn trang ${pageIdx + 1}/${LIMIT_PAGES}...`);
      for (let i = 0; i < 12; i++) {
        await page.evaluate(() => window.scrollBy(0, window.innerHeight * 0.8));
        await new Promise(r => setTimeout(r, 800 + Math.random() * 700));
      }
      await new Promise(r => setTimeout(r, 3000));
    }

    // Lọc trùng toàn bộ
    const uniqueMap = new Map();
    for (const p of productsBuffer) uniqueMap.set(p.productId, p);
    const allProducts = Array.from(uniqueMap.values());
    console.log(`\n[📊] Tổng sản phẩm duy nhất: ${allProducts.length}`);
    console.log(`[📊] Số shop cần cào: ${shopsSet.size}`);

    // ── Cào thông tin Shop ─────────────────────────────────
    console.log(`\n[🏬] Đang cào thông tin ${shopsSet.size} shop...`);
    const shopsList = [];
    let cnt = 0;

    for (const shopId of shopsSet.keys()) {
      cnt++;
      process.stdout.write(`[🏬] (${cnt}/${shopsSet.size}) shopid=${shopId}...`);
      try {
        const shopInfo = await page.evaluate(async (id) => {
          try {
            const r = await fetch(`https://shopee.vn/api/v4/product/get_shop_info?shopid=${id}`);
            if (!r.ok) return null;
            return (await r.json()).data || null;
          } catch { return null; }
        }, shopId);

        if (shopInfo) {
          const shop = {
            shopId:           shopInfo.shopid,
            name:             shopInfo.name,
            username:         shopInfo.username,
            itemCount:        shopInfo.item_count || 0,
            ratingStar:       shopInfo.rating_star || null,
            responseRate:     shopInfo.response_rate || 0,
            followerCount:    shopInfo.follower_count || 0,
            isShopeeVerified: !!shopInfo.is_shopee_verified,
            isOfficialShop:   !!shopInfo.is_official_shop,
            location:         shopInfo.place || '',
            description:      shopInfo.description || '',
            coverImage:       shopInfo.cover ? `https://down-vn.img.susercontent.com/file/${shopInfo.cover}` : null,
            ctime:            shopInfo.ctime ? new Date(shopInfo.ctime * 1000).toISOString() : null,
          };
          shopsList.push(shop);
          console.log(` ✅ ${shopInfo.name}`);
        } else {
          console.log(` ❌ no data`);
        }
      } catch (e) {
        console.log(` ⚠️ error: ${e.message}`);
      }
      await new Promise(r => setTimeout(r, 1200 + Math.random() * 1300));
    }

    // Ghi batch shop vào DB
    batchInsertShops(shopsList);

    // ── Xuất JSON & CSV backup ─────────────────────────────
    fs.writeFileSync(path.join(OUTPUT_DIR, 'shopee_products.json'), JSON.stringify(allProducts, null, 2), 'utf-8');
    fs.writeFileSync(path.join(OUTPUT_DIR, 'shopee_shops.json'), JSON.stringify(shopsList, null, 2), 'utf-8');
    exportCSV(allProducts, shopsList);

    // ── Thống kê cuối ──────────────────────────────────────
    console.log('\n' + '═'.repeat(60));
    console.log(`[✅ DONE] Từ khóa       : "${KEYWORD}"`);
    console.log(`[✅ DONE] Sản phẩm      : ${allProducts.length}`);
    console.log(`[✅ DONE] Shop          : ${shopsList.length}`);
    console.log(`[✅ DONE] Database      : ${DB_PATH}`);
    console.log(`[✅ DONE] JSON products : ${path.join(OUTPUT_DIR, 'shopee_products.json')}`);
    console.log(`[✅ DONE] JSON shops    : ${path.join(OUTPUT_DIR, 'shopee_shops.json')}`);
    console.log(`[✅ DONE] CSV tổng hợp  : ${path.join(OUTPUT_DIR, 'shopee_combined_data.csv')}`);
    console.log('═'.repeat(60) + '\n');

  } catch (err) {
    console.error('[💥 FATAL]', err);
  } finally {
    await browser.close();
    // Dọn file tạm
    for (const f of ['_schema.sql','_products_batch.sql','_shops_batch.sql']) {
      const p = path.join(OUTPUT_DIR, f);
      if (fs.existsSync(p)) fs.unlinkSync(p);
    }
  }
})();

// ── Xuất CSV helper ────────────────────────────────────────
function exportCSV(products, shops) {
  const shopMap = new Map(shops.map(s => [s.shopId, s]));
  const header = 'ProductID,ShopID,ShopName,ProductName,Price(VND),Sold,Rating,Discount%,Location,ImageURL\n';
  const rows = products.map(p => {
    const s = shopMap.get(p.shopId);
    return [
      p.productId,
      p.shopId,
      `"${(s?.name || '').replace(/"/g,'""')}"`,
      `"${(p.name || '').replace(/"/g,'""')}"`,
      Math.round(p.price || 0),
      p.historicalSold || 0,
      p.ratingStar || 0,
      p.discount || 0,
      `"${(p.location || '').replace(/"/g,'""')}"`,
      `"${p.imageUrl || ''}"`,
    ].join(',');
  }).join('\n');
  fs.writeFileSync(
    path.join(OUTPUT_DIR, 'shopee_combined_data.csv'),
    '\ufeff' + header + rows,
    'utf-8'
  );
}
