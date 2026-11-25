import { expect, test, type Page } from '@playwright/test';
import type { InternshipTask, WeeklyReport } from '../src/modules/internship/workflow';

const userId = '00000000-0000-4000-8000-000000000002';
const schoolId = '00000000-0000-4000-8000-000000000011';
const caseId = '00000000-0000-4000-8000-000000000301';
const pageOf = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 });
const tasks: InternshipTask[] = Array.from({ length: 5 }, (_, index) => ({ taskId: 'task-' + index, title: 'Nhiệm vụ ' + (index + 1),
  description: 'Nội dung nhiệm vụ từ API', groupId: 'group-1', dueAt: '2026-11-20T10:00:00Z', priority: 'NORMAL', lateAllowed: true, version: 1,
  assignments: [{ assignmentId: 'assignment-' + index, caseId, studentUserId: userId, state: 'COMPLETED', version: 1, currentSubmissionId: null }] }));
const reports: WeeklyReport[] = Array.from({ length: 40 }, (_, index) => ({ reportId: 'report-' + index, caseId,
  weekStart: '2026-11-16', revisionNo: 1, version: 1, state: 'DRAFT', completedWork: 'Việc đã hoàn thành từ API', difficulties: '', nextPlan: 'Kế hoạch từ API',
  evidenceFileIds: [], dueAt: '2099-01-01T00:00:00Z', submittedAt: null, reviewedAt: null, lateFlag: false }));

async function session(page: Page) {
  const writes: { path: string; body: Record<string, unknown>; key?: string }[] = [];
  const calendarTask = { ...tasks[0], taskId: 'calendar-task', title: 'Nhiệm vụ mở từ lịch' };
  await page.route('**/api/v1/**', route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '');
    if (request.method() !== 'GET') {
      writes.push({ path, body: request.postDataJSON(), key: request.headers()['idempotency-key'] });
      if (path === '/t3/reports' && request.method() === 'POST') return route.fulfill({ json: reports[0] });
    }
    if (path === '/auth/me') return route.fulfill({ json: { userId, displayName: 'Sinh viên kiểm tra', email: 'student-workflow@example.test', memberships: [{ role: 'STUDENT', organizationId: schoolId }] } });
    if (path === '/auth/me/permissions') return route.fulfill({ json: [{ role: 'STUDENT', organizationId: schoolId, permissions: ['student_profile:self:read', 'student_profile:self:write'] }] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'workflow-ui-token' } });
    if (path === '/t3/tasks') return route.fulfill({ json: pageOf(tasks) });
    if (path === '/t3/tasks/calendar-task') return route.fulfill({ json: calendarTask });
    if (path === '/t3/tasks/task-0') return route.fulfill({ json: tasks[0] });
    if (path === '/t3/calendar') return route.fulfill({ json: pageOf([{ taskId: calendarTask.taskId, assignmentId: null, title: 'Mốc công việc trong lịch', state: 'COMPLETED', dueAt: '2026-11-20T10:00:00Z' }]) });
    if (path === '/t3/reports') return route.fulfill({ json: pageOf(reports) });
    if (path === '/t3/reports/report-0') return route.fulfill({ json: reports[0] });
    if (path === '/terms' || path === '/invitations/me' || path === '/preferences/me' || path.endsWith('/submissions') || path.endsWith('/comments')) return route.fulfill({ json: pageOf([]) });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');
  await expect(page.locator('.workspace-student .topbar-title')).toHaveText('Tổng quan');
  return writes;
}

test('Reviewed weekly reports retain the T2 five-card layout and read-only mentor feedback', async ({ page }) => {
  await page.setViewportSize({ width: 1876, height: 940 }); await page.emulateMedia({ reducedMotion: 'reduce' });
  const writes = await session(page);
  const reviewed = reports.slice(0, 5).map((report, index): WeeklyReport => ({ ...report, reportId: 'reviewed-' + index,
    weekStart: ['2026-11-16', '2026-11-09', '2026-11-02', '2026-10-26', '2026-10-19'][index], state: 'REVIEWED', version: 3,
    submittedAt: '2026-11-20T09:47:15Z', reviewedAt: '2026-11-20T09:47:15Z', dueAt: '2026-11-24T16:59:59Z' }));
  await page.route('**/api/v1/t3/reports?**', route => route.fulfill({ json: pageOf(reviewed) }));
  await page.route('**/api/v1/t3/reports/reviewed-**', route => {
    const path = new URL(route.request().url()).pathname;
    const record = reviewed.find(report => path.split('/').includes(report.reportId))!;
    return route.fulfill({ json: path.endsWith('/reviews') ? { reportId: record.reportId, revisionNo: 1,
      reviewerUserId: 'mentor-test', reviewedAt: record.reviewedAt, comment: 'Nhận xét đã lưu từ Mentor.' } : record });
  });
  await navigate(page, 'Báo cáo & đánh giá');
  await page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).selectOption(caseId);
  const cards = page.locator('.student-reports > .t3-workflow > .stack > button.secondary:not(:first-child)');
  await expect(cards).toHaveCount(5);
  const icon = await cards.first().evaluate(node => {
    const style = getComputedStyle(node, '::before');
    return { position: style.position, transform: style.transform, width: style.width, background: style.backgroundImage };
  });
  expect(icon.position).toBe('static');
  expect(icon.transform).toBe('none');
  expect(icon.width).toBe('38px');
  expect(icon.background).toContain('data:image/svg+xml');
  const tops = await cards.evaluateAll(nodes => nodes.map(node => node.getBoundingClientRect().top));
  expect(Math.max(...tops) - Math.min(...tops)).toBeLessThan(2);
  await cards.first().click();
  const detail = page.getByRole('region', { name: 'Chi tiết báo cáo', exact: true });
  await expect(detail.getByText('Nhận xét đã lưu từ Mentor.', { exact: true })).toBeVisible();
  await expect(detail.getByRole('button', { name: 'Lưu bản nháp', exact: true })).toHaveCount(0);
  await expect(detail.locator(':scope > p').filter({ hasText: 'Đã nhận xét:' })).toHaveCount(1);
  await expect(detail.getByRole('region', { name: 'Minh chứng báo cáo', exact: true })).toBeVisible();
  await expect(detail.locator(':scope > dl > dd')).toHaveCount(3);
  await expect(detail.getByLabel('Mã lý do sửa báo cáo', { exact: true })).toBeVisible();
  expect(writes).toEqual([]);
  await page.evaluate(() => window.scrollTo(0, 0));
  await page.screenshot({ path: '../target/student-reviewed-t2-reference.png', fullPage: false, animations: 'disabled' });
});
async function navigate(page: Page, name: string) {
  const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await toggle.isVisible()) await toggle.click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name, exact: true }).click();
}
async function detailIsInView(page: Page, locator: ReturnType<Page['locator']>) {
  await expect.poll(() => locator.evaluate(node => node.getBoundingClientRect().top)).toBeLessThan(440);
  const top = (await locator.boundingBox())!.y, bar = (await page.locator('.topbar').boundingBox())!;
  expect(top).toBeGreaterThanOrEqual(bar.y + bar.height);
}

for (const width of [1440, 390, 320]) {
  test(`Student task board preserves T2 columns, calendar and detail reveal at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 740 }); await page.emulateMedia({ reducedMotion: 'reduce' });
    const writes = await session(page); await navigate(page, 'Nhiệm vụ & sản phẩm');
    const board = page.locator('.student-tasks-page .workflow-surface > div:has(> section.panel > h4)');
    const columns = board.locator(':scope > section.panel');
    await expect(columns).toHaveCount(5);
    await expect(columns.last().getByRole('article')).toHaveCount(5);
    await expect(page.locator('.student-tasks-page .t3-kanban-col-head')).toHaveCount(0);
    if (width === 1440) {
      const heights = await columns.evaluateAll(nodes => nodes.map(node => node.getBoundingClientRect().height));
      expect(Math.max(...heights) - Math.min(...heights)).toBeLessThan(2);
      const dots = await columns.locator(':scope > h4').evaluateAll(nodes => nodes.map(node => getComputedStyle(node, '::before').backgroundColor));
      expect(new Set(dots).size).toBe(5);
    }
    const date = page.getByLabel('Hạn đến ngày', { exact: true });
    expect(await date.evaluate(node => node.parentElement!.tagName)).toBe('LABEL');
    await columns.last().getByRole('button', { name: 'Nhiệm vụ 1', exact: true }).click();
    const detail = page.locator('.student-tasks-page .workflow-surface > section.panel').filter({ has: page.getByRole('heading', { name: 'Nhiệm vụ 1', exact: true }) });
    await detailIsInView(page, detail);
    const month = page.getByLabel('Tháng', { exact: true });
    expect(await month.evaluate(node => node.parentElement!.parentElement!.matches('section.panel'))).toBe(true);
    await month.fill('2026-11');
    await page.getByRole('grid', { name: 'Lịch hạn công việc' }).getByRole('button', { name: 'Mốc công việc trong lịch: Hoàn thành', exact: true }).click();
    const calendarDetail = page.locator('.student-tasks-page .workflow-surface > section.panel').filter({ has: page.getByRole('heading', { name: 'Nhiệm vụ mở từ lịch', exact: true }) });
    await detailIsInView(page, calendarDetail);
    expect(writes).toEqual([]);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `../target/student-t2-tasks-${width}.png`, fullPage: true, animations: 'disabled' });
  });

  test(`Student weekly reports preserve T2 cards and reveal selected or created drafts at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 740 }); await page.emulateMedia({ reducedMotion: 'reduce' });
    const writes = await session(page); await navigate(page, 'Báo cáo & đánh giá');
    await page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).selectOption(caseId);
    const cards = page.locator('.student-reports > .t3-workflow > .stack > button.secondary:not(:first-child)');
    await expect(cards).toHaveCount(40);
    await expect(page.locator('.student-reports .t3-report-list')).toHaveCount(0);
    await expect(cards.first()).toHaveCSS('border-left-width', '3px');
    const date = page.getByLabel('Thứ Hai bắt đầu tuần', { exact: true });
    expect(await date.evaluate(node => node.parentElement!.matches('label:has(> input[type="date"])'))).toBe(true);
    await cards.first().click();
    const detail = page.getByRole('region', { name: 'Chi tiết báo cáo', exact: true });
    await detailIsInView(page, detail);
    await expect(detail.getByText('Việc đã hoàn thành từ API', { exact: true })).toBeVisible();
    const form = page.locator('.student-reports > .t3-workflow > .stack > form');
    await date.fill('2026-11-16');
    await form.getByLabel('Việc đã làm', { exact: true }).fill('Công việc tạo mới');
    await form.getByLabel('Khó khăn', { exact: true }).fill('');
    await form.getByLabel('Kế hoạch tiếp theo', { exact: true }).fill('Kế hoạch tạo mới');
    await form.getByRole('button', { name: 'Tạo bản nháp báo cáo', exact: true }).click();
    await expect.poll(() => writes.length).toBe(1);
    expect(writes[0].body).toEqual({ caseId, weekStart: '2026-11-16', completedWork: 'Công việc tạo mới', difficulties: '', nextPlan: 'Kế hoạch tạo mới', evidenceFileIds: [] });
    expect(writes[0].key).toBeTruthy();
    await detailIsInView(page, detail);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.screenshot({ path: `../target/student-t2-reports-${width}.png`, fullPage: true, animations: 'disabled' });
  });
}
