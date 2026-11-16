import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import { scopedRead, workflowError } from './api';
import { canLeaveDemand } from '../navigation';
import { SchoolFollowUpPanels } from './SchoolFollowUpPanels';
import { evaluationLabels, schoolEvaluation, type SchoolEvaluation } from './schoolEvaluation';

/** Read-only consumer of T3's EvaluationQuery. No local grades or T1 permission bypass. */
export function SchoolPlacementFollowUp({ org, termId, caseId, placementId, placementState }: { org: string; termId: string; caseId: string; placementId?: string; placementState?: string }) {
  const [requested, setRequested] = useState(false), [revision, setRevision] = useState(0);
  const scopeKey = [org, termId, caseId, placementId].join(':');
  const [loaded, setLoaded] = useState<{ key: string; rows: SchoolEvaluation[] } | null>(null), [message, setMessage] = useState(''), [loading, setLoading] = useState(false);
  const rows = loaded?.key === scopeKey ? loaded.rows : null;
  useEffect(() => {
    if (!requested) return;
    const refresh = () => {if (!document.hidden && canLeaveDemand()) setRevision(value => value + 1);};
    window.addEventListener('focus', refresh);document.addEventListener('visibilitychange', refresh);
    return () => {window.removeEventListener('focus', refresh);document.removeEventListener('visibilitychange', refresh);};
  }, [requested]);
  useEffect(() => {
    if (!requested) return;
    const controller = new AbortController();setLoaded(null);setMessage('');setLoading(true);
    void (async () => {
      const values = new Map<string, SchoolEvaluation>();
      for (let page = 0; ; page++) {
        const query = new URLSearchParams({ caseId, termId, ...(placementId ? { placementId } : {}), page: String(page), size: '100' });
        const result = await scopedRead<{ content: unknown[]; totalPages: number }>(org, '/t3/school/evaluations?' + query, controller.signal);
        if (!result || !Array.isArray(result.content) || !Number.isSafeInteger(result.totalPages) || result.totalPages < 0) throw new Error('Invalid evaluation page');
        for (const value of result.content) {const row = schoolEvaluation(value, { caseId, termId, placementId });if (row) values.set(row.evaluationId + ':' + row.revisionNo, row);}
        if (page + 1 >= result.totalPages) break;
        if (page >= 10000) throw new Error('Incomplete evaluation results');
      }
      if (!controller.signal.aborted) setLoaded({ key: scopeKey, rows: [...values.values()].sort((a, b) => (b.revisionNo || 0) - (a.revisionNo || 0)) });
    })().catch(error => {if (!controller.signal.aborted) setMessage(error instanceof ApiError && [404, 503].includes(error.status)
      ? 'Đang chờ T3 cung cấp chức năng đọc đánh giá theo trường và kỳ trong môi trường này.'
      : error instanceof ApiError && [401, 403].includes(error.status) ? 'Bạn chưa được cấp quyền xem đánh giá của hồ sơ này.' : workflowError(error));
    }).finally(() => {if (!controller.signal.aborted) setLoading(false);});
    return () => controller.abort();
  }, [org, termId, caseId, placementId, scopeKey, requested, revision]);
  return <section className="school-placement-followup" aria-label="Theo dõi đánh giá và lịch sử">
    <h4>Theo dõi sau bàn giao</h4><p>Đánh giá do không gian thực tập T3 cung cấp; quyền xem do máy chủ kiểm tra theo trường và kỳ.</p>
    <div className="t5-detail-grid"><section className="t5-detail-card" aria-label="Giám sát đánh giá & công bố điểm">
      <div className="t5-card-heading"><h5>Theo dõi đánh giá</h5><span className="t5-badge t5-badge-info">Dữ liệu từ T3</span></div>
      <button type="button" className="secondary" disabled={loading} onClick={() => {if (canLeaveDemand()) {setRequested(true);setRevision(value => value + 1);}}}>Tải đánh giá từ T3</button>
      {loading && <p role="status">Đang tải đánh giá…</p>}<Notice message={message} tone="info" />
      {!requested && <p>Chọn tải đánh giá để kiểm tra kết quả hiện tại của hồ sơ.</p>}
      {rows && (rows.length === 0 ? <p>Chưa có đánh giá được phép xem cho hồ sơ này.</p> : <ul className="school-evaluation-list">{rows.map(row => <li key={row.evaluationId + ':' + row.revisionNo}>
        <span className={'t5-badge ' + (row.state === 'PUBLISHED' ? 't5-badge-success' : 't5-badge-warning')}>{evaluationLabels[row.state]}</span>
        <p>{evaluationLabels[row.state]}{row.revisionNo !== null && ` · Lần ${row.revisionNo}`} · {row.total100 === null ? 'Chưa có điểm tổng kết' : `Điểm: ${row.total100}/100`}{row.publishedAt && ` · Công bố: ${new Date(row.publishedAt).toLocaleString('vi-VN')}`}</p>
        {row.comments.length > 0 && <div><strong>Nhận xét mentor</strong>{row.comments.map((comment, index) => <p key={index}>{comment}</p>)}</div>}
      </li>)}</ul>)}
      {rows && !rows.some(row => row.state === 'PUBLISHED') && <p>Chưa có đánh giá đã công bố từ T3 cho hồ sơ thực tập này.</p>}
      <ul className="t5-checklist" aria-label="Điều kiện công bố điểm"><li><span>Đánh giá do T3 cung cấp</span><span className={'t5-badge ' + (rows?.length ? 't5-badge-success' : 't5-badge-info')}>{rows?.length ? 'Đã có dữ liệu' : 'Chưa xác nhận'}</span></li>
        <li><span>Quyền và căn cứ công bố của trường</span><span className="t5-badge t5-badge-info">Máy chủ kiểm tra</span></li></ul>
      <p className="field-hint">Công bố kết quả cần thao tác có quyền và phiên bản hiện hành tại dịch vụ đánh giá. Màn hình này chỉ đọc kết quả.</p>
    </section><SchoolFollowUpPanels evaluations={rows} placementState={placementState} /></div>
  </section>;
}
