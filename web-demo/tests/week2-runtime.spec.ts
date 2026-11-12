import { test, expect, type Browser } from '@playwright/test';
import { randomUUID } from 'node:crypto';
test('week 2 skills, cooperation, consent and tasks use real authenticated APIs', async ({ browser }) => {
  test.skip(process.env.E2E_WEEK2 !== 'true', 'Run from Week2WorkflowIT on isolated MySQL');
  test.setTimeout(150000);
  const actor = async (browser: Browser, email: string) => {
    const context = await browser.newContext(), page = await context.newPage(); await page.goto('/');
    await page.getByLabel('Email', { exact: true }).fill(email); await page.getByLabel('Mật khẩu', { exact: true }).fill(process.env.E2E_PASSWORD!);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
    return { context, page };
  };
  const student = await actor(browser, 'student@example.test');
  const studentIdentity = await (await student.page.request.get('/api/v1/auth/me')).json();
  await student.page.getByRole('button', { name: 'Thông tin chung', exact: true }).click();
  await expect(student.page.getByRole('heading', { name: 'Kỹ năng và mức hoàn thiện hồ sơ' })).toBeVisible();
  const note = student.page.getByLabel('Ghi chú', { exact: true }).first(); await note.fill('W2 browser preserved skill note');
  await student.page.getByRole('button', { name: 'Lưu kỹ năng', exact: true }).click();
  await expect(student.page.getByRole('button', { name: 'Lưu kỹ năng', exact: true })).toBeEnabled();
  await student.page.reload(); await student.page.getByRole('button', { name: 'Thông tin chung', exact: true }).click();
  await expect(student.page.getByLabel('Ghi chú', { exact: true }).first()).toHaveValue('W2 browser preserved skill note');
  await student.page.screenshot({ path: '../target/week2-browser-skills.png', fullPage: true });
  await student.page.getByRole('button', { name: 'Lời mời & nguyện vọng', exact: true }).click();
  await expect(student.page.getByText('Đang có hiệu lực', { exact: true })).toHaveCount(0);
  await student.page.getByLabel('Kỹ năng', { exact: true }).check();
  await student.page.getByRole('button', { name: 'Đồng ý chia sẻ các mục đã chọn', exact: true }).click();
  await expect(student.page.getByText('Đang có hiệu lực', { exact: true })).toBeVisible();
  await student.page.screenshot({ path: '../target/week2-browser-consent.png', fullPage: true });
  await student.page.getByRole('button', { name: 'Thu hồi quyền chia sẻ', exact: true }).click();
  await expect(student.page.getByText('Đang có hiệu lực', { exact: true })).toHaveCount(0);
  await student.context.close();

  const school = await actor(browser, 'school@example.test');
  await school.page.getByRole('button', { name: 'Hợp tác doanh nghiệp', exact: true }).click();
  await school.page.getByRole('button', { name: 'Xem nội dung đề nghị 1', exact: true }).click();
  await expect(school.page.getByRole('heading', { name: 'W2 browser cooperation', exact: true })).toBeVisible();
  await school.page.screenshot({ path: '../target/week2-browser-cooperation.png', fullPage: true });
  await school.page.getByLabel('Căn cứ quyết định', { exact: true }).fill('DEMO-BROWSER-W2');
  await school.page.getByRole('button', { name: 'Lưu kết quả xét duyệt', exact: true }).click();
  await expect(school.page.getByRole('heading', { name: 'Đề nghị 1 · Đã duyệt', exact: true })).toBeVisible();
  await school.page.reload(); await school.page.getByRole('button', { name: 'Hợp tác doanh nghiệp', exact: true }).click();
  await expect(school.page.getByRole('heading', { name: 'Đề nghị 1 · Đã duyệt', exact: true })).toBeVisible(); await school.context.close();

  const mentor = await actor(browser, 'mentor@example.test');
  await mentor.page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
  await mentor.page.getByLabel('Phân công đang làm việc', { exact: true }).selectOption({ index: 1 });
  await mentor.page.getByLabel('Đợt thực tập', { exact: true }).selectOption({ index: 1 });
  await mentor.page.getByRole('button', { name: /Xem nhóm/ }).first().click();
  await mentor.page.getByLabel('Tiêu đề', { exact: true }).fill('W2 browser task');
  await mentor.page.getByLabel('Mô tả', { exact: true }).fill('Assigned through the real Mentor API');
  const due = new Date(Date.now() + 86400000); due.setMinutes(due.getMinutes() - due.getTimezoneOffset());
  await mentor.page.getByLabel('Hạn hoàn thành', { exact: true }).fill(due.toISOString().slice(0, 16));
  await mentor.page.getByRole('group', { name: 'Hồ sơ được giao', exact: true }).getByRole('checkbox').first().check();
  await mentor.page.getByRole('button', { name: 'Giao nhiệm vụ', exact: true }).click();
  await expect(mentor.page.getByRole('button', { name: 'W2 browser task', exact: true }).first()).toBeVisible();
  await expect(mentor.page.getByRole('grid', { name: 'Lịch hạn công việc' }).getByRole('button', { name: 'W2 browser task', exact: true })).toBeVisible();
  await mentor.page.screenshot({ path: '../target/week2-browser-mentor.png', fullPage: true }); await mentor.context.close();

  const intern = await actor(browser, 'intern@example.test');
  await intern.page.getByRole('button', { name: 'Công việc của tôi', exact: true }).click();
  const card = intern.page.locator('article').filter({ has: intern.page.getByRole('button', { name: 'W2 browser task', exact: true }) });
  await card.getByRole('button', { name: 'Bắt đầu làm', exact: true }).click();
  await expect(card.getByRole('button', { name: 'Bắt đầu làm', exact: true })).toHaveCount(0);
  await expect(intern.page.getByRole('heading', { name: 'Lịch công việc', exact: true })).toBeVisible();
  await expect(intern.page.getByRole('grid', { name: 'Lịch hạn công việc' }).getByRole('button', { name: 'W2 browser task: Đang làm', exact: true })).toBeVisible();
  await intern.page.screenshot({ path: '../target/week2-browser-student-tasks.png', fullPage: true });
  await intern.context.close();

  // Prepare two real offers through T5/T4 owner APIs, then exercise both UI decisions.
  const author = await actor(browser, 'hr@example.test'), reviewer = await actor(browser, 'school@example.test');
  const authorCsrf = await (await author.page.request.get('/api/v1/auth/csrf')).json();
  const reviewerCsrf = await (await reviewer.page.request.get('/api/v1/auth/csrf')).json();
  const organization = '00000000-0000-4000-8000-000000000012';
  const post = async (page: typeof author.page, csrf: { headerName: string; token: string }, path: string, data: unknown, method = 'POST') => {
    const scope = page === reviewer.page ? '00000000-0000-4000-8000-000000000011' : organization;
    const response = await page.request.fetch('/api/v1' + path, { method, data, headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': randomUUID(), 'X-Organization-Id': scope } });
    const result = await response.json();
    expect(response.ok(), path + ': ' + response.status() + ' ' + (result.errorCode || '')).toBe(true); return result;
  };
  const demands = await (await author.page.request.get('/api/v1/enterprise/demands?size=25', { headers: { 'X-Organization-Id': organization } })).json();
  for (const title of ['W2 UI accept invitation', 'W2 UI decline invitation']) {
    const demand = await post(author.page, authorCsrf, '/enterprise/demands', { ...demands.content[0].data, title });
    const submitted = await post(author.page, authorCsrf, '/enterprise/demands/submit-with-cooperation', { demandId: demand.demandId, expectedDemandVersion: demand.version });
    const approved = await post(reviewer.page, reviewerCsrf, '/school/cooperations/' + submitted.cooperation.cooperationId + '/decision', { expectedVersion: submitted.cooperation.version, decision: 'APPROVE', reasonCode: null, decisionRef: 'DEMO-BROWSER-INVITATION', missingFields: [] }, 'PATCH');
    const evaluated = await post(reviewer.page, reviewerCsrf, '/matching/evaluate', { demandId: demand.demandId, expectedDemandVersion: submitted.demand.version, expectedCriteriaVersion: submitted.demand.criteriaVersion });
    const nominated = await post(reviewer.page, reviewerCsrf, '/matching/runs/' + evaluated.summary.run.matchRunId + '/nominate', { studentUserIds: [studentIdentity.userId], expectedDemandVersion: submitted.demand.version, expectedCriteriaVersion: submitted.demand.criteriaVersion, expectedCooperationVersion: approved.version, expectedBatchVersion: null });
    await post(reviewer.page, reviewerCsrf, '/school/proposals/' + nominated.items[0].proposalItemId + '/invite', { expectedVersion: 1, expiresAt: null });
  }
  await author.context.close(); await reviewer.context.close();
  const decisions = await actor(browser, 'student@example.test');
  await decisions.page.getByRole('button', { name: 'Lời mời & nguyện vọng', exact: true }).click();
  const accepted = decisions.page.locator('section.panel').filter({ has: decisions.page.getByRole('heading', { name: 'W2 UI accept invitation', exact: true }) });
  const declined = decisions.page.locator('section.panel').filter({ has: decisions.page.getByRole('heading', { name: 'W2 UI decline invitation', exact: true }) });
  await accepted.getByRole('button', { name: 'Nhận lời mời', exact: true }).click();
  await expect(accepted.getByRole('button', { name: 'Rút nhận lời', exact: true })).toBeVisible();
  decisions.page.once('dialog', dialog => void dialog.accept());
  await declined.getByRole('button', { name: 'Từ chối', exact: true }).click();
  await expect(declined.getByText(/ · Đã từ chối$/)).toBeVisible();
  await decisions.page.reload(); await decisions.page.getByRole('button', { name: 'Lời mời & nguyện vọng', exact: true }).click();
  await expect(accepted.getByRole('button', { name: 'Rút nhận lời', exact: true })).toBeVisible();
  await expect(declined.getByText(/ · Đã từ chối$/)).toBeVisible();
  await expect(decisions.page.getByText('Đang có hiệu lực', { exact: true })).toHaveCount(0);
  await decisions.context.close();
});
