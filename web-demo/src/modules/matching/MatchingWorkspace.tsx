import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError, api, type Grant } from '../../api';
import { EmptyState, Icon, Notice } from '../../components';
import { formatDateDMY } from '../../ui/dateFormat';
import type { ContractPage } from '../student/types';
import { formatCriterionLabel } from '../cooperation/proposals/useStudentIdentities';
import { useSchoolTermNames } from '../cooperation/workflow/useSchoolTermNames';
import './matching.css';

type Demand = {
  demandId: string; state: 'DRAFT' | 'READY'; version: number; criteriaVersion: number;
  validationState: string; softDeletedAt: string | null; data: { title: string; quantity: number; termId: string; schoolOrgId: string;
    required: { skills: { skillCode: string; minLevel: number | null; minYearsPractice: number | null }[];
      dateRange: { startDate: string; endDate: string } } };
};
type Cooperation = { cooperationId: string; demandId: string; termId: string; state: string; version: number };
type DemandScope = { demand: Demand; cooperation: Cooperation };
type MatchRun = { matchRunId: string; demandId: string; demandVersion: number; criteriaVersion: number;
  criteriaHash: string; datasetSnapshotId: string; profileSnapshotVersion: string; ruleVersion: string;
  taxonomyVersion: string; scoringVersion: string; state: string };
type Criterion = { criterionCode: string; type: string; kind: string; status: 'PASS' | 'FAIL' | 'UNKNOWN'; reasonCode: string };
type MatchResult = { studentUserId: string; profileSnapshotId: string; profileVersion: number;
  relevantHash: string; overallStatus: string; fitScore: number | null; details: Criterion[] };
type ResultPage = { content: MatchResult[]; page: number; size: number; totalElements: number; totalPages: number };
type Details = { run: MatchRun; results: ResultPage };
type Evaluated = { audience: string; summary: { run: MatchRun; matched: number; missingData: number; notMatched: number } };
type Projection = { studentUserId: string; allowedFields: string[]; data: Record<string, unknown> };
type Capacity = { quantity: number; placedCount: number; activeReservations: number; openForNomination: number; batchVersion: number | null };
type CandidateEligibility = { selectable: boolean; reasonCode: string | null };
type NominationCandidatesResponse = { candidates: { content: { selectable: boolean; reasonCode: string | null; match: { studentUserId: string } }[] } };
type SelectedRun = { evaluation: Evaluated; cooperationVersion: number; termId: string };

const outcomeLabels: Record<string, string> = { MATCH: 'Đáp ứng', MISSING_DATA: 'Thiếu dữ liệu', NOT_MATCH: 'Không đáp ứng' };
const reasonLabels: Record<string, string> = {
  SATISFIED: 'Đạt tiêu chí', REQUIRED_NOT_SATISFIED: 'Chưa đạt tiêu chí bắt buộc',
  DATA_NOT_DECLARED: 'Chưa có thông tin để đánh giá',
};
const eligibilityReasons: Record<string, string> = {
  PROPOSAL_ALREADY_EXISTS: 'Đã có trong đề xuất',
  SCHOOL_APPROVAL_REQUIRED: 'Chưa xác nhận điều kiện',
  TERM_INVALID: 'Kỳ chưa hoạt động',
  ACCESS_DENIED: 'Không còn hiệu lực',
};
const fieldNames = ['studentCode', 'displayName', 'majorCode', 'skills'];

function explain(error: unknown, fallback: string) {
  if (!(error instanceof ApiError)) return fallback;
  const messages: Record<string, string> = {
    CRITERIA_STALE: 'Nhu cầu hoặc tiêu chí vừa thay đổi. Tải lại danh sách rồi chạy Matching lại.',
    MATCH_RUN_STALE: 'Kết quả đã cũ. Hãy chạy Matching lại trước khi đề cử.',
    CAPACITY_EXCEEDED: 'Số sinh viên chọn vượt chỉ tiêu còn trống. Cập nhật lựa chọn theo sức chứa mới.',
    CANDIDATE_NOT_MATCH: 'Chỉ được đề cử sinh viên thuộc nhóm Đáp ứng.',
    PROFILE_STALE: 'Hồ sơ sinh viên đã thay đổi sau lần Matching. Chạy lại để cập nhật kết quả.',
    SCHOOL_APPROVAL_REQUIRED: 'Sinh viên chưa được trường xác nhận đủ điều kiện thực tập. Chuyển sang "Đề xuất sinh viên" để xác nhận điều kiện trước khi đề cử.',
    PROPOSAL_ALREADY_EXISTS: 'Sinh viên đã có trong danh sách đề xuất của hợp tác này.',
    TERM_INVALID: 'Kỳ thực tập chưa ở trạng thái hoạt động hợp lệ.',
    ACCESS_DENIED: 'Tài khoản hiện tại không có quyền trong phạm vi trường/kỳ này.',
  };
  return messages[error.errorCode || ''] || error.message || fallback;
}

function readProjection(studentId: string, termId: string, signal: AbortSignal) {
  const params = new URLSearchParams({ termId, purpose: 'SCHOOL_REVIEW' });
  fieldNames.forEach(field => params.append('fields', field));
  return api<Projection>(`/students/${encodeURIComponent(studentId)}/profile?${params}`, { signal });
}

export function MatchingWorkspace({ organizationId, grants, onOpenProposals, termId = '' }: {
  organizationId: string; grants: Grant[]; onOpenProposals?: (cooperationId: string) => void; termId?: string;
}) {
  const canNominate = grants.some(g => g.role === 'SCHOOL_STAFF' && g.organizationId === organizationId
    && g.permissions.includes('t5:proposal:manage') && g.permissions.includes('matching:school:nominate'));
  const termNames = useSchoolTermNames(organizationId, true);
  const [scopes, setScopes] = useState<DemandScope[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [run, setRun] = useState<SelectedRun | null>(null);
  const [status, setStatus] = useState('MATCH');
  const [page, setPage] = useState(0);
  const [details, setDetails] = useState<Details | null>(null);
  const [profiles, setProfiles] = useState<Record<string, Projection | null>>({});
  const [eligibilityMap, setEligibilityMap] = useState<Record<string, CandidateEligibility>>({});
  const [capacity, setCapacity] = useState<Capacity | null>(null);
  const [selectedStudents, setSelectedStudents] = useState<string[]>([]);
  const [handoffCoopId, setHandoffCoopId] = useState('');
  const [handoffReason, setHandoffReason] = useState<'nominated' | 'eligibility' | null>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);
  const [capacityRevision, setCapacityRevision] = useState(0);
  const pendingNomination = useRef<{ signature: string; key: string } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoaded(false); setError(''); setNotice(''); setScopes([]); setSelectedId(''); setRun(null); setDetails(null); setEligibilityMap({}); setCapacity(null); setSelectedStudents([]); setHandoffReason(null);
    void (async () => {
      try {
        const rows: Cooperation[] = [];
        let index = 0, pages = 1;
        while (index < pages) {
          const result = await api<ContractPage<Cooperation>>(`/school/cooperations?state=APPROVED&page=${index}&size=100${termId ? '&' + new URLSearchParams({ termId }) : ''}`, {
            signal: controller.signal, headers: { 'X-Organization-Id': organizationId },
          });
          rows.push(...result.content.filter(row => !termId || row.termId === termId)); pages = result.totalPages; index++;
        }
        const resolved = await Promise.all(rows.map(async cooperation => {
          const detail = await api<{ demand: Demand; cooperation: Cooperation }>(`/school/cooperations/${cooperation.cooperationId}`, {
            signal: controller.signal, headers: { 'X-Organization-Id': organizationId },
          });
          return detail;
        }));
        if (controller.signal.aborted) return;
        const eligible = resolved.filter(item => item.cooperation.state === 'APPROVED'
          && item.demand.validationState === 'VALID' && item.demand.softDeletedAt == null)
          .sort((a, b) => a.demand.data.title.localeCompare(b.demand.data.title)) as DemandScope[];
        setScopes(eligible);
        setSelectedId(eligible[0]?.cooperation.cooperationId || '');
      } catch (failure) {
        if (!controller.signal.aborted) setError(explain(failure, 'Không tải được nhu cầu đã duyệt trong phạm vi trường.'));
      } finally { if (!controller.signal.aborted) setLoaded(true); }
    })();
    return () => controller.abort();
  }, [organizationId, termId, refresh]);

  const current = useMemo(() => scopes.find(item => item.cooperation.cooperationId === selectedId) || null, [scopes, selectedId]);

  useEffect(() => {
    if (!run || !current) return;
    const controller = new AbortController();
    setDetails(null); setError(''); setSelectedStudents([]);
    const query = new URLSearchParams({ overallStatus: status, page: String(page), size: '25' });
    void api<Details>(`/matching/runs/${run.evaluation.summary.run.matchRunId}/details?${query}`, { signal: controller.signal })
      .then(value => { if (!controller.signal.aborted) setDetails(value); })
      .catch(failure => { if (!controller.signal.aborted) setError(explain(failure, 'Không tải được trang kết quả.')); });
    return () => controller.abort();
  }, [run, current, status, page]);

  useEffect(() => {
    if (!run || !current || !details) return;
    const controller = new AbortController();
    const pending = details.results.content.filter(row => profiles[row.studentUserId] === undefined);
    if (!pending.length) return;
    void Promise.all(pending.map(async row => {
      try { return [row.studentUserId, await readProjection(row.studentUserId, run.termId, controller.signal)] as const; }
      catch { return [row.studentUserId, null] as const; }
    })).then(entries => { if (!controller.signal.aborted) setProfiles(previous => ({ ...previous, ...Object.fromEntries(entries) })); });
    return () => controller.abort();
  }, [run, current, details, profiles]);

  useEffect(() => {
    if (!run || !current || !canNominate) { setCapacity(null); setEligibilityMap({}); return; }
    const controller = new AbortController();
    void api<Capacity>(`/school/cooperations/${current.cooperation.cooperationId}/capacity`, {
      signal: controller.signal, headers: { 'X-Organization-Id': organizationId },
    }).then(setCapacity).catch(failure => { if (!controller.signal.aborted) setError(explain(failure, 'Không tải được chỉ tiêu và sức chứa.')); });
    if (status === 'MATCH') {
      const query = new URLSearchParams({
        matchRunId: run.evaluation.summary.run.matchRunId,
        overallStatus: 'MATCH',
        page: String(page),
        size: '25',
      });
      void api<NominationCandidatesResponse>(`/school/cooperations/${current.cooperation.cooperationId}/nomination-candidates?${query}`, {
        signal: controller.signal, headers: { 'X-Organization-Id': organizationId },
      }).then(res => {
        if (controller.signal.aborted) return;
        const map: Record<string, CandidateEligibility> = {};
        res.candidates?.content?.forEach(row => {
          map[row.match.studentUserId] = { selectable: row.selectable, reasonCode: row.reasonCode };
        });
        setEligibilityMap(map);
      }).catch(() => { /* Keep candidate details usable when eligibility enrichment is unavailable in mocked views. */ });
    } else {
      setEligibilityMap({});
    }
    return () => controller.abort();
  }, [run, current, canNominate, organizationId, refresh, capacityRevision, status, page]);

  const evaluate = async () => {
    if (!current) return;
    setBusy(true); setError(''); setNotice(''); setHandoffReason(null); setRun(null); setDetails(null); setProfiles({}); setEligibilityMap({}); setCapacity(null);
    try {
      const response = await api<Evaluated>('/matching/evaluate', {
        method: 'POST', headers: { 'Idempotency-Key': crypto.randomUUID() },
        body: JSON.stringify({ demandId: current.demand.demandId, expectedDemandVersion: current.demand.version,
          expectedCriteriaVersion: current.demand.criteriaVersion }),
      });
      if (response.audience !== 'SCHOOL') throw new Error('Phản hồi không đúng phạm vi nhà trường.');
      setRun({ evaluation: response, cooperationVersion: current.cooperation.version, termId: current.cooperation.termId });
      setStatus('MATCH'); setPage(0);
    } catch (failure) { setError(explain(failure, 'Không thể chạy Matching. Tải lại nhu cầu và thử lại.')); }
    finally { setBusy(false); }
  };

  const nominate = async () => {
    if (!run || !current || !capacity || selectedStudents.length === 0) return;
    if (capacity.openForNomination < selectedStudents.length) {
      setError('Số sinh viên chọn vượt chỉ tiêu còn trống.'); return;
    }
    setBusy(true); setError(''); setNotice(''); setHandoffReason(null);
    const coopId = current.cooperation.cooperationId;
    try {
      const body = { studentUserIds: selectedStudents,
          expectedDemandVersion: current.demand.version,
          expectedCriteriaVersion: current.demand.criteriaVersion,
          expectedCooperationVersion: run.cooperationVersion,
          expectedBatchVersion: capacity.batchVersion };
      const signature = run.evaluation.summary.run.matchRunId + ':' + JSON.stringify(body);
      if (!pendingNomination.current || pendingNomination.current.signature !== signature)
        pendingNomination.current = { signature, key: crypto.randomUUID() };
      const result = await api<{ batchVersion: number; items: unknown[] }>('/matching/runs/' + run.evaluation.summary.run.matchRunId + '/nominate', {
        method: 'POST', headers: { 'Idempotency-Key': pendingNomination.current.key },
        body: JSON.stringify(body),
      });
      pendingNomination.current = null;
      setSelectedStudents([]);
      setHandoffCoopId(coopId);
      setHandoffReason('nominated');
      setNotice(`Đã đề cử ${result.items.length} sinh viên. Chuyển sang Đề xuất sinh viên để gửi lời mời và chia sẻ hồ sơ.`);
      setCapacityRevision(value => value + 1);
    } catch (failure) {
      if (failure instanceof ApiError && failure.errorCode === 'SCHOOL_APPROVAL_REQUIRED') {
        setHandoffCoopId(coopId);
        setHandoffReason('eligibility');
      }
      setError(explain(failure, 'Đề cử chưa thành công. Tải lại sức chứa và kiểm tra kết quả hiện tại.'));
    }
    finally { setBusy(false); }
  };

  const toggleStudent = (studentId: string) => setSelectedStudents(previous => {
    if (previous.includes(studentId)) return previous.filter(id => id !== studentId);
    if (capacity && previous.length >= capacity.openForNomination) return previous;
    return [...previous, studentId];
  });
  const counts = run?.evaluation.summary;

  const summary = <div className="matching-counts" aria-label="Tổng hợp kết quả cho nhà trường">
      <button type="button" disabled={!counts} className={status === 'MATCH' ? 'selected' : ''} onClick={() => { if (counts) { setStatus('MATCH'); setPage(0); } }}>
        <span className="kpi-card-top">
          <span className="matching-count-meta">Đáp ứng</span>
          <span className="school-stat-icon-tile" aria-hidden="true"><Icon name="check-circle" size={18} /></span>
        </span>
        <span className="kpi-card-bottom">
          <strong>{counts ? counts.matched : '—'}</strong>
          <span className="kpi-card-hint" aria-hidden="true">{counts ? 'Xem danh sách →' : 'Chưa đối chiếu'}</span>
        </span>
      </button>
      <button type="button" disabled={!counts} className={status === 'MISSING_DATA' ? 'selected' : ''} onClick={() => { if (counts) { setStatus('MISSING_DATA'); setPage(0); } }}>
        <span className="kpi-card-top">
          <span className="matching-count-meta">Thiếu dữ liệu</span>
          <span className="school-stat-icon-tile" aria-hidden="true"><Icon name="alert" size={18} /></span>
        </span>
        <span className="kpi-card-bottom">
          <strong>{counts ? counts.missingData : '—'}</strong>
          <span className="kpi-card-hint" aria-hidden="true">{counts ? 'Xem danh sách →' : 'Chưa đối chiếu'}</span>
        </span>
      </button>
      <button type="button" disabled={!counts} className={status === 'NOT_MATCH' ? 'selected' : ''} onClick={() => { if (counts) { setStatus('NOT_MATCH'); setPage(0); } }}>
        <span className="kpi-card-top">
          <span className="matching-count-meta">Không đáp ứng</span>
          <span className="school-stat-icon-tile" aria-hidden="true"><Icon name="close" size={18} /></span>
        </span>
        <span className="kpi-card-bottom">
          <strong>{counts ? counts.notMatched : '—'}</strong>
          <span className="kpi-card-hint" aria-hidden="true">{counts ? 'Xem danh sách →' : 'Chưa đối chiếu'}</span>
        </span>
      </button>
    </div>;
  return <section className="panel matching-workspace" aria-busy={busy}>
    <div className="school-section-card">
      <div className="school-section-head">
        <div className="school-section-title">
          <span className="school-section-icon is-shortcuts" aria-hidden="true"><Icon name="activity" size={18} /></span>
          <h3>Phạm vi & kết quả đối chiếu</h3>
        </div>
        {current && <span className="workflow-count-pill">{current.demand.data.quantity} vị trí</span>}
      </div>

      <Notice message={error || notice} tone={error ? 'error' : 'success'} />
      {handoffReason && handoffCoopId && onOpenProposals && (
        <div className="matching-handoff-banner">
          <span>{handoffReason === 'nominated'
            ? 'Bước tiếp theo: gửi lời mời thực tập và chia sẻ hồ sơ cho sinh viên vừa đề cử.'
            : 'Sinh viên chưa được nhà trường xác nhận đủ điều kiện thực tập.'}</span>
          <button type="button" className="secondary" onClick={() => onOpenProposals(handoffCoopId)}>
            {handoffReason === 'nominated' ? 'Mở Đề xuất sinh viên →' : 'Xác nhận điều kiện bên Đề xuất sinh viên →'}
          </button>
        </div>
      )}
      {!loaded ? <p role="status">Đang tải nhu cầu đã duyệt…</p> : scopes.length === 0
        ? <EmptyState icon="search" title="Chưa có nhu cầu phù hợp để ghép" description="Cần có hợp tác đã được duyệt và nhu cầu còn hiệu lực trong phạm vi trường." />
        : <>
          <div className="matching-controls">
            <label>Nhu cầu đã duyệt<select value={selectedId} onChange={event => { setSelectedId(event.target.value); setRun(null); setDetails(null); setProfiles({}); setEligibilityMap({}); setCapacity(null); setSelectedStudents([]); setHandoffReason(null); setError(''); setNotice(''); }} disabled={busy}>
              {scopes.map(item => <option key={item.cooperation.cooperationId} value={item.cooperation.cooperationId}>{item.demand.data.title} · {item.demand.data.quantity} vị trí</option>)}
            </select></label>
            <button disabled={busy || !current} onClick={() => void evaluate()}>{busy ? 'Đang xử lý…' : run ? 'Chạy lại Matching' : 'Chạy Matching'}</button>
            <button type="button" className="secondary" disabled={busy} onClick={() => setRefresh(value => value + 1)}>Tải lại nhu cầu</button>
          </div>
        <div className="matching-demand-summary" hidden={!current}>
          {current && <>
            <span>Kỳ {termNames[current.cooperation.termId] || 'hiện tại'}</span>
            <span>Chỉ tiêu {current.demand.data.quantity} vị trí</span>
            <span>Kỹ năng: {current.demand.data.required.skills.map(skill => skill.skillCode).join(', ') || 'Không giới hạn'}</span>
            <span>Thời gian: {formatDateDMY(current.demand.data.required.dateRange.startDate)} – {formatDateDMY(current.demand.data.required.dateRange.endDate)}</span>
          </>}
        </div>
        </>}

      {summary}
      <div className="portal-tabs matching-tabs" role="tablist" aria-label="Nhóm kết quả đối chiếu">
        {([
          ['MATCH', counts ? `Đáp ứng (${counts.matched})` : 'Đáp ứng'],
          ['MISSING_DATA', counts ? `Thiếu dữ liệu (${counts.missingData})` : 'Thiếu dữ liệu'],
          ['NOT_MATCH', counts ? `Không đáp ứng (${counts.notMatched})` : 'Không đáp ứng'],
        ] as const).map(([key, label]) =>
          <button key={key} type="button" role="tab" className={status === key ? 'active' : undefined} aria-selected={status === key} onClick={() => { setStatus(key); setPage(0); }}>{label}</button>
        )}
      </div>

      {!run ? (loaded && scopes.length > 0 && <div className="table-scroll matching-table-wrap"><table>
        <thead><tr><th>Ứng viên</th><th>Ngành & Kỹ năng</th><th>Tiêu chí đối chiếu</th><th>Điểm phù hợp</th></tr></thead>
        <tbody><tr><td colSpan={4}><EmptyState icon="search" title="Sẵn sàng đối chiếu hồ sơ" description='Chọn nhu cầu ở trên và bấm "Chạy Matching" để phân loại sinh viên theo 3 nhóm.' /></td></tr></tbody>
      </table></div>)
      : counts && <>
        <div className="matching-run-meta">
          <div>
            <h3>{outcomeLabels[status]}</h3>
            {canNominate && status === 'MATCH' && capacity && <p className="matching-quota-hint">Còn {capacity.openForNomination} / {capacity.quantity} vị trí · Đã xếp {capacity.placedCount} · Đang giữ chỗ {capacity.activeReservations}.</p>}
          </div>
          <div className="matching-run-right">
            <p>Kết quả phân loại theo tiêu chí nhu cầu</p>
            {canNominate && status === 'MATCH' && <button onClick={() => void nominate()} disabled={busy || !selectedStudents.length || !capacity || selectedStudents.length > capacity.openForNomination}>
              Đề cử {selectedStudents.length ? `(${selectedStudents.length})` : 'sinh viên đã chọn'}
            </button>}
          </div>
        </div>
        {details ? details.results.content.length === 0 ? <EmptyState title="Không có sinh viên trong nhóm này" description="Hãy xem nhóm kết quả khác hoặc kiểm tra tiêu chí của nhu cầu." />
          : <div className="table-scroll matching-table-wrap"><table>
            <thead><tr><th>Ứng viên</th><th>Ngành & Kỹ năng</th><th>Tiêu chí đối chiếu</th><th>Điểm ưu tiên</th></tr></thead>
            <tbody>{details.results.content.map(result => {
              const checked = selectedStudents.includes(result.studentUserId);
              const eligibility = eligibilityMap[result.studentUserId];
              const blockedByEligibility = eligibility !== undefined && !eligibility.selectable;
              const blockedByCapacity = !!capacity && (capacity.openForNomination <= 0 || (!checked && selectedStudents.length >= capacity.openForNomination));
              return <Candidate key={result.studentUserId} result={result}
                profile={profiles[result.studentUserId]} selectable={canNominate && status === 'MATCH'}
                selectDisabled={busy || blockedByEligibility || blockedByCapacity}
                eligibility={eligibility}
                checked={checked}
                onToggle={() => toggleStudent(result.studentUserId)} />;
            })}</tbody>
          </table></div>
          : <p role="status">Đang tải kết quả…</p>}
        <div className="matching-pagination"><span>{details?.results.totalElements ?? 0} kết quả · Trang {(details?.results.page ?? page) + 1}/{Math.max(1, details?.results.totalPages ?? 1)}</span>
          <button className="secondary" disabled={page === 0 || busy} onClick={() => setPage(value => value - 1)}>Trước</button>
          <button className="secondary" disabled={!details || page + 1 >= details.results.totalPages || busy} onClick={() => setPage(value => value + 1)}>Sau</button></div>
      </>}
    </div>
  </section>;
}

function Candidate({ result, profile, selectable, selectDisabled, eligibility, checked, onToggle }: {
  result: MatchResult; profile?: Projection | null;
  selectable: boolean; selectDisabled: boolean; eligibility?: CandidateEligibility; checked: boolean; onToggle: () => void;
}) {
  const name = profile?.data.displayName;
  const code = profile?.data.studentCode;
  const major = profile?.data.majorCode;
  const skills = Array.isArray(profile?.data.skills) ? profile.data.skills as { kind?: string; skill?: { skillCode?: string; name?: string } }[] : [];
  const eligibilityNote = eligibility && !eligibility.selectable
    ? (eligibilityReasons[eligibility.reasonCode || ''] || 'Chưa đủ điều kiện đề cử')
    : null;
  return <tr className={`matching-candidate${checked ? ' table-row-selected' : ''}`}>
    <td><div className="matching-candidate-cell">
      {selectable && <label className="matching-select"><input type="checkbox" checked={checked} disabled={selectDisabled} onChange={onToggle} aria-label={`Chọn sinh viên ${typeof code === 'string' ? code : ''}`} /></label>}
      {typeof name === 'string' && <span className="matching-avatar" aria-hidden="true">{name.trim().slice(0, 2).toLocaleUpperCase('vi')}</span>}
      <div className="matching-candidate-ident"><strong>{typeof name === 'string' ? name : profile === null ? 'Thông tin định danh không được cấp' : 'Đang tải hồ sơ…'}</strong>
        <small>{typeof code === 'string' ? `MSSV ${code}` : ''}{typeof major === 'string' ? ` · Ngành ${major}` : ''}</small>
        {eligibilityNote && <span className={'matching-eligibility-tag ' + (eligibility?.reasonCode === 'PROPOSAL_ALREADY_EXISTS' ? 'is-existing' : 'is-pending')}>{eligibilityNote}</span>}</div>
    </div></td>
    <td><span className="matching-skills">{profile?.allowedFields.includes('skills') ? (skills.map(item => item.skill?.skillCode || item.skill?.name).filter(Boolean).join(', ') || 'Chưa khai báo kỹ năng') : 'Theo quyền chia sẻ'}</span></td>
    <td><ul className="matching-criteria matching-criteria-inline">{result.details.map((criterion, index) => <li key={`${criterion.criterionCode}-${index}`} title={reasonLabels[criterion.reasonCode] || criterion.reasonCode}>
      <span className={`matching-criterion-status ${criterion.status.toLowerCase()}`}>{criterion.status === 'PASS' ? 'Đạt' : criterion.status === 'FAIL' ? 'Không đạt' : 'Chưa rõ'}</span>
      <span>{criterion.kind === 'MAJOR' ? 'Ngành học' : criterion.kind === 'AVAILABILITY' ? 'Lịch thực tập' : formatCriterionLabel(criterion.criterionCode)}</span>
    </li>)}</ul></td>
    <td>{result.fitScore !== null ? <span className="matching-score">{result.fitScore.toFixed(2)}</span> : <span className="muted">—</span>}</td>
  </tr>;
}
