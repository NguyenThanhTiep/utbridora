import { expect, test, type Page } from '@playwright/test';

// Isolated HTTP fixtures. Real authority and summary arithmetic are tested in EnterpriseProposalIT.
const fixture = '/tests/fixtures/proposals.html';
const pageOf = (content: object[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: content.length ? 1 : 0 });
const placement = { placementId: 'placement-a', schoolOrgId: 'school-1', enterpriseOrgId: 'enterprise-1', cooperationId: 'coop-1', proposalItemId: 'proposal-a',
  state: 'PENDING_APPROVAL', version: 1, effectiveAt: '2026-11-09T00:00:00Z', startDate: '2026-11-24', endDate: '2027-02-24' };
const accepted = { proposalItemId: 'proposal-a', batchId: 'batch-1', studentUserId: 'student-a', cooperationId: 'coop-1', state: 'ENTERPRISE_ACCEPTED', version: 5,
  reservationUntil: '2026-11-20T00:00:00Z', invitationId: 'invitation-a', shareId: 'share-a', placementId: null, activeConsentId: null, activeConsentVersion: null,
  activeConsentExpiresAt: null, shareableFields: [], shareIsActive: true, shareNeedsUpdate: false, shareVersion: 1, eligibilityVersion: 7 };
async function setup(page: Page) {
  await page.clock.install({ time: new Date('2026-11-10T10:00:00Z') });
  await page.route('**/api/v1/**', route => route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-token' } }));
}

for (const school of [true, false]) for (const status of [
  { target: 3, placed: 3, pending: 0, remaining: 0, code: 'FULL', label: 'Đủ chỉ tiêu' },
  { target: 3, placed: 1, pending: 1, remaining: 2, code: 'WAITING', label: 'Đang chờ tiếp nhận' },
  { target: 3, placed: 1, pending: 0, remaining: 2, code: 'SHORT', label: 'Còn thiếu chỉ tiêu' },
]) test(`${school ? 'school' : 'enterprise'} placement summary shows ${status.code} without changing the four counters`, async ({ page }) => {
  await setup(page);const side = school ? 'school' : 'enterprise';
  await page.route(`**/api/v1/${side}/placements**`, route => {
    const url = new URL(route.request().url());
    expect(route.request().headers()['x-organization-id']).toBe(`${side}-1`);
    expect(url.searchParams.get('demandId')).toBe('demand-1');
    if (url.pathname.endsWith('/summary')) return route.fulfill({ json: { ...status, demandId: 'demand-1', cooperationId: 'coop-1' } });
    expect(url.searchParams.get('cooperationId')).toBe('coop-1');
    return route.fulfill({ json: pageOf([]) });
  });
  await page.goto(fixture + (school ? '?school-placement' : '?enterprise-placement'));
  await expect(page.locator('.proposal-placement .proposal-capacity dd')).toHaveText([status.target, status.placed, status.pending, status.remaining].map(String));
  await expect(page.getByRole('status', { name: 'Tình trạng chỉ tiêu tiếp nhận' })).toHaveText(status.label);
  await expect(page.getByRole('status', { name: 'Tình trạng chỉ tiêu tiếp nhận' })).toHaveAttribute('data-state', status.code);
});

test('placement focus and visibility reload data while an open decision preserves input', async ({ page }) => {
  await setup(page);let reads = 0, state = placement.state;
  await page.route('**/api/v1/school/placements**', route => {
    if (new URL(route.request().url()).pathname.endsWith('/summary')) return route.fulfill({ json: { demandId: 'demand-1', cooperationId: 'coop-1', target: 3, placed: state === 'ACTIVE' ? 1 : 0, pending: state === 'ACTIVE' ? 0 : 1, remaining: state === 'ACTIVE' ? 2 : 3 } });
    reads++;return route.fulfill({ json: pageOf([{ ...placement, state }]) });
  });
  await page.goto(fixture + '?school-placement');await expect(page.getByRole('button', { name: 'Phê duyệt tiếp nhận', exact: true })).toBeVisible();
  const initial = reads;state = 'ACTIVE';
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByText('Tiếp nhận 1 · Đang thực tập')).toBeVisible();expect(reads).toBeGreaterThan(initial);
  const focused = reads;state = 'PENDING_APPROVAL';
  await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
  await expect(page.getByRole('button', { name: 'Phê duyệt tiếp nhận', exact: true })).toBeVisible();expect(reads).toBeGreaterThan(focused);
  await page.getByRole('button', { name: 'Phê duyệt tiếp nhận', exact: true }).click();
  await page.getByLabel('Căn cứ thao tác tiếp nhận').fill('QD-IN-PROGRESS');const editing = reads;
  await page.evaluate(() => { window.dispatchEvent(new Event('focus'));document.dispatchEvent(new Event('visibilitychange')); });
  await expect(page.getByLabel('Căn cứ thao tác tiếp nhận')).toHaveValue('QD-IN-PROGRESS');expect(reads).toBe(editing);
});

for (const conflict of [
  { code: 'VERSION_CONFLICT', action: 'approve', button: 'Phê duyệt tiếp nhận', confirm: 'Xác nhận phê duyệt', state: 'PENDING_APPROVAL', message: 'Dữ liệu vừa thay đổi.' },
  { code: 'ACTIVE_PLACEMENT_EXISTS', action: 'approve', button: 'Phê duyệt tiếp nhận', confirm: 'Xác nhận phê duyệt', state: 'PENDING_APPROVAL', message: 'Sinh viên đã có một đợt thực tập được phê duyệt' },
  { code: 'CAPACITY_EXCEEDED', action: 'approve', button: 'Phê duyệt tiếp nhận', confirm: 'Xác nhận phê duyệt', state: 'PENDING_APPROVAL', message: 'Không còn đủ chỉ tiêu.' },
  { code: 'CONSENT_INVALID', action: 'approve', button: 'Phê duyệt tiếp nhận', confirm: 'Xác nhận phê duyệt', state: 'PENDING_APPROVAL', message: 'Quyền chia sẻ của sinh viên đã thay đổi' },
  { code: 'PLACEMENT_NOT_EFFECTIVE', action: 'activate', button: 'Kích hoạt thực tập', confirm: 'Xác nhận kích hoạt', state: 'APPROVED', message: 'Chưa đến thời điểm có hiệu lực' },
  { code: 'STATE_CONFLICT', action: 'cancel', button: 'Hủy tiếp nhận', confirm: 'Xác nhận hủy tiếp nhận', state: 'ACTIVE', message: 'Đề xuất không còn ở trạng thái cho phép thao tác.' },
]) test(`placement ${conflict.code} refreshes both placements and parent candidates`, async ({ page }) => {
  await setup(page);let conflictReceived = false, placementReads = 0, candidateReads = 0;
  await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', route => {
    candidateReads++;return route.fulfill({ json: pageOf([{ ...accepted, version: conflictReceived ? 8 : 5, placementId: conflictReceived ? 'placement-a' : null }]) });
  });
  await page.route('**/api/v1/school/cooperations/coop-1/capacity', route => route.fulfill({ json: { quantity: 3, activeReservations: 1, placedCount: 0, openForNomination: 2 } }));
  await page.route('**/api/v1/school/cooperations/coop-1/publication', route => route.fulfill({ json: { cooperationId: 'coop-1', version: 3, publishedAt: null, closedAt: null } }));
  await page.route('**/api/v1/school/placements**', route => {
    if (new URL(route.request().url()).pathname.endsWith('/summary')) return route.fulfill({ json: { demandId: 'demand-1', cooperationId: 'coop-1', target: 3, placed: 0, pending: 1, remaining: 3 } });
    placementReads++;return route.fulfill({ json: pageOf([{ ...placement, state: conflictReceived ? 'CANCELLED' : conflict.state, version: conflictReceived ? 2 : 1 }]) });
  });
  await page.route(`**/api/v1/placements/placement-a/${conflict.action}`, route => {
    expect(route.request().postDataJSON().expectedVersion).toBe(1);conflictReceived = true;
    return route.fulfill({ status: 409, json: { errorCode: conflict.code, requestId: 'week4-conflict' } });
  });
  await page.goto(fixture + '?school&workflow');
  const workspace = page.getByRole('region', { name: 'Tiếp nhận thực tập', exact: true });
  await workspace.getByRole('button', { name: conflict.button, exact: true }).click();
  await page.getByLabel('Căn cứ thao tác tiếp nhận').fill('QD-WEEK4');
  if (conflict.action === 'cancel') await page.getByRole('combobox', { name: 'Lý do hủy', exact: true }).selectOption('SCHOOL_CANCELLED');
  const priorCandidates = candidateReads, priorPlacements = placementReads;
  await page.getByRole('button', { name: conflict.confirm, exact: true }).click();
  await expect(workspace).toContainText(conflict.message);
  await expect(page.getByText('Tiếp nhận 1 · Đã hủy')).toBeVisible();
  await expect.poll(() => candidateReads).toBeGreaterThan(priorCandidates);expect(placementReads).toBeGreaterThan(priorPlacements);
  await expect(page.getByLabel('Căn cứ thao tác tiếp nhận')).toHaveCount(0);
  await expect(page.getByLabel('Ứng viên đã được doanh nghiệp đồng ý').locator('option')).toHaveCount(1);
});

test('uncertain placement response preserves its key and blocks focus refresh until retry resolves', async ({ page }) => {
  await setup(page);let reads = 0;const attempts: { key: string; body: string | null }[] = [];
  await page.route('**/api/v1/school/placements**', route => {
    if (new URL(route.request().url()).pathname.endsWith('/summary')) return route.fulfill({ json: { demandId: 'demand-1', cooperationId: 'coop-1', target: 3, placed: 0, pending: 1, remaining: 3 } });
    reads++;return route.fulfill({ json: pageOf([placement]) });
  });
  await page.route('**/api/v1/placements/placement-a/approve', route => {
    attempts.push({ key: route.request().headers()['idempotency-key'], body: route.request().postData() });
    return attempts.length === 1 ? route.abort('connectionfailed') : route.fulfill({ json: { ...placement, state: 'APPROVED', version: 2 } });
  });
  await page.goto(fixture + '?school-placement');
  await page.getByRole('button', { name: 'Phê duyệt tiếp nhận', exact: true }).click();await page.getByLabel('Căn cứ thao tác tiếp nhận').fill('QD-UNCERTAIN');
  await page.getByRole('button', { name: 'Xác nhận phê duyệt', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Thử lại thao tác tiếp nhận đã gửi' })).toBeVisible();const before = reads;
  await page.evaluate(() => {window.dispatchEvent(new Event('focus'));document.dispatchEvent(new Event('visibilitychange'));});
  expect(reads).toBe(before);await expect(page.getByLabel('Căn cứ thao tác tiếp nhận')).toHaveValue('QD-UNCERTAIN');
  await page.getByRole('button', { name: 'Thử lại thao tác tiếp nhận đã gửi' }).click();
  await expect.poll(() => attempts.length).toBe(2);expect(attempts[0].key).toBeTruthy();expect(attempts[1]).toEqual(attempts[0]);
});

const operational = { target: 12, placed: 5, pending: 4, remaining: 7 };
const pipeline = { reservations: 4, declined: 2, enterpriseAccepted: 6, pendingCreation: 2, pendingApproval: 1, openForNomination: 3 };
const progressRow = { demandId: 'demand-1', title: 'Backend Java', schoolOrgId: 'school-1', schoolName: 'Trường A', enterpriseOrgId: 'enterprise-1', enterpriseName: 'Công ty A',
  termId: 'term-1', termCode: 'HK1', cooperationId: 'coop-1' as string | null, cooperationState: 'APPROVED' as string | null, status: 'WAITING', counters: operational };
function progressPage(school: boolean, status = 'WAITING', page = 0, totalPages = 1) {
  const base = status === 'FULL' ? { target: 12, placed: 12, pending: 0, remaining: 0 } : status === 'SHORT' ? { ...operational, pending: 0 }
    : ['NO_COOP', 'REJECTED'].includes(status) ? { target: 0, placed: 0, pending: 0, remaining: 0 } : operational;
  const totals = school ? { ...base, ...pipeline } : base;
  const row = { ...progressRow, status, counters: totals, cooperationId: status === 'NO_COOP' ? null : progressRow.cooperationId,
    cooperationState: status === 'NO_COOP' ? null : status === 'REJECTED' ? 'REJECTED' : 'APPROVED' };
  return { content: [row], page, size: 10, totalPages, totalElements: totalPages === 1 ? 1 : 12, totals,
    schools: [{ id: 'school-1', name: 'Trường A' }, { id: 'school-2', name: 'Trường B' }], terms: [{ id: 'term-1', name: 'HK1' }] };
}

for (const school of [true, false]) test(`${school ? 'school' : 'enterprise'} placement sends state, search and every scope filter and replaces the displayed rows`, async ({ page }) => {
  await setup(page);const side = school ? 'school' : 'enterprise', requests: URL[] = [];
  await page.route(`**/api/v1/${side}/placements**`, route => {
    const url = new URL(route.request().url());
    if (url.pathname.endsWith('/summary')) return route.fulfill({ json: { demandId: 'demand-1', cooperationId: 'coop-1', target: 3, placed: 1, pending: 1, remaining: 2 } });
    requests.push(url);expect(route.request().headers()['x-organization-id']).toBe(`${side}-1`);
    const searched = url.searchParams.get('q') === 'p-page-two';
    return route.fulfill({ json: pageOf([{ ...placement, placementId: searched ? 'p-page-two' : 'p-original', state: searched ? 'ACTIVE' : 'PENDING_APPROVAL' }]) });
  });
  await page.goto(fixture + (school ? '?school-placement' : '?enterprise-placement'));
  await expect(page.getByText('Tiếp nhận 1 · Chờ trường phê duyệt', { exact: true })).toBeVisible();
  await page.getByRole('combobox', { name: 'Trạng thái tiếp nhận', exact: true }).selectOption('ACTIVE');
  await page.getByLabel('Tìm kiếm tiếp nhận', { exact: true }).fill('p-page-two');
  await page.getByRole('button', { name: 'Tìm tiếp nhận', exact: true }).click();
  await expect.poll(() => Object.fromEntries(requests.at(-1)!.searchParams)).toMatchObject({
    demandId: 'demand-1', cooperationId: 'coop-1', termId: 'term-1', schoolOrgId: 'school-1', state: 'ACTIVE', q: 'p-page-two', page: '0',
  });
  await expect(page.getByText('Tiếp nhận 1 · Đang thực tập', { exact: true })).toBeVisible();
  await expect(page.getByText('Tiếp nhận 1 · Chờ trường phê duyệt', { exact: true })).toHaveCount(0);
});

for (const school of [true, false]) test(`${school ? 'school' : 'enterprise'} overview filters all five statuses and search and selects an approved cooperation`, async ({ page }) => {
  await setup(page);const side = school ? 'school' : 'enterprise', requests: URL[] = [];
  await page.route(`**/api/v1/${side}/placements/progress?*`, route => {
    const url = new URL(route.request().url());requests.push(url);
    expect(route.request().headers()['x-organization-id']).toBe(`${side}-1`);
    return route.fulfill({ json: progressPage(school, url.searchParams.get('status') || 'WAITING') });
  });
  await page.goto(fixture + '?progress=' + side);
  const counters = page.getByLabel('Chỉ tiêu của toàn bộ kết quả lọc');
  const commonLabels = ['Chỉ tiêu đã duyệt', 'Đã tiếp nhận', 'Chờ duyệt hoặc còn giữ chỗ', 'Còn thiếu'];
  if (school) {
    await expect(counters.locator('li')).toHaveCount(10);
    await expect(counters.getByText('Còn có thể đề cử', { exact: true })).toBeVisible();
  } else {
    await expect(counters.locator('span')).toHaveText(commonLabels);
    await expect(counters.locator('strong')).toHaveText(['12', '5', '4', '7']);
    for (const label of ['Đang giữ chỗ', 'Chờ SV hoặc consent', 'Đủ điều kiện tạo Placement', 'Đủ điều kiện tạo tiếp nhận', 'Còn có thể đề cử', 'Đã từ chối', 'Chờ phê duyệt'])
      await expect(page.getByText(label, { exact: true })).toHaveCount(0);
  }
  await page.getByRole('combobox', { name: 'Kỳ tiếp nhận', exact: true }).selectOption('term-1');
  await page.getByLabel('Tìm nhu cầu và tiếp nhận', { exact: true }).fill('Backend Java');
  await page.getByRole('button', { name: 'Tìm theo phạm vi', exact: true }).click();
  for (const [status, label] of [['FULL', 'Đủ chỉ tiêu'], ['WAITING', 'Đang chờ xử lý'], ['SHORT', 'Còn thiếu chỉ tiêu'], ['NO_COOP', 'Chưa có hợp tác'], ['REJECTED', 'Hợp tác bị từ chối']]) {
    await page.getByRole('combobox', { name: 'Tình trạng chỉ tiêu', exact: true }).selectOption(status);
    await expect.poll(() => Object.fromEntries(requests.at(-1)!.searchParams)).toMatchObject({ status, termId: 'term-1', q: 'Backend Java', page: '0' });
    await expect(page.getByRole('cell', { name: label, exact: true })).toBeVisible();
  }
  await page.getByRole('combobox', { name: 'Tình trạng chỉ tiêu', exact: true }).selectOption('FULL');
  await expect(page.getByRole('cell', { name: 'Đủ chỉ tiêu', exact: true })).toBeVisible();
  await page.evaluate(() => window.addEventListener('progress-selected', event => {document.body.dataset.selectedCooperation = (event as CustomEvent).detail.cooperationId;}));
  await page.getByRole('button', { name: 'Xem tiếp nhận', exact: true }).click();
  await expect(page.locator('body')).toHaveAttribute('data-selected-cooperation', 'coop-1');
});

test('enterprise overview keeps operational totals across pages and sends school, term and text filters', async ({ page }) => {
  await setup(page);const requests: URL[] = [];
  await page.route('**/api/v1/enterprise/placements/progress?*', route => {
    const url = new URL(route.request().url());requests.push(url);const index = Number(url.searchParams.get('page'));
    const response = progressPage(false, 'WAITING', index, 2);
    response.content[0] = { ...response.content[0], schoolOrgId: url.searchParams.get('schoolOrgId') || 'school-1', title: index ? 'Kết quả trang sau' : 'Backend Java' };
    return route.fulfill({ json: response });
  });
  await page.goto(fixture + '?progress=enterprise');const totals = page.getByLabel('Chỉ tiêu của toàn bộ kết quả lọc').locator('strong');
  await expect(totals).toHaveText(['12', '5', '4', '7']);
  await page.getByRole('button', { name: 'Tổng hợp sau', exact: true }).click();await expect(page.getByText('Kết quả trang sau')).toBeVisible();
  await expect(totals).toHaveText(['12', '5', '4', '7']);
  await page.getByRole('combobox', { name: 'Trường tiếp nhận', exact: true }).selectOption('school-2');
  await page.getByRole('combobox', { name: 'Kỳ tiếp nhận', exact: true }).selectOption('term-1');
  await page.getByLabel('Tìm nhu cầu và tiếp nhận').fill('Phiếu QD-42');
  await page.getByRole('button', { name: 'Tìm theo phạm vi', exact: true }).click();
  await expect.poll(() => Object.fromEntries(requests.at(-1)!.searchParams)).toMatchObject({ schoolOrgId: 'school-2', termId: 'term-1', q: 'Phiếu QD-42', page: '0' });
  await page.screenshot({ path: '../target/t5-week4-progress-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await page.screenshot({ path: '../target/t5-week4-progress-mobile.png', fullPage: true });
});

for (const payload of ['placement-page', 'missing-totals', 'foreign-org', 'school-counters']) test(`overview rejects ${payload} payload without crashing or displaying private counters`, async ({ page }) => {
  await setup(page);const errors: string[] = [];page.on('pageerror', error => errors.push(error.message));
  const response = progressPage(false);
  const value = payload === 'placement-page' ? pageOf([placement]) : payload === 'missing-totals' ? { ...response, totals: null }
    : payload === 'school-counters' ? progressPage(true) : { ...response, content: [{ ...response.content[0], enterpriseOrgId: 'foreign-org', title: 'PRIVATE-FOREIGN-TITLE' }] };
  await page.route('**/api/v1/enterprise/placements/progress?*', route => route.fulfill({ json: value }));
  await page.goto(fixture + '?progress=enterprise');
  await expect(page.getByRole('button', { name: 'Tải lại tổng hợp', exact: true })).toBeEnabled();
  await expect(page.getByLabel('Chỉ tiêu của toàn bộ kết quả lọc')).toHaveCount(0);
  await expect(page.getByText('PRIVATE-FOREIGN-TITLE')).toHaveCount(0);expect(errors).toEqual([]);
});

for (const provider of ['missing', 'denied', 'ready']) test(`school handoff loads ${provider} read-only evaluations for exactly its case and term`, async ({ page }) => {
  await setup(page);const calls: string[] = [];page.on('request', request => {if (request.url().includes('/api/v1/')) calls.push(request.method() + ' ' + new URL(request.url()).pathname);});
  const caseId = '00000000-0000-4000-8000-000000000099', path = '/workspace/cases/' + caseId;
  await page.route('**/api/v1/school/placements**', route => {
    const path = new URL(route.request().url()).pathname;
    return route.fulfill({ json: path.endsWith('/summary') ? { demandId: 'demand-1', cooperationId: 'coop-1', target: 3, placed: 1, pending: 0, remaining: 2 }
      : path.endsWith('/internship-link') ? { state: 'AVAILABLE', caseId, route: '/workspace/cases/' + caseId }
      : pageOf([{ ...placement, state: 'ACTIVE' }]) });
  });
  await page.route('**/api/v1/t3/school/evaluations?*', route => {
    const query = new URL(route.request().url()).searchParams;
    expect(query.get('termId')).toBe('term-1');expect(query.get('caseId')).toBe(caseId);expect(query.get('placementId')).toBe(placement.placementId);expect(route.request().headers()['x-organization-id']).toBe('school-1');
    return provider === 'ready' ? route.fulfill({ json: { content: [{ evaluationId: 'eval-1', caseId, state: 'PUBLISHED', total100: 88, revisionNo: 1, version: 2, publishedAt: null },
      { evaluationId: 'foreign', caseId: 'other-case', state: 'PUBLISHED', total100: 99, revisionNo: 1, version: 1, publishedAt: null }], totalPages: 1 } })
      : route.fulfill({ status: provider === 'missing' ? 404 : 403, json: { errorCode: provider === 'missing' ? 'RESOURCE_NOT_FOUND' : 'ACCESS_DENIED' } });
  });
  await page.goto(fixture + '?school-placement');await page.getByRole('button', { name: 'Kiểm tra bàn giao sang không gian thực tập' }).click();
  await expect(page.getByRole('link', { name: 'Mở hồ sơ thực tập', exact: true })).toHaveAttribute('href', path);
  await page.getByRole('button', { name: 'Chi tiết bàn giao', exact: true }).click();await page.getByRole('button', { name: 'Tải đánh giá từ T3', exact: true }).click();
  if (provider === 'ready') {await expect(page.getByText('Đã công bố · Lần 1 · Điểm: 88/100')).toBeVisible();await expect(page.getByText(/Điểm: 99/)).toHaveCount(0);}
  else await expect(page.getByText(provider === 'missing' ? /Đang chờ T3 cung cấp/ : /Bạn chưa được cấp quyền xem đánh giá/)).toBeVisible();
  await expect(page.getByText(/Đang chờ T1 cung cấp API/)).toBeVisible();
  expect(calls.some(call => call.includes('/admin/audit-logs'))).toBeFalsy();expect(calls.every(call => call.startsWith('GET '))).toBeTruthy();
});

const reviewCaseId = '00000000-0000-4000-8000-000000000099';
async function openSchoolFollowUp(page: Page, evaluations: unknown[]) {
  await setup(page);
  await page.route('**/api/v1/school/placements**', route => {
    const path = new URL(route.request().url()).pathname;
    return route.fulfill({ json: path.endsWith('/summary') ? { demandId: 'demand-1', cooperationId: 'coop-1', target: 3, placed: 1, pending: 0, remaining: 2 }
      : path.endsWith('/internship-link') ? { state: 'AVAILABLE', caseId: reviewCaseId, route: '/workspace/cases/' + reviewCaseId } : pageOf([{ ...placement, state: 'ACTIVE' }]) });
  });
  await page.route('**/api/v1/t3/school/evaluations?*', route => {
    const params = new URL(route.request().url()).searchParams;
    expect(params.get('caseId')).toBe(reviewCaseId);expect(params.get('placementId')).toBe(placement.placementId);expect(params.get('termId')).toBe('term-1');
    return route.fulfill({ json: { content: evaluations, totalPages: 1 } });
  });
  await page.goto(fixture + '?school-placement');await page.getByRole('button', { name: 'Kiểm tra bàn giao sang không gian thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Chi tiết bàn giao', exact: true }).click();
  return page.getByRole('region', { name: 'Theo dõi đánh giá và lịch sử', exact: true });
}

for (const mode of ['SUMMARY_ONLY', 'DETAILED']) test(`school post-handoff panels show scoped ${mode} results without enabling publication or closure`, async ({ page }) => {
  const calls: string[] = [];page.on('request', request => {if (request.url().includes('/api/v1/')) calls.push(request.method() + ' ' + request.url());});
  const panel = await openSchoolFollowUp(page, [{ evaluationId: 'summary-1', placementId: placement.placementId, publicationStatus: 'PUBLISHED', summaryScore: 86,
    visibilityMode: mode, comments: 'Nhận xét được cung cấp từ dịch vụ đánh giá.', publishedAt: null }]);
  await expect(panel.getByRole('heading', { name: 'Theo dõi sau bàn giao', exact: true })).toBeVisible();
  await expect(panel.locator('.t5-detail-grid > .t5-detail-card')).toHaveCount(4);
  await panel.getByRole('button', { name: 'Tải đánh giá từ T3', exact: true }).click();
  await expect(panel.getByText('Đã công bố · Điểm: 86/100', { exact: true })).toBeVisible();
  await expect(panel.getByText('Nhận xét được cung cấp từ dịch vụ đánh giá.', { exact: true })).toBeVisible();
  const policy = panel.getByRole('region', { name: 'Chính sách hiển thị điểm nhà trường', exact: true });
  const badge = policy.locator('.t5-card-heading > .t5-badge');
  await expect(badge).toHaveText(mode === 'SUMMARY_ONLY' ? 'Chỉ xem điểm tổng kết' : 'Xem chi tiết tiêu chí & nhận xét');
  await expect(badge).toHaveCSS('background-color', mode === 'SUMMARY_ONLY' ? 'rgb(240, 234, 250)' : 'rgb(229, 246, 237)');
  await expect(panel.locator('.t5-detail-card').first()).toHaveCSS('background-color', 'rgb(255, 255, 255)');
  await expect(panel.locator('.t5-detail-card').first()).toHaveCSS('color', 'rgb(20, 47, 64)');
  await expect(panel.locator('.t5-detail-card').first()).toHaveCSS('border-top-color', 'rgb(227, 234, 242)');
  await expect(panel.getByText('Mức độ hỗ trợ mentor', { exact: true })).toBeVisible();
  await expect(panel.getByText(/Chờ kết nối API hậu kiểm đối tác/)).toBeVisible();
  await expect(panel.getByText(/Chờ tích hợp API quyết định kết thúc hồ sơ/)).toBeVisible();
  await expect(panel.getByRole('button', { name: /Công bố điểm|Đóng hồ sơ|Chấm dứt sớm/ })).toHaveCount(0);
  expect(calls.every(call => call.startsWith('GET '))).toBeTruthy();expect(calls.some(call => call.includes('/admin/audit-logs'))).toBeFalsy();
  if (mode === 'SUMMARY_ONLY') {
    await page.screenshot({ path: '../target/t5-ui-school-followup.png', fullPage: true });
    await page.setViewportSize({ width: 390, height: 844 });await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
    await page.screenshot({ path: '../target/t5-ui-school-followup-mobile.png', fullPage: true });
  }
});

test('school post-handoff remains unconfirmed without evaluations or visibility policy', async ({ page }) => {
  const panel = await openSchoolFollowUp(page, []);
  await panel.getByRole('button', { name: 'Tải đánh giá từ T3', exact: true }).click();
  await expect(panel.getByText('Chưa có đánh giá đã công bố từ T3 cho hồ sơ thực tập này.', { exact: true })).toBeVisible();
  await expect(panel.getByText('Chưa có chính sách từ API', { exact: true })).toBeVisible();
  await expect(panel.getByLabel('Điều kiện công bố điểm').getByText('Chưa xác nhận', { exact: true })).toBeVisible();
  await expect(panel.getByText(/Điểm: \d/)).toHaveCount(0);
});

test('school post-handoff ignores results for another case and rejects invalid published scores', async ({ page }) => {
  const panel = await openSchoolFollowUp(page, [{ evaluationId: 'foreign', caseId: 'another-case', state: 'PUBLISHED', total100: 99, comments: 'PRIVATE-OTHER-CASE' }]);
  await panel.getByRole('button', { name: 'Tải đánh giá từ T3', exact: true }).click();
  await expect(panel.getByText('Chưa có đánh giá đã công bố từ T3 cho hồ sơ thực tập này.', { exact: true })).toBeVisible();
  await expect(page.getByText('PRIVATE-OTHER-CASE')).toHaveCount(0);
  await page.route('**/api/v1/t3/school/evaluations?*', route => route.fulfill({ json: { content: [{ evaluationId: 'invalid', caseId: reviewCaseId, state: 'PUBLISHED', total100: 180 }], totalPages: 1 } }));
  await panel.getByRole('button', { name: 'Tải đánh giá từ T3', exact: true }).click();
  await expect(panel.getByText(/Chưa nhận được phản hồi từ máy chủ/)).toBeVisible();
  await expect(panel.getByText(/Điểm: 180/)).toHaveCount(0);
});
