import {test,expect,type Page} from '@playwright/test';
import {openWorkspacePage} from './helpers/workspace';
import {cohort,grants as hrGrants,group,hr,ids,me as hrMe,mentor} from './fixtures/internship';

const org='00000000-0000-4000-8000-000000000011',other='00000000-0000-4000-8000-000000000012',term='00000000-0000-4000-8000-000000000021';
const rows=(content:unknown[]=[])=>({content,page:0,size:25,totalElements:content.length,totalPages:content.length?1:0});
async function session(page:Page,role='ENTERPRISE',signedIn=true){
  let authenticated=signedIn;const calls:{path:string;method:string;organization:string|undefined}[]=[];
  const user={userId:'routing-user',email:'routing@example.test',displayName:'Routing tester',memberships:[{role,organizationId:role==='ADMIN'?null:org}]};
  const permissions=role==='ADMIN'?['audit:global:read','account:admin:status','membership:admin:write','taxonomy:skills:read']:
    role==='SCHOOL_STAFF'?['cooperation:school:review','t3:workspace:read','school:delegation:manage']:
    role==='STUDENT'?['student_profile:self:read','student_profile:self:write']:['cooperation:enterprise:create'];
  await page.route('**/api/v1/**',route=>{
    const request=route.request(),url=new URL(request.url()),path=url.pathname.replace('/api/v1','');
    calls.push({path:path+url.search,method:request.method(),organization:request.headers()['x-organization-id']});
    if(path==='/auth/me')return route.fulfill({status:authenticated?200:401,json:authenticated?user:{errorCode:'SESSION_EXPIRED'}});
    if(path==='/auth/login'){authenticated=true;return route.fulfill({json:user});}
    if(path==='/auth/logout'){authenticated=false;return route.fulfill({status:204});}
    if(path==='/auth/me/permissions')return route.fulfill({json:[{role,organizationId:role==='ADMIN'?null:org,permissions}]});
    if(path==='/auth/csrf')return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'synthetic-routing-csrf'}});
    if(path==='/cooperation/organizations')return route.fulfill({json:[{id:org,name:'Synthetic organization',type:role==='SCHOOL_STAFF'?'UNIVERSITY':'ENTERPRISE'}]});
    if(path==='/terms')return route.fulfill({json:rows([{termId:term,schoolOrgId:org,termCode:'Synthetic term',status:'ACTIVE',startDate:'2020-01-01',endDate:'2099-01-01',version:1}])});
    if(path==='/students/me/profile')return route.fulfill({json:{studentUserId:user.userId,schoolOrgId:org,profileVersion:1,completeness:{complete:false,checks:[]},profile:{termId:term,phone:null,majorCode:null,skillsDeclared:false,skills:[],customSkills:[],availabilityDeclared:false,availablePeriods:[],orientationCodes:[],subjectCodes:[],projects:[],certificates:[],cvFileId:null}}});
    if(path==='/admin/matching-statistics')return route.fulfill({json:{totalExecutions:0,distinctActors:0,firstExecutionAt:null,lastExecutionAt:null}});
    if(path==='/t3/context-assignments/me')return route.fulfill({json:[]});
    return route.fulfill({json:rows()});
  });return calls;
}
for(const width of [1440,390])test(`enterprise routes follow menus, Reload, Back and Forward at ${width}px`,async({page})=>{
  await page.setViewportSize({width,height:900});await page.emulateMedia({reducedMotion:'reduce'});await session(page);
  await page.goto('/');await expect(page).toHaveURL(/\/enterprise\/overview$/);
  if(width>=901){const length=await page.evaluate(()=>history.length);await page.getByRole('link',{name:'UT.Bridora trang chủ',exact:true}).click();expect(await page.evaluate(()=>history.length)).toBe(length);}
  await openWorkspacePage(page,'Nhu cầu thực tập');await expect(page).toHaveURL(/\/enterprise\/demands$/);
  await openWorkspacePage(page,'Hợp tác');await expect(page).toHaveURL(/\/enterprise\/cooperations$/);
  await page.goBack();await expect(page).toHaveURL(/\/enterprise\/demands$/);await expect(page.getByRole('heading',{level:1,name:'Nhu cầu thực tập',exact:true})).toBeVisible();
  await page.goForward();await expect(page).toHaveURL(/\/enterprise\/cooperations$/);await expect(page.getByRole('heading',{level:1,name:'Hợp tác',exact:true})).toBeVisible();
  await page.reload();await expect(page).toHaveURL(/\/enterprise\/cooperations$/);await expect(page.getByRole('heading',{level:1,name:'Hợp tác',exact:true})).toBeVisible();
  const before=await page.evaluate(()=>history.length);await openWorkspacePage(page,'Hợp tác');expect(await page.evaluate(()=>history.length)).toBe(before);
});
test('school menu and term keep the same URL scope after Reload and Back',async({page})=>{
  await session(page,'SCHOOL_STAFF');await page.goto('/school/cooperations');await expect(page.getByRole('heading',{level:1,name:'Duyệt hợp tác',exact:true})).toBeVisible();
  await page.getByLabel('Kỳ theo dõi',{exact:true}).selectOption(term);await expect(page).toHaveURL(new RegExp('/school/cooperations\\?orgId='+org+'&termId='+term+'$'));
  await openWorkspacePage(page,'Đề xuất sinh viên');await expect(page).toHaveURL(new RegExp('/school/proposals\\?orgId='+org+'&termId='+term+'$'));
  await page.reload();await expect(page.getByLabel('Kỳ theo dõi',{exact:true})).toHaveValue(term);
  await page.goBack();await expect(page.getByRole('heading',{level:1,name:'Duyệt hợp tác',exact:true})).toBeVisible();await expect(page.getByLabel('Kỳ theo dõi',{exact:true})).toHaveValue(term);
});
test('student SRS pages and capability/response tabs restore their destination',async({page})=>{
  await session(page,'STUDENT');await page.goto('/');await expect(page).toHaveURL(/\/tong-quan$/);
  await openWorkspacePage(page,'Năng lực');await expect(page).toHaveURL(/\/ho-so\/nang-luc\?tab=du-an$/);
  await page.getByRole('tab',{name:/Chứng chỉ/}).click();await expect(page).toHaveURL(/\?tab=chung-chi$/);
  await page.getByRole('tab',{name:/Chứng chỉ/}).press('ArrowRight');await expect(page.getByRole('tab',{name:'CV',exact:true})).toBeFocused();await expect(page).toHaveURL(/\?tab=cv$/);
  await page.getByRole('tab',{name:'CV',exact:true}).press('ArrowLeft');await expect(page.getByRole('tab',{name:/Chứng chỉ/})).toBeFocused();await page.reload();await expect(page.getByRole('tab',{name:/Chứng chỉ/})).toHaveAttribute('aria-selected','true');
  await openWorkspacePage(page,'Lời mời & nguyện vọng');await expect(page).toHaveURL(/\/loi-moi$/);
  await page.getByRole('tab',{name:/Lời mời/}).press('ArrowRight');await expect(page.getByRole('tab',{name:/Nguyện vọng/})).toBeFocused();await expect(page).toHaveURL(/\/nguyen-vong$/);await page.reload();await expect(page.getByRole('tab',{name:/Nguyện vọng/})).toHaveAttribute('aria-selected','true');
  await page.goBack();await expect(page).toHaveURL(/\/loi-moi$/);await expect(page.getByRole('tab',{name:/Lời mời/})).toHaveAttribute('aria-selected','true');
});
test('deep link waits for login and resumes Case including followup without putting credentials in history',async({page})=>{
  await session(page,'SCHOOL_STAFF',false);const path='/workspace/cases/77777777-7777-4777-8777-777777777777?followup=policy';
  await page.goto(path);await expect(page.getByRole('heading',{name:'Đăng nhập',exact:true})).toBeVisible();await expect(page).toHaveURL(path);
  await page.getByLabel('Email',{exact:true}).fill('routing@example.test');await page.getByLabel('Mật khẩu',{exact:true}).fill('Synthetic-password-123!');await page.getByRole('button',{name:'Đăng nhập',exact:true}).click();
  await expect(page.getByRole('heading',{level:1,name:'Hồ sơ thực tập',exact:true})).toBeVisible();await expect(page).toHaveURL(path);await page.reload();await expect(page).toHaveURL(path);
  const state=await page.evaluate(()=>JSON.stringify(history.state));expect(state).not.toContain('Synthetic-password');expect(state).not.toContain('token');
});
test('known forbidden route never mounts Admin reads and unknown routes show not found',async({page})=>{
  const calls=await session(page);await page.goto('/admin/audit');await expect(page.getByText('Không có quyền truy cập',{exact:true})).toBeVisible();expect(calls.some(c=>c.path.startsWith('/admin/'))).toBe(false);
  for(const path of ['/route-does-not-exist','/enterprise/students','/workspace/cases/bad-id','/ho-so/nang-luc?tab=invalid']){
    await page.goto(path);await expect(page.getByRole('heading',{level:1,name:'Không tìm thấy trang',exact:true})).toBeVisible();
  }
});
test('explicit organization outside memberships never falls back to a different organization',async({page})=>{
  const calls=await session(page);await page.goto('/enterprise/cooperations?orgId='+other);await expect(page.getByText(/Tổ chức trong đường dẫn không thuộc phạm vi được cấp/)).toBeVisible();
  expect(calls.some(c=>c.path.startsWith('/enterprise/cooperations'))).toBe(false);expect(calls.some(c=>c.organization===other)).toBe(false);
});
test('Admin route restores by URL and mail token is removed while its form remains usable',async({page})=>{
  await session(page,'ADMIN');await page.goto('/admin/matching-statistics');await expect(page.getByRole('heading',{level:1,name:'Thống kê Matching',exact:true})).toBeVisible();await page.reload();await expect(page).toHaveURL(/\/admin\/matching-statistics$/);
  await page.goto('/reset-password#token=synthetic-one-time-token');await expect(page.getByRole('heading',{name:'Tiếp tục với liên kết email',exact:true})).toBeVisible();expect(await page.evaluate(()=>location.hash)).toBe('');expect(await page.evaluate(()=>JSON.stringify(history.state))).not.toContain('synthetic-one-time-token');
});
for(const kind of ['HR','MENTOR'] as const)test(`${kind} workspace preserves its allowed section on Reload`,async({page})=>{
  await session(page,'ENTERPRISE');
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    if(path==='/auth/me')return route.fulfill({json:hrMe});
    if(path==='/auth/me/permissions')return route.fulfill({json:hrGrants});
    if(path==='/t3/context-assignments/me')return route.fulfill({json:[kind==='HR'?hr:mentor]});
    if(path==='/t3/cohorts')return route.fulfill({json:rows([cohort])});
    if(path.endsWith('/groups'))return route.fulfill({json:rows([group])});
    return route.fallback();
  });
  const path='/workspace/internship/'+(kind==='HR'?'cohorts':'overview');await page.goto(path);await expect(page).toHaveURL(path);await expect(page.locator('.workspace')).toBeVisible();await page.reload();await expect(page).toHaveURL(path);
  if(kind==='MENTOR'){await page.goto('/workspace/internship/cohorts');await expect(page).toHaveURL('/workspace/internship/overview');}
});
test('canceled popstate keeps the mounted editor, URL and Forward entries intact',async({page})=>{
  await session(page);await page.goto('/enterprise/overview');await openWorkspacePage(page,'Nhu cầu thực tập');
  // Same cancelable event used by existing dirty/uncertain editors, with observable retained state.
  await page.evaluate(()=>{const input=document.createElement('input');input.id='routing-dirty-value';input.value='unsaved value';document.querySelector('main')!.append(input);(window as any).__routingBlocked=true;window.addEventListener('demand-before-leave',e=>{if((window as any).__routingBlocked)e.preventDefault();});});
  await page.goBack();await expect(page).toHaveURL('/enterprise/demands');await expect(page.locator('#routing-dirty-value')).toHaveValue('unsaved value');
  await page.evaluate(()=>{(window as any).__routingBlocked=false;});await page.goBack();await expect(page).toHaveURL('/enterprise/overview');await page.goForward();await expect(page).toHaveURL('/enterprise/demands');
});
