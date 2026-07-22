// debug_capture.js - Capture body thực của search_items với timing đúng
import { chromium } from 'playwright';
import fs from 'fs';

const KEYWORD = process.env.KEYWORD || 'bàn phím cơ';

(async () => {
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox','--disable-setuid-sandbox','--disable-blink-features=AutomationControlled'],
  });

  const ctx = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 900 },
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    extraHTTPHeaders: { 'Accept-Language': 'vi-VN,vi;q=0.9,en-US;q=0.8' }
  });
  await ctx.addInitScript(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    Object.defineProperty(navigator, 'plugins', { get: () => [1,2,3,4,5] });
  });

  const page = await ctx.newPage();

  // Bắt TẤT CẢ response liên quan API Shopee
  page.on('response', async response => {
    const url = response.url();
    if (!url.includes('/api/v4/')) return;

    const status = response.status();
    console.log(`[${status}] ${url.substring(0, 100)}`);

    if (url.includes('search_items')) {
      try {
        const body = await response.text();
        console.log(`\n>>> search_items body length: ${body.length}`);
        console.log('>>> First 500 chars:', body.substring(0, 500));
        fs.writeFileSync('./output/search_items_response.json', body, 'utf-8');
        console.log('>>> Saved to output/search_items_response.json\n');
      } catch(e) {
        console.log('>>> Cannot read body:', e.message);
      }
    }

    if (url.includes('itemcard/set/elements')) {
      try {
        const body = await response.text();
        console.log(`\n>>> itemcard body length: ${body.length}`);
        fs.writeFileSync('./output/itemcard_response.json', body, 'utf-8');
        console.log('>>> Saved to output/itemcard_response.json\n');
      } catch(e) { /* ignore */ }
    }
  });

  const searchUrl = `https://shopee.vn/search?keyword=${encodeURIComponent(KEYWORD)}`;
  console.log('[GOTO]', searchUrl);

  // Dùng domcontentloaded thay vì networkidle
  await page.goto(searchUrl, { waitUntil: 'domcontentloaded', timeout: 60000 });

  // Đợi 8 giây để JS load xong và fire API
  console.log('[WAIT] Đợi 8 giây...');
  await new Promise(r => setTimeout(r, 8000));

  console.log('[DONE]');
  await browser.close();
})();
