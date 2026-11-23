import { test, expect } from '@playwright/test';
import path from 'node:path';
const evidence = process.env.ADVISORY_EVIDENCE_DIRECTORY || 'test-results/advisory';
const id = 'a17e2026-1121-4000-8000-000000000001';
const b = 'a17e2026-1121-4000-8000-000000000002';
const expiresAt = '2030-01-01T00:00:00Z';
const rows = [{ studentUserId: b, position: 1, rank: 1, fitScore: 70, semanticScore: .8, scoreStatus: 'SCORED', limited: false },
  { studentUserId: id, position: 2, rank: null, fitScore: 100, semanticScore: null, scoreStatus: 'NO_CONTENT', limited: null }];
test.beforeEach(async ({ page }) => {
  await page.route('**/api/v1/**', async route => {
    const path = new URL(route.request().url()).pathname;
    if (path === '/api/v1/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'synthetic' } });
    if (path.endsWith('/advisories') && route.request().method() === 'POST') return route.fulfill({ status: 202, json: { advisoryId: id, matchRunId: id, acceptedAt: '2026-11-21T00:00:00Z' } });
    if (path.endsWith('/items')) return route.fulfill({ json: { state: 'READY', eligibleCount: 2, scoredCount: 1, coverage: .5, totalPages: 1, content: rows } });
    if (path.endsWith('/nomination-candidates')) return route.fulfill({ json: { demandVersion: 2, criteriaVersion: 1, cooperationVersion: 4, batchVersion: null, capacity: { openForNomination: 2 }, candidates: { totalPages: 1, content: rows.map(row => ({ match: { studentUserId: row.studentUserId }, selectable: true })) } } });
    if (path.endsWith('/nominate')) return route.fulfill({ status: 201, json: { items: [{ studentUserId: b }], batchVersion: 1 } });
    if (path.endsWith('/profile')) return route.fulfill({ json: { data: { displayName: path.includes(b) ? 'Sinh viên B' : 'Sinh viên A', studentCode: path.includes(b) ? 'SYN002' : 'SYN001' } } });
    return route.fulfill({ json: { state: 'READY', expiresAt, versions: { policyVersion: 'semantic-desc-canonical-tie-v1', templateVersion: 'school-advisory-text-v1' } } });
  });
  await page.goto('/tests/fixtures/advisory.html');
});
test('shows reordered score, null status, coverage and no automatic selections', async ({ page }) => {
  await page.getByRole('button', { name: 'Tạo gợi ý AI', exact: true }).click();
  await expect(page.getByText('Sinh viên B', { exact: true })).toBeVisible();
  await expect(page.getByText('Đã có điểm cho 1/2 hồ sơ', { exact: false })).toBeVisible();
  await expect(page.getByText('Gợi ý #1 · 0.800000')).toBeVisible();
  await expect(page.getByText('Chưa có điểm AI', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Đề cử 0 sinh viên đã chọn' })).toBeDisabled();
  await expect(page.getByRole('checkbox').first()).not.toBeChecked();
  await page.screenshot({ path: path.join(evidence, 'advisory-desktop.png'), fullPage: true });
});
test('staff choice calls existing nomination endpoint with freshly loaded versions', async ({ page }) => {
  await page.getByRole('button', { name: 'Tạo gợi ý AI', exact: true }).click();
  await page.getByRole('checkbox', { name: 'Chọn hồ sơ vị trí 1' }).check();
  const sent = page.waitForRequest(request => request.url().endsWith('/nominate') && request.method() === 'POST');
  await page.getByRole('button', { name: 'Đề cử 1 sinh viên đã chọn' }).click();
  expect((await sent).postDataJSON()).toEqual({ studentUserIds: [b], expectedDemandVersion: 2, expectedCriteriaVersion: 1, expectedCooperationVersion: 4, expectedBatchVersion: null });
  await expect(page).toHaveTitle('nomination-sent');
});
test('permission revoked during focus refresh clears scores and selection', async ({ page }) => {
  await page.getByRole('button', { name: 'Tạo gợi ý AI', exact: true }).click();
  await page.getByRole('checkbox', { name: 'Chọn hồ sơ vị trí 1' }).check();
  await page.route(`**/matching/advisories/${id}`, route => route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } }));
  await page.evaluate(() => window.dispatchEvent(new Event('focus')));
  await expect(page.getByText('Gợi ý #1 · 0.800000')).toHaveCount(0);
  await expect(page.getByRole('checkbox')).toHaveCount(0);
});
test('lost nomination acknowledgement retries identical body and idempotency key', async ({ page }) => {
  const sent: { body: string | null; key: string | undefined }[] = [];
  await page.route('**/nominate', route => {
    sent.push({ body: route.request().postData(), key: route.request().headers()['idempotency-key'] });
    return route.fulfill(sent.length === 1 ? { status: 503, json: { errorCode: 'TRANSIENT_FAILURE' } } : { status: 201, json: { items: [{ studentUserId: b }] } });
  });
  await page.getByRole('button', { name: 'Tạo gợi ý AI', exact: true }).click();
  await page.getByRole('checkbox', { name: 'Chọn hồ sơ vị trí 1' }).check();
  await page.getByRole('button', { name: 'Đề cử 1 sinh viên đã chọn' }).click();
  await page.getByRole('button', { name: 'Kiểm tra kết quả lần đề cử trước' }).click();
  await expect(page).toHaveTitle('nomination-sent');
  expect(sent).toHaveLength(2); expect(sent[1]).toEqual(sent[0]);
});
test('disabled feature reports fallback without invoking nomination', async ({ page }) => {
  let nominated = false;
  page.on('request', request => { if (request.url().endsWith('/nominate')) nominated = true; });
  await page.route(`**/matching/runs/${id}/advisories`, route => route.fulfill({ status: 503, json: { errorCode: 'AI_ADVISORY_DISABLED' } }));
  await page.getByRole('button', { name: 'Tạo gợi ý AI', exact: true }).click();
  await expect(page.getByRole('alert')).toContainText('chưa được bật');
  expect(nominated).toBe(false);
});
test('expiry clears visible scores and selections without another click', async ({ page }) => {
  await page.route(`**/matching/advisories/${id}`, route => route.fulfill({ json: { state: 'READY', expiresAt: new Date(Date.now() + 2000).toISOString(), versions: { policyVersion: 'semantic-desc-canonical-tie-v1', templateVersion: 'school-advisory-text-v1' } } }));
  await page.getByRole('button', { name: 'Tạo gợi ý AI', exact: true }).click();
  await page.getByRole('checkbox', { name: 'Chọn hồ sơ vị trí 1' }).check();
  await expect(page.getByText('Gợi ý #1 · 0.800000')).toHaveCount(0, { timeout: 6000 });
  await expect(page.getByRole('checkbox')).toHaveCount(0);
  await expect(page.getByRole('status')).toContainText('hết hạn');
});
test('narrow screen remains readable without horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await page.getByRole('button', { name: 'Tạo gợi ý AI', exact: true }).click();
  await expect(page.getByText('Sinh viên B', { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
  await page.screenshot({ path: path.join(evidence, 'advisory-mobile.png'), fullPage: true });
});
test('late item response cannot restore scores after expiry', async ({ page }) => {
  await page.route(`**/matching/advisories/${id}`, route => route.fulfill({ json: { state: 'READY', expiresAt: new Date(Date.now() + 300).toISOString(), versions: { policyVersion: 'semantic-desc-canonical-tie-v1', templateVersion: 'school-advisory-text-v1' } } }));
  await page.route('**/items?page=0&size=25', async route => { await new Promise(resolve => setTimeout(resolve, 800)); await route.fulfill({ json: { state: 'READY', eligibleCount: 2, scoredCount: 1, coverage: .5, totalPages: 1, content: rows } }); });
  const delivered = page.waitForResponse(response => response.url().endsWith('/items?page=0&size=25'));
  await page.getByRole('button', { name: 'Tạo gợi ý AI', exact: true }).click();
  await expect(page.getByRole('status')).toContainText('hết hạn');
  await delivered;
  await expect(page.getByRole('checkbox')).toHaveCount(0);
  await expect(page.getByText('Gợi ý #1 · 0.800000')).toHaveCount(0);
});
