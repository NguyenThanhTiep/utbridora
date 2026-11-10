import { test, expect } from '@playwright/test';

// T2-07/T2-08: chạy từ StudentPreferenceIT trên MySQL riêng. Nguyện vọng được tạo qua backend T2 (DEV02) thật; cơ hội là
// fixture cô lập của test vì API cơ hội T5 chưa có, nên đây không phải bằng chứng đã tích hợp T5.
test('student sees and withdraws a real preference through the T2 API', async ({ page }) => {
  test.skip(process.env.E2E_T2_PREFERENCE !== 'true', 'Run from StudentPreferenceIT on isolated MySQL');
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill('student@example.test');
  await page.getByLabel('Mật khẩu', { exact: true }).fill(process.env.E2E_PASSWORD ?? '');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Nguyện vọng của tôi', exact: true }).click();
  const item = page.locator('li.student-list-item').filter({ hasText: 'Lời nhắn: W3 browser preference' });
  await expect(item.getByText('Đã gửi, chờ nhà trường xét', { exact: true })).toBeVisible();
  page.once('dialog', dialog => void dialog.accept());
  await item.getByRole('button', { name: 'Rút nguyện vọng', exact: true }).click();
  await expect(page.getByText('Đã rút nguyện vọng.', { exact: true })).toBeVisible();
  await expect(item.getByText('Đã rút', { exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: 'Nguyện vọng của tôi', exact: true }).click();
  await expect(item.getByText('Đã rút', { exact: true })).toBeVisible();
  await expect(item.getByRole('button', { name: 'Rút nguyện vọng', exact: true })).toHaveCount(0);
});
