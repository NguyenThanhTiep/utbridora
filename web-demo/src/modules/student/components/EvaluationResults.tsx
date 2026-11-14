import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { EmptyState, Icon, LoadingState } from '../../../components';
import { accessLost, workflowError } from '../../internship/workflow-api';
import { getMyResult } from '../api';
import { useMyCaseIds } from '../internship';
import { dateTime } from '../preferences';
import type { EvaluationDto, StudentResultDto } from '../types';
import '../styles.css';

// Chỉ định dạng số server trả để hiển thị; không tính lại điểm ở trình duyệt (CP-WS4: total100 do T3 tính).
const decimal = (value: number) => value.toLocaleString('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
// Thiếu bất kỳ tín hiệu công bố nào thì xử lý như HIDDEN, kể cả khi server lỡ gửi kèm evaluation.
function publishedOf(result: StudentResultDto, caseId: string): EvaluationDto | null {
  const evaluation = result.evaluation;
  return result.caseId === caseId && result.visibility === 'VISIBLE' && result.reasonCode === 'VISIBLE' && !!evaluation
    && evaluation.caseId === caseId && evaluation.state === 'PUBLISHED' && !!evaluation.publishedAt ? evaluation : null;
}
// Không hiện nguyên văn message server để tránh lộ chi tiết chính sách xem điểm.
const resultError = (error: unknown) => accessLost(error) ? 'Phiên hoặc quyền truy cập đã thay đổi. Vui lòng đăng nhập lại để xem kết quả.'
  : error instanceof ApiError && [404, 501, 503].includes(error.status) ? 'Chưa xem được kết quả: chức năng chưa sẵn sàng hoặc hồ sơ không còn truy cập được. Vui lòng thử lại sau.'
  : 'Chưa tải được kết quả đánh giá.' + (error instanceof ApiError && error.requestId ? ' Mã hỗ trợ: ' + error.requestId + '.' : '');

// T2-F27 / US-T2-13: sinh viên xem kết quả đã công bố của hồ sơ thực tập của mình (GET /t3/me/results, CP-WS4).
export function EvaluationResults({ userId }: { userId: string }) {
  const { caseIds, error: caseError } = useMyCaseIds(userId);
  const [caseId, setCaseId] = useState(''), [result, setResult] = useState<StudentResultDto | null>(null);
  const [error, setError] = useState(''), [attempt, setAttempt] = useState(0);
  useEffect(() => { if (caseIds) setCaseId(previous => caseIds.includes(previous) ? previous : caseIds.length === 1 ? caseIds[0] : ''); }, [caseIds]);
  useEffect(() => {
    setResult(null); setError('');
    if (!caseId) return;
    const c = new AbortController();
    getMyResult(caseId, c.signal).then(value => { if (!c.signal.aborted) setResult(value); })
      .catch(failure => { if (!c.signal.aborted) setError(resultError(failure)); });
    return () => c.abort();
  }, [caseId, attempt]);
  const evaluation = result && publishedOf(result, caseId);
  return <section className="panel">
    <div className="section-heading"><div><p className="section-kicker">KẾT QUẢ ĐÁNH GIÁ</p><h2>Kết quả kỳ thực tập</h2>
      <p className="section-description">Chỉ hiển thị sau khi nhà trường công bố và cho phép sinh viên xem.</p></div>
      <button type="button" className="secondary" disabled={!caseId} onClick={() => setAttempt(previous => previous + 1)}><Icon name="refresh" size={16} />Tải lại kết quả</button></div>
    {caseError ? <p className="student-error-note" role="alert">{workflowError(caseError)}</p>
      : caseIds === null ? <LoadingState label="Đang tìm hồ sơ thực tập…" />
      : caseIds.length === 0 ? <EmptyState icon="briefcase" title="Chưa có hồ sơ thực tập" description="Kết quả đánh giá gắn với hồ sơ thực tập. Hồ sơ hiện ở đây khi bạn được giao nhiệm vụ." />
      : <>
        {caseIds.length > 1 && <label>Hồ sơ thực tập<select value={caseId} onChange={event => setCaseId(event.target.value)}>
          <option value="" disabled>Chọn hồ sơ thực tập…</option>{caseIds.map(id => <option key={id} value={id}>Hồ sơ …{id.slice(-6)}</option>)}</select></label>}
        <div role="region" aria-label="Kết quả đánh giá" aria-live="polite">
          {!caseId ? <p className="muted">Chọn hồ sơ thực tập để xem kết quả.</p>
            : error ? <p className="student-error-note" role="alert">{error}</p>
            : !result ? <LoadingState label="Đang tải kết quả…" />
            : !evaluation ? <EmptyState icon="lock" title="Chưa có kết quả được công bố" />
            : <PublishedEvaluation evaluation={evaluation} />}
        </div>
      </>}
  </section>;
}

function PublishedEvaluation({ evaluation }: { evaluation: EvaluationDto }) {
  const criteria = new Map(evaluation.template.criteria.map(item => [item.criterionCode, item]));
  return <div>
    <p className="student-result-total"><span>Tổng điểm (thang 100)</span><strong>{decimal(evaluation.total100)}</strong></p>
    <p className="student-list-meta">Công bố lúc {dateTime(evaluation.publishedAt!)} · Bản đánh giá số {evaluation.revisionNo}</p>
    <div className="student-table-wrap"><table className="student-result-table"><caption>Điểm theo tiêu chí</caption>
      <thead><tr><th scope="col">Tiêu chí</th><th scope="col">Trọng số</th><th scope="col">Điểm (thang 5)</th><th scope="col">Nhận xét</th></tr></thead>
      <tbody>{evaluation.scores.map(score => {
        const criterion = criteria.get(score.criterionCode);
        return <tr key={score.criterionCode}><th scope="row">{criterion?.label ?? score.criterionCode}</th>
          <td>{criterion ? decimal(criterion.weight) + '%' : '—'}</td><td>{decimal(score.score)}</td><td className="student-pre-line">{score.comment || '—'}</td></tr>;
      })}</tbody></table></div>
  </div>;
}
