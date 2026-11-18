import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import { canLeaveDemand } from '../navigation';
import { scopedRead, workflowError } from './api';
import { progressLabels, validProgress, type PlacementProgress, type ProgressPage, type ProgressCounters } from './placementProgress';
import './styles.css';

export function PlacementOverview({ org, school, select }: { org: string; school: boolean; select: (row: PlacementProgress) => void }) {
  const [result, setResult] = useState<ProgressPage | null>(null), [loading, setLoading] = useState(true), [error, setError] = useState('');
  const [draft, setDraft] = useState(''), [query, setQuery] = useState(''), [schoolId, setSchool] = useState(''), [term, setTerm] = useState(''), [status, setStatus] = useState('');
  const [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const side = school ? 'school' : 'enterprise';
  useEffect(() => {
    const refresh = () => {if (!document.hidden && canLeaveDemand()) setRevision(value => value + 1);};
    window.addEventListener('focus', refresh);document.addEventListener('visibilitychange', refresh);
    window.addEventListener('placement-updated', refresh);
    return () => {window.removeEventListener('focus', refresh);document.removeEventListener('visibilitychange', refresh);window.removeEventListener('placement-updated', refresh);};
  }, []);
  useEffect(() => {
    const controller = new AbortController();setLoading(true);setError('');
    const filters = new URLSearchParams({ page: String(page), size: '10', ...(query ? { q: query } : {}), ...(schoolId ? { schoolOrgId: schoolId } : {}), ...(term ? { termId: term } : {}), ...(status ? { status } : {}) });
    void scopedRead<ProgressPage>(org, `/${side}/placements/progress?${filters}`, controller.signal).then(value => {
      if (controller.signal.aborted) return;
      if (!validProgress(value, org, school)) throw new Error('Invalid scoped progress response');
      if (page > 0 && page >= value.totalPages) {setPage(Math.max(0, value.totalPages - 1));return;}
      setResult(value);
    }).catch(reason => {if (!controller.signal.aborted) {
      setResult(null);setError(reason instanceof ApiError && [404, 503].includes(reason.status)
        ? 'Chưa tải được tổng hợp tiếp nhận. Kiểm tra quyền theo trường/kỳ hoặc chức năng đã được cập nhật trên máy chủ.' : workflowError(reason));
    }}).finally(() => {if (!controller.signal.aborted) setLoading(false);});
    return () => controller.abort();
  }, [org, school, side, query, schoolId, term, status, page, revision]);
  const resetPage = (action: () => void) => {if (canLeaveDemand()) {action();setPage(0);}};
  const progress = result && result.totals && Array.isArray(result.content) && validProgress(result, org, school) ? result : null;
  const tiles: [keyof ProgressCounters, string][] = [['target', 'Chỉ tiêu đã duyệt'], ['placed', 'Đã tiếp nhận'],
    ['pending', 'Chờ duyệt hoặc còn giữ chỗ'], ['remaining', 'Còn thiếu']];
  if (school) tiles.push(['reservations', 'Đang giữ chỗ'], ['declined', 'Đã từ chối'], ['enterpriseAccepted', 'DN chấp thuận'],
    ['pendingCreation', 'Đủ điều kiện tạo tiếp nhận'], ['pendingApproval', 'Chờ phê duyệt'], ['openForNomination', 'Còn có thể đề cử']);
  return <section className="panel placement-overview" aria-label="Tổng hợp tiếp nhận theo phạm vi">
    <div className="placement-header-bar"><h3>Tiến độ tiếp nhận theo nhu cầu</h3><button className="secondary" disabled={loading} onClick={() => {if (canLeaveDemand()) setRevision(value => value + 1);}}>Tải lại tổng hợp</button></div>
    <form className="placement-overview-filters" onSubmit={event => {event.preventDefault();resetPage(() => setQuery(draft.trim()));}}>
      <label>Tìm nhu cầu và tiếp nhận<input type="search" maxLength={150} value={draft} onChange={event => setDraft(event.target.value)} placeholder="Vị trí tuyển, mã nhu cầu, phiếu, tiếp nhận hoặc mã sinh viên" /></label>
      {!school && <label>Trường tiếp nhận<select value={schoolId} onChange={event => resetPage(() => {setSchool(event.target.value);setTerm('');})}><option value="">Tất cả trường</option>{progress?.schools.map(choice => <option key={choice.id} value={choice.id}>{choice.name}</option>)}</select></label>}
      <label>Kỳ tiếp nhận<select value={term} onChange={event => resetPage(() => setTerm(event.target.value))}><option value="">Tất cả kỳ</option>{progress?.terms.map(choice => <option key={choice.id} value={choice.id}>{choice.name}</option>)}</select></label>
      <label>Tình trạng chỉ tiêu<select value={status} onChange={event => resetPage(() => setStatus(event.target.value))}><option value="">Tất cả tình trạng</option>{Object.entries(progressLabels).map(([code, label]) => <option key={code} value={code}>{label}</option>)}</select></label>
      <button disabled={loading}>Tìm theo phạm vi</button>
      {(query || schoolId || term || status) && <button type="button" className="secondary" onClick={() => resetPage(() => {setDraft('');setQuery('');setSchool('');setTerm('');setStatus('');})}>Xóa lọc tổng hợp</button>}
    </form>
    <Notice tone="error" message={error} />{loading && <p role="status">Đang cập nhật tổng hợp…</p>}
    {progress && <>
      <ul className="placement-progress-counters" aria-label="Chỉ tiêu của toàn bộ kết quả lọc">
        {tiles.map(([key, label]) => <li key={key}><span>{label}</span><strong>{progress.totals[key]}</strong></li>)}
      </ul>
      <p className="placement-overview-help">Chỉ tiêu chỉ cộng hợp tác đã duyệt.{school && ' Chờ tạo yêu cầu doanh nghiệp đồng ý, lời mời được nhận, consent và chia sẻ còn hiệu lực; số đang giữ chỗ được theo dõi riêng.'}</p>
      {progress.totalElements === 0 ? <p>Không có nhu cầu trong phạm vi và bộ lọc đã chọn.</p> : <div className="proposal-table"><table><caption>Tiến độ theo nhu cầu trong phạm vi được cấp</caption>
        <thead><tr><th>Vị trí tuyển</th><th>{school ? 'Doanh nghiệp' : 'Trường'}</th><th>Kỳ</th><th>Tình trạng</th><th>Chỉ tiêu</th><th>Đã nhận</th><th>Còn thiếu</th><th>Chi tiết</th></tr></thead>
        <tbody>{progress.content.map(row => <tr key={row.demandId}><th scope="row">{row.title}<small>{row.demandId}</small></th><td>{school ? row.enterpriseName : row.schoolName}</td><td>{row.termCode}</td>
          <td><span className={'placement-progress-pill progress-' + row.status.toLowerCase()}>{progressLabels[row.status]}</span></td><td>{row.counters.target}</td><td>{row.counters.placed}</td><td>{row.counters.remaining}</td>
          <td><button type="button" className="secondary" disabled={loading} onClick={() => {if (canLeaveDemand()) select(row);}}>Xem tiếp nhận</button></td></tr>)}</tbody>
      </table></div>}
      {progress.totalPages > 1 && <div className="button-row"><button className="secondary" disabled={loading || page === 0} onClick={() => {if (canLeaveDemand()) setPage(page - 1);}}>Tổng hợp trước</button><span>Trang tổng hợp {page + 1}/{progress.totalPages}</span><button className="secondary" disabled={loading || page + 1 >= progress.totalPages} onClick={() => {if (canLeaveDemand()) setPage(page + 1);}}>Tổng hợp sau</button></div>}
    </>}
  </section>;
}
