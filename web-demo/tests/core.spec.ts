import { test, expect, type Page } from '@playwright/test';
const env = (name: string) => { const value = process.env[name]; if (!value) throw new Error('Missing test fixture: ' + name); return value; };
async function signIn(page: Page, email: string, password = env('E2E_PASSWORD')) {
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi' })).toBeVisible();
}
test('Quản trị viên đăng nhập, tra cứu tài khoản, audit và đăng xuất', async ({ page }) => {
  await signIn(page, env('E2E_ADMIN_EMAIL'));
  await page.screenshot({ path: 'test-results/dashboard.png', fullPage: true });
  await page.getByRole('button', { name: 'Tài khoản & phân quyền' }).click();
  await page.getByLabel('Tìm tên hoặc email').fill(env('E2E_STUDENT_EMAIL'));
  await expect(page.getByRole('cell', { name: new RegExp(env('E2E_STUDENT_EMAIL').replaceAll('.', '\\.')) })).toBeVisible();
  await page.getByRole('button', { name: 'Nhật ký hệ thống', exact: true }).click();
  await page.getByLabel('Mã sự kiện').fill('LOGIN'); await page.getByRole('button', { name: 'Lọc nhật ký' }).click();
  await expect(page.getByRole('cell', { name: 'LOGIN Thành công', exact: false }).first()).toBeVisible();
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  expect((await page.request.get('/api/v1/auth/me')).status()).toBe(401);
});
test('Sai mật khẩu có lỗi chung và khôi phục không tiết lộ email', async ({ page }) => {
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill('missing@example.test');
  await page.getByLabel('Mật khẩu', { exact: true }).fill('invalid-test-password');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Email, mật khẩu không đúng');
  await page.getByRole('button', { name: 'Quên mật khẩu?' }).click();
  await page.getByLabel('Email', { exact: true }).fill('missing@example.test');
  await page.getByRole('button', { name: 'Quên mật khẩu', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Nếu địa chỉ hợp lệ');
});
test('Kích hoạt lời mời và đăng nhập bằng tài khoản mới', async ({ page }) => {
  await page.goto('/accept-invitation#token=' + env('E2E_INVITATION_TOKEN'));
  await page.getByLabel('Họ và tên').fill('Sinh viên demo an toàn');
  await page.getByLabel('Mật khẩu mới', { exact: true }).fill(env('E2E_PASSWORD'));
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(env('E2E_PASSWORD'));
  await page.getByRole('button', { name: 'Kích hoạt tài khoản', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await signIn(page, env('E2E_INVITATION_EMAIL'));
  await expect(page.getByRole('button', { name: 'Nhật ký hệ thống' })).toHaveCount(0);
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
});
test('Đặt lại mật khẩu một lần, liên kết cũ bị từ chối', async ({ page }) => {
  const path = '/reset-password#token=' + env('E2E_RESET_TOKEN'); await page.goto(path);
  await page.getByLabel('Mật khẩu mới', { exact: true }).fill(env('E2E_NEW_PASSWORD'));
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(env('E2E_NEW_PASSWORD'));
  await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await signIn(page, env('E2E_RESET_EMAIL'), env('E2E_NEW_PASSWORD'));
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await page.goto(path); await page.getByLabel('Mật khẩu mới', { exact: true }).fill(env('E2E_NEW_PASSWORD'));
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(env('E2E_NEW_PASSWORD'));
  await page.getByRole('button', { name: 'Đặt lại mật khẩu', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Liên kết không hợp lệ');
});
test('Sinh viên bị chặn API quản trị và giao diện vừa màn hình di động', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await signIn(page, env('E2E_STUDENT_EMAIL'));
  expect((await page.request.get('/api/v1/admin/audit-logs')).status()).toBe(403);
  await expect(page.getByRole('button', { name: 'Tài khoản & phân quyền' })).toHaveCount(0);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: 'test-results/mobile.png', fullPage: true });
  const cookies = await page.context().cookies(); expect(cookies.find(cookie => cookie.name === 'SESSION')?.httpOnly).toBe(true);
});
test('Khóa, mở tài khoản và quản lý membership trên giao diện', async ({ page }) => {
  await signIn(page, env('E2E_ADMIN_EMAIL')); await page.getByRole('button', { name: 'Tài khoản & phân quyền' }).click();
  await page.getByLabel('Tìm tên hoặc email').fill(env('E2E_ENTERPRISE_EMAIL'));
  await page.getByRole('button', { name: 'Chi tiết', exact: true }).click();
  page.on('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Khóa tài khoản', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Mở khóa', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Mở khóa', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Khóa tài khoản', exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Vai trò mới', exact: true }).selectOption('ENTERPRISE');
  await page.getByRole('combobox', { name: 'Tổ chức', exact: true }).selectOption(env('E2E_ORG_B_ID'));
  await page.getByRole('button', { name: 'Cấp vai trò', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Thu hồi', exact: true })).toHaveCount(2);
  await page.getByRole('button', { name: 'Thu hồi', exact: true }).last().click();
  await expect(page.getByRole('button', { name: 'Thu hồi', exact: true })).toHaveCount(1);
});
test('Phiên bị thu hồi đưa người dùng về đăng nhập khi gọi API', async ({ page, browser }) => {
  const studentContext = await browser.newContext({ baseURL: 'http://127.0.0.1:5178' });
  const studentPage = await studentContext.newPage();
  try {
    await signIn(studentPage, env('E2E_STUDENT_EMAIL')); await signIn(page, env('E2E_ADMIN_EMAIL'));
    await page.getByRole('button', { name: 'Tài khoản & phân quyền' }).click();
    await page.getByLabel('Tìm tên hoặc email').fill(env('E2E_STUDENT_EMAIL'));
    await page.getByRole('button', { name: 'Chi tiết', exact: true }).click();
    await page.getByRole('button', { name: 'Thu hồi phiên đăng nhập', exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Đã thu hồi các phiên cũ');
    await studentPage.getByLabel('Mật khẩu hiện tại', { exact: true }).fill(env('E2E_PASSWORD'));
    await studentPage.getByLabel('Mật khẩu mới', { exact: true }).fill(env('E2E_NEW_PASSWORD'));
    await studentPage.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(env('E2E_NEW_PASSWORD'));
    await studentPage.getByRole('button', { name: 'Cập nhật mật khẩu', exact: true }).click();
    await expect(studentPage.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await expect(studentPage.getByRole('status')).toContainText('Phiên đã hết hạn hoặc bị thu hồi');
  } finally { await studentContext.close(); }
});
