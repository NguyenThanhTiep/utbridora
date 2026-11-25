import {test,expect} from '@playwright/test';
import {readRoute,routeHref,view} from '../../src/routing/routes';
test('SRS student destinations select the corresponding page and independent tabs',()=>{
  expect(readRoute('/ho-so/nang-luc','?tab=chung-chi')).toMatchObject({screen:'student-capability',capabilityTab:'certificates'});
  expect(readRoute('/loi-moi')).toMatchObject({screen:'student-invitations',studentTab:'invitations'});
  expect(readRoute('/nguyen-vong')).toMatchObject({screen:'student-invitations',studentTab:'preferences'});
  expect(routeHref(readRoute('/ho-so/ky-nang'))).toBe('/ho-so');
});
test('malformed identifiers, duplicate state and pages belonging to the other portal are not found',()=>{
  for(const [path,query] of [['/school/statistics',''],['/enterprise/students',''],['/workspace/cases/not-a-uuid',''],['/co-hoi/not-a-uuid',''],['/enterprise/cooperations','?orgId=a&orgId=b'],['/ho-so/nang-luc','?tab=cv&tab=du-an'],['/workspace/cases/77777777-7777-4777-8777-777777777777','?followup=delete'],['//evil.example','']])
    expect(readRoute(path,query)).toEqual({kind:'not-found'});
});
test('external return URL and credentials never become a serialized destination',()=>{
  const href=routeHref(readRoute('/enterprise/overview','?returnTo=https://evil.example&token=private&password=private'));
  expect(href).toBe('/enterprise/overview');
  expect(routeHref(readRoute('/reset-password','?token=private'))).toBe('/reset-password');
});
test('Case followup and portal context survive a route round trip without granting permissions',()=>{
  const id='77777777-7777-4777-8777-777777777777';
  for(const route of [view('internship-case',{caseId:id,followup:'quality'}),view('school',{portalPage:'cooperations',orgId:id,termId:id,recordId:id}),view('internship',{section:'cases'}),view('student-invitations',{studentTab:'preferences',detailId:id})]){
    const url=new URL(routeHref(route),'https://app.example.test');expect(readRoute(url.pathname,url.search)).toEqual(route);
  }
});
