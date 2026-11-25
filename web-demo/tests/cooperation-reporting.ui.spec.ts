import {test,expect} from '@playwright/test';
const fixture='/tests/fixtures/reporting.html';
test('history preserves submitted values, pages and clears decisions on scope loss or change',async({page})=>{
  let lost=false;const requests:{page:number;org:string|undefined;method:string}[]=[];
  await page.route('**/api/v1/school/cooperations/**/submission-history?*',route=>{
    const request=route.request(),p=Number(new URL(request.url()).searchParams.get('page'));requests.push({page:p,org:request.headers()['x-organization-id'],method:request.method()});
    return lost?route.fulfill({status:404,json:{errorCode:'RESOURCE_NOT_FOUND'}}):route.fulfill({json:{content:[{submissionNo:p?1:26,submittedAt:'2026-01-02T00:00:00Z',demandVersion:p?1:26,criteriaVersion:p?2:26,quantitySnapshot:p?10:260,decision:p?'REJECT':null,decidedAt:p?'2026-01-02T01:00:00Z':null,reasonCode:p?'OLD_REASON':null,decisionRef:p?'OLD_DECISION':null}],page:p,size:25,totalElements:26,totalPages:2}});
  });
  await page.goto(fixture);const region=page.getByRole('region',{name:'Lịch sử các lần gửi hợp tác',exact:true});expect(requests).toHaveLength(0);
  await region.getByRole('button',{name:'Tải lịch sử các lần gửi',exact:true}).click();await expect(region.getByRole('cell',{name:/^Chưa có quyết định/})).toBeVisible();await region.getByRole('button',{name:'Lịch sử sau',exact:true}).click();
  await expect(region.getByRole('cell',{name:/^OLD_DECISION/})).toBeVisible();await expect(region.getByRole('cell',{name:'1 / 2',exact:true})).toBeVisible();await expect(region.getByRole('cell',{name:'10',exact:true})).toBeVisible();
  lost=true;await region.getByRole('button',{name:'Tải lịch sử các lần gửi',exact:true}).click();await expect(region.getByRole('cell',{name:/^OLD_DECISION/})).toHaveCount(0);await expect(region.getByText(/Lịch sử không còn truy cập được/)).toBeVisible();
  await page.getByRole('button',{name:'Chuyển trường kiểm thử',exact:true}).click();await expect(region.getByRole('table')).toHaveCount(0);expect(requests.every(r=>r.method==='GET'&&r.org==='school-one')).toBe(true);
  await page.setViewportSize({width:390,height:844});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('statistics abort stale filters, validate dates, show zero and clear results after denied access',async({page})=>{
  const a='00000000-0000-4000-8000-000000000001',b='00000000-0000-4000-8000-000000000002';let release:()=>void=()=>{},denied=false;const queries:URL[]=[];
  await page.route('**/api/v1/admin/matching-statistics**',async route=>{
    const url=new URL(route.request().url());queries.push(url);expect(route.request().method()).toBe('GET');
    if(url.searchParams.get('demandId')===a)await new Promise<void>(resolve=>{release=resolve;});
    if(denied)return route.fulfill({status:403,json:{errorCode:'ACCESS_DENIED'}}).catch(()=>{});
    return route.fulfill({json:{totalExecutions:0,distinctActors:0,firstExecutionAt:null,lastExecutionAt:null}}).catch(()=>{});
  });
  await page.goto(fixture+'?statistics');const load=page.getByRole('button',{name:'Tải thống kê Matching',exact:true}),demand=page.getByLabel('Mã nhu cầu (không bắt buộc)',{exact:true});expect(queries).toHaveLength(0);
  await demand.fill('bad');await load.click();await expect(page.getByText('Mã nhu cầu phải có định dạng UUID.',{exact:true})).toBeVisible();expect(queries).toHaveLength(0);
  await demand.fill(a);await load.click();await expect.poll(()=>queries.length).toBe(1);await demand.fill(b);await load.click();await expect(page.getByText('Chưa có lượt đối chiếu hoàn tất trong bộ lọc này.',{exact:true})).toBeVisible();release();
  expect(queries[1].searchParams.get('demandId')).toBe(b);await expect(page.locator('dl')).toBeVisible();denied=true;await load.click();await expect(page.locator('dl')).toHaveCount(0);await expect(page.getByText('Bạn không còn quyền xem thống kê Matching toàn hệ thống.',{exact:true})).toBeVisible();
  await page.getByLabel('Từ thời điểm',{exact:true}).fill('2026-02-01T00:00');await page.getByLabel('Đến trước thời điểm',{exact:true}).fill('2026-01-01T00:00');const n=queries.length;await load.click();await expect(page.getByText('Thời điểm bắt đầu phải trước thời điểm kết thúc.',{exact:true})).toBeVisible();expect(queries).toHaveLength(n);
  await page.setViewportSize({width:390,height:844});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
test('school role cannot discover global Matching statistics in navigation',async({page})=>{
  await page.route('**/api/v1/**',route=>{
    const p=new URL(route.request().url()).pathname.replace('/api/v1','');
    return route.fulfill({json:p==='/auth/me'?{userId:'school-user',email:'school@example.test',displayName:'School',memberships:[{role:'SCHOOL_STAFF',organizationId:'school-one'}]}:p==='/auth/me/permissions'?[{role:'SCHOOL_STAFF',organizationId:'school-one',permissions:['cooperation:school:review']}]:p==='/cooperation/organizations'?[{id:'school-one',name:'School',type:'UNIVERSITY'}]:{content:[],page:0,totalPages:0,totalElements:0}});
  });
  await page.goto('/');await expect(page.locator('.workspace')).toBeVisible();await expect(page.getByRole('navigation',{name:'Điều hướng chính'}).getByRole('button',{name:'Thống kê Matching',exact:true,includeHidden:true})).toHaveCount(0);
});
