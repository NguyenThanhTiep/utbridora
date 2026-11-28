import { expect, test, type Page } from '@playwright/test';

// Isolated browser contracts; live account/token coverage stays in core.spec.ts.
const sessionExpired = 'Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.';
const password = 'Mật-khẩu-mới-an-toàn';
const tokenFlows = [
  { path: '/reset-password', endpoint: '/auth/password/reset', title: 'Mật khẩu mới', submit: 'Lưu mật khẩu mới', overline: 'BƯỚC 03 / ĐẶT LẠI', validity: '30 phút' },
  { path: '/accept-invitation', endpoint: '/auth/invitations/accept', title: 'Kích hoạt tài khoản', submit: 'Kích hoạt tài khoản', overline: 'TÀI KHOẢN / LỜI MỜI', validity: '48 giờ' },
];

async function anonymous(page: Page, path = '/') {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/auth/me') return route.fulfill({ status: 401, json: { error: 'SESSION_EXPIRED' } });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'browser-test-csrf' } });
    if (path === '/auth/me/permissions') return route.fulfill({ json: [] });
    return route.fulfill({ status: 404, json: { error: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto(path);
  await expect(page.locator('.auth-form')).toBeVisible();
}

async function fillTokenForm(page: Page, path: string, nextPassword = password) {
  if (path === '/accept-invitation') await page.getByLabel('Họ và tên', { exact: true }).fill('Người được mời');
  await page.getByLabel('Mật khẩu mới', { exact: true }).fill(nextPassword);
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(nextPassword);
}

test('login opens directly with the designed brand panel, header and card, and an accessible password toggle', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await anonymous(page);
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Quay lại', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Chọn vai trò/ })).toHaveCount(0);
  await expect(page.locator('.auth-story')).toHaveCSS('background-image', 'linear-gradient(94deg, rgb(16, 47, 61) 0%, rgb(12, 76, 79) 92%)');
  const story = await page.locator('.auth-story').boundingBox();
  const card = await page.locator('.auth-card').boundingBox();
  expect(story!.width).toBeCloseTo(720, 0);
  expect(card!.width).toBeCloseTo(544, 0);
  const entry = (await page.locator('.auth-entry').boundingBox())!;
  expect(entry.width).toBeCloseTo(story!.width, 0);
  const leftMargin = card!.x - entry.x;
  const rightMargin = entry.x + entry.width - card!.x - card!.width;
  expect(leftMargin).toBeCloseTo(rightMargin, 0);
  expect(leftMargin).toBeLessThan(entry.width * .15);
  const header = (await page.locator('.auth-entry-header').boundingBox())!;
  expect(card!.y).toBeGreaterThanOrEqual(header.y + header.height + 16);
  expect(card!.y + card!.height).toBeLessThanOrEqual(900);
  await expect(page.locator('.auth-entry-header')).toBeVisible();
  await expect(page.locator('.connection-title')).toHaveText('MỘT NỀN TẢNG CHUNG');
  await expect(page.locator('.auth-card')).toHaveCSS('border-top-color', 'rgb(227, 234, 242)');
  await expect(page.locator('.auth-form-icon')).toHaveCSS('border-radius', '50%');
  const email = page.getByLabel('Email', { exact: true });
  await expect(email).toHaveAttribute('placeholder', 'Nhập địa chỉ email');
  await email.focus();
  await expect(email).toHaveCSS('border-top-color', 'rgb(11, 146, 124)');
  await expect(email).toHaveCSS('box-shadow', 'rgb(224, 242, 254) 0px 0px 0px 3px');
  const input = page.getByLabel('Mật khẩu', { exact: true });
  await input.fill(password);
  await page.getByRole('button', { name: 'Hiện mật khẩu', exact: true }).focus();
  await page.keyboard.press('Enter');
  await expect(input).toHaveAttribute('type', 'text');
  await expect(page.getByRole('button', { name: 'Ẩn mật khẩu', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.keyboard.press('Enter');
  await expect(input).toHaveAttribute('type', 'password');
  const showPassword = page.getByRole('checkbox', { name: 'Hiện mật khẩu', exact: true });
  await expect(showPassword).toHaveCSS('height', '18px');
  await showPassword.check();
  await expect(input).toHaveAttribute('type', 'text');
  await expect(page.getByRole('button', { name: 'Ẩn mật khẩu', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await page.getByRole('button', { name: 'Ẩn mật khẩu', exact: true }).click();
  await expect(showPassword).not.toBeChecked();
  await expect(input).toHaveAttribute('type', 'password');
  const submit = page.getByRole('button', { name: 'Đăng nhập', exact: true });
  await submit.hover();
  await expect(submit).toHaveCSS('background-color', 'rgb(17, 170, 145)');
  await email.fill(''); await input.fill('');
  await page.mouse.move(0, 0);
  await page.screenshot({ path: 'test-results/auth-desktop.png', fullPage: true });
});

test('login fits common desktop screens without vertical scrolling or hiding its controls', async ({ page }) => {
  await anonymous(page);
  for (const [width, height] of [[1920, 1080], [1440, 900], [1536, 694], [1366, 768], [1280, 720], [1024, 768], [1366, 650], [1280, 600], [1024, 600]]) {
    await page.setViewportSize({ width, height });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollHeight - innerHeight), { message: `${width}x${height}: login should fit vertically` }).toBeLessThanOrEqual(1);
    for (const selector of ['.auth-fields', '.auth-submit', '.auth-help-button', '.connection-art', '.auth-invitation-note', '.auth-entry-footer', '.auth-copyright', '.auth-protected', '.auth-story-footer']) {
      await expect(page.locator(selector)).toBeVisible();
      const bounds = (await page.locator(selector).boundingBox())!;
      expect(bounds.y).toBeGreaterThanOrEqual(0);
      expect(bounds.y + bounds.height).toBeLessThanOrEqual(height);
    }
  }
});

test('the enlarged school introduction stays centered with aligned branding and a larger diagram', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await anonymous(page);
  const story = (await page.locator('.auth-story').boundingBox())!;
  const content = (await page.locator('.auth-story-content').boundingBox())!;
  for (const selector of ['.auth-story > .auth-brand', '.connection-art', '.auth-story-footer']) {
    const bounds = (await page.locator(selector).boundingBox())!;
    expect(bounds.x).toBeCloseTo(content.x, 0);
    expect(bounds.width).toBeCloseTo(content.width, 0);
  }
  expect(content.x - story.x).toBeCloseTo(story.x + story.width - content.x - content.width, 0);
  expect(content.width).toBeGreaterThan(story.width * .75);
  expect((await page.locator('.connection-art').boundingBox())!.height).toBeGreaterThan(350);
  expect((await page.locator('.connection-map').boundingBox())!.width).toBeGreaterThanOrEqual(500);
  expect((await page.locator('.connection-center').boundingBox())!.width).toBeGreaterThan(100);
});

test('access help works on all four screens, closes with Escape and returns focus on desktop and mobile', async ({ page }) => {
  await anonymous(page);
  for (const width of [1440, 320]) {
    await page.setViewportSize({ width, height: 900 });
    for (const path of ['/', '/forgot', '/reset-password', '/accept-invitation']) {
      await page.goto(path === '/forgot' ? '/' : path);
      if (path === '/forgot') await page.getByRole('button', { name: 'Quên mật khẩu?', exact: true }).click();
      const help = page.getByRole('button', { name: 'Cần hỗ trợ?', exact: true });
      await help.click();
      const dialog = page.getByRole('dialog', { name: 'Hỗ trợ truy cập', exact: true });
      await expect(dialog).toBeVisible();
      await expect(dialog).toContainText('quản trị viên đã cấp tài khoản');
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
      await page.keyboard.press('Escape');
      await expect(dialog).not.toBeVisible();
      await expect(help).toBeFocused();
      await help.click();
      await dialog.getByRole('button', { name: 'Đóng', exact: true }).click();
      await expect(dialog).not.toBeVisible();
    }
  }
});

test('diagram keeps the enterprise label inside its frame and clear of the central hub at different widths and text sizes', async ({ page }) => {
  await anonymous(page);
  for (const width of [768, 900, 1024, 1440, 1920]) {
    await page.setViewportSize({ width, height: 1000 });
    for (const fontSize of ['16px', '20px']) {
      await page.evaluate(size => { document.documentElement.style.fontSize = size; }, fontSize);
      const frame = (await page.locator('.connection-art').boundingBox())!;
      const enterprise = (await page.locator('.node-enterprise').boundingBox())!;
      const label = (await page.locator('.node-enterprise b').boundingBox())!;
      const hub = (await page.locator('.connection-center').boundingBox())!;
      const distances: number[] = [];
      const vectors: { x: number; y: number }[] = [];
      for (const node of ['student', 'school', 'enterprise']) {
        const circle = (await page.locator('.node-' + node + ' > span').boundingBox())!;
        const vector = { x: circle.x + circle.width / 2 - hub.x - hub.width / 2, y: circle.y + circle.height / 2 - hub.y - hub.height / 2 };
        vectors.push(vector);
        distances.push(Math.hypot(vector.x, vector.y));
        expect(circle.width).toEqual(circle.height);
      }
      expect(Math.max(...distances) - Math.min(...distances)).toBeLessThan(1);
      for (let index = 0; index < 3; index++) {
        const next = (index + 1) % 3;
        expect((vectors[index].x * vectors[next].x + vectors[index].y * vectors[next].y) / (distances[index] * distances[next])).toBeCloseTo(-.5, 2);
      }
      const student = (await page.locator('.node-student > span').boundingBox())!;
      const school = (await page.locator('.node-school > span').boundingBox())!;
      const studentLabel = (await page.locator('.node-student b').boundingBox())!;
      const schoolLabel = (await page.locator('.node-school b').boundingBox())!;
      expect(studentLabel.x + studentLabel.width).toBeLessThan(hub.x - 2);
      expect(schoolLabel.x).toBeGreaterThan(hub.x + hub.width + 2);
      expect(school.x + school.width - student.x).toBeGreaterThan(hub.width * 2.5);
      expect(student.x).toBeGreaterThan(frame.x + 12);
      expect(school.x + school.width).toBeLessThan(frame.x + frame.width - 12);
      expect(label.y + label.height).toBeLessThanOrEqual(frame.y + frame.height - 16);
      await expect(page.locator('.connection-caption')).toHaveCount(0);
      expect(label.x).toBeGreaterThanOrEqual(frame.x);
      expect(label.x + label.width).toBeLessThanOrEqual(frame.x + frame.width);
      expect(enterprise.y).toBeGreaterThan(hub.y + hub.height + 6);
    }
  }
});

test('connection illustration animates without shifting the hub and respects reduced motion', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await anonymous(page);
  const art = page.locator('.connection-art');
  const running = () => art.evaluate(element => element.getAnimations({ subtree: true }).filter(animation => animation.playState === 'running').length);
  await expect.poll(running).toBeGreaterThan(0);
  const hub = page.locator('.connection-center');
  const before = await hub.boundingBox();
  await expect.poll(() => art.evaluate(element => element.getAnimations({ subtree: true }).some(animation => Number(animation.currentTime) > 150))).toBe(true);
  expect(await hub.boundingBox()).toEqual(before);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect.poll(running).toBe(0);
  await expect(page.getByLabel('Email', { exact: true })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeEnabled();
});

test('headline cycles without controls or layout shifts and respects reduced motion', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await page.clock.install();
  await anonymous(page);
  const lines = page.locator('.auth-rotating-copy .auth-title-line');
  const heading = page.locator('.auth-story h1');
  await expect(lines.first()).toHaveText('Kết nối tiềm năng');
  const bounds = await heading.boundingBox();
  await page.clock.runFor(3400);
  await expect(lines.first()).toHaveText('Học từ trải nghiệm');
  expect(await heading.boundingBox()).toEqual(bounds);
  await expect(page.locator('.auth-rotating-message button')).toHaveCount(0);
  await page.clock.runFor(3400);
  await expect(lines.first()).toHaveText('Kết nối nhà trường');
  await page.clock.runFor(3400);
  await expect(lines.first()).toHaveText('Khám phá cơ hội');
  await page.clock.runFor(3400);
  await expect(lines.first()).toHaveText('Kết nối tiềm năng');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(page.locator('.auth-rotating-message')).toHaveAttribute('data-rotating', 'false');
  await page.clock.runFor(15000);
  await expect(lines.first()).toHaveText('Kết nối tiềm năng');
  await expect(page.getByLabel('Email', { exact: true })).toBeEnabled();
});

test('login keeps its API payload, disables controls while pending and prevents duplicate submission', async ({ page }) => {
  await anonymous(page);
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  const requests: unknown[] = [];
  await page.route('**/api/v1/auth/login', async route => {
    requests.push(route.request().postDataJSON());
    expect(route.request().method()).toBe('POST');
    expect(route.request().headers()['x-csrf-token']).toBe('browser-test-csrf');
    await pending;
    await route.fulfill({ status: 401, json: { error: 'INVALID_CREDENTIALS' } });
  });
  await page.getByLabel('Email', { exact: true }).fill('login@example.test');
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  try {
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page.locator('.auth-form')).toHaveAttribute('aria-busy', 'true');
    await expect(page.getByRole('button', { name: 'Đang xử lý…', exact: true })).toBeDisabled();
    await expect(page.getByLabel('Email', { exact: true })).toBeDisabled();
    await expect(page.getByLabel('Mật khẩu', { exact: true })).toBeDisabled();
    await expect(page.getByRole('checkbox', { name: 'Hiện mật khẩu', exact: true })).toBeDisabled();
    await expect(page.getByRole('button', { name: 'Quên mật khẩu?', exact: true })).toBeDisabled();
    await page.locator('.auth-form').evaluate(form => (form as HTMLFormElement).requestSubmit());
    await expect.poll(() => requests.length).toBe(1);
  } finally { release(); }
  await expect(page.getByRole('status')).toContainText('Email, mật khẩu không đúng');
  await expect(page.getByRole('status')).toHaveCSS('background-color', 'rgb(255, 240, 240)');
  await expect(page.locator('.auth-form')).toHaveAttribute('aria-busy', 'false');
  await expect(page.getByRole('button', { name: 'Đăng nhập', exact: true })).toBeEnabled();
  expect(requests).toEqual([{ email: 'login@example.test', password }]);
});

test('forgot password submits the email and preserves the non-disclosing confirmation', async ({ page }) => {
  await anonymous(page);
  await page.getByRole('button', { name: 'Quên mật khẩu?', exact: true }).click();
  await expect(page.getByText('BƯỚC 01 / YÊU CẦU', { exact: true })).toBeVisible();
  await expect(page.locator('.auth-guidance')).toContainText('không xác nhận email có tồn tại trong hệ thống');
  await page.route('**/api/v1/auth/password/forgot', route => {
    expect(route.request().method()).toBe('POST');
    expect(route.request().postDataJSON()).toEqual({ email: 'forgot@example.test' });
    return route.fulfill({ status: 204 });
  });
  await page.getByLabel('Email', { exact: true }).fill('forgot@example.test');
  await page.getByRole('button', { name: 'Gửi hướng dẫn', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('Nếu địa chỉ hợp lệ');
  await expect(page.getByRole('status')).toHaveCSS('background-color', 'rgb(229, 246, 237)');
  await page.getByRole('button', { name: 'Quay lại đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
});

for (const flow of tokenFlows) {
  test(`${flow.path} keeps its token in memory and sends the existing API payload`, async ({ page }) => {
    await anonymous(page, flow.path + '#token=browser-test-token');
    await expect(page).toHaveURL(flow.path);
    await expect(page.getByText(flow.overline, { exact: true })).toBeVisible();
    await expect(page.locator('.auth-guidance')).toContainText(flow.validity);
    await page.route('**/api/v1' + flow.endpoint, route => {
      expect(route.request().method()).toBe('POST');
      expect(route.request().postDataJSON()).toEqual(flow.path === '/reset-password'
        ? { token: 'browser-test-token', newPassword: password }
        : { token: 'browser-test-token', displayName: 'Người được mời', password });
      return route.fulfill({ status: 204 });
    });
    await fillTokenForm(page, flow.path);
    await page.getByRole('button', { name: flow.submit, exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await expect(page.getByRole('status')).toContainText('Thành công. Vui lòng đăng nhập bằng mật khẩu mới.');
    await expect(page.getByLabel('Mật khẩu', { exact: true })).toHaveValue('');
    expect(await page.evaluate(() => localStorage.length + sessionStorage.length)).toBe(0);
  });

  test(`${flow.path} without a token explains how to continue and blocks submission`, async ({ page }) => {
    await anonymous(page, flow.path);
    await expect(page.getByRole('heading', { name: flow.title, exact: true })).toBeVisible();
    await expect(page.getByRole('status')).toContainText('Liên kết thiếu mã xác nhận');
    await expect(page.getByRole('status')).toHaveCSS('background-color', 'rgb(255, 242, 219)');
    await expect(page.getByRole('button', { name: flow.submit, exact: true })).toBeDisabled();
    await page.getByRole('button', { name: 'Quay lại đăng nhập', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  });

  test(`${flow.path} reports an expired or used link from the server without reporting success`, async ({ page }) => {
    await anonymous(page, flow.path + '#token=expired-browser-test-token');
    await page.route('**/api/v1' + flow.endpoint, route => route.fulfill({ status: 400, json: { error: 'INVALID_REQUEST' } }));
    await fillTokenForm(page, flow.path);
    await page.getByRole('button', { name: flow.submit, exact: true }).click();
    await expect(page.getByRole('status')).toContainText('Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng.');
    await expect(page.getByRole('status')).toHaveCSS('color', 'rgb(183, 110, 0)');
    await expect(page.getByRole('heading', { name: flow.title, exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: flow.submit, exact: true })).toBeEnabled();
    await expect(page.getByLabel('Mật khẩu mới', { exact: true })).toHaveValue(password);
  });
}

test('reset validates Unicode characters, UTF-8 bytes and confirmation before sending', async ({ page }) => {
  await anonymous(page, '/reset-password#token=browser-test-token');
  const requests: unknown[] = [];
  await page.route('**/api/v1/auth/password/reset', route => {
    requests.push(route.request().postDataJSON());
    return route.fulfill({ status: 204 });
  });
  const submit = page.getByRole('button', { name: 'Lưu mật khẩu mới', exact: true });
  // Eleven supplementary Unicode characters use 22 UTF-16 code units, but remain too short.
  for (const invalid of ['🔒'.repeat(11), '界'.repeat(25)]) {
    await fillTokenForm(page, '/reset-password', invalid);
    await submit.click();
    await expect(page.getByRole('status')).toContainText('ít nhất 12 ký tự, tối đa 72 byte UTF-8');
    expect(requests).toEqual([]);
  }
  await fillTokenForm(page, '/reset-password');
  await page.getByLabel('Xác nhận mật khẩu', { exact: true }).fill(password + '!');
  await submit.click();
  await expect(page.getByRole('status')).toContainText('Mật khẩu xác nhận chưa khớp.');
  expect(requests).toEqual([]);
  // Twenty-four three-byte Unicode characters sit exactly on the 72-byte upper bound.
  await fillTokenForm(page, '/reset-password', '界'.repeat(24));
  await submit.click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  expect(requests).toEqual([{ token: 'browser-test-token', newPassword: '界'.repeat(24) }]);
});

test('a successful login restores its account and session expiry returns to the login form', async ({ page }) => {
  await anonymous(page);
  await page.route('**/api/v1/auth/login', route => route.fulfill({ json: {
    userId: 'browser-test-user', email: 'login@example.test', displayName: 'Người dùng từ API', memberships: [],
  } }));
  await page.getByLabel('Email', { exact: true }).fill('login@example.test');
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
  await page.evaluate(() => window.dispatchEvent(new Event('session-expired')));
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await expect(page.getByRole('status')).toHaveText(sessionExpired);
  await expect(page.getByRole('status')).toHaveCSS('background-color', 'rgb(255, 242, 219)');
});

for (const width of [320, 390]) test(`phone access form keeps touch targets and remains usable with a short viewport at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 520 });
  await anonymous(page);
  for (const label of ['Email', 'Mật khẩu']) await expect(page.getByLabel(label, { exact: true })).toHaveCSS('font-size', '16px');
  const toggle = page.getByRole('button', { name: 'Hiện mật khẩu', exact: true });
  const bounds = (await toggle.boundingBox())!;
  expect(bounds.width).toBeGreaterThanOrEqual(44); expect(bounds.height).toBeGreaterThanOrEqual(44);
  const submit = page.getByRole('button', { name: 'Đăng nhập', exact: true });
  await submit.scrollIntoViewIfNeeded();
  expect((await submit.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  await page.getByRole('button', { name: 'Cần hỗ trợ?', exact: true }).click();
  const dialog = page.getByRole('dialog', { name: 'Hỗ trợ truy cập', exact: true });
  await expect(dialog).toBeVisible();
  const popup = (await dialog.boundingBox())!;
  expect(popup.x).toBeGreaterThanOrEqual(0); expect(popup.x + popup.width).toBeLessThanOrEqual(width); expect(popup.height).toBeLessThanOrEqual(488);
  await page.keyboard.press('Escape');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('all four access screens fit desktop, tablet and small phones without horizontal scrolling', async ({ page }) => {
  await anonymous(page);
  for (const width of [1440, 768, 390, 320]) {
    await page.setViewportSize({ width, height: 900 });
    for (const path of ['/', '/forgot', '/reset-password#token=layout-test', '/accept-invitation#token=layout-test']) {
      await page.goto(path === '/forgot' ? '/' : path);
      if (path === '/forgot') await page.getByRole('button', { name: 'Quên mật khẩu?', exact: true }).click();
      await expect(page.locator('.auth-form')).toBeVisible();
      await expect(page.locator('.auth-submit')).toBeVisible();
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
      const card = await page.locator('.auth-card').boundingBox();
      expect(card!.x).toBeGreaterThanOrEqual(0);
      expect(card!.x + card!.width).toBeLessThanOrEqual(width);
      if (width < 701) await expect(page.locator('.auth-story')).toBeHidden();
      else await expect(page.locator('.auth-story')).toBeVisible();
      if (width === 390 && path === '/') await page.screenshot({ path: 'test-results/auth-mobile.png', fullPage: true });
      if (width === 1440 && path !== '/') await page.screenshot({ path: `test-results/auth-${path.split('/')[1].split('#')[0]}-desktop.png`, fullPage: true });
    }
  }
});

test('login floating labels, eye toggle, error shake and morphing pill CTA animate cleanly', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await anonymous(page);

  const controls = page.locator('.auth-form .input-control.is-float');
  const emailControl = controls.nth(0);
  const pwControl = controls.nth(1);
  const emailInput = page.getByLabel('Email', { exact: true });
  const pwInput = page.getByLabel('Mật khẩu', { exact: true });

  await expect(emailControl).not.toHaveClass(/\bis-up\b/);
  await emailInput.focus();
  await expect(emailControl).toHaveClass(/\bis-f\b/);
  await expect(emailControl).toHaveClass(/\bis-up\b/);
  await emailInput.fill('invalid-email');
  await pwInput.focus();
  await expect(emailControl).not.toHaveClass(/\bis-f\b/);
  await expect(emailControl).toHaveClass(/\bis-up\b/);

  const toggle = page.getByRole('button', { name: 'Hiện mật khẩu', exact: true });
  await expect(toggle.locator('.u-eye-lid')).toBeVisible();
  await expect(toggle.locator('.u-eye-lash')).toBeVisible();
  await toggle.click();
  await expect(page.getByRole('button', { name: 'Ẩn mật khẩu', exact: true })).toHaveAttribute('aria-pressed', 'true');
  await expect(pwInput).toHaveAttribute('type', 'text');

  // Client validation flags & shakes the malformed email field and clears once edited.
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(emailControl).toHaveClass(/\bis-err\b/);
  await expect(emailControl).toHaveClass(/\bis-shake\b/);
  await expect(emailInput).toHaveAttribute('aria-invalid', 'true');
  await emailInput.fill('login@example.test');
  await expect(emailControl).not.toHaveClass(/\bis-err\b/);

  // Backend 401 shakes credential fields; next valid submit morphs CTA to 48px pill then green check.
  let failOnce = true;
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  await page.route('**/api/v1/auth/login', async route => {
    if (failOnce) {
      failOnce = false;
      return route.fulfill({ status: 401, json: { error: 'INVALID_CREDENTIALS' } });
    }
    await pending;
    return route.fulfill({
      json: { userId: 'browser-test-user', email: 'login@example.test', displayName: 'Người dùng thử', memberships: [] },
    });
  });

  await pwInput.fill('wrong-password');
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(pwControl).toHaveClass(/\bis-err\b/);
  await expect(pwControl).toHaveClass(/\bis-shake\b/);
  await expect(page.getByRole('status')).toContainText('Email, mật khẩu không đúng');

  await pwInput.fill(password);
  const submitBtn = page.locator('.auth-submit');
  try {
    await submitBtn.click();
    await expect(submitBtn).toHaveClass(/\bis-busy\b/);
    await expect.poll(async () => Math.round((await submitBtn.boundingBox())!.width)).toBe(48);
    await expect(submitBtn).toHaveCSS('border-radius', '24px');
  } finally { release(); }

  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
});

test('constellation mesh #341 renders inside the teal frame, responds to pointer/keyboard bursts, themes, and reduced motion', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await anonymous(page);

  const root = page.locator('.connection-art .u341');
  const canvas = root.locator('.u341-cv');
  const counter = root.locator('.u341-ct');
  const title = page.locator('.connection-title');

  await expect(canvas).toBeVisible();
  await expect(canvas).toHaveAttribute('role', 'img');
  await expect(canvas).toHaveAttribute('aria-label', /Mạng lưới kết nối tương tác/);
  await expect(root).toHaveAttribute('data-node-count', '72');
  await expect(counter).toHaveText(/^\d+ kết nối$/);

  // Verify uniform teal tokens inside auth-story frame.
  const storyTokens = await root.evaluate(el => {
    const s = getComputedStyle(el);
    return {
      bg: s.getPropertyValue('--u341-bg').trim(),
      dot: s.getPropertyValue('--u341-dot').trim().toLowerCase(),
      ptr: s.getPropertyValue('--u341-ptr').trim().toLowerCase(),
      hue: s.getPropertyValue('--u341-link-h').trim(),
    };
  });
  expect(storyTokens.bg).toBe('transparent');
  expect(storyTokens.dot).toBe('#e7f6f0');
  expect(storyTokens.ptr).toBe('#8cf2db');
  expect(storyTokens.hue).toBe('162');

  // Verify counter pill never overlaps title or Student/School nodes across breakpoints.
  const overlaps = (a: { x: number; y: number; width: number; height: number }, b: { x: number; y: number; width: number; height: number }) =>
    a.x < b.x + b.width && a.x + a.width > b.x && a.y < b.y + b.height && a.y + a.height > b.y;
  for (const [width, height] of [[768, 1000], [900, 1000], [1024, 1000], [1024, 600], [1101, 900], [1280, 600], [1440, 900], [1920, 1080]]) {
    await page.setViewportSize({ width, height });
    const ctBox = (await counter.boundingBox())!;
    const titleTextBox = await title.evaluate(el => {
      const range = document.createRange();
      range.selectNodeContents(el);
      const r = range.getBoundingClientRect();
      return { x: r.x, y: r.y, width: r.width, height: r.height };
    });
    const studentCircle = (await page.locator('.node-student > span').boundingBox())!;
    const schoolCircle = (await page.locator('.node-school > span').boundingBox())!;
    expect(overlaps(ctBox, titleTextBox), `counter overlaps title text at ${width}x${height}`).toBe(false);
    expect(overlaps(ctBox, studentCircle), `counter overlaps student circle at ${width}x${height}`).toBe(false);
    expect(overlaps(ctBox, schoolCircle), `counter overlaps school circle at ${width}x${height}`).toBe(false);
  }

  await page.setViewportSize({ width: 1440, height: 900 });
  const cvBox = (await canvas.boundingBox())!;
  // Click anywhere inside the connection diagram (not blocked by SVG overlay) bursts 9 dots.
  await page.mouse.move(cvBox.x + cvBox.width * 0.5, cvBox.y + cvBox.height * 0.5);
  await page.mouse.click(cvBox.x + cvBox.width * 0.25, cvBox.y + cvBox.height * 0.72);
  await expect(root).toHaveAttribute('data-node-count', '81');

  // Keyboard activation also bursts 9 dots cleanly.
  await canvas.focus();
  await expect(canvas).toBeFocused();
  await page.keyboard.press('Enter');
  await expect(root).toHaveAttribute('data-node-count', '90');

  // Particle pool caps at MAX_PARTICLES = 120 after repeated bursts.
  for (let i = 0; i < 5; i++) await page.keyboard.press('Space');
  await expect(root).toHaveAttribute('data-node-count', '120');

  // Reduced motion keeps counter and manual burst working without continuous animation.
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.mouse.click(cvBox.x + cvBox.width * 0.75, cvBox.y + cvBox.height * 0.65);
  await expect(counter).toHaveText(/^\d+ kết nối$/);

  // Light and dark CSS token sets map to UTBridora palette variables on non-story .u341 instances.
  const themeCheck = await page.evaluate(() => {
    const probe = document.createElement('div');
    probe.className = 'u341';
    probe.setAttribute('data-theme', 'light');
    document.body.appendChild(probe);
    const lightDot = getComputedStyle(probe).getPropertyValue('--u341-dot').trim().toLowerCase();
    const lightPtr = getComputedStyle(probe).getPropertyValue('--u341-ptr').trim().toLowerCase();
    probe.setAttribute('data-theme', 'dark');
    const darkDot = getComputedStyle(probe).getPropertyValue('--u341-dot').trim().toLowerCase();
    const darkPtr = getComputedStyle(probe).getPropertyValue('--u341-ptr').trim().toLowerCase();
    probe.remove();
    return { lightDot, lightPtr, darkDot, darkPtr };
  });
  expect(themeCheck.lightDot).toBe('#087f8c');
  expect(themeCheck.lightPtr).toBe('#056670');
  expect(themeCheck.darkDot).toBe('#e7f6f0');
  expect(themeCheck.darkPtr).toBe('#8cf2db');
});
