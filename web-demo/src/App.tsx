import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { api, send, login, logout, whoAmI, ApiError, type Me, type Grant } from './api';
import { Brand, EmptyState, Field, Icon, Notice, useAction, roleNames, permissionNames } from './components';
import { MatchingStatistics } from './modules/matching/MatchingStatistics';
import { AdminOverview, UserManagement, OrganizationManagement, AuditViewer } from './management';
import { useAdminSpotlight } from './adminEffects';
import { RotatingHeadline } from './auth/RotatingHeadline';
import { InternshipWorkspace } from './modules/internship/pages/InternshipWorkspace';
import type { HrSection } from './modules/internship/pages/InternshipWorkspace';
import { CaseEntry } from './modules/internship/pages/CaseEntry';
import { EvaluationsPage } from './modules/internship/pages/EvaluationsPage';
import { SchoolCaseLookup } from './modules/internship/pages/SchoolCaseLookup';
import { OperationsPage } from './modules/internship/pages/OperationsPage';
import { SchoolPreferenceReview } from './modules/internship/management/SchoolPreferenceReview';
import { SentMentorInvitations } from './modules/internship/management/SentMentorInvitations';
import { AssignmentManagementPage } from './modules/internship/management/AssignmentManagementPage';
import { canDiscoverWorkspace } from './modules/internship/access';

import { studentGrants } from './modules/student/access';
import { StudentOverviewPage } from './modules/student/pages/StudentOverviewPage';
import { MyProfilePage } from './modules/student/pages/MyProfilePage';
import { InvitationsPreferencesPage, type StudentResponseTab } from './modules/student/pages/InvitationsPreferencesPage';
import { OpportunitiesPage } from './modules/student/pages/OpportunitiesPage';
import { InternshipTasksPage } from './modules/student/pages/InternshipTasksPage';
import { CapabilityPage } from './modules/student/pages/CapabilityPage';
import { ReportsResultsPage } from './modules/student/pages/ReportsResultsPage';
import { StudentNavigation } from './modules/student/StudentNavigation';
import { MentorInvitations } from './modules/internship/pages/MentorInvitations';
import './modules/workflows.css';
// DEV08 đã nối màn hình danh mục kỹ năng vào khung ứng dụng hiện có để người kiểm thử mở được giao diện US-T4-06.
import { SkillTaxonomy } from './modules/taxonomy';
import { DemandWorkspace, SchoolWorkspace, PortalNavigation, portalPageTitle, canAuthorDemands, canLeaveDemand, type PortalPage } from './modules/cooperation';
import { SchoolDelegations } from './modules/delegations/SchoolDelegations';
import { RoleAccountMenu } from './RoleAccountMenu';
import { useSurfaceSpotlight } from './ui/useSurfaceSpotlight';
import { internshipSpotlightTargets, studentSpotlightTargets, workspaceFrames } from './ui/workspaceSurfaces';
import { useOrganizationNames } from './modules/useOrganizationNames';
import './auth/AuthEffects.css';
import { BrowserRouting, useBrowserRouting } from './routing/BrowserRouting';
import { view, routeHref, type ViewRoute } from './routing/routes';

const screens = [
  { id: 'matching-statistics', title: 'Thống kê Matching', description: 'Tra cứu số lượt đối chiếu hoàn tất trong nhật ký hệ thống.', icon: 'activity', permission: 'audit:global:read' },
  { id: 'school-preferences', title: 'Rà soát nguyện vọng', description: 'Ghi nhận rà soát ý định sinh viên trong phạm vi ủy nhiệm.', icon: 'graduation', permission: null },
  { id: 'sent-mentor-invitations', title: 'Lời mời Mentor đã gửi', description: 'Tra cứu, gửi lại và thu hồi lời mời do HR cấp.', icon: 'mail', permission: null },
  { id: 'assignment-management', title: 'Phân công thực tập', description: 'Xác nhận Coordinator, phân công HR, bàn giao Mentor và thu hồi quyền theo phạm vi.', icon: 'shield', permission: null },
  { id: 'evaluations', title: 'Đánh giá thực tập', description: 'Chấm rubric, công bố và xem kết quả theo quyền được phân công.', icon: 'graduation', permission: null },
  { id: 'internship-operations', title: 'Quản lý thực tập', description: 'Tiến độ, phối hợp và tổng kết thực tập theo phạm vi.', icon: 'briefcase', permission: null },
  { id: 'internship-case', title: 'Hồ sơ thực tập', description: 'Hồ sơ trong phạm vi được phân công.', icon: 'briefcase', permission: null },
  { id: 'school-internship-cases', title: 'Theo dõi hồ sơ thực tập', description: 'Tra cứu trạng thái hồ sơ trong phạm vi nhà trường được ủy quyền.', icon: 'briefcase', permission: null },
  { id: 'school', title: 'Hợp tác doanh nghiệp', description: 'Không gian phối hợp thực tập dành cho nhà trường.', icon: 'building', permission: null },
  { id: 'account', title: 'Tài khoản của tôi', description: 'Thông tin cá nhân, vai trò và bảo mật tài khoản.', icon: 'grid', permission: null },
  { id: 'mentor-invitations', title: 'Lời mời hướng dẫn', description: 'Xem và chấp thuận phân công hướng dẫn thực tập.', icon: 'mail', permission: null },
  { id: 'internship', title: 'Không gian thực tập', description: 'Theo dõi đợt và nhóm thực tập trong phạm vi HR/Mentor được phân công.', icon: 'briefcase', permission: null },
  { id: 'student-overview', title: 'Tổng quan', description: 'Lối vào nhanh tới hồ sơ và các bước tiếp theo của bạn.', icon: 'graduation', permission: 'student_profile:self:read', group: 'student' },
  { id: 'student-profile', title: 'Thông tin chung', description: 'Thông tin cơ bản trong hồ sơ sinh viên của bạn.', icon: 'user', permission: 'student_profile:self:read', group: 'student' },
  // S04 (SRS 14.1, /ho-so/nang-luc): dự án, chứng chỉ, CV và minh chứng.
  { id: 'student-capability', title: 'Năng lực', description: 'Dự án, chứng chỉ, CV và minh chứng trong hồ sơ của bạn.', icon: 'layers', permission: 'student_profile:self:read', group: 'student' },
  { id: 'student-opportunities', title: 'Cơ hội thực tập', description: 'Tìm cơ hội đã được nhà trường công bố và gửi nguyện vọng.', icon: 'briefcase', permission: 'student_profile:self:read', group: 'student' },
  // S06 (SRS 14.1): lời mời và nguyện vọng chung một màn, 2 tab.
  { id: 'student-invitations', title: 'Lời mời & nguyện vọng', description: 'Phản hồi lời mời, quyết định chia sẻ hồ sơ và theo dõi nguyện vọng đã gửi.', icon: 'mail', permission: 'student_profile:self:read', group: 'student' },
  // S07, S08 (SRS 14.1): giữ id 'student-tasks' cho S07 để không đổi điều hướng cũ.
  { id: 'student-tasks', title: 'Nhiệm vụ & sản phẩm', description: 'Vị trí thực tập, nhiệm vụ được giao, lịch hạn và bài nộp của bạn.', icon: 'calendar', permission: 'student_profile:self:read', group: 'student' },
  { id: 'student-reports', title: 'Báo cáo & đánh giá', description: 'Báo cáo tuần và kết quả đánh giá đã được nhà trường công bố.', icon: 'check-circle', permission: 'student_profile:self:read', group: 'student' },
  { id: 'demands', title: 'Nhu cầu thực tập', description: 'Quản lý bản nháp nhu cầu và tiêu chí tuyển thực tập của doanh nghiệp.', icon: 'briefcase', permission: 'cooperation:enterprise:create' },
  { id: 'admin-overview', title: 'Tổng quan quản trị', description: 'Theo dõi hoạt động và truy cập nhanh các chức năng quản trị.', icon: 'grid', permission: null },
  { id: 'users', title: 'Tài khoản & phân quyền', description: 'Quản lý thành viên và quyền truy cập trong hệ thống.', icon: 'users', permission: 'account:admin:status' },
  { id: 'delegations', title: 'Phân công quyền cán bộ', description: 'Cấp và thu hồi quyền trong từng trường và kỳ thực tập.', icon: 'shield', permission: null },
  { id: 'organizations', title: 'Tổ chức & lời mời', description: 'Kết nối các tổ chức và mời thành viên tham gia.', icon: 'building', permission: 'membership:admin:write' },
  { id: 'audit', title: 'Nhật ký hệ thống', description: 'Theo dõi và tra cứu các hoạt động quan trọng.', icon: 'activity', permission: 'audit:global:read' },
  // DEV08 cho phép quản trị viên toàn cục mở màn hình để kiểm thử; giao diện lập trình vẫn tự kiểm tra quyền ở máy chủ.
  { id: 'taxonomy', title: 'Danh mục kỹ năng', description: 'Quản lý cây kỹ năng chuẩn, tên gọi khác và tra cứu mã.', icon: 'layers', permission: 'taxonomy:skills:read' }
];
const internshipSections: { id: HrSection; label: string; icon: string }[] = [
  { id: 'overview', label: 'Tổng quan HR', icon: 'grid' },
  { id: 'cohorts', label: 'Đợt thực tập', icon: 'calendar' },
  { id: 'groups', label: 'Nhóm thực tập', icon: 'users' },
  { id: 'mentors', label: 'Người hướng dẫn', icon: 'user' }
];
const utilityScreens = ['school-preferences', 'sent-mentor-invitations', 'account', 'mentor-invitations', 'internship', 'internship-operations', 'evaluations', 'school-internship-cases', 'demands', 'school', 'taxonomy', 'assignment-management'];
function initials(name: string) {
  const words = name.replace(/\([^)]*\)/g, ' ').trim().split(/\s+/).filter(w => /[\p{L}\p{N}]/u.test(w));
  return (words.length > 1 ? words[0].charAt(0) + words[words.length - 1].charAt(0) : words[0]?.slice(0, 2) || 'UB').toUpperCase();
}

function isMixedRestricted(grants: Grant[]): boolean {
  return grants.some(grant =>
    (grant.role === 'ADMIN' && grant.organizationId === null)
    || (grant.role === 'STUDENT' && !!grant.organizationId));
}

function defaultPortalSide(grants: Grant[]): 'enterprise' | 'school' | null {
  if (isMixedRestricted(grants)) return null;
  const enterprise = grants.some(canAuthorDemands);
  const school = grants.some(grant => grant.role === 'SCHOOL_STAFF' && !!grant.organizationId);
  return enterprise === school ? null : enterprise ? 'enterprise' : 'school';
}

function isInternshipShell(grants: Grant[]): boolean {
  if (defaultPortalSide(grants) || isMixedRestricted(grants)) return false;
  return grants.some(grant => grant.role === 'ENTERPRISE' && !!grant.organizationId);
}

function defaultDestination(user:Me, grants:Grant[]):ViewRoute {
  const side=defaultPortalSide(grants);
  return side ? view(side==='school'?'school':'demands',{portalPage:'overview'})
    : isInternshipShell(grants) && canDiscoverWorkspace(user) ? view('internship',{section:'overview'})
    : studentGrants(grants).length ? view('student-overview') : view('account');
}
export default function App() { return <BrowserRouting><AppShell /></BrowserRouting>; }
function AppShell() {
  const router=useBrowserRouting()!,route=router.route;
  const destination=route.kind==='workspace'?route:view('account');
  const screen=route.kind==='not-found'?'not-found':destination.screen;
  const portalPage=destination.portalPage || 'overview';
  const caseId=destination.caseId || '';
  const studentTab=destination.studentTab || 'invitations';
  const internshipSection=destination.section || 'overview';
  const authScreen=route.kind==='auth'?route.auth:'login';
  const requestedDestination=useRef<ViewRoute|null>(null);
  const [me, setMe] = useState<Me | null>(null);
  const [grants, setGrants] = useState<Grant[]>([]);
  const [initializing, setInitializing] = useState(true);
  const [token, setToken] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') || '');
  const [sessionMessage, setSessionMessage] = useState('');
  const [context, setContext] = useState('0');
  const [organizationForm, setOrganizationForm] = useState<'invite' | null>(null);
  const [mobileOpen, setMobileOpen] = useState(false);
  const [justSignedIn, setJustSignedIn] = useState(false);
  const [justSignedOut, setJustSignedOut] = useState(false);
  const [internshipTopbar, setInternshipTopbar] = useState<HTMLDivElement | null>(null);
  const [internshipScrolled, setInternshipScrolled] = useState(false);
  const [internshipMenuOpen, setInternshipMenuOpen] = useState(false);
  const [internshipKind, setInternshipKind] = useState<'HR' | 'MENTOR' | null>(null);
  const generation = useRef(0);
  const heading = useRef<HTMLHeadingElement>(null);
  const sidebar = useRef<HTMLElement>(null);
  const menuButton = useRef<HTMLButtonElement>(null);
  const [accountOpen, setAccountOpen] = useState(false);
  const accountMenu = useRef<HTMLDivElement>(null);
  const action = useAction();
  const permissions = new Set(grants.filter(grant => grant.role === 'ADMIN' && grant.organizationId === null).flatMap(grant => grant.permissions));
  const isAdmin = grants.some(grant => grant.role === 'ADMIN' && grant.organizationId === null);
  const schools = studentGrants(grants);
  const enterpriseInternship = !!me && canDiscoverWorkspace(me);
  const schoolGradeAccess = grants.some(g => g.role === 'SCHOOL_STAFF' && !!g.organizationId
    && (g.permissions.includes('t3:grades:publish') || g.permissions.includes('t3:result_policy:manage')));
  const schoolCaseAccess = grants.some(g => g.role === 'SCHOOL_STAFF' && !!g.organizationId && g.permissions.includes('t3:workspace:read'));
  const nestedEvaluations = enterpriseInternship || schoolGradeAccess;
  const menus = screens.filter(item => item.id === 'admin-overview' ? isAdmin : item.id === 'internship-case' ? !!caseId
    : item.id === 'school-preferences' ? grants.some(g => g.role === 'SCHOOL_STAFF' && !!g.organizationId && g.permissions.includes('student:preference:school:review'))
    : item.id === 'sent-mentor-invitations' ? grants.some(g => g.role === 'ENTERPRISE' && !!g.organizationId && g.permissions.includes('t3:mentor:invite'))
    : item.id === 'assignment-management' ? grants.some(g => !!g.organizationId && (g.role === 'SCHOOL_STAFF' && g.permissions.includes('t3:coordinator:confirm') || g.role === 'ENTERPRISE' && (g.permissions.includes('t3:hr:manage') || g.permissions.includes('t3:mentors:manage'))))
    : item.id === 'school-internship-cases' ? schoolCaseAccess
    : item.id === 'internship' ? enterpriseInternship
    : item.id === 'internship-operations' ? grants.some(grant => ['SCHOOL_STAFF','ENTERPRISE'].includes(grant.role) && !!grant.organizationId && grant.permissions.includes('t3:workspace:read'))
    : item.id === 'evaluations' ? grants.some(grant => ['SCHOOL_STAFF','ENTERPRISE','STUDENT'].includes(grant.role) && !!grant.organizationId)
    : item.id === 'school' ? grants.some(grant => grant.role === 'SCHOOL_STAFF' && !!grant.organizationId)
    : item.id === 'demands' ? grants.some(canAuthorDemands)
    : item.id === 'delegations' ? permissions.has('membership:admin:write') || grants.some(g => g.role === 'SCHOOL_STAFF' && g.permissions.includes('school:delegation:manage'))
    : item.permission === null || (item.id === 'taxonomy' ? grants.some(grant => grant.permissions.includes('taxonomy:skills:read')) : (item.group === 'student' ? schools.length > 0 : permissions.has(item.permission))));
  const current = screens.find(item => item.id === screen) || {id:'not-found',title:'Không tìm thấy trang',description:'Đường dẫn không tương ứng với một màn hình của hệ thống.',group:undefined};
  const rolePortalSide = defaultPortalSide(grants);
  const internshipShell = isInternshipShell(grants);
  const t5Shell = !!rolePortalSide || internshipShell || screen === 'demands' || screen === 'school';
  const enterprisePerms = new Set(
    grants.filter(grant => grant.role === 'ENTERPRISE' && !!grant.organizationId).flatMap(grant => grant.permissions),
  );
  const canHr = enterprisePerms.has('t3:mentor:invite') || enterprisePerms.has('t3:grade_visibility:manage');
  const canMentor = enterprisePerms.has('internship:enterprise:evaluate')
    || enterprisePerms.has('t3:task:create') || enterprisePerms.has('t3:report:review');
  const hrMentorBadge = canHr && !canMentor ? 'HR' : canMentor && !canHr ? 'MT' : 'DN';
  const hrMentorLabel = canHr && !canMentor ? 'Tài khoản HR' : canMentor && !canHr ? 'Tài khoản Mentor' : 'HR & Mentor';
  const hrMentorKicker = canHr && !canMentor ? 'QUẢN LÝ NHÂN SỰ (HR)'
    : canMentor && !canHr ? 'NGƯỜI HƯỚNG DẪN (MENTOR)' : 'ĐIỀU PHỐI THỰC TẬP';
  const portalScreen = screen === 'demands' || screen === 'school';
  const nativeInternship = ['school-preferences', 'sent-mentor-invitations', 'internship', 'internship-case', 'school-internship-cases', 'internship-operations', 'evaluations', 'mentor-invitations', 'assignment-management'].includes(screen);
  const portalSide = screen === 'demands' ? 'enterprise' : screen === 'school' ? 'school' : rolePortalSide;
  const adminShell = isAdmin && !portalSide;
  const studentShell = !adminShell && !t5Shell && menus.some(item => item.group === 'student');
  const internshipMotion = nativeInternship && !studentShell && !adminShell;
  const adminSurface = useAdminSpotlight(adminShell);
  const workspaceSurface = useSurfaceSpotlight<HTMLElement>(
    (t5Shell && !portalScreen && !adminShell && !nativeInternship) || studentShell || internshipMotion,
    studentShell ? studentSpotlightTargets : internshipMotion ? internshipSpotlightTargets : workspaceFrames + ', .t3-report-row-btn',
    'portal',
  );
  const portalEntry = portalSide === 'enterprise' ? 'demands' : 'school';
  const visibleInternshipSections: typeof internshipSections = !enterpriseInternship ? [] : internshipKind === 'MENTOR'
    ? [{ id: 'overview', label: 'Nhóm được giao', icon: 'users' }]
    : internshipKind === 'HR' ? [...internshipSections, { id: 'cases', label: 'Theo dõi Case', icon: 'briefcase' }]
    : internshipSections;
  const internshipActive = screen === 'internship' || screen === 'school-internship-cases' || screen === 'evaluations' && nestedEvaluations;
  const screenTitle = portalSide && portalScreen ? portalPageTitle(portalSide, portalPage)
    : screen === 'internship' ? visibleInternshipSections.find(item => item.id === internshipSection)?.label || current.title : current.title;
  const pageKicker = screen === 'account' || screen === 'mentor-invitations' ? 'KHÔNG GIAN CÁ NHÂN'
    : current.group === 'student' ? 'KHÔNG GIAN SINH VIÊN' : 'QUẢN LÝ THỰC TẬP';

  useEffect(() => {
    const authTitles: Record<string, string> = { login: 'Đăng nhập', forgot: 'Quên mật khẩu', reset: 'Đặt lại mật khẩu', activate: 'Kích hoạt tài khoản' };
    document.title = (me ? screenTitle : authTitles[authScreen] || 'Không gian kết nối') + ' | UT.Bridora';
  }, [me?.userId, screenTitle, authScreen]);

  useEffect(() => {
    let active = true;
    const currentGeneration = ++generation.current;
    void (async () => {
      try {
        const user = await whoAmI();
        const effective = await api<Grant[]>('/auth/me/permissions');
        if (active && generation.current === currentGeneration) {
          setMe(user); setGrants(effective);

        }
      } catch (error) {
        if (active && !(error instanceof ApiError && error.status === 401)) action.setMessage('Không thể tải phiên. Vui lòng thử đăng nhập lại.');
      } finally { if (active) setInitializing(false); }
    })();
    const expired = () => {
      generation.current += 1;
      setJustSignedIn(false); setJustSignedOut(false); setMe(null); setGrants([]); setMobileOpen(false);
      setInternshipKind(null); setInternshipMenuOpen(false);
      setSessionMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.');
    };
    const onNavigate = (event: Event) => {
      const target = (event as CustomEvent<string>).detail;
      if (target && router.go(view(target))) setMobileOpen(false);
    };
    window.addEventListener('session-expired', expired);
    window.addEventListener('bridora:navigate', onNavigate);
    return () => { active = false; window.removeEventListener('session-expired', expired); window.removeEventListener('bridora:navigate', onNavigate); };
  }, []);

  useEffect(()=>{
    if(!me && route.kind==='workspace')requestedDestination.current=route;
    if(initializing)return;
    if(route.kind==='home' || me && route.kind==='auth' && !token)
      router.go(me?defaultDestination(me,grants):{kind:'auth',auth:'login'},{replace:true,guard:false});
  },[initializing,me,grants,route,token,router.go]);
  useEffect(()=>{
    if(screen==='internship' && internshipKind==='MENTOR' && internshipSection!=='overview')
      router.go(view('internship',{section:'overview'}),{replace:true,guard:false});
  },[screen,internshipKind,internshipSection,router.go]);
  useEffect(() => {
    const frame = requestAnimationFrame(() => heading.current?.focus({ preventScroll: true }));
    return () => cancelAnimationFrame(frame);
  }, [screen, portalPage, internshipSection, caseId, destination.detailId, me?.userId]);
  useEffect(()=>{setMobileOpen(false);setAccountOpen(false);},[route]);
  useEffect(() => {
    const media = window.matchMedia('(min-width: 901px)');
    const changed = () => { if (media.matches) setMobileOpen(false); };
    media.addEventListener('change', changed);
    return () => media.removeEventListener('change', changed);
  }, []);
  useEffect(() => {
    if (screen !== 'internship') return;
    const update = () => setInternshipScrolled(previous => window.scrollY > 120 ? true : window.scrollY < 16 ? false : previous);
    update();
    window.addEventListener('scroll', update, { passive: true });
    return () => window.removeEventListener('scroll', update);
  }, [screen]);
  useEffect(() => {
    if (!mobileOpen) return;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const controls = () => Array.from(sidebar.current?.querySelectorAll<HTMLButtonElement | HTMLAnchorElement>('button:not(:disabled), a[href]') || [])
      .filter(node => node.tabIndex >= 0 && node.getClientRects().length > 0 && getComputedStyle(node).visibility !== 'hidden');
    controls()[0]?.focus();
    const keydown = (event: KeyboardEvent) => {
      if (event.key === 'Escape') { event.preventDefault(); setMobileOpen(false); }
      if (event.key !== 'Tab') return;
      const nodes = controls(), first = nodes[0], last = nodes[nodes.length - 1];
      if (event.shiftKey && document.activeElement === first) { event.preventDefault(); last?.focus(); }
      else if (!event.shiftKey && document.activeElement === last) { event.preventDefault(); first?.focus(); }
    };
    document.addEventListener('keydown', keydown);
    return () => {
      document.body.style.overflow = oldOverflow;
      document.removeEventListener('keydown', keydown);
      menuButton.current?.focus({ preventScroll: true });
    };
  }, [mobileOpen]);
  useEffect(() => setAccountOpen(false), [screen, me?.userId]);
  useEffect(() => {
    if (!accountOpen) return;
    const pointer = (event: MouseEvent) => { if (!accountMenu.current?.contains(event.target as Node)) setAccountOpen(false); };
    const keydown = (event: KeyboardEvent) => {
      if (event.key !== 'Escape') return;
      setAccountOpen(false); accountMenu.current?.querySelector<HTMLButtonElement>('.profile-shortcut')?.focus();
    };
    document.addEventListener('mousedown', pointer); document.addEventListener('keydown', keydown);
    return () => { document.removeEventListener('mousedown', pointer); document.removeEventListener('keydown', keydown); };
  }, [accountOpen]);

  const navigate = (next:string) => {
    if(router.go(next===screen?destination:view(next,next==='demands'?{portalPage:'demands'}:{}))){
      if(next!=='organizations')setOrganizationForm(null);setMobileOpen(false);
    }
  };
  const navigatePortal = (next:PortalPage) => {
    if(router.go(view(portalEntry,{portalPage:next,...(destination.screen===portalEntry?{orgId:destination.orgId,schoolId:destination.schoolId,termId:destination.termId,cooperationId:['proposals','placements'].includes(next)?destination.cooperationId:undefined}:{})})))setMobileOpen(false);
  };
  const navigateInternship=(next:HrSection)=>{
    if(router.go(view('internship',{section:next}))){setInternshipMenuOpen(true);setMobileOpen(false);}
  };
  const setStudentTab=(tab:StudentResponseTab)=>{router.go(view('student-invitations',{studentTab:tab}));};
  useEffect(() => {
    if (!justSignedIn) return;
    const timer = setTimeout(() => setJustSignedIn(false), 2600);
    return () => clearTimeout(timer);
  }, [justSignedIn]);
  useEffect(() => {
    if (!justSignedOut) return;
    const timer = setTimeout(() => setJustSignedOut(false), 2600);
    return () => clearTimeout(timer);
  }, [justSignedOut]);
  const signOut = async () => {
    if (!canLeaveDemand()) return;
    await logout(); generation.current += 1;
    setJustSignedIn(false); setJustSignedOut(true); setMe(null); setGrants([]); setContext('0'); setMobileOpen(false); requestedDestination.current=null;setToken('');
    setInternshipKind(null); setInternshipMenuOpen(false);
    setSessionMessage(''); router.go({kind:'auth',auth:'login'},{replace:true,guard:false});
  };
  useEffect(() => {
    const onLogout = () => { void action.run(signOut); };
    window.addEventListener('bridora:logout', onLogout);
    return () => window.removeEventListener('bridora:logout', onLogout);
  });
  const signedIn = async (user: Me) => {
    const effective = await api<Grant[]>('/auth/me/permissions');
    generation.current += 1;
    setJustSignedOut(false); setJustSignedIn(true);
    setGrants(effective); setMe(user);
    setInternshipKind(null);
    router.go(route.kind==='workspace'?route:requestedDestination.current || defaultDestination(user,effective),{replace:true,guard:false});
    requestedDestination.current=null;setContext('0');setSessionMessage('');
  };
  const navigateAuth = (next:string) => { if(['login','forgot','reset','activate'].includes(next) && router.go({kind:'auth',auth:next as 'login'|'forgot'|'reset'|'activate'})){setJustSignedOut(false);action.setMessage('');setSessionMessage('');} };

  if (initializing || route.kind==='home' || me && route.kind==='auth' && !token) return <main className="startup"><Brand /><div className="startup-status" role="status"><span className="spinner" />Đang chuẩn bị không gian của bạn…</div></main>;
  if(!me && route.kind==='not-found')return <main className="content"><section className="panel stack"><h1>Không tìm thấy trang</h1><p>Đường dẫn không tương ứng với một màn hình của hệ thống.</p><button onClick={()=>router.go({kind:'auth',auth:'login'})}>Đến trang đăng nhập</button></section></main>;
  if (!me) return <>
    {justSignedOut && <SessionFeedback variant="logout" title="Đã đăng xuất an toàn" subtitle="Phiên làm việc đã được đóng" />}
    <AuthLayout screen={authScreen} justSignedOut={justSignedOut}><AuthForm screen={authScreen} token={token} clearToken={() => setToken('')}
      navigate={navigateAuth} signedIn={signedIn} />
      <div className={sessionMessage ? 'auth-warning' : undefined}>
        <Notice message={sessionMessage || action.message} tone={sessionMessage ? 'info' : action.error ? 'error' : 'info'} />
      </div>
    </AuthLayout>
  </>;

  // A mail link can be opened in a browser which is already signed in.
  // Keep its token in memory and provide an explicit session switch.
  if (token && ['reset', 'activate'].includes(authScreen)) return <AuthLayout screen={authScreen}>
    <span className="auth-form-icon"><Icon name="mail" size={26} /></span>
    <h2 className="auth-title">Tiếp tục với liên kết email</h2>
    <p className="auth-description">Bạn đang đăng nhập bằng tài khoản <strong>{me.email}</strong>. Đăng xuất trước khi sử dụng liên kết này.</p>
    <Notice message={action.message} tone={action.error ? 'error' : 'info'} />
    <button className="full" disabled={action.busy} onClick={() => void action.run(async () => {
      await logout(); generation.current += 1; setJustSignedIn(false); setMe(null); setGrants([]);
    })}>Đăng xuất và mở liên kết<Icon name="arrow-right" size={18} /></button>
    <button className="text-button full" disabled={action.busy} onClick={() => { setToken(''); router.go(defaultDestination(me,grants),{replace:true,guard:false}); }}>Về tài khoản của tôi</button>
  </AuthLayout>;

  return <div className={'workspace' + (studentShell ? ' workspace-student' : '') + (adminShell ? ' workspace-admin' : '') + (t5Shell ? ' workspace-t5' : '') + (portalSide ? ' workspace-role-portal' : '') + (portalScreen ? ' workspace-t5-portal' : '') + (internshipShell ? ' workspace-t5-internship' : '') + (nativeInternship ? ' workspace-native-t3' : '') + ((t5Shell && !portalScreen && !nativeInternship) || internshipMotion ? ' has-workspace-motion' : '') + (justSignedIn ? ' is-just-signed-in' : '')}>
    {justSignedIn && <SessionFeedback variant="login" title="Đăng nhập thành công" subtitle={`Chào mừng ${me.displayName}`} />}
    <a className="skip-link" href="#main-content">Bỏ qua điều hướng</a>
    {mobileOpen && <button className="sidebar-backdrop" aria-label="Đóng phần nền menu" onClick={() => setMobileOpen(false)} tabIndex={-1} />}
    <aside className={'sidebar' + (mobileOpen ? ' is-open' : '')} ref={sidebar} id="main-navigation">
      <div className="sidebar-brand"><a href={routeHref(defaultDestination(me,grants))} aria-label="UT.Bridora trang chủ">{adminShell ? <span className="admin-brand"><span>U</span><span><b>UT.BRIDORA</b><small>Quản trị hệ thống</small></span></span> : <Brand inverse={t5Shell || studentShell} />}</a>
        <button className="btn-icon sidebar-close" aria-label="Đóng menu điều hướng" onClick={() => setMobileOpen(false)}><Icon name="close" /></button></div>
      <nav aria-label="Điều hướng chính">
        {adminShell ? <>
          <p className="nav-group-label">QUẢN TRỊ HỆ THỐNG</p>
          {['admin-overview', 'users', 'organizations', 'taxonomy', 'audit', 'delegations'].map(id => menus.find(item => item.id === id)).filter(item => item !== undefined).map(item => <button key={item.id}
            className={'nav' + (screen === item.id ? ' active' : '')} aria-current={screen === item.id ? 'page' : undefined}
            onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
          <p className="nav-group-label nav-group-utilities">TÀI KHOẢN & TIỆN ÍCH</p>
          {menus.filter(item => !['admin-overview', 'users', 'organizations', 'taxonomy', 'audit', 'delegations'].includes(item.id)).map(item => <button key={item.id}
            className={'nav' + (screen === item.id ? ' active' : '')} aria-current={screen === item.id ? 'page' : undefined}
            onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
        </> : studentShell ? <StudentNavigation items={menus} activeScreen={screen} navigate={navigate} /> : portalSide === 'school' ? (
          <PortalNavigation
            side="school"
            page={portalScreen ? portalPage : undefined}
            navigate={navigatePortal}
            extraItems={menus.filter(item => item.id !== portalEntry && item.group !== 'student')}
            activeScreen={portalScreen ? undefined : screen}
            onNavigateScreen={navigate}
          />
        ) : internshipShell ? <>
          <div className="portal-navigation is-internship">
            <p className="nav-group-label">KHÔNG GIAN THỰC TẬP</p>
            {menus.filter(item => ['internship', 'internship-operations', 'evaluations', 'mentor-invitations'].includes(item.id)).map(item => <button key={item.id}
              className={'nav' + (screen === item.id ? ' active' : '')} aria-current={screen === item.id ? 'page' : undefined}
              onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
            {screen === 'internship' && <div className="internship-submenu">{visibleInternshipSections.map(section => <button key={section.id}
              className={'nav internship-subnav' + (internshipSection === section.id ? ' active' : '')}
              aria-current={internshipSection === section.id ? 'page' : undefined}
              onClick={() => navigateInternship(section.id)}>
              <Icon name={section.icon} size={16} /><span>{section.label}</span></button>)}</div>}
          </div>
          <p className="nav-group-label">TÀI KHOẢN & TIỆN ÍCH</p>
          {menus.filter(item => !['internship', 'internship-operations', 'evaluations', 'mentor-invitations'].includes(item.id)).map(item => <button key={item.id}
            className={'nav' + (screen === item.id ? ' active' : '')} aria-current={screen === item.id ? 'page' : undefined}
            onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
        </> : <>
        {portalSide && <PortalNavigation side={portalSide} page={portalScreen ? portalPage : undefined} navigate={navigatePortal} />}
        <p className="nav-group-label">{portalSide ? 'TÀI KHOẢN & TIỆN ÍCH' : 'KHÔNG GIAN CỦA BẠN'}</p>
        {menus.filter(item => utilityScreens.includes(item.id) && item.id !== 'school-internship-cases'
          && !(item.id === 'evaluations' && nestedEvaluations) && (!portalSide || item.id !== portalEntry)).map(item =>
          item.id === 'internship' ? <div key={item.id}>
            <button className={'nav internship-parent' + (internshipActive ? ' active' : '')}
              aria-expanded={internshipMenuOpen} aria-controls="internship-submenu"
              onClick={() => {
                if (!internshipActive && !canLeaveDemand()) return;
                setInternshipMenuOpen(open => !open);
                if (!internshipMenuOpen && !internshipActive) {
                  navigate(enterpriseInternship ? 'internship' : schoolCaseAccess ? 'school-internship-cases' : 'evaluations');
                }
              }}>
              <Icon name={item.icon} /><span>{item.title}</span><Icon name="chevron-down" size={15} />
            </button>
            {internshipMenuOpen && <div id="internship-submenu" className="internship-submenu">
              {visibleInternshipSections.map(section => <button key={section.id}
                className={'nav internship-subnav' + (screen === 'internship' && internshipSection === section.id ? ' active' : '')}
                aria-current={screen === 'internship' && internshipSection === section.id ? 'page' : undefined}
                onClick={() => navigateInternship(section.id)}>
                <Icon name={section.icon} size={16} /><span>{section.label}</span>
              </button>)}
              {menus.filter(child => (child.id === 'evaluations' && nestedEvaluations) || child.id === 'school-internship-cases')
                .map(child => <button key={child.id}
                  className={'nav internship-subnav' + (screen === child.id ? ' active' : '')}
                  aria-current={screen === child.id ? 'page' : undefined} onClick={() => navigate(child.id)}>
                  <Icon name={child.icon} size={16} /><span>{child.title}</span>
                </button>)}
            </div>}
          </div> : <button key={item.id} className={'nav' + (screen === item.id ? ' active' : '')}
            aria-current={screen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
        {menus.some(item => item.group === 'student') && <p className="nav-group-label student-nav-group">SINH VIÊN</p>}
        {menus.filter(item => item.group === 'student').map(item => <button key={item.id} className={'nav' + (screen === item.id ? ' active' : '')}
          aria-current={screen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
        {menus.some(item => !utilityScreens.includes(item.id) && item.group !== 'student') && <p className="nav-group-label nav-group-admin">QUẢN TRỊ HỆ THỐNG</p>}
        {menus.filter(item => !utilityScreens.includes(item.id) && item.group !== 'student').map(item => <button key={item.id} className={'nav' + (screen === item.id ? ' active' : '')}
          aria-current={screen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
        </>}
      </nav>
      {internshipShell && (
        <div className="internship-sidebar-role-badge" aria-hidden="true">
          <strong>DOANH NGHIỆP · {hrMentorBadge}</strong>
          <span>{hrMentorKicker}</span>
        </div>
      )}
      <div className="sidebar-bottom">
        <div className="sidebar-user"><span className="avatar">{initials(me.displayName)}</span><div><b>{me.displayName}</b></div></div>
      </div>
      {!t5Shell && !adminShell && !studentShell && <p className="sidebar-caption">Nhà trường · Doanh nghiệp · Sinh viên</p>}
    </aside>

    <div className="workspace-main">
      <header className={'topbar' + (!nativeInternship && (studentShell || portalSide) ? ' workspace-topbar' : '') + (t5Shell && !nativeInternship ? ` is-portal-topbar is-${portalSide || 'internship'}-topbar` : '')
        + (screen === 'internship' ? ' t3-topbar' + (internshipScrolled ? ' is-condensed' : '') : '')}>
        {screen === 'internship' ? <><button className="btn-icon menu-toggle t3-topbar-menu" ref={menuButton}
          aria-label="Mở menu điều hướng" aria-expanded={mobileOpen} aria-controls="main-navigation"
          onClick={() => setMobileOpen(previous => !previous)}><Icon name="menu" /></button>
          <div className="t3-topbar-slot" ref={setInternshipTopbar} /></>
        : <div className="topbar-location"><button className="btn-icon menu-toggle" ref={menuButton} aria-label="Mở menu điều hướng"
          aria-expanded={mobileOpen} aria-controls="main-navigation" onClick={() => setMobileOpen(previous => !previous)}><Icon name="menu" /></button>
          {!nativeInternship && (studentShell || portalSide) ? <div className="topbar-heading"><p className="topbar-kicker">{portalSide ? portalSide === 'school' ? 'KHÔNG GIAN NHÀ TRƯỜNG' : 'KHÔNG GIAN DOANH NGHIỆP' : pageKicker}</p><h1 className="topbar-title" ref={heading} tabIndex={-1}>{screenTitle}</h1></div>
            : adminShell ? <div className="admin-location"><small>CỔNG QUẢN TRỊ HỆ THỐNG</small><strong>{screenTitle}</strong></div>
            : internshipShell ? <div className="portal-topbar-identity"><span className="portal-organization-icon"><Icon name="briefcase" size={22} /></span><div><small>KHÔNG GIAN THỰC TẬP · {hrMentorBadge}</small><strong>{screenTitle}</strong></div></div>
            : <><span className="topbar-home"><Icon name="layers" size={17} />Không gian làm việc</span><Icon name="chevron-right" size={14} /><span className="topbar-current">{screenTitle}</span></>}</div>}
        {screen !== 'internship' && <div className="topbar-right">
          {studentShell ? <>
            <span className="topbar-date"><Icon name="calendar" size={16} />{new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())}</span>
            <div className="account-menu-wrap" ref={accountMenu}>
              <button className="profile-shortcut workspace-account-trigger" aria-label="Mở menu tài khoản" aria-expanded={accountOpen}
                aria-controls="account-menu" onClick={() => setAccountOpen(open => !open)}>
                <span className="avatar avatar-small">SV</span><span className="workspace-account-label" title={me.displayName}>Sinh viên</span><Icon name="chevron-down" size={14} className="workspace-account-caret" /></button>
              {accountOpen && <div className="account-menu role-menu-popover" id="account-menu" role="region" aria-label="Menu Sinh viên">
                <div className="role-menu-header"><span className="role-menu-header-avatar">SV</span><div className="role-menu-header-meta">
                  <span className="role-menu-header-kicker">CỔNG SINH VIÊN</span>
                  <p className="account-menu-user"><b>{me.displayName}</b><small>{me.email}</small></p>
                </div></div>
                <div className="role-menu-list"><button type="button" className="role-menu-item" onClick={() => { setAccountOpen(false); navigate('student-overview'); }}><Icon name="grid" size={16} />Tổng quan</button>
                <div className="role-menu-divider" aria-hidden="true" />
                <button type="button" className="account-menu-logout role-menu-item is-danger" disabled={action.busy} onClick={() => { setAccountOpen(false); void action.run(signOut); }}><Icon name="logout" size={16} />Đăng xuất</button></div>
              </div>}
            </div>
          </> : portalSide ? <>
            <span className="topbar-date"><Icon name="calendar" size={16} />{new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Ho_Chi_Minh' }).format(new Date())}</span>
            <RoleAccountMenu
              badge={portalSide === 'school' ? 'NT' : 'DN'}
              label={portalSide === 'school' ? 'Nhà trường' : 'Doanh nghiệp'}
              kicker={portalSide === 'school' ? 'CỔNG NHÀ TRƯỜNG' : 'CỔNG DOANH NGHIỆP'}
              title={me.displayName}
              email={me.email}
              variant="topbar"
              onOverview={!portalScreen || portalPage !== 'overview' ? () => navigatePortal('overview') : undefined}
              onAccount={() => navigate('account')}
              onLogout={() => void action.run(signOut)}
              logoutDisabled={action.busy}
            />
          </> : internshipShell ? <>
            <RoleAccountMenu
              badge={hrMentorBadge}
              label={hrMentorLabel}
              kicker={hrMentorKicker}
              title={me.displayName}
              email={me.email}
              variant="topbar"
              onOverview={screen !== 'internship' && menus.some(item => item.id === 'internship') ? () => navigate('internship') : undefined}
              overviewLabel="Không gian thực tập"
              onAccount={() => navigate('account')}
              onLogout={() => void action.run(signOut)}
              logoutDisabled={action.busy}
            />
          </> : adminShell ? <>
            <button className="profile-shortcut" aria-label="Mở tài khoản của tôi" onClick={() => navigate('account')}><span className="avatar avatar-small">{initials(me.displayName)}</span><span>{me.displayName}</span></button>
            <RoleAccountMenu
              badge="QT"
              label="Quản trị"
              kicker="QUẢN TRỊ HỆ THỐNG"
              title={me.displayName}
              email={me.email}
              variant="topbar"
              onOverview={() => navigate('admin-overview')}
              overviewLabel="Tổng quan quản trị"
              onAccount={() => navigate('account')}
              onLogout={() => void action.run(signOut)}
              logoutDisabled={action.busy}
            />
          </> : <>
            <span className="topbar-date"><Icon name="calendar" size={16} />{new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Bangkok' }).format(new Date())}</span>
            <RoleAccountMenu
              badge={initials(me.displayName)}
              label={me.displayName}
              kicker="TÀI KHOẢN CÁ NHÂN"
              title={me.displayName}
              email={me.email}
              variant="topbar"
              onOverview={menus.some(item => item.id === 'student-overview') ? () => navigate('student-overview') : undefined}
              onAccount={() => navigate('account')}
              onLogout={() => void action.run(signOut)}
              logoutDisabled={action.busy}
            />
          </>}
        </div>}
      </header>
      <main className="content" id="main-content" ref={adminShell ? adminSurface : workspaceSurface}>
        {(!studentShell && !portalSide || nativeInternship) && <div key={portalScreen ? 'portal-hdr' : 'hdr:' + screen} className="page-header"><div><p className="section-kicker">{nativeInternship ? 'QUẢN LÝ THỰC TẬP' : screen === 'account' ? 'KHÔNG GIAN CÁ NHÂN' : screen === 'demands' || screen === 'school' ? current.title : current.group === 'student' ? 'KHÔNG GIAN SINH VIÊN' : 'QUẢN TRỊ NỀN TẢNG'}</p>
          <h1 ref={heading} tabIndex={-1}>{nativeInternship ? screenTitle : current.title}</h1><p className="page-description">{current.description}</p></div>
          <span className="workspace-label"><span className="live-dot" />UT.Bridora</span></div>}
        <Notice message={action.message} tone={action.error ? 'error' : 'info'} />
        {portalScreen && menus.some(item => item.id === screen) ? (
          screen === 'demands'
            ? <DemandWorkspace key={me.userId} grants={grants} page={portalPage} onNavigate={navigatePortal} viewerId={me.userId} user={{ displayName: me.displayName, email: me.email }} />
            : <SchoolWorkspace key={me.userId} grants={grants} page={portalPage} onNavigate={navigatePortal} viewerId={me.userId} user={{ displayName: me.displayName, email: me.email }} />
        ) : (
          <div key={screen} className={'app-page-stage' + (!adminShell && !studentShell && portalSide === 'school' && (screen === 'delegations' || screen === 'taxonomy') ? ' school-portal-subview' : '')}>
            {screen === 'account' && me.memberships.some(m => m.role === 'ENTERPRISE') && !grants.some(canAuthorDemands) && !internshipShell
              && <Notice message="Tài khoản có vai trò doanh nghiệp nhưng chưa được cấp quyền quản lý nhu cầu. Nhờ quản trị viên kiểm tra trạng thái xác minh tổ chức và liên kết vai trò." />}
            {route.kind==='not-found' ? <section className="panel"><EmptyState icon="alert" title="Không tìm thấy trang" description="Chọn một chức năng trong menu để tiếp tục." /></section> : !menus.some(item => item.id === screen) ? <section className="panel"><EmptyState icon="lock" title="Không có quyền truy cập" description="Chọn một chức năng trong menu để tiếp tục." /></section>
              : screen === 'admin-overview' ? <AdminOverview permissions={permissions} navigate={navigate} />
              : screen === 'users' ? <UserManagement me={me} permissions={permissions} invite={() => { navigate('organizations'); setOrganizationForm('invite'); }} />
              : screen === 'organizations' ? <OrganizationManagement permissions={permissions} initialForm={organizationForm} />
              : screen === 'matching-statistics' ? <MatchingStatistics key={me.userId} />
              : screen === 'audit' ? <AuditViewer permissions={permissions} />
              : screen === 'delegations' ? <SchoolDelegations key={me.userId} grants={grants} />
              : screen === 'school-preferences' ? <SchoolPreferenceReview key={me.userId} />
              : screen === 'sent-mentor-invitations' ? <SentMentorInvitations key={me.userId} me={me} grants={grants} />
              : screen === 'assignment-management' ? <AssignmentManagementPage key={me.userId} />
              : screen === 'internship-case' ? <CaseEntry key={me.userId + caseId + (destination.followup || '')} caseId={caseId} me={me} grants={grants} />
              : screen === 'school-internship-cases' ? <SchoolCaseLookup key={me.userId} grants={grants} userId={me.userId} />
              : screen === 'evaluations' ? <EvaluationsPage key={me.userId} me={me} grants={grants} />
              : screen === 'internship-operations' ? <OperationsPage key={me.userId} me={me} grants={grants} />
              : screen === 'internship' ? <InternshipWorkspace key={me.userId} me={me} grants={grants} topbarTarget={internshipTopbar}
                  accountMenu={<RoleAccountMenu badge={hrMentorBadge} label={hrMentorLabel} kicker={hrMentorKicker}
                    title={me.displayName} email={me.email} variant="topbar" onAccount={() => navigate('account')}
                    onLogout={() => void action.run(signOut)} logoutDisabled={action.busy} />}
                  section={internshipSection} onKindChange={kind => {
                    setInternshipKind(kind);
                  }} onSectionChange={navigateInternship} />
              : screen === 'student-overview' ? <StudentOverviewPage me={me} schools={schools} openProfile={() => navigate('student-profile')} />
              : screen === 'student-profile' ? <MyProfilePage me={me} schools={schools} />
              : screen === 'student-capability' ? <CapabilityPage key={me.userId} me={me} schools={schools} />
              : screen === 'student-opportunities' ? <OpportunitiesPage schools={schools} openPreferences={() => setStudentTab('preferences')} />
              : screen === 'student-invitations' ? <InvitationsPreferencesPage tab={studentTab} setTab={setStudentTab} />
              : screen === 'student-tasks' ? <InternshipTasksPage key={me.userId} me={me} />
              : screen === 'student-reports' ? <ReportsResultsPage key={me.userId} me={me} />
              : screen === 'mentor-invitations' ? <MentorInvitations />
              // DEV08 hiển thị mô-đun danh mục kỹ năng trong khung ứng dụng hiện có để người kiểm thử bắt đầu kiểm tra giao diện.
              : screen === 'taxonomy' ? <SkillTaxonomy />
              : <AccountOverview me={me} grants={grants} context={context} setContext={setContext}
                signedOut={() => { generation.current += 1; setJustSignedIn(false); setJustSignedOut(false); setMe(null); setGrants([]); setSessionMessage('Đổi mật khẩu thành công. Vui lòng đăng nhập lại.'); }} />}
          </div>
        )}
      </main>
      <footer className="app-footer">
        <div className="app-footer-inner">
          <span className="app-footer-brand">© {new Date().getFullYear()} UT.Bridora</span>
          <span>Kết nối giáo dục với cơ hội nghề nghiệp.</span>
        </div>
      </footer>
    </div>
  </div>;
}

function SessionFeedback({ variant, title, subtitle }: { variant: 'login' | 'logout'; title: string; subtitle: string }) {
  return <>
    <div className={`session-sweep is-${variant}`} aria-hidden="true" />
    <div className={`session-toast is-${variant}`} aria-hidden="true">
      <span className="session-toast-icon"><Icon name={variant === 'login' ? 'check' : 'logout'} size={16} /></span>
      <div><strong>{title}</strong><small>{subtitle}</small></div>
    </div>
  </>;
}

function AuthBrand() {
  return <a href="/" className="auth-brand" aria-label="UT.Bridora trang chủ">
    <span className="auth-monogram" aria-hidden="true">U</span>
    <span><b>UT.BRIDORA</b><small>Kết nối thực tập</small></span>
  </a>;
}

function AuthLayout({ screen, justSignedOut = false, children }: { screen: string; justSignedOut?: boolean; children: ReactNode }) {
  const helpDialog = useRef<HTMLDialogElement>(null);
  const entrySurface = useSurfaceSpotlight<HTMLElement>(true, '.auth-card', 'auth');
  const labels: Record<string, string> = { login: 'CỔNG ĐĂNG NHẬP', forgot: 'KHÔI PHỤC MẬT KHẨU', reset: 'KHÔI PHỤC MẬT KHẨU', activate: 'KÍCH HOẠT TÀI KHOẢN' };
  return <main className={'auth-shell' + (justSignedOut ? ' is-just-signed-out' : '')}>
    <section className="auth-story">
      <AuthBrand />
      <div className="auth-story-content"><span className="auth-tag">NỀN TẢNG KẾT NỐI THỰC TẬP</span>
        <RotatingHeadline />
        <span className="auth-heading-rule" aria-hidden="true" />
        <p className="auth-story-description">Cùng quản lý hành trình thực tập giữa sinh viên, nhà trường và doanh nghiệp trên một nền tảng thống nhất.</p>
        <div className="connection-art" aria-hidden="true">
          <p className="connection-title">MỘT NỀN TẢNG CHUNG</p>
          <div className="connection-map">
          <svg viewBox="0 0 460 460" className="connection-lines">
            <path d="M121.747 144.5 230 207l108.253-62.5M230 207v125" />
            <circle cx="230" cy="207" r="60" /><circle cx="230" cy="207" r="76" />
            <circle className="connection-wave" cx="230" cy="207" r="98" />
            <circle className="connection-orbit" cx="230" cy="207" r="125" />
            <path className="connection-flow flow-student" d="M121.747 144.5 230 207" />
            <path className="connection-flow flow-school" d="M338.253 144.5 230 207" />
            <path className="connection-flow flow-enterprise" d="M230 332V207" />
            <g className="connection-sparks"><circle cx="278" cy="378" r="2" /><circle cx="316" cy="366" r="2" /><circle cx="354" cy="378" r="2" /><circle cx="392" cy="366" r="2" /><circle cx="430" cy="378" r="2" /></g>
          </svg>
          <div className="connection-node node-student"><span><Icon name="graduation" size={22} /></span><b>Sinh viên</b></div>
          <div className="connection-center">UT.Bridora</div>
          <div className="connection-node node-school"><span><Icon name="university" size={22} /></span><b>Nhà trường</b></div>
          <div className="connection-node node-enterprise"><span><Icon name="briefcase" size={22} /></span><b>Doanh nghiệp</b></div>
          </div>
        </div>
        <p className="auth-protected"><span />KẾT NỐI ĐƯỢC BẢO VỆ</p>
      </div>
      <p className="auth-story-footer">UT.BRIDORA / UNIVERSITY • INDUSTRY</p>
    </section>
    <section className="auth-entry" ref={entrySurface}>
      <header className="auth-entry-header">
        <div className="auth-mobile-brand"><AuthBrand /></div>
        <span className="auth-entry-label">{labels[screen]}</span>
        <button className="text-button auth-help-button" type="button" aria-haspopup="dialog" aria-controls="auth-help" onClick={() => helpDialog.current?.showModal()}>Cần hỗ trợ?</button>
        <span className="auth-security-tag"><Icon name="shield" size={13} />AN TOÀN · BẢO MẬT</span>
      </header>
      <div className="auth-entry-body">
        <div className="auth-card">{children}</div>
        <p className="auth-entry-footer">Bảo mật phiên đăng nhập · Quản lý quyền truy cập theo vai trò</p>
        <p className="auth-copyright">UT.Bridora · Cổng truy cập chính thức</p>
      </div>
    </section>
    <dialog className="auth-help-dialog" id="auth-help" ref={helpDialog} aria-labelledby="auth-help-title">
      <div className="auth-help-heading"><h2 id="auth-help-title">Hỗ trợ truy cập</h2><button className="secondary" type="button" onClick={() => helpDialog.current?.close()}>Đóng</button></div>
      <ol>
        <li><b>Đăng nhập tài khoản</b><p>Sử dụng email được cấp và mật khẩu bạn đã đặt. Kiểm tra chữ hoa, chữ thường và trạng thái phím Caps Lock.</p></li>
        <li><b>Chưa kích hoạt tài khoản</b><p>Mở liên kết lời mời trong email để điền họ tên và tự đặt mật khẩu. Lời mời có hiệu lực 48 giờ và chỉ dùng một lần.</p></li>
        <li><b>Quên mật khẩu hoặc liên kết hết hạn</b><p>Chọn “Quên mật khẩu?” tại màn đăng nhập để yêu cầu hướng dẫn mới. Liên kết đặt lại có hiệu lực 30 phút. Kiểm tra cả hộp thư và thư rác.</p></li>
      </ol>
      <p className="auth-help-contact">Nếu chưa nhận được lời mời, tài khoản bị khóa hoặc vẫn không đăng nhập được, hãy liên hệ quản trị viên đã cấp tài khoản cho bạn. Không chia sẻ mật khẩu hoặc liên kết xác nhận.</p>
    </dialog>
  </main>;
}

function AccountOverview({ me, grants, context, setContext, signedOut }: {
  me: Me; grants: Grant[]; context: string; setContext: (value: string) => void; signedOut: () => void;
}) {
  const organizationNames = useOrganizationNames(me.memberships.some(item => Boolean(item.organizationId)));
  const organizationCount = new Set(me.memberships.map(member => member.organizationId).filter(Boolean)).size;
  const permissionCount = new Set(grants.flatMap(grant => grant.permissions)).size;
  const member = me.memberships[Number(context)];
  const selectedPermissions = [...new Set(grants.filter(grant => grant.role === member?.role && grant.organizationId === member?.organizationId).flatMap(grant => grant.permissions))];
  return <div className="account-overview">
    <section className="account-hero"><div><span className="hero-eyebrow"><span className="live-dot" />PHIÊN ĐĂNG NHẬP ĐANG HOẠT ĐỘNG</span>
      <h2>Xin chào, {me.displayName}<span className="hero-heading-dot">.</span></h2>
      <p>Mọi kết nối bắt đầu từ bạn.<br />Quản lý danh tính và quyền truy cập tại một nơi.</p></div>
      <div className="hero-art" aria-hidden="true"><div className="hero-orbit orbit-one" /><div className="hero-orbit orbit-two" /><span className="hero-art-center"><Icon name="shield" size={34} /></span>
        <span className="hero-art-node hero-node-one"><Icon name="graduation" size={22} /></span><span className="hero-art-node hero-node-two"><Icon name="building" size={21} /></span><span className="hero-art-node hero-node-three"><Icon name="user" size={20} /></span></div></section>
    <div className="account-stats">
      {[{ value: me.memberships.length, title: 'Vai trò được cấp', detail: 'Theo từng phạm vi tham gia', icon: 'users' },
        { value: organizationCount, title: 'Tổ chức tham gia', detail: me.memberships.some(item => item.role === 'ADMIN' && !item.organizationId) ? 'Có quyền quản trị toàn hệ thống' : 'Các tổ chức đã cấp vai trò', icon: 'building' },
        { value: permissionCount, title: 'Quyền theo vai trò', detail: 'Từ các vai trò đang có hiệu lực', icon: 'shield' }].map(stat =>
          <section className="stat-card" key={stat.title}><span className="stat-icon"><Icon name={stat.icon} size={21} /></span><div><p>{stat.title}</p><strong>{stat.value.toLocaleString('vi-VN')}</strong><small>{stat.detail}</small></div></section>)}
    </div>
    <div className="account-columns"><div className="account-primary">
      <section className="panel identity-panel"><div className="section-heading"><div><p className="section-kicker">HỒ SƠ CỦA BẠN</p><h2>Thông tin tài khoản</h2></div><span className="profile-avatar">{initials(me.displayName)}</span></div>
        <div className="identity-grid"><div className="identity-item"><span className="identity-label"><Icon name="user" size={15} />Họ và tên</span><strong>{me.displayName}</strong></div>
          <div className="identity-item"><span className="identity-label"><Icon name="mail" size={15} />Email</span><strong>{me.email}</strong></div>
          <div className="identity-item identity-id"><span className="identity-label"><Icon name="key" size={15} />Mã tài khoản</span><code>{me.userId}</code></div></div>
      </section>
      <section className="panel access-panel"><div className="section-heading"><div><p className="section-kicker">PHẠM VI TRUY CẬP</p><h2>Vai trò và tổ chức</h2><p className="section-description">Các vai trò đang được cấp cho tài khoản của bạn.</p></div><span className="section-icon"><Icon name="layers" size={21} /></span></div>
        {me.memberships.length === 0 ? <EmptyState icon="users" title="Chưa có vai trò" description="Liên hệ người quản trị để được cấp quyền phù hợp." /> :
          <div className="account-memberships">{me.memberships.map((item, index) => <div className="account-membership" key={item.role + ':' + item.organizationId + ':' + index}>
            <span className="membership-symbol"><Icon name={item.role === 'ADMIN' ? 'shield' : item.role === 'ENTERPRISE' ? 'briefcase' : 'graduation'} size={19} /></span>
            <div><b>{roleNames[item.role] || item.role}</b><small>{item.organizationId ? (organizationNames[item.organizationId] || (item.role === 'ENTERPRISE' ? 'Phạm vi doanh nghiệp' : 'Phạm vi nhà trường')) : 'Toàn hệ thống'}</small></div>
            <Icon name="check-circle" size={17} className="membership-check" />
          </div>)}</div>}
        <div className="permissions-heading"><h3>Quyền trong phạm vi đã chọn</h3>
          {me.memberships.length > 1 && <label className="context-label">Vai trò đang xem<select aria-label="Vai trò đang xem" value={context} onChange={event => setContext(event.target.value)}>
            {me.memberships.map((item, index) => <option key={index} value={index}>{roleNames[item.role]} · {item.organizationId ? (organizationNames[item.organizationId] || `Tổ chức ${index + 1}`) : 'Toàn hệ thống'}</option>)}</select></label>}</div>
        {selectedPermissions.length ? <ul className="permissions">{selectedPermissions.map(permission => <li key={permission}><Icon name="check" size={15} /><span>{permissionNames[permission] || 'Chức năng được cấp'}</span></li>)}</ul>
          : <p className="permissions-empty">Chưa có quyền hiệu lực trong phạm vi này.</p>}
        <p className="access-note"><Icon name="info" size={15} />Một số chức năng còn phụ thuộc vào trạng thái tổ chức và quyền chia sẻ được cấp.</p>
      </section>
    </div><aside className="account-security"><section className="panel password-panel"><PasswordForm signedOut={signedOut} /></section>
      <div className="security-note"><Icon name="shield" size={22} /><div><b>Chủ động bảo vệ tài khoản</b><p>Sử dụng mật khẩu riêng và đăng xuất sau khi làm việc trên thiết bị dùng chung.</p></div></div>
    </aside></div>
  </div>;
}

function AuthForm({ screen, token, clearToken, navigate, signedIn }: {
  screen: string; token: string; clearToken: () => void; navigate: (screen: string) => void; signedIn: (user: Me) => Promise<void>;
}) {
  const [email, setEmail] = useState(''), [password, setPassword] = useState(''), [confirm, setConfirm] = useState(''), [name, setName] = useState('');
  const [passwordVisible, setPasswordVisible] = useState(false);
  const action = useAction();
  const submitting = useRef(false);
  const titles: Record<string, string> = { login: 'Đăng nhập', forgot: 'Quên mật khẩu', reset: 'Mật khẩu mới', activate: 'Kích hoạt tài khoản' };
  const overlines: Record<string, string> = { login: 'CHÀO MỪNG TRỞ LẠI', forgot: 'BƯỚC 01 / YÊU CẦU', reset: 'BƯỚC 03 / ĐẶT LẠI', activate: 'TÀI KHOẢN / LỜI MỜI' };
  const submitLabels: Record<string, string> = { login: 'Đăng nhập', forgot: 'Gửi hướng dẫn', reset: 'Lưu mật khẩu mới', activate: 'Kích hoạt tài khoản' };
  const descriptions: Record<string, string> = {
    login: 'Nhập thông tin tài khoản để tiếp tục.',
    forgot: 'Nhập email tài khoản để nhận hướng dẫn đặt lại mật khẩu.',
    reset: 'Tạo mật khẩu mới để bảo vệ tài khoản của bạn.',
    activate: 'Hoàn tất thông tin để bắt đầu sử dụng UT.Bridora.'
  };
  const guidanceTitles: Record<string, string> = { forgot: 'Bảo vệ tài khoản', reset: 'Thông tin liên kết', activate: 'Thông tin lời mời' };
  const guidance: Record<string, string> = {
    forgot: 'Kết quả phản hồi không xác nhận email có tồn tại trong hệ thống. Không chia sẻ liên kết khôi phục.',
    reset: 'Liên kết đặt lại mật khẩu có hiệu lực trong 30 phút kể từ khi được gửi và chỉ sử dụng một lần.',
    activate: 'Lời mời có hiệu lực trong 48 giờ kể từ khi được gửi và chỉ sử dụng một lần. Bạn tự đặt mật khẩu để kích hoạt tài khoản.'
  };
  const tokenFlow = ['reset', 'activate'].includes(screen);
  const linkError = tokenFlow && action.error && action.message.startsWith('Liên kết');
  const changeScreen = () => {
    if (action.busy) return;
    action.setMessage(''); setPassword(''); setConfirm(''); setPasswordVisible(false); navigate(screen === 'login' ? 'forgot' : 'login');
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (submitting.current) return;
    submitting.current = true;
    void action.run(async () => {
      try {
        if (screen === 'login') { const user = await login(email, password); setPassword(''); await signedIn(user); return; }
        if (screen === 'forgot') { await send('/auth/password/forgot', 'POST', { email }); action.setMessage('Nếu địa chỉ hợp lệ, hướng dẫn đặt lại mật khẩu sẽ được gửi qua email.'); return; }
        if (!token) throw new Error('Liên kết thiếu mã xác nhận. Vui lòng yêu cầu liên kết mới.');
        if (password !== confirm) throw new Error('Mật khẩu xác nhận chưa khớp.');
        if ([...password].length < 12 || new TextEncoder().encode(password).length > 72) throw new Error('Mật khẩu cần ít nhất 12 ký tự, tối đa 72 byte UTF-8.');
        try { await send(screen === 'reset' ? '/auth/password/reset' : '/auth/invitations/accept', 'POST', screen === 'reset' ? { token, newPassword: password } : { token, displayName: name, password }); }
        catch (error) { if (error instanceof ApiError && error.status === 400) throw new Error('Liên kết không hợp lệ, đã hết hạn hoặc đã được sử dụng.'); throw error; }
        clearToken(); setPassword(''); setConfirm(''); navigate('login'); action.setMessage('Thành công. Vui lòng đăng nhập bằng mật khẩu mới.');
      } finally { submitting.current = false; }
    });
  };
  return <form key={screen} className={'auth-form' + (screen === 'login' ? ' auth-form-login' : '')} onSubmit={submit} aria-busy={action.busy} aria-label={'Biểu mẫu ' + titles[screen]}>
    <span className="auth-form-icon" aria-hidden="true"><Icon name="check" size={16} /></span>
    <p className="auth-overline">{overlines[screen]}</p>
    <h2 className="auth-title" id="auth-title">{titles[screen]}</h2><p className="auth-description">{descriptions[screen]}</p>
    <div className="auth-fields">
      {['login', 'forgot'].includes(screen) && <Field label="Email" icon="mail" type="email" value={email} onChange={setEmail}
        placeholder="Nhập địa chỉ email" autoComplete="username" maxLength={254} disabled={action.busy} />}
      {screen === 'activate' && <Field label="Họ và tên" icon="user" value={name} onChange={setName} maxLength={150} autoComplete="name" placeholder="Nhập họ và tên của bạn" disabled={action.busy} />}
      {screen !== 'forgot' && <Field label={screen === 'login' ? 'Mật khẩu' : 'Mật khẩu mới'} type="password" value={password}
        onChange={setPassword} placeholder={screen === 'login' ? 'Nhập mật khẩu' : 'Tạo mật khẩu mới'}
        passwordVisible={screen === 'login' ? passwordVisible : undefined} onPasswordVisibilityChange={screen === 'login' ? setPasswordVisible : undefined}
        autoComplete={screen === 'login' ? 'current-password' : 'new-password'} disabled={action.busy} />}
      {tokenFlow && <Field label="Xác nhận mật khẩu" type="password" value={confirm} onChange={setConfirm}
        placeholder="Nhập lại mật khẩu mới" autoComplete="new-password" disabled={action.busy} />}
    </div>
    {screen === 'login' && <div className="auth-options">
      <label className="auth-show-password"><input type="checkbox" checked={passwordVisible} onChange={event => setPasswordVisible(event.target.checked)} disabled={action.busy} />Hiện mật khẩu</label>
      <button className="text-button" type="button" disabled={action.busy} onClick={changeScreen}>Quên mật khẩu?</button>
    </div>}
    {tokenFlow && <p className="password-requirement"><Icon name="info" size={15} />Tối thiểu 12 ký tự, tối đa 72 byte UTF-8.</p>}
    <div className={linkError ? 'auth-warning' : undefined}><Notice message={action.message} tone={action.error ? 'error' : 'success'} /></div>
    {tokenFlow && !token && <div className="auth-warning"><Notice tone="error" message="Liên kết thiếu mã xác nhận. Vui lòng mở lại liên kết trong email hoặc yêu cầu liên kết mới." /></div>}
    <button className="auth-submit full" disabled={action.busy || tokenFlow && !token}>
      {action.busy ? <><span className="spinner spinner-light" />Đang xử lý…</> : <>{submitLabels[screen]}{screen === 'login' && <Icon name="arrow-right" size={18} />}</>}
    </button>
    {guidance[screen] && <div className="auth-guidance"><b>{guidanceTitles[screen]}</b><p>{guidance[screen]}</p></div>}
    {screen !== 'login' && <button className="text-button auth-back full" type="button" disabled={action.busy} onClick={changeScreen}><Icon name="arrow-left" size={16} />Quay lại đăng nhập</button>}
    {screen === 'login' && <p className="auth-invitation-note">Sau khi đăng nhập, hệ thống đưa bạn đến không gian phù hợp với quyền truy cập.</p>}
  </form>;
}

function PasswordForm({ signedOut }: { signedOut: () => void }) {
  const [current, setCurrent] = useState(''), [next, setNext] = useState(''), [confirm, setConfirm] = useState('');
  const submitting = useRef(false);
  const action = useAction();
  return <form className="password-form" aria-busy={action.busy} onSubmit={event => {
    event.preventDefault();
    if (submitting.current) return;
    submitting.current = true;
    void action.run(async () => {
      try {
        if (next !== confirm) throw new Error('Mật khẩu xác nhận chưa khớp.');
        if ([...next].length < 12 || new TextEncoder().encode(next).length > 72) throw new Error('Mật khẩu cần ít nhất 12 ký tự, tối đa 72 byte UTF-8.');
        await send('/auth/password/change', 'POST', { currentPassword: current, newPassword: next });
        setCurrent(''); setNext(''); setConfirm(''); signedOut();
      } finally { submitting.current = false; }
    });
  }}>
    <span className="password-panel-icon"><Icon name="lock" size={23} /></span><p className="section-kicker">BẢO MẬT TÀI KHOẢN</p><h2>Đổi mật khẩu</h2>
    <p className="section-description">Sau khi cập nhật, bạn cần đăng nhập lại trên các thiết bị.</p>
    <Field label="Mật khẩu hiện tại" type="password" value={current} onChange={setCurrent} autoComplete="current-password" placeholder="Nhập mật khẩu hiện tại" disabled={action.busy} />
    <Field label="Mật khẩu mới" type="password" value={next} onChange={setNext} autoComplete="new-password" placeholder="Tạo mật khẩu mới" hint="Ít nhất 12 ký tự, tối đa 72 byte UTF-8." disabled={action.busy} />
    <Field label="Xác nhận mật khẩu" type="password" value={confirm} onChange={setConfirm} autoComplete="new-password" placeholder="Nhập lại mật khẩu mới" disabled={action.busy} />
    <Notice message={action.message} tone={action.error ? 'error' : 'success'} />
    <button className="full" disabled={action.busy}>{action.busy ? <span className="spinner spinner-light" /> : <Icon name="key" size={17} />}Cập nhật mật khẩu</button>
  </form>;
}
