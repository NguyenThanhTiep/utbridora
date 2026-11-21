import { openWorkspacePage, signOutFromAccountMenu } from './helpers/workspace';
import { test, expect, type Browser, type Page } from '@playwright/test';
// No route.fulfill: Spring sessions/CSRF, T3 services and MySQL are provided by T312EvaluationBrowserIT.
const cfg:Record<string,string>|null=process.env.E2E_T312_CONFIG?JSON.parse(process.env.E2E_T312_CONFIG):null;
test.use({actionTimeout:15000});
test.describe.configure({mode:'serial'}); // A single persisted evaluation is followed through revisions.
test.beforeEach(()=>test.skip(!cfg,'Requires private T312EvaluationBrowserIT server'));
async function actor(browser:Browser,role:string) {
  const context=await browser.newContext(),page=await context.newPage();await page.goto('/');
  await page.getByLabel('Email',{exact:true}).fill(cfg![role+'Email']);await page.getByLabel('Mật khẩu',{exact:true}).fill(cfg!.password);
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await openWorkspacePage(page, 'Đánh giá thực tập');
  if(role==='mentor'||role==='hr')await page.getByRole('combobox',{name:'Phân công đánh giá',exact:true}).selectOption(cfg![role+'Context']);
  if(role==='mentor')await page.getByRole('combobox',{name:'Hồ sơ trong nhóm',exact:true}).selectOption(cfg!.caseId);
  if(role==='student'||role==='outsider')await selectCase(page);
  if(role==='school'){
    const terms=page.getByRole('combobox',{name:'Kỳ đánh giá',exact:true});
    await expect(terms.locator('option[value="'+cfg!.termId+'"]')).toHaveText(/^SYNTHETIC-/);
    await terms.selectOption(cfg!.termId);
  }
  return {context,page};
}
async function selectCase(page:Page) {await page.getByLabel('Mã hồ sơ thực tập',{exact:true}).fill(cfg!.caseId);await page.getByRole('button',{name:'Mở đánh giá hồ sơ',exact:true}).click();}
async function policy(page:Page,title:string,enabled:boolean) {
  const panel=page.getByRole('region',{name:title,exact:true});await expect(panel).toBeVisible();
  await expect(panel.getByRole('checkbox'),await panel.innerText()).toBeVisible();await panel.getByRole('checkbox').setChecked(enabled);
  const saved=page.waitForResponse(r=>r.request().method()==='PUT' && r.url().includes('visibility'));
  await panel.getByRole('button',{name:'Lưu quyền xem điểm'}).click();expect((await saved).ok()).toBe(true);
  await expect(panel.getByRole('button',{name:'Lưu quyền xem điểm'})).toBeDisabled();
  await expect(panel.getByText('Trạng thái đã lưu: '+(enabled?'Bật':'Tắt'),{exact:false})).toBeVisible();
}
async function schoolRow(page:Page,state:string) {
  await page.getByRole('button',{name:'Tải danh sách đánh giá',exact:true}).click();
  await page.getByRole('button',{name:new RegExp('Hồ sơ .*'+state+' · Version')}).click();
}
async function publish(page:Page) {await schoolRow(page,'SUBMITTED');await page.getByLabel('Căn cứ quyết định').fill('T312-SCHOOL-DECISION');await page.getByRole('button',{name:'Công bố điểm',exact:true}).click();await expect(page.getByRole('button',{name:/Hồ sơ .*PUBLISHED · Version/})).toBeVisible();}
async function revision(page:Page) {await schoolRow(page,'PUBLISHED');await page.getByLabel('Căn cứ quyết định').fill('T312-REVISION');await page.getByLabel('Mã lý do tạo revision').fill('CORRECTION');await page.getByRole('button',{name:'Tạo revision đánh giá',exact:true}).click();await expect(page.getByRole('button',{name:/Hồ sơ .*DRAFT · Version/})).toBeVisible();}
async function result(page:Page,total?:string) {
  const response=page.waitForResponse(r=>r.url().includes('/t3/me/results?'));await page.getByRole('button',{name:'Tải lại kết quả',exact:true}).click();const r=await response,body=await r.json();
  if(total){expect(body.visibility).toBe('VISIBLE');expect(body.evaluation.state).toBe('PUBLISHED');await expect(page.getByRole('heading',{name:'Điểm tổng: '+total+' / 100',exact:true})).toBeVisible();}
  else {expect(body).toEqual({caseId:cfg!.caseId,visibility:'HIDDEN',evaluation:null,reasonCode:'NOT_AVAILABLE'});expect(await r.text()).not.toContain('total100');await expect(page.getByText('Kết quả chưa được phép hiển thị.',{exact:true})).toBeVisible();await expect(page.getByRole('region',{name:'Nội dung đánh giá'})).toHaveCount(0);}
}
test('T3-12 real four-role rubric submit, triple gate, revision, publication and CASE override',async({browser})=>{
  test.setTimeout(150000);const mentor=await actor(browser,'mentor'),school=await actor(browser,'school'),hr=await actor(browser,'hr'),student=await actor(browser,'student');
  const studentPaths:string[]=[];student.page.on('request',r=>{if(r.url().includes('/api/v1/t3/'))studentPaths.push(new URL(r.url()).pathname);});
  try {
    await mentor.page.getByLabel('Rubric áp dụng').selectOption(cfg!.templateId+':1');
    await mentor.page.getByLabel('Điểm Quality (0–5)',{exact:true}).fill('5');await mentor.page.getByLabel('Điểm Process (0–5)',{exact:true}).fill('3');
    await mentor.page.getByLabel('Nhận xét Quality',{exact:true}).fill('Private mentor feedback');
    await expect(mentor.page.getByText('Điểm dự kiến: 76.00',{exact:true})).toBeVisible();await mentor.page.getByRole('button',{name:'Tạo bản nháp đánh giá',exact:true}).click();
    await expect(mentor.page.getByRole('heading',{name:'Điểm tổng: 76.00 / 100',exact:true})).toBeVisible();
    await policy(hr.page,'Quyền xem điểm doanh nghiệp/kỳ',true);await policy(school.page,'Chính sách nhà trường',true);await result(student.page);
    await mentor.page.getByRole('button',{name:'Nộp đánh giá',exact:true}).click();await expect(mentor.page.getByText(/Trạng thái: SUBMITTED/)).toBeVisible();await result(student.page);
    await publish(school.page);await result(student.page,'76.00');
    await student.page.reload();await openWorkspacePage(student.page, 'Đánh giá thực tập');await selectCase(student.page);await result(student.page,'76.00');
    await student.page.screenshot({path:'../target/t3-12-student-visible.png',fullPage:true});
    await revision(school.page);await mentor.page.getByRole('button',{name:'Tải lại đánh giá',exact:true}).click();
    await mentor.page.getByLabel('Điểm Quality (0–5)',{exact:true}).fill('1');await mentor.page.getByLabel('Điểm Process (0–5)',{exact:true}).fill('1');
    await mentor.page.getByRole('button',{name:'Lưu đánh giá',exact:true}).click();await expect(mentor.page.getByRole('heading',{name:'Điểm tổng: 20.00 / 100',exact:true})).toBeVisible();await result(student.page,'76.00');
    await mentor.page.getByRole('button',{name:'Nộp đánh giá',exact:true}).click();await expect(mentor.page.getByText(/Trạng thái: SUBMITTED/)).toBeVisible();await result(student.page,'76.00');
    await publish(school.page);await result(student.page,'20.00');
    await hr.page.getByLabel('Phạm vi chính sách').selectOption('CASE');await selectCase(hr.page);
    // Missing CASE policy reads false/version0; explicitly create false to override ORGANIZATION_TERM=true.
    await policy(hr.page,'Quyền xem điểm theo hồ sơ',false);await result(student.page);
    await student.page.screenshot({path:'../target/t3-12-student-hidden.png',fullPage:true});
    await policy(hr.page,'Quyền xem điểm theo hồ sơ',true);await result(student.page,'20.00');
    await policy(school.page,'Chính sách nhà trường',false);await result(student.page);await policy(school.page,'Chính sách nhà trường',true);await result(student.page,'20.00');
    await mentor.page.getByLabel('Lịch sử đánh giá').selectOption('1');await expect(mentor.page.getByRole('heading',{name:'Điểm tổng: 76.00 / 100',exact:true})).toBeVisible();
    await expect(mentor.page.getByRole('button',{name:'Lưu đánh giá',exact:true})).toHaveCount(0);
    expect(studentPaths.some(p=>p.includes('/evaluations') || p.includes('/rubric-templates') || p.includes('visibility'))).toBe(false);
  } finally {for(const a of [mentor,school,hr,student])await a.context.close();}
});
test('T3-12 real evidence PENDING draft cannot submit or leak into published student result',async({browser})=>{
  test.setTimeout(90000);const school=await actor(browser,'school');await revision(school.page);const mentor=await actor(browser,'mentor'),student=await actor(browser,'student');
  try {
    await mentor.page.getByLabel('Tệp minh chứng đánh giá').setInputFiles({name:'evidence.pdf',mimeType:'application/pdf',buffer:Buffer.from('%PDF-1.7\nT312 synthetic evidence\n%%EOF')});
    await mentor.page.getByRole('button',{name:'Tải tệp đánh giá',exact:true}).click();await expect(mentor.page.getByText(/Đang chờ quét/)).toBeVisible();
    await mentor.page.getByRole('button',{name:'Lưu đánh giá',exact:true}).click();await expect(mentor.page.getByRole('button',{name:'Lưu đánh giá',exact:true})).toBeDisabled();
    await mentor.page.getByRole('button',{name:'Nộp đánh giá',exact:true}).click();await expect(mentor.page.getByRole('alert')).toContainText('FILE_NOT_CLEAN');
    await result(student.page,'20.00');await expect(student.page.getByText('evidence.pdf',{exact:false})).toHaveCount(0);
    await mentor.page.screenshot({path:'../target/t3-12-mentor-evidence.png',fullPage:true});
  } finally {for(const a of [school,mentor,student])await a.context.close();}
});
test('T3-12 real outsider denied and logout clears private result',async({browser})=>{
  const outsider=await actor(browser,'outsider'),student=await actor(browser,'student');
  try {
    await expect(outsider.page.getByText('Quyền truy cập đã thay đổi. Dữ liệu đánh giá đã được đóng.')).toBeVisible();
    const r=await outsider.page.request.get('/api/v1/t3/me/results?caseId='+cfg!.caseId);expect(r.status()).toBe(404);expect(await r.text()).not.toContain('total100');
    await result(student.page,'20.00');await signOutFromAccountMenu(student.page);await expect(student.page.getByRole('heading',{name:'Đăng nhập',exact:true})).toBeVisible();
    await expect(student.page.getByRole('region',{name:'Nội dung đánh giá'})).toHaveCount(0);
  } finally {await outsider.context.close();await student.context.close();}
});

test('week 5 S08 opens an owner case without tasks and clears a withdrawn result on focus',async({browser})=>{
  const student=await actor(browser,'student'),school=await actor(browser,'school'),outsider=await actor(browser,'outsider');
  try {
    const tasks=await student.page.request.get('/api/v1/t3/tasks?page=0&size=100');
    expect(tasks.ok()).toBe(true);expect((await tasks.json()).content).toEqual([]);
    for(const a of [student,outsider]) {
      await a.page.getByRole('button',{name:'Báo cáo & đánh giá',exact:true}).click();
      await a.page.getByRole('tab',{name:'Kết quả đánh giá',exact:true}).click();
      await a.page.getByText('Mở hồ sơ bằng mã được cấp',{exact:true}).click();
      await a.page.getByLabel('Mã hồ sơ để xem kết quả',{exact:true}).fill(cfg!.caseId);
      await a.page.getByRole('button',{name:'Xác nhận hồ sơ kết quả',exact:true}).click();
    }
    await expect(outsider.page.getByText('Không thể mở hồ sơ này. Kiểm tra mã hồ sơ và quyền truy cập.',{exact:true})).toBeVisible();
    await expect(outsider.page.getByRole('table')).toHaveCount(0);
    await expect(student.page.getByRole('table',{name:'Điểm theo tiêu chí'})).toBeVisible();
    await expect(student.page.getByText('20,00',{exact:true})).toBeVisible();
    await policy(school.page,'Chính sách nhà trường',false);
    const checked=student.page.waitForResponse(r=>r.url().includes('/t3/me/results?'));
    await student.page.evaluate(()=>window.dispatchEvent(new Event('focus')));expect((await checked).ok()).toBe(true);
    await expect(student.page.getByRole('heading',{name:'Chưa có kết quả được công bố',exact:true})).toBeVisible();
    await expect(student.page.getByRole('table')).toHaveCount(0);
    await policy(school.page,'Chính sách nhà trường',true);
    await student.page.getByRole('button',{name:'Tải lại kết quả',exact:true}).click();
    await expect(student.page.getByText('20,00',{exact:true})).toBeVisible();
  } finally {for(const a of [student,school,outsider])await a.context.close();}
});
