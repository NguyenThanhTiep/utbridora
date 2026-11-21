import { test, expect, type Browser, type Page } from '@playwright/test';

const cfg:Record<string,string>|null=process.env.E2E_T312_CONFIG?JSON.parse(process.env.E2E_T312_CONFIG):null;
test.use({actionTimeout:15000});
test.beforeEach(()=>test.skip(!cfg,'Requires private T312EvaluationBrowserIT server'));

async function actor(browser:Browser,role:string) {
  const context=await browser.newContext(),page=await context.newPage();await page.goto('/');
  await page.getByLabel('Email',{exact:true}).fill(cfg![role+'Email']);
  await page.getByLabel('Mật khẩu',{exact:true}).fill(cfg!.password);
  await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  await page.getByRole('button',{name:'Đánh giá thực tập',exact:true}).click();
  if(role==='mentor'||role==='hr')await page.getByRole('combobox',{name:'Phân công đánh giá',exact:true}).selectOption(cfg![role+'Context']);
  if(role==='mentor')await page.getByRole('combobox',{name:'Hồ sơ trong nhóm',exact:true}).selectOption(cfg!.caseId);
  if(role==='school')await page.getByRole('combobox',{name:'Kỳ đánh giá',exact:true}).selectOption(cfg!.termId);
  if(role==='student'||role==='outsider') {
    await page.getByLabel('Mã hồ sơ thực tập',{exact:true}).fill(cfg!.caseId);
    await page.getByRole('button',{name:'Mở đánh giá hồ sơ',exact:true}).click();
  }
  return {context,page};
}

async function policy(page:Page,title:string) {
  const panel=page.getByRole('region',{name:title,exact:true});
  await expect(panel).toBeVisible();
  await panel.getByRole('checkbox').setChecked(true);
  const saved=page.waitForResponse(r=>r.request().method()==='PUT'&&r.url().includes('visibility'));
  await panel.getByRole('button',{name:'Lưu quyền xem điểm'}).click();
  expect((await saved).ok()).toBe(true);
}

async function studentResult(page:Page,total?:string) {
  const response=page.waitForResponse(r=>r.url().includes('/t3/me/results?'));
  await page.getByRole('button',{name:'Tải lại kết quả',exact:true}).click();
  const result=await response,body=await result.json();
  if(total) {
    expect(body.visibility).toBe('VISIBLE');
    expect(body.evaluation.state).toBe('PUBLISHED');
    await expect(page.getByRole('heading',{name:'Điểm tổng: '+total+' / 100',exact:true})).toBeVisible();
  } else {
    expect(body).toEqual({caseId:cfg!.caseId,visibility:'HIDDEN',evaluation:null,reasonCode:'NOT_AVAILABLE'});
    await expect(page.getByRole('region',{name:'Nội dung đánh giá'})).toHaveCount(0);
  }
}

async function schoolRow(page:Page,state:string) {
  await page.getByRole('button',{name:'Tải danh sách đánh giá',exact:true}).click();
  await page.getByRole('button',{name:new RegExp('Hồ sơ .*'+state+' · Version')}).click();
}

async function publish(page:Page) {
  await schoolRow(page,'SUBMITTED');
  await page.getByLabel('Căn cứ quyết định').fill('QA03-PUBLISH');
  await page.getByRole('button',{name:'Công bố điểm',exact:true}).click();
  await expect(page.getByRole('button',{name:/Hồ sơ .*PUBLISHED · Version/})).toBeVisible();
}

test('QA03: rubric total, publish eligibility, revision value and immutable evaluation history',async({browser})=>{
  test.setTimeout(180000);
  const mentor=await actor(browser,'mentor'),school=await actor(browser,'school'),hr=await actor(browser,'hr');
  const student=await actor(browser,'student'),outsider=await actor(browser,'outsider');
  const studentT3Paths:string[]=[];
  student.page.on('request',request=>{
    if(request.url().includes('/api/v1/t3/'))studentT3Paths.push(new URL(request.url()).pathname);
  });
  try {
    await mentor.page.getByLabel('Rubric áp dụng').selectOption(cfg!.templateId+':1');
    await mentor.page.getByLabel('Điểm Quality (0–5)',{exact:true}).fill('5');
    await mentor.page.getByLabel('Điểm Process (0–5)',{exact:true}).fill('3');
    await expect(mentor.page.getByText('Điểm dự kiến: 76.00',{exact:true})).toBeVisible();
    await mentor.page.getByRole('button',{name:'Tạo bản nháp đánh giá',exact:true}).click();
    await expect(mentor.page.getByRole('heading',{name:'Điểm tổng: 76.00 / 100',exact:true})).toBeVisible();

    await policy(hr.page,'Quyền xem điểm doanh nghiệp/kỳ');
    await policy(school.page,'Chính sách nhà trường');
    await studentResult(student.page);
    await mentor.page.getByRole('button',{name:'Nộp đánh giá',exact:true}).click();
    await expect(mentor.page.getByText(/Trạng thái: SUBMITTED/)).toBeVisible();
    await studentResult(student.page);

    await publish(school.page);
    await studentResult(student.page,'76.00');
    await schoolRow(school.page,'PUBLISHED');
    await school.page.getByLabel('Căn cứ quyết định').fill('QA03-REVISION');
    await school.page.getByLabel('Mã lý do tạo revision').fill('QA03_CORRECTION');
    await school.page.getByRole('button',{name:'Tạo revision đánh giá',exact:true}).click();
    await expect(school.page.getByRole('button',{name:/Hồ sơ .*DRAFT · Version/})).toBeVisible();

    await mentor.page.getByRole('button',{name:'Tải lại đánh giá',exact:true}).click();
    await mentor.page.getByLabel('Điểm Quality (0–5)',{exact:true}).fill('1');
    await mentor.page.getByLabel('Điểm Process (0–5)',{exact:true}).fill('1');
    await mentor.page.getByRole('button',{name:'Lưu đánh giá',exact:true}).click();
    await expect(mentor.page.getByRole('heading',{name:'Điểm tổng: 20.00 / 100',exact:true})).toBeVisible();
    await studentResult(student.page,'76.00');
    await mentor.page.getByRole('button',{name:'Nộp đánh giá',exact:true}).click();
    await expect(mentor.page.getByText(/Trạng thái: SUBMITTED/)).toBeVisible();
    await studentResult(student.page,'76.00');

    await publish(school.page);
    await studentResult(student.page,'20.00');
    await mentor.page.getByLabel('Lịch sử đánh giá').selectOption('1');
    await expect(mentor.page.getByRole('heading',{name:'Điểm tổng: 76.00 / 100',exact:true})).toBeVisible();
    await expect(mentor.page.getByRole('button',{name:'Lưu đánh giá',exact:true})).toHaveCount(0);
    await expect(mentor.page.getByRole('button',{name:'Nộp đánh giá',exact:true})).toHaveCount(0);

    expect(studentT3Paths.some(path=>
      path.includes('/evaluations')||path.includes('/rubric-templates')||path.includes('visibility'))).toBe(false);
    const denied=await outsider.page.request.get('/api/v1/t3/me/results?caseId='+cfg!.caseId);
    expect(denied.status()).toBe(404);
    expect(await denied.text()).not.toContain('total100');
    await student.page.screenshot({path:'../target/t3-12-qa03-student-visible.png',fullPage:true});
    await mentor.page.screenshot({path:'../target/t3-12-qa03-history-v1-readonly.png',fullPage:true});
  } finally {
    for(const account of [mentor,school,hr,student,outsider])await account.context.close();
  }
});
