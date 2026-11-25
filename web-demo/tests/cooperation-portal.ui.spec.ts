import { expect, test, type Page } from '@playwright/test';

// Browser contract fixtures, not evidence of a live backend or business database.
const enterprise = '11111111-1111-4111-8111-111111111111';
const school = '22222222-2222-4222-8222-222222222222';
const other = '33333333-3333-4333-8333-333333333333';
const term = '44444444-4444-4444-8444-444444444444';
const cooperationId = '55555555-5555-4555-8555-555555555555';
const rows = (content: unknown[], page = 0, totalPages = 1) => ({ content, page, size: 100, totalElements: content.length, totalPages });
const demand = { demandId: 'demand-1', enterpriseOrgId: enterprise, state: 'READY', version: 2, criteriaVersion: 1, validationState: 'VALID', cooperationId, softDeletedAt: null,
  data: { schoolOrgId: school, termId: term, title: 'API Java Intern', quantity: 3, description: 'Vị trí từ API', location: null, benefits: null,
    deadline: '2027-01-01T00:00:00Z', required: { majorCodes: [], skills: [], dateRange: { startDate: '2027-02-01', endDate: '2027-05-01' } }, preferred: [], academicCatalogVersion: 'v1', taxonomyVersion: 'v1' } };
const cooperation = { cooperationId, demandId: demand.demandId, schoolOrgId: school, enterpriseOrgId: enterprise, termId: term, state: 'APPROVED', submissionNo: 1, version: 3 };
function progressRows(schoolSide: boolean, counters = { target: 3, placed: 0, pending: 0, remaining: 3 }) {
  const totals = schoolSide ? { ...counters, reservations: counters.pending, declined: 0, enterpriseAccepted: 0, pendingCreation: 0, pendingApproval: counters.pending, openForNomination: Math.max(0, counters.remaining - counters.pending) } : counters;
  return { content: [{ demandId: demand.demandId, title: demand.data.title, schoolOrgId: school, schoolName: 'Trường A', enterpriseOrgId: enterprise,
    enterpriseName: 'Công ty FPT Software', termId: term, termCode: 'HK1', cooperationId, cooperationState: 'APPROVED',
    status: counters.placed >= counters.target ? 'FULL' : counters.pending ? 'WAITING' : 'SHORT', counters: totals }],
    page: 0, size: 10, totalElements: 1, totalPages: 1, totals, schools: [{ id: school, name: 'Trường A' }], terms: [{ id: term, name: 'HK1' }] };
}

async function session(page: Page, schoolSide = false, signedOut = false) {
  let authenticated = !signedOut;
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1', '');
    const role = schoolSide ? 'SCHOOL_STAFF' : 'ENTERPRISE', org = schoolSide ? school : enterprise;
    if (path === '/cooperation/organizations') return route.fulfill({ json: schoolSide
      ? [{ id: school, name: 'Trường Đại học Giao thông vận tải TP.HCM', type: 'UNIVERSITY' }]
      : [{ id: enterprise, name: 'Công ty FPT Software', type: 'ENTERPRISE' }, { id: other, name: 'Công ty đối tác từ API', type: 'ENTERPRISE' }] });
    if (path.endsWith('/permissions')) return route.fulfill({ json: [org, ...(schoolSide ? [] : [other])].map(organizationId => ({
      role, organizationId, permissions: ['cooperation:enterprise:create', 'cooperation:school:review'],
    })) });
    const user = { userId: 'test-user', email: 'test@example.test', displayName: 'Portal tester', memberships: [{ role, organizationId: org }] };
    if (path === '/auth/login') { authenticated = true; return route.fulfill({ json: user }); }
    if (path === '/auth/me') return authenticated ? route.fulfill({ json: user }) : route.fulfill({ status: 401, json: { error: 'SESSION_EXPIRED' } });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-token' } });
    if (path === '/enterprise/demand-references/schools') return route.fulfill({ json: rows([{ schoolOrgId: school, name: 'Trường Đại học Giao thông vận tải TP.HCM' }]) });
    if (path === '/enterprise/demand-references/terms' || path === '/terms') return route.fulfill({ json: rows([{ termId: term, termCode: 'Học kỳ 1 · 2026–2027' }]) });
    if (path.endsWith('/verifications')) return route.fulfill({ json: rows([]) });
    if (path === '/enterprise/demands') return route.fulfill({ json: rows(route.request().headers()['x-organization-id'] === other ? [] : [demand]) });
    if (path.endsWith('/cooperations')) return route.fulfill({ json: rows(route.request().headers()['x-organization-id'] === other ? [] : [cooperation]) });
    if (path.endsWith('/cooperations/' + cooperationId)) return route.fulfill({ json: { demand, cooperation, feedback: null } });
    if (path.endsWith('/proposals')) return route.fulfill({ json: rows([]) });
    if (path.endsWith('/proposal-batches')) return route.fulfill({ json: rows([]) });
    if (path.endsWith('/capacity')) return route.fulfill({ json: { quantity: 3, activeReservations: 0, placedCount: 0, openForNomination: 3 } });
    if (path.endsWith('/publication')) return route.fulfill({ json: { cooperationId, version: 3, publishedAt: null, closedAt: null } });
    if (path === `/${schoolSide ? 'school' : 'enterprise'}/placements/progress`) return route.fulfill({ json: progressRows(schoolSide) });
    if (path === `/${schoolSide ? 'school' : 'enterprise'}/placements/summary`) return route.fulfill({ json: { demandId: demand.demandId, cooperationId, target: 3, placed: 0, pending: 0, remaining: 3 } });
    if (path === `/${schoolSide ? 'school' : 'enterprise'}/placements`) return route.fulfill({ json: rows([]) });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');
  if (!signedOut) await expect(page.getByRole('heading', { name: schoolSide ? 'Tổng quan Nhà trường' : 'Tổng quan doanh nghiệp', exact: true })).toBeVisible();
}

for (const schoolSide of [true, false]) for (const width of [320, 390]) test(`phone controls and drawer remain usable for ${schoolSide ? 'school' : 'enterprise'} at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 740 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page, schoolSide);
  const menu = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  const trigger = (await menu.boundingBox())!;
  expect(trigger.width).toBeGreaterThanOrEqual(44); expect(trigger.height).toBeGreaterThanOrEqual(44);
  const term = page.getByRole('combobox', { name: 'Kỳ theo dõi', exact: true });
  await expect(term).toHaveCSS('font-size', '16px');
  expect((await term.boundingBox())!.height).toBeGreaterThanOrEqual(44);
  const account = page.locator('.topbar .role-menu-trigger');
  await account.click();
  const popup = page.locator('.role-menu-popover');
  await expect(popup).toBeVisible();
  const bounds = (await popup.boundingBox())!;
  expect(bounds.x).toBeGreaterThanOrEqual(0); expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
  await page.keyboard.press('Escape');
  await menu.click();
  await expect(page.locator('#main-navigation')).toHaveClass(/is-open/);
  await expect(page.locator('body')).toHaveCSS('overflow', 'hidden');
  const close = page.getByRole('button', { name: 'Đóng menu điều hướng', exact: true });
  expect((await close.boundingBox())!.width).toBeGreaterThanOrEqual(44);
  await page.getByRole('link', { name: 'UT.Bridora trang chủ', exact: true }).focus();
  await page.keyboard.press('Shift+Tab');
  await expect(page.locator('#main-navigation').locator('button:not(:disabled):not([tabindex="-1"]):visible, a[href]:visible').last()).toBeFocused();
  await page.keyboard.press('Escape');
  await expect(menu).toBeFocused();
  await expect(page.locator('#main-navigation')).not.toHaveClass(/is-open/);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

test('phone demand editor stacks its columns and keeps all input values readable', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 740 });
  await session(page);
  await page.getByRole('button', { name: 'Mở menu điều hướng', exact: true }).click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Tạo nhu cầu', exact: true }).click();
  const editor = page.locator('.cooperation-editor');
  await expect(editor).toBeVisible();
  const columns = await editor.locator('.bm01-two-col-grid > div').evaluateAll(nodes => nodes.map(node => { const r = node.getBoundingClientRect(); return { x: r.x, y: r.y }; }));
  expect(columns).toHaveLength(2); expect(columns[0].x).toBeCloseTo(columns[1].x, 0); expect(columns[1].y).toBeGreaterThan(columns[0].y);
  for (const input of await editor.locator('input:not([type="checkbox"]):not([type="hidden"]), select, textarea').all()) await expect(input).toHaveCSS('font-size', '16px');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

for (const schoolSide of [false, true]) {
  const title = schoolSide ? 'Tổng quan Nhà trường' : 'Tổng quan doanh nghiệp';
  test(`${schoolSide ? 'school' : 'enterprise'} opens its overview on session restore and keeps the portal navigation on the account page`, async ({ page }) => {
    await session(page, schoolSide);
    await expect(page).toHaveTitle('Tổng quan | UT.Bridora');
    const navigation = page.getByRole('navigation', { name: 'Điều hướng chính' });
    if (schoolSide) await navigation.getByRole('button', { name: 'Tài khoản & Tiện ích', exact: true }).click();
    await navigation.getByRole('button', { name: 'Tài khoản của tôi', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
    await expect(page.locator('.workspace-t5')).toBeVisible();
    await expect(navigation.getByRole('button', { name: 'Tổng quan', exact: true })).not.toHaveAttribute('aria-current', 'page');
    await expect(page.getByRole('button', { name: 'Mở cổng doanh nghiệp', exact: true })).toHaveCount(0);
    await navigation.getByRole('button', { name: 'Tổng quan', exact: true }).click();
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    await page.reload();
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
  });

  test(`${schoolSide ? 'school' : 'enterprise'} goes directly from login to its overview`, async ({ page }) => {
    await session(page, schoolSide, true);
    await page.getByLabel('Email', { exact: true }).fill('test@example.test');
    await page.getByLabel('Mật khẩu', { exact: true }).fill('Test-only-password-123!');
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page.getByRole('heading', { name: title, exact: true })).toBeVisible();
    await expect(page.locator('.workspace-t5')).toBeVisible();
  });
}

test('a direct internship case link keeps its destination for an enterprise user', async ({ page }) => {
  await session(page);
  const path = '/workspace/cases/77777777-7777-4777-8777-777777777777';
  await page.goto(path);
  await expect(page.getByRole('heading', { name: 'Hồ sơ thực tập', level: 1, exact: true })).toBeVisible();
  await expect(page).toHaveURL(path);
  await expect(page.getByRole('heading', { name: 'Tổng quan doanh nghiệp', exact: true })).toHaveCount(0);
});

for (const role of ['ADMIN', 'STUDENT']) {
  test(`portal landing preserves the existing account entry for ${role}`, async ({ page }) => {
    await session(page);
    await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [
      { role: 'ENTERPRISE', organizationId: enterprise, permissions: ['cooperation:enterprise:create'] },
      { role, organizationId: role === 'ADMIN' ? null : school, permissions: [] },
    ] }));
    // A role-default landing is tested at the root; explicit permitted URLs now survive Reload.
    await page.goto('/');
    await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
    await expect(page.locator('.workspace-t5')).toHaveCount(0);
  });
}

test('enterprise overview counts all API pages and contains no fabricated panels', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/cooperations?*', route => {
    const current = Number(new URL(route.request().url()).searchParams.get('page'));
    return route.fulfill({ json: rows([{ ...cooperation, cooperationId: cooperationId + current }], current, 2) });
  });
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Tổng quan', exact: true }).click();
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(page.locator('.portal-stat').filter({ hasText: 'Hợp tác đã duyệt' }).locator('strong')).toHaveText('2');
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Thông báo', exact: true })).toBeVisible();
  await expect(page.getByText(/Lâm Gia Hân|Huỳnh Thị Minh Thơ|2026–2027|Hôm qua|FIGMA|T5-UI-/)).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-enterprise-portal.png', fullPage: true });
});

test('school overview keeps four scoped KPIs and student import is available only in the students menu', async ({ page }) => {
  await session(page, true);const imports: string[] = [];
  page.on('request', request => {if (/\/api\/v1\/.*(imports|roster|resend)/.test(request.url())) imports.push(request.url());});
  await expect(page.locator('.portal-stat')).toHaveCount(4);
  const panel = page.getByRole('region', { name: 'Nhập danh sách & Kích hoạt sinh viên', exact: true });
  await expect(panel).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-ui-school-overview.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.getByRole('button', { name: 'Mở menu điều hướng', exact: true }).click();
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await nav.getByRole('button', { name: 'Sinh viên', exact: true }).click();
  await nav.getByRole('button', { name: 'Danh sách & kích hoạt', exact: true }).click();
  await expect(panel).toBeVisible();
  await expect(panel.getByText('studentCode,email,fullName,majorCode,termCode', { exact: true })).toBeVisible();
  await expect(panel.getByLabel('Kỳ nhập danh sách', { exact: true })).toHaveValue('');
  await expect(panel.getByLabel('Tệp danh sách sinh viên', { exact: true })).toHaveCount(0);
  await expect(panel.getByText(/Dữ liệu nhập không tự xác nhận điều kiện thực tập/)).toBeVisible();
  expect(imports).toEqual([]);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
});

for (const width of [1440, 390]) test(`school dashboard tasks resolve actual names and open the selected cooperation outside the visible list at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page, true);
  const titles = ['Thực tập sinh Backend Java', 'Thực tập sinh Phân tích dữ liệu'];
  const pending = titles.map((title, index) => ({ ...cooperation, cooperationId: `44444444-4444-4444-8444-44444444444${index}`,
    demandId: `55555555-5555-4555-8555-55555555555${index}`, state: 'SUBMITTED' }));
  const details = pending.map((record, index) => ({ cooperation: record, feedback: null,
    demand: { ...demand, demandId: record.demandId, cooperationId: record.cooperationId, data: { ...demand.data, title: titles[index] } } }));
  const commands: string[] = [];
  page.on('request', request => { if (request.method() !== 'GET' && new URL(request.url()).pathname.startsWith('/api/v1/school/')) commands.push(request.method()); });
  await page.route('**/api/v1/school/cooperations?*', route => route.fulfill({ json: new URL(route.request().url()).searchParams.get('size') === '25'
    ? { ...rows([pending[0]]), size: 25, totalElements: 26, totalPages: 2 } : rows(pending) }));
  for (const detail of details) await page.route('**/api/v1/school/cooperations/' + detail.cooperation.cooperationId, route => route.fulfill({ json: detail }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  const tasks = page.locator('.portal-dashboard-tasks');
  await expect(tasks.getByText(titles[0], { exact: true })).toBeVisible();
  const selected = tasks.getByRole('row').filter({ hasText: titles[1] });
  await expect(selected).toContainText('Lần gửi 1');
  await selected.getByRole('button', { name: 'Xem hồ sơ', exact: true }).click();
  const detail = page.locator('#school-cooperation-detail-' + pending[1].cooperationId);
  await expect(detail.getByRole('heading', { name: titles[1], exact: true })).toBeVisible();
  await expect(detail.getByRole('heading', { name: titles[0], exact: true })).toHaveCount(0);
  await expect(detail).toBeFocused();
  const heading = (await detail.getByRole('heading', { name: titles[1], exact: true }).boundingBox())!;
  const toolbar = await page.locator(width > 900 ? '.portal-context-bar' : '.workspace-main > .topbar').boundingBox();
  expect(heading.y).toBeGreaterThanOrEqual((toolbar ? toolbar.y + toolbar.height : 0) + 8);
  expect(heading.y).toBeLessThan(860);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  expect(commands).toEqual([]);
  await page.screenshot({ path: `../target/dashboard-task-cooperation-${width}.png`, fullPage: true, animations: 'disabled' });
});

test('school dashboard verification task opens and expands the exact dossier outside the visible list', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });await page.emulateMedia({ reducedMotion: 'reduce' });await session(page, true);
  const dossiers = ['Doanh nghiệp Công nghệ Ánh Dương', 'Doanh nghiệp Giải pháp Sông Xanh'].map((registeredName, index) => ({
    verificationId: `66666666-6666-4666-8666-66666666666${index}`, enterpriseOrgId: enterprise, state: 'SUBMITTED', version: 1,
    missingFields: [], decisionRef: null, data: { schoolOrgId: school, previousVerificationId: null, registrationCode: 'REG-' + index, registeredName,
      address: 'Địa chỉ kiểm thử ' + index, contactName: 'Liên hệ ' + index, contactEmail: `contact-${index}@example.test`, contactPhone: '0901234567', evidenceFileIds: [] },
  }));
  await page.route('**/api/v1/school/verifications?*', route => route.fulfill({ json: new URL(route.request().url()).searchParams.get('size') === '25'
    ? { ...rows([dossiers[0]]), size: 25, totalElements: 26, totalPages: 2 } : rows(dossiers) }));
  for (const dossier of dossiers) await page.route('**/api/v1/school/verifications/' + dossier.verificationId, route => route.fulfill({ json: dossier }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  const tasks = page.locator('.portal-dashboard-tasks');
  await tasks.getByRole('row').filter({ hasText: dossiers[1].data.registeredName }).getByRole('button', { name: 'Xem hồ sơ', exact: true }).click();
  const detail = page.getByRole('article', { name: 'Chi tiết hồ sơ xác minh BM-03', exact: true });
  await expect(detail.getByRole('heading', { name: 'Hồ sơ BM-03 · ' + dossiers[1].data.registeredName, exact: true })).toBeVisible();
  await expect(detail.locator('.bm03-expanded-section')).toBeVisible();
  await expect(detail).toContainText(dossiers[1].data.address);await expect(detail).not.toContainText(dossiers[0].data.registeredName);
  await expect(detail).toBeFocused();
  await page.getByRole('row').filter({ hasText: dossiers[0].data.registeredName }).click();
  await expect(detail.getByRole('heading', { name: 'Hồ sơ BM-03 · ' + dossiers[0].data.registeredName, exact: true })).toBeVisible();
  await expect(detail).not.toContainText(dossiers[1].data.registeredName);
  await page.route('**/api/v1/school/verifications/' + dossiers[1].verificationId, route => route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } }));
  await page.getByRole('button', { name: 'Tổng quan', exact: true }).click();
  await page.locator('.portal-dashboard-tasks').getByRole('row').filter({ hasText: dossiers[1].data.registeredName }).getByRole('button', { name: 'Xem hồ sơ', exact: true }).click();
  await expect(page.getByText('Hồ sơ xác minh đã thay đổi hoặc không còn trong phạm vi truy cập.', { exact: true })).toBeVisible();
  await expect(detail).toHaveCount(0);
});

test('dashboard task hides internal ids when title lookup fails and never opens another cooperation on denied access', async ({ page }) => {
  await session(page, true);
  const record = { ...cooperation, cooperationId: '77777777-7777-4777-8777-777777775599', state: 'SUBMITTED' };
  await page.route('**/api/v1/school/cooperations?*', route => route.fulfill({ json: rows([record]) }));
  await page.route('**/api/v1/school/cooperations/' + record.cooperationId, route => route.fulfill({ status: 403, json: { errorCode: 'ACCESS_DENIED' } }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  const tasks = page.locator('.portal-dashboard-tasks');
  await expect(tasks.locator('.portal-task-title')).toHaveText('Đề nghị hợp tác');
  await tasks.getByRole('button', { name: 'Xem hồ sơ', exact: true }).click();
  await expect(page.getByText('Đề nghị đã thay đổi hoặc không còn trong phạm vi truy cập.', { exact: true })).toBeVisible();
  await expect(page.locator('.school-cooperation-detail')).toHaveCount(0);
});

for (const schoolSide of [true, false]) test(`${schoolSide ? 'school' : 'enterprise'} dashboard shortcut opens the existing placement workspace`, async ({ page }) => {
  await session(page, schoolSide);
  await page.getByRole('region', { name: 'Truy cập nhanh', exact: true }).getByRole('button', {
    name: schoolSide ? 'Theo dõi tiếp nhận thực tập' : 'Thực tập & bàn giao', exact: true,
  }).click();
  await expect(page.getByRole('region', { name: 'Tổng hợp tiếp nhận theo phạm vi', exact: true })).toBeVisible();
  if (!schoolSide) {
    await expect(page.getByRole('heading', { name: demand.data.title, exact: true })).toHaveCount(0);
    await page.getByRole('button', { name: 'Xem tiếp nhận', exact: true }).click();
  }
  await expect(page.getByRole('heading', { name: demand.data.title, exact: true })).toBeVisible();
});

test('demand cards expose actual required and preferred skill counts without changing the draft', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands?*', route => route.fulfill({ json: rows([{ ...demand,
    data: { ...demand.data, required: { ...demand.data.required, skills: [{ skillCode: 'JAVA', minLevel: 2, minYearsPractice: null }, { skillCode: 'SQL', minLevel: null, minYearsPractice: 1 }] },
      preferred: [{ skillCode: 'REACT', minLevel: 2, minYearsPractice: null }] },
  }]) }));
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  await expect(page.getByLabel('Tóm tắt kỹ năng tuyển dụng').locator('dd')).toHaveText(['2', '1']);
  await expect(page.getByRole('heading', { name: demand.data.title, exact: true })).toBeVisible();
});

test('enterprise candidates use the approved cooperation API and switching organization clears the old scope', async ({ page }) => {
  await session(page);
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await page.getByLabel('Hợp tác đang xem', { exact: true }).selectOption(cooperationId);
  await expect(page.getByLabel('Hợp tác đang xem', { exact: true }).locator('option:checked')).toHaveText(demand.data.title);
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', { exact: true })).toBeVisible();
  await page.getByLabel('Doanh nghiệp đang làm việc', { exact: true }).selectOption(other);
  await expect(page.getByRole('heading', { name: 'Chưa có hợp tác được duyệt', exact: true })).toBeVisible();
  await expect(page.getByRole('option', { name: demand.data.title, exact: true })).toHaveCount(0);
});

test('school cooperation presents the submitted details, real term label and readable mobile cards', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page, true);
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: rows([{ termId: term, schoolOrgId: school, termCode: 'HK1 2026–2027' }]) }));
  const input = { ...demand, data: { ...demand.data, required: { ...demand.data.required, skills: [{ skillCode: 'JAVA', minLevel: 3, minYearsPractice: 1 }] } } };
  await page.route('**/api/v1/school/cooperations/' + cooperationId, route => route.fulfill({ json: { demand: input, cooperation,
    feedback: { reasonCode: null, missingFields: [], decisionRef: 'QD-API-APPROVED' } } }));
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await nav.getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await nav.getByRole('button', { name: 'Duyệt hợp tác', exact: true }).click();
  const table = page.getByRole('table', { name: 'Danh sách đề nghị hợp tác', exact: true });
  await expect(table.getByText('HK1 2026–2027', { exact: true })).toBeVisible();
  await expect(table.getByRole('heading', { name: demand.data.title, exact: true })).toBeVisible();
  await expect(table.getByRole('heading', { name: 'Đề nghị 1', exact: true })).toHaveCount(0);
  await page.getByRole('searchbox', { name: 'Tìm kiếm đề nghị hợp tác', exact: true }).fill(demand.data.title);
  await expect(table.getByRole('heading', { name: demand.data.title, exact: true })).toBeVisible();
  await page.getByRole('searchbox', { name: 'Tìm kiếm đề nghị hợp tác', exact: true }).fill('');
  const open = page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true });
  await open.click();
  const summary = page.locator('.school-coop-summary');
  await expect(summary.getByRole('heading', { name: demand.data.title, exact: true })).toBeVisible();
  await expect(summary.locator('.school-coop-skill-tags')).toContainText('JAVA');
  await expect(summary.locator('.school-coop-skill-tags')).toContainText('Mức 3');
  await expect(summary.getByRole('region', { name: 'Kết quả xét duyệt', exact: true })).toContainText('QD-API-APPROVED');
  await expect(summary.locator('.school-coop-feedback')).toHaveCSS('background-color', 'rgb(241, 250, 246)');
  await expect(summary.locator('.school-coop-description > h4')).toHaveCSS('font-size', '16px');
  await expect(summary.locator('.school-coop-description > p')).toHaveCSS('font-size', '17px');
  await expect(summary.locator('.school-coop-quantity')).toHaveCSS('font-size', '16px');
  await expect(summary.locator('.school-coop-quantity')).toHaveText('3 vị trí');
  await expect(summary.getByRole('button', { name: 'Xem thêm nội dung', exact: true })).toHaveCount(0);
  await expect(summary.getByRole('button', { name: /Xem tất cả .* kỹ năng/ })).toHaveCount(0);
  await expect(open).toHaveAttribute('aria-expanded', 'true');
  await page.screenshot({ path: '../target/school-cooperation-redesign-desktop.png', fullPage: true });
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    await expect(page.locator('#main-navigation')).toBeHidden();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    await expect(open).toBeVisible();
    await page.screenshot({ path: `../target/school-cooperation-redesign-${width}.png`, fullPage: true });
  }
  await summary.getByRole('button', { name: 'Đóng chi tiết', exact: true }).click();
  await expect(summary).toHaveCount(0); await expect(open).toHaveAttribute('aria-expanded', 'false'); await expect(open).toBeFocused();
});

for (const width of [1440, 390, 320]) test(`school cooperation supports long content and many skills at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 844 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page, true);
  const description = ('Nhiệm vụ thực tập: phát triển, kiểm tra và bàn giao sản phẩm.\n' + 'X'.repeat(128) + '\n').repeat(60).slice(0, 10000);
  const skills = Array.from({ length: 50 }, (_, index) => ({ skillCode: index === 0 ? 'S'.repeat(64) : 'SKILL_' + index, minLevel: 3, minYearsPractice: 1 }));
  const preferred = Array.from({ length: 8 }, (_, index) => ({ skillCode: 'PREFERRED_' + index, minLevel: 2, minYearsPractice: null }));
  const longDemand = { ...demand, data: { ...demand.data, description, preferred,
    required: { ...demand.data.required, skills, majorCodes: Array.from({ length: 12 }, (_, index) => 'MAJOR_' + index) } } };
  const writes: string[] = []; page.on('request', request => { if (request.url().includes('/api/v1/') && request.method() !== 'GET') writes.push(request.method() + request.url()); });
  await page.route('**/api/v1/school/cooperations/' + cooperationId, route => route.fulfill({ json: { demand: longDemand, cooperation,
    feedback: { reasonCode: null, missingFields: [], decisionRef: 'QD-' + 'R'.repeat(117) } } }));
  const menu = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await menu.isVisible()) await menu.click();
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await nav.getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await nav.getByRole('button', { name: 'Duyệt hợp tác', exact: true }).click();
  await page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true }).click();
  const summary = page.locator('.school-coop-summary'), paragraph = summary.locator('.school-coop-description > p');
  const expandContent = summary.getByRole('button', { name: 'Xem thêm nội dung', exact: true });
  await expect(expandContent).toBeVisible();
  const compactHeight = (await paragraph.boundingBox())!.height;
  expect(compactHeight).toBeLessThanOrEqual(186);
  await expandContent.click();
  await expect(paragraph).not.toHaveClass(/is-clamped/);
  expect(await paragraph.textContent()).toBe(description);
  expect((await paragraph.boundingBox())!.height).toBeGreaterThan(compactHeight + 100);
  await summary.getByRole('button', { name: 'Thu gọn nội dung', exact: true }).click();
  await expect(paragraph).toHaveClass(/is-clamped/);
  const required = summary.getByRole('list', { name: 'Tiêu chí bắt buộc', exact: true });
  await expect(required.getByRole('listitem')).toHaveCount(6);
  const expandSkills = summary.getByRole('button', { name: 'Xem tất cả 50 kỹ năng', exact: true });
  await expect(expandSkills).toHaveAttribute('aria-expanded', 'false');
  await expandSkills.click();
  await expect(required.getByRole('listitem')).toHaveCount(50);
  await expect(required.getByRole('listitem').last()).toContainText('SKILL_49');
  await expect(summary.getByRole('list', { name: 'Tiêu chí ưu tiên', exact: true }).getByRole('listitem')).toHaveCount(6);
  await summary.getByRole('button', { name: 'Xem tất cả 8 kỹ năng', exact: true }).click();
  await expect(summary.getByRole('list', { name: 'Tiêu chí ưu tiên', exact: true }).getByRole('listitem')).toHaveCount(8);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.screenshot({ path: `../target/school-cooperation-long-${width}.png`, fullPage: true });
  await summary.getByRole('button', { name: 'Thu gọn kỹ năng', exact: true }).first().click();
  await expect(required.getByRole('listitem')).toHaveCount(6);
  await summary.getByRole('button', { name: 'Đóng chi tiết', exact: true }).click();
  await page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true }).click();
  await expect(summary.getByRole('list', { name: 'Tiêu chí bắt buộc', exact: true }).getByRole('listitem')).toHaveCount(6);
  await expect(summary.getByRole('button', { name: 'Xem thêm nội dung', exact: true })).toHaveAttribute('aria-expanded', 'false');
  expect(writes).toEqual([]);
});

test('school cooperation title lookups are bounded, reusable and reject foreign data', async ({ page }) => {
  await session(page, true);
  const entries = Array.from({ length: 5 }, (_, index) => ({ ...cooperation,
    cooperationId: '99999999-9999-4999-8999-' + String(index + 1).padStart(12, '0') }));
  let release!: () => void, started!: () => void; const gate = new Promise<void>(resolve => { release = resolve; });
  const firstWave = new Promise<void>(resolve => { started = resolve; });
  let active = 0, maximum = 0; const requested: string[] = [];
  await page.route('**/api/v1/school/cooperations?**', route => route.fulfill({ json: rows(entries) }));
  await page.route('**/api/v1/school/cooperations/*', async route => {
    const id = new URL(route.request().url()).pathname.split('/').pop()!;
    const index = entries.findIndex(item => item.cooperationId === id);
    expect(route.request().headers()['x-organization-id']).toBe(school);
    active++; maximum = Math.max(maximum, active); requested.push(id); if (requested.length === 3) started();
    await gate;
    active--;
    return route.fulfill({ json: { cooperation: entries[index], demand: { ...demand, data: { ...demand.data,
      schoolOrgId: index === 4 ? other : school, title: index === 4 ? 'PRIVATE-FOREIGN-TITLE' : 'Lập trình API ' + (index + 1) } }, feedback: null } });
  });
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await nav.getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await nav.getByRole('button', { name: 'Duyệt hợp tác', exact: true }).click();
  try { await firstWave; expect(requested).toHaveLength(3); } finally { release(); }
  const table = page.getByRole('table', { name: 'Danh sách đề nghị hợp tác', exact: true });
  await expect(table.getByRole('heading', { name: 'Lập trình API 4', exact: true })).toBeVisible();
  await expect(table.getByRole('heading', { name: 'Chưa tải được tên nhu cầu', exact: true })).toBeVisible();
  await expect(page.getByText('PRIVATE-FOREIGN-TITLE', { exact: true })).toHaveCount(0);
  expect(maximum).toBeLessThanOrEqual(3); expect(requested).toHaveLength(5);
  await page.getByRole('searchbox', { name: 'Tìm kiếm đề nghị hợp tác', exact: true }).fill('Lập trình API 2');
  await expect(table.locator('tbody tr')).toHaveCount(1);
  await expect(table.getByRole('heading', { name: 'Lập trình API 2', exact: true })).toBeVisible();
  expect(requested).toHaveLength(5);
});

test('school cooperation redesign preserves the decision DTO and tolerates unavailable term names', async ({ page }) => {
  await session(page, true);
  let current = { ...cooperation, state: 'SUBMITTED' }; const commands: unknown[] = [];
  await page.route('**/api/v1/terms?**', route => route.fulfill({ status: 503, json: { errorCode: 'DEPENDENCY_UNAVAILABLE' } }));
  await page.route('**/api/v1/school/cooperations?**', route => route.fulfill({ json: rows([current]) }));
  await page.route('**/api/v1/school/cooperations/' + cooperationId, route => route.fulfill({ json: { demand, cooperation: current, feedback: null } }));
  await page.route('**/api/v1/school/cooperations/' + cooperationId + '/decision', route => {
    expect(route.request().method()).toBe('PATCH'); expect(route.request().headers()['x-organization-id']).toBe(school);
    expect(route.request().headers()['idempotency-key']).toBeTruthy(); commands.push(route.request().postDataJSON());
    current = { ...current, state: 'APPROVED', version: current.version + 1 }; return route.fulfill({ json: current });
  });
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await nav.getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await nav.getByRole('button', { name: 'Duyệt hợp tác', exact: true }).click();
  await expect(page.locator('.school-coop-term')).toContainText('—');
  await page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true }).click();
  await page.getByRole('textbox', { name: 'Căn cứ quyết định', exact: true }).fill('QD-UI-REVIEW');
  await page.getByRole('button', { name: 'Lưu kết quả xét duyệt', exact: true }).click();
  await expect(page.getByRole('table', { name: 'Danh sách đề nghị hợp tác', exact: true }).getByRole('heading', { name: demand.data.title, exact: true })).toBeVisible();
  await expect(page.locator('.school-coop-state')).toHaveText('Đã duyệt');
  expect(commands).toEqual([{ expectedVersion: cooperation.version, decision: 'APPROVE', reasonCode: null, decisionRef: 'QD-UI-REVIEW', missingFields: [] }]);
});

test('school portal opens real verification and nomination screens and fits a mobile viewport', async ({ page }) => {
  await session(page, true);
  const navigation = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await navigation.getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await navigation.getByRole('button', { name: 'Xác minh doanh nghiệp', exact: true }).click();
  await expect(page.getByText('Chưa có hồ sơ xác minh trong phạm vi này.')).toBeVisible();
  await navigation.getByRole('button', { name: 'Đợt thực tập', exact: true }).click();
  await navigation.getByRole('button', { name: 'Đề xuất sinh viên', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Tìm sinh viên phù hợp', exact: true })).toBeVisible();
  await navigation.getByRole('button', { name: 'Tổng quan', exact: true }).click();
  await page.screenshot({ path: '../target/t5-school-portal.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('#main-navigation')).toBeHidden();
  await page.getByRole('button', { name: 'Mở menu điều hướng', exact: true }).click();
  await expect(page.locator('#main-navigation')).toBeVisible();
  await page.keyboard.press('Escape');
  await expect(page.locator('#main-navigation')).toBeHidden();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: '../target/t5-school-portal-mobile.png', fullPage: true });
});

for (const schoolSide of [true, false]) test(`${schoolSide ? 'school hides' : 'enterprise retains'} cooperation drafts and their filter`, async ({ page }) => {
  await session(page, schoolSide);
  const side = schoolSide ? 'school' : 'enterprise';
  await page.route(`**/api/v1/${side}/cooperations?**`, route => route.fulfill({ json: rows([{ ...cooperation, state: 'DRAFT', submissionNo: 0 }]) }));
  if (schoolSide) await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: schoolSide ? 'Duyệt hợp tác' : 'Hợp tác', exact: true }).click();
  const filter = page.getByRole('combobox', { name: 'Lọc trạng thái đề nghị', exact: true });
  await expect(filter).toBeVisible();
  await expect(filter.locator('option[value="DRAFT"]')).toHaveCount(schoolSide ? 0 : 1);
  if (schoolSide) {
    await expect(page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true })).toHaveCount(0);
    await expect(page.getByText('Đề nghị 1 · Bản nháp', { exact: true })).toHaveCount(0);
  } else {
    await filter.selectOption('DRAFT');
    await expect(page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true })).toBeVisible();
  }
});

for (const schoolSide of [true, false]) test(`${schoolSide ? 'school hides' : 'enterprise retains'} verification drafts and their filter`, async ({ page }) => {
  await session(page, schoolSide);
  const record = { verificationId: 'verification-private', enterpriseOrgId: enterprise, schoolOrgId: school, state: 'DRAFT', submissionNo: 0, version: 1,
    data: { schoolOrgId: school, previousVerificationId: null, registrationCode: 'PRIVATE-DRAFT', registeredName: 'Private draft company',
      address: 'Private draft address', contactName: 'Private contact', contactEmail: 'private@example.test', contactPhone: '0901234567', evidenceFileIds: [] },
    missingFields: [], decisionRef: null };
  await page.route(`**/api/v1/${schoolSide ? 'school' : 'enterprise'}/verifications?*`, route => route.fulfill({ json: rows([record]) }));
  if (schoolSide) await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: schoolSide ? 'Xác minh doanh nghiệp' : 'Xác minh theo trường', exact: true }).click();
  const filter = page.getByRole('combobox', { name: 'Lọc trạng thái xác minh', exact: true });
  await expect(filter).toBeVisible();
  await expect(filter.locator('option[value="DRAFT"]')).toHaveCount(schoolSide ? 0 : 1);
  if (schoolSide) {
    await expect(page.getByText(record.data.registeredName, { exact: true })).toHaveCount(0);
    await expect(page.getByText(record.data.registrationCode, { exact: true })).toHaveCount(0);
    await expect(page.getByText('Chưa có hồ sơ xác minh trong phạm vi này.', { exact: true })).toBeVisible();
  } else {
    await filter.selectOption('DRAFT');
    await expect(page.getByText(record.data.registeredName, { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Bổ sung hồ sơ', exact: true })).toBeVisible();
  }
});

test('verification selects a school from the API and saves entered data without synthetic evidence', async ({ page }) => {
  await session(page);
  let record: object | null = null;
  await page.route('**/api/v1/enterprise/demand-references/schools?*', route => route.fulfill({ json: rows([{ schoolOrgId: school, name: 'Trường từ API' }]) }));
  await page.route('**/api/v1/enterprise/verifications?*', route => route.fulfill({ json: rows(record ? [record] : []) }));
  await page.route('**/api/v1/enterprise/verifications', async route => {
    const data = route.request().postDataJSON();
    expect(data).toEqual({ schoolOrgId: school, previousVerificationId: null, registrationCode: 'CT-TEST', registeredName: 'Doanh nghiệp kiểm thử',
      address: 'Địa chỉ kiểm thử', contactName: 'Người liên hệ kiểm thử', contactEmail: 'contact@example.test', contactPhone: '0901234567', evidenceFileIds: [] });
    expect(route.request().headers()['x-organization-id']).toBe(enterprise);
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    record = { verificationId: 'verification-test', state: 'DRAFT', version: 1, data, missingFields: [], decisionRef: null };
    await route.fulfill({ status: 201, json: record });
  });
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Xác minh theo trường', exact: true }).click();
  await page.getByRole('button', { name: 'Tạo hồ sơ xác minh', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Chọn tài liệu', exact: true })).toHaveCount(0);
  await expect(page.locator('.verification-evidence-note')).toContainText('hồ sơ cần có minh chứng hợp lệ trước khi gửi xét duyệt');
  await page.getByLabel('Trường nhận hồ sơ').selectOption(school);
  for (const [label, value] of [['Mã đăng ký doanh nghiệp', 'CT-TEST'], ['Tên đăng ký', 'Doanh nghiệp kiểm thử'], ['Địa chỉ', 'Địa chỉ kiểm thử'],
    ['Người liên hệ', 'Người liên hệ kiểm thử'], ['Email liên hệ', 'contact@example.test'], ['Điện thoại liên hệ', '0901234567']]) {
    await page.getByLabel(label, { exact: true }).fill(value);
  }
  await page.getByRole('button', { name: 'Lưu hồ sơ xác minh', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi xác minh', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Dùng thông tin hồ sơ', exact: true })).toHaveCount(0);
});

test('selected cooperation stays selected when switching between candidates and placements', async ({ page }) => {
  await session(page);
  const secondId = '66666666-6666-4666-8666-666666666666';
  const secondDemand = { ...demand, demandId: 'demand-2', cooperationId: secondId, data: { ...demand.data, title: 'Nhu cầu thứ hai từ API' } };
  await page.route('**/api/v1/enterprise/cooperations?*', route => route.fulfill({ json: rows([cooperation, { ...cooperation, cooperationId: secondId, demandId: secondDemand.demandId }]) }));
  await page.route('**/api/v1/enterprise/cooperations/' + secondId, route => route.fulfill({ json: { demand: secondDemand, cooperation: { ...cooperation, cooperationId: secondId }, feedback: null } }));
  await page.route('**/api/v1/enterprise/placements/progress?*', route => {
    const base = progressRows(false);
    return route.fulfill({ json: { ...base, totalElements: 2, content: [...base.content, { ...base.content[0], demandId: secondDemand.demandId, title: secondDemand.data.title, cooperationId: secondId }] } });
  });
  const navigation = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await navigation.getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await page.getByLabel('Hợp tác đang xem', { exact: true }).selectOption(secondId);
  await expect(page.getByLabel('Hợp tác đang xem', { exact: true })).toHaveValue(secondId);
  await expect(page.locator('.enterprise-candidate-scope option:checked')).toHaveText(secondDemand.data.title);
  await expect(page.locator('.enterprise-candidate-detail .candidate-cooperation-meta')).toBeVisible();
  await expect(page.getByRole('heading', { name: secondDemand.data.title, exact: true })).toHaveCount(0);
  await navigation.getByRole('button', { name: 'Tiếp nhận thực tập', exact: true }).click();
  await expect(page.getByLabel('Hợp tác đang xem', { exact: true })).toHaveCount(0);
  await page.getByRole('row').filter({ hasText: secondDemand.data.title }).getByRole('button', { name: 'Xem tiếp nhận', exact: true }).click();
  await expect(page.getByLabel('Hợp tác đang xem', { exact: true })).toHaveValue(secondId);
  await expect(page.getByRole('heading', { name: secondDemand.data.title, exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Đóng chi tiết', exact: true }).click();
  await expect(page.getByRole('heading', { name: secondDemand.data.title, exact: true })).toHaveCount(0);
  await navigation.getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await expect(page.getByLabel('Hợp tác đang xem', { exact: true })).toHaveValue(secondId);
});

test('school portal keeps the placement conflict visible while refreshing its approved detail and candidates', async ({ page }) => {
  const crashes: string[] = [];page.on('pageerror', error => crashes.push(error.message));
  await session(page, true);let conflicted = false, candidateReads = 0, detailReads = 0;
  await page.route('**/api/v1/school/cooperations/' + cooperationId, route => {
    detailReads++;return route.fulfill({ json: { demand, cooperation, feedback: null } });
  });
  await page.route('**/api/v1/school/cooperations/' + cooperationId + '/proposals?*', route => {
    candidateReads++;return route.fulfill({ json: rows([]) });
  });
  await page.route('**/api/v1/school/placements**', route => {
    const path = new URL(route.request().url()).pathname;
    const counters = { target: 3, placed: conflicted ? 1 : 0, pending: conflicted ? 0 : 1, remaining: conflicted ? 2 : 3 };
    if (path.endsWith('/progress')) return route.fulfill({ json: progressRows(true, counters) });
    if (path.endsWith('/summary')) return route.fulfill({ json: { demandId: demand.demandId, cooperationId, ...counters } });
    return route.fulfill({ json: rows([{ placementId: 'placement-week4', schoolOrgId: school, enterpriseOrgId: enterprise, cooperationId, proposalItemId: 'proposal-week4', state: conflicted ? 'APPROVED' : 'PENDING_APPROVAL',
      version: conflicted ? 2 : 1, effectiveAt: '2027-02-01T00:00:00Z', startDate: '2027-02-01', endDate: '2027-05-01' }]) });
  });
  await page.route('**/api/v1/placements/placement-week4/approve', route => {
    conflicted = true;return route.fulfill({ status: 409, json: { errorCode: 'VERSION_CONFLICT' } });
  });
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Đợt thực tập', exact: true }).click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Tiếp nhận thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Phê duyệt tiếp nhận', exact: true }).click();
  await page.getByLabel('Căn cứ thao tác tiếp nhận').fill('QD-WEEK4');const beforeCandidates = candidateReads, beforeDetail = detailReads;
  await page.getByRole('button', { name: 'Xác nhận phê duyệt', exact: true }).click();
  await expect(page.getByText('Tiếp nhận 1 · Đã duyệt, chờ bắt đầu')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Tiếp nhận thực tập', exact: true })).toContainText('Dữ liệu vừa thay đổi.');
  await expect.poll(() => candidateReads).toBeGreaterThan(beforeCandidates);expect(detailReads).toBeGreaterThan(beforeDetail);
  await expect(page.getByLabel('Căn cứ thao tác tiếp nhận')).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Tổng hợp tiếp nhận theo phạm vi' }).getByRole('rowheader', { name: /API Java Intern/ })).toBeVisible();
  expect(crashes).toEqual([]);
});

test('placement overview can select rejected and no-cooperation demands without opening approval actions', async ({ page }) => {
  await session(page, true);
  const counters = { target: 0, placed: 0, pending: 0, reservations: 0, declined: 0, enterpriseAccepted: 0, pendingCreation: 0, pendingApproval: 0, remaining: 0, openForNomination: 0 };
  await page.route('**/api/v1/school/placements/progress?*', route => route.fulfill({ json: {
    content: [{ demandId: 'draft-only', title: 'Nhu cầu chưa có phiếu', schoolOrgId: school, schoolName: 'Trường A', enterpriseOrgId: enterprise, enterpriseName: 'Công ty A', termId: term, termCode: 'HK1', cooperationId: null, cooperationState: null, status: 'NO_COOP', counters },
      { demandId: 'rejected-only', title: 'Nhu cầu đã từ chối', schoolOrgId: school, schoolName: 'Trường A', enterpriseOrgId: enterprise, enterpriseName: 'Công ty A', termId: term, termCode: 'HK1', cooperationId: 'rejected-coop', cooperationState: 'REJECTED', status: 'REJECTED', counters }],
    page: 0, size: 10, totalElements: 2, totalPages: 1, totals: counters, schools: [], terms: [],
  } }));
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Đợt thực tập', exact: true }).click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Tiếp nhận thực tập', exact: true }).click();
  await page.getByRole('row').filter({ hasText: 'Nhu cầu chưa có phiếu' }).getByRole('button', { name: 'Xem tiếp nhận' }).click();
  await expect(page.getByRole('heading', { name: 'Nhu cầu chưa có phiếu', exact: true })).toBeVisible();
  await expect(page.getByText('Nhu cầu chưa có hợp tác được duyệt.', { exact: false })).toBeVisible();
  await page.getByRole('row').filter({ hasText: 'Nhu cầu đã từ chối' }).getByRole('button', { name: 'Xem tiếp nhận' }).click();
  await expect(page.getByText('Yêu cầu hợp tác đã bị từ chối.', { exact: false })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Phê duyệt tiếp nhận', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Tạo hồ sơ chờ phê duyệt', exact: true })).toHaveCount(0);
});

test('enterprise statistics consumes protected release and rejects raw-count payloads without querying school results', async ({ page }) => {
  await session(page);
  const matchingRequests: string[] = [];let unsafe = false;
  page.on('request', request => { if (/\/matching\//.test(request.url())) matchingRequests.push(request.url()); });
  await page.route('**/api/v1/matching/evaluate', route => route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ audience: 'ENTERPRISE', release: {
    releaseId: '00000000-0000-4000-8000-000000000077', matchRunId: '00000000-0000-4000-8000-000000000078', eligibleBand: '5-9', ...(unsafe ? { rawCount: 6 } : {}),
  } }) }));
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Thống kê bảo mật', exact: true }).click();
  await expect(page.getByLabel('Nhu cầu xem thống kê')).toHaveValue(demand.demandId);
  await expect(page.getByRole('heading', { name: 'Nhu cầu đang áp dụng', exact: true })).toHaveCount(1);
  await expect(page.getByRole('button', { name: 'Xem nhu cầu', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xem thống kê đối chiếu', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '5–9 ứng viên' })).toBeVisible();
  expect(matchingRequests.every(url => url.endsWith('/matching/evaluate'))).toBeTruthy();
  unsafe = true;
  await page.getByRole('button', { name: 'Xem thống kê đối chiếu', exact: true }).click();
  await expect(page.getByText('Chưa nhận được bản thống kê được bảo vệ hợp lệ.', { exact: true })).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: '5–9 ứng viên' })).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-enterprise-statistics.png', fullPage: true });
});

test('week 5 protected statistics forgets the previous release when the demand version changes', async ({ page }) => {
  // UI regression fixture only. Real privacy/Matching/MySQL remain covered by EnterpriseProposalIT.
  await session(page);
  let current = demand;
  const bodies: { expectedDemandVersion: number; expectedCriteriaVersion: number }[] = [];
  await page.route('**/api/v1/enterprise/demands?*', route => route.fulfill({ json: rows([current]) }));
  await page.route('**/api/v1/matching/evaluate', route => {
    bodies.push(route.request().postDataJSON());
    return route.fulfill({ json: { audience: 'ENTERPRISE', release: {
      releaseId: '00000000-0000-4000-8000-000000000077', matchRunId: '00000000-0000-4000-8000-000000000078', eligibleBand: '5-9',
    } } });
  });
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Thống kê bảo mật', exact: true }).click();
  await page.getByRole('button', { name: 'Xem thống kê đối chiếu', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '5–9 ứng viên' })).toBeVisible();
  current = { ...demand, version: 3, criteriaVersion: 2, data: { ...demand.data, title: 'Updated criteria' } };
  const refreshed = page.waitForResponse(r => r.url().includes('/enterprise/demands?'));
  await page.evaluate(() => window.dispatchEvent(new CustomEvent('cooperation-data-updated')));await refreshed;
  await expect(page.getByRole('status').filter({ hasText: '5–9 ứng viên' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Xem thống kê đối chiếu', exact: true }).click();
  await expect.poll(() => bodies.length).toBe(2);
  expect(bodies.map(body => [body.expectedDemandVersion, body.expectedCriteriaVersion])).toEqual([[2, 1], [3, 2]]);
});

test('school overview reads every page and deduplicates active students, partners and terms', async ({ page }) => {
  await session(page, true);
  let refreshed = false;
  await page.route('**/api/v1/school/placements?*', route => {
    const query = new URL(route.request().url()).searchParams;
    expect(query.get('state')).toBe('ACTIVE');expect(route.request().headers()['x-organization-id']).toBe(school);
    const pageNumber = Number(query.get('page'));
    const ids = pageNumber === 0 ? ['student-a', 'student-b'] : refreshed ? ['student-a', 'student-c'] : ['student-a'];
    return route.fulfill({ json: rows(ids.map(studentUserId => ({ studentUserId, schoolOrgId: school, state: 'ACTIVE' })), pageNumber, 2) });
  });
  await page.route('**/api/v1/school/cooperations?*', route => {
    const pageNumber = Number(new URL(route.request().url()).searchParams.get('page'));
    return route.fulfill({ json: rows([{ ...cooperation, enterpriseOrgId: pageNumber ? other : enterprise }, ...(pageNumber ? [] : [cooperation])], pageNumber, 2) });
  });
  await page.route('**/api/v1/terms?*', route => {
    const query = new URL(route.request().url()).searchParams;expect(query.get('schoolOrgId')).toBe(school);
    const pageNumber = Number(query.get('page'));
    return route.fulfill({ json: rows([{ termId: pageNumber ? 'term-next' : term, schoolOrgId: school, termCode: pageNumber ? 'HK2' : 'HK1', startDate: '2027-01-01', endDate: '2027-06-30' }], pageNumber, 2) });
  });
  await page.route('**/api/v1/school/verifications?*', route => route.fulfill({ json: rows([
    { verificationId: 'verification-1', state: 'SUBMITTED', data: { registeredName: 'API Partner 1' } },
    { verificationId: 'verification-2', state: 'SUBMITTED', data: { registeredName: 'API Partner 2' } },
  ]) }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  const cards = page.locator('.school-overview-stat');
  await expect(cards.locator('.portal-stat-title > span:first-child')).toHaveText(['Sinh viên thực tập', 'Doanh nghiệp đối tác', 'Đợt thực tập', 'Hồ sơ chờ duyệt']);
  await expect(cards.locator('.school-stat-metric strong')).toHaveText(['2', '2', '2', '2']);
  await expect(cards.locator('.school-stat-order')).toHaveText(['01', '02', '03', '04']);
  await expect(cards.locator('.school-stat-badge')).toHaveText(['SV', 'DN', 'ĐT', 'HS']);
  await page.getByRole('button', { name: 'Đợt thực tập: 2', exact: true }).click();
  const terms = page.getByRole('region', { name: 'Danh sách đợt thực tập', exact: true });
  await expect(terms.getByRole('rowheader')).toHaveText(['HK1', 'HK2']);
  await page.getByRole('button', { name: 'Đóng danh sách đợt', exact: true }).click();await expect(terms).toHaveCount(0);
  refreshed = true;await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(cards.locator('.school-stat-metric strong')).toHaveText(['3', '2', '2', '2']);
});

test('school overview uses unavailable marks for denied metrics without blocking the other cards', async ({ page }) => {
  await session(page, true);
  await page.route('**/api/v1/school/placements?*', route => route.fulfill({ status: 403, json: { errorCode: 'ACCESS_DENIED' } }));
  await page.route('**/api/v1/terms?*', route => route.fulfill({ status: 503, json: { errorCode: 'DEPENDENCY_UNAVAILABLE' } }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(page.locator('.school-overview-stat .school-stat-metric strong')).toHaveText(['—', '1', '—', '0']);
  await expect(page.getByRole('button', { name: 'Đợt thực tập: Chưa có số liệu', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Doanh nghiệp đối tác: 1', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Duyệt hợp tác', level: 2, exact: true })).toBeVisible();
});

test('notification filters and keyboard tabs use current records without a separate notification endpoint', async ({ page }) => {
  await session(page);
  const requests: string[] = [];
  page.on('request', request => { if (/\/api\/v1\/.*notifications/.test(request.url())) requests.push(request.url()); });
  await page.getByRole('button', { name: 'Mở trung tâm thông báo', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Trung tâm thông báo', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Tất cả', exact: true }).focus();
  await page.keyboard.press('ArrowRight');
  await expect(page.getByRole('tab', { name: 'Chưa đọc', exact: true })).toHaveAttribute('aria-selected', 'true');
  await page.getByLabel('Tìm thông báo', { exact: true }).fill('hồ sơ');
  await page.getByLabel('Loại thông báo', { exact: true }).selectOption('proposal');
  await expect(page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc', exact: true })).toBeEnabled();
  await expect(page.getByText('Không có thông báo phù hợp', { exact: true })).toBeVisible();
  expect(requests).toEqual([]);
  await page.screenshot({ path: '../target/t5-enterprise-notifications.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: '../target/t5-enterprise-notifications-mobile.png', fullPage: true });
});

test('batch UI reads the API and displays empty when no shared batch is visible', async ({ page }) => {
  await session(page);
  const requests: string[] = [];
  page.on('request', request => {if (request.url().includes('/proposal-batches')) {requests.push(request.url());expect(request.headers()['x-organization-id']).toBe(enterprise);}});
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', { exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).click();
  await expect(page.getByText('Chưa có dữ liệu lô đề xuất', { exact: true })).toBeVisible();
  // Focus revalidation may read again; every request must retain the selected cooperation.
  expect([...new Set(requests.map(url => new URL(url).pathname))]).toEqual([`/api/v1/enterprise/cooperations/${cooperationId}/proposal-batches`]);
  await expect(page.getByText(/DEMO-PROP|Hôm qua|07\/11\/2026/)).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-enterprise-batches.png', fullPage: true });
  await page.getByRole('button', { name: 'Xem ứng viên được chia sẻ', exact: true }).click();
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', { exact: true })).toBeVisible();
});

test('enterprise batch uses live-shaped API data and opens the same cooperation candidates', async ({ page }) => {
  await session(page);let candidateReads = 0;
  const batchId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  await page.route(`**/api/v1/enterprise/cooperations/${cooperationId}/proposal-batches?*`, route => {
    const query = new URL(route.request().url()).searchParams;
    expect(query.get('page')).toBe('0');expect(query.get('size')).toBe('10');expect(route.request().headers()['x-organization-id']).toBe(enterprise);
    return route.fulfill({ json: rows([{ batchId, cooperationId, visibleProposalCount: 1 }]) });
  });
  const candidate = { proposalItemId: 'candidate-batch', cooperationId, state: 'SHARED', version: 4, shareId: 'share-batch', reservationUntil: '2027-01-01T00:00:00Z' };
  await page.route(`**/api/v1/enterprise/cooperations/${cooperationId}/proposals?*`, route => {
    candidateReads++;expect(route.request().headers()['x-organization-id']).toBe(enterprise);return route.fulfill({ json: rows([candidate]) });
  });
  await page.route('**/api/v1/enterprise/proposals/candidate-batch/profile', route => route.fulfill({ json: {
    profileVersion: 2, validUntil: candidate.reservationUntil, allowedFields: ['majorCode'], data: { majorCode: 'IT' },
  } }));
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await expect(page.locator('.proposal-profile')).toBeVisible();
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).click();
  await expect(page.getByRole('tab', { name: 'Lô đề xuất', exact: true })).toHaveCSS('border-radius', '0px');
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).hover();
  await expect(page.getByRole('tab', { name: 'Lô đề xuất', exact: true })).toHaveCSS('background-color', 'rgb(244, 251, 248)');
  const batch = page.getByRole('region', { name: 'Lô đề xuất được chia sẻ', exact: true });
  await expect(batch.getByText(batchId, { exact: true })).toBeVisible();
  await expect(batch.getByRole('cell', { name: '1 hồ sơ được chia sẻ', exact: true })).toBeVisible();
  await expect(batch.getByText(/chưa sẵn sàng|chưa.*kết nối|Cập nhật/)).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-enterprise-batches-populated.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  const reads = candidateReads;
  await batch.getByRole('button', { name: 'Xem ứng viên trong lô', exact: true }).click();
  await expect(page.getByRole('tab', { name: 'Ứng viên được chia sẻ', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.locator('.proposal-profile')).toBeVisible();await expect.poll(() => candidateReads).toBeGreaterThan(reads);
});

test('enterprise batch refresh removes revoked data and organization switching clears the old batch', async ({ page }) => {
  await session(page);let visible = true, reads = 0;
  const batchId = 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb';
  await page.route(`**/api/v1/enterprise/cooperations/${cooperationId}/proposal-batches?*`, route => {
    reads++;return route.fulfill({ json: rows(visible ? [{ batchId, cooperationId, visibleProposalCount: 1 }] : []) });
  });
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).click();await expect(page.getByText(batchId, { exact: true })).toBeVisible();
  const before = reads;visible = false;await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByText('Chưa có dữ liệu lô đề xuất', { exact: true })).toBeVisible();expect(reads).toBeGreaterThan(before);
  await expect(page.getByText(batchId, { exact: true })).toHaveCount(0);await expect(page.getByRole('button', { name: 'Xem ứng viên trong lô', exact: true })).toHaveCount(0);
  visible = true;await page.getByRole('button', { name: 'Tải lại lô đề xuất', exact: true }).click();await expect(page.getByText(batchId, { exact: true })).toBeVisible();
  await page.getByLabel('Doanh nghiệp đang làm việc', { exact: true }).selectOption(other);
  await expect(page.getByRole('heading', { name: 'Chưa có hợp tác được duyệt', exact: true })).toBeVisible();
  await expect(page.getByText(batchId, { exact: true })).toHaveCount(0);
});

for (const failure of ['denied', 'foreign-cooperation']) test(`enterprise batches handle ${failure} without exposing rows and can retry`, async ({ page }) => {
  await session(page);let retry = false;
  const batchId = 'cccccccc-cccc-4ccc-8ccc-cccccccccccc';
  await page.route(`**/api/v1/enterprise/cooperations/${cooperationId}/proposal-batches?*`, route => !retry && failure === 'denied'
    ? route.fulfill({ status: 403, json: { errorCode: 'ACCESS_DENIED' } })
    : route.fulfill({ json: rows([{ batchId, cooperationId: !retry && failure === 'foreign-cooperation' ? other : cooperationId, visibleProposalCount: 1 }]) }));
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).click();
  await expect(page.getByText(failure === 'denied' ? /Bạn không còn quyền truy cập/ : /Chưa nhận được danh sách lô hợp lệ/)).toBeVisible();
  await expect(page.getByText(batchId, { exact: true })).toHaveCount(0);await expect(page.getByText('Chưa có dữ liệu lô đề xuất', { exact: true })).toHaveCount(0);
  retry = true;await page.getByRole('button', { name: 'Tải lại lô đề xuất', exact: true }).click();
  await expect(page.getByText(batchId, { exact: true })).toBeVisible();
});

test('switching to batch UI cannot discard an uncertain enterprise decision', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-10T10:00:00Z') });
  await session(page);
  const candidate = { proposalItemId: 'candidate-test', cooperationId, state: 'SHARED', version: 4, shareId: 'share-test', reservationUntil: '2026-11-11T00:00:00Z' };
  await page.route('**/api/v1/enterprise/cooperations/' + cooperationId + '/proposals?*', route => route.fulfill({ json: rows([candidate]) }));
  await page.route('**/api/v1/enterprise/proposals/candidate-test/profile', route => route.fulfill({ json: {
    profileVersion: 2, validUntil: candidate.reservationUntil, allowedFields: ['majorCode'], data: { majorCode: 'IT' },
  } }));
  await page.route('**/api/v1/enterprise/proposals/candidate-test/decision', route => route.fulfill({ status: 503, json: { errorCode: 'TEMPORARILY_UNAVAILABLE' } }));
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await page.locator('.proposal-workspace').getByRole('button', { name: 'Tiếp nhận thực tập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Thử lại quyết định đã gửi', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).click();
  await expect(page.getByRole('tab', { name: 'Ứng viên được chia sẻ', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('button', { name: 'Thử lại quyết định đã gửi', exact: true })).toBeVisible();
});

test('cooperation composer preserves selection on rejection and sends only the supported DTO', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands?*', route => route.fulfill({ json: rows([{ ...demand, state: 'DRAFT', cooperationId: null }]) }));
  await page.route('**/api/v1/enterprise/demand-references/schools?*', route => route.fulfill({ json: rows([{ schoolOrgId: school, name: 'Trường từ API' }]) }));
  let attempts = 0;
  await page.route('**/api/v1/enterprise/cooperations', async route => {
    expect(route.request().postDataJSON()).toEqual({ demandId: demand.demandId, expectedDemandVersion: demand.version });
    expect(route.request().headers()['x-organization-id']).toBe(enterprise);
    expect(route.request().headers()['idempotency-key']).toBeTruthy();
    attempts++;
    await route.fulfill({ status: attempts === 1 ? 400 : 201, json: attempts === 1 ? { errorCode: 'INVALID_REQUEST' } : cooperation });
  });
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(page.locator('.portal-stat').filter({ hasText: 'Bản nháp nhu cầu' }).locator('strong')).toHaveText('1');
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Hợp tác', exact: true }).click();
  await page.getByRole('button', { name: 'Soạn từ nhu cầu', exact: true }).click();
  await expect(page.getByLabel('Nhu cầu gửi hợp tác')).toHaveValue(demand.demandId);
  await expect(page.getByLabel('Trường tiếp nhận', { exact: true })).toHaveValue('Trường từ API');
  await expect(page.getByLabel('Nội dung hợp tác', { exact: true })).toHaveCount(0);
  await expect(page.getByLabel('Đầu mối phụ trách', { exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Lưu nháp', exact: true }).click();
  await expect.poll(() => attempts).toBe(1);
  await expect(page.getByLabel('Nhu cầu gửi hợp tác')).toHaveValue(demand.demandId);
  await page.screenshot({ path: '../target/t5-enterprise-cooperation-compose.png', fullPage: true });
  await page.getByRole('button', { name: 'Lưu nháp', exact: true }).click();
  await expect.poll(() => attempts).toBe(2);
  await expect(page.getByLabel('Nhu cầu gửi hợp tác')).toHaveCount(0);
  await expect(page.getByText(/READY thủ công|SNAPSHOT|T4 Skill Taxonomy/)).toHaveCount(0);
});

test('batch layout is accessible without inventing an approved cooperation', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/cooperations?*', route => route.fulfill({ json: rows([]) }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(page.locator('.portal-stat').filter({ hasText: 'Hợp tác đã duyệt' }).locator('strong')).toHaveText('0');
  const reads: string[] = [];
  page.on('request', request => { if (/\/cooperations\/[^/?]+\/(proposals|proposal-batches)/.test(request.url())) reads.push(request.url()); });
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chưa có hợp tác được duyệt', exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).click();
  await expect(page.getByText('Chưa có dữ liệu lô đề xuất', { exact: true })).toBeVisible();
  expect(reads).toEqual([]);
});

test('enterprise handoff detail uses the actual placement link and keeps placement actions read-only', async ({ page }) => {
  await session(page);
  const placementId = '88888888-8888-4888-8888-888888888888';
  const casePath = '/workspace/cases/99999999-9999-4999-8999-999999999999';
  await page.route('**/api/v1/enterprise/placements?*', route => route.fulfill({ json: rows([{
    placementId, schoolOrgId: school, enterpriseOrgId: enterprise, cooperationId, proposalItemId: 'proposal-test', state: 'ACTIVE', version: 3,
    effectiveAt: '2026-11-01T00:00:00Z', startDate: demand.data.required.dateRange.startDate, endDate: demand.data.required.dateRange.endDate,
  }]) }));
  await page.route('**/api/v1/enterprise/placements/summary?*', route => route.fulfill({ json: {
    demandId: demand.demandId, cooperationId, target: 3, placed: 1, pending: 0, remaining: 2,
  } }));
  await page.route('**/api/v1/enterprise/placements/' + placementId + '/internship-link', route => {
    expect(route.request().headers()['x-organization-id']).toBe(enterprise);
    return route.fulfill({ json: { state: 'AVAILABLE', route: casePath } });
  });
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Tiếp nhận thực tập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Kiểm tra bàn giao sang không gian thực tập', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Xem tiếp nhận', exact: true }).click();
  await page.getByRole('button', { name: 'Kiểm tra bàn giao sang không gian thực tập', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Mở hồ sơ thực tập', exact: true })).toHaveAttribute('href', casePath);
  await page.getByRole('button', { name: 'Chi tiết bàn giao', exact: true }).click();
  const detail = page.getByRole('region', { name: 'Chi tiết bàn giao không gian thực tập', exact: true });
  await expect(detail.getByText('Hồ sơ tiếp nhận thực tập', { exact: true })).toBeVisible();
  await expect(detail.getByText('Đã bàn giao', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Phê duyệt tiếp nhận', exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Kích hoạt thực tập', exact: true })).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-enterprise-handoff.png', fullPage: true });
});

for (const schoolSide of [false, true]) {
  test(`${schoolSide ? 'school' : 'enterprise'} context header displays its actual organization name and aligns controls`, async ({ page }) => {
    await page.setViewportSize({ width: 1536, height: 960 });
    await session(page, schoolSide);
    const picker = page.getByLabel(schoolSide ? 'Nhà trường đang làm việc' : 'Doanh nghiệp đang làm việc', { exact: true });
    const name = schoolSide ? 'Trường Đại học Giao thông vận tải TP.HCM' : 'Công ty FPT Software';
    await expect(picker.locator('option:checked')).toHaveText(name);
    await expect(picker).toHaveAttribute('title', name);
    const boxes = await page.locator('.portal-context-bar select, .portal-context-bar .portal-notification-shortcut, .portal-context-bar .portal-role')
      .evaluateAll(nodes => nodes.map(node => ({ y: node.getBoundingClientRect().y, height: node.getBoundingClientRect().height })));
    expect(Math.max(...boxes.map(box => box.y)) - Math.min(...boxes.map(box => box.y))).toBeLessThan(1);
    expect(boxes.every(box => Math.abs(box.height - 48) < 1)).toBeTruthy();
    await page.screenshot({ path: `../target/t5-context-${schoolSide ? 'school' : 'enterprise'}.png`, fullPage: true, animations: 'disabled' });
    if (!schoolSide) {
      await picker.selectOption(other); await expect(picker.locator('option:checked')).toHaveText('Công ty đối tác từ API');
    }
    await page.setViewportSize({ width: 390, height: 844 });
    await expect(page.locator('#main-navigation')).toBeHidden();
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    await expect(picker).toBeVisible();
    await page.screenshot({ path: `../target/t5-context-${schoolSide ? 'school' : 'enterprise'}-mobile.png`, fullPage: true, animations: 'disabled' });
  });
}

for (const width of [1440, 390, 320]) test(`school function frames separate scope, heading and content at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page, true);
  const portal = page.locator('.portal-workspace.is-school');
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  const screens = [
    ['Sinh viên', 'Danh sách & kích hoạt', 'Danh sách & kích hoạt'],
    ['Doanh nghiệp đối tác', 'Xác minh doanh nghiệp', 'Xác minh doanh nghiệp'],
    ['Doanh nghiệp đối tác', 'Duyệt hợp tác', 'Duyệt hợp tác'],
    ['Đợt thực tập', 'Đối chiếu hồ sơ', 'Đối chiếu hồ sơ'],
    ['Đợt thực tập', 'Đề xuất sinh viên', 'Đề xuất sinh viên'],
    ['Đợt thực tập', 'Tiếp nhận thực tập', 'Tiếp nhận thực tập'],
    ['', 'Thông báo', 'Trung tâm thông báo'],
  ];
  for (const [group, menu, heading] of screens) {
    const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
    if (await toggle.isVisible()) await toggle.click();
    const destination = nav.getByRole('button', { name: menu, exact: true });
    if (!await destination.isVisible() && group) await nav.getByRole('button', { name: group, exact: true }).click();
    await destination.click();
    await expect(portal.getByRole('heading', { name: heading, level: 2, exact: true })).toHaveCount(1);
    const title = portal.locator('.portal-page-heading');
    await expect(title.getByRole('heading', { name: heading, exact: true })).toBeVisible();
    await page.evaluate(() => window.scrollTo(0, 0));
    const scopeBox = (await portal.locator('.portal-context-bar').boundingBox())!;
    const titleBox = (await title.boundingBox())!;
    const contentBox = (await portal.locator('.portal-subpage-sheet').boundingBox())!;
    expect(titleBox.y).toBeGreaterThanOrEqual(scopeBox.y + scopeBox.height + 12);
    expect(contentBox.y).toBeGreaterThanOrEqual(titleBox.y + titleBox.height + 12);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  }
});

for (const width of [1440, 390, 320]) test(`enterprise page frames keep one heading and readable scoped lists at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page);
  const portal = page.locator('.portal-workspace.is-enterprise');
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  const screens = [
    ['Tổng quan', 'Tổng quan doanh nghiệp', 'overview'],
    ['Xác minh theo trường', 'Xác minh theo trường', 'verifications'],
    ['Nhu cầu thực tập', 'Nhu cầu thực tập', 'demands'],
    ['Thống kê bảo mật', 'Thống kê bảo mật', 'statistics'],
    ['Hợp tác', 'Hợp tác', 'cooperations'],
    ['Ứng viên đề xuất', 'Ứng viên đề xuất', 'proposals'],
    ['Tiếp nhận thực tập', 'Tiếp nhận thực tập', 'placements'],
    ['Thông báo', 'Trung tâm thông báo', 'notifications'],
  ];
  for (const [menu, heading, name] of screens) {
    const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
    if (await toggle.isVisible()) await toggle.click();
    await nav.getByRole('button', { name: menu, exact: true }).click();
    await expect(portal.getByRole('heading', { name: heading, exact: true })).toHaveCount(1);
    await expect(portal.locator('.enterprise-page-heading').getByRole('heading', { name: heading, exact: true })).toBeVisible();
    await expect(portal.locator('.portal-welcome')).toHaveCount(1);
    if (name !== 'overview') await expect(portal.locator('.portal-subpage-sheet .portal-page-header')).toHaveCount(0);
    if (name === 'demands' || name === 'cooperations') {
      const table = portal.locator(name === 'demands' ? '.portal-demands-table' : '.enterprise-cooperations-table');
      await expect(table.getByRole('rowheader').first()).toContainText(demand.data.title);
      if (name === 'cooperations') await expect(table.locator('td[data-label="Trường tiếp nhận"]')).toHaveText('Trường Đại học Giao thông vận tải TP.HCM');
      if (width < 700) {
        await expect(table.locator('tbody > tr').first()).toHaveCSS('display', 'grid');
        expect(await table.evaluate(node => node.scrollWidth <= node.clientWidth)).toBeTruthy();
      }
    }
    if (name === 'proposals') await expect(portal.getByLabel('Hợp tác đang xem', { exact: true }).locator('option:checked')).toHaveText(demand.data.title);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    if (width === 1440 || (width === 390 && ['demands', 'cooperations'].includes(name)))
      await page.screenshot({ path: `../target/enterprise-refined-${name}-${width}.png`, fullPage: true, animations: 'disabled' });
  }
});

test('enterprise demand cards keep long saved content readable on phones', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page);
  const title = 'Thực tập sinh phát triển nền tảng quản lý dữ liệu và tích hợp hệ thống cho doanh nghiệp tại nhiều chi nhánh';
  const description = 'Nội dung công việc thực tập và yêu cầu phối hợp với nhóm phát triển.\n'.repeat(140);
  await page.route('**/api/v1/enterprise/demands?*', route => route.fulfill({ json: rows([{ ...demand,
    data: { ...demand.data, title, description, required: { ...demand.data.required,
      skills: Array.from({ length: 18 }, (_, index) => ({ skillCode: 'CANONICAL_SKILL_WITH_A_LONG_NAME_' + index, minLevel: 3, minYearsPractice: 1 })) },
      preferred: Array.from({ length: 8 }, (_, index) => ({ skillCode: 'PREFERRED_SKILL_' + index, minLevel: 2, minYearsPractice: null })) },
  }]) }));
  await page.getByRole('button', { name: 'Mở menu điều hướng', exact: true }).click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  const card = page.locator('.portal-demands-table tbody > tr');
  await expect(card.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await expect(card.getByLabel('Tóm tắt kỹ năng tuyển dụng').locator('dd')).toHaveText(['18', '8']);
  await card.getByRole('button', { name: 'Xem chi tiết ' + title, exact: true }).click();
  const detail = page.getByRole('region', { name: title, exact: true });
  await detail.getByRole('button', { name: 'Xem tất cả 18 kỹ năng', exact: true }).click();
  await expect(detail.getByText('CANONICAL_SKILL_WITH_A_LONG_NAME_17', { exact: true })).toBeVisible();
  await detail.getByRole('button', { name: 'Xem thêm nội dung', exact: true }).click();
  await expect(detail.locator('.school-coop-description > p')).toHaveText(description);
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    expect(await card.evaluate(node => node.scrollWidth <= node.clientWidth)).toBeTruthy();
    expect(await detail.evaluate(node => node.scrollWidth <= node.clientWidth)).toBeTruthy();
  }
});

test('demand redesign filters only the current page, opens saved details and preserves draft editing', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page);
  await page.route('**/api/v1/enterprise/demand-references/terms?*', route => route.fulfill({ json: rows([
    { termId: term, termCode: 'HK1 2027', startDate: '2027-01-01', endDate: '2027-06-30' },
  ]) }));
  await page.route('**/api/v1/enterprise/demand-references/academic-catalog?*', route => route.fulfill({ json: {
    schoolOrgId: school, termId: term, academicCatalogVersion: 'v1',
  } }));
  await page.route('**/api/v1/taxonomy/skills?*', route => route.fulfill({ json: {
    taxonomyVersion: 'v1', skills: rows([]),
  } }));
  const draft = { ...demand, demandId: 'draft-redesign', state: 'DRAFT', cooperationId: null,
    data: { ...demand.data, title: 'Thực tập sinh Frontend React', location: 'Thành phố Hồ Chí Minh', benefits: 'Được hướng dẫn bởi kỹ sư trong nhóm.',
      required: { ...demand.data.required, skills: [{ skillCode: 'REACT', minLevel: 2, minYearsPractice: null }, { skillCode: 'JAVASCRIPT', minLevel: 2, minYearsPractice: null }] } } };
  const ready = { ...demand, demandId: 'ready-redesign', cooperationId: null, data: { ...demand.data, title: 'Thực tập sinh phân tích dữ liệu' } };
  const commands: string[] = [];
  page.on('request', request => { if (request.method() !== 'GET' && /\/enterprise\/demands/.test(request.url())) commands.push(request.url()); });
  await page.route('**/api/v1/enterprise/demands?*', route => route.fulfill({ json: rows([draft, ready, demand]) }));
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  const table = page.locator('.portal-demands-table');
  await expect(table.getByRole('rowheader')).toHaveCount(3);
  await page.screenshot({ path: '../target/demand-list-redesign-1440.png', fullPage: true, animations: 'disabled' });
  await page.getByLabel('Trạng thái nhu cầu', { exact: true }).selectOption('SENT');
  await expect(table.getByRole('rowheader')).toHaveCount(1);
  await expect(table.getByRole('button', { name: /Sửa bản nháp/ })).toHaveCount(0);
  await table.getByRole('button', { name: 'Xem chi tiết ' + demand.data.title, exact: true }).click();
  const detail = page.getByRole('region', { name: demand.data.title, exact: true });
  await expect(detail.getByRole('heading', { name: demand.data.title, exact: true })).toBeFocused();
  await expect(detail.getByText('Vị trí từ API', { exact: true })).toBeVisible();
  await expect(detail.getByText('Trường Đại học Giao thông vận tải TP.HCM', { exact: true })).toBeVisible();
  await page.screenshot({ path: '../target/demand-detail-redesign-1440.png', fullPage: true, animations: 'disabled' });
  await detail.getByRole('button', { name: 'Đóng chi tiết nhu cầu', exact: true }).click();
  await expect(table.getByRole('button', { name: 'Xem chi tiết ' + demand.data.title, exact: true })).toBeFocused();
  await page.getByLabel('Tìm trong trang', { exact: true }).fill('không có trong trang');
  await expect(page.getByRole('heading', { name: 'Không tìm thấy nhu cầu phù hợp', exact: true })).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: '0/3' })).toBeVisible();
  await page.getByRole('button', { name: 'Xóa bộ lọc nhu cầu', exact: true }).click();
  await page.getByLabel('Tìm trong trang', { exact: true }).fill('Hồ Chí Minh');
  await expect(table.getByRole('rowheader')).toHaveCount(1);
  await table.getByRole('button', { name: 'Sửa bản nháp ' + draft.data.title, exact: true }).click();
  const editor = page.getByRole('region', { name: 'Sửa bản nháp nhu cầu', exact: true });
  await expect(editor.getByLabel('Tên nhu cầu', { exact: true })).toHaveValue(draft.data.title);
  await expect(editor.getByLabel('Trường nhận nhu cầu', { exact: true })).toBeDisabled();
  await expect(editor.getByLabel('Mô tả công việc', { exact: true })).toHaveValue(draft.data.description);
  const headingBox = (await editor.getByRole('heading', { name: 'Sửa bản nháp nhu cầu', exact: true }).boundingBox())!;
  const contextBox = (await page.locator('.portal-context-bar').boundingBox())!;
  expect(headingBox.y).toBeGreaterThanOrEqual(contextBox.y + contextBox.height + 8);
  await page.screenshot({ path: '../target/demand-editor-redesign-1440.png', fullPage: true, animations: 'disabled' });
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    await expect(editor.getByLabel('Tên nhu cầu', { exact: true })).toHaveCSS('font-size', '16px');
    if (width === 390) await page.screenshot({ path: '../target/demand-editor-redesign-390.png', fullPage: true, animations: 'disabled' });
  }
  await editor.getByRole('button', { name: 'Đóng bản nhập', exact: true }).click();
  await expect(editor).toHaveCount(0);
  await expect(table.getByRole('button', { name: 'Sửa bản nháp ' + draft.data.title, exact: true })).toBeFocused();
  expect(commands).toEqual([]);
});

test('enterprise candidate redesign uses authorized names, folds long lists and fits phone screens', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-10T10:00:00Z') });
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page);
  const shared = { proposalItemId: 'candidate-design', cooperationId, state: 'SHARED', version: 4, shareId: 'share-design', reservationUntil: '2026-11-11T00:00:00Z' };
  const accepted = { ...shared, proposalItemId: 'candidate-private', state: 'ENTERPRISE_ACCEPTED' };
  await page.route(`**/api/v1/enterprise/cooperations/${cooperationId}/proposals?*`, route => route.fulfill({ json: rows([shared, accepted]) }));
  await page.route('**/api/v1/enterprise/proposals/candidate-design/profile', route => route.fulfill({ json: {
    profileVersion: 2, validUntil: shared.reservationUntil, allowedFields: ['displayName', 'studentCode', 'majorCode', 'skills', 'projects', 'cv'],
    data: { displayName: 'Nguyễn Minh An', studentCode: 'SV2026001', majorCode: 'SE', email: 'PRIVATE-EMAIL@example.test',
      skills: Array.from({ length: 10 }, (_, index) => ({ kind: 'CANONICAL', skill: { skillCode: index === 0 ? 'JAVA' : 'CANONICAL_SKILL_WITH_A_LONG_NAME_' + index, level: 3, yearsPractice: 1 } })),
      projects: Array.from({ length: 5 }, (_, index) => ({ projectId: 'project-' + index, title: 'Dự án kiểm thử ' + (index + 1), description: 'Phát triển ứng dụng và phối hợp với nhóm dự án.', role: 'Thành viên phát triển', skillCodes: ['JAVA', 'SQL'] })),
      cv: { fileId: '00000000-0000-4000-8000-000000000081', contentType: 'application/pdf', sizeBytes: 128000, scanStatus: 'CLEAN' },
    },
  } }));
  await page.route('**/api/v1/enterprise/proposals/candidate-private/profile', route => route.fulfill({ json: {
    profileVersion: 2, validUntil: shared.reservationUntil, allowedFields: ['majorCode'],
    data: { majorCode: 'IT', displayName: 'PRIVATE-NAME', email: 'PRIVATE-SECOND@example.test' },
  } }));
  const batchId = 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa';
  await page.route(`**/api/v1/enterprise/cooperations/${cooperationId}/proposal-batches?*`, route => route.fulfill({ json: rows([{ batchId, cooperationId, visibleProposalCount: 2 }]) }));
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Nguyễn Minh An', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Ứng viên 2', exact: true })).toBeVisible();
  await expect(page.getByText(/PRIVATE-/)).toHaveCount(0);
  const profile = page.locator('.enterprise-candidate-card').first();
  await page.screenshot({ path: '../target/enterprise-candidates-compact-1440.png', fullPage: true, animations: 'disabled' });
  await profile.getByRole('button', { name: 'Xem hồ sơ', exact: true }).click();
  await expect(profile.locator('.proposal-skills > li')).toHaveCount(6);
  await expect(profile.getByText('Dự án kiểm thử 5', { exact: true })).toHaveCount(0);
  await page.screenshot({ path: '../target/enterprise-candidates-redesign-1440.png', fullPage: true, animations: 'disabled' });
  await profile.getByRole('button', { name: 'Xem tất cả 10 kỹ năng', exact: true }).click();
  await profile.getByRole('button', { name: 'Xem tất cả 5 dự án', exact: true }).click();
  await expect(profile.locator('.proposal-skills > li')).toHaveCount(10);
  await expect(profile.getByText('Dự án kiểm thử 5', { exact: true })).toBeVisible();
  for (const width of [390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    expect(await profile.evaluate(node => node.scrollWidth <= node.clientWidth)).toBeTruthy();
    await expect(profile.getByRole('button', { name: 'Tiếp nhận thực tập', exact: true })).toBeEnabled();
    if (width === 390) await page.screenshot({ path: '../target/enterprise-candidates-redesign-390.png', fullPage: true, animations: 'disabled' });
  }
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).click();
  const batches = page.getByRole('region', { name: 'Lô đề xuất được chia sẻ', exact: true });
  await expect(batches.getByText(batchId, { exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  expect(await batches.locator('.candidate-batches-table').evaluate(node => node.scrollWidth <= node.clientWidth)).toBeTruthy();
  await page.screenshot({ path: '../target/enterprise-batches-redesign-320.png', fullPage: true, animations: 'disabled' });
  await batches.getByRole('button', { name: 'Xem ứng viên trong lô', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Nguyễn Minh An', exact: true })).toBeVisible();
});

async function openEnterpriseCooperations(page: Page) {
  const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await toggle.isVisible()) await toggle.click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Hợp tác', exact: true }).click();
  await expect(page.locator('.enterprise-cooperation-workspace')).toBeVisible();
}

test('enterprise cooperation searches real demand and school names and labels page-only counts', async ({ page }) => {
  await session(page);
  const nextSchool = '66666666-6666-4666-8666-666666666666';
  const nextDemand = { ...demand, demandId: 'design-demand', data: { ...demand.data, schoolOrgId: nextSchool, title: 'Thiết kế sản phẩm số' } };
  const nextCooperation = { ...cooperation, cooperationId: 'design-cooperation', demandId: nextDemand.demandId, schoolOrgId: nextSchool, state: 'NEEDS_CHANGES' };
  await page.route('**/api/v1/enterprise/demands?*', route => route.fulfill({ json: rows([demand, nextDemand]) }));
  await page.route('**/api/v1/enterprise/demand-references/schools?*', route => route.fulfill({ json: rows([
    { schoolOrgId: school, name: 'Trường Công nghệ' }, { schoolOrgId: nextSchool, name: 'Trường Mỹ thuật' },
  ]) }));
  await page.route('**/api/v1/enterprise/cooperations?*', route => route.fulfill({ json: rows([cooperation, nextCooperation], 0, 2) }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await openEnterpriseCooperations(page);
  const table = page.locator('.enterprise-cooperations-table');
  await expect(table.getByRole('rowheader')).toHaveCount(2);
  await expect(page.locator('.enterprise-cooperation-workspace .kpi-card-hint').first()).toHaveText('Trong trang hiện tại');
  const search = page.getByLabel('Tìm kiếm đề nghị hợp tác', { exact: true });
  await search.fill('Thiết kế sản phẩm');
  await expect(table.getByRole('rowheader')).toHaveCount(1);
  await expect(table.getByRole('rowheader')).toContainText(nextDemand.data.title);
  await search.fill('Trường Công nghệ');
  await expect(table.getByRole('rowheader')).toHaveCount(1);
  await expect(table.getByRole('rowheader')).toContainText(demand.data.title);
  await page.getByRole('button', { name: 'Xóa bộ lọc', exact: true }).click();
  await page.getByTitle('Lọc: Cần bổ sung', { exact: true }).click();
  await expect(table.getByRole('rowheader')).toHaveCount(1);
  await expect(table.getByRole('rowheader')).toContainText(nextDemand.data.title);
  await search.fill('không tồn tại');
  await expect(page.getByRole('heading', { name: 'Không tìm thấy đề nghị hợp tác phù hợp bộ lọc hiện tại.', exact: true })).toBeVisible();
});

for (const state of ['DRAFT', 'SUBMITTED', 'NEEDS_CHANGES', 'APPROVED', 'REJECTED']) test(`enterprise cooperation ${state} keeps feedback and workflow actions consistent`, async ({ page }) => {
  await session(page);
  const record = { ...cooperation, state };
  const feedback = ['NEEDS_CHANGES', 'REJECTED', 'APPROVED'].includes(state)
    ? { reasonCode: state === 'NEEDS_CHANGES' ? 'ADDITIONAL_INFO_REQUIRED' : state === 'REJECTED' ? 'REVIEW_REJECTED' : null,
      missingFields: state === 'NEEDS_CHANGES' ? ['description', 'required'] : [], decisionRef: 'QĐ-ĐỐI-TÁC-2027' } : null;
  await page.route('**/api/v1/enterprise/cooperations?*', route => route.fulfill({ json: rows([record]) }));
  await page.route('**/api/v1/enterprise/cooperations/' + cooperationId, route => route.fulfill({ json: { demand, cooperation: record, feedback } }));
  let command: { path: string; body: unknown; method: string } | undefined;
  await page.route('**/api/v1/enterprise/cooperations/' + cooperationId + '/*', route => {
    command = { path: new URL(route.request().url()).pathname, body: route.request().postDataJSON(), method: route.request().method() };
    return route.fulfill({ status: 400, json: { errorCode: 'INVALID_REQUEST' } });
  });
  await openEnterpriseCooperations(page);
  const open = page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true });
  await open.click();
  const detail = page.locator('.enterprise-cooperation-detail');
  await expect(detail.getByRole('heading', { name: demand.data.title, exact: true })).toBeFocused();
  await expect(detail.getByRole('heading', { name: 'Nội dung nhu cầu', exact: true })).toBeVisible();
  if (feedback) {
    const response = detail.getByRole('region', { name: 'Phản hồi nhà trường', exact: true });
    await expect(response.getByText(feedback.decisionRef, { exact: true })).toBeVisible();
    if (state === 'NEEDS_CHANGES') {
      await expect(response.getByText('Mô tả', { exact: true })).toBeVisible();
      await expect(response.getByText('Tiêu chí bắt buộc', { exact: true })).toBeVisible();
      await expect(response).not.toContainText('ADDITIONAL_INFO_REQUIRED');
    }
  }
  if (['DRAFT', 'NEEDS_CHANGES'].includes(state)) {
    await detail.getByRole('button', { name: 'Gửi lại đề nghị hợp tác', exact: true }).click();
    await expect.poll(() => command).toEqual({ path: '/api/v1/enterprise/cooperations/' + cooperationId + '/submit', method: 'PATCH',
      body: { expectedVersion: cooperation.version, expectedDemandVersion: demand.version } });
  } else if (state === 'REJECTED') {
    await detail.getByRole('button', { name: 'Mở lại bản nháp để bổ sung', exact: true }).click();
    await expect.poll(() => command).toEqual({ path: '/api/v1/enterprise/cooperations/' + cooperationId + '/reopen', method: 'PATCH',
      body: { expectedVersion: cooperation.version, reasonCode: 'ENTERPRISE_REOPENED' } });
  } else {
    await expect(detail.getByRole('button', { name: 'Gửi lại đề nghị hợp tác', exact: true })).toHaveCount(0);
    await expect(detail.getByRole('button', { name: 'Mở lại bản nháp để bổ sung', exact: true })).toHaveCount(0);
  }
  await detail.getByRole('button', { name: 'Đóng chi tiết', exact: true }).click();
  await expect(detail).toHaveCount(0);
  await expect(open).toBeFocused();
});

for (const width of [1440, 390, 320]) test(`enterprise cooperation handles long content and many criteria at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page);
  const longDemand = { ...demand, data: { ...demand.data,
    title: 'Phát triển nền tảng quản lý và kết nối dữ liệu doanh nghiệp',
    description: Array.from({ length: 20 }, (_, index) => `Yêu cầu ${index + 1}: Phân tích và triển khai giải pháp tích hợp dữ liệu.`).join('\n'),
    location: 'Khu công nghệ cao, Thành phố Hồ Chí Minh', benefits: 'Hỗ trợ hướng dẫn và đào tạo chuyên môn.',
    required: { ...demand.data.required, majorCodes: ['CNTT', 'KTPM'], skills: Array.from({ length: 18 }, (_, index) => ({ skillCode: 'REQUIRED-' + index, minLevel: 2, minYearsPractice: null })) },
    preferred: Array.from({ length: 9 }, (_, index) => ({ skillCode: 'PREFERRED-' + index, minLevel: null, minYearsPractice: 1 })),
  } };
  await page.route('**/api/v1/enterprise/demands?*', route => route.fulfill({ json: rows([longDemand]) }));
  await page.route('**/api/v1/enterprise/cooperations/' + cooperationId, route => route.fulfill({ json: { demand: longDemand, cooperation,
    feedback: { reasonCode: null, missingFields: [], decisionRef: 'QĐ-PHÊ-DUYỆT-2027' } } }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await openEnterpriseCooperations(page);
  await expect(page.getByRole('rowheader', { name: new RegExp(longDemand.data.title) })).toBeVisible();
  if (width < 700) {
    const card = (await page.locator('.enterprise-cooperations-table tbody tr').boundingBox())!;
    const title = (await page.locator('.enterprise-coop-name b').boundingBox())!;
    expect(title.width).toBeGreaterThan(card.width - 100);
  }
  if (width === 1440) await page.screenshot({ path: '../target/enterprise-cooperations-list-1440.png', fullPage: true, animations: 'disabled' });
  await page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true }).click();
  const detail = page.locator('.enterprise-cooperation-detail');
  const required = detail.getByRole('list', { name: 'Tiêu chí bắt buộc', exact: true });
  await expect(required.getByRole('listitem')).toHaveCount(6);
  await detail.getByRole('button', { name: 'Xem tất cả 18 kỹ năng', exact: true }).click();
  await expect(required.getByRole('listitem')).toHaveCount(18);
  await detail.getByRole('button', { name: 'Xem thêm nội dung', exact: true }).click();
  await expect(detail.getByRole('button', { name: 'Thu gọn nội dung', exact: true })).toHaveAttribute('aria-expanded', 'true');
  await expect(detail.getByText(longDemand.data.benefits, { exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  expect(await detail.evaluate(node => node.scrollWidth <= node.clientWidth)).toBeTruthy();
  await detail.getByRole('button', { name: 'Thu gọn nội dung', exact: true }).click();
  await detail.getByRole('button', { name: 'Thu gọn kỹ năng', exact: true }).click();
  await detail.scrollIntoViewIfNeeded();
  await page.screenshot({ path: `../target/enterprise-cooperations-detail-${width}.png`, fullPage: true, animations: 'disabled' });
});

test('enterprise cooperation ignores stale detail responses after changing selection', async ({ page }) => {
  await session(page);
  const second = { ...cooperation, cooperationId: 'second-cooperation', demandId: 'second-demand' };
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  let started = false;
  await page.route('**/api/v1/enterprise/cooperations?*', route => route.fulfill({ json: rows([cooperation, second]) }));
  await page.route('**/api/v1/enterprise/cooperations/' + cooperationId, async route => {
    started = true; await gate;
    await route.fulfill({ json: { demand, cooperation, feedback: null } });
  });
  await page.route('**/api/v1/enterprise/cooperations/second-cooperation', route => route.fulfill({ json: {
    demand: { ...demand, demandId: second.demandId, data: { ...demand.data, title: 'Đề nghị được chọn sau' } }, cooperation: second, feedback: null,
  } }));
  await openEnterpriseCooperations(page);
  await page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true }).click();
  await expect.poll(() => started).toBe(true);
  await page.getByRole('button', { name: 'Xem nội dung đề nghị 2', exact: true }).click();
  await expect(page.locator('.enterprise-cooperation-detail h3')).toHaveText('Đề nghị được chọn sau');
  release();
  await expect(page.locator('.enterprise-cooperation-detail h3')).toHaveText('Đề nghị được chọn sau');
  await expect(page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true })).toHaveAttribute('aria-expanded', 'false');
});

test('enterprise cooperation composer retries an unknown result with the same DTO and key', async ({ page }) => {
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page);
  await page.route('**/api/v1/enterprise/demands?*', route => route.fulfill({ json: rows([{ ...demand, state: 'DRAFT', cooperationId: null }]) }));
  const attempts: { body: unknown; key: string }[] = [];
  await page.route('**/api/v1/enterprise/demands/submit-with-cooperation', route => {
    attempts.push({ body: route.request().postDataJSON(), key: route.request().headers()['idempotency-key'] });
    expect(route.request().headers()['x-organization-id']).toBe(enterprise);
    return route.fulfill({ status: attempts.length === 1 ? 503 : 201, json: attempts.length === 1 ? { errorCode: 'DEPENDENCY_UNAVAILABLE' } : cooperation });
  });
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await openEnterpriseCooperations(page);
  await page.getByRole('button', { name: 'Soạn từ nhu cầu', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Nhu cầu đã chọn', exact: true })).toContainText(demand.data.title);
  await page.getByRole('button', { name: 'Kiểm tra và gửi', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Thử lại yêu cầu đã gửi', exact: true })).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Đóng soạn hợp tác', exact: true })).toBeDisabled();
  await expect(page.getByRole('combobox', { name: 'Nhu cầu gửi hợp tác', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Thử lại yêu cầu đã gửi', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Soạn yêu cầu hợp tác', exact: true })).toHaveCount(0);
  expect(attempts).toHaveLength(2);
  expect(attempts[0].key).toBeTruthy();
  expect(attempts[1]).toEqual(attempts[0]);
  expect(attempts[0].body).toEqual({ demandId: demand.demandId, expectedDemandVersion: demand.version });
});

for (const width of [1440, 390, 320]) test(`enterprise final review keeps verification details and long dashboard tasks usable at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await session(page);
  const verification = { verificationId: 'verification-review', enterpriseOrgId: enterprise, state: 'NEEDS_CHANGES', version: 3,
    data: { schoolOrgId: school, previousVerificationId: null, registrationCode: 'DN-2027-01',
      registeredName: 'Công ty Công nghệ và Giải pháp chuyển đổi số Quốc tế', address: 'Khu công nghệ cao, Thành phố Hồ Chí Minh',
      contactName: 'Nguyễn Minh Anh', contactEmail: 'contact@enterprise.example.test', contactPhone: '0901234567', evidenceFileIds: [] },
    missingFields: ['evidenceFileIds'], decisionRef: 'YÊU-CẦU-BỔ-SUNG-2027' };
  await page.route('**/api/v1/enterprise/verifications?*', route => route.fulfill({ json: rows([verification]) }));
  await page.route('**/api/v1/enterprise/verifications/' + verification.verificationId, route => route.fulfill({ json: verification }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(page.locator('.portal-stat').filter({ hasText: 'Xác minh cần bổ sung' }).locator('strong')).toHaveText('1');
  const task = page.locator('.enterprise-task');
  await expect(task.getByRole('heading', { name: verification.data.registeredName, exact: true })).toBeVisible();
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  if (width === 1440) await page.screenshot({ path: '../target/enterprise-overview-final-1440.png', fullPage: true, animations: 'disabled' });
  await task.getByRole('button', { name: 'Xem hồ sơ', exact: false }).click();
  const workspace = page.locator('.enterprise-verifications-workspace');
  await expect(workspace.getByRole('rowheader').first()).toContainText('Trường Đại học Giao thông vận tải TP.HCM');
  await workspace.getByLabel('Tìm kiếm hồ sơ xác minh', { exact: true }).fill('Giao thông vận tải');
  await expect(workspace.getByRole('rowheader')).toHaveCount(1);
  await expect(workspace.locator('.verification-contact').getByRole('link', { name: verification.data.contactEmail, exact: true })).toHaveAttribute('href', 'mailto:' + verification.data.contactEmail);
  await workspace.getByRole('button', { name: 'Bổ sung hồ sơ', exact: true }).click();
  const editor = page.locator('.enterprise-verification-editor');
  await expect(workspace.locator('.workflow-card-list > article')).toHaveCount(0);
  await expect(editor.getByLabel('Tên đăng ký', { exact: true })).toHaveValue(verification.data.registeredName);
  await expect(editor.getByLabel('Email liên hệ', { exact: true })).toHaveValue(verification.data.contactEmail);
  await expect(editor.getByRole('button', { name: 'Chọn tài liệu', exact: true })).toHaveCount(0);
  await expect(editor.locator('.verification-evidence-note')).toContainText('minh chứng hợp lệ');
  await expect(workspace.getByRole('button', { name: 'Gửi xác minh', exact: true })).toBeDisabled();
  if (width < 700) await expect(editor.locator('.cooperation-form-grid')).toHaveCSS('grid-template-columns', /^(?!.*px .*px).*px$/);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  expect(await editor.evaluate(node => node.scrollWidth <= node.clientWidth)).toBeTruthy();
  await page.screenshot({ path: `../target/enterprise-verification-final-${width}.png`, fullPage: true, animations: 'disabled' });
  await editor.getByLabel('Người liên hệ', { exact: true }).fill('Nội dung chưa lưu');
  page.once('dialog', dialog => dialog.dismiss());
  await workspace.getByRole('button', { name: 'Xem hồ sơ', exact: true }).click();
  await expect(editor.getByLabel('Người liên hệ', { exact: true })).toHaveValue('Nội dung chưa lưu');
  page.once('dialog', dialog => dialog.accept());
  await workspace.getByRole('button', { name: 'Xem hồ sơ', exact: true }).click();
  await expect(editor).toHaveCount(0);
  await expect(workspace.locator('.verification-contact')).toContainText(verification.data.contactName);
});

for (const [band, value] of [['SUPPRESSED', 'Được bảo vệ'], ['5-9', '5–9'], ['10-19', '10–19'], ['20+', '20+']] as const)
  test(`enterprise final review presents ${band} in plain language without running statistics on entry`, async ({ page }) => {
    await session(page);
    let requests = 0;
    await page.route('**/api/v1/matching/evaluate', route => {
      requests++;
      expect(route.request().postDataJSON()).toEqual({ demandId: demand.demandId, expectedDemandVersion: demand.version, expectedCriteriaVersion: demand.criteriaVersion });
      return route.fulfill({ json: { audience: 'ENTERPRISE', release: { releaseId: 'protected-release', matchRunId: 'protected-run', eligibleBand: band } } });
    });
    await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Thống kê bảo mật', exact: true }).click();
    const summary = page.locator('.p30-agg-section');
    await expect(summary.getByText('Chưa có kết quả. Bấm “Xem thống kê đối chiếu” để kiểm tra nhu cầu đang chọn.', { exact: true })).toBeVisible();
    expect(requests).toBe(0);
    await expect(summary.getByText(/Protected aggregate|PII|breakdown|REQUIRED|PREFERRED|batch dữ liệu|kết quả đã phát hành/)).toHaveCount(0);
    await summary.getByRole('button', { name: 'Xem thống kê đối chiếu', exact: true }).click();
    await expect(summary.locator('.p30-agg-band-value')).toHaveText(value);
    await expect(summary.locator('.p30-band-step.is-active')).toHaveCount(1);
    await expect(summary.getByRole('link', { name: 'Tải CSV tóm tắt', exact: true })).toHaveAttribute('href', '/api/v1/matching/runs/protected-run/summary.csv');
    if (band === 'SUPPRESSED') await expect(summary.getByText('SUPPRESSED', { exact: true })).toHaveCount(0);
  });

for (const [status, code] of [[503, 'PRIVACY_RELEASE_UNAVAILABLE'], [409, 'PRIVACY_RELEASE_LOCKED']] as const)
  test(`enterprise final review shows an unavailable statistic honestly for ${code}`, async ({ page }) => {
    await session(page);
    await page.route('**/api/v1/matching/evaluate', route => route.fulfill({ status, json: { errorCode: code } }));
    await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Thống kê bảo mật', exact: true }).click();
    const summary = page.locator('.p30-agg-section');
    await summary.getByRole('button', { name: 'Xem thống kê đối chiếu', exact: true }).click();
    await expect(summary.locator('.p30-agg-band-value')).toHaveText('—');
    await expect(summary.locator('.notice')).toBeVisible();
    await expect(summary.getByRole('link', { name: 'Tải CSV tóm tắt', exact: true })).toHaveCount(0);
    await expect(summary.locator('.p30-band-step.is-active')).toHaveCount(0);
  });

for (const schoolSide of [true, false]) for (const width of [1440, 1024, 768, 390, 320])
  test(`shared portal shell keeps identity, scope and motion usable for ${schoolSide ? 'school' : 'enterprise'} at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: 'no-preference' });
    await session(page, schoolSide);
    const topbar = page.locator('.workspace-main > .topbar');
    await expect(topbar).toBeVisible();
    await expect(topbar.locator('.topbar-kicker')).toHaveText(schoolSide ? 'KHÔNG GIAN NHÀ TRƯỜNG' : 'KHÔNG GIAN DOANH NGHIỆP');
    await expect(topbar.getByRole('heading', { level: 1 })).toHaveText('Tổng quan');
    await expect(page.getByRole('heading', { level: 1 })).toHaveCount(1);
    await expect(page.locator('.portal-context-bar .role-menu-trigger')).toHaveCount(0);
    const account = topbar.getByRole('button', { name: `${schoolSide ? 'Nhà trường' : 'Doanh nghiệp'} — Mở menu tài khoản`, exact: true });
    await expect(account.locator('.role-menu-label')).toHaveText(schoolSide ? 'Nhà trường' : 'Doanh nghiệp');
    await expect(account.locator('span').first()).toHaveText(schoolSide ? 'NT' : 'DN');
    if (width >= 901) {
      await expect(topbar.locator('.topbar-date')).toBeVisible();
      await expect(topbar).toHaveCSS('height', '64px');
      const frame = (await topbar.boundingBox())!, scope = (await page.locator('.portal-context-bar').boundingBox())!;
      expect(scope.y).toBeGreaterThan(frame.y + frame.height);
    }
    await account.click();
    await expect(page.locator('.role-menu-header-meta')).toContainText('test@example.test');
    await page.locator('.role-menu-popover').getByRole('button', { name: 'Tài khoản của tôi', exact: true }).focus();
    await page.keyboard.press('Escape');
    await expect(account).toBeFocused();
    await expect(page.locator('.role-menu-popover')).toHaveCount(0);
    const reload = page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true });
    await reload.hover();
    expect(await reload.evaluate(node => getComputedStyle(node, '::before').animationName)).toContain('action-light-sweep');
    await page.emulateMedia({ reducedMotion: 'reduce' });
    expect(await reload.evaluate(node => getComputedStyle(node, '::before').animationName)).toBe('none');
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    await page.screenshot({ path: `../target/portal-theme-${schoolSide ? 'school' : 'enterprise'}-${width}.png`, fullPage: true, animations: 'disabled' });
  });
