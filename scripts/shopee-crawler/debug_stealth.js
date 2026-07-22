// debug_stealth.js - Test với stealth plugin để bypass Shopee anti-bot
import { chromium } from 'playwright-extra';
import StealthPlugin from 'playwright-extra-plugin-stealth';
import fs from 'fs';

chromium.use(StealthPlugin());

const KEYWORD = process.env.KEYWORD || 'bàn phím cơ';

(async () => {
  const browser = await chromium.launch({
    headless: true,
    args: ['--no-sandbox','--disable-setuid-sandbox'],
  });

  const ctx = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    viewport: { width: 1280, height: 900 },
    locale: 'vi-VN',
    timezoneId: 'Asia/Ho_Chi_Minh',
    extraHTTPHeaders: { 'Accept-Language': 'vi-VN,vi;q=0.9,en-US;q=0.8' }
  });

  const page = await ctx.newPage();

  page.on('response', async response => {
    const url = response.url();
    if (!url.includes('search_items')) return;

    console.log(`[${response.status()}] ${url.substring(0,100)}`);
    try {
      const body = await response.text();
      console.log('Body length:', body.length);
      console.log('First 300 chars:', body.substring(0, 300));
      fs.writeFileSync('./output/stealth_response.json', body, 'utf-8');
    } catch(e) { console.log('Error reading body:', e.message); }
  });

  const url = `https://shopee.vn/search?keyword=${encodeURIComponent(KEYWORD)}`;
  console.log('[GOTO]', url);
  await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 60000 });

  // Human-like: di chuyển chuột + đợi
  await page.mouse.move(300, 200);
  await new Promise(r => setTimeout(r, 1000));
  await page.mouse.move(640, 400);
  await new Promise(r => setTimeout(r, 8000));

  console.log('[DONE]');
  await browser.close();
})();
