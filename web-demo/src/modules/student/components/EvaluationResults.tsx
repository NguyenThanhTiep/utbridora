import { useEffect, useState, type CSSProperties } from 'react';
import { ApiError } from '../../../api';
import { EmptyState, Icon, LoadingState } from '../../../components';
import { accessLost, workflowError, workflowRead } from '../../internship/workflow-api';
import { uuidPattern } from '../../internship/workflow';
import { getMyResult } from '../api';
import { useMyCaseIds } from '../internship';
import { dateTime } from '../preferences';
import { StudentIcon, StudentNote } from './StudentUI';
import type { EvaluationDto, StudentResultDto } from '../types';
import '../styles.css';

// Chỉ định dạng số server trả để hiển thị; không tính lại điểm ở trình duyệt (CP-WS4: total100 do T3 tính).
const decimal = (value: number) => value.toLocaleString('vi-VN', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
// Độ đầy vòng tổng điểm / thanh điểm chỉ là tỉ lệ hình vẽ của số server trả (total100 trên 100, điểm trên thang 5); không hiện thành số mới.
const share = (value: number, max: number) => Math.min(100, Math.max(0, value / max * 100));
// Màu nhận diện từng tiêu chí theo thứ tự (chỉ để trình bày): chấm ở tên, đoạn trong thanh cơ cấu trọng số, viền nhận xét.
const tones = ['#0b927c', '#2969a9', '#7c5cc4', '#c27a12', '#c2416b', '#0f7f99'];
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
  useEffect(() => { if (caseIds) setCaseId(previous => (!!verified && previous === verified) || caseIds.includes(previous) ? previous : caseIds.length === 1 ? caseIds[0] : ''); }, [caseIds, verified]);
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
            : !evaluation ? <div className="student-result-locked"><EmptyState icon="lock" title="Chưa có kết quả được công bố" /><ResultConditions /></div>
            : <PublishedEvaluation evaluation={evaluation} />}
        </div>
      </>}
  </section>;
}

// Điều kiện tĩnh theo MVP-BR-17 (Figma 477:12579 vẽ dạng ô đánh dấu). Trình duyệt không biết điều kiện nào còn thiếu
// nên chỉ liệt kê kèm biểu tượng, không hiện như trạng thái và không đánh số (vùng chưa công bố không có chữ số).
const conditions = [{ icon: 'university', text: 'Nhà trường đã công bố kết quả.' }, { icon: 'shield', text: 'Nhà trường cho phép sinh viên xem điểm.' },
  { icon: 'building', text: 'HR doanh nghiệp đã bật hiển thị điểm.' }];
function ResultConditions() {
  return <div className="student-conditions"><p>Chưa đủ điều kiện hiển thị điểm. Không hiển thị bảng tiêu chí hoặc điểm khi thiếu bất kỳ điều kiện nào:</p>
    <ul>{conditions.map(item => <li key={item.text}><span aria-hidden="true"><Icon name={item.icon} size={18} /></span>{item.text}</li>)}</ul>
    <StudentNote>Điểm chỉ xuất hiện khi đủ cả ba điều kiện công bố và cho phép xem.</StudentNote></div>;
}

// "Bảng điểm chứng nhận": thẻ đầu có vòng tổng điểm, nhãn đã công bố, chip ngày công bố / bản đánh giá / số tiêu chí và thanh cơ cấu
// trọng số (trang trí, lặp lại dữ liệu bảng nên ẩn với trình đọc màn hình); bảng tiêu chí giữ dạng bảng (hàng, tiêu đề hàng) như trước.
function PublishedEvaluation({ evaluation }: { evaluation: EvaluationDto }) {
  const criteria = new Map(evaluation.template.criteria.map(item => [item.criterionCode, item]));
  const rows = evaluation.scores.map((score, index) => ({ score, criterion: criteria.get(score.criterionCode), tone: tones[index % tones.length] }));
  const weighted = rows.filter(row => row.criterion && row.criterion.weight > 0);
  return <div className="student-result">
    <div className="student-result-hero" style={{ '--value': share(evaluation.total100, 100) } as CSSProperties}>
      <p className="student-result-kicker"><Icon name="check" size={14} />Kết quả đã công bố</p>
      <p className="student-result-total"><span>Tổng điểm (thang 100)</span><strong>{decimal(evaluation.total100)}</strong><small aria-hidden="true">/ 100</small></p>
      <ul className="student-result-chips" aria-label="Thông tin công bố">
        <li><Icon name="calendar" size={15} />Công bố lúc {dateTime(evaluation.publishedAt!)}</li>
        <li><Icon name="layers" size={15} />Bản đánh giá số {evaluation.revisionNo}</li>
        <li><StudentIcon name="chart" size={15} />{rows.length} tiêu chí</li>
      </ul>
      {weighted.length > 0 && <div className="student-result-weights" aria-hidden="true"><p>Cơ cấu trọng số</p>
        <div className="student-weight-bar">{weighted.map(row => <span key={row.score.criterionCode} style={{ '--w': row.criterion!.weight, '--tone': row.tone } as CSSProperties} />)}</div>
        <ul className="student-weight-legend">{weighted.map(row => <li key={row.score.criterionCode} style={{ '--tone': row.tone } as CSSProperties}>
          {row.criterion!.label}<b>{decimal(row.criterion!.weight)}%</b></li>)}</ul></div>}
      <span className="student-result-seal" aria-hidden="true"><StudentIcon name="award" size={28} /></span>
    </div>
    <div className="student-table-wrap"><table className="student-result-table"><caption><StudentIcon name="chart" size={17} />Điểm theo tiêu chí</caption>
      <thead><tr><th scope="col">Tiêu chí</th><th scope="col">Trọng số</th><th scope="col">Điểm (thang 5)</th><th scope="col">Nhận xét</th></tr></thead>
      <tbody>{rows.map(({ score, criterion, tone }) => <tr key={score.criterionCode} style={{ '--tone': tone } as CSSProperties}>
        <th scope="row"><span className="student-result-dot" aria-hidden="true" />{criterion?.label ?? score.criterionCode}</th>
        <td>{criterion ? <span className="student-result-weight">{decimal(criterion.weight)}%</span> : '—'}</td>
        <td><span className="student-result-score"><b>{decimal(score.score)}</b><small aria-hidden="true">/5</small></span>
          <span className="student-score-bar" aria-hidden="true" style={{ '--value': share(score.score, 5) } as CSSProperties}><span /></span></td>
        <td className="student-pre-line">{score.comment ? <span className="student-result-comment">{score.comment}</span> : '—'}</td></tr>)}</tbody></table></div>
  </div>;
}
