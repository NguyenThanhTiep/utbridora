import { expect, test, type Page } from '@playwright/test';

const org = '11111111-1111-4111-8111-111111111111';
const schoolA = '22222222-2222-4222-8222-222222222222', schoolB = '33333333-3333-4333-8333-333333333333';
const termA = '44444444-4444-4444-8444-444444444441', termA2 = '44444444-4444-4444-8444-444444444442', termB = '44444444-4444-4444-8444-444444444443';
const records = [[schoolA, termA, 'Nhu cầu trường A kỳ 1'], [schoolA, termA2, 'Nhu cầu trường A kỳ 2'], [schoolB, termB, 'Nhu cầu trường B kỳ 1']].map(([schoolOrgId, termId, title], index) => {
  const id = `55555555-5555-4555-8555-55555555555${index}`;
  const demand = { demandId: `demand-${index}`, enterpriseOrgId: org, cooperationId: id, state: 'READY', version: 2, criteriaVersion: 1, validationState: 'VALID', softDeletedAt: null,
    data: { schoolOrgId, termId, title, quantity: 3, description: title, location: null, benefits: null, deadline: '2027-01-01T00:00:00Z',
      required: { majorCodes: [], skills: [], dateRange: { startDate: '2027-02-01', endDate: '2027-05-01' } }, preferred: [], academicCatalogVersion: 'v1', taxonomyVersion: 'v1' } };
  return { demand, cooperation: { cooperationId: id, demandId: demand.demandId, schoolOrgId, enterpriseOrgId: org, termId, state: 'APPROVED', submissionNo: 1, version: 3 }, feedback: null };
});
const rows = (content: unknown[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: content.length ? 1 : 0 });
const counters = { target: 3, placed: 0, pending: 0, remaining: 3 };

async function setup(page: Page) {
  const queries: { path: string; school: string | null; term: string | null }[] = [];
  await page.route('**/api/v1/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1', ''), q = url.searchParams;
    const selected = records.filter(record => (!q.get('schoolOrgId') || record.cooperation.schoolOrgId === q.get('schoolOrgId')) && (!q.get('termId') || record.cooperation.termId === q.get('termId')));
    if (path === '/auth/me') return route.fulfill({ json: { userId: 'scope-viewer', email: 'scope@example.test', displayName: 'Người kiểm tra', memberships: [{ role: 'ENTERPRISE', organizationId: org }] } });
    if (path === '/auth/me/permissions') return route.fulfill({ json: [{ role: 'ENTERPRISE', organizationId: org, permissions: ['cooperation:enterprise:create'] }] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'scope-fixture' } });
    if (path === '/cooperation/organizations') return route.fulfill({ json: [{ id: org, name: 'Doanh nghiệp kiểm thử', type: 'ENTERPRISE' }] });
    if (path.endsWith('/demand-references/schools')) return route.fulfill({ json: rows([{ schoolOrgId: schoolA, name: 'Trường A' }, { schoolOrgId: schoolB, name: 'Trường B' }]) });
    if (path.endsWith('/demand-references/terms')) return route.fulfill({ json: rows(records.filter(row => row.cooperation.schoolOrgId === q.get('schoolOrgId')).map((row, index) => ({ termId: row.cooperation.termId, termCode: `Kỳ ${index + 1}`, status: 'ACTIVE' }))) });
    if (path.endsWith('/demands') || path.endsWith('/cooperations') || path.endsWith('/placements/progress')) {
      queries.push({ path, school: q.get('schoolOrgId'), term: q.get('termId') });
      if (path.endsWith('/demands')) return route.fulfill({ json: rows(selected.map(row => row.demand)) });
      if (path.endsWith('/cooperations')) return route.fulfill({ json: rows(selected.map(row => row.cooperation)) });
      return route.fulfill({ json: { ...rows(selected.map(row => ({ title: row.demand.data.title,
        ...row.cooperation, schoolName: row.cooperation.schoolOrgId === schoolA ? 'Trường A' : 'Trường B', enterpriseName: 'Doanh nghiệp kiểm thử', termCode: 'Kỳ kiểm thử', cooperationState: 'APPROVED', status: 'SHORT', counters }))),
        totals: { ...counters, target: selected.length * 3, remaining: selected.length * 3 }, schools: [{ id: schoolA, name: 'Trường A' }, { id: schoolB, name: 'Trường B' }], terms: records.map((row, index) => ({ id: row.cooperation.termId, name: `Kỳ tham chiếu ${index + 1}` })) } });
    }
    if (path.endsWith('/verifications')) return route.fulfill({ json: rows([]) });
    const detail = records.find(row => path === '/enterprise/cooperations/' + row.cooperation.cooperationId);
    if (detail) return route.fulfill({ json: detail });
    if (path.endsWith('/proposals') || path.endsWith('/proposal-batches') || path.endsWith('/placements')) return route.fulfill({ json: rows([]) });
    if (path.endsWith('/placements/summary')) return route.fulfill({ json: { demandId: q.get('demandId'), cooperationId: selected[0]?.cooperation.cooperationId || null, ...counters } });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');await expect(page.getByRole('heading', { name: 'Tổng quan doanh nghiệp', exact: true })).toBeVisible();
  return queries;
}

for (const [name, mode] of [['Tổng quan', 'overview'], ['Nhu cầu thực tập', 'demands'], ['Thống kê bảo mật', 'statistics'], ['Hợp tác', 'cooperations'], ['Ứng viên đề xuất', 'proposals'], ['Tiếp nhận thực tập', 'placements'], ['Thông báo', 'notifications']] as const) {
  test(`enterprise ${mode} follows the shared school and term scope and clears old selections`, async ({ page }) => {
    const requests = await setup(page);
    if (mode !== 'overview') await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name, exact: true }).click();
    for (const [school, term, expected] of [[schoolA, termA, records[0]], [schoolA, termA2, records[1]], [schoolB, termB, records[2]]] as const) {
      await page.getByRole('combobox', { name: 'Trường theo dõi', exact: true }).selectOption(school);
      await page.getByRole('combobox', { name: 'Kỳ theo dõi', exact: true }).selectOption(term);
      await expect.poll(() => requests.some(row => row.path === '/enterprise/demands' && row.school === school && row.term === term)).toBe(true);
      const stage = page.locator('.portal-page-stage');
      if (mode === 'statistics') await expect(stage.getByRole('combobox', { name: 'Nhu cầu xem thống kê', exact: true }).locator('option')).toHaveText([expected.demand.data.title]);
      else if (mode === 'proposals') await expect(stage.getByRole('combobox', { name: 'Hợp tác đang xem', exact: true }).locator('option')).toHaveText([expected.demand.data.title]);
      else if (mode === 'overview') await expect(stage.locator('.portal-stat').filter({ hasText: 'Hợp tác đã duyệt' }).locator('strong')).toHaveText('1');
      else await expect(stage.getByText(expected.demand.data.title, { exact: true }).first()).toBeVisible();
      if (mode !== 'overview') for (const record of records.filter(row => row !== expected)) await expect(stage.getByText(record.demand.data.title, { exact: true })).toHaveCount(0);
      if (mode === 'placements') await expect.poll(() => requests.some(row => row.path.endsWith('/placements/progress') && row.school === school && row.term === term)).toBe(true);
    }
    await page.getByRole('combobox', { name: 'Trường theo dõi', exact: true }).selectOption('');
    await expect(page.getByRole('combobox', { name: 'Kỳ theo dõi', exact: true })).toHaveValue('');
    await expect.poll(() => requests.at(-1)?.term).toBeNull();
  });
}

test('draft creation uses the shared school and term instead of another schools defaults', async ({ page }) => {
  await setup(page);
  await page.getByRole('combobox', { name: 'Trường theo dõi', exact: true }).selectOption(schoolB);
  await page.getByRole('combobox', { name: 'Kỳ theo dõi', exact: true }).selectOption(termB);
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Tạo nhu cầu', exact: true }).click();
  await expect(page.getByRole('combobox', { name: 'Trường nhận nhu cầu', exact: true })).toHaveValue(schoolB);
  await expect(page.getByRole('combobox', { name: 'Kỳ thực tập', exact: true })).toHaveValue(termB);
  await expect(page.getByRole('combobox', { name: 'Trường nhận nhu cầu', exact: true })).toBeDisabled();
  await expect(page.getByRole('combobox', { name: 'Kỳ thực tập', exact: true })).toBeDisabled();
});

for (const section of ['cooperations', 'proposals', 'placements']) test(`${section} rejects a deep-linked record outside the selected school and term`, async ({ page }) => {
  await setup(page);
  const foreign = records[2].cooperation.cooperationId, reads: string[] = [];
  page.on('request', request => reads.push(new URL(request.url()).pathname));
  await page.goto(`/enterprise/${section}?orgId=${org}&schoolId=${schoolA}&termId=${termA}&recordId=${foreign}&cooperationId=${foreign}`);
  await expect(page.getByText(section === 'cooperations'
    ? 'Đề nghị đã thay đổi hoặc không còn trong phạm vi truy cập.'
    : 'Hợp tác trong thông báo đã thay đổi hoặc không còn trong phạm vi truy cập. Hãy tải lại danh sách.', { exact: true })).toBeVisible();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.portal-detail')).toHaveCount(0);
  expect(reads.some(path => path.includes(foreign + '/proposals'))).toBe(false);
});

test('a late cooperation detail from the previous scope cannot reopen after switching schools', async ({ page }) => {
  await setup(page);
  let release: (() => void) | undefined;
  await page.route('**/api/v1/enterprise/cooperations/' + records[0].cooperation.cooperationId, async route => {
    await new Promise<void>(resolve => { release = resolve; });
    await route.fulfill({ json: records[0] }).catch(() => {});
  });
  await page.getByRole('combobox', { name: 'Trường theo dõi', exact: true }).selectOption(schoolA);
  await page.getByRole('combobox', { name: 'Kỳ theo dõi', exact: true }).selectOption(termA);
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Hợp tác', exact: true }).click();
  await page.locator('.enterprise-cooperations-table').getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true }).click();
  await expect.poll(() => !!release).toBe(true);
  await page.getByRole('combobox', { name: 'Trường theo dõi', exact: true }).selectOption(schoolB);
  await page.getByRole('combobox', { name: 'Kỳ theo dõi', exact: true }).selectOption(termB);
  release!();
  await expect(page.getByRole('dialog')).toHaveCount(0);
  await expect(page.locator('.portal-page-stage').getByText(records[2].demand.data.title, { exact: true })).toBeVisible();
  await expect(page.locator('.portal-page-stage').getByText(records[0].demand.data.title, { exact: true })).toHaveCount(0);
});
