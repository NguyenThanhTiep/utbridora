import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { api, send, login, logout, whoAmI, ApiError, type Me, type Grant } from './api';
import { Brand, EmptyState, Field, Icon, Notice, useAction, roleNames, permissionNames } from './components';
import { UserManagement, OrganizationManagement, AuditViewer } from './management';
import { InternshipWorkspace } from './modules/internship/pages/InternshipWorkspace';
import { CaseEntry } from './modules/internship/pages/CaseEntry';
import { EvaluationsPage } from './modules/internship/pages/EvaluationsPage';
import { canDiscoverWorkspace } from './modules/internship/access';

import { studentGrants } from './modules/student/access';
import { StudentOverviewPage } from './modules/student/pages/StudentOverviewPage';
import { MyProfilePage } from './modules/student/pages/MyProfilePage';
import { InvitationsPreferencesPage, type StudentResponseTab } from './modules/student/pages/InvitationsPreferencesPage';
import { OpportunitiesPage } from './modules/student/pages/OpportunitiesPage';
import { InternshipTasksPage } from './modules/student/pages/InternshipTasksPage';
import { CapabilityPage } from './modules/student/pages/CapabilityPage';
import { ReportsResultsPage } from './modules/student/pages/ReportsResultsPage';
import { MentorInvitations } from './modules/internship/pages/MentorInvitations';
import './modules/workflows.css';
// DEV08 đã nối màn hình danh mục kỹ năng vào khung ứng dụng hiện có để người kiểm thử mở được giao diện US-T4-06.
import { SkillTaxonomy } from './modules/taxonomy';
import { DemandWorkspace, SchoolWorkspace, PortalNavigation, portalPageTitle, canAuthorDemands, canLeaveDemand, type PortalPage } from './modules/cooperation';
import { SchoolDelegations } from './modules/delegations/SchoolDelegations';

const screens = [
  { id: 'evaluations', title: 'Đánh giá thực tập', description: 'Chấm rubric, công bố và xem kết quả theo quyền được phân công.', icon: 'graduation', permission: null },
  { id: 'internship-case', title: 'Hồ sơ thực tập', description: 'Hồ sơ trong phạm vi được phân công.', icon: 'briefcase', permission: null },
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
  { id: 'users', title: 'Tài khoản & phân quyền', description: 'Quản lý thành viên và quyền truy cập trong hệ thống.', icon: 'users', permission: 'account:admin:status' },
  { id: 'delegations', title: 'Phân công quyền cán bộ', description: 'Cấp và thu hồi quyền trong từng trường và kỳ thực tập.', icon: 'shield', permission: null },
  { id: 'organizations', title: 'Tổ chức & lời mời', description: 'Kết nối các tổ chức và mời thành viên tham gia.', icon: 'building', permission: 'membership:admin:write' },
  { id: 'audit', title: 'Nhật ký hệ thống', description: 'Theo dõi và tra cứu các hoạt động quan trọng.', icon: 'activity', permission: 'audit:global:read' },
  // DEV08 cho phép quản trị viên toàn cục mở màn hình để kiểm thử; giao diện lập trình vẫn tự kiểm tra quyền ở máy chủ.
  { id: 'taxonomy', title: 'Danh mục kỹ năng', description: 'Quản lý cây kỹ năng chuẩn, tên gọi khác và tra cứu mã.', icon: 'layers', permission: 'taxonomy:skills:read' }
];
function initials(name: string) {
  const words = name.trim().split(/\s+/);
  return (words.length > 1 ? words[0][0] + words[words.length - 1][0] : words[0]?.slice(0, 2) || 'UB').toUpperCase();
}

function defaultPortalSide(grants: Grant[]): 'enterprise' | 'school' | null {
  if (grants.some(grant => (grant.role === 'ADMIN' && grant.organizationId === null)
    || (grant.role === 'STUDENT' && !!grant.organizationId))) return null;
  const enterprise = grants.some(canAuthorDemands);
  const school = grants.some(grant => grant.role === 'SCHOOL_STAFF' && !!grant.organizationId);
  return enterprise === school ? null : enterprise ? 'enterprise' : 'school';
}
// Màn mở đầu sau đăng nhập: cổng doanh nghiệp/nhà trường; tài khoản sinh viên vào Tổng quan (S01); còn lại Tài khoản của tôi.
function landingScreen(grants: Grant[]) {
  const side = defaultPortalSide(grants);
  return side === 'enterprise' ? 'demands' : side === 'school' ? 'school' : studentGrants(grants).length ? 'student-overview' : 'account';
}

export default function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [grants, setGrants] = useState<Grant[]>([]);
  const [initializing, setInitializing] = useState(true);
  const [screen, setScreen] = useState('account');
  const [portalPage, setPortalPage] = useState<PortalPage>('overview');
  const [caseId, setCaseId] = useState(() => /^\/workspace\/cases\/([0-9a-f-]{36})$/i.exec(location.pathname)?.[1] || '');
  const [studentTab, setStudentTab] = useState<StudentResponseTab>('invitations');
  const [authScreen, setAuthScreen] = useState(location.pathname === '/reset-password' ? 'reset'
    : location.pathname === '/accept-invitation' ? 'activate' : 'login');
  const [token, setToken] = useState(() => new URLSearchParams(location.hash.slice(1)).get('token') || '');
  const [sessionMessage, setSessionMessage] = useState('');
  const [context, setContext] = useState('0');
  const [mobileOpen, setMobileOpen] = useState(false);
  const generation = useRef(0);
  const heading = useRef<HTMLHeadingElement>(null);
  const sidebar = useRef<HTMLElement>(null);
  const menuButton = useRef<HTMLButtonElement>(null);
  const [accountOpen, setAccountOpen] = useState(false);
  const accountMenu = useRef<HTMLDivElement>(null);
  const action = useAction();
  const permissions = new Set(grants.filter(grant => grant.role === 'ADMIN' && grant.organizationId === null).flatMap(grant => grant.permissions));
  const schools = studentGrants(grants);
  const menus = screens.filter(item => item.id === 'internship-case' ? !!caseId : item.id === 'internship' ? !!me && canDiscoverWorkspace(me)
    : item.id === 'evaluations' ? grants.some(grant => ['SCHOOL_STAFF','ENTERPRISE','STUDENT'].includes(grant.role) && !!grant.organizationId)
    : item.id === 'school' ? grants.some(grant => grant.role === 'SCHOOL_STAFF' && !!grant.organizationId)
    : item.id === 'demands' ? grants.some(canAuthorDemands)
    : item.id === 'delegations' ? permissions.has('membership:admin:write') || grants.some(g => g.role === 'SCHOOL_STAFF' && g.permissions.includes('school:delegation:manage'))
    : item.permission === null || (item.id === 'taxonomy' ? grants.some(grant => grant.permissions.includes('taxonomy:skills:read')) : (item.group === 'student' ? schools.length > 0 : permissions.has(item.permission))));
  const current = screens.find(item => item.id === screen) || screens[0];
  const rolePortalSide = defaultPortalSide(grants);
  const portalScreen = screen === 'demands' || screen === 'school';
  const portalSide = screen === 'demands' ? 'enterprise' : screen === 'school' ? 'school' : rolePortalSide;
  const portalEntry = portalSide === 'enterprise' ? 'demands' : 'school';
  const screenTitle = portalSide && portalScreen ? portalPageTitle(portalSide, portalPage) : current.title;
  // Tài khoản có nhóm SINH VIÊN: tên trang (h1) nằm gọn trong thanh trên cùng, không dùng khối tiêu đề lớn trong nội dung.
  const studentShell = menus.some(item => item.group === 'student');
  const pageKicker = screen === 'account' || screen === 'mentor-invitations' ? 'KHÔNG GIAN CÁ NHÂN' : screen === 'demands' || screen === 'school' ? current.title : screen === 'internship' || screen === 'evaluations' ? 'QUẢN LÝ THỰC TẬP' : current.group === 'student' ? 'KHÔNG GIAN SINH VIÊN' : 'QUẢN TRỊ NỀN TẢNG';

  useEffect(() => {
    const authTitles: Record<string, string> = { login: 'Đăng nhập', forgot: 'Quên mật khẩu', reset: 'Đặt lại mật khẩu', activate: 'Kích hoạt tài khoản' };
    document.title = (me ? screenTitle : authTitles[authScreen] || 'Không gian kết nối') + ' | UT.Bridora';
  }, [me?.userId, screenTitle, authScreen]);

  useEffect(() => {
    let active = true;
    const currentGeneration = ++generation.current;
    if (location.hash) history.replaceState(null, '', location.pathname);
    void (async () => {
      try {
        const user = await whoAmI();
        const effective = await api<Grant[]>('/auth/me/permissions');
        if (active && generation.current === currentGeneration) {
          setMe(user); setGrants(effective);
          setScreen(location.pathname.startsWith('/workspace/cases/') ? 'internship-case' : landingScreen(effective));
          setPortalPage('overview');
        }
      } catch (error) {
        if (active && !(error instanceof ApiError && error.status === 401)) action.setMessage('Không thể tải phiên. Vui lòng thử đăng nhập lại.');
      } finally { if (active) setInitializing(false); }
    })();
    const expired = () => {
      generation.current += 1;
      setMe(null); setGrants([]); setMobileOpen(false); setAuthScreen('login');
      setSessionMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.');
    };
    const onNavigate = (event: Event) => {
      const target = (event as CustomEvent<string>).detail;
      if (target && canLeaveDemand()) { setScreen(target); setPortalPage('overview'); setMobileOpen(false); }
    };
    window.addEventListener('session-expired', expired);
    window.addEventListener('bridora:navigate', onNavigate);
    return () => { active = false; window.removeEventListener('session-expired', expired); window.removeEventListener('bridora:navigate', onNavigate); };
  }, []);

  useEffect(() => {
    const frame = requestAnimationFrame(() => heading.current?.focus({ preventScroll: true }));
    return () => cancelAnimationFrame(frame);
  }, [screen, me?.userId]);
  useEffect(() => {
    const media = window.matchMedia('(min-width: 901px)');
    const changed = () => { if (media.matches) setMobileOpen(false); };
    media.addEventListener('change', changed);
    return () => media.removeEventListener('change', changed);
  }, []);
  useEffect(() => {
    if (!mobileOpen) return;
    const oldOverflow = document.body.style.overflow;
    document.body.style.overflow = 'hidden';
    const controls = () => Array.from(sidebar.current?.querySelectorAll<HTMLButtonElement | HTMLAnchorElement>('button:not(:disabled), a[href]') || []);
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
  // Menu tài khoản ở thanh trên cùng (tài khoản sinh viên): đóng khi đổi màn/người dùng, bấm ra ngoài hoặc Esc (trả focus về nút mở).
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

  const navigate = (next: string) => { if (next !== screen && !canLeaveDemand()) return;
    if (next !== 'internship-case' && caseId) {setCaseId('');history.replaceState(null, '', '/');}
    if (next !== screen) setPortalPage(next === 'demands' ? 'demands' : 'overview');
    setScreen(next); setMobileOpen(false); };
  const navigatePortal = (next: PortalPage) => {
    if (!canLeaveDemand()) return;
    if (caseId) { setCaseId(''); history.replaceState(null, '', '/'); }
    setScreen(portalEntry);
    setPortalPage(next);setMobileOpen(false);
  };
  const signOut = async () => {
    if (!canLeaveDemand()) return;
    await logout(); generation.current += 1;
    setMe(null); setGrants([]); setContext('0'); setMobileOpen(false);setCaseId('');
    setAuthScreen('login'); setSessionMessage(''); history.replaceState(null, '', '/');
  };
  const signedIn = async (user: Me) => {
    const effective = await api<Grant[]>('/auth/me/permissions');
    generation.current += 1;
    setGrants(effective); setMe(user);
    setScreen(caseId ? 'internship-case' : landingScreen(effective));
    setPortalPage('overview'); setContext('0'); setSessionMessage('');
  };
  const navigateAuth = (next: string) => { setAuthScreen(next); action.setMessage(''); setSessionMessage(''); };

  if (initializing) return <main className="startup"><Brand /><div className="startup-status" role="status"><span className="spinner" />Đang chuẩn bị không gian của bạn…</div></main>;
  if (!me) return <AuthLayout screen={authScreen}><AuthForm screen={authScreen} token={token} clearToken={() => setToken('')}
    navigate={navigateAuth} signedIn={signedIn} />
    <div className={sessionMessage ? 'auth-warning' : undefined}>
      <Notice message={sessionMessage || action.message} tone={sessionMessage ? 'info' : action.error ? 'error' : 'info'} />
    </div>
  </AuthLayout>;

  // A mail link can be opened in a browser which is already signed in.
  // Keep its token in memory and provide an explicit session switch.
  if (token && ['reset', 'activate'].includes(authScreen)) return <AuthLayout screen={authScreen}>
    <span className="auth-form-icon"><Icon name="mail" size={26} /></span>
    <h2 className="auth-title">Tiếp tục với liên kết email</h2>
    <p className="auth-description">Bạn đang đăng nhập bằng tài khoản <strong>{me.email}</strong>. Đăng xuất trước khi sử dụng liên kết này.</p>
    <Notice message={action.message} tone={action.error ? 'error' : 'info'} />
    <button className="full" disabled={action.busy} onClick={() => void action.run(async () => {
      await logout(); generation.current += 1; setMe(null); setGrants([]);
    })}>Đăng xuất và mở liên kết<Icon name="arrow-right" size={18} /></button>
    <button className="text-button full" disabled={action.busy} onClick={() => { setToken(''); setAuthScreen('login'); history.replaceState(null, '', '/'); }}>Về tài khoản của tôi</button>
  </AuthLayout>;

  return <div className={'workspace' + (portalSide ? ' workspace-t5' : '') + (portalScreen ? ' workspace-t5-portal' : '')}>
    <a className="skip-link" href="#main-content">Bỏ qua điều hướng</a>
    {mobileOpen && <button className="sidebar-backdrop" aria-label="Đóng phần nền menu" onClick={() => setMobileOpen(false)} tabIndex={-1} />}
    <aside className={'sidebar' + (mobileOpen ? ' is-open' : '')} ref={sidebar} id="main-navigation">
      <div className="sidebar-brand"><a href="/" aria-label="UT.Bridora trang chủ"><Brand inverse={!!portalSide} /></a>
        <button className="btn-icon sidebar-close" aria-label="Đóng menu điều hướng" onClick={() => setMobileOpen(false)}><Icon name="close" /></button></div>
      <nav aria-label="Điều hướng chính">
        {portalSide && <PortalNavigation side={portalSide} page={portalScreen ? portalPage : undefined} navigate={navigatePortal} />}
        {menus.some(item => item.group === 'student') && <p className="nav-group-label student-nav-group">SINH VIÊN</p>}
        {menus.filter(item => item.group === 'student').map(item => <button key={item.id} className={'nav' + (screen === item.id ? ' active' : '')}
          aria-current={screen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
        <p className="nav-group-label">{portalSide ? 'TÀI KHOẢN & TIỆN ÍCH' : 'KHÔNG GIAN CỦA BẠN'}</p>
        {menus.filter(item => ['account', 'mentor-invitations', 'internship', 'evaluations', 'demands', 'school', 'taxonomy'].includes(item.id) && (!portalSide || item.id !== portalEntry)).map(item => <button key={item.id} className={'nav' + (screen === item.id ? ' active' : '')}
          aria-current={screen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
        {menus.some(item => !['account', 'mentor-invitations', 'internship', 'evaluations', 'demands', 'school', 'taxonomy'].includes(item.id) && item.group !== 'student') && <p className="nav-group-label nav-group-admin">QUẢN TRỊ HỆ THỐNG</p>}
        {menus.filter(item => !['account', 'mentor-invitations', 'internship', 'evaluations', 'demands', 'school', 'taxonomy'].includes(item.id) && item.group !== 'student').map(item => <button key={item.id} className={'nav' + (screen === item.id ? ' active' : '')}
          aria-current={screen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="sidebar-user"><span className="avatar">{initials(me.displayName)}</span><div><b>{me.displayName}</b><small>{me.email}</small></div></div>
        {/* Tài khoản sinh viên đăng xuất từ menu tài khoản ở thanh trên cùng. */}
        {!studentShell && <button className="sidebar-logout" disabled={action.busy} onClick={() => void action.run(signOut)}><Icon name="logout" size={18} />Đăng xuất</button>}
      </div>
      {!portalSide && <p className="sidebar-caption">Nhà trường · Doanh nghiệp · Sinh viên</p>}
    </aside>

    <div className="workspace-main">
      <header className="topbar">
        <div className="topbar-location"><button className="btn-icon menu-toggle" ref={menuButton} aria-label="Mở menu điều hướng"
          aria-expanded={mobileOpen} aria-controls="main-navigation" onClick={() => setMobileOpen(previous => !previous)}><Icon name="menu" /></button>
          {studentShell ? <div className="topbar-heading"><p className="topbar-kicker">{pageKicker}</p><h1 className="topbar-title" ref={heading} tabIndex={-1}>{screenTitle}</h1></div>
            : <><span className="topbar-home"><Icon name="layers" size={17} />Không gian làm việc</span><Icon name="chevron-right" size={14} /><span className="topbar-current">{screenTitle}</span></>}</div>
        <div className="topbar-right"><span className="topbar-date"><Icon name="calendar" size={16} />{new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Bangkok' }).format(new Date())}</span>
          <div className="account-menu-wrap" ref={accountMenu}>
            <button className="profile-shortcut" aria-label={studentShell ? 'Mở menu tài khoản' : 'Mở tài khoản của tôi'} aria-expanded={studentShell ? accountOpen : undefined}
              aria-controls={studentShell ? 'account-menu' : undefined} onClick={() => studentShell ? setAccountOpen(open => !open) : navigate('account')}>
              <span className="avatar avatar-small">{initials(me.displayName)}</span><span>{me.displayName}</span><Icon name="chevron-down" size={14} /></button>
            {studentShell && accountOpen && <div className="account-menu" id="account-menu">
              <p className="account-menu-user"><b>{me.displayName}</b><small>{me.email}</small></p>
              <button type="button" onClick={() => { setAccountOpen(false); navigate('student-overview'); }}><Icon name="graduation" size={17} />Tổng quan</button>
              <button type="button" className="account-menu-logout" disabled={action.busy} onClick={() => { setAccountOpen(false); void action.run(signOut); }}><Icon name="logout" size={17} />Đăng xuất</button>
            </div>}
          </div></div>
      </header>
      <main className="content" id="main-content">
        {!studentShell && <div className="page-header"><div><p className="section-kicker">{pageKicker}</p>
          <h1 ref={heading} tabIndex={-1}>{current.title}</h1><p className="page-description">{current.description}</p></div>
          <span className="workspace-label"><span className="live-dot" />UT.Bridora</span></div>}
        <Notice message={action.message} tone={action.error ? 'error' : 'info'} />
        {screen === 'account' && me.memberships.some(m => m.role === 'ENTERPRISE') && !grants.some(canAuthorDemands)
          && <Notice message="Tài khoản có vai trò doanh nghiệp nhưng chưa được cấp quyền quản lý nhu cầu. Nhờ quản trị viên kiểm tra trạng thái xác minh tổ chức và liên kết vai trò." />}
        {!menus.some(item => item.id === screen) ? <section className="panel"><EmptyState icon="lock" title="Không có quyền truy cập" description="Chọn một chức năng trong menu để tiếp tục." /></section>
          : screen === 'users' ? <UserManagement me={me} permissions={permissions} />
          : screen === 'organizations' ? <OrganizationManagement permissions={permissions} />
          : screen === 'audit' ? <AuditViewer />
          : screen === 'delegations' ? <SchoolDelegations key={me.userId} grants={grants} />
          : screen === 'internship-case' ? <CaseEntry key={me.userId + caseId} caseId={caseId} me={me} grants={grants} />
          : screen === 'evaluations' ? <EvaluationsPage key={me.userId} me={me} grants={grants} />
          : screen === 'internship' ? <InternshipWorkspace key={me.userId} me={me} grants={grants} />
          : screen === 'student-overview' ? <StudentOverviewPage me={me} schools={schools} openProfile={() => navigate('student-profile')} />
          : screen === 'student-profile' ? <MyProfilePage me={me} schools={schools} />
          : screen === 'student-capability' ? <CapabilityPage key={me.userId} me={me} schools={schools} />
          : screen === 'student-opportunities' ? <OpportunitiesPage schools={schools} openPreferences={() => { setStudentTab('preferences'); navigate('student-invitations'); }} />
          : screen === 'student-invitations' ? <InvitationsPreferencesPage tab={studentTab} setTab={setStudentTab} />
          : screen === 'student-tasks' ? <InternshipTasksPage key={me.userId} me={me} />
          : screen === 'student-reports' ? <ReportsResultsPage key={me.userId} me={me} />
          : screen === 'mentor-invitations' ? <MentorInvitations />
          // DEV08 hiển thị mô-đun danh mục kỹ năng trong khung ứng dụng hiện có để người kiểm thử bắt đầu kiểm tra giao diện.
          : screen === 'taxonomy' ? <SkillTaxonomy />
          : screen === 'demands' ? <DemandWorkspace key={me.userId} grants={grants} page={portalPage} onNavigate={navigatePortal} viewerId={me.userId} />
          : screen === 'school' ? <SchoolWorkspace key={me.userId} grants={grants} page={portalPage} onNavigate={navigatePortal} viewerId={me.userId} />
          : <AccountOverview me={me} grants={grants} context={context} setContext={setContext}
            signedOut={() => { generation.current += 1; setMe(null); setGrants([]); setAuthScreen('login'); setSessionMessage('Đổi mật khẩu thành công. Vui lòng đăng nhập lại.'); }} />}
        <footer className="app-footer"><span>© {new Date().getFullYear()} UT.Bridora</span><span>Kết nối giáo dục với cơ hội nghề nghiệp.</span></footer>
      </main>
    </div>
  </div>;
}

function AuthBrand() {
  return <a href="/" className="auth-brand" aria-label="UT.Bridora trang chủ">
    <span className="auth-monogram" aria-hidden="true">U</span>
    <span><b>UT.BRIDORA</b><small>Kết nối thực tập</small></span>
  </a>;
}

function AuthLayout({ screen, children }: { screen: string; children: ReactNode }) {
  const helpDialog = useRef<HTMLDialogElement>(null);
  const labels: Record<string, string> = { login: 'CỔNG ĐĂNG NHẬP', forgot: 'KHÔI PHỤC MẬT KHẨU', reset: 'KHÔI PHỤC MẬT KHẨU', activate: 'KÍCH HOẠT TÀI KHOẢN' };
  return <main className="auth-shell">
    <section className="auth-story">
      <AuthBrand />
      <div className="auth-story-content"><span className="auth-tag">NỀN TẢNG KẾT NỐI THỰC TẬP</span>
        <h1>Kết nối tiềm năng<br />Kiến tạo tương lai.</h1>
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
    <section className="auth-entry">
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
            <div><b>{roleNames[item.role] || item.role}</b><small>{item.organizationId ? (item.role === 'ENTERPRISE' ? 'Phạm vi doanh nghiệp' : 'Phạm vi nhà trường') : 'Toàn hệ thống'}</small></div>
            {item.organizationId && <code className="membership-reference" title={item.organizationId}>{item.organizationId.slice(0, 8)}</code>}<Icon name="check-circle" size={17} className="membership-check" />
          </div>)}</div>}
        <div className="permissions-heading"><h3>Quyền trong phạm vi đã chọn</h3>
          {me.memberships.length > 1 && <label className="context-label">Vai trò đang xem<select aria-label="Vai trò đang xem" value={context} onChange={event => setContext(event.target.value)}>
            {me.memberships.map((item, index) => <option key={index} value={index}>{roleNames[item.role]} · {item.organizationId ? item.organizationId.slice(0, 8) : 'Toàn hệ thống'}</option>)}</select></label>}</div>
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
  return <form className={'auth-form' + (screen === 'login' ? ' auth-form-login' : '')} onSubmit={submit} aria-busy={action.busy} aria-label={'Biểu mẫu ' + titles[screen]}>
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
