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
    if (path.endsWith('/capacity')) return route.fulfill({ json: { quantity: 3, activeReservations: 0, placedCount: 0, openForNomination: 3 } });
    if (path.endsWith('/publication')) return route.fulfill({ json: { cooperationId, version: 3, publishedAt: null, closedAt: null } });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');
  if (!signedOut) await expect(page.getByRole('heading', { name: schoolSide ? 'Tổng quan Nhà trường' : 'Tổng quan doanh nghiệp', exact: true })).toBeVisible();
}

for (const schoolSide of [false, true]) {
  const title = schoolSide ? 'Tổng quan Nhà trường' : 'Tổng quan doanh nghiệp';
  test(`${schoolSide ? 'school' : 'enterprise'} opens its overview on session restore and keeps the portal navigation on the account page`, async ({ page }) => {
    await session(page, schoolSide);
    await expect(page).toHaveTitle('Tổng quan | UT.Bridora');
    const navigation = page.getByRole('navigation', { name: 'Điều hướng chính' });
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
    await page.reload();
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

test('enterprise candidates use the approved cooperation API and switching organization clears the old scope', async ({ page }) => {
  await session(page);
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await page.getByLabel('Hợp tác đang xem', { exact: true }).selectOption(cooperationId);
  await expect(page.getByRole('heading', { name: demand.data.title, exact: true })).toBeVisible();
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', { exact: true })).toBeVisible();
  await page.getByLabel('Doanh nghiệp đang làm việc', { exact: true }).selectOption(other);
  await expect(page.getByRole('heading', { name: 'Chưa có hợp tác được duyệt', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: demand.data.title, exact: true })).toHaveCount(0);
});

test('school portal opens real verification and nomination screens and fits a mobile viewport', async ({ page }) => {
  await session(page, true);
  const navigation = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await navigation.getByRole('button', { name: 'Xác minh doanh nghiệp', exact: true }).click();
  await expect(page.getByText('Chưa có hồ sơ xác minh trong phạm vi này.')).toBeVisible();
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
  await expect(page.getByRole('button', { name: 'Chọn tài liệu', exact: true })).toBeDisabled();
  await page.getByLabel('Trường nhận hồ sơ').selectOption(school);
  for (const [label, value] of [['Mã đăng ký doanh nghiệp', 'CT-TEST'], ['Tên đăng ký', 'Doanh nghiệp kiểm thử'], ['Địa chỉ', 'Địa chỉ kiểm thử'],
    ['Người liên hệ', 'Người liên hệ kiểm thử'], ['Email liên hệ', 'contact@example.test'], ['Điện thoại liên hệ', '0901234567']]) {
    await page.getByLabel(label, { exact: true }).fill(value);
  }
  await page.getByRole('button', { name: 'Lưu hồ sơ xác minh', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Gửi xác minh', exact: true })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Dùng thông tin hồ sơ', exact: true })).toHaveCount(0);
});

test('selected cooperation stays selected when switching from candidates to placements', async ({ page }) => {
  await session(page);
  const secondId = '66666666-6666-4666-8666-666666666666';
  const secondDemand = { ...demand, demandId: 'demand-2', cooperationId: secondId, data: { ...demand.data, title: 'Nhu cầu thứ hai từ API' } };
  await page.route('**/api/v1/enterprise/cooperations?*', route => route.fulfill({ json: rows([cooperation, { ...cooperation, cooperationId: secondId, demandId: secondDemand.demandId }]) }));
  await page.route('**/api/v1/enterprise/cooperations/' + secondId, route => route.fulfill({ json: { demand: secondDemand, cooperation: { ...cooperation, cooperationId: secondId }, feedback: null } }));
  const navigation = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await navigation.getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await page.getByLabel('Hợp tác đang xem', { exact: true }).selectOption(secondId);
  await expect(page.getByRole('heading', { name: secondDemand.data.title, exact: true })).toBeVisible();
  await navigation.getByRole('button', { name: 'Tiếp nhận thực tập', exact: true }).click();
  await expect(page.getByLabel('Hợp tác đang xem', { exact: true })).toHaveValue(secondId);
  await expect(page.getByRole('heading', { name: secondDemand.data.title, exact: true })).toBeVisible();
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
  await page.getByRole('button', { name: 'Xem thống kê đối chiếu', exact: true }).click();
  await expect(page.getByRole('status').filter({ hasText: '5–9 ứng viên' })).toBeVisible();
  expect(matchingRequests.every(url => url.endsWith('/matching/evaluate'))).toBeTruthy();
  unsafe = true;
  await page.getByRole('button', { name: 'Xem thống kê đối chiếu', exact: true }).click();
  await expect(page.getByText('Chưa nhận được bản thống kê được bảo vệ hợp lệ.', { exact: true })).toBeVisible();
  await expect(page.getByRole('status').filter({ hasText: '5–9 ứng viên' })).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-enterprise-statistics.png', fullPage: true });
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

test('batch UI remains empty while the shared candidate API stays available', async ({ page }) => {
  await session(page);
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Ứng viên đề xuất', exact: true }).click();
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', { exact: true })).toBeVisible();
  await page.getByRole('tab', { name: 'Lô đề xuất', exact: true }).click();
  await expect(page.getByText('Chưa có dữ liệu lô đề xuất', { exact: true })).toBeVisible();
  await expect(page.getByText(/DEMO-PROP|Hôm qua|07\/11\/2026/)).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-enterprise-batches.png', fullPage: true });
  await page.getByRole('button', { name: 'Xem ứng viên được chia sẻ', exact: true }).click();
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', { exact: true })).toBeVisible();
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
  await expect(page.getByLabel('Nội dung hợp tác', { exact: true })).toBeDisabled();
  await expect(page.getByLabel('Đầu mối phụ trách', { exact: true })).toBeDisabled();
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
  page.on('request', request => { if (/\/cooperations\/[^/?]+\/proposals/.test(request.url())) reads.push(request.url()); });
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
  await page.getByRole('button', { name: 'Kiểm tra bàn giao sang không gian thực tập', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Mở hồ sơ thực tập', exact: true })).toHaveAttribute('href', casePath);
  await page.getByRole('button', { name: 'Chi tiết bàn giao', exact: true }).click();
  const detail = page.getByRole('region', { name: 'Chi tiết bàn giao không gian thực tập', exact: true });
  await expect(detail.getByText(placementId, { exact: true })).toBeVisible();
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
