import { expect, test, type Page } from '@playwright/test';

// UI contract fixtures. The MySQL/ClamAV/Mailpit acceptance suite supplies real backend evidence.
const term = '44444444-4444-4444-8444-444444444444', other = '55555555-5555-4555-8555-555555555555';
const jobId = '11111111-1111-4111-8111-111111111111', rowId = '22222222-2222-4222-8222-222222222222';
const initial = { importJobId: jobId, jobVersion: 1, jobState: 'PREVIEW_READY', fileHash: 'a'.repeat(64), termId: term,
  previewExpiresAt: '2099-01-01T00:00:00Z', totalRows: 2, validRows: 1, invalidRows: 1, previewNeedsReviewRows: 0,
  selectedRows: 0, processedRows: 0, successRows: 0, needsReviewRows: 0, permanentFailedRows: 0, transientFailedRows: 0 };
const valid = { rowId, rowNo: 2, rowVersion: 1, sourceData: { studentCode: '000Ab', fullName: 'Sinh viên giả lập', email: 'synthetic@example.test' },
  validationState: 'VALID', selected: false, rowState: 'NOT_SELECTED', errorCodes: [], activationState: 'NOT_REQUIRED', invitationVersion: null, mailState: 'NOT_REQUIRED' };
const invalid = { ...valid, rowId: other, rowNo: 3, validationState: 'INVALID', sourceData: { ...valid.sourceData, studentCode: null }, errorCodes: ['STUDENT_CODE_MUST_BE_TEXT'] };
async function fixture(page: Page, terminal = false) {
  let job = terminal ? { ...initial, jobVersion: 3, jobState: 'COMPLETED', selectedRows: 1, processedRows: 1, successRows: 1 } : { ...initial };
  const requests: { path: string; key?: string; body: unknown }[] = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', '');
    if (path === '/terms') return route.fulfill({ json: { content: [term, other].map((termId, i) => ({ termId, termCode: 'HK' + (i + 1), schoolOrgId: 'school-1', status: 'ACTIVE' })), totalPages: 1 } });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } });
    const content = job.jobState === 'PREVIEW_READY' ? [valid, invalid] : [{ ...valid, rowVersion: 3, rowState: 'PROVISIONED', selected: true, invitationVersion: 2, activationState: 'PENDING', mailState: 'FAILED' }, invalid];
    const rows = { content, page: 0, size: 25, totalElements: 2, totalPages: 1 };
    if (request.method() === 'POST') {
      requests.push({ path, key: request.headers()['idempotency-key'], body: path.endsWith('/preview') ? request.postData() : request.postDataJSON() });
      expect(request.headers()['x-csrf-token']).toBe('fixture-csrf');
      if (path.endsWith('/preview')) return route.fulfill({ status: 201, json: { job, rows } });
      if (path.endsWith('/confirm')) { job = { ...job, jobVersion: 2, jobState: 'PROCESSING', selectedRows: 1 }; return route.fulfill({ status: 202, json: { importJobId: jobId, jobVersion: 2, jobState: 'PROCESSING', selectedRows: 1 } }); }
      if (path.endsWith('/resend')) return route.fulfill({ status: 202, json: { rowId, rowVersion: 3, invitationVersion: 3, mailId: other, mailState: 'QUEUED', acceptedAt: '2026-11-19T00:00:00Z' } });
    }
    if (path.endsWith('/rows')) return route.fulfill({ json: rows });
    if (path.endsWith('/' + jobId)) return route.fulfill({ json: job });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/tests/fixtures/student-import.html');await page.getByLabel('Kỳ nhập danh sách', { exact: true }).selectOption(term);
  return requests;
}
async function preview(page: Page) {
  await page.getByLabel('Tệp danh sách sinh viên', { exact: true }).setInputFiles({ name: 'students.csv', mimeType: 'text/csv', buffer: Buffer.from('studentCode,email,fullName,majorCode,termCode\n000Ab,synthetic@example.test,Sinh vien,IT,HK1') });
  await page.getByRole('button', { name: 'Kiểm tra và xem trước', exact: true }).click();
}
test('preview preserves identifiers and confirms only selected valid row IDs with CSRF and a UUID key', async ({ page }) => {
  const requests = await fixture(page);await preview(page);
  await expect(page.getByText('2 / 000Ab', { exact: true })).toBeVisible();await expect(page.getByLabel('Chọn dòng 3')).toBeDisabled();
  await page.getByLabel('Chọn dòng 2').check();page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Xác nhận nhập 1 dòng', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đang xử lý', exact: true })).toBeVisible();
  expect(requests.find(request => request.path.endsWith('/confirm'))?.body).toEqual({ expectedJobVersion: 1, fileHash: initial.fileHash, selectedRowIds: [rowId] });
  expect(requests.every(request => /^[0-9a-f-]{36}$/.test(request.key || ''))).toBeTruthy();
});
test('a completed job with failed mail can resend without confirming or provisioning again', async ({ page }) => {
  const requests = await fixture(page, true);await page.getByLabel('Mã đợt nhập cần theo dõi').fill(jobId);await page.getByRole('button', { name: 'Theo dõi đợt nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Nhập danh sách hoàn tất', exact: true })).toBeVisible();await expect(page.getByText('Chờ kích hoạt', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Gửi lại thư kích hoạt', exact: true }).click();
  await expect(page.getByText('Đã xếp hàng thư kích hoạt mới.', { exact: true })).toBeVisible();
  expect(requests).toHaveLength(1);expect(requests[0].body).toEqual({ expectedRowVersion: 3, expectedInvitationVersion: 2 });
});
test('term change and session expiry clear staged PII and commands', async ({ page }) => {
  await fixture(page);await preview(page);await expect(page.locator('td').filter({ hasText: 'synthetic@example.test' })).toHaveCount(2);
  await page.getByLabel('Kỳ nhập danh sách', { exact: true }).selectOption(other);await expect(page.locator('td').filter({ hasText: 'synthetic@example.test' })).toHaveCount(0);
  await page.getByLabel('Kỳ nhập danh sách', { exact: true }).selectOption(term);await preview(page);
  await page.evaluate(() => window.dispatchEvent(new Event('session-expired')));await expect(page.locator('td').filter({ hasText: 'synthetic@example.test' })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Kiểm tra và xem trước', exact: true })).toHaveCount(0);
});
