// debug_wait.js - Thử đợi lâu hơn và dùng mobile UA
import { chromium } from 'playwright';
import fs from 'fs';

const KEYWORD = process.env.KEYWORD || 'bàn phím cơ';

(async () => {
  // Thử mobile UA để bypass anti-bot
  const UAS = [
    // Desktop Chrome mới nhất
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36',
    // Mobile Android
    'Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.6478.122 Mobile Safari/537.36',
  ];

  for (const [idx, ua] of UAS.entries()) {
    console.log(`\n=== Test ${idx + 1}: UA = ${ua.substring(0, 60)}... ===`);
    const browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox','--disable-setuid-sandbox','--disable-blink-features=AutomationControlled','--disable-dev-shm-usage'],
    });
    const ctx = await browser.newContext({
      userAgent: ua,
      viewport: idx === 1 ? { width: 390, height: 844 } : { width: 1280, height: 900 },
      locale: 'vi-VN',
      timezoneId: 'Asia/Ho_Chi_Minh',
      isMobile: idx === 1,
      hasTouch: idx === 1,
      extraHTTPHeaders: {
        'Accept-Language': 'vi-VN,vi;q=0.9,en-US;q=0.8',
        'sec-ch-ua': '"Chromium";v="126", "Google Chrome";v="126"',
        'sec-ch-ua-mobile': idx === 1 ? '?1' : '?0',
        'sec-ch-ua-platform': idx === 1 ? '"Android"' : '"Windows"',
      }
    });
    await ctx.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
      Object.defineProperty(navigator, 'plugins', { get: () => [1,2,3,4,5] });
    });

    const page = await ctx.newPage();

    // Intercept để check status
    page.on('response', response => {
      const url = response.url();
      if (url.includes('search_items') || url.includes('shopee.vn/api')) {
        console.log(`  [API ${response.status()}] ${url.substring(0, 80)}`);
      }
    });

    const url = `https://shopee.vn/search?keyword=${encodeURIComponent(KEYWORD)}`;
    try {
      await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
      // Đợi thêm để JS render xong
      await new Promise(r => setTimeout(r, 8000));

      const htmlLen = await page.evaluate(() => document.body.innerHTML.length);
      const productCount = await page.evaluate(() =>
        document.querySelectorAll('a[href]').length
      ).catch(() => 0);
      const has403 = await page.evaluate(() => document.body.innerHTML.includes('403'));
      console.log(`  HTML size: ${htmlLen}, links: ${productCount}, has403: ${has403}`);

      // Screenshot để xem trực quan
      await page.screenshot({ path: `./output/debug_screenshot_${idx}.png`, fullPage: false });
      console.log(`  Screenshot saved: output/debug_screenshot_${idx}.png`);

    } catch(e) {
      console.error('  Error:', e.message);
    }
    await browser.close();
  }
  console.log('\nDone.');
})();
