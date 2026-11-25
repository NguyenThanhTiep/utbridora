import { test, expect, type Page } from '@playwright/test';
import { openWorkspacePage } from './helpers/workspace';

// HTTP fixtures verify exceptional UI outcomes. SchoolFollowUpManagementIT supplies real MySQL and Chromium evidence.
const id=(n:number)=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
const actor=id(1), school=id(2), enterprise=id(3), term=id(4), grant=id(5), preference=id(6), invitation=id(7), group=id(8);
const pageOf=(content:unknown[],page=0,total=content.length)=>({content,page,size:25,totalElements:total,totalPages:Math.ceil(total/25)});
async function identity(page:Page,role:string,permissions:string[]) {
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    const organizationId=role==='SCHOOL_STAFF'?school:enterprise;
    const json=path==='/auth/me'?{userId:actor,email:'synthetic@example.test',displayName:'UI Actor',memberships:[{role,organizationId}]}
      :path==='/auth/me/permissions'?[{role,organizationId,permissions}]
      :path==='/auth/csrf'?{headerName:'X-CSRF-TOKEN',token:'ui-fixture'}
      :path==='/cooperation/organizations'?[{id:school,name:'UI School',type:'UNIVERSITY'},{id:enterprise,name:'UI Enterprise',type:'ENTERPRISE'}]:undefined;
    return json===undefined?route.fulfill({status:404,json:{errorCode:'RESOURCE_NOT_FOUND'}}):route.fulfill({json});
  });
}

test('preference review reloads version after conflict, retries exact uncertain command, and clears PII on lost scope',async({page})=>{
  await identity(page,'SCHOOL_STAFF',['student:preference:school:review']);
  let version=1,reviewed=false,lost=false;
  const commands:{body:string|null;key:string|undefined}[]=[];
  const value=()=>({preferenceId:preference,studentUserId:actor,schoolOrgId:school,termId:term,opportunityId:id(9),intentVersion:1,version,status:reviewed?'REVIEWED':'SUBMITTED',message:'Private synthetic intent',createdAt:'2026-11-25T01:00:00Z'});
  await page.route('**/api/v1/school/preferences**',route=>{
    const request=route.request(),path=new URL(request.url()).pathname;
    if(lost)return route.fulfill({status:404,json:{errorCode:'RESOURCE_NOT_FOUND'}});
    if(path.endsWith('/scopes'))return route.fulfill({json:pageOf([{schoolOrgId:school,termId:term,termCode:'UI TERM'}])});
    if(request.method()==='POST'){
      commands.push({body:request.postData(),key:request.headers()['idempotency-key']});
      if(commands.length===1){version=2;return route.fulfill({status:409,json:{errorCode:'VERSION_CONFLICT'}});}
      if(commands.length===2)return route.fulfill({status:503,json:{errorCode:'DEPENDENCY_UNAVAILABLE'}});
      reviewed=true;version=3;return route.fulfill({json:value()});
    }
    if(path.endsWith('/'+preference))return route.fulfill({json:value()});
    expect(request.headers()['x-organization-id']).toBe(school);
    return route.fulfill({json:pageOf([value()])});
  });
  await page.goto('/');await openWorkspacePage(page,'Rà soát nguyện vọng');await page.getByLabel('Phạm vi rà soát nguyện vọng',{exact:true}).selectOption(school+':'+term);
  await page.getByRole('button',{name:'Xem nguyện vọng',exact:true}).click();await page.getByLabel('Mã lý do rà soát',{exact:true}).fill('SCHOOL_REVIEWED');
  const confirm=page.getByRole('checkbox',{name:'Tôi xác nhận đã rà soát ý định này.',exact:true}),send=page.getByRole('button',{name:'Ghi nhận đã rà soát',exact:true});await confirm.check();await send.click();
  await expect(page.getByText('Tải phiên bản hiện tại trước khi rà soát lại.',{exact:true})).toBeVisible();await expect(send).toBeDisabled();
  await page.getByRole('button',{name:'Tải phiên bản nguyện vọng hiện tại',exact:true}).click();await expect(page.getByRole('region',{name:'Chi tiết nguyện vọng'})).toContainText('1 / 2');await confirm.check();await send.click();
  await expect(page.getByRole('button',{name:'Thử lại thao tác vừa gửi',exact:true})).toBeVisible();await expect(page.getByLabel('Phạm vi rà soát nguyện vọng',{exact:true})).toBeDisabled();await expect(page.getByLabel('Mã lý do rà soát',{exact:true})).toBeDisabled();
  await page.getByRole('button',{name:'Thử lại thao tác vừa gửi',exact:true}).click();await expect(page.getByText('Đã ghi nhận rà soát nguyện vọng.',{exact:true})).toBeVisible();
  expect(commands[1]).toEqual(commands[2]);expect(commands[0].key).not.toBe(commands[1].key);expect(JSON.parse(commands[1].body!)).toEqual({expectedVersion:2,reasonCode:'SCHOOL_REVIEWED'});
  // Wait for the successful command's automatic refresh before simulating a later revocation.
  await expect(page.getByRole('region',{name:'Chi tiết nguyện vọng'})).toContainText('1 / 3');
  lost=true;await page.getByRole('button',{name:'Tải phiên bản nguyện vọng hiện tại',exact:true}).click();await expect(page.getByText('Private synthetic intent',{exact:true})).toHaveCount(0);await expect(page.getByRole('region',{name:'Chi tiết nguyện vọng'})).toHaveCount(0);
});

test('sent invitation pagination reads details, rejects stale revoke and preserves exact retry payload',async({page})=>{
  await identity(page,'ENTERPRISE',['t3:mentor:invite','t3:mentors:manage','t3:workspace:read']);
  const assignment={id:grant,userId:actor,kind:'HR',active:true,validFrom:'2020-01-01T00:00:00Z',validUntil:'2099-01-01T00:00:00Z',scope:{schoolOrgId:school,enterpriseOrgId:enterprise,termId:term,groupId:null,caseId:null}};
  let version=1,state='ACCEPTED';const commands:{body:string|null;key:string|undefined}[]=[];
  const value=()=>({invitation:{invitationId:invitation,version,state,enterpriseOrgId:enterprise,termId:term,groupIds:[group],expiresAt:'2099-01-01T00:00:00Z'},email:'mentor@example.test',recipientUserId:id(10)});
  await page.route('**/api/v1/t3/**',route=>{
    const request=route.request(),url=new URL(request.url()),path=url.pathname;
    if(path.endsWith('/context-assignments/me'))return route.fulfill({json:[assignment]});
    expect(request.headers()['x-context-assignment-id']).toBe(grant);
    if(path.endsWith('/assignment-management/groups'))return route.fulfill({json:pageOf([{groupId:group,code:'G-ui',cohortCode:'UI'}])});
    if(path.endsWith('/revoke')){
      commands.push({body:request.postData(),key:request.headers()['idempotency-key']});
      if(commands.length===1){version=2;return route.fulfill({status:409,json:{errorCode:'VERSION_CONFLICT'}});}
      if(commands.length===2)return route.fulfill({status:503,json:{errorCode:'DEPENDENCY_UNAVAILABLE'}});
      version=3;state='REVOKED';return route.fulfill({json:value().invitation});
    }
    if(path.endsWith('/sent/'+invitation))return route.fulfill({json:value()});
    if(path.endsWith('/sent')){const p=Number(url.searchParams.get('page')||0);return route.fulfill({json:{...pageOf(p?[value()]:[],p,26),totalPages:2}});}
    return route.fulfill({status:404,json:{errorCode:'RESOURCE_NOT_FOUND'}});
  });
  await page.goto('/');await openWorkspacePage(page,'Lời mời Mentor đã gửi');await page.getByLabel('Phân công HR quản lý lời mời',{exact:true}).selectOption(grant);await page.getByRole('button',{name:'Trang sau',exact:true}).click();
  await page.getByRole('button',{name:'Chi tiết lời mời',exact:true}).click();await expect(page.getByRole('region',{name:'Chi tiết lời mời'})).toContainText('mentor@example.test');
  const reason=page.getByLabel('Mã lý do thu hồi lời mời',{exact:true}),confirm=page.getByRole('checkbox',{name:'Tôi xác nhận thu hồi lời mời và các quyền phụ thuộc.',exact:true}),send=page.getByRole('button',{name:'Thu hồi lời mời đã chọn',exact:true});
  await reason.fill('MENTOR_CHANGED');await confirm.check();await send.click();await expect(send).toBeDisabled();await expect(page.getByText('Tải phiên bản hiện tại trước khi thu hồi.',{exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Tải phiên bản lời mời hiện tại',exact:true}).click();await expect(page.getByRole('region',{name:'Chi tiết lời mời'})).toContainText('2');await confirm.check();await send.click();await page.getByRole('button',{name:'Thử lại thao tác vừa gửi',exact:true}).click();
  await expect(page.getByText('Đã thu hồi lời mời theo kết quả máy chủ.',{exact:true})).toBeVisible();expect(commands[1]).toEqual(commands[2]);expect(JSON.parse(commands[1].body!)).toEqual({expectedVersion:2,reasonCode:'MENTOR_CHANGED'});
  await page.setViewportSize({width:390,height:844});await expect.poll(()=>page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
});
