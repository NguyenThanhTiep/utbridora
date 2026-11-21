import { useEffect, useRef, useState } from 'react';
import { api, send, type Audit, type History, type Me, type Organization, type Page, type User } from './api';
import { askConfirm } from './confirm';
import {
  EmptyState, Field, Icon, LoadingState, Notice, Pagination, roleNames, StatusBadge, statusNames, useAction
} from './components';

function initials(name: string) {
  const words = name.replace(/\([^)]*\)/g, ' ').trim().split(/\s+/).filter(w => /[\p{L}\p{N}]/u.test(w));
  return (words.length > 1 ? words[0].charAt(0) + words[words.length - 1].charAt(0) : words[0]?.slice(0, 2) || 'UB').toUpperCase();
}

function timestamp(value: string) {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? '—' : date.toLocaleString('vi-VN', {
    day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit'
  });
}

const auditActionNames: Record<string, string> = {
  LOGIN: 'Đăng nhập hệ thống', LOGOUT: 'Đăng xuất', PASSWORD_CHANGED: 'Đổi mật khẩu', PASSWORD_RESET: 'Đặt lại mật khẩu',
  INVITATION_CREATED: 'Tạo lời mời tài khoản', ACCOUNT_ACTIVATED: 'Kích hoạt tài khoản', ACCOUNT_STATUS_CHANGED: 'Đổi trạng thái tài khoản',
  MEMBERSHIP_GRANTED: 'Cấp vai trò tổ chức', MEMBERSHIP_REVOKED: 'Thu hồi vai trò', SESSIONS_REVOKED: 'Thu hồi phiên đăng nhập',
  ACCESS_DENIED: 'Từ chối truy cập', ORG_CREATED: 'Tạo tổ chức mới', ORG_DEMO_VERIFIED: 'Xác minh tổ chức trong môi trường demo',
  STUDENT_PROFILE_UPDATED: 'Cập nhật hồ sơ sinh viên', STUDENT_EVIDENCE_ADDED: 'Thêm minh chứng sinh viên', STUDENT_PROFILE_ACCESSED: 'Xem hồ sơ sinh viên',
  FILE_UPLOADED: 'Tải tệp lên', FILE_DOWNLOADED: 'Tải tệp xuống', INVITATION_RESPONDED: 'Phản hồi lời mời thực tập',
  ENTERPRISE_VERIFIED: 'Xác minh doanh nghiệp', COOPERATION_REVIEWED: 'Xét duyệt đề nghị hợp tác',
  PROFILE_SHARED: 'Chia sẻ hồ sơ sinh viên', PROFILE_SHARE_REVOKED: 'Thu hồi chia sẻ hồ sơ',
  PLACEMENT_CREATED: 'Tạo tiếp nhận thực tập', PLACEMENT_TERMINATED: 'Chấm dứt tiếp nhận thực tập',
  TASK_ASSIGNED: 'Giao công việc', SUBMISSION_CREATED: 'Tạo bài nộp công việc', SUBMISSION_REVISED: 'Sửa bài nộp công việc', SUBMISSION_REVIEWED: 'Nhận xét bài nộp công việc',
  EVALUATION_SUBMITTED: 'Gửi đánh giá thực tập', EVALUATION_PUBLISHED: 'Công bố đánh giá thực tập',
  MATCH_AGGREGATE_VIEWED: 'Xem kết quả đối chiếu tổng hợp', MATCH_EXPORT_ATTEMPTED: 'Yêu cầu xuất kết quả đối chiếu',
  DEMAND_CHANGED: 'Cập nhật nhu cầu thực tập', TAXONOMY_CHANGED: 'Cập nhật danh mục kỹ năng',
  ENTERPRISE_VERIFICATION_DRAFT_SAVED: 'Lưu nháp hồ sơ xác minh doanh nghiệp', ENTERPRISE_VERIFICATION_SUBMITTED: 'Gửi hồ sơ xác minh doanh nghiệp',
  ENTERPRISE_VERIFICATION_REJECTED: 'Từ chối xác minh doanh nghiệp', ENTERPRISE_VERIFICATION_CHANGES_REQUESTED: 'Yêu cầu bổ sung hồ sơ xác minh',
  PLACEMENT_APPROVED: 'Phê duyệt tiếp nhận thực tập', PLACEMENT_ACTIVATED: 'Kích hoạt thực tập', PLACEMENT_CANCELLED: 'Hủy tiếp nhận thực tập', PLACEMENT_COMPLETED: 'Hoàn thành thực tập',
  COHORT_CREATED: 'Tạo đợt thực tập', GROUP_MEMBERSHIP_CHANGED: 'Cập nhật thành viên nhóm',
  PRIVACY_RELEASE_CREATED: 'Tạo bản công bố dữ liệu được bảo vệ', PRIVACY_RELEASE_BLOCKED: 'Chặn công bố dữ liệu',
  CAPABILITY_GRANTED: 'Cấp quyền theo phạm vi', CAPABILITY_REVOKED: 'Thu hồi quyền theo phạm vi',
  CONSENT_GRANTED: 'Đồng ý chia sẻ dữ liệu', CONSENT_REVOKED: 'Thu hồi đồng ý chia sẻ dữ liệu',
  STUDENT_ELIGIBILITY_CHANGED: 'Cập nhật điều kiện thực tập của sinh viên', SCHOOL_PROFILE_SHARING_POLICY_CHANGED: 'Cập nhật chính sách chia sẻ hồ sơ của trường',
  COOPERATION_DRAFT_SAVED: 'Lưu nháp đề nghị hợp tác', COOPERATION_SUBMITTED: 'Gửi đề nghị hợp tác',
  PROPOSAL_ITEM_RESERVED: 'Giữ chỗ sinh viên trong đề xuất', PROPOSAL_ITEM_DECIDED: 'Quyết định đề xuất sinh viên',
  INTERNSHIP_INVITATION_ISSUED: 'Gửi lời mời thực tập', INTERNSHIP_INVITATION_REVOKED: 'Thu hồi lời mời thực tập',
  TASK_UPDATED: 'Cập nhật công việc', MATCH_RUN_REQUESTED: 'Yêu cầu đối chiếu hồ sơ', MATCH_RUN_COMPLETED: 'Hoàn tất đối chiếu hồ sơ', MATCH_DETAILS_VIEWED: 'Xem chi tiết đối chiếu',
  MENTOR_INVITATION_ACCEPTED: 'Chấp nhận lời mời hướng dẫn', ACCOUNT_INVITATION_REVOKED: 'Thu hồi lời mời tài khoản',
  ACTIVATION_INVITATION_REISSUED: 'Gửi lại lời mời kích hoạt tài khoản', INTERNSHIP_INVITATION_EXPIRED: 'Lời mời thực tập hết hạn',
  OPPORTUNITY_PUBLICATION_CHANGED: 'Cập nhật công bố cơ hội thực tập',
  PREFERENCE_SUBMITTED: 'Gửi nguyện vọng thực tập', PREFERENCE_WITHDRAWN: 'Rút nguyện vọng thực tập', PREFERENCE_REVIEWED: 'Xét nguyện vọng thực tập',
  TASK_COMMENTED: 'Bình luận công việc', WEEKLY_REPORT_DRAFT_SAVED: 'Lưu nháp báo cáo tuần', WEEKLY_REPORT_REVISION_CREATED: 'Tạo phiên bản báo cáo tuần',
  WEEKLY_REPORT_SUBMITTED: 'Nộp báo cáo tuần', WEEKLY_REPORT_REVIEWED: 'Nhận xét báo cáo tuần'
};
const auditResourceNames: Record<string, string> = {
  user: 'Tài khoản', account: 'Tài khoản', organization: 'Tổ chức', membership: 'Vai trò thành viên', invitation: 'Lời mời', account_invitation: 'Lời mời tài khoản',
  school_delegation: 'Phân công cán bộ', capability_assignment: 'Phân công quyền', skill: 'Kỹ năng', skill_category: 'Danh mục kỹ năng', taxonomy: 'Danh mục kỹ năng',
  demand: 'Nhu cầu thực tập', cooperation: 'Đề nghị hợp tác', proposal_batch: 'Lô đề xuất', proposal_item: 'Đề xuất sinh viên',
  internship_invitation: 'Lời mời thực tập', mentor_invitation: 'Lời mời hướng dẫn', placement: 'Tiếp nhận thực tập', cohort: 'Đợt thực tập',
  group: 'Nhóm thực tập', group_membership: 'Thành viên nhóm', internship_case: 'Hồ sơ thực tập',
  student_profile: 'Hồ sơ sinh viên', student_evidence: 'Minh chứng sinh viên', student_consent: 'Đồng ý chia sẻ hồ sơ', student_preference: 'Nguyện vọng thực tập',
  student_term_eligibility: 'Điều kiện thực tập', school_sharing_policy: 'Chính sách chia sẻ hồ sơ', profile_share: 'Chia sẻ hồ sơ',
  file: 'Tệp', task: 'Công việc', task_assignment: 'Phân công công việc', submission: 'Bài nộp công việc', weekly_report: 'Báo cáo tuần',
  evaluation: 'Đánh giá thực tập', match_run: 'Lượt đối chiếu hồ sơ', matching_snapshot: 'Kết quả đối chiếu', privacy_release: 'Bản công bố dữ liệu được bảo vệ'
};
const actionName = (code: string) => Object.hasOwn(auditActionNames, code) ? auditActionNames[code] : code;
const resourceName = (code: string) => Object.hasOwn(auditResourceNames, code.toLowerCase()) ? auditResourceNames[code.toLowerCase()] : code;
const shortReference = (id: string) => id.slice(0, 8) + '…';
type AuditUser = Pick<User, 'displayName' | 'email'>;

// Name enrichment is optional and bounded; it must never block the audit read.
async function readAuditDirectory<T>(path: string, signal: AbortSignal): Promise<T[]> {
  const first = await api<Page<T>>(path + '?page=0&size=100', { signal });
  const items = [...first.content];
  if (first.totalPages > 1) {
    try { items.push(...(await api<Page<T>>(path + '?page=1&size=100', { signal })).content); }
    catch { /* Keep the first page when a supplementary read is unavailable. */ }
  }
  return items;
}
const reasonNames: Record<string, string> = {
  SECURITY_REVIEW: 'Rà soát bảo mật', REVIEW_COMPLETE: 'Hoàn tất rà soát', PROGRAM_COMPLETED: 'Kết thúc chương trình'
};
const reasonLabel = (code: string) => Object.hasOwn(reasonNames, code) ? reasonNames[code] : code;

function sessionSummary(user: User): { label: string; tone: 'active' | 'pending' | 'suspended' | 'deactivated' } {
  if (user.lockedUntil && new Date(user.lockedUntil).getTime() > Date.now()) {
    return { label: 'Khóa tạm đến ' + timestamp(user.lockedUntil), tone: 'suspended' };
  }
  if (user.status === 'ACTIVE') return { label: 'Đang hiệu lực', tone: 'active' };
  if (user.status === 'PENDING_ACTIVATION') return { label: 'Chờ kích hoạt', tone: 'pending' };
  if (user.status === 'SUSPENDED') return { label: 'Tạm khóa phiên', tone: 'suspended' };
  return { label: 'Đã vô hiệu hóa', tone: 'deactivated' };
}

function useDirectoryMap<T, V>(enabled: boolean, path: string, revision: number, mapEntry: (item: T) => [string, V]) {
  const [entries, setEntries] = useState(new Map<string, V>());
  const mapper = useRef(mapEntry);
  mapper.current = mapEntry;
  useEffect(() => {
    if (!enabled) { setEntries(new Map()); return; }
    const controller = new AbortController();
    void readAuditDirectory<T>(path, controller.signal).then(items => {
      if (!controller.signal.aborted) setEntries(new Map(items.map(item => mapper.current(item))));
    }).catch(() => {});
    return () => controller.abort();
  }, [enabled, path, revision]);
  return enabled ? entries : new Map<string, V>();
}

function useAuditDirectories(permissions: Set<string>, revision: number) {
  const canReadUsers = permissions.has('audit:global:read') && permissions.has('account:admin:status');
  const canReadOrganizations = permissions.has('audit:global:read') && permissions.has('membership:admin:write');
  const userMap = useDirectoryMap<User, AuditUser>(canReadUsers, '/admin/users', revision,
    user => [user.userId, { displayName: user.displayName, email: user.email }]);
  const orgMap = useDirectoryMap<Organization, string>(canReadOrganizations, '/admin/organizations', revision,
    org => [org.id, org.name]);
  return { userMap, orgMap };
}
function AuditActor({ actorId, userMap }: { actorId: string | null; userMap: Map<string, AuditUser> }) {
  if (actorId === null) return <span className="audit-system-actor"><Icon name="shield" size={16} />Hệ thống / Chưa xác thực</span>;
  const user = userMap.get(actorId);
  return user?.displayName ? <div className="table-person audit-person" title={actorId}>
    <span className="table-avatar audit-avatar" aria-hidden="true">{initials(user.displayName)}</span>
    <div className="table-person-copy"><b>{user.displayName}</b><small>{user.email}</small></div>
  </div> : <code className="subtle-code audit-reference" title={actorId}>{shortReference(actorId)}</code>;
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

export function AdminOverview({ permissions, navigate }: { permissions: Set<string>; navigate: (screen: string) => void }) {
  const [revision, setRevision] = useState(0);
  const { userMap, orgMap } = useAuditDirectories(permissions, revision);
  const users = useRemotePage<User>(permissions.has('account:admin:status') ? '/admin/users?page=0&size=1' : null, revision);
  const organizations = useRemotePage<Organization>(permissions.has('membership:admin:write') ? '/admin/organizations?page=0&size=1' : null, revision);
  const suspended = useRemotePage<User>(permissions.has('account:admin:status') ? '/admin/users?status=SUSPENDED&page=0&size=1' : null, revision);
  const audit = useRemotePage<Audit>(permissions.has('audit:global:read') ? '/admin/audit-logs?page=0&size=5' : null, revision);
  const metrics = [
    { title: 'Tổng tài khoản', description: 'Tài khoản trong hệ thống', icon: 'ND', screen: 'users', read: users, allowed: permissions.has('account:admin:status') },
    { title: 'Tổng tổ chức', description: 'Trường & doanh nghiệp', icon: 'TC', screen: 'organizations', read: organizations, allowed: permissions.has('membership:admin:write') },
    { title: 'Tài khoản tạm ngưng', description: 'Đang bị tạm ngưng', icon: 'TN', screen: 'users', read: suspended, allowed: permissions.has('account:admin:status') },
    { title: 'Hoạt động hệ thống', description: 'Sự kiện đã ghi nhận', icon: 'HĐ', screen: 'audit', read: audit, allowed: permissions.has('audit:global:read') }
  ];
  const shortcuts = [
    { title: 'Quản lý người dùng', screen: 'users', icon: 'ND', permission: 'account:admin:status' },
    { title: 'Quản lý tổ chức', screen: 'organizations', icon: 'TC', permission: 'membership:admin:write' },
    { title: 'Danh mục kỹ năng', screen: 'taxonomy', icon: 'KN', permission: 'taxonomy:skills:read' },
    { title: 'Phân công quyền cán bộ', screen: 'delegations', icon: 'PQ', permission: 'membership:admin:write' }
  ];
  return <div className="admin-overview stack">
    <section className="admin-welcome"><div>
      <h2>Tổng quan Quản trị hệ thống</h2><p>Theo dõi tài khoản, tổ chức, danh mục và hoạt động trên nền tảng UT.Bridora.</p></div><span className="admin-welcome-icon"><Icon name="shield" size={44} /></span></section>
    <div className="section-heading"><h2 className="admin-section-title">TÌNH HÌNH TỔNG QUAN</h2><button className="secondary" onClick={() => setRevision(value => value + 1)} disabled={metrics.some(item => item.read.loading)}><Icon name="refresh" size={16} />Tải lại dữ liệu</button></div>
    <div className="admin-metrics">{metrics.map((item, index) => <button key={item.title} className={'admin-metric metric-' + index} disabled={!item.allowed} onClick={() => navigate(item.screen)}>
      <span className="admin-metric-heading"><b>{item.title}</b></span><span className="admin-metric-value"><strong>{!item.read.loading && !item.read.error && item.read.data ? item.read.data.totalElements.toLocaleString('vi-VN') : '—'}</strong><span>{item.icon}</span></span>
      <small className="admin-metric-description">{item.description}</small>
      <small>{!item.allowed ? 'Chưa có quyền xem' : item.read.error ? 'Chưa tải được dữ liệu' : item.read.loading ? 'Đang tải…' : 'Xem chi tiết →'}</small></button>)}</div>
    {metrics.some(item => item.read.error) && <Notice message="Một số số liệu chưa tải được. Bạn có thể tải lại hoặc mở màn chi tiết." tone="info" />}
    <div className="admin-overview-bottom"><section className="panel"><h2 className="admin-section-title">TRUY CẬP NHANH</h2><div className="admin-shortcuts">{shortcuts.filter(item => permissions.has(item.permission)).map(item => <button className="admin-shortcut" key={item.screen} onClick={() => navigate(item.screen)}><span className="admin-shortcut-icon">{item.icon}</span><span><b>{item.title}</b></span><Icon name="chevron-right" size={16} /></button>)}</div></section>
      <section className="panel"><h2 className="admin-section-title">NHẬT KÝ HOẠT ĐỘNG</h2><div className="admin-recent-audit">
        {audit.loading ? <LoadingState label="Đang tải hoạt động…" /> : audit.error ? <ReadFailure message={audit.error} retry={() => setRevision(value => value + 1)} /> : !permissions.has('audit:global:read') ? <p>Bạn chưa có quyền xem nhật ký hệ thống.</p> : audit.data?.content.length ? <ul>{audit.data.content.map(log => <li className="audit-preview-item" key={log.id}>
          <span className={'audit-preview-dot is-' + log.result.toLowerCase()} aria-hidden="true" />
          <div className="audit-preview-main">
            <b title={actionName(log.action)}>{actionName(log.action)}</b>
            <span className="audit-preview-identity" title={[log.actorId ? userMap.get(log.actorId)?.displayName : null, log.actorId, log.organizationId ? orgMap.get(log.organizationId) : null, log.organizationId, log.action].filter(Boolean).join(' · ')}>
              {log.actorId === null ? 'Hệ thống / Chưa xác thực' : userMap.get(log.actorId)?.displayName || shortReference(log.actorId)}
              {log.organizationId && <> · {orgMap.get(log.organizationId) || shortReference(log.organizationId)}</>}
              {' · '}<code className="subtle-code" title={log.action}>{log.action}</code>
            </span>
          </div>
          <div className="audit-preview-meta">
            <StatusBadge status={log.result} />
            <time dateTime={log.occurredAt}>{timestamp(log.occurredAt)}</time>
          </div>
        </li>)}</ul> : <p>Chưa có hoạt động để hiển thị</p>}
        {permissions.has('audit:global:read') && <button className="text-button" onClick={() => navigate('audit')}>Xem nhật ký →</button>}</div></section></div>
  </div>;
}

export function UserManagement({ me, permissions, invite }: { me: Me; permissions: Set<string>; invite?: () => void }) {
  const [showRules, setShowRules] = useState(false);
  const [selected, setSelected] = useState<User | null>(null);
  const [search, setSearch] = useState(''), [status, setStatus] = useState('');
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const detailRef = useRef<HTMLElement>(null);
  const listing = useRemotePage<User>('/admin/users?' + new URLSearchParams({
    page: String(page), search, ...(status ? { status } : {})
  }), revision);
  const orgMap = useDirectoryMap<Organization, string>(permissions.has('membership:admin:write'), '/admin/organizations', revision,
    org => [org.id, org.name]);
  const userMap = useDirectoryMap<User, AuditUser>(permissions.has('account:admin:status'), '/admin/users', revision,
    user => [user.userId, { displayName: user.displayName, email: user.email }]);
  const refresh = () => setRevision(value => value + 1);

  useEffect(() => {
    if (listing.loading || listing.error || !listing.data) return;
    const content = listing.data.content;
    setSelected(previous => previous ? content.find(user => user.userId === previous.userId) || null : null);
  }, [listing.data, listing.loading, listing.error]);

  const openDetail = (user: User) => {
    setSelected(user);
    requestAnimationFrame(() => detailRef.current?.scrollIntoView({ behavior: 'smooth', block: 'nearest' }));
  };

  return <div className="stack">
    <section className="panel management-panel">
      <div className="section-heading">
        <div><h2>Danh sách tài khoản</h2></div>
        <div className="table-actions">
          {listing.data && <span className="result-count">{listing.data.totalElements} tài khoản</span>}
          <button className="secondary btn-icon" type="button" aria-label="Tải lại danh sách tài khoản" title="Tải lại" onClick={refresh} disabled={listing.loading}><Icon name="refresh" size={17} /></button>
        </div>
      </div>

      <form className="filters user-filters" onSubmit={event => { event.preventDefault(); setPage(0); refresh(); }}>
        <div className="filter-search"><Field label="Tìm tên hoặc email" value={search} icon="search"
          placeholder="Nhập tên hoặc địa chỉ email…" onChange={value => { setSearch(value); setPage(0); }} required={false} maxLength={150} /></div>
        <label>Trạng thái<select value={status} onChange={event => { setStatus(event.target.value); setPage(0); }}>
          <option value="">Tất cả trạng thái</option>
          {Object.entries(statusNames).filter(([key]) => ['ACTIVE', 'SUSPENDED', 'DEACTIVATED', 'PENDING_ACTIVATION'].includes(key))
            .map(([key, label]) => <option key={key} value={key}>{label}</option>)}
        </select></label>
        <div className="user-filter-actions">
          <button type="submit" disabled={listing.loading}><Icon name="filter" size={16} />Áp dụng bộ lọc</button>
          {(search || status) && <button className="secondary" type="button" onClick={() => { setSearch(''); setStatus(''); setPage(0); }}>
            <Icon name="close" size={16} />Xóa bộ lọc
          </button>}
        </div>
      </form>

      {listing.loading ? <LoadingState label="Đang tải tài khoản…" />
        : listing.error ? <ReadFailure message={listing.error} retry={refresh} />
        : listing.data?.content.length === 0 ? <EmptyState icon="users" title="Không có tài khoản phù hợp"
          description="Thử tìm bằng tên hoặc email khác, hoặc thay đổi bộ lọc trạng thái." />
        : <div className="table-wrap"><table className="data-table">
          <thead><tr><th>Tài khoản</th><th>Vai trò / Phạm vi</th><th>Trạng thái</th><th>Phiên</th><th className="table-action-heading">Thao tác</th></tr></thead>
          <tbody>{listing.data?.content.map(user => {
            const session = sessionSummary(user);
            return <tr key={user.userId} className={selected?.userId === user.userId ? 'table-row-selected' : undefined}>
              <td><div className="table-person"><span className="table-avatar" aria-hidden="true">{initials(user.displayName)}</span>
                <div className="table-person-copy"><b>{user.displayName}</b><small>{user.email}</small></div></div></td>
              <td><div className="role-list">{user.memberships.map((member, index) => <span key={index} className="role-chip" title={member.organizationId || undefined}>
                <span>{roleNames[member.role] || member.role}</span>
                <small>{member.organizationId ? (orgMap.get(member.organizationId) || shortReference(member.organizationId)) : 'Toàn hệ thống'}</small>
              </span>)}
                {user.memberships.length === 0 && <span className="muted">Chưa có vai trò</span>}</div></td>
              <td><StatusBadge status={user.status} /></td>
              <td><span className={'session-state session-' + session.tone}>{session.label}</span></td>
              <td className="table-action-cell"><button className="secondary table-detail-button" type="button" onClick={() => openDetail(user)}>Chi tiết<Icon name="chevron-right" size={15} /></button></td>
            </tr>;
          })}</tbody>
        </table></div>}
      {!listing.error && <Pagination page={listing.data} busy={listing.loading} change={setPage} />}
      <div className="button-row admin-table-tools"><button className="secondary admin-outline" aria-expanded={showRules} aria-controls="account-status-rules" onClick={() => setShowRules(value => !value)}>Quy tắc trạng thái</button>
        {invite && permissions.has('membership:admin:write') && <button className="secondary admin-outline" onClick={invite}><Icon name="mail" size={16} />Mời tài khoản mới</button>}</div>
      {showRules && <div className="notice" id="account-status-rules">Đang hoạt động ↔ Tạm ngưng → Vô hiệu hóa. Tài khoản vô hiệu hóa không thể mở lại. Thay đổi trạng thái sẽ thu hồi phiên cũ; khóa đăng nhập tạm được hiển thị riêng.</div>}
    </section>
    {selected && <AccountDetail key={selected.userId} detailRef={detailRef} user={selected} currentId={me.userId} permissions={permissions} orgMap={orgMap} userMap={userMap} refresh={refresh} close={() => setSelected(null)} />}
  </div>;
}

function AccountDetail({ detailRef, user, currentId, permissions, orgMap, userMap, refresh, close }: {
  detailRef: React.RefObject<HTMLElement | null>; user: User; currentId: string; permissions: Set<string>; orgMap: Map<string, string>; userMap: Map<string, AuditUser>; refresh: () => void; close: () => void
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
    if (!await askConfirm({ title: 'Đổi trạng thái tài khoản?', message: 'Xác nhận thay đổi trạng thái tài khoản ' + user.email + '?', confirmLabel: 'Đổi trạng thái',
      tone: next === 'ACTIVE' ? 'primary' : 'danger' })) return;
    await send('/admin/users/' + user.userId + '/status', 'PATCH', { status: next, reasonCode: reason });
    refresh();
    await loadHistory();
    action.setMessage('Đã cập nhật trạng thái và thu hồi phiên cũ.');
  });

  return <section ref={detailRef} className="panel detail-panel" aria-label="Chi tiết tài khoản">
    <div className="detail-heading">
      <div className="table-person"><span className="table-avatar avatar-large" aria-hidden="true">{initials(user.displayName)}</span>
        <div className="table-person-copy"><p className="section-kicker">CHI TIẾT TÀI KHOẢN</p><h2>{user.displayName}</h2><p className="section-description">{user.email}</p></div></div>
      <div className="table-actions"><StatusBadge status={user.status} /><button className="secondary btn-icon" type="button" aria-label="Đóng chi tiết tài khoản" onClick={close}><Icon name="close" size={18} /></button></div>
    </div>
    <p className="detail-reference">Mã tài khoản <code className="subtle-code">{user.userId}</code></p>
    <Notice message={action.message} tone={action.error ? 'error' : 'success'} />
    <div className="detail-grid">
      <section className="detail-section">
        <div className="inline-heading"><span className="section-icon"><Icon name="shield" size={19} /></span><div><h3>Trạng thái tài khoản</h3></div></div>
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
        <div className="inline-heading"><span className="section-icon"><Icon name="users" size={19} /></span><div><h3>Vai trò đang hoạt động</h3></div></div>
        {user.memberships.length === 0 && <p className="empty-inline">Tài khoản chưa được cấp vai trò nào.</p>}
        <div className="membership-list">{user.memberships.map((member, index) => <div className="membership" key={member.role + ':' + (member.organizationId || '') + ':' + index}>
          <div><b>{roleNames[member.role] || member.role}</b><small title={member.organizationId || undefined}>{member.organizationId ? (orgMap.get(member.organizationId) || shortReference(member.organizationId)) : 'Toàn hệ thống'}</small></div>
          <button className="secondary" type="button" disabled={action.busy || currentId === user.userId && member.role === 'ADMIN'} onClick={() => void action.run(async () => {
            if (!await askConfirm({ title: 'Thu hồi vai trò?', message: 'Thu hồi vai trò này và các phiên cũ?', confirmLabel: 'Thu hồi vai trò', tone: 'danger' })) return;
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
      <div className="section-heading"><div><h3>Lịch sử trạng thái</h3></div><span className="result-count">{history.totalElements} thay đổi</span></div>
      {history.content.length === 0 ? <EmptyState icon="clock" title="Chưa có thay đổi trạng thái" />
        : <div className="table-wrap"><table className="data-table history-table"><thead><tr><th>Thời gian</th><th>Chuyển trạng thái</th><th>Lý do</th><th>Người thực hiện</th></tr></thead>
          <tbody>{history.content.map(item => <tr key={item.id}>
            <td><time dateTime={item.changedAt} title={item.changedAt}>{timestamp(item.changedAt)}</time></td>
            <td><div className="status-transition"><StatusBadge status={item.fromStatus} /><Icon name="arrow-right" size={14} /><StatusBadge status={item.toStatus} /></div></td>
            <td><span className="resource-name" title={item.reasonCode}>{reasonLabel(item.reasonCode)}</span></td>
            <td><AuditActor actorId={item.actorId} userMap={userMap} /></td>
          </tr>)}</tbody></table></div>}
      <Pagination page={history} busy={action.busy} change={next => void action.run(() => loadHistory(next))} />
    </section>}
  </section>;
}

export function OrganizationManagement({ permissions, initialForm = null }: { permissions: Set<string>; initialForm?: 'invite' | null }) {
  const [showCreate, setShowCreate] = useState(false), [showInvite, setShowInvite] = useState(initialForm === 'invite');
  const [search, setSearch] = useState(''), [filterType, setFilterType] = useState('');
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const listing = useRemotePage<Organization>('/admin/organizations?page=' + page, revision);
  const visibleOrganizations = listing.data?.content.filter(item => (!filterType || item.type === filterType)
    && (item.name + ' ' + item.id).toLocaleLowerCase('vi-VN').includes(search.trim().toLocaleLowerCase('vi-VN'))) || [];
  const [name, setName] = useState(''), [type, setType] = useState('ENTERPRISE');
  const [email, setEmail] = useState(''), [role, setRole] = useState('ENTERPRISE'), [org, setOrg] = useState('');
  const action = useAction();
  const refresh = () => setRevision(value => value + 1);

  return <div className="stack">
    <section className="panel management-panel">
      <div className="section-heading"><div><h2>Tổ chức</h2></div>
        <div className="table-actions">{listing.data && <span className="result-count">{listing.data.totalElements} tổ chức</span>}
          <button className="secondary btn-icon" type="button" aria-label="Tải lại danh sách tổ chức" title="Tải lại" disabled={listing.loading} onClick={refresh}><Icon name="refresh" size={17} /></button></div>
      </div>
      <Notice message={action.message} tone={action.error ? 'error' : 'success'} />
      <div className="filters user-filters"><Field label="Tìm tên hoặc mã tổ chức" value={search} onChange={setSearch} required={false} icon="search" />
        <label>Lọc loại tổ chức<select value={filterType} onChange={event => setFilterType(event.target.value)}><option value="">Tất cả</option><option value="ENTERPRISE">Doanh nghiệp</option><option value="UNIVERSITY">Nhà trường</option></select></label>
        {(search || filterType) && <button className="text-button" onClick={() => { setSearch(''); setFilterType(''); }}>Xóa bộ lọc</button>}</div>
      <p className="form-description">Bộ lọc áp dụng cho trang danh sách hiện tại.</p>
      {listing.loading ? <LoadingState label="Đang tải tổ chức…" />
        : listing.error ? <ReadFailure message={listing.error} retry={refresh} />
        : listing.data?.content.length === 0 ? <EmptyState icon="building" title="Chưa có tổ chức"
          description={permissions.has('organization:admin:create') ? 'Tạo tổ chức đầu tiên bằng biểu mẫu bên dưới để bắt đầu kết nối thành viên.' : 'Các tổ chức sẽ xuất hiện sau khi được quản trị viên thêm vào hệ thống.'} />
        : <div className="table-wrap"><table className="data-table">
          <thead><tr><th>Tổ chức</th><th>Loại</th><th>Định danh</th><th>Phạm vi</th><th>Trạng thái</th></tr></thead><tbody>{visibleOrganizations.map(item => <tr key={item.id}>
            <td><div className="table-person"><span className="table-avatar organization-avatar" aria-hidden="true"><Icon name="building" size={21} /></span>
              <div className="table-person-copy"><b>{item.name}</b></div></div></td>
            <td><span className="role-chip">{item.type === 'ENTERPRISE' ? 'Doanh nghiệp' : 'Nhà trường'}</span></td>
            <td><code className="subtle-code">{item.id}</code></td><td>{item.name}</td>
            <td><StatusBadge status={item.status} /></td>
          </tr>)}</tbody>
        </table>{visibleOrganizations.length === 0 && <p className="taxonomy-empty">Không có tổ chức phù hợp trong trang này.</p>}</div>}
      {!listing.error && <Pagination page={listing.data} busy={listing.loading} change={next => { setPage(next); setOrg(''); }} />}
      <div className="button-row admin-table-tools">
        {permissions.has('organization:admin:create') && <button type="button" className="secondary admin-outline" disabled={action.busy}
          aria-label={showCreate ? 'Thu gọn form tạo tổ chức' : 'Tạo tổ chức'} aria-expanded={showCreate} aria-controls="admin-create-organization"
          onClick={() => setShowCreate(value => !value)}><Icon name="plus" size={16} />Tạo tổ chức</button>}
        <button type="button" className="secondary admin-outline" disabled={action.busy}
          aria-label={showInvite ? 'Thu gọn form mời tài khoản' : 'Mời tài khoản'} aria-expanded={showInvite} aria-controls="admin-invite-account"
          onClick={() => setShowInvite(value => !value)}><Icon name="mail" size={16} />Mời tài khoản</button>
      </div>
    </section>

    <div className={'dashboard-grid organization-forms' + (showCreate && showInvite ? '' : ' single-form')} hidden={!showCreate && !showInvite}>
      {permissions.has('organization:admin:create') && <section className="panel form-panel" id="admin-create-organization" hidden={!showCreate}>
        <form onSubmit={event => {
          event.preventDefault();
          void action.run(async () => {
            await send('/admin/organizations', 'POST', { name, type });
            setName(''); setPage(0); refresh();
            action.setMessage('Đã tạo tổ chức ở trạng thái chờ xác thực.');
          });
        }}>
          <div className="inline-heading"><span className="section-icon"><Icon name="building" size={21} /></span><div><h2>Tạo tổ chức</h2></div></div>
          <p className="form-description">Tổ chức mới được tạo ở trạng thái chờ xác thực. Quyền sử dụng chức năng sẽ tùy thuộc kết quả xác thực.</p>
          <Field label="Tên tổ chức" value={name} onChange={setName} maxLength={200} placeholder="Tên doanh nghiệp hoặc nhà trường" />
          <label>Loại tổ chức<select value={type} onChange={event => setType(event.target.value)}>
            <option value="ENTERPRISE">Doanh nghiệp</option><option value="UNIVERSITY">Nhà trường</option>
          </select></label>
          <button type="submit" disabled={action.busy}><Icon name="plus" size={17} />Tạo tổ chức</button>
        </form>
      </section>}

      <section className="panel form-panel" id="admin-invite-account" hidden={!showInvite}>
        <form onSubmit={event => {
          event.preventDefault();
          void action.run(async () => {
            await send('/admin/invitations', 'POST', { email, role, organizationId: role === 'ADMIN' ? null : org });
            setEmail('');
            action.setMessage('Đã gửi lời mời. Liên kết có hiệu lực trong 48 giờ.');
          });
        }}>
          <div className="inline-heading"><span className="section-icon"><Icon name="mail" size={21} /></span><div><h2>Mời tài khoản</h2></div></div>
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

export function AuditViewer({ permissions }: { permissions: Set<string> }) {
  const [filtersOpen, setFiltersOpen] = useState(true);
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const { userMap, orgMap } = useAuditDirectories(permissions, revision);
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
    <div className="section-heading"><div><h2>Nhật ký hệ thống</h2></div>
      <div className="table-actions">{listing.data && <span className="result-count">{listing.data.totalElements} sự kiện</span>}
        <button className="secondary" type="button" aria-expanded={filtersOpen} aria-controls="admin-audit-filters" onClick={() => setFiltersOpen(value => !value)}><Icon name="filter" size={16} />Bộ lọc nâng cao</button>
        <button className="secondary btn-icon" type="button" aria-label="Tải lại nhật ký" title="Tải lại" disabled={listing.loading} onClick={refresh}><Icon name="refresh" size={17} /></button></div>
    </div>

    <form className="audit-filter-form" id="admin-audit-filters" hidden={!filtersOpen} onSubmit={submit => {
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
      <div className="filter-actions">
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
        <thead><tr><th>Người thực hiện / truy vết</th><th>Hành động / kết quả</th><th>Tài nguyên / phạm vi</th><th>Mã yêu cầu</th><th>Thời gian</th></tr></thead>
        <tbody>{listing.data?.content.map(log => <tr key={log.id}>
          <td><AuditActor actorId={log.actorId} userMap={userMap} /></td>
          <td><div className="audit-event-line"><b>{actionName(log.action)}</b><div className="audit-event-meta"><code className="subtle-code">{log.action}</code>{' '}<StatusBadge status={log.result} /></div></div></td>
          <td><span className="resource-name" title={log.resourceType}>{resourceName(log.resourceType)}</span><small><code className="subtle-code">{log.resourceId || 'Không áp dụng'}</code></small>
            {log.reasonCode && <small className="audit-reason">{log.reasonCode}</small>}<small className="audit-organization" title={log.organizationId || undefined}><Icon name="building" size={13} />{log.organizationId ? orgMap.get(log.organizationId) || shortReference(log.organizationId) : 'Toàn hệ thống'}</small></td>
          <td><code className="subtle-code">{log.requestId || '—'}</code></td>
          <td><time dateTime={log.occurredAt} title={log.occurredAt}>{timestamp(log.occurredAt)}</time></td>
        </tr>)}</tbody>
      </table></div>}
    {!listing.error && <Pagination page={listing.data} busy={listing.loading} change={setPage} />}
  </section>;
}
