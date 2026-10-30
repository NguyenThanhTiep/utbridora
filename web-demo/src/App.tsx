import { useEffect, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { api, send, login, logout, whoAmI, ApiError, type Me, type Grant } from './api';
import { Brand, EmptyState, Field, Icon, Notice, useAction, roleNames, permissionNames } from './components';
import { UserManagement, OrganizationManagement, AuditViewer } from './management';

const screens = [
  { id: 'account', title: 'Tài khoản của tôi', description: 'Thông tin cá nhân, vai trò và bảo mật tài khoản.', icon: 'grid', permission: null },
  { id: 'users', title: 'Tài khoản & phân quyền', description: 'Quản lý thành viên và quyền truy cập trong hệ thống.', icon: 'users', permission: 'account:admin:status' },
  { id: 'organizations', title: 'Tổ chức & lời mời', description: 'Kết nối các tổ chức và mời thành viên tham gia.', icon: 'building', permission: 'membership:admin:write' },
  { id: 'audit', title: 'Nhật ký hệ thống', description: 'Theo dõi và tra cứu các hoạt động quan trọng.', icon: 'activity', permission: 'audit:global:read' }
];
function initials(name: string) {
  const words = name.trim().split(/\s+/);
  return (words.length > 1 ? words[0][0] + words[words.length - 1][0] : words[0]?.slice(0, 2) || 'UB').toUpperCase();
}

export default function App() {
  const [me, setMe] = useState<Me | null>(null);
  const [grants, setGrants] = useState<Grant[]>([]);
  const [initializing, setInitializing] = useState(true);
  const [screen, setScreen] = useState('account');
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
  const action = useAction();
  const permissions = new Set(grants.filter(grant => grant.role === 'ADMIN' && grant.organizationId === null).flatMap(grant => grant.permissions));
  const menus = screens.filter(item => item.permission === null || permissions.has(item.permission));
  const current = screens.find(item => item.id === screen) || screens[0];

  useEffect(() => {
    const authTitles: Record<string, string> = { login: 'Đăng nhập', forgot: 'Quên mật khẩu', reset: 'Đặt lại mật khẩu', activate: 'Kích hoạt tài khoản' };
    document.title = (me ? current.title : authTitles[authScreen] || 'Không gian kết nối') + ' | UT.Bridora';
  }, [me?.userId, screen, authScreen]);

  useEffect(() => {
    let active = true;
    const currentGeneration = ++generation.current;
    if (location.hash) history.replaceState(null, '', location.pathname);
    void (async () => {
      try {
        const user = await whoAmI();
        const effective = await api<Grant[]>('/auth/me/permissions');
        if (active && generation.current === currentGeneration) { setMe(user); setGrants(effective); }
      } catch (error) {
        if (active && !(error instanceof ApiError && error.status === 401)) action.setMessage('Không thể tải phiên. Vui lòng thử đăng nhập lại.');
      } finally { if (active) setInitializing(false); }
    })();
    const expired = () => {
      generation.current += 1;
      setMe(null); setGrants([]); setMobileOpen(false); setAuthScreen('login');
      setSessionMessage('Phiên đã hết hạn hoặc bị thu hồi. Vui lòng đăng nhập lại.');
    };
    window.addEventListener('session-expired', expired);
    return () => { active = false; window.removeEventListener('session-expired', expired); };
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

  const navigate = (next: string) => { setScreen(next); setMobileOpen(false); };
  const signOut = async () => {
    await logout(); generation.current += 1;
    setMe(null); setGrants([]); setContext('0'); setMobileOpen(false);
    setAuthScreen('login'); setSessionMessage(''); history.replaceState(null, '', '/');
  };
  const signedIn = async (user: Me) => {
    const effective = await api<Grant[]>('/auth/me/permissions');
    generation.current += 1;
    setGrants(effective); setMe(user); setScreen('account'); setContext('0'); setSessionMessage('');
  };
  const navigateAuth = (next: string) => { setAuthScreen(next); action.setMessage(''); setSessionMessage(''); };

  if (initializing) return <main className="startup"><Brand /><div className="startup-status" role="status"><span className="spinner" />Đang chuẩn bị không gian của bạn…</div></main>;
  if (!me) return <AuthLayout><AuthForm screen={authScreen} token={token} clearToken={() => setToken('')}
    navigate={navigateAuth} signedIn={signedIn} />
    <Notice message={sessionMessage || action.message} tone={sessionMessage ? 'info' : action.error ? 'error' : 'info'} />
  </AuthLayout>;

  // A mail link can be opened in a browser which is already signed in.
  // Keep its token in memory and provide an explicit session switch.
  if (token && ['reset', 'activate'].includes(authScreen)) return <AuthLayout>
    <span className="auth-form-icon"><Icon name="mail" size={26} /></span>
    <h2 className="auth-title">Tiếp tục với liên kết email</h2>
    <p className="auth-description">Bạn đang đăng nhập bằng tài khoản <strong>{me.email}</strong>. Đăng xuất trước khi sử dụng liên kết này.</p>
    <Notice message={action.message} tone={action.error ? 'error' : 'info'} />
    <button className="full" disabled={action.busy} onClick={() => void action.run(async () => {
      await logout(); generation.current += 1; setMe(null); setGrants([]);
    })}>Đăng xuất và mở liên kết<Icon name="arrow-right" size={18} /></button>
    <button className="text-button full" disabled={action.busy} onClick={() => { setToken(''); setAuthScreen('login'); history.replaceState(null, '', '/'); }}>Về tài khoản của tôi</button>
  </AuthLayout>;

  return <div className="workspace">
    <a className="skip-link" href="#main-content">Bỏ qua điều hướng</a>
    {mobileOpen && <button className="sidebar-backdrop" aria-label="Đóng phần nền menu" onClick={() => setMobileOpen(false)} tabIndex={-1} />}
    <aside className={'sidebar' + (mobileOpen ? ' is-open' : '')} ref={sidebar} id="main-navigation">
      <div className="sidebar-brand"><a href="/" aria-label="UT.Bridora trang chủ"><Brand /></a>
        <button className="btn-icon sidebar-close" aria-label="Đóng menu điều hướng" onClick={() => setMobileOpen(false)}><Icon name="close" /></button></div>
      <nav aria-label="Điều hướng chính">
        <p className="nav-group-label">KHÔNG GIAN CỦA BẠN</p>
        {menus.filter(item => item.id === 'account').map(item => <button key={item.id} className={'nav' + (screen === item.id ? ' active' : '')}
          aria-current={screen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
        {menus.some(item => item.id !== 'account') && <p className="nav-group-label nav-group-admin">QUẢN TRỊ HỆ THỐNG</p>}
        {menus.filter(item => item.id !== 'account').map(item => <button key={item.id} className={'nav' + (screen === item.id ? ' active' : '')}
          aria-current={screen === item.id ? 'page' : undefined} onClick={() => navigate(item.id)}><Icon name={item.icon} /><span>{item.title}</span></button>)}
      </nav>
      <div className="sidebar-bottom">
        <div className="sidebar-user"><span className="avatar">{initials(me.displayName)}</span><div><b>{me.displayName}</b><small>{me.email}</small></div></div>
        <button className="sidebar-logout" disabled={action.busy} onClick={() => void action.run(signOut)}><Icon name="logout" size={18} />Đăng xuất</button>
      </div>
      <p className="sidebar-caption">Nhà trường · Doanh nghiệp · Sinh viên</p>
    </aside>

    <div className="workspace-main">
      <header className="topbar">
        <div className="topbar-location"><button className="btn-icon menu-toggle" ref={menuButton} aria-label="Mở menu điều hướng"
          aria-expanded={mobileOpen} aria-controls="main-navigation" onClick={() => setMobileOpen(previous => !previous)}><Icon name="menu" /></button>
          <span className="topbar-home"><Icon name="layers" size={17} />Không gian làm việc</span><Icon name="chevron-right" size={14} /><span className="topbar-current">{current.title}</span></div>
        <div className="topbar-right"><span className="topbar-date"><Icon name="calendar" size={16} />{new Intl.DateTimeFormat('vi-VN', { day: '2-digit', month: '2-digit', year: 'numeric', timeZone: 'Asia/Bangkok' }).format(new Date())}</span>
          <button className="profile-shortcut" aria-label="Mở tài khoản của tôi" onClick={() => navigate('account')}><span className="avatar avatar-small">{initials(me.displayName)}</span><span>{me.displayName}</span><Icon name="chevron-down" size={14} /></button></div>
      </header>
      <main className="content" id="main-content">
        <div className="page-header"><div><p className="section-kicker">{screen === 'account' ? 'KHÔNG GIAN CÁ NHÂN' : 'QUẢN TRỊ NỀN TẢNG'}</p>
          <h1 ref={heading} tabIndex={-1}>{current.title}</h1><p className="page-description">{current.description}</p></div>
          <span className="workspace-label"><span className="live-dot" />UT.Bridora</span></div>
        <Notice message={action.message} tone={action.error ? 'error' : 'info'} />
        {!menus.some(item => item.id === screen) ? <section className="panel"><EmptyState icon="lock" title="Không có quyền truy cập" description="Chọn một chức năng trong menu để tiếp tục." /></section>
          : screen === 'users' ? <UserManagement me={me} permissions={permissions} />
          : screen === 'organizations' ? <OrganizationManagement permissions={permissions} />
          : screen === 'audit' ? <AuditViewer />
          : <AccountOverview me={me} grants={grants} context={context} setContext={setContext}
            signedOut={() => { generation.current += 1; setMe(null); setGrants([]); setAuthScreen('login'); setSessionMessage('Đổi mật khẩu thành công. Vui lòng đăng nhập lại.'); }} />}
        <footer className="app-footer"><span>© {new Date().getFullYear()} UT.Bridora</span><span>Kết nối giáo dục với cơ hội nghề nghiệp.</span></footer>
      </main>
    </div>
  </div>;
}

function AuthLayout({ children }: { children: ReactNode }) {
  return <main className="auth-shell">
    <section className="auth-story">
      <a href="/" className="auth-brand" aria-label="UT.Bridora trang chủ"><Brand inverse /></a>
      <div className="auth-story-content"><span className="auth-tag"><span />NHÀ TRƯỜNG · DOANH NGHIỆP · SINH VIÊN</span>
        <h1>Kết nối tri thức<br />với <em>cơ hội thực tế.</em></h1>
        <p>Một không gian chung để nhà trường, doanh nghiệp và sinh viên cùng xây dựng hành trình phát triển.</p>
        <div className="connection-art" aria-hidden="true">
          <svg viewBox="0 0 480 220" className="connection-lines"><path d="M85 136C150 136 145 66 240 66s90 70 155 70M85 136c55 0 65 50 155 50s100-50 155-50" /><path className="connection-line-dashed" d="M85 136h310M240 66v120" /><circle cx="240" cy="136" r="42" /><circle cx="240" cy="136" r="66" /></svg>
          <div className="connection-node node-school"><span><Icon name="graduation" size={24} /></span><b>Nhà trường</b><small>Nền tảng tri thức</small></div>
          <div className="connection-center"><Icon name="layers" size={28} /></div>
          <div className="connection-node node-enterprise"><span><Icon name="briefcase" size={24} /></span><b>Doanh nghiệp</b><small>Cơ hội thực tế</small></div>
          <div className="connection-student"><Icon name="user" size={17} /><span>Sinh viên</span></div>
        </div>
      </div>
      <div className="auth-story-footer"><span className="auth-footer-rule" /><span>Cùng kết nối. Cùng phát triển.</span></div>
    </section>
    <section className="auth-entry"><div className="auth-mobile-brand"><Brand /></div><div className="auth-card">{children}</div>
      <div className="auth-entry-footer"><Icon name="shield" size={15} /><span>Chỉ sử dụng tài khoản được cấp cho bạn.</span></div>
      <p className="auth-copyright">© {new Date().getFullYear()} UT.Bridora. Mọi quyền được bảo lưu.</p>
    </section>
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
  const action = useAction();
  const submitting = useRef(false);
  const titles: Record<string, string> = { login: 'Đăng nhập', forgot: 'Quên mật khẩu', reset: 'Đặt lại mật khẩu', activate: 'Kích hoạt tài khoản' };
  const descriptions: Record<string, string> = {
    login: 'Chào mừng trở lại! Đăng nhập để tiếp tục hành trình của bạn.',
    forgot: 'Nhập email tài khoản. Chúng tôi sẽ gửi hướng dẫn giúp bạn đặt lại mật khẩu.',
    reset: 'Tạo mật khẩu mới để tiếp tục sử dụng tài khoản của bạn.',
    activate: 'Hoàn tất thông tin dưới đây để bắt đầu cùng UT.Bridora.'
  };
  const tokenFlow = ['reset', 'activate'].includes(screen);
  const changeScreen = () => {
    if (action.busy) return;
    action.setMessage(''); setPassword(''); setConfirm(''); navigate(screen === 'login' ? 'forgot' : 'login');
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
  return <form className="auth-form" onSubmit={submit} aria-busy={action.busy}>
    <span className="auth-form-icon"><Icon name={screen === 'login' ? 'user' : screen === 'forgot' ? 'mail' : screen === 'reset' ? 'key' : 'graduation'} size={25} /></span>
    <p className="auth-overline">{screen === 'login' ? 'KHÔNG GIAN CỦA BẠN' : 'TÀI KHOẢN UT.BRIDORA'}</p>
    <h2 className="auth-title">{titles[screen]}</h2><p className="auth-description">{descriptions[screen]}</p>
    <div className="auth-fields">
      {['login', 'forgot'].includes(screen) && <Field label="Email" icon="mail" type="email" value={email} onChange={setEmail}
        placeholder="tenban@example.com" autoComplete="username" maxLength={254} disabled={action.busy} />}
      {screen === 'activate' && <Field label="Họ và tên" icon="user" value={name} onChange={setName} maxLength={150} autoComplete="name" placeholder="Nhập họ và tên của bạn" disabled={action.busy} />}
      {screen !== 'forgot' && <Field label={screen === 'login' ? 'Mật khẩu' : 'Mật khẩu mới'} icon="lock" type="password" value={password}
        onChange={setPassword} placeholder={screen === 'login' ? 'Nhập mật khẩu của bạn' : 'Tạo mật khẩu mới'}
        autoComplete={screen === 'login' ? 'current-password' : 'new-password'} disabled={action.busy} />}
      {tokenFlow && <Field label="Xác nhận mật khẩu" icon="lock" type="password" value={confirm} onChange={setConfirm}
        placeholder="Nhập lại mật khẩu mới" autoComplete="new-password" disabled={action.busy} />}
    </div>
    {screen === 'login' && <div className="auth-options"><button className="text-button" type="button" disabled={action.busy} onClick={changeScreen}>Quên mật khẩu?</button></div>}
    {tokenFlow && <p className="password-requirement"><Icon name="info" size={15} />Tối thiểu 12 ký tự, tối đa 72 byte UTF-8.</p>}
    <Notice message={action.message} tone={action.error ? 'error' : 'success'} />
    {tokenFlow && !token && <Notice tone="error" message="Liên kết thiếu mã xác nhận. Vui lòng mở lại liên kết trong email hoặc yêu cầu liên kết mới." />}
    <button className="auth-submit full" disabled={action.busy || tokenFlow && !token}>
      {action.busy ? <><span className="spinner spinner-light" />Đang xử lý…</> : <>{titles[screen]}<Icon name="arrow-right" size={18} /></>}
    </button>
    {screen !== 'login' && <button className="text-button auth-back full" type="button" disabled={action.busy} onClick={changeScreen}><Icon name="arrow-left" size={16} />Quay lại đăng nhập</button>}
    {screen === 'login' && <p className="auth-invitation-note">Chưa có tài khoản? Liên hệ quản trị viên để nhận lời mời tham gia.</p>}
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
