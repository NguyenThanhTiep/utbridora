import { useEffect, useRef, useState } from 'react';
import { api, ApiError, type Grant } from '../../api';
import { EmptyState, LoadingState, Notice, permissionNames } from '../../components';
import { newIdempotencyKey } from '../student/api';
import './styles.css';

type Page<T> = { content: T[]; page: number; totalElements: number; totalPages: number };
type Scope = { schoolOrgId: string; schoolName: string; termId: string; termCode: string; termStatus: string };
type Delegation = { id: string; userId: string; schoolOrgId: string; termId: string; permissionCodes: string[];
  profileReadFields: string[]; validFrom: string; validUntil: string; active: boolean; version: number; grantedBy: string; decisionRef: string };
type Detail = { delegation: Delegation; parentId: string | null; displayName: string; email: string;
  grantorName: string; effectivePermissionCodes: string[]; canRevoke: boolean };
type Options = { scope: Scope; authorities: Delegation[]; permissionCodes: string[]; profileReadFields: string[] };
type Candidate = { userId: string; displayName: string; email: string };
const fieldNames: Record<string, string> = { displayName: 'Họ tên', studentCode: 'Mã sinh viên', majorCode: 'Ngành',
  skills: 'Kỹ năng', projects: 'Dự án', certificates: 'Chứng chỉ', cv: 'CV', evidence: 'Minh chứng', email: 'Email', phone: 'Điện thoại' };
const localDate = (date: Date) => new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
const stamp = (value: string) => new Date(value).toLocaleString('vi-VN');
const failure = (error: unknown) => error instanceof ApiError
  ? `${error.message}${error.errorCode ? ' (' + error.errorCode + ')' : ''}${error.requestId ? ' · Mã tra cứu: ' + error.requestId : ''}`
  : error instanceof Error ? error.message : 'Không thể kết nối. Vui lòng thử lại.';

function useRead<T>(path: string, revision = 0) {
  const [data, setData] = useState<T | null>(null), [error, setError] = useState(''), [loading, setLoading] = useState(true);
  useEffect(() => {
    const controller = new AbortController(); setData(null); setError(''); setLoading(true);
    void api<T>(path, { signal: controller.signal }).then(value => { if (!controller.signal.aborted) setData(value); })
      .catch(reason => { if (!controller.signal.aborted) setError(failure(reason)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [path, revision]);
  return { data, error, loading };
}
function Pager({ data, busy, change }: { data: Page<unknown> | null; busy: boolean; change: (page: number) => void }) {
  return data && <div className="pagination"><span>{data.totalElements} kết quả · Trang {data.page + 1}/{Math.max(1, data.totalPages)}</span><div className="pagination-buttons">
    <button type="button" className="secondary" disabled={busy || data.page === 0} onClick={() => change(data.page - 1)}>Trước</button>
    <button type="button" className="secondary" disabled={busy || data.page + 1 >= data.totalPages} onClick={() => change(data.page + 1)}>Sau</button>
  </div></div>;
}
export function SchoolDelegations({ grants }: { grants: Grant[] }) {
  const admin = grants.some(g => g.role === 'ADMIN' && g.organizationId === null && g.permissions.includes('membership:admin:write'));
  const staff = grants.some(g => g.role === 'SCHOOL_STAFF' && g.permissions.includes('school:delegation:manage'));
  const [mode, setMode] = useState(admin ? 'admin' : 'school');
  if (!admin && !staff) return <EmptyState icon="lock" title="Chưa có quyền quản lý phân công" />;
  return <div className="stack delegation-workspace">
    <section className="panel"><h2>Phân công quyền cán bộ</h2>
      {admin && staff && <label>Hình thức quản lý<select value={mode} onChange={e => setMode(e.target.value)}><option value="admin">Khởi tạo demo</option><option value="school">Ủy quyền nghiệp vụ</option></select></label>}
      <p>{mode === 'admin' ? 'Khởi tạo phân công demo bằng quyền quản trị. Căn cứ phải bắt đầu bằng DEMO-. Quyền này không cấp quyền truy cập hồ sơ sinh viên cho admin.'
        : 'Ủy quyền trong trường và kỳ bạn đang được giao. Quyền, thông tin được xem và thời hạn phải nằm trong một phân công cấp quyền còn hiệu lực.'}</p>
    </section>
    <ScopePicker key={mode} mode={mode} />
  </div>;
}
function ScopePicker({ mode }: { mode: string }) {
  const [page, setPage] = useState(0), [selected, setSelected] = useState(''), [revision, setRevision] = useState(0);
  const base = '/' + mode + '/school-delegations';
  const scopes = useRead<Page<Scope>>(base + '/scopes?page=' + page, revision);
  const current = scopes.data?.content.find(s => s.termId === selected) || scopes.data?.content[0];
  return <>
    <section className="panel"><div className="section-heading"><h2>Phạm vi quản lý</h2><button type="button" className="secondary" onClick={() => setRevision(n => n + 1)}>Tải lại phạm vi</button></div>
      <Notice message={scopes.error} tone="error" />
      {scopes.loading ? <LoadingState /> : scopes.data?.content.length === 0
        ? <EmptyState title="Chưa có kỳ được quản lý" description="Cần phân công có quyền quản lý còn hiệu lực trong đúng trường/kỳ. Vai trò cán bộ trường chưa đủ để ủy quyền." />
        : scopes.data && <label>Trường và kỳ<select value={current?.termId || ''} onChange={e => setSelected(e.target.value)}>
          {scopes.data.content.map(s => <option key={s.termId} value={s.termId}>{s.schoolName} · {s.termCode} · {s.termStatus}</option>)}
        </select></label>}
      <Pager data={scopes.data} busy={scopes.loading} change={p => { setSelected(''); setPage(p); }} />
    </section>
    {current && !scopes.loading && !scopes.error && <ScopeEditor key={mode + current.termId + revision} mode={mode} scope={current} />}
  </>;
}
function ScopeEditor({ mode, scope }: { mode: string; scope: Scope }) {
  const base = '/' + mode + '/school-delegations', query = '?termId=' + scope.termId;
  const [revision, setRevision] = useState(0), [page, setPage] = useState(0), [search, setSearch] = useState(''), [candidatePage, setCandidatePage] = useState(0);
  const options = useRead<Options>(base + '/options' + query, revision);
  const candidates = useRead<Page<Candidate>>(base + '/candidates' + query + '&page=' + candidatePage + '&search=' + encodeURIComponent(search), revision);
  const listing = useRead<Page<Detail>>(base + query + '&page=' + page, revision);
  const [selected, setSelected] = useState(''), [detailRevision, setDetailRevision] = useState(0);
  const [authorityId, setAuthorityId] = useState(''), [userId, setUserId] = useState(''), [permissions, setPermissions] = useState<string[]>([]), [fields, setFields] = useState<string[]>([]);
  const [from, setFrom] = useState(() => localDate(new Date())), [until, setUntil] = useState(() => localDate(new Date(Date.now() + 7 * 86400000))), [reference, setReference] = useState('');
  const [busy, setBusy] = useState(false), [message, setMessage] = useState(''), [error, setError] = useState('');
  const pending = useRef<{ payload: string; key: string } | null>(null), live = useRef(true);
  useEffect(() => { live.current = true; return () => { live.current = false; }; }, []);
  const authority = options.data?.authorities.find(a => a.id === authorityId) || options.data?.authorities[0];
  const allowed = mode === 'admin' ? options.data?.permissionCodes || [] : (authority?.permissionCodes || []).filter(p => options.data?.permissionCodes.includes(p));
  const allowedFields = mode === 'admin' ? options.data?.profileReadFields || [] : authority?.profileReadFields || [];
  const chooseAuthority = (id: string) => { setAuthorityId(id); setPermissions([]); setFields([]); const a = options.data?.authorities.find(v => v.id === id); if (a) { setFrom(localDate(new Date(Math.max(Date.now(), Date.parse(a.validFrom))))); setUntil(localDate(new Date(a.validUntil))); } };
  useEffect(() => { if (mode === 'school' && authority) chooseAuthority(authority.id); }, [options.data]);
  const chosenUser = candidates.data?.content.some(c => c.userId === userId) ? userId : '';
  const submit = async () => {
    setMessage(''); setError('');
    if (!chosenUser || permissions.length === 0 || !from || !until || Date.parse(from) >= Date.parse(until)) { setError('Chọn cán bộ, quyền và thời hạn hợp lệ.'); return; }
    if (mode === 'admin' && !reference.trim().startsWith('DEMO-')) { setError('Căn cứ khởi tạo demo phải bắt đầu bằng DEMO-.'); return; }
    if (mode === 'school' && (!authority || Date.parse(from) < Date.parse(authority.validFrom) || Date.parse(until) > Date.parse(authority.validUntil))) { setError('Thời hạn phải nằm trong phân công cấp quyền đang chọn.'); return; }
    const body = { userId: chosenUser, permissionCodes: permissions, profileReadFields: permissions.includes('student_profile:school:read') ? fields : [], validFrom: new Date(from).toISOString(), validUntil: new Date(until).toISOString(), decisionRef: reference.trim(),
      ...(mode === 'admin' ? { schoolOrgId: scope.schoolOrgId, termId: scope.termId } : {}) };
    const payload = JSON.stringify(body); if (pending.current?.payload !== payload) pending.current = { payload, key: newIdempotencyKey() };
    setBusy(true);
    try {
      const d = await api<Delegation>(mode === 'admin' ? '/admin/school-delegations' : '/school/terms/' + scope.termId + '/delegations', { method: 'POST', headers: { 'Idempotency-Key': pending.current.key }, body: payload });
      if (live.current) { pending.current = null; setSelected(d.id); setDetailRevision(n => n + 1); setRevision(n => n + 1); setPermissions([]); setFields([]); setMessage('Đã cấp phân công. Cán bộ nhận quyền cần đăng nhập lại.'); }
    } catch (reason) { if (live.current) setError(failure(reason)); } finally { if (live.current) setBusy(false); }
  };
  return <>
    <section className="panel"><h2>{mode === 'admin' ? 'Khởi tạo phân công demo' : 'Cấp phân công nghiệp vụ'}</h2>
      <Notice message={message} tone="success" /><Notice message={error || options.error || candidates.error} tone="error" />
      {options.loading ? <LoadingState /> : options.data && <form onSubmit={e => { e.preventDefault(); void submit(); }}><fieldset disabled={busy} className="delegation-form">
        {mode === 'school' && <label>Phân công cấp quyền<select value={authority?.id || ''} onChange={e => chooseAuthority(e.target.value)}>{options.data.authorities.map(a => <option key={a.id} value={a.id}>{a.decisionRef} · đến {stamp(a.validUntil)}</option>)}</select></label>}
        <label>Tìm cán bộ theo tên hoặc email<input value={search} maxLength={150} onChange={e => { setSearch(e.target.value); setCandidatePage(0); setUserId(''); }} /></label>
        {candidates.loading ? <LoadingState label="Đang tìm cán bộ…" /> : <label>Cán bộ nhận quyền<select required value={chosenUser} onChange={e => setUserId(e.target.value)}><option value="">Chọn cán bộ thuộc trường</option>{candidates.data?.content.map(c => <option key={c.userId} value={c.userId}>{c.displayName} · {c.email}</option>)}</select></label>}
        <Pager data={candidates.data} busy={busy || candidates.loading} change={p => { setCandidatePage(p); setUserId(''); }} />
        <fieldset className="delegation-choices"><legend>Quyền được cấp</legend>{allowed.map(p => <label key={p}><input type="checkbox" checked={permissions.includes(p)} onChange={e => { setPermissions(e.target.checked ? [...permissions, p] : permissions.filter(v => v !== p)); if (p === 'student_profile:school:read') setFields([]); }} />{permissionNames[p] || p}</label>)}</fieldset>
        {permissions.includes('student_profile:school:read') && <fieldset className="delegation-choices"><legend>Thông tin hồ sơ được xem</legend>{allowedFields.map(f => <label key={f}><input type="checkbox" checked={fields.includes(f)} onChange={e => setFields(e.target.checked ? [...fields, f] : fields.filter(v => v !== f))} />{fieldNames[f] || f}</label>)}</fieldset>}
        <div className="delegation-dates"><label>Có hiệu lực từ<input required type="datetime-local" value={from} onChange={e => setFrom(e.target.value)} /></label><label>Hết hiệu lực lúc<input required type="datetime-local" value={until} onChange={e => setUntil(e.target.value)} /></label></div>
        <label>Căn cứ phân công<input required maxLength={120} value={reference} placeholder={mode === 'admin' ? 'DEMO-...' : 'Số quyết định hoặc căn cứ phân công'} onChange={e => setReference(e.target.value)} /></label>
        <button disabled={busy || candidates.loading || !!candidates.error || permissions.length === 0 || permissions.includes('student_profile:school:read') && fields.length === 0}>{busy ? 'Đang gửi…' : 'Cấp phân công'}</button>
      </fieldset></form>}
    </section>
    <section className="panel"><div className="section-heading"><h2>Danh sách phân công</h2><button type="button" className="secondary" disabled={busy} onClick={() => { setRevision(n => n + 1); setDetailRevision(n => n + 1); }}>Tải lại phân công</button></div>
      <Notice message={listing.error} tone="error" />
      {listing.loading ? <LoadingState /> : listing.data?.content.length === 0 ? <p>Chưa có phân công trong kỳ này.</p> : <div className="table-wrap"><table className="data-table"><thead><tr><th>Cán bộ</th><th>Căn cứ</th><th>Hiệu lực</th><th>Thao tác</th></tr></thead><tbody>{listing.data?.content.map(d => <tr key={d.delegation.id}><td>{d.displayName}<br /><small>{d.email}</small></td><td>{d.delegation.decisionRef}</td><td>{state(d)}<br /><small>{stamp(d.delegation.validFrom)} – {stamp(d.delegation.validUntil)}</small></td><td><button type="button" className="secondary" aria-label={'Xem phân công ' + d.delegation.decisionRef} onClick={() => { setSelected(d.delegation.id); setDetailRevision(n => n + 1); }}>Xem phân công</button></td></tr>)}</tbody></table></div>}
      <Pager data={listing.data} busy={listing.loading || busy} change={setPage} />
    </section>
    {selected && <DelegationDetail key={selected} path={base + '/' + selected} revision={detailRevision} changed={() => { setRevision(n => n + 1); setDetailRevision(n => n + 1); }} close={() => setSelected('')} />}
  </>;
}
function state(d: Detail) {
  return !d.delegation.active ? 'Đã thu hồi' : d.effectivePermissionCodes.length === d.delegation.permissionCodes.length ? 'Đang có hiệu lực'
    : d.effectivePermissionCodes.length ? 'Hiệu lực một phần' : 'Chưa có hoặc đã mất hiệu lực';
}
function DelegationDetail({ path, revision, changed, close }: { path: string; revision: number; changed: () => void; close: () => void }) {
  const detail = useRead<Detail>(path, revision), [reason, setReason] = useState('ASSIGNMENT_ENDED'), [busy, setBusy] = useState(false), [error, setError] = useState(''), [message, setMessage] = useState('');
  const pending = useRef<{ payload: string; key: string } | null>(null);
  const d = detail.data;
  const revoke = async () => {
    if (!d) return; setBusy(true); setError(''); setMessage('');
    const payload = JSON.stringify({ expectedVersion: d.delegation.version, reasonCode: reason });
    if (pending.current?.payload !== payload) pending.current = { payload, key: newIdempotencyKey() };
    try { await api('/school/delegations/' + d.delegation.id, { method: 'DELETE', headers: { 'Idempotency-Key': pending.current.key }, body: payload }); pending.current = null; setMessage('Đã thu hồi phân công và các phiên bị ảnh hưởng.'); changed(); }
    catch (problem) { setError(failure(problem)); } finally { setBusy(false); }
  };
  return <section className="panel" aria-label="Chi tiết phân công"><div className="section-heading"><h2>Chi tiết phân công</h2><button type="button" className="secondary" disabled={busy} onClick={close}>Đóng chi tiết</button></div>
    <Notice message={detail.error || error} tone="error" /><Notice message={message} tone="success" />
    {detail.loading ? <LoadingState /> : d && <><h3>{d.displayName}</h3><p>{d.email} · {state(d)}</p><dl className="delegation-meta"><dt>Căn cứ</dt><dd>{d.delegation.decisionRef}</dd><dt>Người cấp</dt><dd>{d.grantorName}</dd><dt>Thời hạn</dt><dd>{stamp(d.delegation.validFrom)} – {stamp(d.delegation.validUntil)}</dd><dt>Phiên bản</dt><dd>{d.delegation.version}</dd><dt>Phân công</dt><dd>{d.parentId ? 'Được ủy quyền từ phân công ' + d.parentId : 'Phân công gốc'}</dd></dl>
      <h3>Quyền được cấp</h3><ul>{d.delegation.permissionCodes.map(p => <li key={p}>{permissionNames[p] || p}{!d.effectivePermissionCodes.includes(p) && ' · Chưa có hoặc đã mất hiệu lực'}</li>)}</ul>
      {d.delegation.profileReadFields.length > 0 && <p>Thông tin được xem: {d.delegation.profileReadFields.map(f => fieldNames[f] || f).join(', ')}</p>}
      {d.canRevoke ? <form onSubmit={e => { e.preventDefault(); void revoke(); }}><label>Lý do thu hồi<select value={reason} disabled={busy} onChange={e => setReason(e.target.value)}><option value="ASSIGNMENT_ENDED">Kết thúc phân công</option><option value="SECURITY_REVIEW">Rà soát quyền truy cập</option><option value="SCOPE_CHANGED">Thay đổi phạm vi phụ trách</option></select></label><button className="danger" disabled={busy}>Thu hồi phân công</button></form>
        : d.delegation.active && <p>Chỉ người đã cấp và còn thẩm quyền mới được thu hồi phân công này.</p>}
    </>}
  </section>;
}
