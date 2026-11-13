import { test, expect, type Page, type Route } from '@playwright/test';
import type { InternshipTask, Submission, Review, TaskComment, WeeklyReport, ReportReview, ReportFile } from '../src/modules/internship/workflow';
import { ids } from './fixtures/internship';

// Contract fixture tests: requests use production transport/components, responses are synthetic.
// These deliberately do NOT claim T3-07/T3-08 backend, auth or database acceptance.
const fixture = '/tests/fixtures/internship-week3.html';
const taskId = '00000000-0000-4000-8000-000000000020', assignmentId = '00000000-0000-4000-8000-000000000021';
const rootId = '00000000-0000-4000-8000-000000000030';
const pageOf = <T,>(content: T[], page = 0, totalPages = content.length ? 1 : 0) => ({ content, page, size: 100, totalElements: content.length, totalPages });
const envelope = (code: string) => ({ errorCode: code, error: code, message: 'Not shown', requestId: 'fixture-request', fieldErrors: [], retryAfterSeconds: null });
function report(overrides: Partial<WeeklyReport> = {}): WeeklyReport {
  return { reportId: rootId, caseId: ids.case, weekStart: '2026-11-09', revisionNo: 1, version: 1, state: 'DRAFT',
    completedWork: 'Việc ban đầu', difficulties: '', nextPlan: 'Kế hoạch ban đầu', evidenceFileIds: [], dueAt: '2026-11-17T16:59:59Z',
    submittedAt: null, reviewedAt: null, lateFlag: false, ...overrides };
}
async function setup(page: Page, state: 'IN_PROGRESS' | 'SUBMITTED' = 'IN_PROGRESS') {
  const task: InternshipTask = { taskId, title: 'Nhiệm vụ tuần 3', description: 'Mô tả nhiệm vụ', groupId: ids.group,
    dueAt: '2026-11-30T10:00:00Z', priority: 'NORMAL', lateAllowed: true, version: 1,
    assignments: [{ assignmentId, caseId: ids.case, studentUserId: ids.user, state, version: 2, currentSubmissionId: state === 'SUBMITTED' ? 'sub-1' : null }] };
  const submissions: Submission[] = state === 'SUBMITTED' ? [{ submissionId: 'sub-1', assignmentId, versionNo: 1, fileIds: [],
    url: 'https://example.test/v1', note: 'Nội dung v1 bất biến', submittedAt: '2026-11-10T03:00:00Z', lateFlag: false }] : [];
  const comments: TaskComment[] = [], reports: WeeklyReport[] = [];
  const feedback = new Map<number, ReportReview>(), files = new Map<string, ReportFile>();
  const submissionReviews = new Map<string, Review>();
  const mutations: { path: string; body: Record<string, unknown>; key: string | undefined; context: string | undefined; csrf: string | undefined }[] = [];
  let intercept: ((route: Route) => Promise<boolean>) | undefined;
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', ''), params = new URL(request.url()).searchParams;
    if (request.method() !== 'GET') mutations.push({ path, body: request.headers()['content-type']?.startsWith('multipart/') ? { multipart: true } : request.postDataJSON(), key: request.headers()['idempotency-key'],
      context: request.headers()['x-context-assignment-id'], csrf: request.headers()['x-csrf-token'] });
    if (intercept && await intercept(route)) return;
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-csrf' } });
    if (path === '/files' && request.method() === 'POST') {
      const file: ReportFile = { fileId: '00000000-0000-4000-8000-000000000042', resource: { resourceType: 'WEEKLY_REPORT', resourceId: rootId },
        purpose: 'WEEKLY_REPORT', originalName: 'evidence.pdf', contentType: 'application/pdf', sizeBytes: 50, sha256: '0'.repeat(64), scanStatus: 'PENDING', createdAt: '2026-11-12T03:00:00Z', deletedAt: null };
      files.set(file.fileId, file); return route.fulfill({ status: 202, json: file });
    }
    if (path.startsWith('/files/')) {
      const id = path.split('/')[2], file = files.get(id) || { fileId: id, resource: { resourceType: 'WEEKLY_REPORT', resourceId: rootId },
        purpose: 'WEEKLY_REPORT', originalName: 'existing.pdf', contentType: 'application/pdf', sizeBytes: 50, sha256: '0'.repeat(64), scanStatus: 'CLEAN', createdAt: '2026-11-12T03:00:00Z', deletedAt: null };
      if (path.endsWith('/metadata')) return route.fulfill({ json: file });
      if (file.scanStatus !== 'CLEAN') return route.fulfill({ status: 409, json: envelope('FILE_NOT_CLEAN') });
      return route.fulfill({ contentType: 'application/pdf', body: '%PDF-1.7\nSynthetic file\n%%EOF' });
    }
    if (path === '/t3/tasks') return route.fulfill({ json: pageOf([task]) });
    if (path === '/t3/tasks/' + taskId) return route.fulfill({ json: task });
    if (path === '/t3/calendar') return route.fulfill({ json: pageOf([{ taskId, assignmentId, title: task.title, dueAt: task.dueAt, state: task.assignments[0].state }]) });
    if (path === '/t3/cases/' + ids.case) return route.fulfill({ json: { caseId: ids.case } });
    if (path === '/t3/assignments/' + assignmentId + '/status') {
      task.assignments[0].state = 'IN_PROGRESS'; task.assignments[0].version++; return route.fulfill({ json: task.assignments[0] });
    }
    if (path.endsWith('/submissions')) {
      if (request.method() === 'GET') return route.fulfill({ json: pageOf([...submissions].reverse()) });
      const input = request.postDataJSON();
      const value: Submission = { ...input, assignmentId, submissionId: 'sub-' + (submissions.length + 1), versionNo: submissions.length + 1,
        submittedAt: '2026-11-10T04:00:00Z', lateFlag: false };
      submissions.push(value); Object.assign(task.assignments[0], { state: 'SUBMITTED', version: task.assignments[0].version + 1, currentSubmissionId: value.submissionId });
      return route.fulfill({ status: 201, json: value });
    }
    if (path.startsWith('/t3/submissions/') && path.endsWith('/reviews')) {
      const submissionId = path.split('/')[3];
      if (request.method() === 'GET') {
        const value = submissionReviews.get(submissionId);
        return route.fulfill(value ? { json: value } : { status: 204 });
      }
      const body = request.postDataJSON(), current = submissions.at(-1)!;
      task.assignments[0].state = body.verdict === 'ACCEPT' ? 'COMPLETED' : 'REVISION_REQUIRED'; task.assignments[0].version++;
      const value: Review = { reviewId: 'review-' + current.submissionId, submissionId: current.submissionId, reviewedVersionNo: current.versionNo,
        ...body, reviewerUserId: ids.other, createdAt: '2026-11-10T04:01:00Z', assignmentVersion: task.assignments[0].version };
      submissionReviews.set(current.submissionId, value); return route.fulfill({ status: 201, json: value });
    }
    if (path.endsWith('/comments')) {
      if (request.method() === 'GET') return route.fulfill({ json: pageOf(comments) });
      const value = { commentId: 'comment-' + comments.length, assignmentId, authorUserId: ids.user, text: request.postDataJSON().text, createdAt: '2026-11-10T04:01:00Z' };
      comments.push(value); return route.fulfill({ status: 201, json: value });
    }
    if (path === '/t3/reports') {
      if (request.method() === 'GET') return route.fulfill({ json: pageOf(reports.length ? [reports.at(-1)!] : []) });
      const value = report(request.postDataJSON()); reports.push(value); return route.fulfill({ status: 201, json: value });
    }
    if (path.startsWith('/t3/reports/')) {
      const current = reports.at(-1)!;
      if (request.method() === 'GET' && path.endsWith('/reviews')) {
        const review = feedback.get(params.has('revisionNo') ? Number(params.get('revisionNo')) : current.revisionNo);
        return route.fulfill(review ? { json: review } : { status: 404, json: envelope('RESOURCE_NOT_FOUND') });
      }
      if (request.method() === 'GET') return route.fulfill({ json: params.has('revisionNo') ? reports.find(r => r.revisionNo === Number(params.get('revisionNo'))) : current });
      const input = request.postDataJSON();
      if (path.endsWith('/revisions')) reports.push({ ...current, revisionNo: current.revisionNo + 1, version: current.version + 1, state: 'DRAFT', submittedAt: null, reviewedAt: null, lateFlag: false });
      else if (path.endsWith('/submit')) Object.assign(current, { state: 'SUBMITTED', submittedAt: '2026-11-10T04:02:00Z', version: current.version + 1 });
      else if (path.endsWith('/reviews')) {
        Object.assign(current, { state: 'REVIEWED', reviewedAt: '2026-11-10T04:03:00Z', version: current.version + 1 });
        feedback.set(current.revisionNo, { reportId: rootId, revisionNo: current.revisionNo, reviewerUserId: ids.other, comment: input.comment, reviewedAt: current.reviewedAt! });
      }
      else Object.assign(current, input, { version: current.version + 1 });
      return route.fulfill({ json: reports.at(-1) });
    }
    return route.fulfill({ status: 404, json: envelope('NOT_FOUND') });
  });
  return { task, submissions, submissionReviews, comments, reports, feedback, files, mutations, intercept: (handler: typeof intercept) => { intercept = handler; } };
}
async function openTask(page: Page, role = 'student') { await page.goto(fixture + '?role=' + role); await page.getByRole('button', { name: 'Nhiệm vụ tuần 3', exact: true }).click(); }
async function openReports(page: Page, role = 'student') {
  await page.goto(fixture + '?role=' + role); await page.getByRole('button', { name: 'Mở báo cáo tuần', exact: true }).click();
  await page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).selectOption(ids.case);
}

test('T3-09 URL submit uses CSRF/version/key and reload reads saved fixture history', async ({ page }) => {
  const server = await setup(page); await openTask(page);
  await page.getByLabel('Đường dẫn bài nộp HTTPS').fill('https://example.test/v1'); await page.getByLabel('Ghi chú bài nộp').fill('Nội dung v1');
  await page.getByRole('button', { name: 'Gửi bài nộp', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Mở URL bản v1' })).toHaveAttribute('href', 'https://example.test/v1');
  await page.getByLabel('Tháng', { exact: true }).fill('2026-11');
  await expect(page.getByRole('grid', { name: 'Lịch hạn công việc' }).getByRole('button')).toHaveText('Nhiệm vụ tuần 3: Đã nộp');
  expect(server.mutations[0]).toMatchObject({ body: { expectedAssignmentVersion: 2, fileIds: [], url: 'https://example.test/v1', note: 'Nội dung v1' }, csrf: 'fixture-csrf' });
  expect(server.mutations[0].key).toMatch(/^[0-9a-f-]{36}$/); expect(server.mutations[0].context).toBeUndefined();
  await page.reload(); await page.getByRole('button', { name: 'Nhiệm vụ tuần 3', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Mở URL bản v1' })).toBeVisible();
});

test('T3-09 409 preserves URL/note and explicit refresh uses latest version with a new key', async ({ page }) => {
  const server = await setup(page); let conflict = true;
  server.intercept(async route => {
    if (route.request().method() === 'POST' && route.request().url().endsWith('/submissions') && conflict) {
      conflict = false; server.task.assignments[0].version = 7; await route.fulfill({ status: 409, json: envelope('VERSION_CONFLICT') }); return true;
    } return false;
  });
  await openTask(page); await page.getByLabel('Đường dẫn bài nộp HTTPS').fill('https://example.test/my-work'); await page.getByLabel('Ghi chú bài nộp').fill('Không mất bản nháp');
  await page.getByRole('button', { name: 'Gửi bài nộp', exact: true }).click(); await expect(page.getByRole('alert')).toContainText('VERSION_CONFLICT');
  await page.getByRole('button', { name: 'Tải phiên bản hiện tại', exact: true }).click();
  await expect(page.getByText('Trạng thái: IN_PROGRESS · Phiên bản phân công: 7')).toBeVisible();
  await expect(page.getByLabel('Ghi chú bài nộp')).toHaveValue('Không mất bản nháp');
  await page.getByRole('button', { name: 'Gửi bài nộp', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Mở URL bản v1' })).toBeVisible();
  expect(server.mutations[1].body.expectedAssignmentVersion).toBe(7); expect(server.mutations[1].key).not.toBe(server.mutations[0].key);
});

test('T3-09 lost response retries identical payload/key and blocks edits while uncertain', async ({ page }) => {
  const server = await setup(page); let fail = true;
  server.intercept(async route => {
    if (route.request().method() === 'POST' && route.request().url().endsWith('/submissions') && fail) { fail = false; await route.abort('failed'); return true; } return false;
  });
  await openTask(page); await page.getByLabel('Đường dẫn bài nộp HTTPS').fill('https://example.test/retry');
  await page.getByRole('button', { name: 'Gửi bài nộp', exact: true }).dblclick();
  await expect(page.getByRole('button', { name: 'Thử lại thao tác vừa gửi' })).toBeVisible();
  await expect(page.getByLabel('Đường dẫn bài nộp HTTPS')).toBeDisabled();
  await page.getByRole('button', { name: 'Thử lại thao tác vừa gửi' }).click();
  await expect(page.getByRole('link', { name: 'Mở URL bản v1' })).toBeVisible();
  expect(server.mutations).toHaveLength(2); expect(server.mutations[1]).toEqual(server.mutations[0]);
});

test('T3-09 Mentor requests revision with selected context; student starts and submits v2, v1 stays intact', async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); const original = structuredClone(server.submissions[0]); await openTask(page, 'mentor');
  await page.getByRole('textbox', { name: 'Nhận xét bài nộp', exact: true }).fill('Bổ sung kiểm thử'); await page.getByRole('button', { name: 'Gửi nhận xét bài nộp', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Bổ sung kiểm thử');
  expect(server.mutations[0]).toMatchObject({ context: ids.mentor, body: { expectedAssignmentVersion: 2, verdict: 'REQUEST_REVISION', comment: 'Bổ sung kiểm thử' } });
  await openTask(page); await page.getByRole('button', { name: 'Bắt đầu bản làm tiếp theo' }).click();
  await page.getByLabel('Đường dẫn bài nộp HTTPS').fill('https://example.test/v2'); await page.getByRole('button', { name: 'Gửi bài nộp', exact: true }).click();
  await expect(page.getByRole('link', { name: 'Mở URL bản v2' })).toBeVisible(); await expect(page.getByRole('link', { name: 'Mở URL bản v1' })).toBeVisible();
  expect(server.submissions[0]).toEqual(original); expect(server.submissions).toHaveLength(2);
});

test('T3-09 HR can read but has no submission, review or comment mutation controls', async ({ page }) => {
  await setup(page, 'SUBMITTED'); await openTask(page, 'hr');
  await expect(page.getByRole('link', { name: 'Mở URL bản v1' })).toBeVisible();
  await expect(page.getByRole('button', { name: /^Gửi/ })).toHaveCount(0);
});

test('T3-09 comments append through API and unsafe submission URLs never become links', async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); server.submissions[0].url = 'javascript:alert(1)'; await openTask(page);
  await expect(page.getByRole('link', { name: 'Mở URL bản v1' })).toHaveCount(0);
  await page.getByLabel('Nội dung trao đổi').fill('Trao đổi mới');
  const appended = page.waitForResponse(response => response.url().endsWith('/comments') && response.request().method() === 'POST');
  await page.getByRole('button', { name: 'Gửi trao đổi', exact: true }).click();
  expect((await appended).status()).toBe(201);
  expect(server.comments).toHaveLength(1);
  // The typed textarea text is not proof that the POST and subsequent list read completed.
  const savedComment = page.locator('article p.t3-preserve-text').filter({ hasText: /^Trao đổi mới$/ });
  await expect(savedComment).toBeVisible();
  await page.reload(); await page.getByRole('button', { name: 'Nhiệm vụ tuần 3', exact: true }).click();
  await expect(savedComment).toBeVisible();
});

test('T3-09 denied mutation clears private data and drafts; session revocation closes workspace', async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); await openTask(page, 'mentor');
  server.intercept(async route => { if (route.request().method() === 'POST' && route.request().url().endsWith('/reviews')) {
    await route.fulfill({ status: 403, json: envelope('ACCESS_DENIED') }); return true; } return false; });
  await page.getByRole('textbox', { name: 'Nhận xét bài nộp', exact: true }).fill('Private draft'); await page.getByRole('button', { name: 'Gửi nhận xét bài nộp', exact: true }).click();
  await expect(page.getByText('Dữ liệu của phạm vi này đã được đóng.', { exact: false })).toBeVisible();
  await expect(page.getByText('Nội dung v1 bất biến')).toHaveCount(0); await expect(page.getByRole('textbox', { name: 'Nhận xét bài nộp', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Xác nhận lại quyền truy cập' }).click();
  await page.getByRole('button', { name: 'Thu hồi phiên fixture' }).click(); await expect(page.getByRole('heading', { name: 'Bảng công việc' })).toHaveCount(0);
});

test('T3-09 delayed response from previous context cannot restore its artifacts', async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); let release!: () => void; const gate = new Promise<void>(resolve => { release = resolve; }); let entered!: () => void; const started = new Promise<void>(resolve => { entered = resolve; });
  let finished!: () => void; const responded = new Promise<void>(resolve => { finished = resolve; });
  server.intercept(async route => { if (route.request().method() === 'GET' && route.request().url().includes('/submissions?')) {
    entered(); await gate; await route.fulfill({ json: pageOf(server.submissions) }).catch(() => {}); finished(); return true; } return false; });
  await openTask(page, 'mentor'); await started; await page.getByRole('button', { name: 'Đổi phạm vi fixture' }).click(); release(); await responded;
  await expect(page.getByRole('heading', { name: 'Bảng công việc' })).toBeVisible();
  await expect(page.getByText('Nội dung v1 bất biến')).toHaveCount(0); await expect(page.getByRole('region', { name: 'Bài nộp và trao đổi' })).toHaveCount(0);
});

test('T3-09 missing report backend is visible as unavailable, not successful empty history', async ({ page }) => {
  const server = await setup(page);
  server.intercept(async route => { if (route.request().url().includes('/reports?')) { await route.fulfill({ status: 503, json: envelope('DEPENDENCY_UNAVAILABLE') }); return true; } return false; });
  await openReports(page); await expect(page.getByRole('alert')).toContainText('DEPENDENCY_UNAVAILABLE');
  await expect(page.getByText('Chưa có báo cáo cho hồ sơ này.')).toHaveCount(0);
});

test('T3-09 create draft, save, submit, revise and read old server revision', async ({ page }) => {
  const server = await setup(page); await openReports(page);
  await page.getByLabel('Thứ Hai bắt đầu tuần').fill('2026-11-10'); await expect(page.getByRole('button', { name: 'Tạo bản nháp báo cáo' })).toBeDisabled();
  await page.getByLabel('Thứ Hai bắt đầu tuần').fill('2026-11-09'); await page.getByRole('textbox', { name: 'Việc đã làm', exact: true }).fill('Báo cáo gốc');
  await page.getByRole('textbox', { name: 'Kế hoạch tiếp theo', exact: true }).fill('Làm tiếp'); await page.getByRole('button', { name: 'Tạo bản nháp báo cáo' }).click();
  const editor = page.getByRole('region', { name: 'Chi tiết báo cáo' });
  await expect(editor.getByRole('textbox', { name: 'Việc đã làm', exact: true })).toHaveValue('Báo cáo gốc'); expect(server.mutations[0].body.evidenceFileIds).toEqual([]);
  await editor.getByRole('textbox', { name: 'Việc đã làm', exact: true }).fill('Báo cáo v1 đã lưu'); await expect(editor.getByRole('button', { name: 'Nộp báo cáo tuần' })).toBeDisabled();
  await editor.getByRole('button', { name: 'Lưu bản nháp' }).click(); await expect(editor.getByRole('button', { name: 'Nộp báo cáo tuần' })).toBeEnabled();
  await editor.getByRole('button', { name: 'Nộp báo cáo tuần' }).click(); await editor.getByLabel('Mã lý do sửa báo cáo').fill('UPDATE_PROGRESS');
  await editor.getByRole('button', { name: 'Tạo bản sửa báo cáo' }).click(); await editor.getByRole('textbox', { name: 'Việc đã làm', exact: true }).fill('Báo cáo v2');
  await editor.getByRole('button', { name: 'Lưu bản nháp' }).click(); await expect(editor.getByRole('button', { name: 'Lưu bản nháp' })).toBeDisabled();
  await editor.getByRole('combobox', { name: 'Phiên bản báo cáo', exact: true }).selectOption('1'); await expect(editor.getByText('Báo cáo v1 đã lưu', { exact: true })).toBeVisible();
  await expect(editor.getByRole('button', { name: 'Lưu bản nháp' })).toHaveCount(0); expect(server.reports[0].completedWork).toBe('Báo cáo v1 đã lưu');
  await page.reload(); await page.getByRole('button', { name: 'Mở báo cáo tuần', exact: true }).click(); await page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).selectOption(ids.case);
  await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 2 · DRAFT', exact: true }).click(); await expect(editor.getByRole('textbox', { name: 'Việc đã làm', exact: true })).toHaveValue('Báo cáo v2');
});

test('T3-09 report 409 preserves edits and file refs, refresh updates expectedVersion', async ({ page }) => {
  const server = await setup(page); server.reports.push(report({ evidenceFileIds: ['00000000-0000-4000-8000-000000000041'] })); let conflict = true;
  server.intercept(async route => { if (route.request().method() === 'PATCH' && conflict) { conflict = false; server.reports[0].version = 8;
    await route.fulfill({ status: 409, json: envelope('VERSION_CONFLICT') }); return true; } return false; });
  await openReports(page); await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · DRAFT', exact: true }).click();
  const editor = page.getByRole('region', { name: 'Chi tiết báo cáo' }); await editor.getByRole('textbox', { name: 'Việc đã làm', exact: true }).fill('Bản nhập cần giữ');
  await editor.getByRole('button', { name: 'Lưu bản nháp' }).click(); await expect(editor.getByRole('alert')).toContainText('VERSION_CONFLICT');
  await editor.getByRole('button', { name: 'Tải phiên bản báo cáo hiện tại' }).click(); await expect(editor.getByText(/Version 8/)).toBeVisible();
  await expect(editor.getByRole('textbox', { name: 'Việc đã làm', exact: true })).toHaveValue('Bản nhập cần giữ'); await editor.getByRole('button', { name: 'Lưu bản nháp' }).click();
  await expect(editor.getByRole('button', { name: 'Lưu bản nháp' })).toBeDisabled();
  expect(server.mutations[1].body).toMatchObject({ expectedVersion: 8, completedWork: 'Bản nhập cần giữ', evidenceFileIds: ['00000000-0000-4000-8000-000000000041'] });
});

test('T3-09 Mentor report review is scoped; HR has read-only reports', async ({ page }) => {
  const server = await setup(page); server.reports.push(report({ state: 'SUBMITTED', version: 3 })); await openReports(page, 'mentor');
  await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · SUBMITTED', exact: true }).click();
  await page.getByRole('textbox', { name: 'Nhận xét báo cáo', exact: true }).fill('Đã kiểm tra'); await page.getByRole('button', { name: 'Gửi nhận xét báo cáo' }).click();
  await expect(page.getByRole('region', { name: 'Chi tiết báo cáo' })).toContainText('REVIEWED'); expect(server.mutations[0]).toMatchObject({ context: ids.mentor, body: { expectedVersion: 3, comment: 'Đã kiểm tra' } });
  await openReports(page, 'hr'); await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · REVIEWED', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Chi tiết báo cáo' })).toContainText('REVIEWED');
  await expect(page.getByRole('button', { name: /Gửi nhận xét báo cáo|Tạo bản sửa báo cáo|Tạo bản nháp báo cáo/ })).toHaveCount(0);
});

test('T3-09 mobile URL form stays within viewport and can submit', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 }); await setup(page); await openTask(page);
  await page.getByLabel('Đường dẫn bài nộp HTTPS').fill('https://example.test/mobile');
  await expect(page.getByRole('button', { name: 'Gửi bài nộp', exact: true })).toBeEnabled();
  const form = page.getByRole('region', { name: 'Bài nộp và trao đổi' });
  const bounds = await form.boundingBox(); expect(bounds!.x + bounds!.width).toBeLessThanOrEqual(321);
  await page.screenshot({ path: '../target/t3-09-mobile-fixture.png', fullPage: true });
});

test('T3-09 history reads every page and permits review only for current submission', async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); server.task.assignments[0].currentSubmissionId = 'sub-2';
  const current = { ...server.submissions[0], submissionId: 'sub-2', versionNo: 2, url: 'https://example.test/v2' };
  const pages: number[] = [];
  server.intercept(async route => {
    const url = new URL(route.request().url());
    if (route.request().method() === 'GET' && url.pathname.endsWith('/submissions')) {
      const index = Number(url.searchParams.get('page')); pages.push(index);
      await route.fulfill({ json: pageOf([index === 0 ? current : server.submissions[0]], index, 2) }); return true;
    } return false;
  });
  await openTask(page, 'mentor'); await expect(page.getByRole('link', { name: 'Mở URL bản v1' })).toBeVisible();
  await expect(page.getByRole('link', { name: 'Mở URL bản v2' })).toBeVisible(); expect(pages).toEqual([0, 1]);
  await expect(page.getByRole('button', { name: 'Gửi nhận xét bài nộp', exact: true })).toHaveCount(1);
  await expect(page.locator('article').filter({ has: page.getByRole('heading', { name: 'Bản nộp v1', exact: true }) })
    .getByRole('button', { name: 'Gửi nhận xét bài nộp', exact: true })).toHaveCount(0);
});

test('T3-09 report response for another case closes scope without exposing content', async ({ page }) => {
  const server = await setup(page); server.reports.push(report());
  server.intercept(async route => { if (route.request().method() === 'GET' && new URL(route.request().url()).pathname.endsWith('/reports/' + rootId)) {
    await route.fulfill({ json: report({ caseId: ids.other, completedWork: 'PRIVATE-OTHER-CASE' }) }); return true;
  } return false; });
  await openReports(page); await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · DRAFT', exact: true }).click();
  await expect(page.getByText('Dữ liệu của phạm vi này đã được đóng.', { exact: false })).toBeVisible();
  await expect(page.getByText('PRIVATE-OTHER-CASE')).toHaveCount(0);
});

test('T3-09 student without tasks verifies a case through scoped API before opening reports', async ({ page }) => {
  const server = await setup(page);
  server.intercept(async route => { if (new URL(route.request().url()).pathname.endsWith('/t3/tasks')) { await route.fulfill({ json: pageOf([]) }); return true; } return false; });
  await page.goto(fixture); await page.getByRole('button', { name: 'Mở báo cáo tuần', exact: true }).click();
  await page.getByText('Tìm hồ sơ chưa có nhiệm vụ', { exact: true }).click();
  await page.getByLabel('Mã hồ sơ thực tập', { exact: true }).fill(ids.case); await page.getByRole('button', { name: 'Xác nhận hồ sơ' }).click();
  await expect(page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true })).toHaveValue(ids.case);
  await expect(page.getByRole('button', { name: 'Tạo bản nháp báo cáo' })).toBeVisible();
});

test('T3-09 report review is read from API after reload and stays bound to its historical revision', async ({ page }) => {
  const server = await setup(page);
  server.reports.push(report({ state: 'REVIEWED', version: 3, reviewedAt: '2026-11-12T03:00:00Z' }), report({ revisionNo: 2, version: 4, completedWork: 'New draft' }));
  server.feedback.set(1, { reportId: rootId, revisionNo: 1, reviewerUserId: ids.other, comment: 'Historical feedback from API', reviewedAt: '2026-11-12T03:00:00Z' });
  await openReports(page); await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 2 · DRAFT', exact: true }).click();
  const editor = page.getByRole('region', { name: 'Chi tiết báo cáo' });
  await expect(editor.getByRole('region', { name: 'Nhận xét báo cáo đã lưu' })).toHaveCount(0);
  await editor.getByRole('combobox', { name: 'Phiên bản báo cáo', exact: true }).selectOption('1');
  await expect(editor.getByRole('region', { name: 'Nhận xét báo cáo đã lưu' })).toContainText('Historical feedback from API');
  await page.reload(); await page.getByRole('button', { name: 'Mở báo cáo tuần', exact: true }).click();
  await page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).selectOption(ids.case);
  await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 2 · DRAFT', exact: true }).click();
  await editor.getByRole('combobox', { name: 'Phiên bản báo cáo', exact: true }).selectOption('1');
  await expect(editor.getByRole('region', { name: 'Nhận xét báo cáo đã lưu' })).toContainText('Historical feedback from API');
});

test('T3-09 multipart upload retries same file/binding/key; draft saves PENDING refs', async ({ page }) => {
  const server = await setup(page); server.reports.push(report()); const uploads: { type: string; body: string; key: string; csrf: string }[] = [];
  server.intercept(async route => {
    const req = route.request(); if (req.method() === 'POST' && req.url().endsWith('/files')) {
      uploads.push({ type: req.headers()['content-type'], body: req.postData()!, key: req.headers()['idempotency-key'], csrf: req.headers()['x-csrf-token'] });
      if (uploads.length === 1) { await route.abort('failed'); return true; }
    } return false;
  });
  await openReports(page); await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · DRAFT', exact: true }).click();
  const editor = page.getByRole('region', { name: 'Chi tiết báo cáo' });
  await editor.getByLabel('Chọn tệp minh chứng').setInputFiles({ name: 'evidence.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nSame bytes on retry') });
  await editor.getByRole('button', { name: 'Tải lên minh chứng', exact: true }).click();
  await expect(editor.getByRole('button', { name: 'Thử lại thao tác vừa gửi' })).toBeVisible();
  await expect(editor.getByLabel('Chọn tệp minh chứng')).toBeDisabled(); await editor.getByRole('button', { name: 'Thử lại thao tác vừa gửi' }).click();
  await expect(editor.getByRole('region', { name: 'Minh chứng báo cáo' })).toContainText('Đang chờ quét');
  expect(uploads).toHaveLength(2); expect(uploads[1].key).toBe(uploads[0].key);
  for (const item of uploads) { expect(item.type).toContain('multipart/form-data; boundary='); expect(item.body).toContain('Same bytes on retry'); expect(item.body).toContain(rootId); expect(item.body).toContain('WEEKLY_REPORT'); expect(item.csrf).toBe('fixture-csrf'); }
  await editor.getByRole('button', { name: 'Lưu bản nháp' }).click(); await expect(editor.getByRole('button', { name: 'Lưu bản nháp' })).toBeDisabled();
  expect(server.reports[0].evidenceFileIds).toEqual(['00000000-0000-4000-8000-000000000042']);
  await expect(editor.getByRole('button', { name: /Tải minh chứng/ })).toBeDisabled();
});

test('T3-09 clean evidence downloads through authenticated transport and can be removed from draft', async ({ page }) => {
  const server = await setup(page); server.reports.push(report({ evidenceFileIds: ['00000000-0000-4000-8000-000000000041'] }));
  await openReports(page); await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · DRAFT', exact: true }).click();
  const editor = page.getByRole('region', { name: 'Chi tiết báo cáo' }); await expect(editor.getByText('existing.pdf · Đã kiểm tra')).toBeVisible();
  const downloading = page.waitForEvent('download'); await editor.getByRole('button', { name: /Tải minh chứng/ }).click();
  expect((await downloading).suggestedFilename()).toBe('existing.pdf');
  await editor.getByRole('button', { name: /Gỡ minh chứng/ }).click(); await editor.getByRole('button', { name: 'Lưu bản nháp' }).click();
  await expect.poll(() => server.reports[0].evidenceFileIds).toEqual([]);
  await expect(editor.getByText('Minh chứng đính kèm (0/20)')).toBeVisible();
  await openReports(page); await page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · DRAFT', exact: true }).click();
  await expect(page.getByRole('region', { name: 'Minh chứng báo cáo' })).toContainText('(0/20)');
});

function reviewedV1(): Review {
  return { reviewId: 'review-1', submissionId: 'sub-1', reviewedVersionNo: 1, verdict: 'REQUEST_REVISION',
    comment: 'Nhận xét v1 đã lưu', reviewerUserId: ids.other, createdAt: '2026-11-12T07:00:00Z', assignmentVersion: 3 };
}
function addV2(server: Awaited<ReturnType<typeof setup>>) {
  server.submissions.push({ ...server.submissions[0], submissionId: 'sub-2', versionNo: 2, url: 'https://example.test/v2' });
  Object.assign(server.task.assignments[0], { currentSubmissionId: 'sub-2', version: 5 });
}
test('T3-09 persisted submission feedback survives reload; 204 on v2 never inherits v1; GET carries context only', async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); server.submissionReviews.set('sub-1', reviewedV1()); addV2(server);
  const reads: Record<string, string>[] = [];
  server.intercept(async route => { if (route.request().method() === 'GET' && route.request().url().includes('/submissions/') && route.request().url().endsWith('/reviews')) reads.push(route.request().headers()); return false; });
  await openTask(page, 'mentor'); const feedback = page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' });
  await expect(feedback).toContainText('Bản nộp này chưa có nhận xét.');
  await page.getByRole('button', { name: 'Xem nhận xét bản v1', exact: true }).click();
  await expect(feedback).toContainText('Nhận xét v1 đã lưu'); await expect(feedback).toContainText('Yêu cầu sửa');
  await page.getByRole('button', { name: 'Xem nhận xét bản v2', exact: true }).click();
  await expect(feedback).toContainText('Bản nộp này chưa có nhận xét.'); await expect(page.getByText('Nhận xét v1 đã lưu')).toHaveCount(0);
  server.submissionReviews.set('sub-2', { ...reviewedV1(), reviewId: 'review-2', submissionId: 'sub-2', reviewedVersionNo: 2, verdict: 'ACCEPT', comment: 'Chấp nhận v2 đã lưu' });
  await page.reload(); await page.getByRole('button', { name: 'Nhiệm vụ tuần 3', exact: true }).click();
  await expect(feedback).toContainText('Chấp nhận v2 đã lưu');
  await page.getByRole('button', { name: 'Xem nhận xét bản v1', exact: true }).click(); await expect(feedback).toContainText('Nhận xét v1 đã lưu');
  expect(reads.length).toBeGreaterThanOrEqual(5);
  for (const headers of reads) { expect(headers['x-context-assignment-id']).toBe(ids.mentor); expect(headers['idempotency-key']).toBeUndefined(); expect(headers['x-csrf-token']).toBeUndefined(); }
});

for (const mismatch of ['submissionId', 'reviewedVersionNo'] as const) test('T3-09 review rejects mismatched ' + mismatch, async ({ page }) => {
  const server = await setup(page, 'SUBMITTED');
  server.submissionReviews.set('sub-1', { ...reviewedV1(), [mismatch]: mismatch === 'submissionId' ? 'foreign-submission' : 2 });
  await openTask(page);
  await expect(page.getByText('Dữ liệu của phạm vi này đã được đóng.', { exact: false })).toBeVisible();
  await expect(page.getByText('Nhận xét v1 đã lưu')).toHaveCount(0);
});

for (const status of [401, 404]) test('T3-09 review ' + status + ' clears previously displayed private feedback', async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); server.submissionReviews.set('sub-1', reviewedV1()); await openTask(page);
  await expect(page.getByText('Nhận xét v1 đã lưu')).toBeVisible();
  server.intercept(async route => { if (route.request().method() === 'GET' && route.request().url().endsWith('/reviews')) {
    await route.fulfill({ status, json: envelope(status === 404 ? 'RESOURCE_NOT_FOUND' : 'SESSION_EXPIRED') }); return true; } return false; });
  await page.getByRole('button', { name: 'Tải phiên bản hiện tại', exact: true }).click();
  await expect(page.getByText('Nhận xét v1 đã lưu')).toHaveCount(0);
  await expect(page.getByRole('region', { name: 'Bài nộp và trao đổi' })).toHaveCount(0);
});

for (const change of ['version', 'context']) test('T3-09 late review response cannot restore feedback after changing ' + change, async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); addV2(server);
  let release!: () => void, entered!: () => void, finished!: () => void;
  const gate = new Promise<void>(resolve => { release = resolve; }), started = new Promise<void>(resolve => { entered = resolve; });
  const responded = new Promise<void>(resolve => { finished = resolve; });
  server.intercept(async route => { if (route.request().method() === 'GET' && route.request().url().endsWith('/sub-1/reviews')) {
    entered(); await gate; await route.fulfill({ json: reviewedV1() }).catch(() => {}); finished(); return true; } return false; });
  await openTask(page, 'mentor'); await page.getByRole('button', { name: 'Xem nhận xét bản v1', exact: true }).click(); await started;
  await page.getByRole('button', { name: change === 'version' ? 'Xem nhận xét bản v2' : 'Đổi phạm vi fixture', exact: true }).click();
  release(); await responded;
  if (change === 'version') await expect(page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Bản nộp này chưa có nhận xét.');
  else await expect(page.getByRole('region', { name: 'Bài nộp và trao đổi' })).toHaveCount(0);
  await expect(page.getByText('Nhận xét v1 đã lưu')).toHaveCount(0);
});

test('T3-09 unavailable review is an error with retry, not an unreviewed submission', async ({ page }) => {
  const server = await setup(page, 'SUBMITTED'); let unavailable = true; server.submissionReviews.set('sub-1', reviewedV1());
  server.intercept(async route => { if (unavailable && route.request().method() === 'GET' && route.request().url().endsWith('/reviews')) {
    await route.fulfill({ status: 503, json: envelope('DEPENDENCY_UNAVAILABLE') }); return true; } return false; });
  await openTask(page); const feedback = page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' });
  await expect(feedback.getByRole('alert')).toContainText('DEPENDENCY_UNAVAILABLE');
  await expect(feedback.getByText('Bản nộp này chưa có nhận xét.')).toHaveCount(0);
  unavailable = false; await feedback.getByRole('button', { name: 'Tải lại nhận xét bài nộp' }).click();
  await expect(feedback).toContainText('Nhận xét v1 đã lưu');
});
