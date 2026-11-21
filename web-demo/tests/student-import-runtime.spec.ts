import { expect, test } from '@playwright/test';

// Dedicated backend harness with real MySQL/ClamAV/Mailpit; no routes are intercepted.
test.skip(!process.env.IMPORT_E2E, 'Requires the isolated student import backend harness');
test('school imports one valid row, resends mail, and student activates the same bound roster', async ({ page, browser, request }) => {
  test.setTimeout(120000);
  await page.goto('/');await page.getByLabel('Email', { exact: true }).fill(process.env.IMPORT_STAFF_EMAIL!);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(process.env.IMPORT_PASSWORD!);await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await page.getByRole('heading', { name: 'Tổng quan Nhà trường', exact: true }).waitFor({ state: 'visible' });
  const menu = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await menu.isVisible()) await menu.click();
  const navigation = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await navigation.getByRole('button', { name: 'Sinh viên', exact: true }).click();
  await navigation.getByRole('button', { name: 'Danh sách & kích hoạt', exact: true }).click();
  const panel = page.getByRole('region', { name: 'Nhập danh sách & Kích hoạt sinh viên', exact: true });await expect(panel).toBeVisible();
  await panel.getByLabel('Kỳ nhập danh sách', { exact: true }).selectOption(process.env.IMPORT_TERM!);
  const csv = 'studentCode,email,fullName,majorCode,termCode\n000UI,' + process.env.IMPORT_STUDENT_EMAIL + ',Synthetic UI Student,IT,' + process.env.IMPORT_TERM_CODE + '\n,bad,Invalid Name,IT,WRONG\n';
  await panel.getByLabel('Tệp danh sách sinh viên', { exact: true }).setInputFiles({ name: 'synthetic-students.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await panel.getByRole('button', { name: 'Kiểm tra và xem trước', exact: true }).click();
  await expect(panel.getByRole('heading', { name: 'Chờ xác nhận', exact: true })).toBeVisible();await expect(panel.getByLabel('Chọn dòng 3')).toBeDisabled();
  await panel.getByLabel('Chọn dòng 2').check();
  await panel.getByRole('button', { name: 'Xác nhận nhập 1 dòng', exact: true }).click();
  await page.getByRole('dialog', { name: 'Nhập danh sách sinh viên?', exact: true }).getByRole('button', { name: 'Nhập 1 dòng', exact: true }).click();
  await expect(panel.getByRole('heading', { name: 'Nhập danh sách hoàn tất', exact: true })).toBeVisible({ timeout: 45000 });
  await expect(panel.getByText('Chờ kích hoạt', { exact: true })).toBeVisible();
  await panel.getByRole('button', { name: 'Gửi lại thư kích hoạt', exact: true }).click();await expect(panel.getByText('Đã xếp hàng thư kích hoạt mới.', { exact: true })).toBeVisible();
  let text = '';
  await expect.poll(async () => {
    const response = await request.get(process.env.IMPORT_MAILPIT + '/api/v1/messages');
    const messages = (await response.json()).messages as { ID: string }[];
    for (const summary of messages) {
      const message = await (await request.get(process.env.IMPORT_MAILPIT + '/api/v1/message/' + summary.ID)).json();
      if (JSON.stringify(message.To).includes(process.env.IMPORT_STUDENT_EMAIL!) && /#token=/.test(message.Text)) { text = message.Text; return true; }
    }
    return false;
  }, { timeout: 45000 }).toBeTruthy();
  const token = /#token=([A-Za-z0-9_-]+)/.exec(text)?.[1];expect(Boolean(token)).toBeTruthy();
  const context = await browser.newContext({ baseURL: process.env.IMPORT_BROWSER_URL });const student = await context.newPage();
  try {
    await student.goto('/accept-invitation#token=' + token);await student.getByLabel('Họ và tên').fill('Synthetic UI Student');
    await student.getByLabel('Mật khẩu mới', { exact: true }).fill(process.env.IMPORT_PASSWORD!);await student.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(process.env.IMPORT_PASSWORD!);
    await student.getByRole('button', { name: 'Kích hoạt tài khoản', exact: true }).click();await expect(student.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await panel.getByRole('button', { name: 'Tải lại tiến độ', exact: true }).click();await expect(panel.getByText('Đã kích hoạt', { exact: true })).toBeVisible();
    expect(await student.evaluate(() => localStorage.length)).toBe(0);
  } finally { await context.close(); }
});
