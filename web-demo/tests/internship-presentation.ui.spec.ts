import { expect, test, type Page } from '@playwright/test';
import { cohort, grants, group, hr, ids, me, mentor, mockContextLabels } from './fixtures/internship';
import { openWorkspacePage } from './helpers/workspace';

test.use({ locale: 'en-US' });
const rows = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 });

async function workspace(page: Page, schoolRole: 'HR' | 'MENTOR' | 'SCHOOL_STAFF', withContent = false) {
  const assignment = schoolRole === 'HR' ? hr : mentor;
  const school = schoolRole === 'SCHOOL_STAFF';
  const schoolPermissions = ['t3:workspace:read', 't3:grades:publish', 't3:result_policy:manage', 't3:coordination:manage', 't3:cases:close', 't3:enterprise_quality:school:write'];
  const requests: { path: string; method: string; context: string | undefined; body: unknown }[] = [];
  await page.clock.install({ time: new Date('2026-11-19T10:00:00Z') });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await page.route('**/api/v1/**', route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', '');
    requests.push({ path: path + url.search, method: request.method(), context: request.headers()['x-context-assignment-id'], body: request.method() === 'GET' ? null : request.postDataJSON() });
    if (path === '/auth/me') return route.fulfill({ json: school ? { ...me, memberships: [{ role: 'SCHOOL_STAFF', organizationId: ids.school }] } : me });
    if (path.endsWith('/permissions')) return route.fulfill({ json: [school ? { role: 'SCHOOL_STAFF', organizationId: ids.school, permissions: schoolPermissions }
      : { ...grants[0], permissions: [...grants[0].permissions, schoolRole === 'HR' ? 't3:mentor:invite' : 't3:task:create', ...(withContent ? ['internship:enterprise:evaluate', 't3:grade_visibility:manage', 't3:coordination:manage', 't3:hiring_interest:manage'] : [])] }] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' } });
    if (path === '/t3/context-assignments/me') return route.fulfill({ json: [assignment] });
    if (path === '/t3/cohorts') return route.fulfill({ json: rows([cohort]) });
    if (path === '/terms') return route.fulfill({ json: rows([{ termId: ids.term, schoolOrgId: ids.school, termCode: 'Học kỳ kiểm tra bố cục' }]) });
    if (path.endsWith('/groups')) return route.fulfill({ json: rows([group]) });
    if (withContent && path === '/t3/tasks') return route.fulfill({ json: rows([{
      taskId: ids.other, groupId: ids.group, title: 'Kiểm thử giao diện với tên nhiệm vụ dài và nhiều nội dung cần theo dõi',
      description: 'Đối chiếu bố cục, trường nhập và thao tác trong phạm vi thực tập. '.repeat(8), dueAt: '2026-11-25T10:00:00Z',
      priority: 'HIGH', lateAllowed: true, version: 1,
      assignments: [{ assignmentId: ids.other, caseId: ids.case, studentUserId: ids.user, state: 'IN_PROGRESS', version: 1, currentSubmissionId: null }]
    }]) });
    if (withContent && path.endsWith('/dashboard')) return route.fulfill({ json: {
      cohortCount: 1, caseCount: 10, groupCount: 2, mentorCount: 2,
      assignmentCounts: ['NEW', 'IN_PROGRESS', 'SUBMITTED', 'REVISION_REQUIRED', 'COMPLETED'].map(state => ({ state, count: state === 'IN_PROGRESS' ? 10 : 0 })),
      lateAssignmentCount: 2, pendingReviewCount: 3
    } });
    if (withContent && path === '/t3/evaluations') return route.fulfill({ status: 204 });
    if (withContent && path === '/t3/rubric-templates') return route.fulfill({ json: rows([{
      templateId: ids.other, templateVersion: 1, criteria: [{ criterionCode: 'QUALITY', label: 'Chất lượng sản phẩm và khả năng phối hợp trong nhóm thực tập', weight: 100 }]
    }]) });
    if (path.endsWith('/groups:auto')) return route.fulfill({ status: 422, json: { errorCode: 'NO_GROUPABLE_CASES', requestId: 'test-grouping-request', fieldErrors: [] } });
    return route.fulfill({ json: rows([]) });
  });
  await mockContextLabels(page);
  await page.goto('/');
  await openWorkspacePage(page, school ? 'Quản lý thực tập' : 'Không gian thực tập');
  if (!school) {
    await page.getByLabel('Phân công đang làm việc', { exact: true }).selectOption(assignment.id);
    await page.getByLabel('Đợt thực tập', { exact: true }).selectOption(ids.cohort);
  }
  return requests;
}

for (const width of [1440, 390, 320]) test(`Mentor presentation keeps selected tabs and a Vietnamese calendar month at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  const requests = await workspace(page, 'MENTOR');
  await page.getByRole('button', { name: 'Xem nhóm ' + group.code, exact: true }).click();
  const calendar = page.getByRole('button', { name: 'Lịch công việc', exact: true });
  await calendar.click();
  await calendar.hover();
  await expect(calendar).toHaveCSS('background-color', 'rgb(231, 246, 240)');
  const month = page.getByLabel('Tháng', { exact: true });
  await month.fill('2026-11');
  const display = month.locator('..').locator('.vn-date-display');
  await expect(display).toHaveText('Tháng 11/2026');
  await expect(display).toBeVisible();
  await expect(month).toHaveValue('2026-11');
  await month.fill('2026-02');
  await expect(display).toHaveText('Tháng 2/2026');
  const grid = page.getByRole('grid', { name: 'Lịch hạn công việc' });
  await expect(grid.getByRole('gridcell')).toHaveCount(28);
  const expectedFrom = await page.evaluate(() => new Date('2026-02-01T00:00:00').toISOString());
  await expect.poll(() => requests.some(request => {
    const url = new URL(request.path, 'http://fixture.test');
    return url.pathname === '/t3/calendar' && url.searchParams.get('from') === expectedFrom && request.context === ids.mentor;
  })).toBe(true);
  expect(requests.filter(request => request.method !== 'GET')).toEqual([]);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.evaluate(() => window.scrollTo(0, 0));
  await expect.poll(() => page.evaluate(() => window.scrollY)).toBe(0);
  await page.screenshot({ path: `../target/t3-consistent-calendar-${width}.png`, fullPage: true, animations: 'disabled' });
});

test('HR grouping explains an empty candidate list while retaining the T3 DTO and context', async ({ page }) => {
  const requests = await workspace(page, 'HR');
  const menu = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await menu.isVisible()) await menu.click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Nhóm thực tập', exact: true }).click();
  await page.getByRole('button', { name: 'Xem trước nhóm', exact: true }).click();
  await expect(page.getByText(/Không còn hồ sơ chưa được phân nhóm trong đợt này/)).toBeVisible();
  const writes = requests.filter(request => request.method !== 'GET');
  expect(writes).toHaveLength(1);
  expect(writes[0]).toMatchObject({ path: '/t3/cohorts/' + ids.cohort + '/groups:auto', context: ids.hr,
    body: { mode: 'PREVIEW', groupSize: 10, expectedCohortVersion: cohort.version } });
});

async function layoutIssues(page: Page) {
  return page.evaluate(() => {
    const issues: string[] = [];
    if (document.documentElement.scrollWidth > innerWidth + 1) issues.push('Page overflows horizontally');
    const visible = (element: Element) => {
      const box = element.getBoundingClientRect(), style = getComputedStyle(element);
      return box.width > 0 && box.height > 0 && style.visibility !== 'hidden' && style.position !== 'absolute' && style.position !== 'fixed';
    };
    const name = (element: Element) => (element.className || element.tagName) + ': ' + element.textContent?.trim().slice(0, 65);
    if (innerWidth > 900) for (const toolbar of document.querySelectorAll('.workspace-native-t3 .t3-grade-toolbar')) {
      const bottoms = [...toolbar.querySelectorAll('input, select, button')].filter(visible).map(control => control.getBoundingClientRect().bottom);
      if (bottoms.length && Math.max(...bottoms) - Math.min(...bottoms) > 1) issues.push('Grade filter fields and actions are misaligned');
    }
    const header = document.querySelector('.workspace-native-t3 .workspace-main > .topbar');
    if (header) {
      const frame = header.getBoundingClientRect();
      for (const element of header.querySelectorAll('.portal-topbar-identity, .portal-topbar-identity > *, .topbar-right, .t3-context-bar')) {
        if (!visible(element)) continue;
        const box = element.getBoundingClientRect();
        if (box.top < frame.top - 1 || box.bottom > frame.bottom + 1) issues.push(`Header content escapes frame: ${name(element)}`);
      }
    }
    for (const parent of document.querySelectorAll('.workspace-main .stack, .workspace-main .button-row, .t3-view-nav, .t3-context-bar, .t3-context-scope, .notification-scope-fields, .t3-inline-form, .workspace-main .section-heading, .workspace-main form, .workspace-main fieldset')) {
      const children = [...parent.children].filter(visible);
      for (let i = 0; i < children.length; i++) for (let j = i + 1; j < children.length; j++) {
        const a = children[i].getBoundingClientRect(), b = children[j].getBoundingClientRect();
        if (Math.min(a.right, b.right) - Math.max(a.left, b.left) > 3 && Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top) > 3)
          issues.push(`Sibling overlap in ${parent.className}: ${name(children[i])} / ${name(children[j])}`);
      }
    }
    for (const element of document.querySelectorAll('.workspace-native-t3 .content input:not([type="checkbox"]):not([type="radio"]), .workspace-native-t3 .content select, .workspace-native-t3 .content textarea')) {
      if (!visible(element)) continue;
      const frame = element.closest('fieldset, .panel');
      if (!frame || element.closest('.t3-kanban, .t3-table-scroll')) continue;
      const box = element.getBoundingClientRect(), outer = frame.getBoundingClientRect();
      if (box.right > outer.right + 1 || box.left < outer.left - 1) issues.push(`Control escapes card: ${element.getAttribute('aria-label') || element.closest('label')?.textContent?.trim()}`);
      if (element.tagName === 'TEXTAREA' && getComputedStyle(element).display !== 'block') issues.push('Textarea remains inline: ' + element.closest('label')?.textContent?.trim());
    }
    for (const button of document.querySelectorAll('.workspace-native-t3 .content form:not(.t3-inline-form) > :is(label, fieldset, p, small) + button, .workspace-native-t3 .content fieldset > :is(label, p, small) + button')) {
      const label = button.previousElementSibling;
      if (!label || !visible(button) || !visible(label)) continue;
      const field = label.getBoundingClientRect(), action = button.getBoundingClientRect();
      if (Math.min(field.right, action.right) > Math.max(field.left, action.left) && action.top - field.bottom < 12)
        issues.push(`Form action touches preceding field: ${button.textContent?.trim()}`);
    }
    return issues;
  });
}

for (const width of [1440, 1024, 768, 390, 320]) test(`T3 cards and controls do not overlap across workbench views at ${width}px`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 900 });
  const requests = await workspace(page, 'MENTOR', true);
  await page.getByRole('button', { name: 'Xem nhóm ' + group.code, exact: true }).click();
  const issues: string[] = [];
  const check = async (stage: string) => {
    await page.evaluate(() => window.scrollTo(0, 0));
    issues.push(...(await layoutIssues(page)).map(issue => stage + ': ' + issue));
    await page.screenshot({ path: testInfo.outputPath(stage + '.png'), fullPage: true, animations: 'disabled' });
  };
  for (const [stage, label] of [['board', 'Bảng Kanban'], ['list', 'Danh sách nhiệm vụ'], ['create', 'Giao nhiệm vụ mới'], ['calendar', 'Lịch công việc']] as const) {
    await page.getByRole('button', { name: label, exact: true }).click();
    await check(stage);
  }
  await page.getByRole('button', { name: 'Báo cáo tuần', exact: true }).click();
  await page.getByRole('button', { name: 'Mở báo cáo tuần', exact: true }).click();
  await page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).selectOption(ids.case);
  await check('reports');
  const navigate = async (label: string) => {
    const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
    if (await toggle.isVisible()) await toggle.click();
    await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: label, exact: true }).click();
  };
  await navigate('Quản lý thực tập');
  await page.getByRole('combobox', { name: 'Phân công quản lý thực tập', exact: true }).selectOption(ids.mentor);
  await expect(page.getByLabel('Phân bố tiến độ nhiệm vụ', { exact: true })).toBeVisible();
  await check('operations');
  const caseForm = page.locator('.t3-case-operations');
  const openCase = caseForm.getByRole('button', { name: 'Mở hồ sơ vận hành', exact: true });
  await expect(openCase).toBeDisabled();
  await caseForm.getByRole('textbox', { name: 'Mã Case được cấp', exact: true }).fill(ids.case);
  await expect(openCase).toBeEnabled();
  await caseForm.screenshot({ path: testInfo.outputPath('case-form-spacing.png'), animations: 'disabled' });
  await navigate('Đánh giá thực tập');
  await page.getByRole('combobox', { name: 'Phân công đánh giá', exact: true }).selectOption(ids.mentor);
  await page.getByRole('combobox', { name: 'Hồ sơ trong nhóm', exact: true }).selectOption(ids.case);
  await page.getByRole('combobox', { name: 'Rubric áp dụng', exact: true }).selectOption(ids.other + ':1');
  await check('evaluation');
  expect(requests.filter(request => request.method !== 'GET')).toEqual([]);
  expect(issues).toEqual([]);
});

for (const width of [1440, 768, 320]) test(`HR expanded cohort and mentor forms stay within their cards at ${width}px`, async ({ page }, testInfo) => {
  await page.setViewportSize({ width, height: 900 });
  const requests = await workspace(page, 'HR', true);
  const issues: string[] = [];
  const navigate = async (label: string) => {
    const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
    if (await toggle.isVisible()) await toggle.click();
    await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: label, exact: true }).click();
  };
  const check = async (stage: string) => {
    await page.evaluate(() => window.scrollTo(0, 0));
    issues.push(...(await layoutIssues(page)).map(issue => stage + ': ' + issue));
    await page.screenshot({ path: testInfo.outputPath(stage + '.png'), fullPage: true, animations: 'disabled' });
  };
  await navigate('Đợt thực tập');
  await page.getByRole('button', { name: '+ Tạo đợt thực tập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tạo đợt thực tập', exact: true })).toBeVisible();
  await check('cohort-create-and-intake');
  await navigate('Nhóm thực tập');
  await page.getByRole('button', { name: 'Xem nhóm ' + group.code, exact: true }).click();
  await check('group-management');
  await navigate('Người hướng dẫn');
  const openGroup = page.getByRole('button', { name: 'Xem nhóm ' + group.code, exact: true });
  if (await openGroup.isVisible()) await openGroup.click();
  await expect(page.getByRole('heading', { name: 'Mời Mentor cho nhóm ' + group.code, exact: true })).toBeVisible();
  await check('mentor-invitation');
  expect(requests.filter(request => request.method !== 'GET')).toEqual([]);
  expect(issues).toEqual([]);
});

async function expandedRecords(page: Page) {
  const reportId = '00000000-0000-4000-8000-000000000030';
  const fileId = '00000000-0000-4000-8000-000000000042';
  const evaluationFile = '00000000-0000-4000-8000-000000000043';
  const title = 'Nhiệm vụ có bài nộp đang chờ nhận xét';
  const task = { taskId: ids.other, title, description: 'Mô tả nhiệm vụ và các yêu cầu cần kiểm tra. '.repeat(6), groupId: ids.group,
    dueAt: '2026-11-25T10:00:00Z', priority: 'HIGH', lateAllowed: true, version: 1,
    assignments: [{ assignmentId: ids.other, caseId: ids.case, studentUserId: ids.user, state: 'SUBMITTED', version: 2, currentSubmissionId: 'submission' }] };
  const report = { reportId, caseId: ids.case, weekStart: '2026-11-16', revisionNo: 1, version: 2, state: 'SUBMITTED',
    completedWork: 'Công việc đã hoàn thành cần được Mentor nhận xét. '.repeat(8), difficulties: 'Khó khăn khi phối hợp và xử lý dữ liệu.', nextPlan: 'Hoàn thiện phần còn lại.',
    evidenceFileIds: [fileId], dueAt: '2026-11-24T10:00:00Z', submittedAt: '2026-11-19T09:00:00Z', reviewedAt: null, lateFlag: false };
  const rubric = { templateId: ids.other, templateVersion: 1, criteria: [{ criterionCode: 'QUALITY', label: 'Chất lượng sản phẩm và phối hợp công việc', weight: 100 }] };
  const evaluation = { evaluationId: ids.other, caseId: ids.case, template: rubric, scores: [{ criterionCode: 'QUALITY', score: 4, comment: 'Nội dung nhận xét đã lưu.', evidenceFileIds: [evaluationFile] }],
    total100: 80, state: 'DRAFT', revisionNo: 1, version: 2, publishedAt: null, decisionRef: null };
  const openedCase = { caseId: ids.case, placementId: ids.other, studentUserId: ids.user, schoolOrgId: ids.school, enterpriseOrgId: ids.enterprise, termId: ids.term, state: 'IN_PROGRESS', version: 2 };
  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url()), path = url.pathname.replace('/api/v1', '');
    if (path === '/t3/tasks') return route.fulfill({ json: rows([task]) });
    if (path === '/t3/tasks/' + ids.other) return route.fulfill({ json: task });
    if (path.endsWith('/submissions')) return route.fulfill({ json: rows([{ submissionId: 'submission', assignmentId: ids.other, versionNo: 1, fileIds: [],
      url: 'https://example.test/deliverable', note: 'Nội dung bài nộp cần kiểm tra.', submittedAt: '2026-11-18T10:00:00Z', lateFlag: false }]) });
    if (path.startsWith('/t3/submissions/') && path.endsWith('/reviews')) return route.fulfill({ status: 204 });
    if (path.endsWith('/comments')) return route.fulfill({ json: rows([{ commentId: ids.other, assignmentId: ids.other, authorUserId: ids.user,
      text: 'Trao đổi dài về nhiệm vụ để kiểm tra nội dung xuống dòng. '.repeat(8), createdAt: '2026-11-18T10:00:00Z' }]) });
    if (path === '/t3/reports') return route.fulfill({ json: rows([report]) });
    if (path === '/t3/reports/' + reportId) return route.fulfill({ json: report });
    if (path === '/t3/evaluations') return route.fulfill({ json: evaluation });
    if (path === '/t3/evaluations/' + ids.other + '/history') return route.fulfill({ json: rows([evaluation]) });
    if (path === '/t3/school/evaluations') return route.fulfill({ json: rows([{ ...evaluation, state: 'SUBMITTED' }]) });
    if (path === '/t3/cases/' + ids.case) return route.fulfill({ json: openedCase });
    if (path.endsWith('/hiring-interest')) return route.fulfill({ json: { caseId: ids.case, interested: true, note: 'Tiếp tục trao đổi sau thực tập.', version: 1, updatedAt: null } });
    if (path === '/t3/school/issues') return route.fulfill({ json: rows([{ issueId: ids.other, caseId: ids.case, category: 'COMMUNICATION',
      description: 'Phối hợp với người hướng dẫn để làm rõ công việc. '.repeat(8), state: 'OPEN', ownerUserId: ids.user, version: 1, createdAt: '2026-11-18T10:00:00Z', updatedAt: '2026-11-18T10:00:00Z' }]) });
    if (path.includes('visibility')) return route.fulfill({ json: { enabled: true, version: 1, schoolOrgId: ids.school, enterpriseOrgId: ids.enterprise,
      termId: ids.term, caseId: url.searchParams.get('caseId'), scope: url.searchParams.has('caseId') ? 'CASE' : 'ORGANIZATION_TERM' } });
    if (path === '/t3/enterprise-quality/templates') return route.fulfill({ json: rows([{ templateId: ids.other, version: 1,
      criteria: [{ code: 'COOPERATION', label: 'Mức độ phối hợp của doanh nghiệp trong quá trình thực tập' }] }]) });
    if (path.startsWith('/files/') && path.endsWith('/metadata')) {
      const evaluation = path.includes(evaluationFile);
      return route.fulfill({ json: { fileId: evaluation ? evaluationFile : fileId, resource: { resourceType: evaluation ? 'EVALUATION' : 'WEEKLY_REPORT', resourceId: evaluation ? ids.other : reportId },
        purpose: evaluation ? 'EVIDENCE' : 'WEEKLY_REPORT', originalName: 'Minh chứng công việc và kết quả thực tập.pdf', contentType: 'application/pdf', sizeBytes: 100,
        sha256: '0'.repeat(64), scanStatus: 'CLEAN', createdAt: '2026-11-18T10:00:00Z', deletedAt: null } });
    }
    return route.fallback();
  });
  return { title };
}

for (const role of ['MENTOR', 'HR', 'SCHOOL_STAFF'] as const) for (const width of [1440, 390, 320]) {
  test(`${role} expanded workflows have comfortable form spacing at ${width}px`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    const writes: string[] = [];
    page.on('request', request => { if (request.url().includes('/api/') && request.method() !== 'GET') writes.push(request.url()); });
    await workspace(page, role, true);
    const { title } = await expandedRecords(page);
    const issues: string[] = [];
    const check = async (stage: string) => {
      await page.evaluate(() => window.scrollTo(0, 0));
      issues.push(...(await layoutIssues(page)).map(issue => stage + ': ' + issue));
      await page.screenshot({ path: testInfo.outputPath(stage + '.png'), fullPage: true, animations: 'disabled' });
    };
    if (role === 'MENTOR') {
      await page.getByRole('button', { name: 'Xem nhóm ' + group.code, exact: true }).click();
      await page.getByRole('button', { name: 'Bảng Kanban', exact: true }).click();
      await page.locator('.t3-kanban').getByRole('button', { name: title, exact: true }).click();
      await expect(page.getByRole('textbox', { name: 'Nhận xét bài nộp', exact: true })).toBeVisible();
      await check('submission-review-and-comments');
      await page.getByRole('button', { name: 'Đóng chi tiết', exact: true }).click();
      await page.getByRole('button', { name: 'Báo cáo tuần', exact: true }).click();
      await page.getByRole('button', { name: 'Mở báo cáo tuần', exact: true }).click();
      await page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).selectOption(ids.case);
      await page.getByRole('button', { name: /Tuần 2026-11-16/ }).click();
      await expect(page.getByRole('textbox', { name: 'Nhận xét báo cáo', exact: true })).toBeVisible();
      await check('report-review-and-evidence');
    }
    await openWorkspacePage(page, 'Quản lý thực tập');
    if (role === 'SCHOOL_STAFF') {
      await page.getByRole('combobox', { name: 'Kỳ quản lý thực tập', exact: true }).selectOption(ids.term);
      await page.getByRole('combobox', { name: 'Rubric chất lượng', exact: true }).selectOption(ids.other + ':1');
    } else await page.getByRole('combobox', { name: 'Phân công quản lý thực tập', exact: true }).selectOption(role === 'HR' ? ids.hr : ids.mentor);
    await page.getByRole('textbox', { name: 'Mã Case được cấp', exact: true }).fill(ids.case);
    await page.getByRole('button', { name: 'Mở hồ sơ vận hành', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Yêu cầu phối hợp', exact: true })).toBeVisible();
    if (role === 'SCHOOL_STAFF') await expect(page.getByRole('textbox', { name: 'Mã lý do đóng Case', exact: true })).toBeVisible();
    if (role === 'HR') await expect(page.getByRole('textbox', { name: 'Ghi chú quan tâm tuyển dụng', exact: true })).toBeVisible();
    await check('case-coordination-and-decisions');
    await openWorkspacePage(page, 'Đánh giá thực tập');
    if (role === 'SCHOOL_STAFF') {
      await page.getByRole('combobox', { name: 'Kỳ đánh giá', exact: true }).selectOption(ids.term);
      await page.getByRole('button', { name: /Hồ sơ .*SUBMITTED/ }).click();
      await expect(page.getByRole('textbox', { name: 'Căn cứ quyết định', exact: true })).toBeVisible();
      await page.getByText('Nhập mã kỳ được phân công', { exact: true }).click();
    } else {
      await page.getByRole('combobox', { name: 'Phân công đánh giá', exact: true }).selectOption(role === 'HR' ? ids.hr : ids.mentor);
      if (role === 'MENTOR') {
        await page.getByRole('combobox', { name: 'Hồ sơ trong nhóm', exact: true }).selectOption(ids.case);
        await expect(page.getByLabel('Tệp minh chứng đánh giá')).toBeVisible();
      } else await expect(page.getByRole('checkbox', { name: 'Cho phép sinh viên xem điểm', exact: true })).toBeVisible();
    }
    await check('grade-policy-and-evidence');
    expect(writes).toEqual([]);
    expect(issues).toEqual([]);
  });
}
