import { useEffect, useState } from 'react';
import { api, send, type Audit, type History, type Me, type Organization, type Page, type User } from './api';
import {
  EmptyState, Field, Icon, LoadingState, Notice, Pagination, roleNames, StatusBadge, statusNames, useAction
} from './components';

function initials(name: string) {
  const words = name.trim().split(/\s+/).filter(Boolean);
  return (words.length > 1 ? words[0].charAt(0) + words[words.length - 1].charAt(0) : words[0]?.slice(0, 2) || 'UB').toUpperCase();
}

function timestamp(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });
}

/** Keep each read's loading/error state separate from mutations and ignore stale responses. */
function useRemotePage<T>(url: string | null, revision = 0) {
  const [data, setData] = useState<Page<T> | null>(null);
  const [loading, setLoading] = useState(Boolean(url));
  const [error, setError] = useState('');
  const [loadedKey, setLoadedKey] = useState('');
  const key = (url || '') + ':' + revision;
  useEffect(() => {
    if (!url) return;
    let active = true;
    setLoading(true);
    setError('');
    void api<Page<T>>(url).then(found => {
      if (active) setData(found);
    }).catch(problem => {
      if (active) setError(problem instanceof Error ? problem.message : 'Không thể tải dữ liệu. Vui lòng thử lại.');
    }).finally(() => {
      if (active) { setLoadedKey(key); setLoading(false); }
    });
    return () => { active = false; };
  }, [url, revision]);
  return { data, loading: Boolean(url) && (loading || loadedKey !== key), error };
}

function ReadFailure({ message, retry }: { message: string; retry: () => void }) {
  return <div role="status" aria-live="polite"><EmptyState icon="refresh" title="Chưa tải được dữ liệu" description={message}>
    <button className="secondary" type="button" onClick={retry}><Icon name="refresh" size={16} />Thử lại</button>
  </EmptyState></div>;
}

export function UserManagement({ me, permissions }: { me: Me; permissions: Set<string> }) {
  const [selected, setSelected] = useState<User | null>(null);
  const [search, setSearch] = useState(''), [status, setStatus] = useState('');
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const listing = useRemotePage<User>('/admin/users?' + new URLSearchParams({
    page: String(page), search, ...(status ? { status } : {})
  }), revision);
  const refresh = () => setRevision(value => value + 1);

  useEffect(() => {
    if (listing.loading || listing.error || !listing.data) return;
    const content = listing.data.content;
    setSelected(previous => previous ? content.find(user => user.userId === previous.userId) || null : null);
  }, [listing.data, listing.loading, listing.error]);

  return <div className="stack">
    <section className="panel management-panel">
      <div className="section-heading">
        <div><p className="section-kicker">QUẢN TRỊ NGƯỜI DÙNG</p><h2>Danh sách tài khoản</h2>
          <p className="section-description">Quản lý trạng thái truy cập và vai trò của từng thành viên.</p></div>
        <div className="table-actions">
          {listing.data && <span className="result-count">{listing.data.totalElements} tài khoản</span>}
          <button className="secondary btn-icon" type="button" aria-label="Tải lại danh sách tài khoản" title="Tải lại" onClick={refresh} disabled={listing.loading}><Icon name="refresh" size={17} /></button>
        </div>
      </div>

      <div className="filters user-filters">
        <div className="filter-search"><Field label="Tìm tên hoặc email" value={search} icon="search"
          placeholder="Nhập tên hoặc địa chỉ email…" onChange={value => { setSearch(value); setPage(0); }} required={false} maxLength={150} /></div>
        <label>Trạng thái<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
          <option value="">Tất cả trạng thái</option>
          {Object.entries(statusNames).filter(([key]) => ['ACTIVE', 'SUSPENDED', 'DEACTIVATED', 'PENDING_ACTIVATION'].includes(key))
            .map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select></label>
        {(search || status) && <button className="text-button" type="button" onClick={() => { setSearch(''); setStatus(''); setPage(0); }}>
          <Icon name="close" size={16} />Xóa bộ lọc
        </button>}
      </div>

      {listing.loading ? <LoadingState label="Đang tải tài khoản…" />
        : listing.error ? <ReadFailure message={listing.error} retry={refresh} />
        : listing.data?.content.length === 0 ? <EmptyState icon="users" title="Không có tài khoản phù hợp"
          description="Thử tìm bằng tên hoặc email khác, hoặc thay đổi bộ lọc trạng thái." />
        : <div className="table-wrap"><table className="data-table">
          <thead><tr><th>Tài khoản</th><th>Trạng thái</th><th>Vai trò</th><th className="table-action-heading">Thao tác</th></tr></thead>
          <tbody>{listing.data?.content.map(user => <tr key={user.userId} className={selected?.userId === user.userId ? 'table-row-selected' : undefined}>
            <td><div className="table-person"><span className="table-avatar" aria-hidden="true">{initials(user.displayName)}</span>
              <div className="table-person-copy"><b>{user.displayName}</b><small>{user.email}</small></div></div></td>
            <td><StatusBadge status={user.status} /></td>
            <td><div className="role-list">{[...new Set(user.memberships.map(member => member.role))].map(role => <span key={role} className="role-chip">{roleNames[role] || role}</span>)}
              {user.memberships.length === 0 && <span className="muted">Chưa có vai trò</span>}</div></td>
            <td className="table-action-cell"><button className="secondary table-detail-button" type="button" onClick={() => setSelected(user)}>Chi tiết<Icon name="chevron-right" size={15} /></button></td>
          </tr>)}</tbody>
        </table></div>}
      {!listing.error && <Pagination page={listing.data} busy={listing.loading} change={setPage} />}
    </section>
    {selected && <AccountDetail key={selected.userId} user={selected} currentId={me.userId} permissions={permissions} refresh={refresh} close={() => setSelected(null)} />}
  </div>;
}

function AccountDetail({ user, currentId, permissions, refresh, close }: {
  user: User; currentId: string; permissions: Set<string>; refresh: () => void; close: () => void
}) {
  const [reason, setReason] = useState('SECURITY_REVIEW'), [role, setRole] = useState('STUDENT');
  const [organization, setOrganization] = useState(''), [orgPage, setOrgPage] = useState(0);
  const [history, setHistory] = useState<Page<History> | null>(null);
  const organizations = useRemotePage<Organization>(permissions.has('membership:admin:write')
    ? '/admin/organizations?size=100&page=' + orgPage : null);
  const action = useAction();
  const loadHistory = async (historyPage = 0) => setHistory(await api<Page<History>>('/admin/users/' + user.userId + '/status-history?page=' + historyPage));
  const transitions: Record<string, string[]> = {
    ACTIVE: ['SUSPENDED', 'DEACTIVATED'], SUSPENDED: ['ACTIVE', 'DEACTIVATED'],
    PENDING_ACTIVATION: ['SUSPENDED', 'DEACTIVATED'], DEACTIVATED: []
  };
  const changeStatus = (next: string) => void action.run(async () => {
    if (!window.confirm('Xác nhận thay đổi trạng thái tài khoản ' + user.email + '?')) return;
    await send('/admin/users/' + user.userId + '/status', 'PATCH', { status: next, reasonCode: reason });
    refresh();
    await loadHistory();
    action.setMessage('Đã cập nhật trạng thái và thu hồi phiên cũ.');
  });

  return <section className="panel detail-panel" aria-label="Chi tiết tài khoản">
    <div className="detail-heading">
      <div className="table-person"><span className="table-avatar avatar-large" aria-hidden="true">{initials(user.displayName)}</span>
        <div className="table-person-copy"><p className="section-kicker">CHI TIẾT TÀI KHOẢN</p><h2>{user.displayName}</h2><p className="section-description">{user.email}</p></div></div>
      <div className="table-actions"><StatusBadge status={user.status} /><button className="secondary btn-icon" type="button" aria-label="Đóng chi tiết tài khoản" onClick={close}><Icon name="close" size={18} /></button></div>
    </div>
    <p className="detail-reference">Mã tài khoản <code className="subtle-code">{user.userId}</code></p>
    <Notice message={action.message} tone={action.error ? 'error' : 'success'} />
    <div className="detail-grid">
      <section className="detail-section">
        <div className="inline-heading"><span className="section-icon"><Icon name="shield" size={19} /></span><div><h3>Trạng thái tài khoản</h3><p className="form-description">Kiểm soát khả năng đăng nhập và các phiên đang hoạt động.</p></div></div>
        {user.lockedUntil && <p className="notice"><Icon name="clock" size={16} />Khóa đăng nhập tạm đến: {timestamp(user.lockedUntil)}</p>}
        <label>Lý do thay đổi<select value={reason} onChange={event => setReason(event.target.value)}>
          <option value="SECURITY_REVIEW">Rà soát bảo mật</option><option value="REVIEW_COMPLETE">Hoàn tất rà soát</option><option value="PROGRAM_COMPLETED">Kết thúc chương trình</option>
        </select></label>
        <div className="button-row">{(transitions[user.status] || []).map(next => <button key={next} type="button"
          className={next === 'DEACTIVATED' ? 'danger' : next === 'SUSPENDED' ? 'secondary' : ''}
          disabled={action.busy || currentId === user.userId && next !== 'ACTIVE'} onClick={() => changeStatus(next)}>
          <Icon name={next === 'ACTIVE' ? 'check' : 'shield'} size={16} />{next === 'ACTIVE' ? 'Mở khóa' : next === 'SUSPENDED' ? 'Khóa tài khoản' : 'Vô hiệu hóa'}
        </button>)}</div>
        {user.status === 'DEACTIVATED' && <p className="form-description">Tài khoản đã được vô hiệu hóa và không hỗ trợ mở lại tại đây.</p>}
        {currentId === user.userId && <p className="form-description">Bạn không thể khóa hoặc thu hồi vai trò quản trị của chính mình.</p>}
        {permissions.has('account:admin:revoke') && <button className="secondary full-width-action" type="button" disabled={action.busy} onClick={() => void action.run(async () => {
          await send('/admin/users/' + user.userId + '/revoke-sessions', 'PATCH');
          action.setMessage('Đã thu hồi các phiên cũ.');
        })}><Icon name="key" size={16} />Thu hồi phiên đăng nhập</button>}
        <button className="text-button history-toggle" type="button" disabled={action.busy} onClick={() => void action.run(() => loadHistory())}><Icon name="clock" size={16} />Xem lịch sử trạng thái</button>
      </section>

      {permissions.has('membership:admin:write') && <section className="detail-section">
        <div className="inline-heading"><span className="section-icon"><Icon name="users" size={19} /></span><div><h3>Vai trò đang hoạt động</h3><p className="form-description">Mỗi vai trò có phạm vi theo tổ chức được cấp.</p></div></div>
        {user.memberships.length === 0 && <p className="empty-inline">Tài khoản chưa được cấp vai trò nào.</p>}
        <div className="membership-list">{user.memberships.map((member, index) => <div className="membership" key={member.role + ':' + (member.organizationId || '') + ':' + index}>
          <div><b>{roleNames[member.role]}</b><small className="subtle-code">{member.organizationId || 'Toàn hệ thống'}</small></div>
          <button className="secondary" type="button" disabled={action.busy || currentId === user.userId && member.role === 'ADMIN'} onClick={() => void action.run(async () => {
            if (!window.confirm('Thu hồi vai trò này và các phiên cũ?')) return;
            await send('/admin/memberships', 'DELETE', { userId: user.userId, ...member });
            refresh();
            action.setMessage('Đã thu hồi vai trò.');
          })}>Thu hồi</button>
        </div>)}</div>
        <form className="membership-form" onSubmit={event => {
          event.preventDefault();
          void action.run(async () => {
            await send('/admin/memberships', 'POST', { userId: user.userId, role, organizationId: role === 'ADMIN' ? null : organization });
            refresh();
            action.setMessage('Đã cấp vai trò. Các phiên cũ cần đăng nhập lại.');
          });
        }}>
          <h4 className="form-section-title">Cấp thêm vai trò</h4>
          <label>Vai trò mới<select value={role} onChange={event => { setRole(event.target.value); setOrganization(''); }}>
            {Object.entries(roleNames).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select></label>
          {role !== 'ADMIN' && <label>Tổ chức<select required value={organization} disabled={organizations.loading || Boolean(organizations.error)} onChange={event => setOrganization(event.target.value)}>
            <option value="">{organizations.loading ? 'Đang tải tổ chức…' : 'Chọn tổ chức'}</option>
            {organizations.data?.content.filter(org => org.type === (role === 'ENTERPRISE' ? 'ENTERPRISE' : 'UNIVERSITY'))
              .map(org => <option key={org.id} value={org.id}>{org.name}</option>)}
          </select></label>}
          {organizations.error && <Notice message={organizations.error} tone="error" />}
          {organizations.data && organizations.data.totalPages > 1 && <Pagination page={organizations.data} busy={organizations.loading} change={next => { setOrgPage(next); setOrganization(''); }} />}
          <button type="submit" disabled={action.busy || user.status === 'DEACTIVATED' || role !== 'ADMIN' && organizations.loading}><Icon name="plus" size={16} />Cấp vai trò</button>
        </form>
      </section>}
    </div>

    {history && <section className="history-section">
      <div className="section-heading"><div><h3>Lịch sử trạng thái</h3><p className="section-description">Theo dõi các lần thay đổi và lý do được ghi nhận.</p></div><span className="result-count">{history.totalElements} thay đổi</span></div>
      {history.content.length === 0 ? <EmptyState icon="clock" title="Chưa có thay đổi trạng thái" description="Các thay đổi trong tương lai sẽ được ghi nhận tại đây." />
        : <div className="table-wrap"><table className="data-table history-table"><thead><tr><th>Thời gian</th><th>Chuyển trạng thái</th><th>Lý do</th><th>Người thực hiện</th></tr></thead>
          <tbody>{history.content.map(item => <tr key={item.id}>
            <td><time dateTime={item.changedAt} title={item.changedAt}>{timestamp(item.changedAt)}</time></td>
            <td><div className="status-transition"><StatusBadge status={item.fromStatus} /><Icon name="arrow-right" size={14} /><StatusBadge status={item.toStatus} /></div></td>
            <td><code className="subtle-code">{item.reasonCode}</code></td><td><code className="subtle-code">{item.actorId}</code></td>
          </tr>)}</tbody></table></div>}
      <Pagination page={history} busy={action.busy} change={next => void action.run(() => loadHistory(next))} />
    </section>}
  </section>;
}

export function OrganizationManagement({ permissions }: { permissions: Set<string> }) {
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const listing = useRemotePage<Organization>('/admin/organizations?page=' + page, revision);
  const [name, setName] = useState(''), [type, setType] = useState('ENTERPRISE');
  const [email, setEmail] = useState(''), [role, setRole] = useState('ENTERPRISE'), [org, setOrg] = useState('');
  const action = useAction();
  const refresh = () => setRevision(value => value + 1);

  return <div className="stack">
    <section className="panel management-panel">
      <div className="section-heading"><div><p className="section-kicker">MẠNG LƯỚI KẾT NỐI</p><h2>Tổ chức</h2>
        <p className="section-description">Quản lý danh tính tổ chức và kết nối thành viên vào đúng vai trò.</p></div>
        <div className="table-actions">{listing.data && <span className="result-count">{listing.data.totalElements} tổ chức</span>}
          <button className="secondary btn-icon" type="button" aria-label="Tải lại danh sách tổ chức" title="Tải lại" disabled={listing.loading} onClick={refresh}><Icon name="refresh" size={17} /></button></div>
      </div>
      <Notice message={action.message} tone={action.error ? 'error' : 'success'} />
      {listing.loading ? <LoadingState label="Đang tải tổ chức…" />
        : listing.error ? <ReadFailure message={listing.error} retry={refresh} />
        : listing.data?.content.length === 0 ? <EmptyState icon="building" title="Chưa có tổ chức"
          description={permissions.has('organization:admin:create') ? 'Tạo tổ chức đầu tiên bằng biểu mẫu bên dưới để bắt đầu kết nối thành viên.' : 'Các tổ chức sẽ xuất hiện sau khi được quản trị viên thêm vào hệ thống.'} />
        : <div className="table-wrap"><table className="data-table">
          <thead><tr><th>Tên tổ chức</th><th>Loại</th><th>Trạng thái</th></tr></thead><tbody>{listing.data?.content.map(item => <tr key={item.id}>
            <td><div className="table-person"><span className="table-avatar organization-avatar" aria-hidden="true"><Icon name="building" size={21} /></span>
              <div className="table-person-copy"><b>{item.name}</b><small><code className="subtle-code">{item.id}</code></small></div></div></td>
            <td><span className="role-chip">{item.type === 'ENTERPRISE' ? 'Doanh nghiệp' : 'Nhà trường'}</span></td>
            <td><StatusBadge status={item.status} /></td>
          </tr>)}</tbody>
        </table></div>}
      {!listing.error && <Pagination page={listing.data} busy={listing.loading} change={next => { setPage(next); setOrg(''); }} />}
    </section>

    <div className="dashboard-grid organization-forms">
      {permissions.has('organization:admin:create') && <section className="panel form-panel">
        <form onSubmit={event => {
          event.preventDefault();
          void action.run(async () => {
            await send('/admin/organizations', 'POST', { name, type });
            setName(''); setPage(0); refresh();
            action.setMessage('Đã tạo tổ chức ở trạng thái chờ xác thực.');
          });
        }}>
          <div className="inline-heading"><span className="section-icon"><Icon name="building" size={21} /></span><div><p className="section-kicker">THÊM VÀO MẠNG LƯỚI</p><h2>Tạo tổ chức</h2></div></div>
          <p className="form-description">Tổ chức mới được tạo ở trạng thái chờ xác thực. Quyền sử dụng chức năng sẽ tùy thuộc kết quả xác thực.</p>
          <Field label="Tên tổ chức" value={name} onChange={setName} maxLength={200} placeholder="Tên doanh nghiệp hoặc nhà trường" />
          <label>Loại tổ chức<select value={type} onChange={event => setType(event.target.value)}>
            <option value="ENTERPRISE">Doanh nghiệp</option><option value="UNIVERSITY">Nhà trường</option>
          </select></label>
          <button type="submit" disabled={action.busy}><Icon name="plus" size={17} />Tạo tổ chức</button>
        </form>
      </section>}

      <section className="panel form-panel">
        <form onSubmit={event => {
          event.preventDefault();
          void action.run(async () => {
            await send('/admin/invitations', 'POST', { email, role, organizationId: role === 'ADMIN' ? null : org });
            setEmail('');
            action.setMessage('Đã gửi lời mời. Liên kết có hiệu lực trong 48 giờ.');
          });
        }}>
          <div className="inline-heading"><span className="section-icon"><Icon name="mail" size={21} /></span><div><p className="section-kicker">KẾT NỐI THÀNH VIÊN</p><h2>Mời tài khoản</h2></div></div>
          <p className="form-description">Gửi liên kết kích hoạt đến email của thành viên và cấp vai trò theo tổ chức.</p>
          <Field label="Email người nhận" type="email" value={email} onChange={setEmail} maxLength={254} placeholder="thanhvien@example.com" icon="mail" />
          <label>Vai trò<select value={role} onChange={event => { setRole(event.target.value); setOrg(''); }}>
            {Object.entries(roleNames).map(([key, label]) => <option key={key} value={key}>{label}</option>)}
          </select></label>
          {role !== 'ADMIN' && <label>Tổ chức nhận vai trò<select required value={org} disabled={listing.loading || Boolean(listing.error)} onChange={event => setOrg(event.target.value)}>
            <option value="">{listing.loading ? 'Đang tải tổ chức…' : 'Chọn tổ chức trong trang hiện tại'}</option>
            {listing.data?.content.filter(item => item.type === (role === 'ENTERPRISE' ? 'ENTERPRISE' : 'UNIVERSITY'))
              .map(item => <option key={item.id} value={item.id}>{item.name}</option>)}
          </select></label>}
          {role !== 'ADMIN' && listing.data && listing.data.totalPages > 1 && <p className="form-description">Chuyển trang danh sách phía trên nếu tổ chức cần chọn chưa xuất hiện.</p>}
          <button type="submit" disabled={action.busy || role !== 'ADMIN' && listing.loading}><Icon name="mail" size={17} />Gửi lời mời</button>
        </form>
      </section>
    </div>
  </div>;
}

export function AuditViewer() {
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const [event, setEvent] = useState(''), [actor, setActor] = useState(''), [result, setResult] = useState('');
  const [from, setFrom] = useState(''), [until, setUntil] = useState(''), [org, setOrg] = useState('');
  const [query, setQuery] = useState(''), [filterError, setFilterError] = useState('');
  const listing = useRemotePage<Audit>('/admin/audit-logs?page=' + page + query, revision);
  const refresh = () => setRevision(value => value + 1);
  const clearFilters = () => {
    setEvent(''); setActor(''); setResult(''); setFrom(''); setUntil(''); setOrg('');
    setFilterError(''); setPage(0); setQuery(''); refresh();
  };

  return <section className="panel management-panel audit-panel">
    <div className="section-heading"><div><p className="section-kicker">THEO DÕI & ĐỐI SOÁT</p><h2>Nhật ký hệ thống</h2>
      <p className="section-description">Tra cứu các sự kiện bảo mật và thao tác quản trị đã được ghi nhận.</p></div>
      <div className="table-actions">{listing.data && <span className="result-count">{listing.data.totalElements} sự kiện</span>}
        <button className="secondary btn-icon" type="button" aria-label="Tải lại nhật ký" title="Tải lại" disabled={listing.loading} onClick={refresh}><Icon name="refresh" size={17} /></button></div>
    </div>

    <form className="audit-filter-form" onSubmit={submit => {
      submit.preventDefault();
      if (from && until && new Date(from).getTime() >= new Date(until).getTime()) {
        setFilterError('Thời điểm kết thúc cần sau thời điểm bắt đầu.'); return;
      }
      const parameters = new URLSearchParams();
      if (event) parameters.set('action', event);
      if (actor) parameters.set('actorId', actor);
      if (org) parameters.set('organizationId', org);
      if (result) parameters.set('result', result);
      if (from) parameters.set('from', new Date(from).toISOString());
      if (until) parameters.set('until', new Date(until).toISOString());
      setFilterError(''); setPage(0); setQuery('&' + parameters); refresh();
    }}>
      <div className="filters audit-filters">
        <Field label="Mã sự kiện" value={event} onChange={setEvent} required={false} placeholder="Ví dụ: LOGIN" pattern="[A-Z0-9_]+" maxLength={90} icon="activity" />
        <Field label="Mã người thực hiện" value={actor} onChange={setActor} required={false} maxLength={36} placeholder="Mã tài khoản cần tra cứu" />
        <label>Kết quả<select value={result} onChange={change => setResult(change.target.value)}>
          <option value="">Tất cả kết quả</option><option value="SUCCESS">Thành công</option><option value="FAILURE">Thất bại</option><option value="DENIED">Bị từ chối</option>
        </select></label>
        <Field label="Mã tổ chức" value={org} onChange={setOrg} required={false} maxLength={36} placeholder="Lọc theo tổ chức" />
        <Field label="Từ thời điểm" type="datetime-local" value={from} onChange={setFrom} required={false} />
        <Field label="Đến trước thời điểm" type="datetime-local" value={until} onChange={setUntil} required={false} />
      </div>
      <div className="filter-actions"><p className="form-description"><Icon name="filter" size={15} />Kết hợp bộ lọc để tìm đúng sự kiện cần đối soát.</p>
        <div className="button-row"><button className="secondary" type="button" onClick={clearFilters}>Đặt lại bộ lọc</button>
          <button type="submit" disabled={listing.loading}><Icon name="search" size={16} />Lọc nhật ký</button></div>
      </div>
    </form>
    <Notice message={filterError} tone="error" />

    {listing.loading ? <LoadingState label="Đang tải nhật ký…" />
      : listing.error ? <ReadFailure message={listing.error} retry={refresh} />
      : listing.data?.content.length === 0 ? <EmptyState icon="activity" title="Không có sự kiện phù hợp"
        description="Thử mở rộng khoảng thời gian hoặc thay đổi các điều kiện tìm kiếm." />
      : <div className="table-wrap"><table className="data-table audit-table">
        <thead><tr><th>Thời gian</th><th>Sự kiện / kết quả</th><th>Tài nguyên</th><th>Người thực hiện / truy vết</th></tr></thead>
        <tbody>{listing.data?.content.map(log => <tr key={log.id}>
          <td><time dateTime={log.occurredAt} title={log.occurredAt}>{timestamp(log.occurredAt)}</time></td>
          <td><div className="audit-event-line"><b>{log.action}</b>{' '}<StatusBadge status={log.result} /></div></td>
          <td><span className="resource-name">{log.resourceType}</span><small><code className="subtle-code">{log.resourceId || 'Không áp dụng'}</code></small>
            {log.reasonCode && <small className="audit-reason">{log.reasonCode}</small>}</td>
          <td><code className="subtle-code">{log.actorId || 'Chưa xác thực'}</code><small className="audit-request-id">Mã yêu cầu: <span>{log.requestId || '—'}</span></small></td>
        </tr>)}</tbody>
      </table></div>}
    {!listing.error && <Pagination page={listing.data} busy={listing.loading} change={setPage} />}
  </section>;
}
