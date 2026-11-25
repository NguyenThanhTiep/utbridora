import {test,expect} from '@playwright/test';
import {openWorkspacePage} from './helpers/workspace';
const org='00000000-0000-4000-8000-000000000011';
async function enterprise(page:import('@playwright/test').Page){
  const rows={content:[],page:0,size:25,totalElements:0,totalPages:0};
  await page.route('**/api/v1/**',route=>{
    const path=new URL(route.request().url()).pathname.replace('/api/v1','');
    return route.fulfill({json:path==='/auth/me'?{userId:'dirty-editor-user',email:'dirty@example.test',displayName:'Dirty editor',memberships:[{role:'ENTERPRISE',organizationId:org}]}:
      path==='/auth/me/permissions'?[{role:'ENTERPRISE',organizationId:org,permissions:['cooperation:enterprise:create']}]:
      path==='/cooperation/organizations'?[{id:org,name:'Synthetic enterprise',type:'ENTERPRISE'}]:
      path==='/auth/csrf'?{headerName:'X-CSRF-TOKEN',token:'synthetic-csrf'}:rows});
  });
}
test('real demand editor retains unsaved input when menu and multi-entry Back are canceled',async({page})=>{
  await enterprise(page);await page.goto('/enterprise/overview');await openWorkspacePage(page,'Tài khoản của tôi');await openWorkspacePage(page,'Nhu cầu thực tập');
  await page.getByRole('button',{name:'Tạo nhu cầu',exact:true}).click();const title=page.getByLabel('Tên nhu cầu',{exact:true});await title.fill('Unsaved routing demand');
  let prompts=0;page.on('dialog',async dialog=>{prompts++;await dialog.dismiss();});
  await openWorkspacePage(page,'Hợp tác');await expect(page).toHaveURL('/enterprise/demands');await expect(title).toHaveValue('Unsaved routing demand');expect(prompts).toBe(1);
  await page.evaluate(()=>history.go(-2));await expect.poll(()=>prompts).toBe(2);await expect(page).toHaveURL('/enterprise/demands');await expect(title).toHaveValue('Unsaved routing demand');
  page.removeAllListeners('dialog');page.once('dialog',dialog=>dialog.accept());await page.goBack();await expect(page).toHaveURL('/tai-khoan');await page.goForward();await expect(page).toHaveURL('/enterprise/demands');
});
test('existing internal Case anchors navigate without reloading the document and Back restores the page',async({page})=>{
  await enterprise(page);await page.goto('/enterprise/cooperations');await expect(page.getByRole('heading',{level:1,name:'Hợp tác',exact:true})).toBeVisible();
  await page.evaluate(()=>{(window as any).__routingDocument='same document';const link=document.createElement('a');link.href='/workspace/cases/77777777-7777-4777-8777-777777777777?followup=quality';link.textContent='Open synthetic Case link';document.querySelector('main')!.append(link);});
  await page.getByRole('link',{name:'Open synthetic Case link',exact:true}).click();await expect(page).toHaveURL(/\/workspace\/cases\/.*\?followup=quality$/);
  expect(await page.evaluate(()=>(window as any).__routingDocument)).toBe('same document');await page.goBack();await expect(page).toHaveURL('/enterprise/cooperations');await expect(page.getByRole('heading',{level:1,name:'Hợp tác',exact:true})).toBeVisible();
});
test('anonymous unknown route shows not found and recovery uses a local login route',async({page})=>{
  await page.route('**/api/v1/**',route=>route.fulfill({status:401,json:{errorCode:'SESSION_EXPIRED'}}));
  await page.goto('/does-not-exist?returnTo=https://evil.example');await expect(page.getByRole('heading',{level:1,name:'Không tìm thấy trang',exact:true})).toBeVisible();
  await page.getByRole('button',{name:'Đến trang đăng nhập',exact:true}).click();await expect(page).toHaveURL('/dang-nhap');await expect(page.getByRole('heading',{name:'Đăng nhập',exact:true})).toBeVisible();
});
