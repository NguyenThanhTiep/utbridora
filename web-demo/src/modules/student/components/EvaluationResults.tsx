import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { EmptyState, Icon, LoadingState } from '../../../components';
import { accessLost, workflowError, workflowRead } from '../../internship/workflow-api';
import { uuidPattern } from '../../internship/workflow';
import { getMyResult } from '../api';
import { useMyCaseIds } from '../internship';
import { dateTime } from '../preferences';
import { StudentNote } from './StudentUI';
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
  const [lookup, setLookup] = useState(''), [lookupId, setLookupId] = useState(''), [verified, setVerified] = useState('');
  const [lookupError, setLookupError] = useState(''), [checking, setChecking] = useState(false), [closed, setClosed] = useState(false);
  const [lookupAttempt, setLookupAttempt] = useState(0);
  const choices = [...new Set([...(caseIds || []), ...(verified ? [verified] : [])])];
  useEffect(() => {
    if (!lookupId || closed) return;
    const c = new AbortController(); setChecking(true); setLookupError('');
    void workflowRead<{ caseId: string; studentUserId: string }>('/t3/cases/' + lookupId, undefined, c.signal).then(value => {
      if (c.signal.aborted) return;
      if (value.caseId !== lookupId || value.studentUserId !== userId) { setClosed(true); return; }
      setVerified(value.caseId); setCaseId(value.caseId);
    }).catch(failure => { if (!c.signal.aborted) {
      if (accessLost(failure)) setClosed(true);
      else setLookupError('Không thể mở hồ sơ này. Kiểm tra mã hồ sơ và quyền truy cập.');
    } }).finally(() => { if (!c.signal.aborted) setChecking(false); });
    return () => c.abort();
  }, [lookupId, lookupAttempt, userId, closed]);
  useEffect(() => {
    const clear = () => { setResult(null); setError(''); setAttempt(n => n + 1); };
    const visibility = () => { clear(); };
    const close = () => { setResult(null); setCaseId(''); setVerified(''); setClosed(true); };
    window.addEventListener('focus', clear); document.addEventListener('visibilitychange', visibility);
    window.addEventListener('session-expired', close);
    return () => { window.removeEventListener('focus', clear); document.removeEventListener('visibilitychange', visibility); window.removeEventListener('session-expired', close); };
  }, []);
  useEffect(() => { if (caseIds) setCaseId(previous => caseIds.includes(previous) ? previous : caseIds.length === 1 ? caseIds[0] : ''); }, [caseIds]);
  useEffect(() => {
    setResult(null); setError('');
    if (!caseId || closed || document.hidden) return;
    const c = new AbortController();
    getMyResult(caseId, c.signal).then(value => { if (!c.signal.aborted) setResult(value); })
      .catch(failure => { if (!c.signal.aborted) { if (accessLost(failure)) setClosed(true); else setError(resultError(failure)); } });
    return () => c.abort();
  }, [caseId, attempt, closed]);
  const evaluation = !closed && result && publishedOf(result, caseId);
  if (closed) return <section className="panel"><p role="alert">Phiên hoặc quyền truy cập đã thay đổi. Dữ liệu kết quả đã được đóng. Vui lòng đăng nhập lại.</p></section>;
  return <section className="panel student-results">
    <div className="section-heading"><div><p className="section-kicker">KẾT QUẢ THỰC TẬP</p><h2>Kết quả kỳ thực tập</h2>
      <p className="section-description">Chỉ hiển thị sau khi nhà trường công bố và cho phép sinh viên xem.</p></div>
      <button type="button" className="secondary" disabled={!caseId} onClick={() => setAttempt(previous => previous + 1)}><Icon name="refresh" size={16} />Tải lại kết quả</button></div>
    <details><summary>Mở hồ sơ bằng mã được cấp</summary><form onSubmit={event => { event.preventDefault();
      if (uuidPattern.test(lookup)) { setResult(null); setCaseId(''); setVerified(''); setLookupError(''); setLookupId(lookup.toLowerCase()); setLookupAttempt(n => n + 1); }
    }}><label>Mã hồ sơ để xem kết quả<input required value={lookup} onChange={event => setLookup(event.target.value)} /></label>
      <p>Có thể mở hồ sơ chưa được giao nhiệm vụ. Máy chủ kiểm tra quyền sở hữu trước khi hiển thị.</p>
      <button type="submit" disabled={checking || !uuidPattern.test(lookup)}>Xác nhận hồ sơ kết quả</button>
    </form></details>
    {checking && <LoadingState label="Đang xác nhận hồ sơ…" />}{lookupError && <p role="alert">{lookupError}</p>}
    {!!caseError && <p className="student-error-note" role="alert">{workflowError(caseError)}</p>}
    {caseIds === null && !verified ? <LoadingState label="Đang tìm hồ sơ thực tập…" />
      : choices.length === 0 ? <EmptyState icon="briefcase" title="Chưa có hồ sơ thực tập" description="Bạn có thể dùng mã hồ sơ được cấp để xem kết quả của hồ sơ chưa có nhiệm vụ." />
      : <>
        {choices.length > 1 && <label>Hồ sơ thực tập<select value={caseId} onChange={event => setCaseId(event.target.value)}>
          <option value="" disabled>Chọn hồ sơ thực tập…</option>{choices.map(id => <option key={id} value={id}>Hồ sơ …{id.slice(-6)}</option>)}</select></label>}
        <div role="region" aria-label="Kết quả đánh giá" aria-live="polite">
          {!caseId ? <p className="muted">Chọn hồ sơ thực tập để xem kết quả.</p>
            : error ? <p className="student-error-note" role="alert">{error}</p>
            : !result ? <LoadingState label="Đang tải kết quả…" />
            : !evaluation ? <><EmptyState icon="lock" title="Chưa có kết quả được công bố" /><ResultConditions /></>
            : <PublishedEvaluation evaluation={evaluation} />}
        </div>
      </>}
  </section>;
}

// Điều kiện tĩnh theo MVP-BR-17 (Figma 477:12579 vẽ dạng ô đánh dấu). Trình duyệt không biết điều kiện nào còn thiếu
// nên chỉ liệt kê, không hiện như trạng thái.
const conditions = ['Nhà trường đã công bố kết quả.', 'Nhà trường cho phép sinh viên xem điểm.', 'HR doanh nghiệp đã bật hiển thị điểm.'];
function ResultConditions() {
  return <div className="student-conditions"><p>Chưa đủ điều kiện hiển thị điểm. Không hiển thị bảng tiêu chí hoặc điểm khi thiếu bất kỳ điều kiện nào:</p>
    <ul>{conditions.map(item => <li key={item}>{item}</li>)}</ul>
    <StudentNote>Điểm chỉ xuất hiện khi đủ cả ba điều kiện công bố và cho phép xem.</StudentNote></div>;
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
