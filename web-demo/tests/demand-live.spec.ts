import { test, expect } from '@playwright/test';

test('real API: sign in, create, reload, edit, reload', async ({ page }) => {
  test.skip(!process.env.DEMAND_E2E_EMAIL, 'Run through DemandIT with -Dfrontend.e2e=true');
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(process.env.DEMAND_E2E_EMAIL!);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(process.env.DEMAND_E2E_PASSWORD!);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Tạo nhu cầu', exact: true }).click();
  await page.getByLabel('Trường nhận nhu cầu', { exact: true }).selectOption(process.env.DEMAND_E2E_SCHOOL!);
  await page.getByLabel('Kỳ thực tập', { exact: true }).selectOption(process.env.DEMAND_E2E_TERM!);
  await page.getByLabel('Tên nhu cầu').fill('Live demand');
  await page.getByLabel('Số lượng vị trí').fill('3');
  await page.getByLabel('Hạn tuyển').fill('2027-01-01T09:00');
  await page.getByLabel('Mô tả công việc').fill('Real HTTP and MySQL');
  await page.getByLabel('Ngày bắt đầu thực tập').fill('2027-02-01');
  await page.getByLabel('Ngày kết thúc thực tập').fill('2027-05-01');
  if (process.env.DEMAND_E2E_TAXONOMY?.startsWith('skills-v')) {
    await page.getByRole('button', { name: 'Tra cứu kỹ năng', exact: true }).click();
    await page.getByRole('button', { name: 'Chọn JAVA bắt buộc', exact: true }).click();
    await expect(page.getByLabel('Phiên bản danh mục kỹ năng')).toHaveValue(process.env.DEMAND_E2E_TAXONOMY);
  } else {
    await page.getByRole('button', { name: 'Thêm kỹ năng bắt buộc', exact: true }).click();
    await page.getByLabel('Bắt buộc: mã kỹ năng 1', { exact: true }).fill('JAVA');
    await page.getByLabel('Phiên bản danh mục kỹ năng').selectOption(process.env.DEMAND_E2E_TAXONOMY || 'v1');
  }
  await page.getByLabel('Phiên bản danh mục học vụ').selectOption(process.env.DEMAND_E2E_ACADEMIC || 'v1');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Live demand', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  await page.getByRole('button', { name: /Sửa bản nháp/ }).click();
  await page.getByLabel('Tên nhu cầu').fill('Live updated');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Live updated', exact: true })).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Live updated', exact: true })).toBeVisible();
});
