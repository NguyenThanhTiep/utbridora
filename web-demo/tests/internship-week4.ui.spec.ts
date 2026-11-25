import { test, expect, type Page, type Route } from '@playwright/test';
import type { Evaluation, Rubric, StudentResult } from '../src/modules/internship/evaluation';
import { hr,mentor,ids } from './fixtures/internship';
const caseId='00000000-0000-4000-8000-000000000001',termId='00000000-0000-4000-8000-000000000002';
const rubric:Rubric={templateId:'rubric',templateVersion:1,criteria:[{criterionCode:'Q',label:'Quality',weight:40},{criterionCode:'P',label:'Process',weight:60}]};
const draft=(patch:Partial<Evaluation>={}):Evaluation=>({evaluationId:'evaluation',caseId,template:rubric,scores:[{criterionCode:'Q',score:5,comment:'Private note',evidenceFileIds:[]},{criterionCode:'P',score:3,comment:null,evidenceFileIds:[]}],total100:76,state:'DRAFT',revisionNo:1,version:1,publishedAt:null,decisionRef:null,...patch});
const published=()=>draft({state:'PUBLISHED',publishedAt:'2026-11-14T03:00:00Z',decisionRef:'SCHOOL-1'});
const pageOf=(content:unknown[])=>({content,page:0,size:100,totalElements:content.length,totalPages:content.length?1:0});
const error=(errorCode:string)=>({errorCode,message:'fixture',requestId:'fixture',fieldErrors:[]});
async function setup(page:Page,current:Evaluation|null=draft()) {
  const state={current,result:{caseId,visibility:'VISIBLE',evaluation:published(),reasonCode:'VISIBLE'} as StudentResult,
    policy:{termId,schoolOrgId:'school',version:0,enabled:false}};
  const mutations:{path:string;body:Record<string,unknown>;key:string;context:string;csrf:string}[]=[];
  const paths:string[]=[];let intercept:((r:Route)=>Promise<boolean>)|undefined;
  await page.route('**/api/v1/**',async route=>{
    const req=route.request(),url=new URL(req.url()),path=url.pathname.replace('/api/v1','');paths.push(path);
    if(req.method()!=='GET')mutations.push({path,body:req.postDataJSON(),key:req.headers()['idempotency-key'],context:req.headers()['x-context-assignment-id'],csrf:req.headers()['x-csrf-token']});
    if(intercept && await intercept(route))return;
    if(path==='/auth/csrf')return route.fulfill({json:{headerName:'X-CSRF-TOKEN',token:'csrf-fixture'}});
    if(path==='/t3/me/results')return route.fulfill({json:state.result});
    if(path.includes('visibility')) {
      if(req.method()==='PUT')Object.assign(state.policy,req.postDataJSON(),{version:state.policy.version+1});
      return route.fulfill({json:state.policy});
    }
    if(path==='/t3/rubric-templates')return route.fulfill({json:pageOf([rubric])});
    if(path==='/t3/school/evaluations')return route.fulfill({json:pageOf(state.current?[state.current]:[])});
    if(path.endsWith('/history'))return route.fulfill({json:pageOf([draft()])});
    if(path.startsWith('/t3/evaluations')) {
      if(req.method()==='GET')return route.fulfill(state.current?{json:url.searchParams.has('version')?draft():state.current}:{status:204});
      const body=req.postDataJSON();
      if(!state.current)state.current=draft({scores:body.scores});
      else {state.current={...state.current,version:state.current.version+1,...(body.scores?{scores:body.scores}:{})};}
      if(path.endsWith('/publish'))Object.assign(state.current,{state:'PUBLISHED',decisionRef:body.decisionRef,publishedAt:'2026-11-14T03:00:00Z'});
      if(path.endsWith('/submit'))state.current.state='SUBMITTED';
      if(path.endsWith('/revisions'))Object.assign(state.current,{state:'DRAFT',revisionNo:state.current.revisionNo+1,publishedAt:null,decisionRef:null});
      return route.fulfill({json:state.current});
    }
    return route.fulfill({status:404,json:error('RESOURCE_NOT_FOUND')});
  });
  return {state,mutations,paths,intercept:(handler:typeof intercept)=>{intercept=handler;}};
}
const open=(page:Page,role='mentor')=>page.goto('/tests/fixtures/internship-week4.html?role='+role);
test('T3-12 204 rubric requires explicit scores and sends numeric DTO, context, CSRF and key',async({page})=>{
  const s=await setup(page,null);await open(page);await page.getByLabel('Rubric áp dụng').selectOption('rubric:1');
  const save=page.getByRole('button',{name:'Tạo bản nháp đánh giá'});await expect(save).toBeDisabled();
  await page.getByLabel('Điểm Quality (0–5)',{exact:true}).fill('5');await page.getByLabel('Điểm Process (0–5)',{exact:true}).fill('3');
  await expect(page.getByText('Điểm dự kiến: 76.00',{exact:true})).toBeVisible();await save.click();
  await expect(page.getByRole('heading',{name:'Điểm tổng: 76.00 / 100'})).toBeVisible();
  expect(s.mutations[0]).toMatchObject({body:{caseId,templateId:'rubric',templateVersion:1,scores:[{criterionCode:'Q',score:5},{criterionCode:'P',score:3}]},context:'mentor-context',csrf:'csrf-fixture'});
  expect(s.mutations[0].key).toMatch(/^[0-9a-f-]{36}$/);
});
test('T3-12 conflict preserves draft and explicit refresh uses latest version',async({page})=>{
  const s=await setup(page);let conflict=true;s.intercept(async r=>{if(r.request().method()==='PUT' && conflict){conflict=false;s.state.current!.version=7;await r.fulfill({status:409,json:error('VERSION_CONFLICT')});return true;}return false;});
  await open(page);await page.getByLabel('Nhận xét Quality',{exact:true}).fill('Giữ bản nháp');await page.getByRole('button',{name:'Lưu đánh giá',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('VERSION_CONFLICT');await page.getByRole('button',{name:'Tải lại đánh giá',exact:true}).click();
  await expect(page.getByText(/Revision 1 · Version 7/)).toBeVisible();await expect(page.getByLabel('Nhận xét Quality',{exact:true})).toHaveValue('Giữ bản nháp');
  await page.getByRole('button',{name:'Lưu đánh giá',exact:true}).click();await expect.poll(()=>s.mutations.length).toBe(2);
  expect(s.mutations[1].body.expectedVersion).toBe(7);expect(s.mutations[1].key).not.toBe(s.mutations[0].key);
});
test('T3-12 uncertain mutation retries identical payload/key and locks edits',async({page})=>{
  const s=await setup(page);let fail=true;s.intercept(async r=>{if(r.request().method()==='PUT' && fail){fail=false;await r.abort('failed');return true;}return false;});
  await open(page);await page.getByLabel('Nhận xét Quality',{exact:true}).fill('Retry note');await page.getByRole('button',{name:'Lưu đánh giá',exact:true}).click();
  await expect(page.getByLabel('Nhận xét Quality',{exact:true})).toBeDisabled();await page.getByRole('button',{name:'Thử lại thao tác vừa gửi'}).click();
  await expect.poll(()=>s.mutations.length).toBe(2);expect(s.mutations[1]).toEqual(s.mutations[0]);
});
test('T3-12 immutable historical version is read only and wrong version closes view',async({page})=>{
  const s=await setup(page,draft({version:3}));await open(page);await page.getByLabel('Lịch sử đánh giá').selectOption('1');
  await expect(page.getByText(/Revision 1 · Version 1/)).toBeVisible();await expect(page.getByLabel('Nhận xét Quality',{exact:true})).toHaveCount(0);
  s.intercept(async r=>{if(r.request().url().includes('?version=1')){await r.fulfill({json:draft({version:2})});return true;}return false;});
  await page.getByRole('button',{name:'Tải lại đánh giá',exact:true}).click();await expect(page.getByText('Đã đóng dữ liệu fixture')).toBeVisible();
});
test('T3-12 missing CASE=false policy can be explicitly created with expectedVersion zero',async({page})=>{
  const s=await setup(page);await open(page,'hr');await page.getByRole('button',{name:'Lưu quyền xem điểm'}).click();
  await expect(page.getByText('Trạng thái đã lưu: Tắt · Version 1')).toBeVisible();expect(s.mutations[0]).toMatchObject({body:{expectedVersion:0,enabled:false},context:'hr-context'});
  await expect(page.getByRole('button',{name:'Lưu quyền xem điểm'})).toBeDisabled();
});
test('T3-12 School publish requires decision and preserves expectedVersion',async({page})=>{
  const s=await setup(page,draft({state:'SUBMITTED',version:4}));await open(page,'school');await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();
  const publish=page.getByRole('button',{name:'Công bố điểm',exact:true});await expect(publish).toBeDisabled();await page.getByLabel('Căn cứ quyết định').fill('x'.repeat(121));await expect(publish).toBeDisabled();
  await page.getByLabel('Căn cứ quyết định').fill(' DECISION-1 ');await publish.click();await expect(page.getByRole('button',{name:/Hồ sơ .*PUBLISHED/})).toBeVisible();
  expect(s.mutations[0]).toMatchObject({path:'/t3/evaluations/evaluation/publish',body:{expectedVersion:4,decisionRef:'DECISION-1'}});
});
test('T3-12 student reload HIDDEN removes all cached scores and uses only results API',async({page})=>{
  const s=await setup(page);await open(page,'student');await expect(page.getByRole('heading',{name:'Điểm tổng: 76.00 / 100'})).toBeVisible();
  s.state.result={caseId,visibility:'HIDDEN',evaluation:null,reasonCode:'NOT_AVAILABLE'};await page.getByRole('button',{name:'Tải lại kết quả'}).click();
  await expect(page.getByText('Kết quả chưa được phép hiển thị.')).toBeVisible();await expect(page.getByText('Private note')).toHaveCount(0);expect(new Set(s.paths)).toEqual(new Set(['/t3/me/results']));
});
test('T3-12 student rejects accidental draft payload and removes visible data on 404',async({page})=>{
  const s=await setup(page);s.state.result.evaluation=draft();await open(page,'student');await expect(page.getByRole('alert')).toContainText('Không thể xác nhận');await expect(page.getByText('Private note')).toHaveCount(0);
  s.state.result.evaluation=published();await page.getByRole('button',{name:'Tải lại kết quả'}).click();await expect(page.getByText('Private note')).toBeVisible();
  s.intercept(async r=>{await r.fulfill({status:404,json:error('RESOURCE_NOT_FOUND')});return true;});await page.getByRole('button',{name:'Tải lại kết quả'}).click();
  await expect(page.getByText('Đã đóng dữ liệu fixture')).toBeVisible();await expect(page.getByText('Private note')).toHaveCount(0);
});
test('T3-12 changing case clears old result immediately and rejects mismatched response',async({page})=>{
  await setup(page);await open(page,'student');await expect(page.getByText('Private note')).toBeVisible();await page.getByRole('button',{name:'Đổi hồ sơ fixture'}).click();
  await expect(page.getByText('Private note')).toHaveCount(0);await expect(page.getByRole('alert')).toContainText('Không thể xác nhận');
});
test('T3-12 rubric editor fits 320px and keyboard input validates before save',async({page})=>{
  await setup(page);await page.setViewportSize({width:320,height:740});await open(page);
  const input=page.getByLabel('Điểm Quality (0–5)',{exact:true});await input.fill('5.001');await input.press('Tab');await expect(page.getByLabel('Nhận xét Quality',{exact:true})).toBeFocused();
  await expect(page.getByRole('button',{name:'Lưu đánh giá',exact:true})).toBeDisabled();
  expect(await page.evaluate(()=>document.documentElement.scrollWidth<=window.innerWidth)).toBe(true);
});
test('T3-12 group-scoped HR has only CASE policy and revoked session closes it',async({page})=>{
  const s=await setup(page);s.intercept(async r=>{
    const url=new URL(r.request().url());
    if(url.pathname.endsWith('/context-assignments/me')){await r.fulfill({json:[{...hr,validUntil:'2099-01-01T00:00:00Z',scope:{...hr.scope,groupId:ids.group}}]});return true;}
    if(url.pathname.includes('grade-visibility')){await r.fulfill({json:{enterpriseOrgId:ids.enterprise,termId:ids.term,caseId,scope:'CASE',enabled:false,version:0}});return true;}
    return false;
  });await open(page,'root');await page.getByRole('combobox',{name:'Phân công đánh giá',exact:true}).selectOption(ids.hr);
  await expect(page.getByLabel('Phạm vi chính sách')).toHaveCount(0);await page.getByLabel('Mã hồ sơ thực tập',{exact:true}).fill(caseId);
  await page.getByRole('button',{name:'Mở đánh giá hồ sơ'}).click();await expect(page.getByRole('region',{name:'Quyền xem điểm theo hồ sơ'})).toBeVisible();
  const conditions=page.getByRole('region',{name:'Ba điều kiện hiển thị điểm'});
  await expect(conditions.getByText('Chưa xác nhận',{exact:true})).toHaveCount(2);
  await expect(conditions.getByText('Kế thừa doanh nghiệp/kỳ',{exact:true})).toBeVisible();
  await page.getByLabel('Cho phép sinh viên xem điểm').check();
  await expect(conditions.getByText('Kế thừa doanh nghiệp/kỳ',{exact:true})).toBeVisible();
  expect(s.paths.some(p=>p.includes('school/evaluations'))).toBe(false);await expect(page.getByRole('button',{name:'Công bố điểm',exact:true})).toHaveCount(0);
  await page.evaluate(()=>window.dispatchEvent(new Event('session-expired')));await expect(page.getByText('Quyền truy cập đã thay đổi. Dữ liệu đánh giá đã được đóng.')).toBeVisible();
  await expect(page.getByRole('region',{name:'Quyền xem điểm theo hồ sơ'})).toHaveCount(0);
});
test('T3-12 Mentor context expiry unmounts private draft without another request',async({page})=>{
  await page.clock.install({time:new Date('2026-11-14T03:00:00Z')});const s=await setup(page);
  s.intercept(async r=>{
    const path=new URL(r.request().url()).pathname;
    if(path.endsWith('/context-assignments/me')){await r.fulfill({json:[{...mentor,validUntil:'2026-11-14T03:00:10Z'}]});return true;}
    if(path.endsWith('/mentors/me/groups')){await r.fulfill({json:pageOf([{groupId:ids.group,caseIds:[caseId]}])});return true;}return false;
  });await open(page,'root');await page.getByRole('combobox',{name:'Phân công đánh giá',exact:true}).selectOption(ids.mentor);
  await page.getByRole('combobox',{name:'Hồ sơ trong nhóm',exact:true}).selectOption(caseId);await expect(page.getByRole('region',{name:'Nội dung đánh giá'}).getByText('Private note',{exact:true})).toBeVisible();
  await page.clock.fastForward(11000);await expect(page.getByText('Private note',{exact:true})).toHaveCount(0);await expect(page.getByLabel('Nhận xét Quality',{exact:true})).toHaveCount(0);
  await expect(page.getByText('Chưa có phân công Mentor/HR phù hợp còn hiệu lực.')).toBeVisible();
});

test('DEV06 School conflict reload preserves decision and reason, then uses current version',async({page})=>{
  const s=await setup(page,draft({state:'SUBMITTED',version:4}));let conflict=true;
  s.intercept(async r=>{if(r.request().url().endsWith('/publish') && conflict){conflict=false;s.state.current!.version=8;
    await r.fulfill({status:409,json:error('VERSION_CONFLICT')});return true;}return false;});
  await open(page,'school');await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();
  await page.getByLabel('Căn cứ quyết định').fill(' DECISION-RETRY ');
  await page.getByLabel('Mã lý do tạo revision').fill('CORRECTION');
  await page.getByRole('button',{name:'Công bố điểm',exact:true}).click();
  await expect(page.getByRole('alert')).toContainText('VERSION_CONFLICT');
  await page.getByRole('button',{name:'Tải lại đánh giá đang chọn'}).click();
  await expect(page.getByText(/Trạng thái: SUBMITTED · Revision 1 · Version 8/)).toBeVisible();
  await expect(page.getByLabel('Căn cứ quyết định')).toHaveValue(' DECISION-RETRY ');
  await expect(page.getByLabel('Mã lý do tạo revision')).toHaveValue('CORRECTION');
  await page.getByRole('button',{name:'Công bố điểm',exact:true}).click();
  await expect(page.getByText(/Đã công bố điểm\. Sinh viên/)).toBeVisible();
  expect(s.mutations[1].body).toEqual({expectedVersion:8,decisionRef:'DECISION-RETRY'});
  expect(s.mutations[1].key).not.toBe(s.mutations[0].key);
  expect(s.paths.some(p=>p.endsWith('/history') || p==='/t3/evaluations/evaluation')).toBe(false);
});

test('DEV06 School revision shows DRAFT and preserves previously published student result',async({page})=>{
  const s=await setup(page,published());await open(page,'school');
  await page.getByRole('button',{name:/Hồ sơ .*PUBLISHED/}).click();
  await page.getByLabel('Căn cứ quyết định').fill('REVISION-1');await page.getByLabel('Mã lý do tạo revision').fill('CORRECTION');
  await page.getByRole('button',{name:'Tạo revision đánh giá'}).click();
  await page.getByRole('button',{name:/Hồ sơ .*DRAFT/}).click();
  await expect(page.getByText(/Đang chờ Mentor hoàn thiện/)).toBeVisible();
  await expect(page.getByRole('button',{name:'Công bố điểm',exact:true})).toHaveCount(0);
  expect(s.mutations[0].body).toEqual({expectedVersion:1,decisionRef:'REVISION-1',reasonCode:'CORRECTION'});
  await open(page,'student');await expect(page.getByRole('heading',{name:'Điểm tổng: 76.00 / 100'})).toBeVisible();
});

test('DEV06 policy conflict keeps unsaved choice across reload, persists after browser reload',async({page})=>{
  const s=await setup(page);let conflict=true;s.intercept(async r=>{
    if(r.request().method()==='PUT' && conflict){conflict=false;s.state.policy.version=4;
      await r.fulfill({status:409,json:error('VERSION_CONFLICT')});return true;}return false;});
  await open(page,'hr');await page.getByRole('checkbox').check();await page.getByRole('button',{name:'Lưu quyền xem điểm'}).click();
  await expect(page.getByRole('alert')).toContainText('VERSION_CONFLICT');await page.getByRole('button',{name:'Tải lại chính sách'}).click();
  await expect(page.getByText('Trạng thái đã lưu: Tắt · Version 4')).toBeVisible();await expect(page.getByRole('checkbox')).toBeChecked();
  await page.getByRole('button',{name:'Lưu quyền xem điểm'}).click();await expect(page.getByText('Trạng thái đã lưu: Bật · Version 5')).toBeVisible();
  expect(s.mutations[1].body).toEqual({expectedVersion:4,enabled:true});
  await page.reload();await expect(page.getByRole('checkbox')).toBeChecked();
});

test('DEV06 School filter uses scoped list, empty state and removes old decision',async({page})=>{
  const s=await setup(page,draft({state:'SUBMITTED'}));let query='';
  s.intercept(async r=>{const u=new URL(r.request().url());if(u.pathname.endsWith('/school/evaluations') && u.searchParams.has('caseId')){
    query=u.search;await r.fulfill({json:pageOf([])});return true;}return false;});
  await open(page,'school');await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();await page.getByLabel('Căn cứ quyết định').fill('PRIVATE-DRAFT');
  await page.getByLabel('Lọc theo mã hồ sơ').fill(caseId);await page.getByRole('button',{name:'Áp dụng bộ lọc'}).click();
  await expect(page.getByText(/Chưa có đánh giá trong phạm vi này/)).toBeVisible();
  expect(query).toContain('termId='+termId);expect(query).toContain('caseId='+caseId);
  await expect(page.getByLabel('Căn cứ quyết định')).toHaveCount(0);
  await page.getByLabel('Lọc theo mã hồ sơ').fill('');await page.getByRole('button',{name:'Áp dụng bộ lọc'}).click();
  await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();await expect(page.getByLabel('Căn cứ quyết định')).toHaveValue('');
});

test('DEV06 School revoked access clears private score and draft on refresh',async({page})=>{
  const s=await setup(page,draft({state:'SUBMITTED'}));await open(page,'school');
  await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();await page.getByLabel('Căn cứ quyết định').fill('PRIVATE-DRAFT');
  s.intercept(async r=>{await r.fulfill({status:403,json:error('ACCESS_DENIED')});return true;});
  await page.getByRole('button',{name:'Tải lại đánh giá đang chọn'}).click();await expect(page.getByText('Đã đóng dữ liệu fixture')).toBeVisible();
  await expect(page.getByText('Private note')).toHaveCount(0);await expect(page.getByLabel('Căn cứ quyết định')).toHaveCount(0);
});

test('DEV06 School uncertain publish locks selection and retries same key/body',async({page})=>{
  const s=await setup(page,draft({state:'SUBMITTED'}));let fail=true;s.intercept(async r=>{
    if(r.request().url().endsWith('/publish') && fail){fail=false;await r.abort('failed');return true;}return false;});
  await open(page,'school');await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();await page.getByLabel('Căn cứ quyết định').fill('RETRY');
  await page.getByRole('button',{name:'Công bố điểm',exact:true}).click();await expect(page.getByRole('button',{name:'Thử lại thao tác vừa gửi'})).toBeVisible();
  await expect(page.getByLabel('Lọc trạng thái đánh giá')).toBeDisabled();await expect(page.getByLabel('Căn cứ quyết định')).toBeDisabled();
  await page.getByRole('button',{name:'Thử lại thao tác vừa gửi'}).click();await expect.poll(()=>s.mutations.length).toBe(2);expect(s.mutations[1]).toEqual(s.mutations[0]);
});

test('DEV06 School desktop and mobile layout has no horizontal overflow',async({page},testInfo)=>{
  await setup(page,draft({state:'SUBMITTED'}));await open(page,'school');await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();
  for(const width of [1440,768,375]){
    await page.setViewportSize({width,height:900});
    expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBe(true);
    await page.screenshot({path:testInfo.outputPath('school-'+width+'.png'),fullPage:true});
  }
});

test('DEV06 School changing term clears policy draft and selected decision',async({page})=>{
  const s=await setup(page,draft({state:'SUBMITTED'})),otherTerm='00000000-0000-4000-8000-000000000099';
  s.intercept(async r=>{const u=new URL(r.request().url());
    if(u.pathname.endsWith('/terms')){await r.fulfill({json:pageOf([termId,otherTerm].map((id,i)=>({termId:id,schoolOrgId:'school',termCode:'TERM-'+i})))});return true;}
    if(u.pathname.includes(otherTerm) && u.pathname.endsWith('/result-visibility')){await r.fulfill({json:{termId:otherTerm,schoolOrgId:'school',enabled:false,version:0}});return true;}
    return false;});
  await open(page,'school-root');await page.getByRole('combobox',{name:'Kỳ đánh giá',exact:true}).selectOption(termId);
  await page.getByRole('checkbox').check();await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();await page.getByLabel('Căn cứ quyết định').fill('TERM-PRIVATE');
  await page.getByRole('combobox',{name:'Kỳ đánh giá',exact:true}).selectOption(otherTerm);await expect(page.getByRole('checkbox')).not.toBeChecked();
  await expect(page.getByLabel('Căn cứ quyết định')).toHaveCount(0);
  await page.getByRole('button',{name:/Hồ sơ .*SUBMITTED/}).click();await expect(page.getByLabel('Căn cứ quyết định')).toHaveValue('');
});

test('DEV06 School expired session during term lookup closes the scope',async({page})=>{
  const s=await setup(page);s.intercept(async r=>{if(new URL(r.request().url()).pathname.endsWith('/terms')){
    await r.fulfill({status:401,json:error('SESSION_EXPIRED')});return true;}return false;});
  await open(page,'school-root');await expect(page.getByText('Quyền truy cập đã thay đổi. Dữ liệu đánh giá đã được đóng.')).toBeVisible();
  await expect(page.getByLabel('Mã kỳ thực tập')).toHaveCount(0);expect(s.paths.some(p=>p==='/t3/school/evaluations')).toBe(false);
});

test('DEV06 School without T5 term permission can use a delegated term checked by grade APIs',async({page})=>{
  const s=await setup(page);s.intercept(async r=>{if(new URL(r.request().url()).pathname.endsWith('/terms')){
    await r.fulfill({status:404,json:error('RESOURCE_NOT_FOUND')});return true;}return false;});
  await open(page,'school-root');await expect(page.getByRole('alert')).toContainText('Không tải được danh sách kỳ');
  await page.getByText('Nhập mã kỳ được phân công',{exact:true}).click();await page.getByLabel('Mã kỳ thực tập').fill(termId);
  await page.getByRole('button',{name:'Mở kỳ đánh giá'}).click();await expect(page.getByRole('button',{name:/Hồ sơ .*DRAFT/})).toBeVisible();
  expect(s.paths).toContain('/t3/school/evaluations');
});
