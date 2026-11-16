import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import type { ContractPage } from '../../student/types';
import type { Demand } from '../types';
import type { SchoolProposal } from './types';
import { scopedRead, workflowError } from './api';
import { useWorkflowCommand } from './useWorkflowCommand';
import { WorkspaceHandoff } from './WorkspaceHandoff';

type Placement = { placementId: string; schoolOrgId: string; enterpriseOrgId: string; cooperationId: string; proposalItemId: string;
  state: string; version: number; effectiveAt: string; startDate: string; endDate: string };
type Summary = { demandId: string; cooperationId: string | null; target: number; placed: number; pending: number; remaining: number };
type Link = { state: 'WAITING_HANDOFF' | 'AVAILABLE'; route: string | null; caseId?: string | null };
const states: Record<string, string> = { PENDING_APPROVAL: 'Chờ trường phê duyệt', APPROVED: 'Đã duyệt, chờ bắt đầu', ACTIVE: 'Đang thực tập', CANCELLED: 'Đã hủy', COMPLETED: 'Đã hoàn thành' };
const statusGuides: Record<string, { school: string; enterprise: string }> = {
  PENDING_APPROVAL: {
    school: 'Doanh nghiệp đã đồng ý tiếp nhận. Nhà trường kiểm tra thời hạn hiệu lực, chỉ tiêu còn lại rồi thực hiện phê duyệt tiếp nhận.',
    enterprise: 'Hồ sơ tiếp nhận đã được khởi tạo và đang chờ Nhà trường phê duyệt chính thức trước khi chuyển sang giai đoạn thực tập.',
  },
  APPROVED: {
    school: 'Có thể kích hoạt thực tập khi đến thời điểm có hiệu lực.',
    enterprise: 'Đã phê duyệt tiếp nhận. Kiểm tra bàn giao để chuẩn bị giao việc khi hồ sơ có hiệu lực.',
  },
  ACTIVE: {
    school: 'Sinh viên đang thực tập. Giao việc, nộp bài và đánh giá tại không gian thực tập.',
    enterprise: 'Sinh viên đang thực tập. Mở không gian thực tập để theo dõi công việc.',
  },
  COMPLETED: {
    school: 'Đợt thực tập đã hoàn tất và đóng hồ sơ theo quy định.',
    enterprise: 'Đợt thực tập đã hoàn thành. Có thể tra cứu lại hồ sơ thực tập đã lưu trữ trên Không gian thực tập (T3).',
  },
  CANCELLED: {
    school: 'Hồ sơ tiếp nhận này đã bị hủy và giải phóng chỉ tiêu tương ứng.',
    enterprise: 'Hồ sơ tiếp nhận này đã dừng/hủy theo quyết định đã ghi nhận.',
  },
};
const lifecycleSteps = [
  { key: 'PENDING_APPROVAL', label: '1. Tạo chờ duyệt' },
  { key: 'APPROVED', label: '2. Trường phê duyệt' },
  { key: 'ACTIVE', label: '3. Đang thực tập' },
  { key: 'COMPLETED', label: '4. Hoàn tất / Đóng' },
];
function stepRank(state: string): number {
  if (state === 'PENDING_APPROVAL') return 0;
  if (state === 'APPROVED') return 1;
  if (state === 'ACTIVE') return 2;
  if (state === 'COMPLETED') return 3;
  return -1;
}

/** Consumer of DEV10's CP-T5UI1 routes. No local state transition substitutes for an unavailable API. */
export function PlacementWorkspace({ org, school, cooperationId, demand, candidates = [], locked = false, changed, pendingChanged, initialCandidateId }: {
  org: string; school: boolean; cooperationId: string; demand: Demand; candidates?: SchoolProposal[]; locked?: boolean;
  changed?: () => void; pendingChanged?: (pending: boolean) => void; initialCandidateId?: string;
}) {
  const [result, setResult] = useState<ContractPage<Placement> | null>(null), [summary, setSummary] = useState<Summary | null>(null);
  const [page, setPage] = useState(0), [filter, setFilter] = useState(''), [search, setSearch] = useState(''), [revision, setRevision] = useState(0), [loading, setLoading] = useState(true);
  const [searchInput, setSearchInput] = useState('');
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [selected, setSelected] = useState('');
  const [start, setStart] = useState(demand.data.required.dateRange.startDate), [end, setEnd] = useState(demand.data.required.dateRange.endDate), [effective, setEffective] = useState('');
  const [decision, setDecision] = useState<{ item: Placement; action: string } | null>(null), [reference, setReference] = useState(''), [reason, setReason] = useState('');
  const [links, setLinks] = useState<Record<string, Link>>({}), [now, setNow] = useState(Date.now());
  const [expandedPlacement, setExpandedPlacement] = useState<string | null>(null);
  const pendingRef = useRef(pendingChanged); pendingRef.current = pendingChanged;
  const side = school ? 'school' : 'enterprise';
  const notifyProgress = () => window.setTimeout(() => window.dispatchEvent(new Event('placement-updated')), 0);
  const command = useWorkflowCommand<Placement>(org, value => {
    setDecision(null); setSelected(''); setNotice(`Đã ghi nhận: ${states[value.state] || value.state}.`);setRevision(n => n + 1);changed?.();notifyProgress();
  }, error => {
    // State/scope conflicts invalidate the selected version; schema errors keep the entered decision.
    if (error instanceof ApiError && [401, 403, 404, 409].includes(error.status)) {setDecision(null);setSelected('');}
    setError(workflowError(error));setRevision(n => n + 1);changed?.();notifyProgress();
  });
  const refreshRef = useRef(changed);refreshRef.current = changed;
  useEffect(() => { pendingRef.current?.(command.blocked);return () => pendingRef.current?.(false); }, [command.blocked]);
  useEffect(() => {
    const refresh = () => {
      if (!document.hidden && !locked && !command.blocked && !decision) {
        setRevision(n => n + 1);refreshRef.current?.();
      }
    };
    window.addEventListener('focus', refresh);document.addEventListener('visibilitychange', refresh);
    return () => {window.removeEventListener('focus', refresh);document.removeEventListener('visibilitychange', refresh);};
  }, [locked, command.blocked, decision]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 15000);return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    const c = new AbortController();setLoading(true);setResult(null);setSummary(null);setLinks({});setError('');
    const query = new URLSearchParams({ cooperationId, demandId: demand.demandId, termId: demand.data.termId, schoolOrgId: demand.data.schoolOrgId,
      page: String(page), size: '25', ...(filter ? { state: filter } : {}), ...(search ? { q: search } : {}) });
    void Promise.all([
      scopedRead<ContractPage<Placement>>(org, `/${side}/placements?${query}`, c.signal),
      scopedRead<Summary>(org, `/${side}/placements/summary?demandId=${encodeURIComponent(demand.demandId)}`, c.signal),
    ]).then(([list, totals]) => {
      if (c.signal.aborted) return;
      if (list.content.some(p => p.cooperationId !== cooperationId || (school ? p.schoolOrgId : p.enterpriseOrgId) !== org)
          || totals.demandId !== demand.demandId || totals.cooperationId !== null && totals.cooperationId !== cooperationId) throw new Error('Scope mismatch');
      if (page > 0 && page >= list.totalPages) {setPage(Math.max(0, list.totalPages - 1));return;}
      setResult(list);setSummary(totals);
    }).catch(error => { if (!c.signal.aborted) setError(error instanceof ApiError && [403, 404, 503].includes(error.status)
      ? 'Chưa truy cập được chức năng tiếp nhận thực tập. Kiểm tra quyền hoặc chờ chức năng sẵn sàng rồi tải lại.' : workflowError(error)); })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [org, school, side, cooperationId, demand.demandId, demand.data.termId, demand.data.schoolOrgId, page, filter, search, revision]);
  const ready = candidates.filter(item => item.state === 'ENTERPRISE_ACCEPTED' && !item.placementId && item.shareIsActive && item.eligibilityVersion > 0);
  useEffect(() => {
    if (initialCandidateId && candidates.some(item => item.proposalItemId === initialCandidateId && item.state === 'ENTERPRISE_ACCEPTED'
      && !item.placementId && item.shareIsActive && item.eligibilityVersion > 0)) setSelected(initialCandidateId);
  }, [initialCandidateId, candidates]);
  const blocked = locked || command.blocked || loading;
  const visibleItems = (result?.content || []).map((item, n) => ({ item, seq: page * 25 + n + 1 }));
  const placedPct = summary && summary.target > 0 ? Math.min(100, Math.round((summary.placed / summary.target) * 100)) : 0;
  const pendingPct = summary && summary.target > 0 ? Math.min(100 - placedPct, Math.round((summary.pending / summary.target) * 100)) : 0;
  const quotaStatus = summary && (summary.target > 0 && summary.placed >= summary.target
    ? { code: 'FULL', label: 'Đủ chỉ tiêu' } : summary.placed < summary.target
      ? summary.pending > 0 ? { code: 'WAITING', label: 'Đang chờ tiếp nhận' } : { code: 'SHORT', label: 'Còn thiếu chỉ tiêu' }
      : null);
  const checkHandoff = (item: Placement) => {
    const controller = new AbortController();
    void scopedRead<Link>(org, `/${side}/placements/${item.placementId}/internship-link`, controller.signal).then(link => {
      if (link.state === 'AVAILABLE' && (!link.route || !/^\/workspace\/cases\/[0-9a-f-]{36}$/i.test(link.route))) throw new Error('Invalid internship route');
      setLinks(old => ({ ...old, [item.placementId]: link })); setExpandedPlacement(item.placementId);
    }).catch(error => setError(workflowError(error)));
  };
  return <section className="proposal-placement" aria-label="Tiếp nhận thực tập">
    <div className="placement-header-bar">
      <div>
        <h4>Tiếp nhận thực tập</h4>
      </div>
      <button className="secondary" disabled={blocked} onClick={() => setRevision(n => n + 1)}>Tải lại tiếp nhận thực tập</button>
    </div>
    <Notice message={error || command.message} tone="error" /><p role="status">{notice}</p>
    {loading && <p role="status">Đang tải tiếp nhận thực tập…</p>}
    {command.retryable && <button disabled={command.busy} onClick={() => void command.retry()}>Thử lại thao tác tiếp nhận đã gửi</button>}
    {summary && <>
      <dl className="proposal-capacity"><div><dt>Chỉ tiêu tiếp nhận</dt><dd>{summary.target}</dd></div><div><dt>Đã chốt</dt><dd>{summary.placed}</dd></div>
        <div><dt>Đang chờ</dt><dd>{summary.pending}</dd></div><div><dt>Còn chỗ</dt><dd>{summary.remaining}</dd></div></dl>
      {quotaStatus && <p className={'placement-quota-status quota-' + quotaStatus.code.toLowerCase()} role="status"
        aria-label="Tình trạng chỉ tiêu tiếp nhận" data-state={quotaStatus.code}>{quotaStatus.label}</p>}
      {school && <div className="placement-quota-bar" aria-label="Tiến độ lấp đầy chỉ tiêu tiếp nhận">
        <div className="placement-quota-legend">
          <span>Tiến độ lấp đầy chỉ tiêu: <strong>{summary.placed}/{summary.target}</strong> đã chốt ({placedPct}%)</span>
          <span>Đang chờ phê duyệt: <strong>{summary.pending}</strong> · Còn trống: <strong>{summary.remaining}</strong></span>
        </div>
        <div className="placement-quota-track">
          <div className="placement-quota-fill placed" style={{ width: `${placedPct}%` }} />
          <div className="placement-quota-fill pending" style={{ width: `${pendingPct}%` }} />
        </div>
      </div>}
    </>}
    {result && <>
      <div className="placement-filter-bar">
        <label>Trạng thái tiếp nhận<select disabled={blocked} value={filter} onChange={e => {setFilter(e.target.value);setPage(0);}}>
          <option value="">Tất cả trạng thái</option>{Object.entries(states).map(([key, text]) => <option value={key} key={key}>{text}</option>)}
        </select></label>
        <form onSubmit={event => {event.preventDefault();if (!blocked) {setSearch(searchInput.trim());setPage(0);}}}>
          <label>Tìm kiếm tiếp nhận<input type="search" maxLength={150} disabled={blocked} placeholder="Mã tiếp nhận, nhu cầu, phiếu hợp tác, mã sinh viên, vị trí tuyển…" value={searchInput} onChange={e => setSearchInput(e.target.value)} /></label>
          <button className="secondary" disabled={blocked}>Tìm tiếp nhận</button>
        </form>
        {(filter || search) && <button type="button" className="secondary" disabled={blocked} onClick={() => { setFilter(''); setSearch(''); setSearchInput(''); setPage(0); }}>Xóa bộ lọc</button>}
      </div>
      {!result.content.length && <p>{search ? 'Không tìm thấy hồ sơ tiếp nhận khớp từ khóa trong phạm vi được phép.' : 'Chưa có hồ sơ tiếp nhận trong nhóm trạng thái này.'}</p>}
      {!school && <div className="proposal-table"><table><thead><tr><th>Tiếp nhận</th><th>Trạng thái</th><th>Thời gian thực tập</th><th>Thao tác</th></tr></thead>
        <tbody>{visibleItems.map(({ item, seq }) => <tr key={item.placementId}>
          <th scope="row">Tiếp nhận {seq} · {states[item.state] || item.state}</th>
          <td><span className={'status-pill pill-' + item.state.toLowerCase()}>{states[item.state] || item.state}</span></td>
          <td>{item.startDate} – {item.endDate}</td><td><div className="button-row">
            <button className="secondary" disabled={blocked} aria-expanded={expandedPlacement === item.placementId}
              onClick={() => setExpandedPlacement(old => old === item.placementId ? null : item.placementId)}>Xem chi tiết</button>
            {['APPROVED', 'ACTIVE', 'COMPLETED'].includes(item.state) && <button className="secondary" disabled={blocked}
              onClick={() => checkHandoff(item)}>Kiểm tra bàn giao sang không gian thực tập</button>}
          </div></td>
        </tr>)}</tbody></table></div>}
      {visibleItems.filter(({ item }) => school || expandedPlacement === item.placementId).map(({ item, seq }) => {
        const rank = stepRank(item.state);
        const guide = statusGuides[item.state]?.[school ? 'school' : 'enterprise'];
        const handoff = links[item.placementId];
        return <article key={item.placementId} className={`placement-item-card state-${item.state.toLowerCase()}`}>
          <div className="placement-card-header">
            <div className="placement-title-group">
              <span className="placement-seq-badge">#{seq}</span>
              <h5>Tiếp nhận {seq} · {states[item.state] || item.state}</h5>
            </div>
            <span className={`status-pill pill-${item.state.toLowerCase()}`}>
              <span className="pill-dot" />
              {states[item.state] || item.state}
            </span>
          </div>
          {item.state !== 'CANCELLED' && <ol className="placement-stepper" aria-label="Tiến trình vòng đời tiếp nhận">
            {lifecycleSteps.map((step, idx) => <li key={step.key} className={`placement-step ${idx < rank ? 'step-done' : idx === rank ? 'step-current' : 'step-upcoming'}`}>
              <span className="step-indicator">{idx < rank ? '✓' : idx + 1}</span>
              <span className="step-label">{step.label}</span>
            </li>)}
          </ol>}
          {guide && <div className={`placement-status-banner tone-${item.state.toLowerCase()}`} role="note">
            <p>{guide}</p>
          </div>}
          <div className="placement-meta-grid">
            <div className="placement-meta-item">
              <span className="placement-meta-label">Khoảng thời gian thực tập</span>
              <span className="placement-meta-value"><strong>{item.startDate}</strong> đến <strong>{item.endDate}</strong></span>
            </div>
            <div className="placement-meta-item">
              <span className="placement-meta-label">Thời điểm có hiệu lực</span>
              <span className="placement-meta-value">{new Date(item.effectiveAt).toLocaleString('vi-VN')}</span>
            </div>
            <div className="placement-meta-item">
              <span className="placement-meta-label">Mã tiếp nhận · Phiên bản</span>
              <span className="placement-meta-value"><code>{item.placementId.slice(0, 8)}…</code> · v{item.version}</span>
            </div>
          </div>
          {school && <div className="button-row">
            {item.state === 'PENDING_APPROVAL' && <button className="btn-action-approve" disabled={blocked} onClick={() => {setDecision({item, action: 'approve'});setReference('');setReason('SCHOOL_APPROVED');}}>Phê duyệt tiếp nhận</button>}
            {item.state === 'APPROVED' && <button className="btn-action-activate" disabled={blocked || now < Date.parse(item.effectiveAt)} onClick={() => {setDecision({item, action: 'activate'});setReference('');setReason('START_REQUESTED');}}>Kích hoạt thực tập</button>}
            {['PENDING_APPROVAL', 'APPROVED', 'ACTIVE'].includes(item.state) && <button className="secondary btn-action-cancel" disabled={blocked} onClick={() => {setDecision({item, action: 'cancel'});setReference('');setReason('');}}>Hủy tiếp nhận</button>}
          </div>}
          {item.state === 'APPROVED' && now < Date.parse(item.effectiveAt) && <p className="placement-time-warning">Chưa đến thời điểm kích hoạt. Hồ sơ chưa cho phép giao hoặc nộp nhiệm vụ thực tập.</p>}
          {school && ['APPROVED', 'ACTIVE', 'COMPLETED'].includes(item.state) && <button className="secondary" disabled={blocked}
            onClick={() => checkHandoff(item)}>Kiểm tra bàn giao sang không gian thực tập</button>}
          {handoff && <WorkspaceHandoff link={handoff} placementId={item.placementId} placementState={item.state} effectiveAt={item.effectiveAt} locked={blocked}
            schoolOrgId={school ? org : undefined} termId={demand.data.termId} />}
        </article>;
      })}
      {result.totalPages > 1 && <div className="button-row"><button className="secondary" disabled={blocked || page === 0} onClick={() => setPage(n => n - 1)}>Trang tiếp nhận trước</button>
        <span>Trang {page + 1}/{result.totalPages}</span><button className="secondary" disabled={blocked || page + 1 >= result.totalPages} onClick={() => setPage(n => n + 1)}>Trang tiếp nhận sau</button></div>}
    </>}
    {decision && <form className="placement-decision-panel" onSubmit={e => {e.preventDefault();void command.submit(`/placements/${decision.item.placementId}/${decision.action}`, 'POST', {
      expectedVersion: decision.item.version, decisionRef: reference, reasonCode: reason,
    });}}><fieldset disabled={blocked}><legend>{decision.action === 'approve' ? 'Phê duyệt tiếp nhận' : decision.action === 'activate' ? 'Kích hoạt thực tập' : 'Hủy tiếp nhận'}</legend>
      <label>Căn cứ thao tác tiếp nhận<input required maxLength={120} value={reference} onChange={e => setReference(e.target.value)} /></label>
      <div className="quick-ref-row">
        <span className="quick-ref-label">Gợi ý căn cứ:</span>
        <button type="button" className="quick-ref-chip" onClick={() => setReference(`QD-DUYET-${new Date().getFullYear()}`)}>QD-DUYET-{new Date().getFullYear()}</button>
        <button type="button" className="quick-ref-chip" onClick={() => setReference(`QD-START-${new Date().getFullYear()}`)}>QD-START-{new Date().getFullYear()}</button>
        <button type="button" className="quick-ref-chip" onClick={() => setReference('QD-APPROVE')}>QD-APPROVE</button>
      </div>
      {decision.action === 'cancel' && <label>Lý do hủy<select required value={reason} onChange={e => setReason(e.target.value)}><option value="">Chọn lý do</option>
        <option value="SCHOOL_CANCELLED">Theo quyết định nhà trường</option><option value="STUDENT_WITHDREW">Sinh viên xin dừng</option><option value="ENTERPRISE_CANCELLED">Doanh nghiệp dừng tiếp nhận</option>
      </select></label>}<div className="button-row"><button>Xác nhận {decision.action === 'approve' ? 'phê duyệt' : decision.action === 'activate' ? 'kích hoạt' : 'hủy tiếp nhận'}</button>
        <button className="secondary" type="button" onClick={() => setDecision(null)}>Đóng thao tác tiếp nhận</button></div>
    </fieldset></form>}
    {school && result && <form onSubmit={e => {
      e.preventDefault();const item = ready.find(c => c.proposalItemId === selected);
      if (!item || !effective || !Number.isFinite(Date.parse(effective)) || start > end) {setError('Chọn ứng viên đủ điều kiện và khoảng thời gian tiếp nhận hợp lệ.');return;}
      void command.submit('/school/placements', 'POST', { proposalItemId: item.proposalItemId, expectedProposalVersion: item.version,
        expectedEligibilityVersion: item.eligibilityVersion, effectiveAt: new Date(effective).toISOString(), startDate: start, endDate: end });
    }}><fieldset disabled={blocked}><legend>Tạo tiếp nhận chờ phê duyệt</legend><label>Ứng viên đã được doanh nghiệp đồng ý<select required value={selected} onChange={e => setSelected(e.target.value)}>
      <option value="">Chọn ứng viên</option>{ready.map((item, n) => <option value={item.proposalItemId} key={item.proposalItemId}>Ứng viên {n + 1} · {item.studentUserId}</option>)}
    </select></label>{!ready.length && <p>Chưa có ứng viên đủ điều kiện tạo tiếp nhận trên trang đề xuất đang xem.</p>}
      <div className="cooperation-form-grid"><label>Ngày bắt đầu<input type="date" required value={start} onChange={e => setStart(e.target.value)} /></label>
        <label>Ngày kết thúc<input type="date" required min={start} value={end} onChange={e => setEnd(e.target.value)} /></label></div>
      <label>Thời điểm có hiệu lực<input type="datetime-local" required value={effective} onChange={e => setEffective(e.target.value)} /></label>
      <div className="button-row">
        <button disabled={!ready.length}>Tạo hồ sơ chờ phê duyệt</button>
        <button type="button" className="text-button" onClick={() => {
          const d = new Date();
          setEffective(new Date(d.getTime() - d.getTimezoneOffset() * 60000).toISOString().slice(0, 16));
        }}>Lấy thời điểm hiện tại</button>
      </div>
    </fieldset></form>}
  </section>;
}
