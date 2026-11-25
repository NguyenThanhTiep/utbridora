import {test,expect,type Page} from '@playwright/test';
const id=(n:number)=>'00000000-0000-4000-8000-'+String(n).padStart(12,'0');
const term=id(21),school=id(11),opportunityId=id(81),invitationId=id(101),oldId=id(201),latestId=id(202);
const rows=(content:unknown[]=[])=>({content,page:0,size:100,totalElements:content.length,totalPages:content.length?1:0});
const opportunity={opportunityId,cooperationId:id(31),demandId:id(41),schoolOrgId:school,enterpriseOrgId:id(12),termId:term,enterpriseName:'Synthetic employer',title:'Synthetic routing opportunity',majorCodes:[],dateRange:{startDate:'2026-01-01',endDate:'2099-01-01'},benefits:null,location:null,publishedAt:'2026-01-01T00:00:00Z',closedAt:'2099-01-01T00:00:00Z',version:1};
async function student(page:Page){
  const calls:{path:string;method:string}[]=[];
  const invitations=[invitationId,id(102)].map((v,n)=>({invitationId:v,proposalItemId:id(301+n),placementCandidateId:id(301+n),studentUserId:id(2),schoolOrgId:school,enterpriseOrgId:id(12),termId:term,opportunityId,title:'Synthetic invitation '+n,enterpriseName:'Synthetic employer',state:'PENDING',expiresAt:'2099-01-01T00:00:00Z',decidedAt:null,decisionReasonCode:null,version:1,placementId:null}));
  const preferences=[{preferenceId:oldId,intentVersion:1,status:'WITHDRAWN',withdrawnAt:'2026-01-01T01:00:00Z'},{preferenceId:latestId,intentVersion:2,status:'SUBMITTED',withdrawnAt:null}].map(p=>({...p,studentUserId:id(2),schoolOrgId:school,termId:term,opportunityId,version:2,message:null,createdAt:'2026-01-01T00:00:00Z',reviewedAt:null,reviewedByUserId:null}));
  await page.route('**/api/v1/**',route=>{
    const request=route.request(),path=new URL(request.url()).pathname.replace('/api/v1','');calls.push({path,method:request.method()});
    if(path==='/auth/me')return route.fulfill({json:{userId:id(2),email:'student-routing@example.test',displayName:'Student routing',memberships:[{role:'STUDENT',organizationId:school}]}});
    if(path==='/auth/me/permissions')return route.fulfill({json:[{role:'STUDENT',organizationId:school,permissions:['student_profile:self:read']}]});
    if(path==='/terms')return route.fulfill({json:rows([{termId:term,schoolOrgId:school,termCode:'Synthetic term',status:'ACTIVE',startDate:'2020-01-01',endDate:'2099-01-01',version:1}])});
    if(path==='/students/me/catalogs')return route.fulfill({json:{catalogVersion:'synthetic',entries:rows()}});
    if(path==='/students/me/profile')return route.fulfill({status:404,json:{errorCode:'PROFILE_NOT_FOUND'}});
    if(path==='/opportunities')return route.fulfill({json:rows([opportunity])});
    if(path==='/opportunities/'+opportunityId)return route.fulfill({json:opportunity});
    if(path==='/invitations/me')return route.fulfill({json:rows(invitations)});
    if(path==='/invitations/'+invitationId)return route.fulfill({json:invitations[0]});
    if(path==='/students/me/preferences')return route.fulfill({json:rows(preferences)});
    if(path.endsWith('/profile-sharing-policy'))return route.fulfill({json:{schoolOrgId:school,termId:term,version:1,allowedFields:['skills'],updatedAt:null,updatedByUserId:null}});
    return route.fulfill({json:rows()});
  });return calls;
}
test('student opportunity detail has a copyable URL and Back restores list and detail',async({page})=>{
  const calls=await student(page);await page.goto('/co-hoi');await page.getByRole('button',{name:'Xem chi tiết',exact:true}).click();await expect(page).toHaveURL('/co-hoi/'+opportunityId);
  await page.reload();await expect(page.getByRole('heading',{name:opportunity.title,exact:true})).toBeVisible();await page.getByRole('button',{name:'Quay lại danh sách',exact:true}).click();await expect(page).toHaveURL('/co-hoi');
  await page.goBack();await expect(page).toHaveURL('/co-hoi/'+opportunityId);expect(calls.every(c=>c.method==='GET')).toBe(true);
});
test('student invitation detail restores its selected record after Reload and Back',async({page})=>{
  const calls=await student(page);await page.goto('/loi-moi');await page.locator('section.student-offer').filter({hasText:'Synthetic invitation 0'}).getByRole('button',{name:'Xem chi tiết',exact:true}).click();await expect(page).toHaveURL('/loi-moi/'+invitationId);
  await page.reload();await expect(page.getByRole('button',{name:'Ẩn chi tiết',exact:true})).toHaveAttribute('aria-expanded','true');await expect(page.locator('section.student-offer')).toHaveCount(1);
  await page.getByRole('button',{name:'Ẩn chi tiết',exact:true}).click();await expect(page).toHaveURL('/loi-moi');await expect(page.locator('section.student-offer')).toHaveCount(2);await page.goBack();await expect(page).toHaveURL('/loi-moi/'+invitationId);expect(calls.every(c=>c.method==='GET')).toBe(true);
});
test('old preference bookmark shows that historical intent and keeps it read only',async({page})=>{
  const calls=await student(page);await page.goto('/nguyen-vong/'+oldId);await expect(page.getByText('Đây là lần gửi trước. Trở về danh sách để xem lần gửi hiện tại.',{exact:true})).toBeVisible();await expect(page.getByRole('button',{name:'Rút nguyện vọng',exact:true})).toHaveCount(0);
  await expect(page.locator('dl.student-detail > div').filter({has:page.getByText('Lần gửi',{exact:true})}).locator('dd')).toHaveText('1');await page.reload();await expect(page).toHaveURL('/nguyen-vong/'+oldId);expect(calls.every(c=>c.method==='GET')).toBe(true);
});
