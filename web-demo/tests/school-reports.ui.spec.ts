import { expect, test, type Page } from '@playwright/test';
import { openWorkspacePage } from './helpers/workspace';

const school = '11111111-1111-4111-8111-111111111111', other = '22222222-2222-4222-8222-222222222222';
const term = '33333333-3333-4333-8333-333333333333', nextTerm = '44444444-4444-4444-8444-444444444444';
const rows = Array.from({ length: 12 }, (_, n) => ({ matchRunId: 'run-' + n, demandId: 'demand-' + n,
  title: n === 0 ? 'Java API, nền tảng hợp tác doanh nghiệp' : 'Nhu cầu báo cáo ' + n, enterpriseName: 'Doanh nghiệp từ API', termId: term, termCode: 'HK-2026',
  completedAt: '2026-11-28T02:30:00Z', demandVersion: 1, criteriaVersion: 1, criteriaCurrent: n !== 0, matched: 6, missingData: 3, notMatched: 1 }));
const paged = (content: unknown[]) => ({ content, page: 0, number: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 });
function report(content = rows, page = 0, schoolOrgId = school) {
  const totals = content.reduce((a, row) => ({ demands: a.demands + 1, compared: a.compared + row.matched + row.missingData + row.notMatched,
    matched: a.matched + row.matched, missingData: a.missingData + row.missingData, notMatched: a.notMatched + row.notMatched }), { demands: 0, compared: 0, matched: 0, missingData: 0, notMatched: 0 });
  return { schoolOrgId, content: content.slice(page * 10, page * 10 + 10), page, size: 10, totalElements: content.length, totalPages: Math.ceil(content.length / 10), totals, generatedAt: '2026-11-28T03:00:00Z' };
}
async function setup(page: Page) {
  const requests: { query: URLSearchParams; org?: string; export: boolean }[] = [];
  await page.route('**/api/v1/**', route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', '');
    if (path === '/auth/me') return route.fulfill({ json: { userId: 'report-staff', displayName: 'Cán bộ báo cáo', email: 'reports@example.test', memberships: [{ role: 'SCHOOL_STAFF', organizationId: school }] } });
    if (path === '/auth/me/permissions') return route.fulfill({ json: [school, other].map(organizationId => ({ role: 'SCHOOL_STAFF', organizationId, permissions: ['cooperation:school:review', 'matching:school:detail_read'] })) });
    if (path === '/cooperation/organizations') return route.fulfill({ json: [{ id: school, name: 'Trường báo cáo A', type: 'UNIVERSITY' }, { id: other, name: 'Trường báo cáo B', type: 'UNIVERSITY' }] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } });
    if (path === '/terms') return route.fulfill({ json: paged([{ termId: term, termCode: 'HK-2026', status: 'ACTIVE' }, { termId: nextTerm, termCode: 'HK-2027', status: 'ACTIVE' }]) });
    if (path.startsWith('/school/matching-reports')) {
      expect(request.method()).toBe('GET');const org = request.headers()['x-organization-id'];
      requests.push({ query: url.searchParams, org, export: path.endsWith('.csv') });
      if (path.endsWith('/export.csv')) return route.fulfill({ contentType: 'text/csv;charset=utf-8', body: '\uFEFF"Nhu cầu","Đáp ứng"\r\n"Java API","6"\r\n' });
      const content = org !== school || url.searchParams.get('termId') === nextTerm ? [] : rows.filter(row => !url.searchParams.get('q') || row.title.includes(url.searchParams.get('q')!));
      return route.fulfill({ json: report(content, Number(url.searchParams.get('page') || 0), org) });
    }
    if (path.endsWith('/cooperations') || path.endsWith('/verifications')) return route.fulfill({ json: paged([]) });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');await openWorkspacePage(page, 'Thống kê đối chiếu');
  return requests;
}

for (const width of [1440, 390, 320]) test(`school report uses global totals, filters, pagination, dialogs and CSV at ${width}px`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 900 });await page.emulateMedia({ reducedMotion: 'reduce' });
  const requests = await setup(page), view = page.getByRole('region', { name: 'Báo cáo thống kê đối chiếu', exact: true });
  await expect(view.locator('.school-report-metrics').first().locator('strong')).toHaveText(['120', '72', '36', '12']);
  await expect(view.locator('tbody > tr')).toHaveCount(10);
  await view.getByRole('navigation', { name: 'Phân trang báo cáo đối chiếu' }).getByRole('button', { name: 'Sau', exact: true }).click();
  await expect(view.locator('tbody > tr')).toHaveCount(2);
  await expect(view.locator('.school-report-metrics').first().locator('strong')).toHaveText(['120', '72', '36', '12']);
  await view.getByLabel('Tìm nhu cầu hoặc doanh nghiệp').fill('Java');await view.getByRole('button', { name: 'Áp dụng bộ lọc', exact: true }).click();
  await expect(view.locator('tbody > tr')).toHaveCount(1);
  expect(requests.at(-1)?.query.get('page')).toBe('0');expect(requests.at(-1)?.query.get('q')).toBe('Java');
  await view.locator('tbody > tr').getByRole('rowheader').click();
  const dialog = page.getByRole('dialog', { name: 'Báo cáo đối chiếu ' + rows[0].title });
  await expect(dialog).toBeVisible();await expect(dialog).toContainText('Tiêu chí của nhu cầu đã thay đổi');
  expect(await dialog.evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
  await page.keyboard.press('Escape');await expect(dialog).toHaveCount(0);
  const file = page.waitForEvent('download');await view.getByRole('button', { name: 'Xuất CSV', exact: true }).click();const download = await file;
  expect(download.suggestedFilename()).toMatch(/^bao-cao-doi-chieu-.*\.csv$/);
  const csv = await (await import('node:fs/promises')).readFile((await download.path())!, 'utf8');expect(csv).toContain('"Java API","6"');
  const exported = requests.find(value => value.export)!;
  expect(exported.org).toBe(school);expect(exported.query.get('q')).toBe('Java');expect(exported.query.has('page')).toBe(false);
  await expect(view.getByText('Đã tải báo cáo CSV theo bộ lọc hiện tại.', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath(`school-report-${width}.png`), fullPage: true, animations: 'disabled' });
});

test('report scope follows school and term and exports no stale data after switching', async ({ page }) => {
  const requests = await setup(page);
  await expect(page.locator('.school-report-table tbody > tr')).toHaveCount(10);
  await page.getByLabel('Kỳ theo dõi', { exact: true }).selectOption(nextTerm);
  await expect(page.getByText('Chưa có kết quả đối chiếu trong phạm vi này', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Xuất CSV', exact: true })).toBeDisabled();
  expect(requests.at(-1)?.query.get('termId')).toBe(nextTerm);await expect(page.locator('.school-report-table')).toHaveCount(0);
  await page.getByLabel('Nhà trường đang làm việc', { exact: true }).selectOption(other);
  await expect.poll(() => requests.at(-1)?.org).toBe(other);
  await expect(page.getByText('Chưa có kết quả đối chiếu trong phạm vi này', { exact: true })).toBeVisible();
  await expect(page.locator('.school-report-table')).toHaveCount(0);
});

for (const status of [404, 503]) test(`report shows HTTP ${status} without fake zero counts or enabled export`, async ({ page }) => {
  await setup(page);await page.route('**/api/v1/school/matching-reports?*', route => route.fulfill({ status, json: { errorCode: status === 404 ? 'RESOURCE_NOT_FOUND' : 'DEPENDENCY_UNAVAILABLE' } }));
  await page.getByRole('button', { name: 'Tải lại báo cáo', exact: true }).click();
  await expect(page.locator('.school-report-table')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Xuất CSV', exact: true })).toBeDisabled();
  await expect(page.locator('.school-report-metrics').first().locator('strong')).toHaveText(['—', '—', '—', '—']);
  if (status === 404) await expect(page.getByText(/Bạn chưa được phân công xem kết quả đối chiếu/)).toBeVisible();
});

test('a foreign-school report payload is rejected and export revocation removes previously visible rows', async ({ page }) => {
  await setup(page);await page.route('**/api/v1/school/matching-reports/export.csv?*', route => route.fulfill({ status: 403, json: { errorCode: 'ACCESS_DENIED' } }));
  await page.getByRole('button', { name: 'Xuất CSV', exact: true }).click();
  await expect(page.locator('.school-report-table')).toHaveCount(0);await expect(page.getByRole('button', { name: 'Xuất CSV', exact: true })).toBeDisabled();
  await page.route('**/api/v1/school/matching-reports?*', route => route.fulfill({ json: report(rows, 0, other) }));
  await page.getByRole('button', { name: 'Tải lại báo cáo', exact: true }).click();
  await expect(page.getByText('Chưa nhận được báo cáo hợp lệ trong phạm vi đã chọn. Vui lòng tải lại.', { exact: true })).toBeVisible();
  await expect(page.locator('.school-report-table')).toHaveCount(0);
});
