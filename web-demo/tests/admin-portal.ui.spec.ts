import { expect, test, type Page } from '@playwright/test';
import { fileURLToPath } from 'node:url';

// Browser-only fixtures verify layout and the existing HTTP contracts, not backend authorization.
const permissions = ['account:admin:status', 'account:admin:revoke', 'membership:admin:write', 'organization:admin:create', 'audit:global:read', 'taxonomy:skills:read'];
const schoolId = '00000000-0000-4000-8000-000000000011';
const staffId = '00000000-0000-4000-8000-000000000012';
const me = { userId: '00000000-0000-4000-8000-000000000001', displayName: 'Quản trị thử nghiệm', email: 'admin-ui@example.test', memberships: [{ role: 'ADMIN', organizationId: null }] };
const staff = { ...me, userId: staffId, displayName: 'Cán bộ thử nghiệm', email: 'staff-ui@example.test', status: 'ACTIVE', lockedUntil: null, memberships: [{ role: 'SCHOOL_STAFF', organizationId: schoolId }] };
const organizations = [{ id: schoolId, name: 'Đơn vị kiểm thử học vụ', type: 'UNIVERSITY', status: 'VERIFIED' }, { id: '00000000-0000-4000-8000-000000000013', name: 'Đơn vị kiểm thử hợp tác', type: 'ENTERPRISE', status: 'PENDING' }];
const audit = { id: 'audit-test', actorId: me.userId, action: 'LOGIN', result: 'SUCCESS', resourceType: 'user', resourceId: me.userId, organizationId: null, requestId: 'browser-audit-request', occurredAt: '2026-10-01T01:00:00Z', reasonCode: null };
const category = { categoryCode: 'TEST', name: 'Nhóm kiểm thử', parentCategoryCode: null, status: 'ACTIVE', version: 1 };
const pageOf = (content: unknown[], totalElements = content.length) => ({ content, number: 0, page: 0, size: 25, totalElements, totalPages: totalElements ? 1 : 0 });
const screenshotPath = (name: string) => fileURLToPath(new URL('../../target/' + name, import.meta.url));

async function session(page: Page, options: { canWrite?: boolean; permissions?: string[] } = {}) {
  const commands: { path: string; method: string; body: any; key?: string }[] = [];
  const skill = { skillCode: 'BROWSER_TEST', name: 'Kỹ năng kiểm thử', categoryCode: 'TEST', parentSkillCode: null, status: 'ACTIVE', version: 3 };
  let aliases = [{ aliasId: 'alias-test', skillCode: skill.skillCode, alias: 'Tên thử nghiệm', version: 1 }];
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', '');
    const json = (value: unknown) => route.fulfill({ json: value });
    if (request.method() !== 'GET') {
      const body = request.postDataJSON();
      commands.push({ path, method: request.method(), body, key: request.headers()['idempotency-key'] });
      if (path.endsWith('/resolve')) return json({ items: body.values.map((input: string) => ({ input, normalizedInput: input.toLowerCase(), status: 'RESOLVED', skillCode: skill.skillCode })) });
      if (path.endsWith('/aliases') && request.method() === 'POST') { aliases = [...aliases, { aliasId: 'new-alias', alias: body.alias, skillCode: skill.skillCode, version: 1 }]; skill.version++; }
      else if (path.includes('/aliases/') && request.method() === 'DELETE') { aliases = aliases.filter(item => !path.endsWith('/' + item.aliasId)); skill.version++; }
      else if (path === '/taxonomy/skills/' + skill.skillCode) { skill.name = body.name; skill.status = body.status; skill.version++; }
      return json({ message: 'Đã xử lý' });
    }
    if (path === '/auth/me') return json(me);
    if (path === '/auth/me/permissions') return json([{ role: 'ADMIN', organizationId: null, permissions: options.permissions || permissions }]);
    if (path === '/auth/csrf') return json({ headerName: 'X-CSRF-TOKEN', token: 'browser-csrf' });
    if (path === '/admin/users') return json(pageOf([staff], url.searchParams.get('status') === 'SUSPENDED' ? 3 : 17));
    if (path === '/admin/organizations') return json(pageOf(organizations, 8));
    if (path.endsWith('/status-history')) return json(pageOf([]));
    if (path === '/admin/audit-logs') return json(pageOf(url.searchParams.get('size') === '5'
      ? Array.from({ length: 5 }, (_, index) => ({ ...audit, id: 'audit-test-' + index, requestId: 'audit-request-' + index })) : [audit], 29));
    if (path === '/taxonomy/editor-access') return json({ allowed: options.canWrite !== false });
    if (path === '/taxonomy/categories') return json(pageOf([category]));
    if (path === '/taxonomy/skills') return json({ taxonomyVersion: 'browser-taxonomy-v1', skills: pageOf([skill]) });
    if (path.endsWith('/aliases')) return json(aliases);
    return route.fulfill({ status: 404, json: { error: 'NOT_FOUND' } });
  });
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
  return commands;
}

async function navigate(page: Page, title: string) {
  const open = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await open.isVisible()) await open.click();
  await page.getByRole('navigation').getByRole('button', { name: title, exact: true }).click();
}

test('operation toast confirms completed creation, stays across navigation and can be dismissed', async ({ page }) => {
  await session(page);
  await expect(page.locator('.operation-toast')).toHaveCount(0);
  let finish!: () => void;
  const pending = new Promise<void>(resolve => { finish = resolve; });
  await page.route('**/api/v1/admin/organizations', async route => {
    if (route.request().method() === 'GET') return route.fallback();
    expect(route.request().method()).toBe('POST');
    expect(route.request().postDataJSON()).toEqual({ name: 'Tổ chức kiểm thử thông báo', type: 'ENTERPRISE' });
    await pending;
    return route.fulfill({ json: { id: 'created-organization', name: 'Tổ chức kiểm thử thông báo', type: 'ENTERPRISE', status: 'PENDING' } });
  });
  await navigate(page, 'Tổ chức & lời mời');
  await page.locator('.admin-table-tools').getByRole('button', { name: 'Tạo tổ chức', exact: true }).click();
  const form = page.locator('#admin-create-organization');
  await form.getByLabel('Tên tổ chức', { exact: true }).fill('Tổ chức kiểm thử thông báo');
  await form.getByRole('button', { name: 'Tạo tổ chức', exact: true }).click();
  await expect(form.getByRole('button', { name: 'Tạo tổ chức', exact: true })).toBeDisabled();
  await expect(page.locator('.operation-toast')).toHaveCount(0);
  finish();
  const toast = page.locator('.operation-toast');
  await expect(toast).toHaveCount(1);
  await expect(toast).toHaveAttribute('aria-live', 'polite');
  await expect(toast.locator('strong')).toHaveText('Tạo thành công');
  await expect(form.getByLabel('Tên tổ chức', { exact: true })).toHaveValue('');
  await toast.hover();
  await page.waitForTimeout(4700);
  await expect(toast).toBeVisible();
  await navigate(page, 'Tài khoản của tôi');
  await expect(toast).toBeVisible();
  await toast.getByRole('button', { name: 'Đóng thông báo thao tác', exact: true }).click();
  await expect(toast).toHaveCount(0);
});

for (const status of [403, 503]) test(`operation feedback preserves form data and never announces success after HTTP ${status}`, async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page);
  await page.route('**/api/v1/admin/organizations', route => route.request().method() === 'GET' ? route.fallback()
    : route.fulfill({ status, json: { errorCode: status === 403 ? 'ACCESS_DENIED' : 'DEPENDENCY_UNAVAILABLE' } }));
  await navigate(page, 'Tổ chức & lời mời');
  await page.locator('.admin-table-tools').getByRole('button', { name: 'Tạo tổ chức', exact: true }).click();
  const form = page.locator('#admin-create-organization');
  await form.getByLabel('Tên tổ chức', { exact: true }).fill('Giữ nguyên nội dung đang nhập');
  await form.getByRole('button', { name: 'Tạo tổ chức', exact: true }).click();
  const toast = page.locator('.operation-toast');
  await expect(toast).toHaveCount(1);
  await expect(toast).toHaveAttribute('aria-live', status === 403 ? 'assertive' : 'polite');
  await expect(toast.locator('strong')).toHaveText(status === 403 ? 'Chưa thể hoàn tất thao tác' : 'Chưa xác nhận được kết quả');
  await expect(page.locator('.operation-toast.is-success')).toHaveCount(0);
  await expect(form.getByLabel('Tên tổ chức', { exact: true })).toHaveValue('Giữ nguyên nội dung đang nhập');
  await expect(toast).toHaveCSS('animation-name', 'none');
  const bounds = (await toast.boundingBox())!;
  expect(bounds.x).toBeGreaterThanOrEqual(0); expect(bounds.x + bounds.width).toBeLessThanOrEqual(390);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('accepted work is shown as pending and toast dismisses automatically on a 320px screen', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await session(page);
  await page.route('**/api/v1/admin/organizations', route => route.request().method() === 'GET' ? route.fallback()
    : route.fulfill({ status: 202, json: { message: 'Accepted' } }));
  await navigate(page, 'Tổ chức & lời mời');
  await page.locator('.admin-table-tools').getByRole('button', { name: 'Tạo tổ chức', exact: true }).click();
  const form = page.locator('#admin-create-organization');
  await form.getByLabel('Tên tổ chức', { exact: true }).fill('Tổ chức kiểm thử tiếp nhận');
  await page.clock.install();
  await form.getByRole('button', { name: 'Tạo tổ chức', exact: true }).click();
  const toast = page.locator('.operation-toast');
  await expect(toast).toHaveClass(/is-info/);
  await expect(toast.locator('strong')).toHaveText('Đã tiếp nhận yêu cầu');
  await expect(page.locator('.operation-toast.is-success')).toHaveCount(0);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.clock.fastForward(7500);
  await expect(toast).toHaveCount(0);
});

test('admin keeps account as the landing screen; overview reads real-shaped pages and shortcuts navigate', async ({ page }) => {
  await session(page);
  await expect(page.locator('.workspace-admin')).toBeVisible();
  const navNames = await page.getByRole('navigation').getByRole('button').allTextContents();
  expect(navNames.slice(0, 6)).toEqual(['Tổng quan quản trị', 'Tài khoản & phân quyền', 'Tổ chức & lời mời', 'Danh mục kỹ năng', 'Nhật ký hệ thống', 'Phân công quyền cán bộ']);
  await expect(page.getByRole('navigation').getByRole('button', { name: 'Tổng quan', exact: true })).toHaveCount(0);
  await navigate(page, 'Tổng quan quản trị');
  for (const value of ['17', '8', '3', '29']) await expect(page.locator('.admin-metric-value strong').filter({ hasText: new RegExp('^' + value + '$') })).toBeVisible();
  await expect(page.locator('.admin-recent-audit')).toContainText('LOGIN');
  await page.getByRole('button', { name: /Quản lý người dùng/ }).click();
  await expect(page.getByLabel('Tìm tên hoặc email')).toBeVisible();
  await page.getByRole('button', { name: 'Mời tài khoản mới', exact: true }).click();
  await expect(page.getByLabel('Email người nhận')).toBeVisible();
  await page.getByRole('button', { name: 'Mở tài khoản của tôi', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
});

test('overview handles dependency failures without presenting stale counts or losing other cards', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/admin/users?**', route => route.fulfill({ status: 503, json: { error: 'UNAVAILABLE' } }));
  await navigate(page, 'Tổng quan quản trị');
  await expect(page.locator('.metric-0 .admin-metric-value strong')).toHaveText('—');
  await expect(page.locator('.metric-1 .admin-metric-value strong')).toHaveText('8');
  await expect(page.getByRole('status')).toContainText('Một số số liệu chưa tải được');
});

test('account detail preserves status, membership, session and history actions', async ({ page }) => {
  const commands = await session(page);
  await navigate(page, 'Tài khoản & phân quyền');
  await expect(page.getByRole('columnheader')).toHaveCount(5);
  await page.getByRole('button', { name: 'Quy tắc trạng thái' }).click();
  await expect(page.locator('#account-status-rules')).toBeVisible();
  await page.getByRole('button', { name: 'Chi tiết', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Khóa tài khoản', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Vô hiệu hóa', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Thu hồi phiên đăng nhập', exact: true }).click();
  await expect.poll(() => commands.some(item => item.path === '/admin/users/' + staffId + '/revoke-sessions' && item.method === 'PATCH')).toBe(true);
  await page.getByRole('combobox', { name: 'Vai trò mới', exact: true }).selectOption('SCHOOL_STAFF');
  await page.getByRole('combobox', { name: 'Tổ chức', exact: true }).selectOption(schoolId);
  await page.getByRole('button', { name: 'Cấp vai trò', exact: true }).click();
  await expect.poll(() => commands.some(item => item.path === '/admin/memberships' && item.body.organizationId === schoolId)).toBe(true);
  await page.getByRole('button', { name: 'Xem lịch sử trạng thái', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Lịch sử trạng thái', exact: true })).toBeVisible();
});

test('organization filtering keeps the invitation choices independent and both forms use existing APIs', async ({ page }) => {
  const commands = await session(page);
  await navigate(page, 'Tổ chức & lời mời');
  await page.getByRole('combobox', { name: 'Lọc loại tổ chức', exact: true }).selectOption('UNIVERSITY');
  await expect(page.getByRole('table').locator('tbody tr')).toHaveCount(1);
  await expect(page.getByLabel('Tên tổ chức', { exact: true })).toBeHidden();
  await page.getByRole('button', { name: 'Tạo tổ chức', exact: true }).click();
  await page.getByLabel('Tên tổ chức', { exact: true }).fill('Tổ chức mới kiểm thử');
  await page.getByRole('button', { name: 'Tạo tổ chức', exact: true }).click();
  await expect.poll(() => commands.some(item => item.path === '/admin/organizations' && item.body.type === 'ENTERPRISE')).toBe(true);
  await page.getByRole('button', { name: 'Mời tài khoản', exact: true }).click();
  await page.getByLabel('Email người nhận').fill('invitation-ui@example.test');
  await page.getByRole('combobox', { name: 'Vai trò', exact: true }).selectOption('ENTERPRISE');
  await page.getByRole('combobox', { name: 'Tổ chức nhận vai trò', exact: true }).selectOption(organizations[1].id);
  await page.getByRole('button', { name: 'Gửi lời mời', exact: true }).click();
  await expect.poll(() => commands.some(item => item.path === '/admin/invitations' && item.body.organizationId === organizations[1].id)).toBe(true);
});

test('audit retains all six filters and request IDs while advanced filters can collapse', async ({ page }) => {
  await session(page);
  await navigate(page, 'Nhật ký hệ thống');
  for (const label of ['Mã sự kiện', 'Mã người thực hiện', 'Mã tổ chức', 'Từ thời điểm', 'Đến trước thời điểm']) await expect(page.getByLabel(label, { exact: true })).toBeVisible();
  await expect(page.getByRole('combobox', { name: 'Kết quả', exact: true })).toBeVisible();
  await page.getByLabel('Mã sự kiện').fill('LOGIN');
  const request = page.waitForRequest(request => request.url().includes('/admin/audit-logs?') && new URL(request.url()).searchParams.get('action') === 'LOGIN');
  await page.getByRole('button', { name: 'Lọc nhật ký', exact: true }).click(); await request;
  await expect(page.getByRole('cell', { name: audit.requestId, exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'LOGIN Thành công', exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Bộ lọc nâng cao' }).click();
  await expect(page.getByLabel('Mã sự kiện')).toBeHidden();
  await page.getByRole('button', { name: 'Bộ lọc nâng cao' }).click();
  await page.getByRole('button', { name: 'Đặt lại bộ lọc' }).click();
  await expect(page.getByLabel('Mã sự kiện')).toHaveValue('');
});

test('taxonomy tabs preserve editing versions, idempotency, aliases and resolver requests', async ({ page }) => {
  const commands = await session(page);
  await navigate(page, 'Danh mục kỹ năng');
  await page.getByLabel('Tìm kỹ năng').fill('BROWSER_TEST');
  await page.locator('.skill-node').click();
  const editor = page.locator('.taxonomy-editor');
  await expect(editor.getByRole('heading', { name: 'Chi tiết kỹ năng', exact: true })).toBeVisible();
  await editor.getByRole('button', { name: 'Chỉnh sửa', exact: true }).click();
  await editor.getByLabel('Tên chuẩn', { exact: true }).fill('Kỹ năng đã chỉnh sửa');
  await editor.getByRole('button', { name: 'Lưu thay đổi', exact: true }).click();
  await expect.poll(() => commands.find(item => item.path === '/taxonomy/skills/BROWSER_TEST')?.body.expectedVersion).toBe(3);
  expect(commands.find(item => item.path === '/taxonomy/skills/BROWSER_TEST')?.key).toMatch(/^[0-9a-f-]{36}$/);
  await expect(page.locator('.taxonomy-success')).toContainText('Đã cập nhật kỹ năng');
  await editor.getByRole('button', { name: 'Xem tên thay thế' }).click();
  await expect(page.getByRole('tab', { name: 'Tên thay thế & Tra cứu' })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('cell', { name: 'Tên thử nghiệm', exact: true })).toBeVisible();
  await page.getByLabel('Thêm tên gọi khác').fill('Tên bổ sung');
  await page.getByRole('button', { name: 'Thêm alias' }).click();
  await expect(page.getByRole('cell', { name: 'Tên bổ sung', exact: true })).toBeVisible();
  await page.getByRole('row').filter({ hasText: 'Tên bổ sung' }).getByRole('button', { name: 'Gỡ', exact: true }).click();
  await expect(page.getByRole('cell', { name: 'Tên bổ sung', exact: true })).toHaveCount(0);
  await page.getByLabel('Các tên cần tra cứu').fill('Tên thử nghiệm');
  await page.getByRole('button', { name: 'Tra cứu', exact: true }).click();
  await expect(page.getByRole('cell', { name: 'Đã phân giải', exact: true })).toBeVisible();
  expect(commands.find(item => item.path === '/taxonomy/skills/resolve')?.body).toEqual({ values: ['Tên thử nghiệm'] });
  await page.getByRole('tab', { name: 'Cây kỹ năng chuẩn', exact: true }).click();
  await page.getByRole('button', { name: 'Ngừng sử dụng', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Kích hoạt lại', exact: true })).toBeVisible();
  await expect(page.locator('.taxonomy-editor .status-badge')).toHaveText('Ngừng sử dụng');
  expect(commands.filter(item => item.path === '/taxonomy/skills/BROWSER_TEST').at(-1)?.body).toMatchObject({ expectedVersion: 6, status: 'DEPRECATED' });
});

test('read-only taxonomy prevents mutation and preserves lookup', async ({ page }) => {
  await session(page, { canWrite: false });
  await navigate(page, 'Danh mục kỹ năng');
  await expect(page.getByRole('button', { name: 'Thêm danh mục', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Thêm kỹ năng', exact: true })).toBeDisabled();
  await page.getByRole('tab', { name: 'Tên thay thế & Tra cứu' }).click();
  await page.getByRole('combobox', { name: 'Kỹ năng quản lý tên thay thế', exact: true }).selectOption('BROWSER_TEST');
  await expect(page.getByRole('button', { name: 'Thêm alias', exact: true })).toBeDisabled();
  await expect(page.getByLabel('Các tên cần tra cứu')).toBeVisible();
  await page.getByLabel('Các tên cần tra cứu').fill('Tên thử nghiệm');
  await page.getByRole('button', { name: 'Tra cứu', exact: true }).click();
  await expect(page.getByRole('cell', { name: 'Đã phân giải', exact: true })).toBeVisible();
  await expect(page.locator('.operation-toast')).toHaveCount(0);
});

test('admin screens fit mobile including detail, both taxonomy tabs and navigation', async ({ page }) => {
  await session(page);
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    for (const title of ['Tài khoản của tôi', 'Tổng quan quản trị', 'Tài khoản & phân quyền', 'Tổ chức & lời mời', 'Nhật ký hệ thống', 'Danh mục kỹ năng', 'Phân công quyền cán bộ']) {
      await navigate(page, title);
      if (title === 'Tài khoản & phân quyền') await page.getByRole('button', { name: 'Chi tiết', exact: true }).click();
      if (title === 'Danh mục kỹ năng') { await page.getByRole('tab', { name: 'Tên thay thế & Tra cứu' }).click(); await page.getByRole('combobox', { name: 'Kỹ năng quản lý tên thay thế', exact: true }).selectOption('BROWSER_TEST'); }
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth), { message: title + ' at ' + width }).toBeLessThanOrEqual(0);
    }
  }
});

test('desktop admin surfaces match the shared shell and all six design screens remain reachable', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await session(page);
  const sidebar = await page.locator('.sidebar').boundingBox();
  // Keep enough room for the unchanged menu names without crowding the desktop content.
  expect(sidebar!.width).toBeGreaterThanOrEqual(260);
  expect(sidebar!.width).toBeLessThanOrEqual(280);
  for (const [title, file] of [['Tổng quan quản trị', 'overview'], ['Tài khoản & phân quyền', 'users'], ['Tổ chức & lời mời', 'organizations'], ['Nhật ký hệ thống', 'audit'], ['Danh mục kỹ năng', 'taxonomy']]) {
    await navigate(page, title);
    if (file === 'overview') {
      await expect(page.locator('.admin-recent-audit li')).toHaveCount(5);
      // Read final geometry after the finite entrance effects settle.
      await page.locator('.admin-overview-bottom').evaluate(async node => {
        await Promise.all(node.getAnimations({ subtree: true }).map(animation => animation.finished.catch(() => undefined)));
      });
      const bottom = await page.locator('.admin-overview-bottom').boundingBox();
      expect(bottom!.y + bottom!.height).toBeLessThanOrEqual(900);
      const panels = await page.locator('.admin-overview-bottom > .panel').evaluateAll(nodes => nodes.map(node => {
        const bounds = node.getBoundingClientRect(); return { top: bounds.top, bottom: bounds.bottom };
      }));
      expect(Math.abs(panels[0].top - panels[1].top)).toBeLessThan(1);
      expect(Math.abs(panels[0].bottom - panels[1].bottom)).toBeLessThan(1);
      await expect(page.locator('.admin-metric-description')).toHaveCount(4);
      await page.emulateMedia({ reducedMotion: 'reduce' });
      const hoverLink = page.getByRole('navigation').getByRole('button', { name: 'Tài khoản & phân quyền', exact: true });
      await hoverLink.hover();
      await expect(hoverLink).toHaveCSS('transform', 'none');
      await expect(hoverLink).toHaveCSS('transition-duration', '0s');
      const shortcut = page.locator('.admin-shortcut').first();
      await shortcut.hover();
      await expect(shortcut).toHaveCSS('transform', 'none');
      await expect(shortcut).toHaveCSS('animation-name', 'none');
      await expect(page.locator('.admin-welcome')).toHaveCSS('animation-name', 'none');
      const refresh = page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true });
      await refresh.hover();
      await expect(refresh).toHaveCSS('transform', 'none');
      await expect(refresh).toHaveCSS('transition-duration', '0s');
      await refresh.focus();
      await page.keyboard.down('Space');
      await expect(refresh).toHaveCSS('transform', 'none');
      await page.keyboard.up('Space');
      await page.emulateMedia({ reducedMotion: 'no-preference' });
    }
    if (file === 'taxonomy') await page.locator('.skill-node').click();
    await expect(page.getByRole('navigation').getByRole('button', { name: title, exact: true })).toHaveCSS('background-color', 'rgb(11, 146, 124)');
    await expect(page.locator('.admin-location strong')).toHaveText(title);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await page.screenshot({ path: screenshotPath(`admin-${file}-desktop.png`), fullPage: true, animations: 'disabled' });
  }
  await page.getByRole('tab', { name: 'Tên thay thế & Tra cứu' }).click();
  await expect(page.getByRole('cell', { name: 'Tên thử nghiệm', exact: true })).toBeVisible();
  await page.screenshot({ path: screenshotPath('admin-aliases-desktop.png'), fullPage: true, animations: 'disabled' });
});

test('organization forms collapse without discarding input and apply filter refreshes the actual query', async ({ page }) => {
  await session(page);
  await navigate(page, 'Tổ chức & lời mời');
  await page.getByRole('button', { name: 'Mời tài khoản', exact: true }).click();
  await page.getByLabel('Email người nhận').fill('preserved@example.test');
  await page.getByRole('button', { name: 'Thu gọn form mời tài khoản' }).click();
  await expect(page.getByLabel('Email người nhận')).toBeHidden();
  await page.getByRole('button', { name: 'Mời tài khoản', exact: true }).click();
  await expect(page.getByLabel('Email người nhận')).toHaveValue('preserved@example.test');
  await navigate(page, 'Tài khoản & phân quyền');
  await page.getByLabel('Tìm tên hoặc email').fill('staff-ui');
  await expect(page.locator('.loading-state')).toHaveCount(0);
  const requested = page.waitForRequest(request => request.url().includes('/admin/users?') && new URL(request.url()).searchParams.get('search') === 'staff-ui');
  await page.getByRole('button', { name: 'Áp dụng bộ lọc', exact: true }).click();
  await requested;
});

test('taxonomy creation opens only the requested form and keeps API payloads and command keys', async ({ page }) => {
  const commands = await session(page);
  await navigate(page, 'Danh mục kỹ năng');
  await expect(page.getByLabel('Mã danh mục', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Thêm danh mục', exact: true }).click();
  await page.getByLabel('Mã danh mục', { exact: true }).fill('TEST_NEW');
  await page.getByLabel('Tên danh mục', { exact: true }).fill('Danh mục thử nghiệm');
  await page.getByRole('button', { name: 'Lưu danh mục mới' }).click();
  await expect.poll(() => commands.some(item => item.path === '/taxonomy/categories' && item.body.categoryCode === 'TEST_NEW' && !!item.key)).toBe(true);
  await page.getByRole('button', { name: 'Thêm kỹ năng', exact: true }).click();
  await expect(page.getByLabel('Mã danh mục', { exact: true })).toHaveCount(0);
  await page.getByLabel('Mã kỹ năng', { exact: true }).fill('SKILL_NEW');
  await page.getByLabel('Tên chuẩn', { exact: true }).fill('Kỹ năng mới');
  await page.getByRole('combobox', { name: 'Danh mục', exact: true }).selectOption('TEST');
  await page.getByRole('button', { name: 'Lưu kỹ năng mới' }).click();
  await expect.poll(() => commands.some(item => item.path === '/taxonomy/skills' && item.body.categoryCode === 'TEST' && item.body.skillCode === 'SKILL_NEW' && !!item.key)).toBe(true);
});

test('delegation forms, effectiveness badges and populated detail fit desktop and small phones', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await session(page);
  const permissions = ['cooperation:school:review', 'student_profile:school:read'];
  const scope = { schoolOrgId: schoolId, schoolName: 'Đơn vị kiểm thử học vụ', termId: '00000000-0000-4000-8000-000000000021', termCode: 'TEST-TERM', termStatus: 'ACTIVE' };
  const details = Array.from({ length: 4 }, (_, index) => ({
    delegation: { id: 'delegation-' + index, userId: staffId, schoolOrgId: schoolId, termId: scope.termId, permissionCodes: permissions,
      profileReadFields: ['displayName', 'email'], validFrom: '2026-01-01T00:00:00Z', validUntil: '2099-12-31T23:59:00Z',
      active: index !== 2, version: 1, grantedBy: me.userId, decisionRef: 'DEMO-UI-' + index },
    displayName: staff.displayName, email: staff.email, grantorName: me.displayName, parentId: null,
    effectivePermissionCodes: index === 0 ? permissions : index === 1 ? permissions.slice(0, 1) : [], canRevoke: index === 0
  }));
  const paged = (content: unknown[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: 1 });
  await page.route('**/api/v1/admin/school-delegations**', route => {
    const path = new URL(route.request().url()).pathname;
    let data: unknown = paged(details);
    if (path.endsWith('/scopes')) data = paged([scope]);
    else if (path.endsWith('/options')) data = { scope, authorities: [], permissionCodes: permissions, profileReadFields: ['displayName', 'email'] };
    else if (path.endsWith('/candidates')) data = paged([staff]);
    else if (path.includes('/delegation-')) data = details.find(item => path.endsWith('/' + item.delegation.id));
    return route.fulfill({ json: data });
  });
  await navigate(page, 'Phân công quyền cán bộ');
  await expect(page.getByRole('heading', { name: 'Khởi tạo phân công demo', exact: true })).toBeVisible();
  const table = page.locator('.delegation-list');
  for (const title of ['Đang có hiệu lực', 'Hiệu lực một phần', 'Đã thu hồi', 'Chưa có hoặc đã mất hiệu lực']) await expect(table.getByText(title, { exact: true })).toBeVisible();
  await page.getByRole('checkbox', { name: 'Xem hồ sơ trong phạm vi trường được giao', exact: true }).check();
  await page.getByRole('checkbox', { name: 'Họ tên', exact: true }).check();
  await expect(page.locator('.delegation-choices label').filter({ has: page.getByRole('checkbox', { name: 'Họ tên', exact: true }) })).toHaveCSS('background-color', 'rgb(231, 246, 240)');
  await page.getByRole('button', { name: 'Xem phân công DEMO-UI-0', exact: true }).click();
  const detail = page.getByRole('region', { name: 'Chi tiết phân công', exact: true });
  await expect(detail.getByText('DEMO-UI-0', { exact: true })).toBeVisible();
  await expect(detail.getByRole('button', { name: 'Thu hồi phân công', exact: true })).toBeVisible();
  await page.evaluate(() => scrollTo(0, 0));
  await page.screenshot({ path: screenshotPath('delegation-ui-desktop.png'), fullPage: true, animations: 'disabled' });
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
    await expect(page.getByLabel('Căn cứ phân công', { exact: true })).toBeVisible();
    await expect(detail.getByRole('button', { name: 'Thu hồi phân công', exact: true })).toBeVisible();
    await expect.poll(() => page.locator('aside.sidebar').evaluate(element => element.getBoundingClientRect().right)).toBeLessThanOrEqual(0);
    await page.evaluate(() => scrollTo(0, 0));
    await page.screenshot({ path: screenshotPath(`delegation-ui-${width}.png`), fullPage: true, animations: 'disabled' });
  }
});

test('audit enriches actor and organization from permitted real-shaped API pages and keeps original codes', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await session(page);
  const queriedPages: string[] = [];
  await page.route('**/api/v1/admin/users?**', route => {
    const query = new URL(route.request().url()).searchParams;
    if (query.get('size') === '100') queriedPages.push(query.get('page') || '0');
    return route.fulfill({ json: { ...pageOf(query.get('page') === '1' ? [me] : [staff]), totalPages: 2, totalElements: 101 } });
  });
  await page.route('**/api/v1/admin/audit-logs?**', route => route.fulfill({ json: pageOf([{ ...audit, resourceType: 'organization', organizationId: schoolId, resourceId: schoolId }]) }));
  await navigate(page, 'Nhật ký hệ thống');
  const row = page.getByRole('row').filter({ has: page.getByRole('cell', { name: audit.requestId, exact: true }) });
  await expect(row.locator('.audit-person')).toContainText(me.displayName);
  await expect(row.locator('.audit-person')).toContainText(me.email);
  await expect(row.locator('.audit-person')).toHaveAttribute('title', me.userId);
  await expect(row.getByText('Đăng nhập hệ thống', { exact: true })).toBeVisible();
  await expect(row.getByRole('cell', { name: 'LOGIN Thành công', exact: false })).toBeVisible();
  await expect(row.locator('.resource-name')).toHaveText('Tổ chức');
  await expect(row.locator('.audit-organization')).toHaveText(organizations[0].name);
  await expect(row.getByRole('cell', { name: audit.requestId, exact: true })).toBeVisible();
  expect(queriedPages).toContain('1');
  expect(queriedPages.every(page => ['0', '1'].includes(page))).toBe(true);
  await page.screenshot({ path: screenshotPath('audit-names-desktop.png'), fullPage: true, animations: 'disabled' });
  await navigate(page, 'Tổng quan quản trị');
  const widget = page.locator('.admin-recent-audit');
  await expect(widget).toContainText('Đăng nhập hệ thống');
  await expect(widget).toContainText(me.displayName);
  await expect(widget).toContainText(organizations[0].name);
  await expect(widget).toContainText('LOGIN');
  await page.screenshot({ path: screenshotPath('audit-names-widget.png'), fullPage: true, animations: 'disabled' });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
});

test('supplementary directory errors do not break audit rows, request IDs or unknown-event fallbacks', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/admin/users?**', route => route.fulfill({ status: 403, json: { error: 'ACCESS_DENIED' } }));
  await page.route('**/api/v1/admin/organizations?**', route => route.abort('failed'));
  await page.route('**/api/v1/admin/audit-logs?**', route => route.fulfill({ json: pageOf([
    { ...audit, organizationId: schoolId },
    { ...audit, id: 'system-event', actorId: null, action: 'FUTURE_AUDIT_CODE', resourceType: 'future_resource', requestId: 'system-request' }
  ]) }));
  await navigate(page, 'Nhật ký hệ thống');
  await expect(page.getByRole('cell', { name: audit.requestId, exact: true })).toBeVisible();
  await expect(page.locator('.audit-reference')).toHaveText(me.userId.slice(0, 8) + '…');
  await expect(page.locator('.audit-reference')).toHaveAttribute('title', me.userId);
  await expect(page.getByText('Hệ thống / Chưa xác thực', { exact: true })).toBeVisible();
  await expect(page.locator('.audit-event-line > b').filter({ hasText: 'FUTURE_AUDIT_CODE' })).toBeVisible();
  await expect(page.getByText('future_resource', { exact: true })).toBeVisible();
  await expect(page.getByRole('status')).toHaveCount(0);
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(0);
  }
});

test('audit-only permission never requests account or organization directories', async ({ page }) => {
  await session(page, { permissions: ['audit:global:read'] });
  const reads: string[] = [];
  page.on('request', request => { if (/\/admin\/(users|organizations)\?/.test(request.url())) reads.push(request.url()); });
  await navigate(page, 'Nhật ký hệ thống');
  await expect(page.getByRole('cell', { name: audit.requestId, exact: true })).toBeVisible();
  await expect(page.locator('.audit-reference')).toHaveAttribute('title', me.userId);
  expect(reads).toEqual([]);
});

test('admin pointer lighting clears on leave, reduced motion and touch input without changing navigation', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await session(page);
  await navigate(page, 'Tổng quan quản trị');
  const metric = page.locator('.admin-metric').first();
  await metric.hover();
  await expect(metric).toHaveAttribute('data-admin-glow', '');
  expect(await metric.evaluate(node => node.style.getPropertyValue('--admin-glow-x'))).toMatch(/px$/);
  await page.mouse.move(10, 10);
  await expect(metric).not.toHaveAttribute('data-admin-glow');
  await metric.hover();
  await expect(metric).toHaveAttribute('data-admin-glow', '');
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(metric).not.toHaveAttribute('data-admin-glow');
  await expect(metric).toHaveCSS('animation-name', 'none');
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await metric.dispatchEvent('pointermove', { pointerType: 'touch', clientX: 450, clientY: 350 });
  await expect(metric).not.toHaveAttribute('data-admin-glow');
  const shortcut = page.locator('.admin-shortcut').first();
  await shortcut.hover();
  await expect(shortcut).toHaveAttribute('data-admin-glow', '');
  await shortcut.click();
  await expect(page.getByLabel('Tìm tên hoặc email')).toBeVisible();
});
