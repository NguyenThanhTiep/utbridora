import { test, expect } from '@playwright/test';
import { openWorkspacePage } from './helpers/workspace';
test('school reads submitted cooperation history and Admin filters Matching audit statistics on real APIs',async({browser})=>{
  test.skip(!process.env.E2E_COOPERATION_REPORTING,'Run through CooperationReportingIT with isolated MySQL');test.setTimeout(180000);
  const cfg=JSON.parse(process.env.E2E_COOPERATION_REPORTING!) as Record<string,string>;
  const actor=async(email:string)=>{const context=await browser.newContext({timezoneId:'UTC',reducedMotion:'reduce'}),page=await context.newPage();await page.goto('/');await page.getByLabel('Email',{exact:true}).fill(email);await page.getByLabel('Mật khẩu',{exact:true}).fill(cfg.password);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();await expect(page.locator('.workspace')).toBeVisible();return{context,page};};
  const school=await actor(cfg.staffEmail);await openWorkspacePage(school.page,'Duyệt hợp tác');await school.page.getByRole('button',{name:'Xem nội dung đề nghị 1',exact:true}).click();
  const history=school.page.getByRole('region',{name:'Lịch sử các lần gửi hợp tác',exact:true});await history.getByRole('button',{name:'Tải lịch sử các lần gửi',exact:true}).click();await expect(history.getByRole('cell',{name:/^Lần gửi 26/})).toBeVisible();
  await history.getByRole('button',{name:'Lịch sử sau',exact:true}).click();await expect(history.getByRole('cell',{name:/^HISTORIC-1/})).toBeVisible();await expect(history.getByText('CRITERIA_CHANGED',{exact:true})).toBeVisible();await expect(history.getByRole('row').filter({hasText:'Lần gửi 1'})).toContainText('Từ chối');
  await school.page.setViewportSize({width:390,height:844});await expect.poll(()=>school.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  const admin=await actor(cfg.adminEmail);await openWorkspacePage(admin.page,'Thống kê Matching');const stats=admin.page.getByRole('region',{name:'Thống kê thực thi Matching',exact:true});
  await stats.getByLabel('Mã nhu cầu (không bắt buộc)',{exact:true}).fill(cfg.demandId);await stats.getByLabel('Từ thời điểm',{exact:true}).fill('2026-01-01T00:00');await stats.getByLabel('Đến trước thời điểm',{exact:true}).fill('2026-01-02T00:00');await stats.getByRole('button',{name:'Tải thống kê Matching',exact:true}).click();
  await expect(stats.locator('dl')).toBeVisible();await expect(stats.locator('dl > div').filter({has:admin.page.getByText('Lượt đối chiếu hoàn tất',{exact:true})}).locator('dd')).toHaveText('2');await expect(stats.locator('dl > div').filter({has:admin.page.getByText('Người thực thi khác nhau',{exact:true})}).locator('dd')).toHaveText('1');
  await stats.getByLabel('Đến trước thời điểm',{exact:true}).fill('2026-01-01T00:00');await expect(stats.locator('dl')).toHaveCount(0);await stats.getByRole('button',{name:'Tải thống kê Matching',exact:true}).click();await expect(stats.getByText('Thời điểm bắt đầu phải trước thời điểm kết thúc.',{exact:true})).toBeVisible();
  await admin.page.setViewportSize({width:390,height:844});await expect.poll(()=>admin.page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await admin.page.screenshot({path:'test-results/cooperation-reporting-browser/admin-statistics-mobile.png',fullPage:true});await school.context.close();await admin.context.close();
});

test('protected routing survives login, Reload and Back with real sessions and current school authority',async({browser})=>{
  test.skip(!process.env.E2E_COOPERATION_REPORTING,'Run through CooperationReportingIT with isolated MySQL');test.setTimeout(180000);
  const cfg=JSON.parse(process.env.E2E_COOPERATION_REPORTING!) as Record<string,string>;
  const context=await browser.newContext({viewport:{width:1440,height:1000},reducedMotion:'reduce'}),page=await context.newPage();
  const path='/school/cooperations?'+new URLSearchParams({orgId:cfg.schoolId,recordId:cfg.cooperationId});
  await page.goto(path);await expect(page.getByRole('heading',{name:'Đăng nhập',exact:true})).toBeVisible();await expect(page).toHaveURL(path);
  await page.getByLabel('Email',{exact:true}).fill(cfg.staffEmail);await page.getByLabel('Mật khẩu',{exact:true}).fill(cfg.password);await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  const history=page.getByRole('region',{name:'Lịch sử các lần gửi hợp tác',exact:true});await expect(history).toBeVisible();await expect(page).toHaveURL(path);
  await history.getByRole('button',{name:'Tải lịch sử các lần gửi',exact:true}).click();await expect(history.getByRole('cell',{name:/^Lần gửi 26/})).toBeVisible();
  await page.reload();await expect(page).toHaveURL(path);await expect(history).toBeVisible();await page.getByRole('button',{name:'Đóng chi tiết',exact:true}).click();await expect(history).toHaveCount(0);
  await page.goBack();await expect(page).toHaveURL(path);await expect(history).toBeVisible();await history.getByRole('button',{name:'Tải lịch sử các lần gửi',exact:true}).click();await expect(history.getByRole('cell',{name:/^Lần gửi 26/})).toBeVisible();
  await page.setViewportSize({width:390,height:844});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await page.screenshot({path:'test-results/url-routing/school-deep-link-mobile.png',fullPage:true});
  await page.setViewportSize({width:1440,height:1000});await page.goto('/admin/audit');await expect(page.getByText('Không có quyền truy cập',{exact:true})).toBeVisible();await context.close();
  const adminContext=await browser.newContext({viewport:{width:390,height:844},reducedMotion:'reduce'}),admin=await adminContext.newPage();
  await admin.goto('/admin/matching-statistics');await admin.getByLabel('Email',{exact:true}).fill(cfg.adminEmail);await admin.getByLabel('Mật khẩu',{exact:true}).fill(cfg.password);await admin.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  await expect(admin).toHaveURL('/admin/matching-statistics');await admin.getByLabel('Mã nhu cầu (không bắt buộc)',{exact:true}).fill(cfg.demandId);await admin.getByRole('button',{name:'Tải thống kê Matching',exact:true}).click();
  await expect(admin.locator('dl > div').filter({has:admin.getByText('Lượt đối chiếu hoàn tất',{exact:true})}).locator('dd')).toHaveText('3');await expect.poll(()=>admin.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);await admin.screenshot({path:'test-results/url-routing/admin-deep-link-mobile.png',fullPage:true});await adminContext.close();
});
