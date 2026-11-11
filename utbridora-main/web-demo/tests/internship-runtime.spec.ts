import { test, expect } from '@playwright/test';

// Real server/assignment/owner data from InternshipRuntimeIT. No intercepted API responses.
test('HR and Mentor navigate their actual assigned cohort and group', async ({ page }) => {
  test.skip(!process.env.E2E_T3_HR_EMAIL, 'Requires isolated InternshipRuntimeIT server');
  for (const [email, assignment] of [[process.env.E2E_T3_HR_EMAIL!, process.env.E2E_T3_HR_ASSIGNMENT!],
    [process.env.E2E_T3_MENTOR_EMAIL!, process.env.E2E_T3_MENTOR_ASSIGNMENT!]]) {
    await page.goto('/');
    await page.getByLabel('Email', { exact: true }).fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(process.env.E2E_PASSWORD!);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
    await page.getByLabel('Phân công đang làm việc').selectOption(assignment);
    await page.getByLabel('Đợt thực tập', { exact: true }).selectOption(process.env.E2E_T3_COHORT!);
    await page.getByRole('button', { name: 'Xem nhóm G-' + process.env.E2E_T3_GROUP! }).click();
    await expect(page.getByRole('heading', { name: 'Nhóm G-' + process.env.E2E_T3_GROUP!, exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  }
});
