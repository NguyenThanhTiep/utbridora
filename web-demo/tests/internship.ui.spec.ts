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
  await expect(page.locator('.internship-facts dd')).toHaveText(['0', '0']);
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

test('T3 shell UI mock: global ADMIN has no implicit internship menu', async ({ page }) => {
  await page.route('**/api/v1/auth/me', route => route.fulfill({ json: { ...me, memberships: [{ role: 'ADMIN', organizationId: null }] } }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [{ role: 'ADMIN', organizationId: null, permissions: ['t3:workspace:read'] }] }));
  await page.goto('/'); await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Không gian thực tập', exact: true })).toHaveCount(0);
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
