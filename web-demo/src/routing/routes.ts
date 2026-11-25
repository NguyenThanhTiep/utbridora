import type { PortalPage } from '../modules/cooperation/portal/navigation';
import { pagesFor } from '../modules/cooperation/portal/navigation';
import type { HrSection } from '../modules/internship/pages/InternshipWorkspace';
import type { CapabilityTab } from '../modules/student/pages/CapabilityPage';
import type { StudentResponseTab } from '../modules/student/pages/InvitationsPreferencesPage';

export type AuthScreen = 'login' | 'forgot' | 'reset' | 'activate';
export type ViewRoute = {
  kind: 'workspace'; screen: string; portalPage?: PortalPage; section?: HrSection;
  studentTab?: StudentResponseTab; capabilityTab?: CapabilityTab; detailId?: string; caseId?: string;
  followup?: 'policy' | 'quality' | 'close';
  orgId?:string; schoolId?:string; termId?:string; recordId?:string; cooperationId?:string; proposalId?:string;
};
export type AppRoute = ViewRoute | {kind:'home'} | {kind:'auth';auth:AuthScreen} | {kind:'not-found'};
const screenPaths:Record<string,string> = {
  account:'/tai-khoan', 'admin-overview':'/admin/overview', users:'/admin/users', organizations:'/admin/organizations',
  audit:'/admin/audit', 'matching-statistics':'/admin/matching-statistics', delegations:'/workspace/delegations',
  'assignment-management':'/workspace/assignments', 'school-preferences':'/school/preferences',
  'sent-mentor-invitations':'/enterprise/mentor-invitations', evaluations:'/workspace/evaluations',
  'internship-operations':'/workspace/operations', 'school-internship-cases':'/school/internship-cases',
  'mentor-invitations':'/workspace/mentor-invitations', taxonomy:'/danh-muc/ky-nang',
  'student-overview':'/tong-quan', 'student-profile':'/ho-so', 'student-opportunities':'/co-hoi',
  'student-tasks':'/thuc-tap/nhiem-vu', 'student-reports':'/thuc-tap/bao-cao-danh-gia',
};
const authPaths:Record<AuthScreen,string>={login:'/dang-nhap',forgot:'/quen-mat-khau',reset:'/reset-password',activate:'/accept-invitation'};
const capabilityNames:Record<CapabilityTab,string>={projects:'du-an',certificates:'chung-chi',cv:'cv',evidence:'minh-chung'};
const sections:HrSection[]=['overview','cohorts','groups','mentors','cases'];
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const scopeKeys=['orgId','schoolId','termId','recordId','cooperationId','proposalId'] as const;
const opaqueId=/^[a-zA-Z0-9][a-zA-Z0-9_-]{0,99}$/;
export const view = (screen:string, options:Omit<ViewRoute,'kind'|'screen'> = {}):ViewRoute => ({kind:'workspace',screen,...options});
export function routeHref(route:AppRoute):string {
  if(route.kind==='home')return '/';
  if(route.kind==='auth')return authPaths[route.auth];
  if(route.kind==='not-found')return '/404';
  if(route.screen==='internship-case')return '/workspace/cases/'+encodeURIComponent(route.caseId || '')+(route.followup?'?followup='+route.followup:'');
  if(route.screen==='demands' || route.screen==='school'){
    const q=new URLSearchParams();for(const k of scopeKeys)if(route[k])q.set(k,route[k]!);
    return '/'+(route.screen==='school'?'school':'enterprise')+'/'+(route.portalPage || 'overview')+(q.size?'?'+q:'');
  }
  if(route.screen==='internship')return '/workspace/internship/'+(route.section || 'overview');
  if(route.screen==='student-capability')return '/ho-so/nang-luc?tab='+capabilityNames[route.capabilityTab || 'projects'];
  if(route.screen==='student-invitations')return (route.studentTab==='preferences'?'/nguyen-vong':'/loi-moi')+(route.detailId?'/'+encodeURIComponent(route.detailId):'');
  const path=Object.hasOwn(screenPaths,route.screen)?screenPaths[route.screen]:undefined;
  return path ? path+(route.screen==='student-opportunities' && route.detailId?'/'+encodeURIComponent(route.detailId):'') : '/404';
}
/** Only known internal destinations are accepted. No redirect URL or token is read from query parameters. */
export function readRoute(pathname:string, search=''):AppRoute {
  const path=pathname.length>1?pathname.replace(/\/+$/,''):pathname,q=new URLSearchParams(search);
  if(path==='/')return {kind:'home'};
  for(const [auth,p] of Object.entries(authPaths))if(path===p)return {kind:'auth',auth:auth as AuthScreen};
  if(path==='/login')return {kind:'auth',auth:'login'};
  if(path==='/forgot-password')return {kind:'auth',auth:'forgot'};
  if(path==='/ho-so/ky-nang')return view('student-profile'); // The existing profile screen owns skills as well.
  if(path==='/ho-so/nang-luc'){
    if(q.getAll('tab').length>1)return {kind:'not-found'};
    const tab=q.get('tab') || 'du-an',entry=Object.entries(capabilityNames).find(([k,v])=>v===tab || k===tab);
    return entry?view('student-capability',{capabilityTab:entry[0] as CapabilityTab}):{kind:'not-found'};
  }
  const caseMatch=/^\/workspace\/cases\/([^/]+)$/.exec(path);
  if(caseMatch){
    const followup=q.get('followup');
    return uuid.test(caseMatch[1]) && q.getAll('followup').length<=1 && (!followup || ['policy','quality','close'].includes(followup))
      ?view('internship-case',{caseId:caseMatch[1].toLowerCase(),...(followup?{followup:followup as ViewRoute['followup']}:{})}):{kind:'not-found'};
  }
  const portal=/^\/(enterprise|school)\/([^/]+)$/.exec(path);
  if(portal && pagesFor(portal[1] as 'enterprise'|'school').some(p=>p.id===portal[2])){
    const options:Omit<ViewRoute,'kind'|'screen'>={};
    for(const k of scopeKeys){const value=q.get(k);if(q.getAll(k).length>1 || value!==null && !opaqueId.test(value))return {kind:'not-found'};if(value)options[k]=value;}
    return view(portal[1]==='school'?'school':'demands',{...options,portalPage:portal[2] as PortalPage});
  }
  const internship=/^\/workspace\/internship\/([^/]+)$/.exec(path);
  if(internship && sections.includes(internship[1] as HrSection))return view('internship',{section:internship[1] as HrSection});
  const detail=/^\/(co-hoi|loi-moi|nguyen-vong)(?:\/([^/]+))?$/.exec(path);
  if(detail){
    if(detail[2] && !uuid.test(detail[2]))return {kind:'not-found'};
    return view(detail[1]==='co-hoi'?'student-opportunities':'student-invitations',{
      ...(detail[2]?{detailId:detail[2].toLowerCase()}:{}),...(detail[1]!=='co-hoi'?{studentTab:detail[1]==='nguyen-vong'?'preferences':'invitations'}:{}),
    });
  }
  const entry=Object.entries(screenPaths).find(([,p])=>p===path);
  return entry?view(entry[0]):{kind:'not-found'};
}
