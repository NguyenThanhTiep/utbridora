import { test, expect, type Browser, type Page } from '@playwright/test';

// Real Spring/CSRF/JDBC/MySQL and private T1 storage from T309WorkflowBrowserIT.
// No route interception, fake HTTP responses or shared developer database.
type Fixture = { password: string; studentEmail: string; mentorEmail: string; hrEmail: string; outsiderEmail: string;
  mentorContext: string; hrContext: string; cohortId: string; groupId: string; caseId: string; assignmentId: string; taskId: string };
const cfg: Fixture | null = process.env.E2E_T309_CONFIG ? JSON.parse(process.env.E2E_T309_CONFIG) as Fixture : null;
test.beforeEach(() => { test.skip(!cfg, 'Requires isolated T309WorkflowBrowserIT server'); });
async function actor(browser: Browser, role: 'student' | 'mentor' | 'hr' | 'outsider') {
  const context = await browser.newContext(), page = await context.newPage();
  await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(cfg![role + 'Email' as keyof Fixture]);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(cfg!.password); await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.locator('.workspace')).toBeVisible();
  await board(page, role); return { context, page };
}
async function board(page: Page, role: 'student' | 'mentor' | 'hr' | 'outsider') {
  if (role === 'student' || role === 'outsider') await page.getByRole('button', { name: 'Nhiệm vụ & sản phẩm', exact: true }).click();
  else {
    await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
    await page.getByLabel('Phân công đang làm việc').selectOption(cfg![role === 'mentor' ? 'mentorContext' : 'hrContext']);
    await page.getByRole('button', { name: role === 'mentor' ? 'Nhóm được giao' : 'Nhóm thực tập', exact: true }).click();
    await page.getByLabel('Đợt thực tập', { exact: true }).selectOption(cfg!.cohortId);
    await page.getByRole('button', { name: 'Xem nhóm G1', exact: true }).click();
    await page.getByRole('button',{name:'Bảng Kanban',exact:true}).click();
  }
}
async function detail(page: Page) {
  // Mentor/HR calendar entries have a task title without an assignment; open the actual board card.
  await page.locator('article').getByRole('button', { name: 'Synthetic T309', exact: true }).click();
}
// Sinh viên mở báo cáo tuần ở S08 "Báo cáo & đánh giá" (T2-10); Mentor mở trong bảng việc của nhóm.
async function reports(page: Page, role: 'student' | 'mentor') {
  if (role === 'student') await page.getByRole('button', { name: 'Báo cáo & đánh giá', exact: true }).click();
  else {await page.getByRole('button',{name:'Báo cáo tuần',exact:true}).click();await page.getByRole('button', { name: 'Mở báo cáo tuần', exact: true }).click();}
  await page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).selectOption(cfg!.caseId);
}
async function createReport(page: Page, week: string, work: string) {
  const form = page.getByRole('group', { name: 'Tạo báo cáo tuần', exact: true });
  await form.getByLabel('Thứ Hai bắt đầu tuần').fill(week);
  await form.getByRole('textbox', { name: 'Việc đã làm', exact: true }).fill(work);
  await form.getByRole('textbox', { name: 'Kế hoạch tiếp theo', exact: true }).fill('Browser next plan');
  await form.getByRole('button', { name: 'Tạo bản nháp báo cáo', exact: true }).click();
  const editor = page.getByRole('region', { name: 'Chi tiết báo cáo' });
  await expect(editor.getByRole('textbox', { name: 'Việc đã làm', exact: true })).toHaveValue(work); return editor;
}

test('DEV07 real backend: create task resets form and Case-filtered board survives reload',async({browser})=>{
  const mentor=await actor(browser,'mentor');
  try {
    await mentor.page.getByRole('button',{name:'Giao nhiệm vụ mới',exact:true}).click();
    await mentor.page.getByLabel('Tiêu đề',{exact:true}).fill('DEV07 created via UI');
    await mentor.page.getByLabel('Mô tả',{exact:true}).fill('Synthetic isolated UI creation');
    await mentor.page.getByLabel('Hạn hoàn thành',{exact:true}).fill(new Date(Date.now()+86400000).toISOString().slice(0,16));
    await mentor.page.getByRole('checkbox').check();
    const saved=mentor.page.waitForResponse(r=>r.url().endsWith('/t3/tasks')&&r.request().method()==='POST');
    await mentor.page.getByRole('button',{name:'Giao nhiệm vụ',exact:true}).click();
    expect((await saved).ok()).toBe(true);
    await expect(mentor.page.getByLabel('Tiêu đề',{exact:true})).toHaveValue('');
    await mentor.page.reload();await board(mentor.page,'mentor');
    await mentor.page.getByRole('combobox',{name:'Hồ sơ được giao',exact:true}).selectOption(cfg!.caseId);
    await expect(mentor.page.locator('article').getByRole('button',{name:'DEV07 created via UI',exact:true})).toBeVisible();
    await mentor.page.getByRole('button',{name:'Lịch công việc',exact:true}).click();
    const calendar=mentor.page.waitForResponse(r=>r.url().includes('/t3/calendar?')&&new URL(r.url()).searchParams.get('caseId')===cfg!.caseId);
    await mentor.page.getByRole('combobox',{name:'Hồ sơ trên lịch',exact:true}).selectOption(cfg!.caseId);
    expect((await calendar).ok()).toBe(true);
  } finally {await mentor.context.close();}
});

test('T3-09 real backend: student URL v1, Mentor revision, URL v2, ACCEPT, comments and reload', async ({ browser }) => {
  test.setTimeout(90_000);
  const student = await actor(browser, 'student'), mentor = await actor(browser, 'mentor');
  try {
    for(const width of [1440,768,375]) {
      await mentor.page.setViewportSize({width,height:960});
      await expect(mentor.page.locator('article').getByRole('button',{name:'Synthetic T309',exact:true})).toBeVisible();
      expect(await mentor.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
      const menu=mentor.page.getByRole('button',{name:'Mở menu điều hướng',exact:true});
      if(await menu.isVisible()) {
        await menu.click();await expect(menu).toHaveAttribute('aria-expanded','true');
        await mentor.page.keyboard.press('Escape');await expect(menu).toBeFocused();await expect(menu).toHaveAttribute('aria-expanded','false');
      }
      await mentor.page.evaluate(async()=>{document.documentElement.style.scrollBehavior='auto';window.scrollTo(0,0);await new Promise<void>(resolve=>requestAnimationFrame(()=>requestAnimationFrame(()=>resolve())));});
      if(width===1440)expect((await mentor.page.locator('#main-navigation').boundingBox())!.y).toBe(0);
      await mentor.page.screenshot({path:`../target/t3-uiux-shell-${width}.png`,fullPage:width!==1440,animations:'disabled'});
    }
    await mentor.page.setViewportSize({width:1440,height:960});
    await detail(student.page); await student.page.getByLabel('Đường dẫn bài nộp HTTPS').fill('https://example.test/t309/v1');
    await student.page.getByLabel('Ghi chú bài nộp').fill('Immutable browser v1'); await student.page.getByRole('button', { name: 'Gửi bài nộp', exact: true }).click();
    await expect(student.page.getByRole('link', { name: 'Mở URL bản v1' })).toBeVisible();
    await student.page.getByLabel('Nội dung trao đổi').fill('Student task comment'); await student.page.getByRole('button', { name: 'Gửi trao đổi', exact: true }).click();
    await expect(student.page.getByText('Student task comment', { exact: true })).toBeVisible();
    await detail(mentor.page); await mentor.page.getByRole('textbox', { name: 'Nhận xét bài nộp', exact: true }).fill('Please add browser tests');
    await mentor.page.getByRole('button', { name: 'Gửi nhận xét bài nộp', exact: true }).click();
    await expect(mentor.page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Please add browser tests');
    await mentor.page.getByLabel('Nội dung trao đổi').fill('Mentor task comment'); await mentor.page.getByRole('button', { name: 'Gửi trao đổi', exact: true }).click();
    await expect(mentor.page.getByText('Mentor task comment', { exact: true })).toBeVisible();
    await student.page.reload(); await board(student.page, 'student'); await detail(student.page);
    await expect(student.page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Please add browser tests');
    await expect(student.page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Yêu cầu sửa');
    await student.page.getByRole('button', { name: 'Bắt đầu bản làm tiếp theo' }).click();
    await student.page.getByLabel('Đường dẫn bài nộp HTTPS').fill('https://example.test/t309/v2');
    await student.page.getByRole('button', { name: 'Gửi bài nộp', exact: true }).click();
    await expect(student.page.getByRole('link', { name: 'Mở URL bản v2' })).toBeVisible();
    await expect(student.page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Bản nộp này chưa có nhận xét.');
    await expect(student.page.getByText('Please add browser tests', { exact: true })).toHaveCount(0);
    await mentor.page.getByRole('button', { name: 'Tải phiên bản hiện tại', exact: true }).click();
    await expect(mentor.page.getByRole('heading', { name: 'Bản nộp v2 · Hiện tại', exact: true })).toBeVisible();
    await mentor.page.getByRole('combobox', { name: 'Kết quả review', exact: true }).selectOption('ACCEPT');
    await mentor.page.getByRole('textbox', { name: 'Nhận xét bài nộp', exact: true }).fill('Accepted browser v2'); await mentor.page.getByRole('button', { name: 'Gửi nhận xét bài nộp', exact: true }).click();
    await expect(mentor.page.getByText(/Trạng thái: COMPLETED/)).toBeVisible();
    await student.page.reload(); await board(student.page, 'student'); await detail(student.page);
    await expect(student.page.getByText(/Trạng thái: COMPLETED/)).toBeVisible();
    await expect(student.page.getByRole('link', { name: 'Mở URL bản v1' })).toHaveAttribute('href', 'https://example.test/t309/v1');
    await expect(student.page.getByRole('link', { name: 'Mở URL bản v2' })).toHaveAttribute('href', 'https://example.test/t309/v2');
    await expect(student.page.getByText('Immutable browser v1', { exact: true })).toBeVisible();
    await expect(student.page.getByText('Mentor task comment', { exact: true })).toBeVisible();
    await expect(student.page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Accepted browser v2');
    await expect(student.page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Chấp nhận');
    await student.page.getByRole('button', { name: 'Xem nhận xét bản v1', exact: true }).click();
    await expect(student.page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Please add browser tests');
    await expect(student.page.getByText('Accepted browser v2', { exact: true })).toHaveCount(0);
    await student.page.screenshot({ path: '../target/t3-09-real-submission.png', fullPage: true });
  } finally { await student.context.close(); await mentor.context.close(); }
});

test('T3-09 real backend: report review survives reload and historical revision remains immutable', async ({ browser }) => {
  test.setTimeout(90_000); const student = await actor(browser, 'student'), mentor = await actor(browser, 'mentor');
  try {
    await reports(student.page, 'student'); const editor = await createReport(student.page, '2026-11-09', 'Browser report v1');
    await editor.getByRole('button', { name: 'Nộp báo cáo tuần', exact: true }).click(); await expect(editor).toContainText('SUBMITTED');
    await reports(mentor.page, 'mentor'); await mentor.page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · SUBMITTED', exact: true }).click();
    await mentor.page.getByRole('textbox', { name: 'Nhận xét báo cáo', exact: true }).fill('Mentor report feedback');
    await mentor.page.getByRole('button', { name: 'Gửi nhận xét báo cáo', exact: true }).click();
    await expect(mentor.page.getByRole('region', { name: 'Nhận xét báo cáo đã lưu' })).toContainText('Mentor report feedback');
    await student.page.reload(); await board(student.page, 'student'); await reports(student.page, 'student');
    await student.page.getByRole('button', { name: 'Tuần 2026-11-09 · Bản 1 · REVIEWED', exact: true }).click();
    await expect(student.page.getByRole('region', { name: 'Nhận xét báo cáo đã lưu' })).toContainText('Mentor report feedback');
    await editor.getByLabel('Mã lý do sửa báo cáo').fill('UPDATE_PROGRESS'); await editor.getByRole('button', { name: 'Tạo bản sửa báo cáo' }).click();
    await editor.getByRole('textbox', { name: 'Việc đã làm', exact: true }).fill('Browser report v2'); await editor.getByRole('button', { name: 'Lưu bản nháp' }).click();
    await expect(editor.getByRole('button', { name: 'Lưu bản nháp' })).toBeDisabled();
    await editor.getByRole('combobox', { name: 'Phiên bản báo cáo', exact: true }).selectOption('1');
    await expect(editor.getByText('Browser report v1', { exact: true })).toBeVisible();
    await expect(editor.getByRole('region', { name: 'Nhận xét báo cáo đã lưu' })).toContainText('Mentor report feedback');
    await expect(editor.getByRole('button', { name: 'Lưu bản nháp' })).toHaveCount(0);
    await student.page.screenshot({ path: '../target/t3-09-real-report-history.png', fullPage: true });
  } finally { await student.context.close(); await mentor.context.close(); }
});

test('T3-09 real backend: multipart attachment stays PENDING, submit is denied and draft survives reload', async ({ browser }) => {
  test.setTimeout(60_000); const student = await actor(browser, 'student');
  try {
    await reports(student.page, 'student'); const editor = await createReport(student.page, '2026-11-16', 'Browser pending evidence');
    await editor.getByLabel('Chọn tệp minh chứng').setInputFiles({ name: 't309-evidence.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\nSynthetic browser evidence\n%%EOF') });
    await editor.getByRole('button', { name: 'Tải lên minh chứng', exact: true }).click();
    await expect(editor.getByRole('region', { name: 'Minh chứng báo cáo' })).toContainText('Đang chờ quét');
    await editor.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click(); await expect(editor.getByRole('button', { name: 'Nộp báo cáo tuần', exact: true })).toBeEnabled();
    await editor.getByRole('button', { name: 'Nộp báo cáo tuần', exact: true }).click();
    await expect(editor.getByRole('alert')).toContainText('FILE_NOT_CLEAN');
    await expect(editor.getByRole('button', { name: /Tải minh chứng/ })).toBeDisabled();
    await student.page.reload(); await board(student.page, 'student'); await reports(student.page, 'student');
    await student.page.getByRole('button', { name: 'Tuần 2026-11-16 · Bản 1 · DRAFT', exact: true }).click();
    await expect(editor.getByRole('region', { name: 'Minh chứng báo cáo' })).toContainText('Đang chờ quét');
    await expect(editor.getByRole('textbox', { name: 'Việc đã làm', exact: true })).toHaveValue('Browser pending evidence');
    await student.page.screenshot({ path: '../target/t3-09-real-pending-evidence.png', fullPage: true });
  } finally { await student.context.close(); }
});

test('T3-09 real backend: outsider reads denied and HR cannot mutate review UI', async ({ browser }) => {
  const outsider = await actor(browser, 'outsider'), hr = await actor(browser, 'hr');
  try {
    await expect(outsider.page.getByRole('button', { name: 'Synthetic T309', exact: true })).toHaveCount(0);
    const response = await outsider.page.request.get('/api/v1/t3/assignments/' + cfg!.assignmentId + '/submissions'); expect(response.status()).toBe(404);
    await detail(hr.page); await expect(hr.page.getByRole('link', { name: 'Mở URL bản v1' })).toBeVisible();
    await expect(hr.page.getByRole('region', { name: 'Nhận xét bài nộp đã lưu' })).toContainText('Accepted browser v2');
    const history = await hr.page.request.get('/api/v1/t3/assignments/' + cfg!.assignmentId + '/submissions', { headers: { 'X-Context-Assignment-Id': cfg!.hrContext } });
    const latest = (await history.json()).content[0];
    const hiddenReview = await outsider.page.request.get('/api/v1/t3/submissions/' + latest.submissionId + '/reviews'); expect(hiddenReview.status()).toBe(404);
    await expect(hr.page.getByRole('region', { name: 'Bài nộp và trao đổi' }).getByRole('button', { name: /^Gửi/ })).toHaveCount(0);
    await hr.page.getByRole('button', { name: 'Đăng xuất', exact: true }).click(); await expect(hr.page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
    await expect(hr.page.getByRole('link', { name: 'Mở URL bản v1' })).toHaveCount(0);
  } finally { await outsider.context.close(); await hr.context.close(); }
});
