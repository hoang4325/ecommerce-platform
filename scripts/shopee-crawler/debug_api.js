// debug_api.js - Chỉ dùng để xem raw response từ Shopee API
import { chromium } from 'playwright';
import fs from 'fs';

const KEYWORD = process.env.KEYWORD || 'bàn phím cơ';
const OUTPUT  = './output/debug_response.json';

(async () => {
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox','--disable-setuid-sandbox','--disable-blink-features=AutomationControlled']
  });
  const ctx = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 800 },
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
  });
  await ctx.addInitScript(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
  });

  const page = await ctx.newPage();
  let captured = false;

  page.on('response', async response => {
    const url = response.url();
    if (url.includes('search_items') && !captured) {
      captured = true;
      console.log('[URL]', url);
      try {
        const text = await response.text();
        // Lưu toàn bộ raw text để phân tích
        fs.writeFileSync(OUTPUT, text, 'utf-8');
        console.log('[RAW LENGTH]', text.length, 'chars');

        // In các keys cấp 1 và cấp 2 để debug cấu trúc
        const json = JSON.parse(text);
        console.log('[KEYS LVL1]', Object.keys(json));
        for (const k of Object.keys(json)) {
          const v = json[k];
          if (v && typeof v === 'object') {
            if (Array.isArray(v)) {
              console.log(`  [${k}] = Array(${v.length})`);
              if (v.length > 0) console.log(`    [0 keys]`, Object.keys(v[0]));
            } else {
              console.log(`  [${k}] = Object, keys:`, Object.keys(v));
            }
          } else {
            console.log(`  [${k}] =`, v);
          }
        }
      } catch(e) {
        console.error('[ERROR]', e.message);
      }
    }
  });

  const url = `https://shopee.vn/search?keyword=${encodeURIComponent(KEYWORD)}`;
  console.log('[GOTO]', url);
  await page.goto(url, { waitUntil: 'networkidle', timeout: 60000 });
  await new Promise(r => setTimeout(r, 5000));
  
  // Cuộn một chút để trigger search API
  await page.evaluate(() => window.scrollBy(0, 600));
  await new Promise(r => setTimeout(r, 3000));

  await browser.close();
  console.log('[DONE] Response saved to', OUTPUT);
})();
