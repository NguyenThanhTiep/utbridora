import { useEffect, useRef, useState } from 'react';
import { api } from '../../api';
import { Notice } from '../../components';
import { newIdempotencyKey } from '../student/api';
import type { ContractPage } from '../student/types';
import type { Demand } from './types';
import { EnterpriseProposals } from './proposals/EnterpriseProposals';
import { SchoolProposals } from './proposals/SchoolProposals';
import { PlacementWorkspace } from './proposals/PlacementWorkspace';
import { EnterpriseMatchingSummary } from './proposals/EnterpriseMatchingSummary';
type Cooperation = { cooperationId: string; demandId: string; state: string; version: number; submissionNo: number; termId: string };
type VerificationInput = { schoolOrgId: string; previousVerificationId: string | null; registrationCode: string; registeredName: string; address: string; contactName: string; contactEmail: string; contactPhone: string; evidenceFileIds: string[] };
type Verification = { verificationId: string; state: string; version: number; data: VerificationInput; missingFields: string[]; decisionRef: string | null };
type Detail = { demand: Demand; cooperation: Cooperation; feedback: { reasonCode: string | null; missingFields: string[]; decisionRef: string } | null };
const states: Record<string, string> = { DRAFT: 'Bản nháp', SUBMITTED: 'Đã gửi', NEEDS_CHANGES: 'Cần bổ sung', APPROVED: 'Đã duyệt', REJECTED: 'Từ chối' };
function useCommand(org: string, changed: () => void) {
  const [busy, setBusy] = useState(false), [message, setMessage] = useState(''); const pending = useRef(new Map<string, string>());
  const command = async (path: string, method: string, data: unknown) => {
    const body = JSON.stringify(data), ref = path + body; if (!pending.current.has(ref)) pending.current.set(ref, newIdempotencyKey()); setBusy(true); setMessage('');
    try { await api(path, { method, body, headers: { 'X-Organization-Id': org, 'Idempotency-Key': pending.current.get(ref)! } }); pending.current.delete(ref); changed(); }
    catch { setMessage('Thao tác chưa hoàn tất. Kiểm tra quyền, phiên bản và thông tin bắt buộc rồi thử lại.'); } finally { setBusy(false); }
  };
  return { busy, message, command };
}
export function CooperationFlows({ org, school }: { org: string; school: boolean }) {
  const [items, setItems] = useState<Cooperation[]>([]), [detail, setDetail] = useState<Detail | null>(null), [refresh, setRefresh] = useState(0), [page, setPage] = useState(0), [total, setTotal] = useState(0), [error, setError] = useState('');
  const side = school ? 'school' : 'enterprise', changed = () => { setDetail(null); setRefresh(n => n + 1); }; const action = useCommand(org, changed);
  useEffect(() => { const update = () => changed(); window.addEventListener('cooperation-updated', update); return () => window.removeEventListener('cooperation-updated', update); }, []);
  useEffect(() => { const c = new AbortController(); setError(''); setDetail(null); void api<ContractPage<Cooperation>>('/' + side + '/cooperations?page=' + page + '&size=25', { signal: c.signal, headers: { 'X-Organization-Id': org } }).then(p => { if (!c.signal.aborted) { setItems(p.content); setTotal(p.totalPages); } }).catch(() => { if (!c.signal.aborted) setError('Chưa tải được đề nghị hợp tác.'); }); return () => c.abort(); }, [org, side, refresh, page]);
  return <section className="panel workflow-surface"><h2>Đề nghị hợp tác</h2><Notice message={action.message || error} tone="error" /><button className="secondary" disabled={action.busy} onClick={changed}>Tải lại đề nghị</button>
    {items.length === 0 && <p>Chưa có đề nghị trong phạm vi này.</p>}
    {items.map((c, n) => <article key={c.cooperationId}><h3>Đề nghị {page * 25 + n + 1} · {states[c.state] || c.state}</h3><p>Lần gửi {c.submissionNo}</p><button className="secondary" onClick={() => void api<Detail>('/' + side + '/cooperations/' + c.cooperationId, { headers: { 'X-Organization-Id': org } }).then(setDetail).catch(() => setError('Không đọc được đề nghị.'))}>Xem nội dung đề nghị {page * 25 + n + 1}</button></article>)}
    <div className="button-row"><button className="secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>Trước</button><span>Trang {page + 1}/{Math.max(1, total)}</span><button className="secondary" disabled={page + 1 >= total} onClick={() => setPage(n => n + 1)}>Sau</button></div>
    {detail && <article><h3>{detail.demand.data.title}</h3><p>{detail.demand.data.description}</p><p>{detail.demand.data.quantity} vị trí · {detail.demand.data.location}</p><p>Tiêu chí bắt buộc: {detail.demand.data.required.skills.map(s => s.skillCode).join(', ') || 'Không yêu cầu kỹ năng cụ thể'}</p><p>Thời gian: {detail.demand.data.required.dateRange.startDate} – {detail.demand.data.required.dateRange.endDate}</p>
      {detail.feedback && <p>Phản hồi: {detail.feedback.reasonCode || 'Đã duyệt'} · Cần bổ sung: {detail.feedback.missingFields.join(', ') || 'Không có'} · Căn cứ: {detail.feedback.decisionRef}</p>}
      {school && detail.cooperation.state === 'SUBMITTED' && <ReviewDecision busy={action.busy} fields={['title', 'description', 'quantity', 'required', 'preferred', 'deadline']} submit={data => action.command('/school/cooperations/' + detail.cooperation.cooperationId + '/decision', 'PATCH', { expectedVersion: detail.cooperation.version, ...data })} />}
      {!school && ['DRAFT', 'NEEDS_CHANGES'].includes(detail.cooperation.state) && <button disabled={action.busy} onClick={() => void action.command('/enterprise/cooperations/' + detail.cooperation.cooperationId + '/submit', 'PATCH', { expectedVersion: detail.cooperation.version, expectedDemandVersion: detail.demand.version })}>Gửi lại đề nghị hợp tác</button>}
      {!school && detail.cooperation.state === 'REJECTED' && <button disabled={action.busy} onClick={() => void action.command('/enterprise/cooperations/' + detail.cooperation.cooperationId + '/reopen', 'PATCH', { expectedVersion: detail.cooperation.version, reasonCode: 'ENTERPRISE_REOPENED' })}>Mở lại bản nháp để bổ sung</button>}
      {detail.cooperation.state === 'APPROVED' && (school
        ? <SchoolProposals key={org + detail.cooperation.cooperationId} org={org} cooperationId={detail.cooperation.cooperationId} demand={detail.demand} />
        : <div key={org + detail.cooperation.cooperationId}><EnterpriseMatchingSummary key={org + detail.demand.demandId + ':' + detail.demand.version} org={org} demand={detail.demand} /><EnterpriseProposals org={org} cooperationId={detail.cooperation.cooperationId} />
          <PlacementWorkspace org={org} school={false} cooperationId={detail.cooperation.cooperationId} demand={detail.demand} /></div>)}
    </article>}
  </section>;
}
function ReviewDecision({ busy, fields, submit }: { busy: boolean; fields: string[]; submit: (data: { decision: string; reasonCode: string | null; decisionRef: string; missingFields: string[] }) => Promise<void> }) {
  const fieldLabels: Record<string, string> = { title: 'Tiêu đề', description: 'Mô tả', quantity: 'Chỉ tiêu', required: 'Tiêu chí bắt buộc', preferred: 'Tiêu chí ưu tiên', deadline: 'Hạn tuyển', registrationCode: 'Mã đăng ký', registeredName: 'Tên đăng ký', address: 'Địa chỉ', contactName: 'Người liên hệ', contactEmail: 'Email liên hệ', contactPhone: 'Điện thoại', evidenceFileIds: 'Minh chứng' };
  const [decision, setDecision] = useState('APPROVE'), [reference, setReference] = useState(''), [missing, setMissing] = useState<string[]>([]);
  return <form onSubmit={e => { e.preventDefault(); void submit({ decision, reasonCode: decision === 'APPROVE' ? null : decision === 'REJECT' ? 'REVIEW_REJECTED' : 'ADDITIONAL_INFO_REQUIRED', decisionRef: reference, missingFields: decision === 'REQUEST_INFO' ? missing : [] }); }}><fieldset disabled={busy}>
    <label>Kết quả xét duyệt<select value={decision} onChange={e => setDecision(e.target.value)}><option value="APPROVE">Phê duyệt</option><option value="REQUEST_INFO">Yêu cầu bổ sung</option><option value="REJECT">Từ chối</option></select></label><label>Căn cứ quyết định<input required maxLength={120} value={reference} onChange={e => setReference(e.target.value)} /></label>
    {decision === 'REQUEST_INFO' && <fieldset><legend>Thông tin cần bổ sung</legend>{fields.map(f => <label key={f}><input type="checkbox" checked={missing.includes(f)} onChange={e => setMissing(e.target.checked ? [...missing, f] : missing.filter(v => v !== f))} />{fieldLabels[f] || f}</label>)}</fieldset>}<button disabled={decision === 'REQUEST_INFO' && missing.length === 0}>Lưu kết quả xét duyệt</button>
  </fieldset></form>;
}
export function VerificationFlows({ org, school }: { org: string; school: boolean }) {
  const [items, setItems] = useState<Verification[]>([]), [refresh, setRefresh] = useState(0), [page, setPage] = useState(0), [total, setTotal] = useState(0), [editing, setEditing] = useState<Verification | null | undefined>(undefined), [error, setError] = useState('');
  const changed = () => { setEditing(undefined); setRefresh(n => n + 1); }, action = useCommand(org, changed), side = school ? 'school' : 'enterprise';
  useEffect(() => { const c = new AbortController(); void api<ContractPage<Verification>>('/' + side + '/verifications?page=' + page + '&size=25', { signal: c.signal, headers: { 'X-Organization-Id': org } }).then(p => { if (!c.signal.aborted) { setItems(p.content); setTotal(p.totalPages); } }).catch(() => { if (!c.signal.aborted) setError('Chưa tải được hồ sơ xác minh.'); }); return () => c.abort(); }, [org, side, page, refresh]);
  return <section className="panel workflow-surface"><h2>Xác minh doanh nghiệp</h2><Notice message={error || action.message} tone="error" /><div className="button-row"><button className="secondary" disabled={action.busy} onClick={changed}>Tải lại xác minh</button>{!school && <button onClick={() => setEditing(null)}>Tạo hồ sơ xác minh</button>}</div>
    {items.map(v => <article key={v.verificationId}><h3>{v.data.registeredName} · {states[v.state]}</h3><p>{v.data.registrationCode} · {v.data.address}</p><p>Liên hệ: {v.data.contactName} · {v.data.contactEmail} · {v.data.contactPhone}</p><p>{v.data.evidenceFileIds.length} minh chứng đã liên kết</p>{v.missingFields.length > 0 && <p>Cần bổ sung: {v.missingFields.join(', ')}</p>}
      {school && v.state === 'SUBMITTED' && <ReviewDecision busy={action.busy} fields={['registrationCode', 'registeredName', 'address', 'contactName', 'contactEmail', 'contactPhone', 'evidenceFileIds']} submit={data => action.command('/school/verifications/' + v.verificationId + '/decision', 'PATCH', { expectedVersion: v.version, ...data })} />}
      {!school && ['DRAFT', 'NEEDS_CHANGES'].includes(v.state) && <div className="button-row"><button className="secondary" onClick={() => setEditing(v)}>Bổ sung hồ sơ</button><button disabled={action.busy || !v.data.evidenceFileIds.length} onClick={() => void action.command('/enterprise/verifications/' + v.verificationId + '/submit', 'PATCH', { expectedVersion: v.version })}>Gửi xác minh</button>{!v.data.evidenceFileIds.length && <p>Bổ sung minh chứng trước khi gửi. Tải tệp được triển khai ở giai đoạn tiếp theo.</p>}</div>}
    </article>)}
    {editing !== undefined && <VerificationEditor key={editing?.verificationId || 'new'} existing={editing} busy={action.busy} save={data => action.command('/enterprise/verifications' + (editing ? '/' + editing.verificationId : ''), editing ? 'PATCH' : 'POST', editing ? { expectedVersion: editing.version, data } : data)} />}
    <div className="button-row"><button className="secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>Trước</button><span>Trang {page + 1}/{Math.max(1, total)}</span><button className="secondary" disabled={page + 1 >= total} onClick={() => setPage(n => n + 1)}>Sau</button></div>
  </section>;
}
function VerificationEditor({ existing, busy, save }: { existing: Verification | null; busy: boolean; save: (data: VerificationInput) => Promise<void> }) {
  const [data, setData] = useState<VerificationInput>(existing?.data || { schoolOrgId: '', previousVerificationId: null, registrationCode: '', registeredName: '', address: '', contactName: '', contactEmail: '', contactPhone: '', evidenceFileIds: [] });
  const labels: Record<string, string> = { schoolOrgId: 'Mã trường nhận hồ sơ', registrationCode: 'Mã đăng ký doanh nghiệp', registeredName: 'Tên đăng ký', address: 'Địa chỉ', contactName: 'Người liên hệ', contactEmail: 'Email liên hệ', contactPhone: 'Điện thoại liên hệ' };
  return <form onSubmit={e => { e.preventDefault(); void save(data); }}><h3>{existing ? 'Bổ sung thông tin xác minh' : 'Hồ sơ xác minh mới'}</h3><fieldset disabled={busy}>{(Object.keys(labels) as (keyof VerificationInput)[]).map(f => <label key={f}>{labels[f]}<input required type={f === 'contactEmail' ? 'email' : 'text'} readOnly={f === 'schoolOrgId' && !!existing} value={String(data[f])} onChange={e => setData({ ...data, [f]: e.target.value })} /></label>)}<button>Lưu hồ sơ xác minh</button></fieldset></form>;
}
