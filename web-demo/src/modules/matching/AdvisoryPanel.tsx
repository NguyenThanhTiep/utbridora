import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../../api';

type Run = { matchRunId: string; demandVersion: number; criteriaVersion: number; profileSnapshotVersion: string };
type Row = { studentUserId: string; position: number; rank: number | null; fitScore: number;
  semanticScore: number | null; scoreStatus: 'SCORED' | 'NO_CONTENT' | 'INPUT_TOO_LARGE'; limited: boolean | null };
type Meta = { state: string; expiresAt: string; versions: { policyVersion: string; templateVersion: string } };
type Page = { state: string; scoredCount: number; eligibleCount: number; coverage: number; totalPages: number; content: Row[] };
type Identity = { data: { displayName?: string; studentCode?: string; majorCode?: string } };
type NominationContext = { demandVersion: number; criteriaVersion: number; cooperationVersion: number; batchVersion: number | null;
  capacity: { openForNomination: number }; candidates: { totalPages: number; content: { match: { studentUserId: string }; selectable: boolean }[] } };
const statuses: Record<string, string> = { QUEUED: 'Đang chờ xử lý', RUNNING: 'Đang phân tích', READY: 'Đã có gợi ý',
  NO_DATA: 'Chưa đủ nội dung để xếp hạng AI', FAILED: 'Chưa tạo được gợi ý', STALE: 'Dữ liệu đã thay đổi', REVOKED: 'Gợi ý không còn hiệu lực', EXPIRED: 'Gợi ý đã hết hạn' };
function message(error: unknown): string {
  const codes: Record<string, string> = {
    AI_ADVISORY_DISABLED: 'Gợi ý AI chưa được bật trong phạm vi này. Bạn vẫn có thể dùng kết quả Matching.',
    AI_ADVISORY_SOURCE_STALE: 'Hồ sơ hoặc nhu cầu đã thay đổi. Hãy chạy Matching lại.',
    AI_ADVISORY_RUN_NOT_ELIGIBLE: 'Hãy chạy một lần Matching mới cho nhà trường để tạo gợi ý.',
    AI_ADVISORY_JOB_ACTIVE: 'Lần chạy này đang có một yêu cầu gợi ý được xử lý.',
    AI_ADVISORY_EXPIRED: 'Gợi ý đã hết hạn. Hãy tạo lại gợi ý từ dữ liệu hiện hành.',
    AI_ADVISORY_CAPACITY_EXCEEDED: 'Hàng đợi đang đầy. Vui lòng thử lại sau.',
    AI_ADVISORY_INPUT_TOO_LARGE: 'Nội dung nhu cầu vượt giới hạn phân tích. Bạn vẫn có thể dùng Matching.',
    AI_ADVISORY_LIMIT_EXCEEDED: 'Dữ liệu vượt giới hạn phân tích. Bạn vẫn có thể dùng Matching.',
  };
  return error instanceof ApiError ? codes[error.errorCode || ''] || error.message : 'Chưa kết nối được máy chủ. Vui lòng thử lại.';
}

export function AdvisoryPanel({ run, termId, cooperationId, organizationId, canCreate, canNominate, onNomination }: {
  run: Run; termId: string; cooperationId: string; organizationId: string; canCreate: boolean; canNominate: boolean; onNomination: () => void;
}) {
  const [id, setId] = useState(''); const [meta, setMeta] = useState<Meta | null>(null);
  const [rows, setRows] = useState<Page | null>(null); const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<string[]>([]); const [error, setError] = useState('');
  const [notice, setNotice] = useState(''); const [busy, setBusy] = useState(false); const [refresh, setRefresh] = useState(0);
  const [identities, setIdentities] = useState<Record<string, Identity | null>>({});
  const createKey = useRef<string | null>(null); const nomination = useRef<{ body: string; key: string } | null>(null);
  const command = useRef<AbortController | null>(null);
  const epoch = useRef(0);
  useEffect(() => {
    const clear = () => { epoch.current++; command.current?.abort(); setBusy(false); setRows(null); setMeta(null); setSelected([]); setIdentities({}); setRefresh(n => n + 1); };
    const session = () => { clear(); setId(''); createKey.current = null; nomination.current = null; };
    const visible = () => { if (document.visibilityState === 'visible') clear(); };
    window.addEventListener('focus', clear); window.addEventListener('session-expired', session); document.addEventListener('visibilitychange', visible);
    return () => { epoch.current++; command.current?.abort(); window.removeEventListener('focus', clear); window.removeEventListener('session-expired', session); document.removeEventListener('visibilitychange', visible); };
  }, []);
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController(); const generation = epoch.current;
    setRows(null); setSelected([]); setIdentities({});
    void (async () => {
      try {
        const current = await api<Meta>(`/matching/advisories/${id}`, { signal: controller.signal });
        if (controller.signal.aborted || generation !== epoch.current) return;
        setMeta(current);
        if (['READY', 'NO_DATA'].includes(current.state)) {
          if (new Date(current.expiresAt).getTime() <= Date.now()) { setMeta({ ...current, state: 'EXPIRED' }); return; }
          const result = await api<Page>(`/matching/advisories/${id}/items?page=${page}&size=25`, { signal: controller.signal });
          if (!controller.signal.aborted && generation === epoch.current) {
            if (new Date(current.expiresAt).getTime() <= Date.now()) { setRows(null); setMeta({ ...current, state: 'EXPIRED' }); }
            else { setRows(result); setError(''); }
          }
        }
      } catch (failure) { if (!controller.signal.aborted && generation === epoch.current) { setRows(null); setMeta(null); setSelected([]); setError(message(failure)); } }
    })();
    return () => controller.abort();
  }, [id, page, refresh]);
  useEffect(() => {
    if (!meta || !['QUEUED', 'RUNNING'].includes(meta.state)) return;
    const timer = window.setInterval(() => setRefresh(n => n + 1), 5000);
    return () => window.clearInterval(timer);
  }, [meta?.state]);
  useEffect(() => {
    if (!meta || !['READY', 'NO_DATA'].includes(meta.state)) return;
    const remaining = new Date(meta.expiresAt).getTime() - Date.now();
    const timer = window.setTimeout(() => { setRows(null); setSelected([]); setIdentities({}); setMeta(current => current ? { ...current, state: 'EXPIRED' } : null); }, Math.max(0, Math.min(remaining, 2147483647)));
    return () => window.clearTimeout(timer);
  }, [meta?.expiresAt, meta?.state]);
  useEffect(() => {
    if (!rows) return;
    const controller = new AbortController();
    void Promise.all(rows.content.map(async row => {
      const params = new URLSearchParams({ termId, purpose: 'SCHOOL_REVIEW' });
      ['displayName', 'studentCode', 'majorCode'].forEach(field => params.append('fields', field));
      const data = await api<Identity>(`/students/${row.studentUserId}/profile?${params}`, { signal: controller.signal }).catch(() => null);
      return [row.studentUserId, data] as const;
    })).then(entries => { if (!controller.signal.aborted) setIdentities(Object.fromEntries(entries)); });
    return () => controller.abort();
  }, [rows, termId]);

  async function create() {
    if (!canCreate || busy) return;
    const controller = new AbortController(); command.current = controller;
    const generation = epoch.current; setBusy(true); setError(''); setNotice(''); setSelected([]); setRows(null);
    createKey.current ||= crypto.randomUUID();
    try {
      const result = await api<{ advisoryId: string }>(`/matching/runs/${run.matchRunId}/advisories`, {
        method: 'POST', signal: controller.signal, headers: { 'Idempotency-Key': createKey.current },
        body: JSON.stringify({ expectedDemandVersion: run.demandVersion, expectedCriteriaVersion: run.criteriaVersion, expectedProfileSnapshotVersion: run.profileSnapshotVersion }),
      });
      if (controller.signal.aborted || generation !== epoch.current) return;
      createKey.current = null; setId(result.advisoryId); setMeta(null); setPage(0); setRefresh(n => n + 1);
    } catch (failure) { if (!controller.signal.aborted && generation === epoch.current) setError(message(failure)); }
    finally { if (generation === epoch.current) setBusy(false); }
  }
  async function nominate() {
    if (nomination.current) { await retryNomination(); return; }
    if (!selected.length || !id || busy) return;
    const controller = new AbortController(); command.current = controller; const generation = epoch.current;
    setBusy(true); setError('');
    try {
      // A fresh advisory gate before using a UI selection; POST nominate still has its own canonical guards.
      const current = await api<Meta>(`/matching/advisories/${id}`, { signal: controller.signal });
      if (!['READY', 'NO_DATA'].includes(current.state)) throw new Error('stale');
      let context: NominationContext | null = null; const found = new Set<string>(); let index = 0, pages = 1;
      while (index < pages && found.size < selected.length) {
        const next = await api<NominationContext>(`/school/cooperations/${cooperationId}/nomination-candidates?matchRunId=${run.matchRunId}&overallStatus=MATCH&page=${index}&size=100`, { signal: controller.signal });
        if (context && [next.demandVersion, next.criteriaVersion, next.cooperationVersion, next.batchVersion].join(':') !== [context.demandVersion, context.criteriaVersion, context.cooperationVersion, context.batchVersion].join(':')) throw new Error('changed');
        context = next; pages = next.candidates.totalPages; index++;
        next.candidates.content.forEach(candidate => { if (candidate.selectable && selected.includes(candidate.match.studentUserId)) found.add(candidate.match.studentUserId); });
      }
      if (!context || found.size !== selected.length || context.capacity.openForNomination < selected.length) throw new Error('unavailable');
      const body = JSON.stringify({ studentUserIds: selected, expectedDemandVersion: context.demandVersion, expectedCriteriaVersion: context.criteriaVersion,
        expectedCooperationVersion: context.cooperationVersion, expectedBatchVersion: context.batchVersion });
      nomination.current = { body, key: crypto.randomUUID() };
      await api(`/matching/runs/${run.matchRunId}/nominate`, { method: 'POST', signal: controller.signal, headers: { 'Idempotency-Key': nomination.current.key, 'X-Organization-Id': organizationId }, body });
      if (!controller.signal.aborted && generation === epoch.current) { nomination.current = null; setSelected([]); setNotice('Đã đề cử các sinh viên được chọn.'); onNomination(); }
    } catch (failure) { if (!controller.signal.aborted && generation === epoch.current) { if (failure instanceof ApiError && failure.status < 500 && failure.errorCode !== 'REQUEST_IN_PROGRESS') nomination.current = null; setSelected([]); setRows(null); setError(failure instanceof ApiError ? message(failure) : 'Chưa xác nhận được kết quả. Kiểm tra lần gửi trước trước khi đề cử tiếp.'); } }
    finally { if (generation === epoch.current) setBusy(false); }
  }
  async function retryNomination() {
    const pending = nomination.current;
    if (!pending || busy) return;
    const controller = new AbortController(); command.current = controller; const generation = epoch.current;
    setBusy(true); setError('');
    try {
      // Recover exactly the prior explicit command; never use a fresh key after a lost ACK.
      await api(`/matching/runs/${run.matchRunId}/nominate`, { method: 'POST', signal: controller.signal,
        headers: { 'Idempotency-Key': pending.key, 'X-Organization-Id': organizationId }, body: pending.body });
      if (!controller.signal.aborted && generation === epoch.current) { nomination.current = null; setSelected([]); setNotice('Đã xác nhận kết quả lần đề cử trước.'); onNomination(); }
    } catch (failure) { if (!controller.signal.aborted && generation === epoch.current) { if (failure instanceof ApiError && failure.status < 500 && failure.errorCode !== 'REQUEST_IN_PROGRESS') nomination.current = null; setError(message(failure)); } }
    finally { if (generation === epoch.current) setBusy(false); }
  }
  return <section className="advisory-panel" aria-label="Gợi ý AI cho nhà trường" aria-busy={busy}>
    <div className="matching-result-header"><div><h3>Gợi ý AI</h3><p>Tham khảo thứ tự gợi ý trong nhóm đáp ứng điều kiện, sau đó tự chọn sinh viên để đề cử.</p></div>
      {canCreate && <button className="secondary" disabled={busy || !!meta && ['QUEUED', 'RUNNING'].includes(meta.state)} onClick={() => void create()}>{id ? 'Tạo lại gợi ý' : 'Tạo gợi ý AI'}</button>}</div>
    {error && <p role="alert" className="notice error">{error}</p>}{notice && <p role="status">{notice}</p>}
    {nomination.current && <button className="secondary" disabled={busy} onClick={() => void retryNomination()}>Kiểm tra kết quả lần đề cử trước</button>}
    {meta && <p role="status">{statuses[meta.state] || 'Chưa sẵn sàng'} · Hết hạn {new Date(meta.expiresAt).toLocaleString('vi-VN')}</p>}
    {id && <button className="secondary" disabled={busy} onClick={() => { setRows(null); setSelected([]); setRefresh(n => n + 1); }}>Kiểm tra lại gợi ý</button>}
    {rows && <><p>Đã có điểm cho {rows.scoredCount}/{rows.eligibleCount} hồ sơ ({(rows.coverage * 100).toFixed(1)}%). Điểm tương đồng không phải xác suất trúng tuyển.</p>
      {rows.state === 'NO_DATA' && <p>Danh sách dưới đây giữ thứ tự Matching vì chưa có điểm AI.</p>}
      <div className="matching-candidates">{rows.content.map(row => <article className="matching-candidate" key={row.studentUserId}>
        <div className="matching-candidate-top">{canNominate && <input type="checkbox" aria-label={`Chọn hồ sơ vị trí ${row.position}`} checked={selected.includes(row.studentUserId)} disabled={busy}
          onChange={() => setSelected(old => old.includes(row.studentUserId) ? old.filter(x => x !== row.studentUserId) : [...old, row.studentUserId])} />}
          <div className="advisory-identity"><h4>{identities[row.studentUserId]?.data.displayName || 'Hồ sơ sinh viên'}</h4><p>{identities[row.studentUserId]?.data.studentCode || 'Định danh theo quyền được cấp'}</p></div>
          <strong>{row.rank === null ? 'Chưa có điểm AI' : `Gợi ý #${row.rank} · ${row.semanticScore?.toFixed(6)}`}</strong></div>
        <p>Điểm ưu tiên: {row.fitScore.toFixed(2)}{row.limited ? ' · Nội dung đã được giới hạn khi phân tích' : ''}</p>
        {row.scoreStatus !== 'SCORED' && <p>{row.scoreStatus === 'INPUT_TOO_LARGE' ? 'Nội dung vượt giới hạn phân tích; hồ sơ vẫn có thể được xem xét.' : 'Chưa đủ nội dung ngữ nghĩa; đây không phải đánh giá năng lực thấp.'}</p>}
      </article>)}</div>
      <div className="matching-pagination"><button className="secondary" disabled={!page || busy} onClick={() => setPage(n => n - 1)}>Trước</button>
        <span>Trang {page + 1}/{Math.max(1, rows.totalPages)}</span><button className="secondary" disabled={page + 1 >= rows.totalPages || busy} onClick={() => setPage(n => n + 1)}>Sau</button>
        {canNominate && <button disabled={busy || !selected.length} onClick={() => void nominate()}>Đề cử {selected.length} sinh viên đã chọn</button>}</div>
    </>}
  </section>;
}
