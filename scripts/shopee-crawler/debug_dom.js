// debug_dom.js - Xem cấu trúc HTML của trang kết quả tìm kiếm Shopee
import { chromium } from 'playwright';
import fs from 'fs';

const KEYWORD = process.env.KEYWORD || 'bàn phím cơ';

(async () => {
  const browser = await chromium.launch({
    headless: true,
    args: [
      '--no-sandbox', '--disable-setuid-sandbox',
      '--disable-blink-features=AutomationControlled',
      '--disable-dev-shm-usage',
      '--disable-extensions',
    ]
  });

  const ctx = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 900 },
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    extraHTTPHeaders: {
      'Accept-Language': 'vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7',
    }
  });

  await ctx.addInitScript(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
    Object.defineProperty(navigator, 'languages', { get: () => ['vi-VN', 'vi', 'en-US', 'en'] });
  });

  const page = await ctx.newPage();

  const url = `https://shopee.vn/search?keyword=${encodeURIComponent(KEYWORD)}`;
  console.log('[GOTO]', url);

  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });

  // Simulate human: di chuyển chuột ngẫu nhiên
  await page.mouse.move(400, 300);
  await new Promise(r => setTimeout(r, 1000));
  await page.mouse.move(600, 400);
  await new Promise(r => setTimeout(r, 2000));

  // Đợi sản phẩm load
  try {
    await page.waitForSelector('[data-sqe="item"]', { timeout: 15000 });
    console.log('[✅] Tìm thấy selector [data-sqe="item"]');
  } catch {
    console.log('[❌] Không tìm thấy [data-sqe="item"]');
  }

  // Thử các selector phổ biến của Shopee
  const selectors = [
    '[data-sqe="item"]',
    '.shopee-search-item-result__item',
    '.col-xs-2-4.shopee-search-item-result__item',
    'li.col-xs-2-4',
    '[class*="search-item"]',
    '[class*="item-card"]',
    'a[href*="/product/"]',
    'div[class*="ShopeeItem"]',
  ];

  for (const sel of selectors) {
    const count = await page.$$(sel).then(r => r.length).catch(() => 0);
    console.log(`  ${sel}: ${count} elements`);
  }

  // Lưu HTML một đoạn để phân tích
  const bodyHtml = await page.evaluate(() => document.body.innerHTML);
  fs.writeFileSync('./output/debug_page.html', bodyHtml, 'utf-8');
  console.log('[SAVED] HTML length:', bodyHtml.length);

  // Tìm tất cả links sản phẩm
  const productLinks = await page.evaluate(() =>
    [...document.querySelectorAll('a[href]')]
      .map(a => a.href)
      .filter(h => h.includes('/product/') || h.match(/shopee\.vn\/.+-i\.\d+\.\d+/))
      .slice(0, 5)
  );
  console.log('[PRODUCT LINKS FOUND]', productLinks.length);
  productLinks.forEach(l => console.log(' ', l));

  await browser.close();
})();
