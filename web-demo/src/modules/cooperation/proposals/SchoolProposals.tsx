import { useCallback, useEffect, useState } from 'react';
import { Notice } from '../../../components';
import type { ContractPage } from '../../student/types';
import { scopedRead, workflowError } from './api';
import { proposalStates, type SchoolProposal, type Capacity } from './types';
import { PublicationEditor } from './PublicationEditor';
import { ShareEditor } from './ShareEditor';
import { NominationPanel } from './NominationPanel';
import { InvitationAction } from './InvitationAction';
import type { Demand } from '../types';
import { PlacementWorkspace } from './PlacementWorkspace';
import { ConsentStatus } from './ConsentStatus';
import { ProposalStateFilter } from './ProposalStateFilter';
import './styles.css';

function invitationLabel(item: SchoolProposal) {
  if (!item.invitationId) return 'Chưa gửi';
  if (item.state === 'INVITED') return 'Đang chờ sinh viên';
  if (item.state === 'DECLINED' && !item.shareId) return 'Sinh viên đã từ chối';
  if (item.state === 'REVOKED') return 'Đã rút lời đồng ý';
  if (item.state === 'EXPIRED') return 'Hết hạn';
  return 'Sinh viên đã đồng ý';
}
export function SchoolProposals({ org, cooperationId, demand }: { org: string; cooperationId: string; demand?: Demand }) {
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0), [error, setError] = useState(''), [loading, setLoading] = useState(true);
  const [result, setResult] = useState<ContractPage<SchoolProposal> | null>(null), [capacity, setCapacity] = useState<Capacity | null>(null);
  const [pending, setPending] = useState<Set<string>>(new Set()), [notice, setNotice] = useState('');
  const [state, setState] = useState('');
  const markPending = useCallback((id: string, active: boolean) => setPending(old => {
    if (old.has(id) === active) return old;
    const next = new Set(old); if (active) next.add(id); else next.delete(id); return next;
  }), []);
  useEffect(() => {
    const revalidate = () => {if (!document.hidden && pending.size === 0) setRevision(n => n + 1);};
    window.addEventListener('focus', revalidate);document.addEventListener('visibilitychange', revalidate);
    return () => {window.removeEventListener('focus', revalidate);document.removeEventListener('visibilitychange', revalidate);};
  }, [pending]);
  useEffect(() => {
    const c = new AbortController(); setLoading(true); setResult(null); setCapacity(null); setError('');
    const query = new URLSearchParams({page: String(page), size: '25', ...(state ? {state} : {})});
    void Promise.all([
      scopedRead<ContractPage<SchoolProposal>>(org, `/school/cooperations/${cooperationId}/proposals?${query}`, c.signal),
      scopedRead<Capacity>(org, `/school/cooperations/${cooperationId}/capacity`, c.signal),
    ]).then(([list, summary]) => { if (!c.signal.aborted) {
      if (page > 0 && page >= list.totalPages) setPage(Math.max(0, list.totalPages - 1));
      else {setResult(list); setCapacity(summary);}
    } })
      .catch(reason => { if (!c.signal.aborted) setError(workflowError(reason)); })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [org, cooperationId, page, revision, state]);
  return <section className="proposal-workspace" aria-label="Tiến trình đề xuất"><h3>Tiến trình đề xuất và chỉ tiêu</h3>
    <Notice message={error} tone="error" /><p role="status">{notice}</p><button className="secondary" disabled={loading || pending.size > 0} onClick={() => setRevision(n => n + 1)}>Tải lại tiến trình</button>
    {loading && <p role="status">Đang tải tiến trình…</p>}
    {capacity && <dl className="proposal-capacity"><div><dt>Chỉ tiêu tuyển</dt><dd>{capacity.quantity}</dd></div>
      <div><dt>Đang giữ chỗ</dt><dd>{capacity.activeReservations}</dd></div><div><dt>Đã nhận (chốt thực tập)</dt><dd>{capacity.placedCount}</dd></div>
      <div><dt>Còn lại</dt><dd>{capacity.openForNomination}</dd></div></dl>}
    {demand && <NominationPanel key={org + cooperationId + ':' + revision} org={org} cooperationId={cooperationId} demand={demand}
      locked={pending.size > 0 && !pending.has('nomination')} pendingChanged={active => markPending('nomination', active)}
      changed={message => { setNotice(message); setRevision(n => n + 1); }} />}
    <ProposalStateFilter school value={state} disabled={loading || pending.size > 0} changed={value => {setState(value);setPage(0);}} />
    {result?.totalElements === 0 && <p>{state ? 'Không có đề xuất trong trạng thái đã chọn.' : 'Chưa có ứng viên được đề xuất.'}</p>}
    {!!result?.content.length && <div className="proposal-table"><table><caption>Tiến trình của từng ứng viên</caption>
      <thead><tr><th>Ứng viên</th><th>Lời mời</th><th>Sinh viên đồng ý chia sẻ</th><th>Chia sẻ hồ sơ</th><th>Doanh nghiệp phản hồi</th><th>Trạng thái hiện tại</th></tr></thead>
      <tbody>{result.content.map((item, n) => <tr key={item.proposalItemId}>
        <th scope="row">Ứng viên {page * 25 + n + 1}<small>{item.studentUserId}</small></th>
        <td>{invitationLabel(item)}<InvitationAction key={item.proposalItemId + ':invite:' + revision} org={org} item={item}
          locked={pending.size > 0 && !pending.has(item.proposalItemId)} pendingChanged={active => markPending(item.proposalItemId, active)}
          changed={message => { setNotice(message); setRevision(n => n + 1); }} /></td>
        <td><ConsentStatus item={item} /></td><td><ShareEditor key={item.proposalItemId + ':' + item.version + ':' + revision} org={org} item={item}
          locked={pending.size > 0 && !pending.has(item.proposalItemId)}
          changed={message => { setNotice(message); setRevision(n => n + 1); }}
          pendingChanged={active => markPending(item.proposalItemId, active)} /></td>
        <td>{['ENTERPRISE_ACCEPTED', 'PLACED'].includes(item.state) ? 'Đã đồng ý' : item.state === 'DECLINED' && item.shareId ? 'Đã từ chối' : 'Chưa phản hồi'}</td>
        <td>{proposalStates[item.state] || item.state}</td>
      </tr>)}</tbody></table></div>}
    {result && result.totalPages > 1 && <div className="button-row"><button className="secondary" disabled={page === 0 || loading || pending.size > 0} onClick={() => setPage(n => n - 1)}>Trang tiến trình trước</button>
      <span>Trang {page + 1}/{result.totalPages}</span><button className="secondary" disabled={page + 1 >= result.totalPages || loading || pending.size > 0} onClick={() => setPage(n => n + 1)}>Trang tiến trình sau</button></div>}
    {demand && <PlacementWorkspace org={org} school cooperationId={cooperationId} demand={demand} candidates={result?.content || []}
      locked={pending.size > 0 && !pending.has('placement')} pendingChanged={active => markPending('placement', active)} changed={() => setRevision(n => n + 1)} />}
    <PublicationEditor key={org + cooperationId} org={org} cooperationId={cooperationId} />
  </section>;
}
