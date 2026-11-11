import { test, expect } from '@playwright/test';

test('student creates and updates a profile through the real term and profile APIs', async ({ page }) => {
  test.skip(!process.env.E2E_PROFILE_STUDENT_EMAIL, 'Run from StudentProfileIT with isolated MySQL fixtures');
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(process.env.E2E_PROFILE_STUDENT_EMAIL!);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(process.env.E2E_PASSWORD!);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Thông tin chung', exact: true }).click();
  await expect(page.getByText('Chưa thể đọc hồ sơ trong học kỳ này', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Tạo hồ sơ', exact: true }).click();
  await expect(page.getByLabel('Ngành đào tạo', { exact: true })).toBeEnabled();
  await page.getByLabel('Ngành đào tạo', { exact: true }).selectOption('SE');
  await page.getByLabel('Số điện thoại', { exact: true }).fill('0901234567');
  await page.getByRole('button', { name: 'Lưu thông tin chung', exact: true }).click();
  await expect(page.getByText('Đã lưu thông tin chung.', { exact: true })).toBeVisible();
  await page.reload();
  await page.getByRole('button', { name: 'Thông tin chung', exact: true }).click();
  await expect(page.getByLabel('Số điện thoại', { exact: true })).toHaveValue('0901234567');
  await expect(page.getByLabel('Ngành đào tạo', { exact: true })).toHaveValue('SE');
});
