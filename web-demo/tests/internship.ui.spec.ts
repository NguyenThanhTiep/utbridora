import { test, expect, type Page } from '@playwright/test';
import { cohort, emptyGroup, grants, group, groupWithRepeatedMentors, hr, ids, me, mentor } from './fixtures/internship';

// UI fixture tests only: these do not establish backend assignment authorization.
const fixtureUrl = '/tests/fixtures/internship.html';
async function defaults(page: Page) {
  await page.clock.install({ time: new Date('2026-11-05T10:00:00Z') });
  await page.route('**/api/v1/test-only/t3/assignments', route => route.fulfill({ json: [hr, mentor] }));
  await page.route('**/api/v1/test-only/t3/cohorts/*', route => route.fulfill({ json: [cohort] }));
  await page.route('**/api/v1/test-only/t3/groups/**', route => route.fulfill({ json: [group] }));
}
async function choose(page: Page, assignment = ids.hr) {
  await page.getByLabel('Phân công đang làm việc').selectOption(assignment);
  await page.getByLabel('Đợt thực tập', { exact: true }).selectOption(ids.cohort);
}

test('T3 UI fixture: HR selects assignment, cohort and group; navigation returns to list', async ({ page }) => {
  await defaults(page); await page.goto(fixtureUrl);
  await expect(page.getByLabel('Phân công đang làm việc')).toHaveValue('');
  await expect(page.getByLabel('Đợt thực tập', { exact: true })).toHaveCount(0);
  await choose(page); await page.getByRole('button', { name: 'Xem nhóm N-01' }).click();
  await expect(page.getByRole('heading', { name: 'Nhóm N-01' })).toBeVisible();
  await expect(page.locator('.internship-facts dd').first()).toHaveText('1');
  await page.getByRole('button', { name: 'Về danh sách nhóm' }).click();
  await expect(page.getByRole('button', { name: 'Xem nhóm N-01' })).toBeVisible();
});

test('T3 UI fixture: Mentor only opens the assigned group', async ({ page }) => {
  await defaults(page); await page.goto(fixtureUrl); await choose(page, ids.mentor);
  await expect(page.getByText('KHÔNG GIAN MENTOR', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xem nhóm N-01' }).click();
  await expect(page.getByRole('heading', { name: 'Nhóm N-01' })).toBeVisible();
});

test('T3 UI contract fixture: repeated mentor IDs count people once in cards and details', async ({ page }) => {
  await defaults(page);
  await page.route('**/api/v1/test-only/t3/groups/**', route => route.fulfill({ json: [groupWithRepeatedMentors] }));
  await page.goto(fixtureUrl); await choose(page, ids.mentor);
  await expect(page.getByText('1 hồ sơ thực tập · 2 mentor', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xem nhóm N-01' }).click();
  await expect(page.locator('.internship-facts > div').filter({ hasText: 'Mentor được phân công' }).locator('dd')).toHaveText('2');
  await page.getByRole('button', { name: 'Về danh sách nhóm' }).click();
  await expect(page.getByText('1 hồ sơ thực tập · 2 mentor', { exact: true })).toBeVisible();
});

test('T3 UI contract fixture: HR can view an existing group with empty member lists', async ({ page }) => {
  await defaults(page);
  await page.route('**/api/v1/test-only/t3/groups/**', route => route.fulfill({ json: [emptyGroup] }));
  await page.goto(fixtureUrl); await choose(page);
  await expect(page.getByText('0 hồ sơ thực tập · 0 mentor', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xem nhóm N-01' }).click();
  await expect(page.locator('.internship-group-detail .internship-facts dd')).toHaveText(['0', '0']);
});

test('T3 UI fixture: foreign group response fails closed without rendering names or counts', async ({ page }) => {
  await defaults(page);
  await page.route('**/api/v1/test-only/t3/groups/**', route => route.fulfill({ json: [group,
    { ...group, groupId: ids.other, code: 'PRIVATE-FOREIGN-GROUP' }] }));
  await page.goto(fixtureUrl); await choose(page, ids.mentor);
  await expect(page.getByText('Không thể xác nhận phạm vi dữ liệu.', { exact: false })).toBeVisible();
  await expect(page.getByText('PRIVATE-FOREIGN-GROUP')).toHaveCount(0);
  await expect(page.getByRole('button', { name: /Xem nhóm/ })).toHaveCount(0);
});

test('T3 UI fixture: foreign school cohort is never rendered', async ({ page }) => {
  await defaults(page);
  await page.route('**/api/v1/test-only/t3/cohorts/*', route => route.fulfill({ json: [{ ...cohort, schoolOrgId: ids.other, name: 'PRIVATE-SCHOOL' }] }));
  await page.goto(fixtureUrl); await page.getByLabel('Phân công đang làm việc').selectOption(ids.hr);
  await expect(page.getByText('Không thể xác nhận phạm vi dữ liệu.', { exact: false })).toBeVisible();
  await expect(page.getByText('PRIVATE-SCHOOL')).toHaveCount(0);
});

test('T3 UI fixture: no valid assignment does not request cohorts or groups', async ({ page }) => {
  await defaults(page); let reads = 0;
  await page.route('**/api/v1/test-only/t3/assignments', route => route.fulfill({ json: [{ ...hr, active: false }] }));
  page.on('request', request => { if (/test-only\/t3\/(cohorts|groups)/.test(request.url())) reads++; });
  await page.goto(fixtureUrl);
  await expect(page.getByRole('heading', { name: 'Chưa có phân công phù hợp' })).toBeVisible();
  expect(reads).toBe(0);
});

test('T3 UI fixture: empty cohorts and groups are distinguished', async ({ page }) => {
  await defaults(page); let emptyCohorts = true;
  await page.route('**/api/v1/test-only/t3/cohorts/*', route => route.fulfill({ json: emptyCohorts ? [] : [cohort] }));
  await page.route('**/api/v1/test-only/t3/groups/**', route => route.fulfill({ json: [] }));
  await page.goto(fixtureUrl); await page.getByLabel('Phân công đang làm việc').selectOption(ids.hr);
  await expect(page.getByRole('heading', { name: 'Chưa có đợt thực tập' })).toBeVisible();
  emptyCohorts = false; await page.getByRole('button', { name: 'Tải lại phân công' }).click(); await choose(page);
  await expect(page.getByRole('heading', { name: 'Chưa có nhóm được phân công' })).toBeVisible();
});

test('T3 UI fixture: loading, safe error and retry', async ({ page }) => {
  await defaults(page); let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; }); let first = true;
  await page.route('**/api/v1/test-only/t3/assignments', async route => {
    if (first) { first = false; await gate; await route.fulfill({ status: 500, json: { message: 'PRIVATE SQL ERROR' } }); }
    else await route.fulfill({ json: [hr] });
  });
  await page.goto(fixtureUrl);
  await expect(page.getByText('Đang xác nhận phân công…')).toBeVisible(); release();
  await expect(page.getByRole('heading', { name: 'Chưa tải được dữ liệu' })).toBeVisible();
  await expect(page.getByText('PRIVATE SQL ERROR')).toHaveCount(0);
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByLabel('Phân công đang làm việc')).toBeVisible();
});

test('T3 UI fixture: forbidden group read clears data and offers assignment refresh', async ({ page }) => {
  await defaults(page);
  await page.route('**/api/v1/test-only/t3/groups/**', route => route.fulfill({ status: 403, json: { error: 'ACCESS_DENIED' } }));
  await page.goto(fixtureUrl); await choose(page);
  await expect(page.getByRole('heading', { name: 'Không có quyền truy cập' })).toBeVisible();
  await expect(page.getByRole('button', { name: /Xem nhóm/ })).toHaveCount(0);
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await expect(page.getByLabel('Phân công đang làm việc')).toHaveValue('');
});

test('T3 UI fixture: changing assignment discards delayed responses from the previous scope', async ({ page }) => {
  await defaults(page); let release!: () => void; let started!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const requested = new Promise<void>(resolve => { started = resolve; });
  await page.route('**/api/v1/test-only/t3/cohorts/' + ids.hr, async route => {
    started(); await gate; await route.fulfill({ json: [{ ...cohort, name: 'OLD-SCOPE-RESPONSE' }] });
  });
  await page.goto(fixtureUrl); await page.getByLabel('Phân công đang làm việc').selectOption(ids.hr); await requested;
  await page.getByLabel('Phân công đang làm việc').selectOption(ids.mentor);
  await page.getByLabel('Đợt thực tập', { exact: true }).selectOption(ids.cohort);
  await expect(page.getByRole('button', { name: 'Xem nhóm N-01' })).toBeVisible();
  release(); await expect(page.getByText('OLD-SCOPE-RESPONSE')).toHaveCount(0);
});

test('T3 UI fixture: exact assignment expiry removes already open group', async ({ page }) => {
  await defaults(page);
  await page.route('**/api/v1/test-only/t3/assignments', route => route.fulfill({ json: [{ ...hr, validUntil: '2026-11-05T10:00:10Z' }] }));
  await page.goto(fixtureUrl); await choose(page);
  await expect(page.getByRole('button', { name: 'Xem nhóm N-01' })).toBeVisible();
  await page.clock.fastForward(10_001);
  await expect(page.getByRole('heading', { name: 'Chưa có phân công phù hợp' })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xem nhóm N-01' })).toHaveCount(0);
});

test('T3 UI fixture: assignment refresh clears an open group after revocation', async ({ page }) => {
  await defaults(page); let revoked = false;
  await page.route('**/api/v1/test-only/t3/assignments', route => route.fulfill({ json: revoked ? [] : [hr] }));
  await page.goto(fixtureUrl); await choose(page);
  await page.getByRole('button', { name: 'Xem nhóm N-01' }).click();
  revoked = true; await page.getByRole('button', { name: 'Tải lại phân công' }).click();
  await expect(page.getByRole('heading', { name: 'Chưa có phân công phù hợp' })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Nhóm N-01' })).toHaveCount(0);
});

test('T3 UI fixture: changing cohort discards old groups including delayed responses', async ({ page }) => {
  await defaults(page);
  await page.route('**/api/v1/test-only/t3/cohorts/*', route => route.fulfill({ json: [cohort, { ...cohort, cohortId: ids.other, code: 'TT-OTHER' }] }));
  let release!: () => void; let started!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const requested = new Promise<void>(resolve => { started = resolve; });
  await page.route('**/api/v1/test-only/t3/groups/**', async route => {
    if (route.request().url().endsWith(ids.cohort)) {
      started(); await gate; await route.fulfill({ json: [{ ...group, code: 'OLD-GROUP' }] });
    } else await route.fulfill({ json: [{ ...group, cohortId: ids.other, code: 'CURRENT-GROUP' }] });
  });
  await page.goto(fixtureUrl); await choose(page); await requested;
  await page.getByLabel('Đợt thực tập', { exact: true }).selectOption(ids.other);
  await expect(page.getByRole('button', { name: 'Xem nhóm CURRENT-GROUP' })).toBeVisible();
  release(); await expect(page.getByText('OLD-GROUP', { exact: false })).toHaveCount(0);
});

test('T3 UI fixture: responsive group view and keyboard navigation', async ({ page }) => {
  await defaults(page); await page.goto(fixtureUrl); await choose(page);
  for (const width of [1280, 390, 320]) {
    await page.setViewportSize({ width, height: 844 });
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - innerWidth)).toBeLessThanOrEqual(1);
    await page.screenshot({ path: `test-results/t3-03-groups-${width}.png`, fullPage: true });
  }
  await page.getByRole('button', { name: 'Xem nhóm N-01' }).focus(); await page.keyboard.press('Enter');
  await expect(page.getByRole('button', { name: 'Về danh sách nhóm' })).toBeVisible();
});

test('T3 shell UI mock: enterprise loads current assignments; logout removes it', async ({ page }) => {
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: me }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: grants }));
  await page.route('**/api/v1/t3/context-assignments/me', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-only' } }));
  await page.route('**/api/v1/auth/logout', route => route.fulfill({ status: 204 }));
  const businessRequests: string[] = [];
  page.on('request', request => { const path = new URL(request.url()).pathname; if (path.startsWith('/api/v1/t3/')) businessRequests.push(path); });
  await page.goto('/'); await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chưa có phân công phù hợp' })).toBeVisible();
  expect([...new Set(businessRequests)]).toEqual(['/api/v1/t3/context-assignments/me']);
  await page.getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Không gian thực tập', exact: true })).toHaveCount(0);
});

test('DEV05 HR creates cohort, intakes Placement and confirms a versioned grouping plan', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-19T10:00:00Z') });
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: me }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: grants }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } }));
  await page.route('**/api/v1/t3/context-assignments/me', route => route.fulfill({ json: [hr] }));
  let created = false;
  const requests: { path: string; body: Record<string, unknown>; context: string | undefined; key: string | undefined }[] = [];
  await page.route('**/api/v1/t3/cohorts**', route => {
    const request = route.request(), path = new URL(request.url()).pathname;
    if (request.method() === 'GET' && path === '/api/v1/t3/cohorts') {
      return route.fulfill({ json: { content: created ? [cohort] : [], page: 0, size: 100, totalElements: created ? 1 : 0, totalPages: created ? 1 : 0 } });
    }
    if (request.method() === 'GET' && path.endsWith('/groups')) {
      return route.fulfill({ json: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } });
    }
    const body = request.postDataJSON() as Record<string, unknown>;
    requests.push({ path, body, context: request.headers()['x-context-assignment-id'], key: request.headers()['idempotency-key'] });
    if (path === '/api/v1/t3/cohorts') { created = true; return route.fulfill({ status: 201, json: cohort }); }
    if (path.endsWith('/members:batch')) return route.fulfill({ json: { cohortId: ids.cohort, version: 2,
      items: [{ placementId: ids.other, caseId: ids.case, outcome: 'ADDED', reasonCode: 'NONE' }] } });
    if (body.mode === 'PREVIEW') return route.fulfill({ json: { planId: ids.other, cohortVersion: 2,
      expiresAt: '2026-11-19T10:15:00Z', groups: [{ ordinal: 1, caseIds: [ids.case] }] } });
    return route.fulfill({ json: [group] });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Đợt thực tập', exact: true }).click();
  await page.getByLabel('Phân công đang làm việc').selectOption(ids.hr);
  await page.getByLabel('Mã đợt').fill('TEST-T3');
  await page.getByLabel('Tên đợt').fill('Đợt thử DEV05');
  await page.getByRole('button', { name: 'Tạo đợt', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Nhận Placement từ T5 vào ' + cohort.name })).toBeVisible();
  await page.getByLabel('Placement ID').fill(ids.other);
  await page.getByRole('button', { name: 'Đối chiếu và nhận hồ sơ' }).click();
  await expect(page.getByText(ids.other + ': Đã nhận')).toBeVisible();
  await page.getByRole('button', { name: 'Nhóm thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Xem trước nhóm' }).click();
  await expect(page.getByText('1 nhóm dự kiến', { exact: false })).toBeVisible();
  await page.getByRole('button', { name: 'Xác nhận chia nhóm' }).click();
  await expect.poll(() => requests.length).toBe(4);
  expect(requests.map(item => item.path)).toEqual([
    '/api/v1/t3/cohorts', '/api/v1/t3/cohorts/' + ids.cohort + '/members:batch',
    '/api/v1/t3/cohorts/' + ids.cohort + '/groups:auto', '/api/v1/t3/cohorts/' + ids.cohort + '/groups:auto'
  ]);
  expect(requests.map(item => item.body.expectedCohortVersion)).toEqual([undefined, undefined, 2, 2]);
  expect(requests[1].body).toMatchObject({ expectedVersion: cohort.version, placementIds: [ids.other], previewId: null, fileHash: null });
  expect(requests.every(item => item.context === ids.hr && !!item.key)).toBe(true);
});

test('DEV05 HR adds and moves a Case using current Case and group versions', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-19T10:00:00Z') });
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: me }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: grants }));
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } }));
  await page.route('**/api/v1/t3/context-assignments/me', route => route.fulfill({ json: [hr] }));
  await page.route('**/api/v1/t3/cohorts?*', route => route.fulfill({ json: { content: [cohort], page: 0, size: 100, totalElements: 1, totalPages: 1 } }));
  const second = { ...group, groupId: ids.other, code: 'N-02', caseIds: [], version: 3 };
  await page.route('**/api/v1/t3/cohorts/*/groups?*', route => route.fulfill({ json: {
    content: [group, second], page: 0, size: 100, totalElements: 2, totalPages: 1
  } }));
  // Opening a group also mounts its read-only task board; these unrelated reads
  // must not expire this mocked UI session before the group command is checked.
  const emptyBoardPage = { content: [], page: 0, totalPages: 1, totalElements: 0 };
  await page.route('**/api/v1/t3/tasks?*', route => route.fulfill({ json: emptyBoardPage }));
  await page.route('**/api/v1/t3/calendar?*', route => route.fulfill({ json: emptyBoardPage }));
  await page.route('**/api/v1/t3/cases/*', route => route.fulfill({ json: {
    caseId: route.request().url().split('/').pop(), placementId: ids.cohort,
    schoolOrgId: ids.school, enterpriseOrgId: ids.enterprise, termId: ids.term, state: 'IN_PROGRESS', version: 7
  } }));
  const writes: Record<string, unknown>[] = [];
  await page.route('**/api/v1/t3/groups/*/members*', route => {
    writes.push(route.request().postDataJSON() as Record<string, unknown>);
    return route.fulfill({ status: 201, json: { toGroupId: ids.group, toGroupVersion: 4 } });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Nhóm thực tập', exact: true }).click();
  await page.getByLabel('Phân công đang làm việc').selectOption(ids.hr);
  await page.getByLabel('Đợt thực tập', { exact: true }).selectOption(ids.cohort);
  await page.getByRole('button', { name: 'Xem nhóm ' + group.code }).click();
  await page.getByLabel('Case ID chưa có nhóm').fill(ids.other);
  await page.getByRole('button', { name: 'Thêm vào nhóm' }).click();
  await expect.poll(() => writes.length).toBe(1);
  expect(writes[0]).toMatchObject({ caseId: ids.other, expectedCaseVersion: 7, expectedGroupVersion: group.version });
  await page.getByLabel('Hồ sơ trong nhóm').selectOption(ids.case);
  await page.getByLabel('Nhóm đích').selectOption(ids.other);
  await page.getByLabel('Mã lý do').fill('GROUP_REBALANCE');
  await page.getByRole('button', { name: 'Chuyển hồ sơ' }).click();
  await expect.poll(() => writes.length).toBe(2);
  expect(writes[1]).toMatchObject({ caseId: ids.case, toGroupId: ids.other, expectedCaseVersion: 7,
    expectedFromGroupVersion: group.version, expectedToGroupVersion: second.version, reasonCode: 'GROUP_REBALANCE' });
});

test('T3 shell UI mock: global ADMIN has no implicit internship menu', async ({ page }) => {
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { ...me, memberships: [{ role: 'ADMIN', organizationId: null }] } }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [{ role: 'ADMIN', organizationId: null, permissions: ['t3:workspace:read'] }] }));
  await page.goto('/'); await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Không gian thực tập', exact: true })).toHaveCount(0);
});

test('DEV06 School can open internship Case lookup and denied lookup clears prior details', async ({ page }) => {
  const memberships = [{ role: 'SCHOOL_STAFF', organizationId: ids.school }];
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { ...me, memberships } }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [
    { ...memberships[0], permissions: ['t3:workspace:read', 't3:grades:publish'] }
  ] }));
  // The school portal starts background reads before navigation reaches T3.
  // Keep this UI fixture authenticated without masking the Case lookup response.
  const emptyPage = { content: [], page: 0, totalPages: 1, totalElements: 0 };
  await page.route('**/api/v1/cooperation/organizations', route => route.fulfill({ json: [] }));
  await page.route('**/api/v1/school/cooperations?*', route => route.fulfill({ json: emptyPage }));
  await page.route('**/api/v1/school/verifications?*', route => route.fulfill({ json: emptyPage }));
  await page.route('**/api/v1/terms?*', route => route.fulfill({ json: emptyPage }));
  await page.route('**/api/v1/t3/cases/*', route => route.request().url().endsWith(ids.case)
    ? route.fulfill({ json: { caseId: ids.case, schoolOrgId: ids.school, termId: ids.term,
      placementId: ids.cohort, enterpriseOrgId: ids.enterprise, state: 'IN_PROGRESS', version: 2 } })
    : route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } }));

  await page.goto('/');
  await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
  const submenu = page.locator('#internship-submenu');
  await expect(submenu.getByRole('button', { name: 'Đánh giá thực tập', exact: true })).toBeVisible();
  await submenu.getByRole('button', { name: 'Theo dõi hồ sơ thực tập', exact: true }).click();
  const field = page.getByLabel('Mã hồ sơ thực tập');
  await field.fill(ids.case);
  await page.getByRole('button', { name: 'Tra cứu hồ sơ', exact: true }).click();
  await expect(page.getByText('Trạng thái: Đang thực tập')).toBeVisible();
  await field.fill(ids.other);
  await expect(page.getByText('Mã hồ sơ: ' + ids.case)).toHaveCount(0);
  await page.getByRole('button', { name: 'Tra cứu hồ sơ', exact: true }).click();
  await expect(page.getByText('Không tìm thấy hồ sơ trong phạm vi trường và kỳ bạn được ủy quyền.')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Thông tin hồ sơ' })).toHaveCount(0);
});

test('DEV06 HR tracks grouped Case with assignment context and clears details after revocation', async ({ page }) => {
  await page.clock.install({ time: new Date('2026-11-19T10:00:00Z') });
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: me }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: grants }));
  await page.route('**/api/v1/t3/context-assignments/me', route => route.fulfill({ json: [hr] }));
  const pageOf = (content: (typeof cohort | typeof group)[]) => ({ content, page: 0, totalPages: 1, totalElements: content.length });
  await page.route('**/api/v1/t3/cohorts?*', route => route.fulfill({ json: pageOf([cohort]) }));
  await page.route('**/api/v1/t3/cohorts/*/groups?*', route => route.fulfill({ json: pageOf([group]) }));
  let permitted = true;
  const contexts: (string | undefined)[] = [];
  await page.route('**/api/v1/t3/cases/' + ids.case, route => {
    contexts.push(route.request().headers()['x-context-assignment-id']);
    return permitted ? route.fulfill({ json: { caseId: ids.case, schoolOrgId: ids.school, termId: ids.term,
      placementId: ids.cohort, enterpriseOrgId: ids.enterprise, state: 'WAITING_START', version: 2 } })
      : route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');
  await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Theo dõi Case', exact: true }).click();
  await page.getByLabel('Đợt thực tập', { exact: true }).selectOption(ids.cohort);
  await page.getByLabel('Hồ sơ theo dõi').selectOption(ids.case);
  await expect(page.getByText('Trạng thái: Chờ bắt đầu')).toBeVisible();
  permitted = false;
  await page.getByRole('button', { name: 'Tải lại trạng thái', exact: true }).click();
  await expect(page.getByText('Bạn không còn quyền xem hồ sơ này. Hãy kiểm tra lại phân công.')).toBeVisible();
  await expect(page.getByRole('region', { name: 'Chi tiết Case' })).toHaveCount(0);
  expect(contexts.length).toBeGreaterThanOrEqual(2);
  expect([...new Set(contexts)]).toEqual([ids.hr]);
});

test('T3 shell UI mock: session expiry leaves workspace and requires login', async ({ page }) => {
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: me }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: grants }));
  await page.route('**/api/v1/t3/context-assignments/me', route => route.fulfill({ json: [] }));
  await page.goto('/'); await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chưa có phân công phù hợp' })).toBeVisible();
  await page.evaluate(() => window.dispatchEvent(new Event('session-expired')));
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Chờ tích hợp dữ liệu' })).toHaveCount(0);
});
