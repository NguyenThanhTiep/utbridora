import { useCallback, useEffect, useState } from 'react';
import { Notice } from '../../../components';
import type { ContractPage } from '../../student/types';
import { scopedRead, workflowError } from './api';
import { proposalStates, type EnterpriseProposal } from './types';
import { SharedProfileView } from './SharedProfileView';
import { useWorkflowCommand } from './useWorkflowCommand';
import { useSharedProfile } from './useSharedProfile';
import { ProposalStateFilter } from './ProposalStateFilter';
import './styles.css';
import { readAll } from '../portal/data';
import { canLeaveDemand } from '../navigation';

export function EnterpriseProposals({ org, cooperationId, focusProposalId }: { org: string; cooperationId: string; focusProposalId?: string }) {
  const [focusId, setFocusId] = useState(focusProposalId || '');
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0), [result, setResult] = useState<ContractPage<EnterpriseProposal> | null>(null);
  const [error, setError] = useState(''), [loading, setLoading] = useState(true), [notice, setNotice] = useState('');
  const [pending, setPending] = useState<Set<string>>(new Set());
  const [state, setState] = useState('');
  const markPending = useCallback((id: string, active: boolean) => setPending(old => {
    if (old.has(id) === active) return old;
    const next = new Set(old); if (active) next.add(id); else next.delete(id); return next;
  }), []);
  const refresh = () => setRevision(value => value + 1);
  useEffect(() => {
    const revalidate = () => { if (!document.hidden && pending.size === 0) setRevision(n => n + 1); };
    window.addEventListener('focus', revalidate);document.addEventListener('visibilitychange', revalidate);
    return () => {window.removeEventListener('focus', revalidate);document.removeEventListener('visibilitychange', revalidate);};
  }, [pending]);
  useEffect(() => {
    const controller = new AbortController(); setLoading(true); setError(''); setResult(null);
    const query = new URLSearchParams({page: String(page), size: '10', ...(state ? {state} : {})});
    const request = focusId ? readAll<EnterpriseProposal>(org, `/enterprise/cooperations/${cooperationId}/proposals`, controller.signal).then(items => {
      const content = items.filter(item => item.proposalItemId === focusId);
      return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 };
    }) : scopedRead<ContractPage<EnterpriseProposal>>(org, `/enterprise/cooperations/${cooperationId}/proposals?${query}`, controller.signal);
    void request
      .then(value => { if (!controller.signal.aborted) {
        if (page > 0 && page >= value.totalPages) setPage(Math.max(0, value.totalPages - 1));
        else setResult(value);
      } }).catch(reason => { if (!controller.signal.aborted) setError(workflowError(reason)); })
      .finally(() => { if (!controller.signal.aborted) setLoading(false); });
    return () => controller.abort();
  }, [org, cooperationId, page, revision, state, focusId]);
  return <section className="proposal-workspace" aria-label="Ứng viên được đề xuất">
    <div className="placement-header-bar">
      <div>
        <h3>Ứng viên được đề xuất</h3>
      </div>
      <button className="secondary" disabled={loading || pending.size > 0} onClick={refresh}>Tải lại ứng viên</button>
    </div>
    <Notice message={error} tone="error" />{notice && <p role="status">{notice}</p>}
    {focusId ? <button className="secondary" disabled={pending.size > 0} onClick={() => { if (canLeaveDemand()) { setFocusId('');setPage(0); } }}>Xem tất cả ứng viên</button>
      : <ProposalStateFilter school={false} value={state} disabled={loading || pending.size > 0} changed={value => {setState(value);setPage(0);}} />}
    {loading && <p role="status">Đang tải ứng viên…</p>}
    {result?.totalElements === 0 && <p>{focusId ? 'Hồ sơ này đã thay đổi hoặc không còn được chia sẻ.' : state ? 'Không có ứng viên được chia sẻ trong trạng thái đã chọn.' : 'Chưa có hồ sơ được chia sẻ hợp lệ.'}</p>}
    {result?.content.map((item, n) => <Candidate key={item.proposalItemId + ':' + item.version + ':' + revision}
      org={org} item={item} number={page * 10 + n + 1} locked={pending.size > 0 && !pending.has(item.proposalItemId)} markPending={markPending} changed={value => { setNotice(value); refresh(); }} />)}
    {result && result.totalPages > 1 && <div className="button-row">
      <button className="secondary" disabled={loading || pending.size > 0 || page === 0} onClick={() => setPage(p => p - 1)}>Trang ứng viên trước</button>
      <span>Trang {page + 1}/{result.totalPages}</span>
      <button className="secondary" disabled={loading || pending.size > 0 || page + 1 >= result.totalPages} onClick={() => setPage(p => p + 1)}>Trang ứng viên sau</button>
    </div>}
  </section>;
}

function Candidate({ org, item, number, changed, locked, markPending }: { org: string; item: EnterpriseProposal; number: number; changed: (notice: string) => void; locked: boolean; markPending: (id: string, active: boolean) => void }) {
  const { profile, error, clear } = useSharedProfile(org, item);
  const [declining, setDeclining] = useState(false), [reason, setReason] = useState(''), [expired, setExpired] = useState(Date.now() >= Date.parse(item.reservationUntil));
  const [unavailable, setUnavailable] = useState(false);
  const [fileError, setFileError] = useState('');
  const command = useWorkflowCommand<EnterpriseProposal>(org,
    value => changed(value.state === 'ENTERPRISE_ACCEPTED' ? 'Đã ghi nhận đồng ý tiếp nhận. Nhà trường sẽ thực hiện bước chốt thực tập.' : 'Đã ghi nhận từ chối ứng viên.'),
    () => { clear(); setUnavailable(true); });
  useEffect(() => {
    markPending(item.proposalItemId, command.blocked);
    return () => markPending(item.proposalItemId, false);
  }, [item.proposalItemId, command.blocked, markPending]);
  useEffect(() => {
    const timer = window.setInterval(() => setExpired(Date.now() >= Date.parse(item.reservationUntil)), 1000);
    return () => window.clearInterval(timer);
  }, [org, item.proposalItemId]);
  const decide = (decision: 'ACCEPT' | 'DECLINE') => command.submit(`/enterprise/proposals/${item.proposalItemId}/decision`, 'PATCH',
    { expectedVersion: item.version, decision, ...(decision === 'DECLINE' ? { reasonCode: reason } : {}) });
  return <article className="proposal-card"><div className="cooperation-demand-title"><h4>Ứng viên {number}</h4>
    <span className={`cooperation-state state-${item.state.toLowerCase()}`}>{item.state === 'DECLINED' ? 'Doanh nghiệp đã từ chối' : proposalStates[item.state] || item.state}</span></div>
    <Notice message={fileError || error || command.message} tone="error" />
    {profile && <SharedProfileView profile={profile} files={{org, proposalItemId: item.proposalItemId,
      denied: message => {setFileError(message);clear();setUnavailable(true);}}} />}
    {!profile && !error && !unavailable && <p role="status">Đang tải hồ sơ được chia sẻ…</p>}
    {unavailable && <button className="secondary" disabled={locked} onClick={() => changed('Đã yêu cầu tải lại trạng thái ứng viên.')}>Kiểm tra lại ứng viên</button>}
    {command.retryable && <div><p>Chưa xác định được kết quả. Thử lại để kiểm tra quyết định đã gửi.</p>
      <button disabled={command.busy} onClick={() => void command.retry()}>Thử lại quyết định đã gửi</button></div>}
    {item.state === 'SHARED' && !unavailable && <div>
      <div className={`reservation-box ${expired ? 'expired' : 'active'}`}>
        <span>{expired ? '⏱️' : '⏳'}</span>
        <p style={{ margin: 0 }}>{expired ? 'Đã hết thời gian giữ chỗ.' : `Giữ chỗ đến ${new Date(item.reservationUntil).toLocaleString('vi-VN')}`}</p>
      </div>
      <fieldset disabled={locked || command.blocked || expired || !profile}>
        {declining ? <form onSubmit={e => { e.preventDefault(); if (reason) void decide('DECLINE'); }}>
          <label>Lý do từ chối<select required value={reason} onChange={e => setReason(e.target.value)}>
            <option value="">Chọn lý do</option><option value="SKILL_MISMATCH">Kỹ năng chưa phù hợp</option>
            <option value="SCHEDULE_CONFLICT">Không phù hợp lịch thực tập</option><option value="OTHER">Lý do khác</option>
          </select></label><div className="button-row"><button disabled={!reason}>Xác nhận từ chối</button>
            <button type="button" className="secondary" onClick={() => setDeclining(false)}>Hủy</button></div>
        </form> : <div className="button-row"><button className="btn-enterprise-accept" onClick={() => void decide('ACCEPT')}>Tiếp nhận thực tập</button>
          <button className="secondary btn-enterprise-decline" onClick={() => setDeclining(true)}>Từ chối</button></div>}
      </fieldset>
    </div>}
  </article>;
}
