import { test, expect, type Page } from '@playwright/test';

// Các test có nhãn “UI mock” chỉ kiểm tra trạng thái giao diện, không thay thế
// 7 luồng tích hợp backend thật trong core.spec.ts.
const fixture = (name: string) => {
  const value = process.env[name];
  if (!value) throw new Error('Missing test fixture: ' + name);
  return value;
};
const usersPage = (content: unknown[]) => ({ content, number: 0, totalElements: content.length, totalPages: content.length ? 1 : 0 });
const syntheticUser = (displayName: string, email: string) => ({
  userId: '7ee49e0f-bf31-4469-b02c-0edb19680d91', displayName, email,
  status: 'ACTIVE', lockedUntil: null, memberships: []
});
async function signIn(page: Page) {
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(fixture('E2E_ADMIN_EMAIL'));
  await page.getByLabel('Mật khẩu', { exact: true }).fill(fixture('E2E_PASSWORD'));
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
}
async function navigate(page: Page, title: string) {
  const menu = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await menu.isVisible() && await menu.getAttribute('aria-expanded') !== 'true') await menu.click();
  await page.getByRole('button', { name: title, exact: true }).click();
  await expect(page.getByRole('heading', { name: title, exact: true }).first()).toBeVisible();
}
async function expectNoPageOverflow(page: Page) {
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(1);
}
async function waitForScreenData(page: Page, title: string) {
  // Chờ nội dung đã render; networkidle có thể còn trạng thái cũ khi chuyển tab SPA.
  if (title !== 'Tài khoản của tôi') await expect(page.getByRole('table').first()).toBeVisible();
}

test('UX: hiện mật khẩu có trạng thái truy cập và đăng nhập được bằng bàn phím', async ({ page }) => {
  await page.goto('/');
  const email = page.getByLabel('Email', { exact: true });
  const password = page.getByLabel('Mật khẩu', { exact: true });
  await email.fill(fixture('E2E_ADMIN_EMAIL'));
  await password.fill(fixture('E2E_PASSWORD'));
  const show = page.getByRole('button', { name: 'Hiện mật khẩu', exact: true });
  await show.focus();
  await page.keyboard.press('Enter');
  await expect(password).toHaveAttribute('type', 'text');
  const hide = page.getByRole('button', { name: 'Ẩn mật khẩu', exact: true });
  await expect(hide).toHaveAttribute('aria-pressed', 'true');
  await page.keyboard.press('Enter');
  await expect(password).toHaveAttribute('type', 'password');
  await expect(show).toHaveAttribute('aria-pressed', 'false');
  await password.focus();
  await page.keyboard.press('Enter');
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
});

test('UX: menu di động mở, đóng bằng Escape và đóng sau khi chọn chức năng', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await signIn(page);
  const open = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  await expect(open).toHaveAttribute('aria-expanded', 'false');
  await open.click();
  await expect(open).toHaveAttribute('aria-expanded', 'true');
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính' })).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(open).toHaveAttribute('aria-expanded', 'false');
  await open.click();
  await page.getByRole('button', { name: 'Tài khoản & phân quyền', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản & phân quyền', exact: true })).toBeVisible();
  await expect(open).toHaveAttribute('aria-expanded', 'false');
  await open.click();
  await page.getByRole('button', { name: 'Đóng menu điều hướng', exact: true }).click();
  await expect(open).toHaveAttribute('aria-expanded', 'false');
});

test('UX: bốn màn quản trị vừa màn hình 320px và 390px', async ({ page }) => {
  await signIn(page);
  const screens = [
    { title: 'Tài khoản của tôi', file: 'account' },
    { title: 'Tài khoản & phân quyền', file: 'users' },
    { title: 'Tổ chức & lời mời', file: 'organizations' },
    { title: 'Nhật ký hệ thống', file: 'audit' }
  ];
  for (const screen of screens.slice(1)) {
    await navigate(page, screen.title);
    await waitForScreenData(page, screen.title);
    await page.screenshot({ path: `test-results/${screen.file}-desktop.png`, fullPage: true });
  }
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    for (const screen of screens) {
      await navigate(page, screen.title);
      await waitForScreenData(page, screen.title);
      await expectNoPageOverflow(page);
      if (width === 390 && screen.file !== 'account') await page.screenshot({ path: `test-results/${screen.file}-mobile.png`, fullPage: true });
    }
  }
});

test('UX UI mock: kết quả tìm kiếm cũ không ghi đè truy vấn mới', async ({ page }) => {
  await signIn(page);
  let releaseOld!: () => void;
  let oldStarted!: () => void;
  const oldGate = new Promise<void>(resolve => { releaseOld = resolve; });
  const started = new Promise<void>(resolve => { oldStarted = resolve; });
  await page.route('**/api/v1/admin/users?**', async route => {
    const search = new URL(route.request().url()).searchParams.get('search');
    if (search === 'old-search') {
      oldStarted(); await oldGate;
      await route.fulfill({ json: usersPage([syntheticUser('Kết quả đã cũ', 'old-result@example.test')]) });
    } else if (search === 'new-search') {
      await route.fulfill({ json: usersPage([syntheticUser('Kết quả mới nhất', 'new-result@example.test')]) });
    } else await route.continue();
  });
  try {
    await navigate(page, 'Tài khoản & phân quyền');
    const search = page.getByLabel('Tìm tên hoặc email');
    await search.fill('old-search');
    await started;
    await search.fill('new-search');
    await expect(page.getByRole('cell', { name: /Kết quả mới nhất/ })).toBeVisible();
    const oldResponse = page.waitForResponse(response => new URL(response.url()).searchParams.get('search') === 'old-search');
    releaseOld();
    await (await oldResponse).finished();
    // Cho React hoàn tất callback sau response bị đảo thứ tự, tránh kiểm tra quá sớm.
    await page.waitForTimeout(150);
    await expect(page.getByRole('cell', { name: /Kết quả mới nhất/ })).toBeVisible();
    await expect(page.getByRole('cell', { name: /Kết quả đã cũ/ })).toHaveCount(0);
  } finally { releaseOld(); }
});

test('UX: liên kết kích hoạt và đặt lại thiếu mã giải thích được cách tiếp tục', async ({ page }) => {
  for (const [path, title] of [['/reset-password', 'Đặt lại mật khẩu'], ['/accept-invitation', 'Kích hoạt tài khoản']]) {
    await page.goto(path);
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: title, exact: true })).toBeDisabled();
    await expect(page.locator('main')).toContainText(/mã xác nhận|thiếu mã|mở lại liên kết/i);
    await page.getByRole('button', { name: 'Quay lại đăng nhập', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  }
  expect(await page.evaluate(() => localStorage.length)).toBe(0);
});

test('UX UI mock: danh sách rỗng có hướng dẫn, lỗi tải có thể thử lại', async ({ page }) => {
  await signIn(page);
  let attempts = 0;
  let failing = true;
  await page.route('**/api/v1/admin/users?**', route => {
    attempts += 1;
    return failing
      ? route.fulfill({ status: 503, json: { error: 'SERVICE_UNAVAILABLE' } })
      : route.fulfill({ json: usersPage([]) });
  });
  await navigate(page, 'Tài khoản & phân quyền');
  await expect(page.getByRole('status')).toContainText('Không thể xử lý yêu cầu');
  failing = false;
  await page.getByRole('status').getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByText('Không có tài khoản phù hợp', { exact: false })).toBeVisible();
  expect(attempts).toBeGreaterThanOrEqual(2);
});

test('UX UI mock: yêu cầu đăng nhập chậm chặn gửi trùng và khôi phục sau lỗi', async ({ page }) => {
  let release!: () => void;
  let requestStarted!: () => void;
  let attempts = 0;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const started = new Promise<void>(resolve => { requestStarted = resolve; });
  await page.route('**/api/v1/auth/login', async route => {
    attempts += 1; requestStarted(); await gate;
    await route.fulfill({ status: 503, json: { error: 'SERVICE_UNAVAILABLE' } });
  });
  try {
    await page.goto('/');
    await page.getByLabel('Email', { exact: true }).fill('synthetic-loading@example.test');
    const password = page.getByLabel('Mật khẩu', { exact: true });
    await password.fill('Synthetic-loading-password');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await started;
    await expect(page.getByRole('button', { name: /Đang xử lý|Đang đăng nhập/ })).toBeDisabled();
    // Submit lặp vẫn bị chặn kể cả khi sự kiện được phát trực tiếp lúc input disabled.
    await page.locator('form').dispatchEvent('submit');
    await page.waitForTimeout(150);
    expect(attempts).toBe(1);
    release();
    await expect(page.getByRole('status')).toContainText('Không thể xử lý yêu cầu');
    await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeEnabled();
  } finally { release(); }
});
