import { randomUUID } from 'node:crypto';
import { expect, type Page } from '@playwright/test';

export async function prepareQa05Demand(author: Page, reviewer: Page, student: Page) {
  const schoolOrg = '00000000-0000-4000-8000-000000000011';
  const enterpriseOrg = '00000000-0000-4000-8000-000000000012';
  const title = 'QA05 Week 5 full path';
  const original = await author.request.get('/api/v1/enterprise/demands?size=25', {
    headers: { 'X-Organization-Id': enterpriseOrg }
  });
  expect(original.status()).toBe(200);
  const template = (await original.json()).content.find((row: {data: {title: string}}) =>
    row.data.title === 'DEMO - Java Internship').data;
  await author.getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  await author.getByRole('button', { name: 'Tạo nhu cầu', exact: true }).click();
  await author.getByLabel('Trường nhận nhu cầu', { exact: true }).selectOption(schoolOrg);
  await author.getByLabel('Kỳ thực tập', { exact: true }).selectOption(template.termId);
  await author.getByLabel('Phiên bản danh mục học vụ').selectOption(template.academicCatalogVersion);
  await author.getByLabel('Tên nhu cầu').fill(title);
  await author.getByLabel('Số lượng vị trí').fill('1');
  await author.getByLabel('Hạn tuyển').fill(template.deadline.slice(0, 16));
  await author.getByLabel('Mô tả công việc').fill('Synthetic QA05 end-to-end internship demand');
  await author.getByLabel('Mã ngành chính thức (không bắt buộc)', { exact: true }).fill('SE');
  await author.getByLabel('Ngày bắt đầu thực tập').fill(template.required.dateRange.startDate);
  await author.getByLabel('Ngày kết thúc thực tập').fill(template.required.dateRange.endDate);
  await author.getByRole('button', { name: 'Tra cứu kỹ năng', exact: true }).click();
  await author.getByRole('button', { name: 'Chọn JAVA bắt buộc', exact: true }).click();
  const createdReply = author.waitForResponse(r => r.url().endsWith('/api/v1/enterprise/demands') && r.request().method() === 'POST');
  await author.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  const created = await createdReply;
  const draft = await created.json();
  expect(created.status(), JSON.stringify(draft)).toBe(201);
  expect(draft.state).toBe('DRAFT');
  expect(draft.data.title).toBe(title);

  await author.reload();
  await author.getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  const submittedReply = author.waitForResponse(r => r.url().endsWith('/enterprise/demands/submit-with-cooperation') && r.request().method() === 'POST');
  await author.getByRole('row').filter({ hasText: title }).getByRole('button', { name: /Gửi yêu cầu hợp tác/ }).click();
  const sent = await submittedReply;
  const submitted = await sent.json();
  expect(sent.ok(), JSON.stringify(submitted)).toBe(true);
  expect(submitted.cooperation.state).toBe('SUBMITTED');
  expect(submitted.demand.demandId).toBe(draft.demandId);
  const coop = submitted.cooperation.cooperationId;

  const forbidden = await student.request.get('/api/v1/school/cooperations/' + coop, {
    headers: { 'X-Organization-Id': schoolOrg }
  });
  expect([403, 404]).toContain(forbidden.status());
  const reviewQueue = await reviewer.request.get('/api/v1/school/cooperations', {
    headers: { 'X-Organization-Id': schoolOrg }
  });
  expect(reviewQueue.status()).toBe(200);
  const indexInQueue = (await reviewQueue.json()).content.findIndex((row: {cooperationId: string}) => row.cooperationId === coop);
  expect(indexInQueue).toBeGreaterThanOrEqual(0);
  await reviewer.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Duyệt hợp tác', exact: true }).click();
  await reviewer.getByRole('button', { name: 'Xem nội dung đề nghị ' + (indexInQueue + 1), exact: true }).click();
  await expect(reviewer.getByRole('heading', { name: title, exact: true })).toBeVisible();
  await reviewer.getByLabel('Căn cứ quyết định', { exact: true }).fill('QA05-W5-COOPERATION');
  const approvedReply = reviewer.waitForResponse(r => r.url().endsWith('/school/cooperations/' + coop + '/decision') && r.request().method() === 'PATCH');
  await reviewer.getByRole('button', { name: 'Lưu kết quả xét duyệt', exact: true }).click();
  const review = await approvedReply;
  const approved = await review.json();
  expect(review.ok(), JSON.stringify(approved)).toBe(true);
  expect(approved.state).toBe('APPROVED');

  const csrf = await (await reviewer.request.get('/api/v1/auth/csrf')).json();
  const schoolCommand = async (path: string, data: unknown) => {
    const response = await reviewer.request.post('/api/v1' + path, { data, headers: {
      [csrf.headerName]: csrf.token, 'Idempotency-Key': randomUUID(), 'X-Organization-Id': schoolOrg
    }});
    const body = await response.json();
    expect(response.ok(), path + ' ' + JSON.stringify(body)).toBe(true);
    return body;
  };
  const evaluated = await schoolCommand('/matching/evaluate', {
    demandId: draft.demandId, expectedDemandVersion: submitted.demand.version,
    expectedCriteriaVersion: submitted.demand.criteriaVersion
  });
  expect(evaluated.summary.run.state).toBe('COMPLETED');
  const studentUser = await (await student.request.get('/api/v1/auth/me')).json();
  const nominated = await schoolCommand('/matching/runs/' + evaluated.summary.run.matchRunId + '/nominate', {
    studentUserIds: [studentUser.userId], expectedDemandVersion: submitted.demand.version,
    expectedCriteriaVersion: submitted.demand.criteriaVersion, expectedCooperationVersion: approved.version,
    expectedBatchVersion: null
  });
  const proposal = nominated.items[0].proposalItemId;
  expect(proposal).toBeTruthy();
  return { coop, proposal };
}
