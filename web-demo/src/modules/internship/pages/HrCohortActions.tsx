import { useRef, useState, type FormEvent } from 'react';
import { api, ApiError } from '../../../api';
import { newIdempotencyKey } from '../../student/api';
import type { Cohort, WorkspaceAssignment } from '../types';

type BatchResult = {
  version: number;
  items: { placementId: string; caseId: string | null; outcome: string; reasonCode: string | null }[];
};
type GroupingPlan = {
  planId: string; cohortVersion: number; expiresAt: string;
  groups: { ordinal: number; caseIds: string[] }[];
};
const uuid = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function errorMessage(error: unknown) {
  if (error instanceof ApiError) {
    const hint = error.status === 409 ? ' Dữ liệu đã thay đổi; hãy tải lại đợt thực tập.' : '';
    return `${error.message}${hint}${error.requestId ? ` (mã hỗ trợ ${error.requestId})` : ''}`;
  }
  return 'Không kết nối được máy chủ. Vui lòng thử lại.';
}

// Keep the exact payload and key for a retry after a lost response.
function usePendingRequest() {
  const pending = useRef<{ body: string; key: string } | null>(null);
  return {
    request(body: object) {
      const encoded = JSON.stringify(body);
      if (pending.current?.body !== encoded) pending.current = { body: encoded, key: newIdempotencyKey() };
      return pending.current;
    },
    clear() { pending.current = null; }
  };
}

export function HrCreateCohort({ assignment, created }: {
  assignment: WorkspaceAssignment; created: (value: Cohort) => void;
}) {
  const [code, setCode] = useState(''), [name, setName] = useState('');
  const [busy, setBusy] = useState(false), [feedback, setFeedback] = useState('');
  const pending = usePendingRequest();
  async function submit(event: FormEvent) {
    event.preventDefault(); setBusy(true); setFeedback('');
    const request = pending.request({ termId: assignment.scope.termId, code: code.trim(), name: name.trim() });
    try {
      const cohort = await api<Cohort>('/t3/cohorts', {
        method: 'POST', body: request.body,
        headers: { 'X-Context-Assignment-Id': assignment.id, 'Idempotency-Key': request.key }
      });
      pending.clear(); setCode(''); setName(''); setFeedback('Đã tạo đợt thực tập.');
      created(cohort);
    } catch (error) { setFeedback(errorMessage(error)); }
    finally { setBusy(false); }
  }
  return <form className="panel stack" onSubmit={event => void submit(event)}>
    <h3>Tạo đợt thực tập</h3>
    <p className="muted">Trường và kỳ lấy từ phân công HR đang chọn.</p>
    <label>Mã đợt<input required maxLength={64} value={code} onChange={event => setCode(event.target.value)} /></label>
    <label>Tên đợt<input required maxLength={200} value={name} onChange={event => setName(event.target.value)} /></label>
    <button disabled={busy || !code.trim() || !name.trim()}>Tạo đợt</button>
    {feedback && <p role="status">{feedback}</p>}
  </form>;
}

export function HrCohortManagement({ assignment, cohort, changed, versionChanged, section = 'all' }: {
  assignment: WorkspaceAssignment; cohort: Cohort; changed: () => void;
  versionChanged: (version: number) => void; section?: 'all' | 'cohorts' | 'groups';
}) {
  const [ids, setIds] = useState(''), [size, setSize] = useState(10);
  const [version, setVersion] = useState(cohort.version), [plan, setPlan] = useState<GroupingPlan | null>(null);
  const [result, setResult] = useState<BatchResult | null>(null);
  const [busy, setBusy] = useState(false), [feedback, setFeedback] = useState('');
  const pending = usePendingRequest();
  const path = `/t3/cohorts/${encodeURIComponent(cohort.cohortId)}`;

  async function send<T>(endpoint: string, body: object): Promise<T | undefined> {
    setBusy(true); setFeedback('');
    const request = pending.request(body);
    try {
      const value = await api<T>(endpoint, {
        method: 'POST', body: request.body,
        headers: { 'X-Context-Assignment-Id': assignment.id, 'Idempotency-Key': request.key }
      });
      pending.clear();
      return value;
    } catch (error) { setFeedback(errorMessage(error)); return undefined; }
    finally { setBusy(false); }
  }
  async function intake(event: FormEvent) {
    event.preventDefault();
    const values = ids.split(/[\s,;]+/).map(id => id.trim()).filter(Boolean);
    if (!values.length || values.length > 1000 || values.some(id => !uuid.test(id)) || new Set(values).size !== values.length) {
      setFeedback('Nhập 1–1000 Placement ID dạng UUID, không trùng nhau.'); return;
    }
    const value = await send<BatchResult>(path + '/members:batch', {
      expectedVersion: version, placementIds: values, previewId: null, fileHash: null
    });
    if (value) { setVersion(value.version); versionChanged(value.version); setResult(value); setPlan(null); setIds(''); }
  }
  async function preview() {
    if (!Number.isInteger(size) || size < 1 || size > 1000) {
      setFeedback('Sĩ số nhóm phải là số nguyên từ 1 đến 1000.'); return;
    }
    const value = await send<GroupingPlan>(path + '/groups:auto', {
      mode: 'PREVIEW', groupSize: size, expectedCohortVersion: version
    });
    if (value) setPlan(value);
  }
  async function confirm() {
    if (!plan) return;
    const value = await send<unknown[]>(path + '/groups:auto', {
      mode: 'CONFIRM', planId: plan.planId, expectedCohortVersion: plan.cohortVersion
    });
    if (value) { setPlan(null); changed(); }
  }
  return <div className="stack">
    {(section === 'all' || section === 'cohorts') && <form className="panel stack" onSubmit={event => void intake(event)}>
      <h3>Nhận Placement từ T5 vào {cohort.name}</h3>
      <p className="muted">Dán Placement ID đã được cấp, mỗi dòng một ID. T3 chỉ đối chiếu nguồn T5; không tạo Placement mới.</p>
      <label>Placement ID<textarea value={ids} onChange={event => setIds(event.target.value)} rows={4} placeholder="UUID mỗi dòng" /></label>
      <button disabled={busy || !ids.trim()}>Đối chiếu và nhận hồ sơ</button>
      {result && <ul aria-label="Kết quả nhận hồ sơ">{result.items.map(item => <li key={item.placementId}>
        {item.placementId}: {item.outcome === 'ADDED' ? 'Đã nhận' :
          item.outcome === 'ALREADY_PRESENT' ? 'Đã có trong đợt' : 'Không hợp lệ'}
        {item.reasonCode && !['NONE', 'ALREADY_PRESENT'].includes(item.reasonCode) ? ` (${item.reasonCode})` : ''}
      </li>)}</ul>}
    </form>}
    {(section === 'all' || section === 'groups') && <section className="panel stack">
      <h3>Chia nhóm tự động</h3>
      <label>Số hồ sơ mỗi nhóm<input type="number" min="1" max="1000" step="1" value={size}
        onChange={event => { setSize(Number(event.target.value)); setPlan(null); }} /></label>
      <button type="button" disabled={busy} onClick={() => void preview()}>Xem trước nhóm</button>
      {plan && <div>
        <p>{plan.groups.length} nhóm dự kiến; bản xem trước hết hạn {new Date(plan.expiresAt).toLocaleString('vi-VN')}.</p>
        <ul>{plan.groups.map(group => <li key={group.ordinal}>Nhóm {group.ordinal}: {group.caseIds.length} hồ sơ</li>)}</ul>
        <button type="button" disabled={busy} onClick={() => void confirm()}>Xác nhận chia nhóm</button>
      </div>}
    </section>}
    {feedback && <p role="status">{feedback}</p>}
  </div>;
}
