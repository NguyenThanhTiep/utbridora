import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import type { ContractPage } from '../../student/types';
import type { Demand } from '../types';
import type { SchoolProposal } from './types';
import { scopedRead, workflowError } from './api';
import { useWorkflowCommand } from './useWorkflowCommand';
import { canLeaveDemand } from '../navigation';

type Placement = { placementId: string; schoolOrgId: string; enterpriseOrgId: string; cooperationId: string; proposalItemId: string;
  state: string; version: number; effectiveAt: string; startDate: string; endDate: string };
type Summary = { demandId: string; cooperationId: string | null; target: number; placed: number; pending: number; remaining: number };
type Link = { state: 'WAITING_HANDOFF' | 'AVAILABLE'; route: string | null };
const states: Record<string, string> = { PENDING_APPROVAL: 'Chờ trường phê duyệt', APPROVED: 'Đã duyệt, chờ bắt đầu', ACTIVE: 'Đang thực tập', CANCELLED: 'Đã hủy', COMPLETED: 'Đã hoàn thành' };

/** Consumer of DEV10's CP-T5UI1 routes. No local state transition substitutes for an unavailable API. */
export function PlacementWorkspace({ org, school, cooperationId, demand, candidates = [], locked = false, changed, pendingChanged }: {
  org: string; school: boolean; cooperationId: string; demand: Demand; candidates?: SchoolProposal[]; locked?: boolean;
  changed?: () => void; pendingChanged?: (pending: boolean) => void;
}) {
  const [result, setResult] = useState<ContractPage<Placement> | null>(null), [summary, setSummary] = useState<Summary | null>(null);
  const [page, setPage] = useState(0), [filter, setFilter] = useState(''), [revision, setRevision] = useState(0), [loading, setLoading] = useState(true);
  const [error, setError] = useState(''), [notice, setNotice] = useState(''), [selected, setSelected] = useState('');
  const [start, setStart] = useState(demand.data.required.dateRange.startDate), [end, setEnd] = useState(demand.data.required.dateRange.endDate), [effective, setEffective] = useState('');
  const [decision, setDecision] = useState<{ item: Placement; action: string } | null>(null), [reference, setReference] = useState(''), [reason, setReason] = useState('');
  const [links, setLinks] = useState<Record<string, Link>>({}), [now, setNow] = useState(Date.now());
  const pendingRef = useRef(pendingChanged); pendingRef.current = pendingChanged;
  const side = school ? 'school' : 'enterprise';
  const command = useWorkflowCommand<Placement>(org, value => {
    setDecision(null); setSelected(''); setNotice(`Đã ghi nhận: ${states[value.state] || value.state}.`);setRevision(n => n + 1);changed?.();
  }, error => { setDecision(null);setError(workflowError(error));setRevision(n => n + 1); });
  useEffect(() => { pendingRef.current?.(command.blocked);return () => pendingRef.current?.(false); }, [command.blocked]);
  useEffect(() => {
    const timer = window.setInterval(() => setNow(Date.now()), 15000);return () => window.clearInterval(timer);
  }, []);
  useEffect(() => {
    const c = new AbortController();setLoading(true);setResult(null);setSummary(null);setLinks({});setError('');
    const query = new URLSearchParams({ cooperationId, page: String(page), size: '25', ...(filter ? { state: filter } : {}) });
    void Promise.all([
      scopedRead<ContractPage<Placement>>(org, `/${side}/placements?${query}`, c.signal),
      scopedRead<Summary>(org, `/${side}/placements/summary?demandId=${encodeURIComponent(demand.demandId)}`, c.signal),
    ]).then(([list, totals]) => {
      if (c.signal.aborted) return;
      if (list.content.some(p => p.cooperationId !== cooperationId || (school ? p.schoolOrgId : p.enterpriseOrgId) !== org)
          || totals.demandId !== demand.demandId || totals.cooperationId !== null && totals.cooperationId !== cooperationId) throw new Error('Scope mismatch');
      setResult(list);setSummary(totals);
    }).catch(error => { if (!c.signal.aborted) setError(error instanceof ApiError && [403, 404, 503].includes(error.status)
      ? 'Chưa truy cập được chức năng tiếp nhận thực tập. Kiểm tra quyền hoặc chờ chức năng sẵn sàng rồi tải lại.' : workflowError(error)); })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [org, school, side, cooperationId, demand.demandId, page, filter, revision]);
  const ready = candidates.filter(item => item.state === 'ENTERPRISE_ACCEPTED' && !item.placementId && item.shareIsActive && item.eligibilityVersion > 0);
  const blocked = locked || command.blocked || loading;
  return <section className="proposal-placement" aria-label="Tiếp nhận thực tập"><h4>Tiếp nhận thực tập</h4>
    <p>Doanh nghiệp đồng ý tiếp nhận, trường tạo hồ sơ chờ duyệt, phê duyệt riêng và kích hoạt khi đến thời điểm có hiệu lực.</p>
    <Notice message={error || command.message} tone="error" /><p role="status">{notice}</p>
    <button className="secondary" disabled={blocked} onClick={() => setRevision(n => n + 1)}>Tải lại tiếp nhận thực tập</button>
    {loading && <p role="status">Đang tải tiếp nhận thực tập…</p>}
    {command.retryable && <button disabled={command.busy} onClick={() => void command.retry()}>Thử lại thao tác tiếp nhận đã gửi</button>}
    {summary && <dl className="proposal-capacity"><div><dt>Chỉ tiêu tiếp nhận</dt><dd>{summary.target}</dd></div><div><dt>Đã chốt</dt><dd>{summary.placed}</dd></div>
      <div><dt>Đang chờ</dt><dd>{summary.pending}</dd></div><div><dt>Còn chỗ</dt><dd>{summary.remaining}</dd></div></dl>}
    {result && <><label>Trạng thái tiếp nhận<select disabled={blocked} value={filter} onChange={e => {setFilter(e.target.value);setPage(0);}}>
      <option value="">Tất cả trạng thái</option>{Object.entries(states).map(([key, text]) => <option value={key} key={key}>{text}</option>)}
    </select></label>{!result.content.length && <p>Chưa có hồ sơ tiếp nhận trong nhóm trạng thái này.</p>}
      {result.content.map((item, n) => <article key={item.placementId} className={`placement-item-card state-${item.state.toLowerCase()}`}>
        <div className="placement-card-header">
          <div className="placement-title-group">
            <span className="placement-seq-badge">#{page * 25 + n + 1}</span>
            <h5>Tiếp nhận {page * 25 + n + 1} · {states[item.state] || item.state}</h5>
          </div>
          <span className={`status-pill pill-${item.state.toLowerCase()}`}>
            <span className="pill-dot" />
            {states[item.state] || item.state}
          </span>
        </div>
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
            <span className="placement-meta-label">Mã tiếp nhận</span>
            <span className="placement-meta-value"><code>{item.placementId.slice(0, 8)}…</code></span>
          </div>
        </div>
        <p className="placement-time-text">Thời gian: {item.startDate} – {item.endDate} · Hiệu lực từ {new Date(item.effectiveAt).toLocaleString('vi-VN')}</p>
        {school && <div className="button-row">
          {item.state === 'PENDING_APPROVAL' && <button className="btn-action-approve" disabled={blocked} onClick={() => {setDecision({item, action: 'approve'});setReference('');setReason('SCHOOL_APPROVED');}}>Phê duyệt tiếp nhận</button>}
          {item.state === 'APPROVED' && <button className="btn-action-activate" disabled={blocked || now < Date.parse(item.effectiveAt)} onClick={() => {setDecision({item, action: 'activate'});setReference('');setReason('START_REQUESTED');}}>Kích hoạt thực tập</button>}
          {['PENDING_APPROVAL', 'APPROVED', 'ACTIVE'].includes(item.state) && <button className="secondary btn-action-cancel" disabled={blocked} onClick={() => {setDecision({item, action: 'cancel'});setReference('');setReason('');}}>Hủy tiếp nhận</button>}
        </div>}
        {item.state === 'APPROVED' && now < Date.parse(item.effectiveAt) && <p className="placement-time-warning">Chưa đến thời điểm kích hoạt. Hồ sơ chưa cho phép giao hoặc nộp nhiệm vụ thực tập.</p>}
        {['APPROVED', 'ACTIVE', 'COMPLETED'].includes(item.state) && <button className="secondary" disabled={blocked} onClick={() => {
          const c = new AbortController();void scopedRead<Link>(org, `/${side}/placements/${item.placementId}/internship-link`, c.signal).then(link => {
            if (link.state === 'AVAILABLE' && (!link.route || !/^\/workspace\/cases\/[0-9a-f-]{36}$/i.test(link.route))) throw new Error('Invalid internship route');
            setLinks(old => ({...old, [item.placementId]: link}));
          }).catch(error => setError(workflowError(error)));
        }}>Kiểm tra bàn giao sang không gian thực tập</button>}
        {links[item.placementId]?.state === 'WAITING_HANDOFF' && <p className="handoff-waiting">Đang chờ bàn giao sang không gian thực tập.</p>}
        {links[item.placementId]?.state === 'AVAILABLE' && <a href={links[item.placementId].route!}
          aria-disabled={blocked} onClick={e => {if (blocked || !canLeaveDemand()) e.preventDefault();}}>Mở hồ sơ thực tập</a>}
      </article>)}
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
