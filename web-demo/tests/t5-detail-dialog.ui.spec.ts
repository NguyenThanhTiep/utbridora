import { expect, test } from '@playwright/test';

for (const width of [1440, 390, 320]) test(`T5 detail dialog traps focus, scrolls internally and restores the page at ${width}px`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 844 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.goto('/tests/fixtures/t5-detail-dialog.html');
  await page.evaluate(() => window.scrollTo(0, 450));
  const opener = page.getByRole('button', { name: 'Xem chi tiết', exact: true });
  await opener.click();
  const dialog = page.getByRole('dialog', { name: 'Chi tiết hồ sơ thử nghiệm', exact: true });
  await expect(dialog).toBeVisible();
  const pageY = await page.evaluate(() => scrollY);
  const centered = async () => {
    const box = (await dialog.boundingBox())!;
    expect(box.y).toBeGreaterThanOrEqual(width <= 600 ? 11 : 19);
    expect(844 - box.y - box.height).toBeGreaterThanOrEqual(width <= 600 ? 11 : 19);
    expect(Math.abs(box.y - (844 - box.y - box.height))).toBeLessThan(2);
  };
  await centered();
  expect(await dialog.evaluate(node => node.scrollHeight > node.clientHeight)).toBe(true);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await opener.evaluate(node => node.focus());
  expect(await dialog.evaluate(node => node.contains(document.activeElement))).toBe(true);
  await dialog.getByRole('button', { name: 'Thao tác cuối hồ sơ' }).focus();
  await centered();
  await page.keyboard.press('Tab');
  expect(await dialog.evaluate(node => node.contains(document.activeElement))).toBe(true);
  expect(await page.evaluate(() => scrollY)).toBe(pageY);
  await dialog.evaluate(node => { node.scrollTop = 0; });
  await page.screenshot({ path: testInfo.outputPath(`t5-detail-${width}.png`) });
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(opener).toBeFocused();
  expect(await page.evaluate(() => scrollY)).toBe(pageY);
  expect(await page.evaluate(() => document.documentElement.style.overflow)).toBe('');
});

test('T5 nested details retain the scroll lock, and an uncertain action blocks dismissal', async ({ page }) => {
  await page.goto('/tests/fixtures/t5-detail-dialog.html');
  await page.getByRole('button', { name: 'Xem chi tiết', exact: true }).click();
  const detail = page.getByRole('dialog', { name: 'Chi tiết hồ sơ thử nghiệm', exact: true });
  await detail.getByRole('button', { name: 'Mở thao tác bên trong' }).click();
  const nested = page.getByRole('dialog', { name: 'Thao tác hồ sơ', exact: true });
  await expect(nested).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(nested).toHaveCount(0);
  await expect(detail).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.style.overflow)).toBe('hidden');
  await detail.getByRole('button', { name: 'Đổi trạng thái đang xử lý' }).click();
  await page.keyboard.press('Escape');
  await page.mouse.click(1, 1);
  await expect(detail).toBeVisible();
  await detail.getByRole('button', { name: 'Đổi trạng thái đang xử lý' }).click();
  await page.mouse.click(1, 1);
  await expect(detail).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.style.overflow)).toBe('');
});
