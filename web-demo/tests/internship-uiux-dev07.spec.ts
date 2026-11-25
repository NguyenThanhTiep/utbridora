import { test, expect, type Page } from '@playwright/test';
import { ids } from './fixtures/internship';
const taskId='00000000-0000-4000-8000-000000000020';
async function setup(page:Page) {
  const requests:{url:string;body:unknown}[]=[];
  await page.route('**/api/v1/**',async r=>{
    const q=r.request(),u=new URL(q.url());requests.push({url:q.url(),body:q.method()==='GET'?null:q.postDataJSON()});
    if(u.pathname.endsWith('/auth/csrf'))return r.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'synthetic-csrf'}});
    if(q.method()==='POST')return r.fulfill({status:409,json:{errorCode:'VERSION_CONFLICT',message:'Conflict',requestId:'synthetic'}});
    const content=u.pathname.endsWith('/tasks')?[{taskId,title:'Xây dựng REST API',description:'Hoàn thiện API theo hợp đồng',groupId:ids.group,dueAt:'2026-11-25T10:00:00Z',priority:'HIGH',lateAllowed:true,version:1,assignments:[{assignmentId:'a',caseId:ids.case,studentUserId:ids.user,state:'IN_PROGRESS',version:1,currentSubmissionId:null}]}]:[];
    return r.fulfill({json:{content,page:0,size:25,totalPages:content.length?1:0,totalElements:content.length}});
  });await page.goto('/tests/fixtures/internship-week3.html?role=mentor');return requests;
}
for(const width of [1440,768,375])test(`DEV07 workbench responsive ${width}`,async({page})=>{
  await page.setViewportSize({width,height:960});await setup(page);
  await page.getByRole('button',{name:'Bảng Kanban',exact:true}).click();
  await expect(page.locator('.t3-kanban').getByRole('button',{name:'Xây dựng REST API',exact:true})).toBeVisible();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:`../target/t3-uiux-kanban-${width}.png`,fullPage:true,animations:'disabled'});
  await page.getByRole('button',{name:'Danh sách nhiệm vụ',exact:true}).click();
  await expect(page.getByRole('region',{name:'Danh sách nhiệm vụ'})).toBeVisible();
  await page.screenshot({path:`../target/t3-uiux-list-${width}.png`,fullPage:true,animations:'disabled'});
  await page.getByRole('button',{name:'Giao nhiệm vụ mới',exact:true}).click();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
  await page.screenshot({path:`../target/t3-uiux-create-${width}.png`,fullPage:true,animations:'disabled'});
});
test('DEV07 navigation keeps draft and 409 refresh keeps create form',async({page})=>{
  await setup(page);await page.getByRole('button',{name:'Giao nhiệm vụ mới',exact:true}).click();
  await page.getByLabel('Tiêu đề',{exact:true}).fill('Bản nháp giữ lại');await page.getByLabel('Mô tả',{exact:true}).fill('Nội dung cần lưu');
  await page.getByLabel('Hạn hoàn thành',{exact:true}).fill('2026-12-10T10:00');await page.getByRole('checkbox').first().check();
  await page.getByRole('button',{name:'Giao nhiệm vụ',exact:true}).click();await expect(page.getByText(/VERSION_CONFLICT/)).toBeVisible();
  await page.getByRole('button',{name:'Bảng Kanban',exact:true}).click();await page.getByRole('button',{name:'Tải lại bảng việc'}).click();
  await page.getByRole('button',{name:'Giao nhiệm vụ mới',exact:true}).click();await expect(page.getByLabel('Tiêu đề',{exact:true})).toHaveValue('Bản nháp giữ lại');
});
test('DEV07 Case filter reaches API and search exposes honest empty state',async({page})=>{
  const requests=await setup(page);await page.getByRole('button',{name:'Bảng Kanban',exact:true}).click();
  await page.getByRole('combobox',{name:'Hồ sơ được giao',exact:true}).selectOption(ids.case);
  await expect.poll(()=>requests.some(r=>new URL(r.url).searchParams.get('caseId')===ids.case)).toBe(true);
  await page.getByLabel('Tìm tiêu đề trong trang').fill('không tồn tại');await expect(page.getByText('Chưa có nhiệm vụ phù hợp với bộ lọc.')).toBeVisible();
  await page.getByRole('button',{name:'Báo cáo tuần',exact:true}).click();await expect(page.getByRole('button',{name:'Mở báo cáo tuần'})).toBeVisible();
  const calendar=page.getByRole('button',{name:'Lịch công việc',exact:true});await calendar.focus();await page.keyboard.press('Enter');
  await expect(calendar).toHaveAttribute('aria-pressed','true');
  await page.getByRole('combobox',{name:'Hồ sơ trên lịch',exact:true}).selectOption(ids.case);
  await expect.poll(()=>requests.some(r=>{const u=new URL(r.url);return u.pathname.endsWith('/calendar')&&u.searchParams.get('caseId')===ids.case;})).toBe(true);
  await expect(page.getByText('Chưa có hạn công việc trong tháng và phạm vi đã chọn.')).toBeVisible();
});
