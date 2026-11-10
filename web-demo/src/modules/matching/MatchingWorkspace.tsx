import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError, api, type Grant } from '../../api';
import { EmptyState, Notice } from '../../components';
import type { ContractPage } from '../student/types';
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
type SelectedRun = { evaluation: Evaluated; cooperationVersion: number; termId: string };

const outcomeLabels: Record<string, string> = { MATCH: 'Đáp ứng', MISSING_DATA: 'Thiếu dữ liệu', NOT_MATCH: 'Không đáp ứng' };
const reasonLabels: Record<string, string> = {
  SATISFIED: 'Đạt tiêu chí', REQUIRED_NOT_SATISFIED: 'Chưa đạt tiêu chí bắt buộc',
  DATA_NOT_DECLARED: 'Chưa có thông tin để đánh giá',
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
    ACCESS_DENIED: 'Tài khoản hiện tại không có quyền trong phạm vi trường/kỳ này.',
  };
  return messages[error.errorCode || ''] || error.message || fallback;
}

function readProjection(studentId: string, termId: string, signal: AbortSignal) {
  const params = new URLSearchParams({ termId, purpose: 'SCHOOL_REVIEW' });
  fieldNames.forEach(field => params.append('fields', field));
  return api<Projection>(`/students/${encodeURIComponent(studentId)}/profile?${params}`, { signal });
}

export function MatchingWorkspace({ organizationId, grants }: { organizationId: string; grants: Grant[] }) {
  const canNominate = grants.some(g => g.role === 'SCHOOL_STAFF' && g.organizationId === organizationId
    && g.permissions.includes('t5:proposal:manage') && g.permissions.includes('matching:school:nominate'));
  const [scopes, setScopes] = useState<DemandScope[]>([]);
  const [selectedId, setSelectedId] = useState('');
  const [loaded, setLoaded] = useState(false);
  const [run, setRun] = useState<SelectedRun | null>(null);
  const [status, setStatus] = useState('MATCH');
  const [page, setPage] = useState(0);
  const [details, setDetails] = useState<Details | null>(null);
  const [profiles, setProfiles] = useState<Record<string, Projection | null>>({});
  const [capacity, setCapacity] = useState<Capacity | null>(null);
  const [selectedStudents, setSelectedStudents] = useState<string[]>([]);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [refresh, setRefresh] = useState(0);
  const pendingNomination = useRef<{ signature: string; key: string } | null>(null);

  useEffect(() => {
    const controller = new AbortController();
    setLoaded(false); setError(''); setScopes([]); setSelectedId(''); setRun(null); setDetails(null); setCapacity(null);
    void (async () => {
      try {
        const rows: Cooperation[] = [];
        let index = 0, pages = 1;
        while (index < pages) {
          const result = await api<ContractPage<Cooperation>>(`/school/cooperations?state=APPROVED&page=${index}&size=100`, {
            signal: controller.signal, headers: { 'X-Organization-Id': organizationId },
          });
          rows.push(...result.content); pages = result.totalPages; index++;
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
  }, [organizationId, refresh]);

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
    if (!run || !current || !canNominate) { setCapacity(null); return; }
    const controller = new AbortController();
    void api<Capacity>(`/school/cooperations/${current.cooperation.cooperationId}/capacity`, {
      signal: controller.signal, headers: { 'X-Organization-Id': organizationId },
    }).then(setCapacity).catch(failure => { if (!controller.signal.aborted) setError(explain(failure, 'Không tải được chỉ tiêu và sức chứa.')); });
    return () => controller.abort();
  }, [run, current, canNominate, organizationId, refresh]);

  const evaluate = async () => {
    if (!current) return;
    setBusy(true); setError(''); setNotice(''); setRun(null); setDetails(null); setProfiles({}); setCapacity(null);
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
    setBusy(true); setError(''); setNotice('');
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
      setNotice(`Đã đề cử ${result.items.length} sinh viên. Phiên bản batch hiện tại: ${result.batchVersion}.`);
      setRefresh(value => value + 1);
    } catch (failure) { setError(explain(failure, 'Đề cử chưa thành công. Tải lại sức chứa và kiểm tra kết quả hiện tại.')); }
    finally { setBusy(false); }
  };

  const toggleStudent = (studentId: string) => setSelectedStudents(previous => previous.includes(studentId)
    ? previous.filter(id => id !== studentId) : [...previous, studentId]);
  const counts = run?.evaluation.summary;

  return <section className="panel matching-workspace" aria-busy={busy}>
    <div className="matching-heading"><div><p className="section-kicker">T4 · MATCHING</p><h2>Kết quả ghép sinh viên</h2>
      <p className="section-description">Dữ liệu nhu cầu và hồ sơ được đọc từ T5, T2; kết quả được tạo bởi Matching API.</p></div>
      <button className="secondary" disabled={busy} onClick={() => setRefresh(value => value + 1)}>Tải lại nhu cầu</button></div>
    <Notice message={error || notice} tone={error ? 'error' : 'success'} />
    {!loaded ? <p role="status">Đang tải nhu cầu đã duyệt…</p> : scopes.length === 0
      ? <EmptyState icon="search" title="Chưa có nhu cầu phù hợp để ghép" description="Cần có hợp tác đã được duyệt và nhu cầu còn hiệu lực trong phạm vi trường." />
      : <>
        <div className="matching-controls">
          <label>Nhu cầu đã duyệt<select value={selectedId} onChange={event => { setSelectedId(event.target.value); setRun(null); setDetails(null); setProfiles({}); setCapacity(null); setSelectedStudents([]); setError(''); setNotice(''); }} disabled={busy}>
            {scopes.map(item => <option key={item.cooperation.cooperationId} value={item.cooperation.cooperationId}>{item.demand.data.title} · {item.demand.data.quantity} vị trí</option>)}
          </select></label>
          <button disabled={busy || !current} onClick={() => void evaluate()}>{busy ? 'Đang xử lý…' : run ? 'Chạy lại Matching' : 'Chạy Matching'}</button>
        </div>
        {current && <div className="matching-demand-summary"><span>Kỳ: <code>{current.cooperation.termId}</code></span><span>Phiên bản nhu cầu: {current.demand.version}</span><span>Tiêu chí: {current.demand.criteriaVersion}</span>
          <span>Kỹ năng bắt buộc: {current.demand.data.required.skills.map(skill => skill.skillCode).join(', ') || 'Không có'}</span>
          <span>Lịch: {current.demand.data.required.dateRange.startDate} – {current.demand.data.required.dateRange.endDate}</span></div>}
      </>}

    {run && counts && <>
      <div className="matching-run-meta"><h3>Kết quả lần chạy</h3><p>Rule <code>{counts.run.ruleVersion}</code> · Taxonomy <code>{counts.run.taxonomyVersion}</code> · Snapshot <code>{counts.run.profileSnapshotVersion.slice(0, 12)}</code> · Run <code>{counts.run.matchRunId}</code></p></div>
      <div className="matching-counts" aria-label="Tổng hợp kết quả cho nhà trường">
        <button className={status === 'MATCH' ? 'selected' : ''} onClick={() => { setStatus('MATCH'); setPage(0); }}>Đáp ứng <strong>{counts.matched}</strong></button>
        <button className={status === 'MISSING_DATA' ? 'selected' : ''} onClick={() => { setStatus('MISSING_DATA'); setPage(0); }}>Thiếu dữ liệu <strong>{counts.missingData}</strong></button>
        <button className={status === 'NOT_MATCH' ? 'selected' : ''} onClick={() => { setStatus('NOT_MATCH'); setPage(0); }}>Không đáp ứng <strong>{counts.notMatched}</strong></button>
      </div>
      <div className="matching-result-header"><h3>{outcomeLabels[status]}</h3>
        {canNominate && status === 'MATCH' && <button onClick={() => void nominate()} disabled={busy || !selectedStudents.length || !capacity || selectedStudents.length > capacity.openForNomination}>
          Đề cử {selectedStudents.length ? `(${selectedStudents.length})` : 'sinh viên đã chọn'}
        </button>}
      </div>
      {canNominate && status === 'MATCH' && capacity && <p className="field-hint">Còn {capacity.openForNomination} / {capacity.quantity} vị trí · Đã xếp {capacity.placedCount} · Đang giữ chỗ {capacity.activeReservations}.</p>}
      {details ? details.results.content.length === 0 ? <EmptyState title="Không có sinh viên trong nhóm này" description="Hãy xem nhóm kết quả khác hoặc kiểm tra tiêu chí của nhu cầu." />
        : <div className="matching-candidates">{details.results.content.map(result => <Candidate key={result.studentUserId} result={result}
          profile={profiles[result.studentUserId]} selectable={canNominate && status === 'MATCH'} checked={selectedStudents.includes(result.studentUserId)}
          onToggle={() => toggleStudent(result.studentUserId)} />)}</div>
        : <p role="status">Đang tải kết quả…</p>}
      <div className="matching-pagination"><span>{details?.results.totalElements ?? 0} kết quả · Trang {(details?.results.page ?? page) + 1}/{Math.max(1, details?.results.totalPages ?? 1)}</span>
        <button className="secondary" disabled={page === 0 || busy} onClick={() => setPage(value => value - 1)}>Trước</button>
        <button className="secondary" disabled={!details || page + 1 >= details.results.totalPages || busy} onClick={() => setPage(value => value + 1)}>Sau</button></div>
    </>}
  </section>;
}

function Candidate({ result, profile, selectable, checked, onToggle }: { result: MatchResult; profile?: Projection | null;
  selectable: boolean; checked: boolean; onToggle: () => void }) {
  const name = profile?.data.displayName;
  const code = profile?.data.studentCode;
  const major = profile?.data.majorCode;
  const skills = Array.isArray(profile?.data.skills) ? profile.data.skills as { kind?: string; skill?: { skillCode?: string; name?: string } }[] : [];
  return <article className="matching-candidate">
    <div className="matching-candidate-top">
      {selectable && <label className="matching-select"><input type="checkbox" checked={checked} onChange={onToggle} aria-label={`Chọn sinh viên ${typeof code === 'string' ? code : ''}`} /></label>}
      <div className="matching-candidate-ident"><h4>{typeof name === 'string' ? name : profile === null ? 'Thông tin định danh không được cấp' : 'Đang tải hồ sơ…'}</h4>
        <p>{typeof code === 'string' ? `MSSV ${code}` : ''}{typeof major === 'string' ? ` · Ngành ${major}` : ''}</p></div>
      {result.fitScore !== null && <span className="matching-score">Điểm ưu tiên {result.fitScore.toFixed(2)}</span>}
    </div>
    {profile?.allowedFields.includes('skills') && <p className="matching-skills">Kỹ năng: {skills.map(item => item.skill?.skillCode || item.skill?.name).filter(Boolean).join(', ') || 'Chưa có kỹ năng khai báo'}</p>}
    <ul className="matching-criteria">{result.details.map((criterion, index) => <li key={`${criterion.criterionCode}-${index}`}>
      <span className={`matching-criterion-status ${criterion.status.toLowerCase()}`}>{criterion.status === 'PASS' ? 'Đạt' : criterion.status === 'FAIL' ? 'Không đạt' : 'Chưa rõ'}</span>
      <span>{criterion.kind === 'MAJOR' ? 'Ngành' : criterion.kind === 'AVAILABILITY' ? 'Lịch thực tập' : `Kỹ năng ${criterion.criterionCode}`}</span>
      <small>{reasonLabels[criterion.reasonCode] || criterion.reasonCode}</small>
    </li>)}</ul>
  </article>;
}
