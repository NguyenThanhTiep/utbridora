// Check the actual pinned browser and runner libraries; do not suppress Playwright's dependency checks.
const { chromium } = require('@playwright/test');
(async () => {
  const browser = await chromium.launch();
  try {
    const page = await browser.newPage();
    await page.setContent('<!doctype html><meta charset="utf-8"><p>UTBridora — Tiến độ thực tập</p>');
    await page.evaluate(() => document.fonts.ready);
    const ready = await page.locator('p').evaluate(p => p.getBoundingClientRect().height > 0 && p.textContent.includes('Tiến độ'));
    if (!ready) throw new Error('Chromium failed to render the UTF-8 preflight page');
    console.log('Actual Chromium runner preflight PASS:', browser.version());
  } finally { await browser.close(); }
})().catch(error => { console.error(error.message); process.exitCode = 1; });
