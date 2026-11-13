import { expect, test, type Page } from '@playwright/test';

const enterprise = '11111111-1111-4111-8111-111111111111', school = '22222222-2222-4222-8222-222222222222';
const other = '33333333-3333-4333-8333-333333333333', term = '44444444-4444-4444-8444-444444444444';
const coopId = '55555555-5555-4555-8555-555555555555';
const pageOf = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 });
function fixtures() {
  const data = { schoolOrgId: school, previousVerificationId: null, registrationCode: 'API-REG', registeredName: 'Doanh nghiệp từ API',
    address: 'Địa chỉ API', contactName: 'Liên hệ API', contactEmail: 'contact@example.test', contactPhone: '0901234567', evidenceFileIds: [] };
  const verification = { verificationId: 'ver-1', enterpriseOrgId: enterprise, schoolOrgId: school, state: 'NEEDS_CHANGES', version: 3, data,
    missingFields: ['address', 'evidenceFileIds'], decisionRef: 'REF-API', submittedAt: '2026-11-09T01:00:00Z', decidedAt: '2026-11-10T02:00:00Z' };
  const cooperation = { cooperationId: coopId, demandId: 'demand-1', schoolOrgId: school, enterpriseOrgId: enterprise, termId: term,
    state: 'APPROVED', submissionNo: 1, version: 3, submittedAt: '2026-11-09T03:00:00Z' };
  const demand = { demandId: 'demand-1', enterpriseOrgId: enterprise, cooperationId: coopId, state: 'READY', version: 2, criteriaVersion: 1, validationState: 'VALID', softDeletedAt: null,
    data: { schoolOrgId: school, termId: term, title: 'Vị trí từ API', quantity: 3, description: 'Nội dung API', location: null, benefits: null, deadline: '2027-01-01T00:00:00Z',
      required: { majorCodes: [], skills: [], dateRange: { startDate: '2027-02-01', endDate: '2027-05-01' } }, preferred: [], taxonomyVersion: 'v1', academicCatalogVersion: 'v1' } };
  const candidate = { proposalItemId: 'proposal-1', cooperationId: coopId, state: 'SHARED', version: 4, shareId: 'share-1', reservationUntil: '2030-01-01T00:00:00Z',
    placementId: null, shareIsActive: true, eligibilityVersion: 2, studentUserId: 'student-1' };
  return { verification, cooperation, demand, candidate, userId: 'notification-user', empty: false, profiles: [] as string[] };
}
async function session(page: Page, schoolSide = false, f = fixtures()) {
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1', '');
    const org = schoolSide ? school : enterprise, role = schoolSide ? 'SCHOOL_STAFF' : 'ENTERPRISE';
    if (path === '/cooperation/organizations') return route.fulfill({ json: schoolSide
      ? [{ id: school, name: 'Trường từ API', type: 'UNIVERSITY' }]
      : [{ id: enterprise, name: 'Doanh nghiệp từ API', type: 'ENTERPRISE' }, { id: other, name: 'Đối tác khác từ API', type: 'ENTERPRISE' }] });
    if (path === '/auth/me') return route.fulfill({ json: { userId: f.userId, email: 'notification@example.test', displayName: 'Notification tester', memberships: [{ role, organizationId: org }] } });
    if (path === '/auth/me/permissions') return route.fulfill({ json: [{ role, organizationId: org, permissions: ['cooperation:enterprise:create', 'cooperation:school:review'] },
      ...(!schoolSide ? [{ role, organizationId: other, permissions: ['cooperation:enterprise:create'] }] : [])] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-token' } });
    if (path === '/enterprise/demand-references/schools') return route.fulfill({ json: pageOf([{ schoolOrgId: school, name: 'Trường từ API' }]) });
    if (path === '/enterprise/demand-references/terms' || path === '/terms') return route.fulfill({ json: pageOf([{ termId: term, termCode: 'TERM-API' }]) });
    const visible = !f.empty && route.request().headers()['x-organization-id'] === org;
    if (path.endsWith('/verifications')) return route.fulfill({ json: pageOf(visible ? [f.verification,
      ...(!schoolSide ? [{ ...f.verification, verificationId: 'ver-2', state: 'APPROVED', version: 4 }] : [])] : []) });
    if (path.endsWith('/verifications/ver-1')) return route.fulfill({ json: f.verification });
    if (path === '/enterprise/demands') return route.fulfill({ json: pageOf(visible ? [f.demand] : []) });
    if (path.endsWith('/cooperations')) return route.fulfill({ json: pageOf(visible ? [f.cooperation,
      { ...f.cooperation, cooperationId: 'coop-other', state: schoolSide ? 'SUBMITTED' : 'REJECTED' }] : []) });
    if (path.endsWith('/cooperations/' + coopId)) return route.fulfill({ json: { demand: f.demand, cooperation: f.cooperation, feedback: null } });
    if (path.endsWith('/cooperations/coop-other')) return route.fulfill({ json: { demand: f.demand, cooperation: { ...f.cooperation, cooperationId: 'coop-other', state: 'REJECTED' }, feedback: null } });
    if (path.endsWith('/proposals')) return route.fulfill({ json: pageOf(visible ? [f.candidate] : []) });
    if (path.endsWith('/profile')) {
      f.profiles.push(path);
      return route.fulfill({ json: { profileVersion: 1, validUntil: '2030-01-01T00:00:00Z', allowedFields: ['majorCode'], data: { majorCode: 'IT-API', email: 'HIDDEN-EMAIL@example.test' } } });
    }
    if (path === '/school/placements') return route.fulfill({ json: pageOf([]) });
    if (path.endsWith('/placements/summary')) return route.fulfill({ json: { demandId: f.demand.demandId, cooperationId: coopId, target: 3, placed: 0, pending: 0, remaining: 3 } });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');
  await expect(page.getByRole('button', { name: 'Mở trung tâm thông báo', exact: true })).toBeVisible();
  return f;
}
const bellCount = (page: Page) => page.locator('.notification-count:not(.is-loading)');
const openCenter = (page: Page) => page.getByRole('button', { name: 'Mở trung tâm thông báo', exact: true }).click();

test('enterprise receives the five API-driven cases, real timestamps, and translated missing fields', async ({ page }) => {
  await session(page);
  await expect(bellCount(page)).toHaveText('5');
  await expect(page.getByText('Bạn có 5 thông báo chưa đọc', { exact: true })).toBeVisible();
  await openCenter(page);
  await expect(page.locator('.notification-center tbody tr')).toHaveCount(5);
  await expect(page.getByText('Cần bổ sung: Địa chỉ, Minh chứng', { exact: true })).toBeVisible();
  await expect(page.locator('time').first()).toHaveAttribute('datetime', '2026-11-10T02:00:00Z');
  await expect(page.getByText(/Lâm Gia Hân|Ánh Dương|Hôm qua/)).toHaveCount(0);
  await page.screenshot({ path: '../target/t5-notifications-enterprise.png', fullPage: true });
});

test('read IDs persist by organization and viewer; a new source version becomes unread', async ({ page }) => {
  const f = await session(page);
  await expect(bellCount(page)).toHaveText('5'); await openCenter(page);
  await page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc', exact: true }).click();
  await expect(bellCount(page)).toHaveCount(0);
  const saved = await page.evaluate(org => JSON.parse(localStorage.getItem(`utbridora_read_notifs_${org}`) || '[]'), enterprise);
  expect(saved).toHaveLength(5); expect(JSON.stringify(saved)).not.toContain(f.verification.data.contactEmail);
  await page.reload(); await openCenter(page); await expect(bellCount(page)).toHaveCount(0);
  f.verification.version++;
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(bellCount(page)).toHaveText('1');
  await page.getByLabel('Doanh nghiệp đang làm việc', { exact: true }).selectOption(other);
  await expect(page.getByRole('heading', { name: 'Chưa có thông báo nào.', exact: true })).toBeVisible();
  await page.getByLabel('Doanh nghiệp đang làm việc', { exact: true }).selectOption(enterprise);
  await expect(bellCount(page)).toHaveText('1');
  f.userId = 'second-user'; await page.reload(); await expect(bellCount(page)).toHaveText('5');
});

test('a verification notification reads its exact authorized record and marks only that message', async ({ page }) => {
  await session(page); await openCenter(page);
  await page.getByRole('button', { name: 'Mở hồ sơ Nhà trường yêu cầu bổ sung hồ sơ BM-03', exact: true }).click();
  await expect(page.locator('.workflow-card-list').getByRole('heading', { name: 'Doanh nghiệp từ API · Cần bổ sung', exact: true })).toBeVisible();
  await expect(bellCount(page)).toHaveText('4');
});

test('a candidate notification opens the exact revalidated shared profile without fetching private profiles for the bell', async ({ page }) => {
  const f = await session(page); await expect(bellCount(page)).toHaveText('5'); expect(f.profiles).toEqual([]);
  await openCenter(page);
  await page.getByRole('button', { name: 'Mở hồ sơ Nhà trường đã chia sẻ hồ sơ ứng viên đủ điều kiện', exact: true }).click();
  await expect(page.locator('.proposal-profile').getByText('Chuyên ngành: IT-API', { exact: true })).toBeVisible();
  expect(new Set(f.profiles)).toEqual(new Set(['/enterprise/proposals/proposal-1/profile']));
  await expect(page.getByText('HIDDEN-EMAIL@example.test', { exact: true })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Xem tất cả ứng viên', exact: true })).toBeVisible();
});

test('cooperation notifications open the exact reviewed record', async ({ page }) => {
  await session(page); await openCenter(page);
  await page.getByRole('button', { name: 'Mở hồ sơ Đề nghị hợp tác BM-02 bị từ chối', exact: true }).click();
  await expect(page.locator('.workflow-detail-drawer').getByRole('heading', { name: 'Vị trí từ API', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Mở lại bản nháp để bổ sung', exact: true })).toBeVisible();
});

test('school receives new verification, cooperation and enterprise acceptance; placement stays a separate guarded action', async ({ page }) => {
  const f = fixtures(); f.verification.state = 'SUBMITTED'; f.candidate.state = 'ENTERPRISE_ACCEPTED';
  await session(page, true, f); await expect(bellCount(page)).toHaveText('3'); await openCenter(page);
  await expect(page.locator('.notification-kind').filter({ hasText: 'Doanh nghiệp từ API vừa gửi hồ sơ xác minh mới cần duyệt' })).toBeVisible();
  await page.screenshot({ path: '../target/t5-notifications-school.png', fullPage: true });
  await page.getByRole('button', { name: 'Mở hồ sơ Doanh nghiệp đã chấp thuận tiếp nhận sinh viên', exact: true }).click();
  await expect(page.getByRole('combobox', { name: /^Ứng viên đã được doanh nghiệp đồng ý/ })).toHaveValue('proposal-1');
  await expect(page.getByRole('button', { name: 'Tạo hồ sơ chờ phê duyệt', exact: true })).toBeVisible();
});

test('empty database has no fabricated badge or notifications and handles unavailable browser storage', async ({ page }) => {
  await page.addInitScript(() => { Storage.prototype.getItem = () => { throw new Error('Storage disabled'); };
    Storage.prototype.setItem = () => { throw new Error('Storage disabled'); }; });
  const f = fixtures(); f.empty = true; await session(page, false, f); await openCenter(page);
  await expect(page.getByRole('heading', { name: 'Chưa có thông báo nào.', exact: true })).toBeVisible();
  await expect(bellCount(page)).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc', exact: true })).toBeDisabled();
  f.empty = false; await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(bellCount(page)).toHaveText('5');
  await page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc', exact: true }).click();
  await expect(bellCount(page)).toHaveCount(0);
});

test('topbar scope labels come from owner APIs and scope filters do not invent records', async ({ page }) => {
  await session(page); await page.getByLabel('Trường theo dõi', { exact: true }).selectOption(school);
  await expect(page.getByLabel('Trường theo dõi').locator('option:checked')).toHaveText('Trường từ API');
  await page.getByLabel('Kỳ theo dõi', { exact: true }).selectOption(term);
  await expect(page.getByLabel('Kỳ theo dõi').locator('option:checked')).toHaveText('TERM-API');
  await expect(bellCount(page)).toHaveText('5'); await openCenter(page);
  await page.getByLabel('Loại thông báo', { exact: true }).selectOption('verification');
  await expect(page.locator('.notification-center tbody tr')).toHaveCount(2);
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  await page.screenshot({ path: '../target/t5-notifications-mobile.png', fullPage: true });
});

test('polling receives owner state changes and removes a revoked shared-candidate notification', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-12T00:00:00Z') });
  const f = await session(page); await expect(bellCount(page)).toHaveText('5'); await openCenter(page);
  f.candidate.state = 'REVOKED'; f.verification.state = 'APPROVED'; f.verification.version++;
  await page.clock.fastForward(31000);
  await expect(page.getByText('Nhà trường đã chia sẻ hồ sơ ứng viên đủ điều kiện', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Nhà trường yêu cầu bổ sung hồ sơ BM-03', { exact: true })).toHaveCount(0);
  await expect(page.locator('.notification-center tbody tr')).toHaveCount(4);
});

test('read state synchronizes across browser tabs and a replacement share is a new unread notification', async ({ page, context }) => {
  const f = await session(page), second = await context.newPage(); await session(second, false, f);
  await expect(bellCount(page)).toHaveText('5'); await expect(bellCount(second)).toHaveText('5');
  await openCenter(page); await page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc', exact: true }).click();
  await expect(bellCount(second)).toHaveCount(0);
  f.candidate.shareId = 'replacement-share';
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(bellCount(page)).toHaveText('1'); await second.close();
});

test('an unavailable proposal source never shows a complete unread count or enables mark-all', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/cooperations/' + coopId + '/proposals?*', route => route.fulfill({ status: 403, json: { errorCode: 'ACCESS_DENIED' } }));
  await page.getByRole('button', { name: 'Tải lại dữ liệu', exact: true }).click();
  await expect(page.locator('.notification-count.is-loading')).toHaveText('—');
  await openCenter(page);
  await expect(page.getByRole('button', { name: 'Đánh dấu tất cả đã đọc', exact: true })).toBeDisabled();
  await expect(page.getByRole('heading', { name: 'Chưa có thông báo nào.', exact: true })).toHaveCount(0);
});
