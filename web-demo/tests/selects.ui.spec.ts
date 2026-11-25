import { expect, test, type Page } from '@playwright/test';

async function controls(page: Page, width = 1280) {
  await page.setViewportSize({ width, height: 840 });
  await page.route('**/api/v1/**', route => route.fulfill({ status: 401, json: {} }));
  await page.goto('/');
  const stylesheet = new URL('/src/ui/selects.css', page.url()).href;
  // Exercise the actual shared stylesheet without a backend or business-data writes.
  await page.setContent(`<!doctype html><html lang="vi"><head>
    <link rel="stylesheet" href="${stylesheet}">
    <style>
      * { box-sizing: border-box; } body { margin: 0; background: #f3f6fb; color: #142f40; font: 14px Arial, sans-serif; }
      #root { max-width: 480px; padding: 20px; margin: auto; }
      form { padding: 18px; background: white; border: 1px solid #e3eaf2; border-radius: 16px; }
      label { display: block; margin-bottom: 16px; } select { width: 100%; margin-top: 8px; padding-block: 10px; }
    </style></head><body><main id="root"><form>
      <label>Kỳ theo dõi<select name="term"><option value="">Tất cả kỳ</option>
        <option value="week1">DEMO-WEEK1</option><option value="week2">Học kỳ 2 · 2026–2027</option>
        <option value="closed" disabled>Kỳ đã đóng</option></select></label>
      <label>Trường<select name="school" required><option value="">Chọn trường</option>
        <optgroup label="Nhà trường"><option value="uth">Trường Đại học Giao thông vận tải Thành phố Hồ Chí Minh</option></optgroup>
        <option value="long">${'Tên trường rất dài '.repeat(16)}</option></select></label>
      <label>Chưa có phạm vi<select disabled><option>Chưa có kỳ trong phạm vi</option></select></label>
      <button type="submit">Áp dụng</button>
    </form></main></body></html>`);
  await expect(page.getByLabel('Kỳ theo dõi')).toHaveCSS('border-radius', '10px');
}

async function requireCustomPicker(page: Page) {
  test.skip(!await page.evaluate(() => CSS.supports('appearance', 'base-select') && CSS.supports('selector(::picker(select))')),
    'This browser retains its native dropdown fallback.');
}

test('shared dropdown supports pointer selection, selected styling and outside dismissal', async ({ page }) => {
  await controls(page);
  await requireCustomPicker(page);
  const term = page.getByLabel('Kỳ theo dõi');
  await term.click();
  await expect.poll(() => term.evaluate(node => node.matches(':open'))).toBeTruthy();
  await expect.poll(() => term.evaluate(node => getComputedStyle(node, '::picker(select)').borderRadius)).toBe('13px');
  await page.getByRole('option', { name: 'DEMO-WEEK1', exact: true }).click();
  await expect(term).toHaveValue('week1');
  await term.click();
  await expect(term.locator('option:checked')).toHaveCSS('background-color', 'rgb(231, 246, 240)');
  await expect.poll(() => term.evaluate(node => getComputedStyle(node, '::picker(select)').opacity)).toBe('1');
  await page.screenshot({ path: '../target/shared-select-open.png', animations: 'disabled' });
  await page.mouse.click(4, 4);
  await expect.poll(() => term.evaluate(node => node.matches(':open'))).toBeFalsy();
  await expect(term).toHaveValue('week1');
});

test('shared dropdown preserves keyboard selection, Escape, native validation and form values', async ({ page }) => {
  await controls(page);
  const term = page.getByLabel('Kỳ theo dõi');
  await term.focus();
  await page.keyboard.press('Space');
  await page.keyboard.press('ArrowDown');
  await page.keyboard.press('Enter');
  await expect(term).toHaveValue('week1');
  await term.press('Space');
  await page.keyboard.press('Escape');
  await expect(term).toBeFocused();
  await expect(term).toHaveValue('week1');
  await expect(page.getByLabel('Chưa có phạm vi')).toBeDisabled();
  expect(await page.locator('form').evaluate(form => (form as HTMLFormElement).checkValidity())).toBeFalsy();
  await page.getByRole('combobox', { name: 'Trường', exact: true }).selectOption('uth');
  expect(await page.locator('form').evaluate(form => (form as HTMLFormElement).checkValidity())).toBeTruthy();
  expect(await page.locator('form').evaluate(form => Object.fromEntries(new FormData(form as HTMLFormElement)))).toEqual({ term: 'week1', school: 'uth' });
});

for (const width of [320, 390]) test(`shared dropdown fits long labels and scrollable options at ${width}px`, async ({ page }) => {
  await controls(page, width);
  await requireCustomPicker(page);
  const school = page.getByRole('combobox', { name: 'Trường', exact: true });
  await school.selectOption('long');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await school.click();
  const option = page.getByRole('option', { name: 'Trường Đại học Giao thông vận tải Thành phố Hồ Chí Minh', exact: true });
  await expect(option).toBeVisible();
  const bounds = await option.boundingBox();
  expect(bounds!.x).toBeGreaterThanOrEqual(0);
  expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(width);
  await option.click();
  await expect(school).toHaveValue('uth');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('a long dropdown scrolls to the final option without leaving the viewport', async ({ page }) => {
  await controls(page, 390);
  await requireCustomPicker(page);
  const term = page.getByLabel('Kỳ theo dõi');
  await term.evaluate(node => {
    for (let index = 1; index <= 60; index++) (node as HTMLSelectElement).add(new Option('Kỳ thực tập ' + index, 'term-' + index));
  });
  await term.click();
  await page.getByRole('option', { name: 'Kỳ thực tập 60', exact: true }).click();
  await expect(term).toHaveValue('term-60');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('shared dropdown keeps native fallback and respects reduced motion', async ({ page }) => {
  await controls(page);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.getByLabel('Kỳ theo dõi')).toHaveCSS('transition-duration', '0s');
  // Emulate a browser declining the feature query: the original select must still work.
  await page.evaluate(async () => {
    const response = await fetch('/src/ui/selects.css');
    const css = (await response.text()).replace('@supports (appearance: base-select) and selector(::picker(select))', '@supports (unknown-select-feature: unsupported)');
    document.querySelector('link[rel="stylesheet"]')?.remove();
    const style = document.createElement('style'); style.textContent = css; document.head.append(style);
  });
  await page.getByLabel('Kỳ theo dõi').selectOption('week2');
  await expect(page.getByLabel('Kỳ theo dõi')).toHaveValue('week2');
  expect(await page.getByLabel('Kỳ theo dõi').evaluate(node => getComputedStyle(node).appearance)).not.toBe('base-select');
});
