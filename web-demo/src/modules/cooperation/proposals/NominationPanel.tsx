import { useEffect, useMemo, useRef, useState } from 'react';
import { Notice } from '../../../components';
import { PagerNav } from '../../../ui/PagerNav';
import { askConfirm } from '../../../confirm';
import type { ContractPage } from '../../student/types';
import type { Demand } from '../types';
import type { Capacity } from './types';
import { scopedRead, workflowError } from './api';
import { useWorkflowCommand } from './useWorkflowCommand';
import { formatCriterionLabel, useStudentIdentities } from './useStudentIdentities';

type MatchRun = { matchRunId: string; demandId: string; demandVersion: number; criteriaVersion: number; state: string };
type Evaluated = { audience: string; summary: { run: MatchRun; matched: number; missingData: number; notMatched: number } };
type Candidate = { studentCode: string; majorCode: string; eligibilityVersion: number; selectable: boolean; reasonCode: string | null;
  match: { studentUserId: string; overallStatus: string; fitScore: number | null; details: { criterionCode: string; type: string; status: string }[] } };
type Context = { cooperationId: string; cooperationVersion: number; demandVersion: number; criteriaVersion: number; batchVersion: number | null;
  capacity: Capacity; run: MatchRun; candidates: ContractPage<Candidate> };
type Nominated = { batchId: string; batchVersion: number; items: { proposalItemId: string }[] };
type EligibilityDecision = { studentUserId: string; schoolApproved: boolean };
const reasons: Record<string, string> = { PROPOSAL_ALREADY_EXISTS: 'Đã có trong danh sách đề xuất', CANDIDATE_NOT_MATCH: 'Chưa đạt đủ tiêu chí bắt buộc',
  SCHOOL_APPROVAL_REQUIRED: 'Chưa được trường xác nhận đủ điều kiện thực tập', TERM_INVALID: 'Kỳ chưa hoạt động', ACCESS_DENIED: 'Tài khoản hoặc quyền sinh viên không còn hiệu lực' };
const selectable = (row: Candidate) => row.selectable && row.match.overallStatus === 'MATCH'
  && row.match.details.filter(d => d.type === 'REQUIRED').every(d => d.status === 'PASS');

export function NominationPanel({ org, cooperationId, demand, locked, changed, pendingChanged }: {
  org: string; cooperationId: string; demand: Demand; locked: boolean; changed: (notice: string) => void; pendingChanged: (value: boolean) => void;
}) {
  const [run, setRun] = useState<MatchRun | null>(null), [context, setContext] = useState<Context | null>(null);
  const [filter, setFilter] = useState('MATCH'), [page, setPage] = useState(0), [revision, setRevision] = useState(0);
  const [loading, setLoading] = useState(false), [error, setError] = useState(''), [selected, setSelected] = useState<string[]>([]);
  const [approving, setApproving] = useState<Candidate | null>(null), [decisionRef, setDecisionRef] = useState('');
  const pendingRef = useRef(pendingChanged); pendingRef.current = pendingChanged;
  const command = useWorkflowCommand<Evaluated | Nominated | EligibilityDecision>(org, value => {
    if ('summary' in value) { setRun(value.summary.run); setPage(0); setSelected([]); setRevision(n => n + 1); }
    else if ('items' in value) changed(`Đã tạo đề xuất cho ${value.items.length} sinh viên. Tiếp theo, gửi lời mời cho từng người ở bảng tiến trình.`);
    else { setApproving(null);setRevision(n => n + 1); }
  }, error => { setError(workflowError(error)); setContext(null); setSelected([]); });
  useEffect(() => { pendingRef.current(command.blocked || loading); return () => pendingRef.current(false); }, [command.blocked, loading]);
  useEffect(() => {
    if (!run) return;
    const c = new AbortController(); setLoading(true); setContext(null); setError(''); setSelected([]);
    const query = new URLSearchParams({ matchRunId: run.matchRunId, overallStatus: filter, page: String(page), size: '25' });
    void scopedRead<Context>(org, `/school/cooperations/${cooperationId}/nomination-candidates?${query}`, c.signal)
      .then(value => { if (!c.signal.aborted) setContext(value); }).catch(error => { if (!c.signal.aborted) setError(workflowError(error)); })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [org, cooperationId, run, filter, page, revision]);
  const rows = context?.candidates.content || [], available = rows.filter(selectable), slots = context?.capacity.openForNomination || 0;
  const candidateIds = useMemo(() => rows.map(row => row.match.studentUserId), [rows]);
  const identities = useStudentIdentities(candidateIds, demand.data.termId);
  const disabled = locked || command.blocked || loading;
  return <section className="proposal-nomination" aria-label="Chọn sinh viên đề xuất">
    <div className="nomination-header">
      <div>
        <h4 aria-label="Chọn sinh viên cho đề nghị đã duyệt">Chọn sinh viên</h4>
        <p>Xác nhận điều kiện thực tập và bổ sung sinh viên vào danh sách đề xuất. Ứng viên đã đề cử bên Đối chiếu hồ sơ sẽ hiển thị trực tiếp ở bảng tiến trình bên dưới.</p>
      </div>
      <button className="nomination-find-btn" disabled={disabled} onClick={() => { setError(''); void command.submit('/matching/evaluate', 'POST', {
        demandId: demand.demandId, expectedDemandVersion: demand.version, expectedCriteriaVersion: demand.criteriaVersion,
      }); }}>Tìm sinh viên phù hợp</button>
    </div>
    <Notice message={error || command.message} tone="error" />
    {command.retryable && <div className="button-row"><button disabled={command.busy} onClick={() => void command.retry()}>Thử lại thao tác tìm hoặc đề xuất đã gửi</button></div>}
    {loading && <p className="rc-loading-state" role="status">Đang đối chiếu điều kiện sinh viên…</p>}
    {run && !command.retryable && <div className="nomination-filter-bar"><label>Kết quả đối chiếu<select disabled={disabled} value={filter} onChange={e => { setSelected([]); setPage(0); setFilter(e.target.value); }}>
      <option value="MATCH">Đạt tiêu chí</option><option value="MISSING_DATA">Thiếu dữ liệu</option><option value="NOT_MATCH">Chưa phù hợp</option>
    </select></label><button className="secondary" disabled={disabled} onClick={() => setRevision(n => n + 1)}>Tải lại kết quả đối chiếu</button></div>}
    {context && <>
      <div className="nomination-slot-bar">
        <p className="nomination-slot-summary">Còn {slots} chỗ có thể đề xuất · Đã chọn {selected.length} sinh viên trên trang này.</p>
      </div>
      {!rows.length && <p>Chưa có sinh viên trong nhóm kết quả này. Kiểm tra hồ sơ, thời gian thực tập và xác nhận điều kiện của trường.</p>}
      {!!rows.length && <>
        <div className="nomination-selection-toolbar">
          <div className="button-row">
            <button className="secondary" disabled={disabled || !available.length || available.length > slots}
              onClick={() => setSelected(available.map(row => row.match.studentUserId))}>Chọn tất cả đủ điều kiện trên trang</button>
            <button className="secondary" disabled={disabled || !selected.length} onClick={() => setSelected([])}>Bỏ chọn</button>
          </div>
          <button className="nomination-submit-btn" disabled={disabled || !selected.length || selected.length > slots} onClick={async () => {
            const labels = rows.filter(row => selected.includes(row.match.studentUserId)).map(row => {
              const name = identities[row.match.studentUserId]?.displayName;
              return name ? `${name}${row.studentCode ? ` (${row.studentCode})` : ''}` : (row.studentCode || 'ứng viên');
            });
            if (!await askConfirm({ title: 'Tạo đề xuất sinh viên?', confirmLabel: 'Tạo đề xuất', message: `Xác nhận đề xuất ${selected.length} sinh viên: ${labels.join(', ')}?` })) return;
            void command.submit(`/matching/runs/${context.run.matchRunId}/nominate`, 'POST', { studentUserIds: selected,
              expectedDemandVersion: context.demandVersion, expectedCriteriaVersion: context.criteriaVersion,
              expectedCooperationVersion: context.cooperationVersion, expectedBatchVersion: context.batchVersion });
          }}>Tạo đề xuất cho sinh viên đã chọn</button>
        </div>
        <div className="proposal-table"><table><caption>Sinh viên theo kết quả đối chiếu</caption><thead><tr><th>Chọn</th><th>Sinh viên</th><th>Ngành</th><th>Điểm ưu tiên</th><th>Điều kiện</th></tr></thead>
          <tbody>{rows.map((row, index) => {
            const identity = identities[row.match.studentUserId];
            return <tr key={row.match.studentUserId} className={selected.includes(row.match.studentUserId) ? 'table-row-selected' : undefined}><td><input type="checkbox" aria-label={`Chọn ${row.studentCode || row.match.studentUserId}`}
              checked={selected.includes(row.match.studentUserId)} disabled={disabled || !selectable(row) || !selected.includes(row.match.studentUserId) && selected.length >= slots}
              onChange={e => setSelected(old => e.target.checked ? [...old, row.match.studentUserId] : old.filter(id => id !== row.match.studentUserId))} /></td>
              <th scope="row">
                {identity?.displayName ? (
                  <div className="proposal-student-ident">
                    <strong>{identity.displayName}</strong>
                    {row.studentCode && <small>MSSV {row.studentCode}</small>}
                  </div>
                ) : (
                  <span>{row.studentCode || `Ứng viên ${index + 1}`}</span>
                )}
              </th><td>{row.majorCode || 'Chưa cập nhật'}</td><td><span className="nomination-score-pill">{row.match.fitScore ?? '—'}</span></td>
              <td><span className={'nomination-status-pill ' + (selectable(row) ? 'is-ready' : 'is-blocked')}>{selectable(row) ? 'Đủ điều kiện đề xuất' : reasons[row.reasonCode || ''] || 'Chưa đủ điều kiện'}</span>
                {row.reasonCode === 'SCHOOL_APPROVAL_REQUIRED' && row.match.overallStatus === 'MATCH' && <button className="secondary nomination-approve-btn" disabled={disabled}
                  onClick={() => {setApproving(row);setDecisionRef('');}}>Xác nhận điều kiện cho {row.studentCode || row.match.studentUserId}</button>}
                {row.match.details.some(d => d.type === 'REQUIRED' && d.status !== 'PASS') && <small>{row.match.details.filter(d => d.type === 'REQUIRED' && d.status !== 'PASS').map(d => `${formatCriterionLabel(d.criterionCode)}: ${d.status === 'UNKNOWN' ? 'thiếu dữ liệu' : 'chưa đạt'}`).join('; ')}</small>}</td></tr>;
          })}</tbody>
        </table></div>
      </>}
      {context.candidates.totalPages > 1 && <PagerNav page={page} totalPages={context.candidates.totalPages} busy={disabled} prevLabel="Trang sinh viên trước" nextLabel="Trang sinh viên sau" ariaLabel="Phân trang ứng viên" onChange={setPage} />}
    </>}
    {approving && <form className="nomination-eligibility-drawer" onSubmit={e => {e.preventDefault();void command.submit('/school/student-eligibilities/decisions', 'POST', {
      studentUserId: approving.match.studentUserId, termId: demand.data.termId, expectedVersion: approving.eligibilityVersion,
      schoolApproved: true, reasonCode: 'SCHOOL_ELIGIBLE', decisionRef,
    });}}><fieldset disabled={disabled}><legend>Xác nhận điều kiện thực tập của {identities[approving.match.studentUserId]?.displayName || approving.studentCode || 'sinh viên đã chọn'}</legend>
      <p>Cán bộ được phân quyền xác nhận sau khi đã kiểm tra điều kiện học vụ. Thao tác này không gửi lời mời hoặc cấp chia sẻ hồ sơ.</p>
      <label>Căn cứ xác nhận điều kiện<input required maxLength={120} value={decisionRef} onChange={e => setDecisionRef(e.target.value)} /></label>
      <div className="button-row"><button>Xác nhận sinh viên đủ điều kiện</button><button className="secondary" type="button" onClick={() => setApproving(null)}>Đóng xác nhận điều kiện</button></div>
    </fieldset></form>}
  </section>;
}
