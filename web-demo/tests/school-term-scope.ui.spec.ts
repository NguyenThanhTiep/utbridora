import { expect, test, type Page } from '@playwright/test';
import { openWorkspacePage } from './helpers/workspace';

// Isolated browser fixtures; no production accounts or database writes.
const school = '22222222-2222-4222-8222-222222222222';
const enterprise = '11111111-1111-4111-8111-111111111111';
const current = '44444444-4444-4444-8444-444444444444';
const past = '55555555-5555-4555-8555-555555555555';
const next = '66666666-6666-4666-8666-666666666666';
const terms = [current, past, next].map((termId, index) => ({ termId, schoolOrgId: school, termCode: ['Kỳ hiện tại', 'Kỳ trước', 'Kỳ tiếp theo'][index], status: 'ACTIVE' }));
const cooperations = [
  { cooperationId: 'coop-current', demandId: 'demand-current', schoolOrgId: school, enterpriseOrgId: enterprise, termId: current, state: 'APPROVED', submissionNo: 1, version: 1 },
  { cooperationId: 'coop-pending', demandId: 'demand-pending', schoolOrgId: school, enterpriseOrgId: enterprise, termId: current, state: 'SUBMITTED', submissionNo: 1, version: 1 },
  { cooperationId: 'coop-past', demandId: 'demand-past', schoolOrgId: school, enterpriseOrgId: 'other-enterprise', termId: past, state: 'APPROVED', submissionNo: 1, version: 1 },
];
const placements = [current, current, past].map((termId, index) => ({ studentUserId: 'student-' + index, schoolOrgId: school, termId, state: 'ACTIVE' }));
const rows = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: 1 });
type Read = { path: string; termId: string; state: string; size: string; finished: boolean };

async function session(page: Page, options: { beforeRead?: (path: string, term: string) => Promise<void>; failTerm?: string } = {}) {
  const reads: Read[] = [];
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', '');
    const termId = url.searchParams.get('termId') || '';
    const read = { path, termId, state: url.searchParams.get('state') || '', size: url.searchParams.get('size') || '', finished: false };
    reads.push(read);
    if (path.startsWith('/school/')) expect(request.headers()['x-organization-id']).toBe(school);
    await options.beforeRead?.(path, termId);
    let json: unknown;
    if (path === '/auth/me') json = { userId: 'school-user', email: 'scope@example.test', displayName: 'School Scope', memberships: [{ role: 'SCHOOL_STAFF', organizationId: school }] };
    else if (path.endsWith('/permissions')) json = [{ role: 'SCHOOL_STAFF', organizationId: school, permissions: ['cooperation:school:review', 'matching:school:read'] }];
    else if (path === '/cooperation/organizations') json = [{ id: school, name: 'Trường kiểm thử', type: 'UNIVERSITY' }];
    else if (path === '/terms') json = rows(terms);
    else if (path === '/school/placements') json = rows(placements.filter(row => !termId || row.termId === termId));
    else if (path === '/school/cooperations') json = rows(cooperations.filter(row => (!termId || row.termId === termId) && (!read.state || row.state === read.state)));
    else if (path === '/school/verifications') {
      expect(termId).toBe('');
      json = rows([{ verificationId: 'verification-1', enterpriseOrgId: enterprise, state: 'SUBMITTED', version: 1, data: { schoolOrgId: school, registeredName: 'Doanh nghiệp chờ xác minh' } }]);
    } else if (path === '/school/placements/progress') json = { ...rows([]), totals: { target: 0, placed: 0, pending: 0, remaining: 0,
      reservations: 0, declined: 0, enterpriseAccepted: 0, pendingCreation: 0, pendingApproval: 0, openForNomination: 0 }, schools: [], terms: terms.map(term => ({ id: term.termId, name: term.termCode })) };
    else if (path.endsWith('/proposals')) json = rows([]);
    else if (path.startsWith('/school/cooperations/')) {
      const cooperation = cooperations.find(row => path === '/school/cooperations/' + row.cooperationId);
      if (cooperation) json = { cooperation, feedback: null, demand: { demandId: cooperation.demandId, enterpriseOrgId: cooperation.enterpriseOrgId,
        cooperationId: cooperation.cooperationId, state: 'READY', version: 1, criteriaVersion: 1, validationState: 'VALID', softDeletedAt: null,
        data: { title: 'Nhu cầu ' + cooperation.termId, schoolOrgId: school, termId: cooperation.termId, quantity: 2, description: 'Fixture',
          required: { majorCodes: [], skills: [], dateRange: { startDate: '2027-01-01', endDate: '2027-05-01' } }, preferred: [], taxonomyVersion: 'v1', academicCatalogVersion: 'v1' } } };
    }
    const failed = termId === options.failTerm && ['/school/placements', '/school/cooperations'].includes(path);
    await route.fulfill(failed ? { status: 503, json: { errorCode: 'DEPENDENCY_UNAVAILABLE' } }
      : json === undefined ? { status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } } : { json })
      .catch(() => { /* Switching scope may abort this response. */ }).finally(() => { read.finished = true; });
  });
  await page.goto('/');
  await expect(page.locator('.school-overview-stat .school-stat-metric strong')).toHaveText(['3', '2', '3', '1']);
  await expect(page.getByLabel('Kỳ theo dõi', { exact: true }).locator('option')).toHaveCount(4);
  return reads;
}

test('school term selection updates KPIs, task list and term details and clearing it restores all terms', async ({ page }) => {
  const reads = await session(page);
  const selector = page.getByLabel('Kỳ theo dõi', { exact: true });
  const values = page.locator('.school-overview-stat .school-stat-metric strong');
  await selector.selectOption(current);
  await expect(values).toHaveText(['2', '1', '1', '1']);
  await expect(page.locator('.portal-dashboard-tasks')).toContainText('Nhu cầu ' + current);
  await selector.selectOption(past);
  await expect(values).toHaveText(['1', '1', '1', '1']);
  await expect(page.locator('.portal-dashboard-tasks')).not.toContainText('Nhu cầu ' + current);
  await page.getByRole('button', { name: 'Đợt thực tập: 1', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Danh sách đợt thực tập' }).getByRole('rowheader')).toHaveText(['Kỳ trước']);
  await selector.selectOption(next);
  await expect(values).toHaveText(['0', '0', '1', '1']);
  await expect(page.getByRole('region', { name: 'Danh sách đợt thực tập' })).toHaveCount(0);
  await page.getByRole('button', { name: 'Đợt thực tập: 1', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Danh sách đợt thực tập' }).getByRole('rowheader')).toHaveText(['Kỳ tiếp theo']);
  await expect(page.locator('.school-overview-stat').last()).toContainText('Chờ xác minh của toàn trường');
  await selector.selectOption('');
  await expect(values).toHaveText(['3', '2', '3', '1']);
  for (const termId of [current, past, next]) {
    expect(reads.some(read => read.path === '/school/cooperations' && read.termId === termId)).toBe(true);
    expect(reads.some(read => read.path === '/school/placements' && read.termId === termId && read.state === 'ACTIVE')).toBe(true);
  }
});

test('a delayed response for the previous term cannot replace the selected term', async ({ page }) => {
  let release!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; });
  const reads = await session(page, { beforeRead: async (path, term) => { if (term === current && ['/school/cooperations', '/school/placements'].includes(path)) await gate; } });
  try {
    await page.getByLabel('Kỳ theo dõi', { exact: true }).selectOption(current);
    await expect.poll(() => reads.filter(read => read.termId === current).length).toBeGreaterThanOrEqual(2);
    await page.getByLabel('Kỳ theo dõi', { exact: true }).selectOption(past);
    await expect(page.locator('.school-overview-stat .school-stat-metric strong')).toHaveText(['1', '1', '1', '1']);
    release();
    await expect.poll(() => reads.filter(read => read.termId === current).every(read => read.finished)).toBe(true);
    await expect(page.locator('.school-overview-stat .school-stat-metric strong')).toHaveText(['1', '1', '1', '1']);
  } finally { release(); }
});

test('selected-term failures display unavailable metrics instead of keeping the previous totals', async ({ page }) => {
  await session(page, { failTerm: next });
  await page.getByLabel('Kỳ theo dõi', { exact: true }).selectOption(next);
  await expect(page.locator('.school-overview-stat .school-stat-metric strong')).toHaveText(['—', '—', '1', '1']);
  await page.getByLabel('Kỳ theo dõi', { exact: true }).selectOption('');
  await expect(page.locator('.school-overview-stat .school-stat-metric strong')).toHaveText(['3', '2', '3', '1']);
});

test('school navigation keeps the term in cooperation, placement and matching reads', async ({ page }) => {
  const reads = await session(page);
  await page.getByLabel('Kỳ theo dõi', { exact: true }).selectOption(current);
  await expect(page.locator('.school-overview-stat .school-stat-metric strong')).toHaveText(['2', '1', '1', '1']);
  await page.getByRole('button', { name: 'Doanh nghiệp đối tác: 1', exact: true }).click();
  await expect.poll(() => reads.some(read => read.path === '/school/cooperations' && read.size === '25' && read.termId === current)).toBe(true);
  await page.getByRole('button', { name: 'Tổng quan', exact: true }).click();
  await page.getByRole('button', { name: 'Theo dõi tiếp nhận thực tập', exact: true }).click();
  await expect(page.getByRole('combobox', { name: 'Kỳ tiếp nhận', exact: true })).toHaveValue(current);
  await expect.poll(() => reads.some(read => read.path === '/school/placements/progress' && read.termId === current)).toBe(true);
  await page.getByRole('combobox', { name: 'Kỳ tiếp nhận', exact: true }).selectOption(past);
  await expect(page.getByLabel('Kỳ theo dõi', { exact: true })).toHaveValue(past);
  await expect.poll(() => reads.some(read => read.path === '/school/placements/progress' && read.termId === past)).toBe(true);
  await openWorkspacePage(page, 'Đối chiếu hồ sơ');
  await expect.poll(() => reads.some(read => read.path === '/school/cooperations' && read.state === 'APPROVED' && read.termId === past)).toBe(true);
});
