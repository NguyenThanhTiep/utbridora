import { useEffect, useRef, useState } from 'react';
import { api } from '../../../api';
import { Notice } from '../../../components';
import { newIdempotencyKey } from '../api';
import type { ContractPage } from '../types';
type Invitation = { invitationId: string; title: string; enterpriseName: string; enterpriseOrgId: string; schoolOrgId: string; state: string; termId: string; version: number; expiresAt: string };
type Consent = { consentId: string; invitationId: string; recipientOrgId: string; schoolOrgId: string; termId: string; fieldWhitelist: string[]; lineageVersion: number; expiresAt: string; revokedAt: string | null; replacedByConsentId: string | null };
type Policy = { version: number; allowedFields: string[] };
const labels: Record<string, string> = { majorCode: 'Ngành', skills: 'Kỹ năng', projects: 'Dự án', certificates: 'Chứng chỉ', cv: 'CV', evidence: 'Minh chứng', displayName: 'Họ tên', studentCode: 'Mã sinh viên', email: 'Email', phone: 'Điện thoại' };
export function InvitationConsentPage() {
  const [invitations, setInvitations] = useState<Invitation[]>([]), [consents, setConsents] = useState<Consent[]>([]), [refresh, setRefresh] = useState(0);
  const [message, setMessage] = useState(''), [busy, setBusy] = useState(false);
  const pending = useRef(new Map<string, string>());
  useEffect(() => {
    const c = new AbortController();
    const readAll = async <T,>(path: string) => { const items: T[] = []; let page = 0, total = 1; while (page < total) { const r = await api<ContractPage<T>>(path + '?page=' + page + '&size=100', { signal: c.signal }); items.push(...r.content); total = r.totalPages; page++; } return items; };
    void Promise.all([readAll<Invitation>('/invitations/me'), readAll<Consent>('/students/me/consents')]).then(([i, s]) => { if (!c.signal.aborted) { setInvitations(i); setConsents(s); } }).catch(() => { if (!c.signal.aborted) setMessage('Không tải được lời mời hoặc quyền chia sẻ.'); });
    return () => c.abort();
  }, [refresh]);
  const command = async (path: string, data: unknown) => {
    const body = JSON.stringify(data), ref = path + body; if (!pending.current.has(ref)) pending.current.set(ref, newIdempotencyKey());
    setBusy(true); setMessage('');
    try { await api(path, { method: 'POST', body, headers: { 'Idempotency-Key': pending.current.get(ref)! } }); pending.current.delete(ref); setRefresh(n => n + 1); }
    catch { setMessage('Thao tác chưa hoàn tất. Kiểm tra thời hạn và tải lại trạng thái trước khi thử lại.'); }
    finally { setBusy(false); }
  };
  return <div className="stack workflow-surface"><Notice message={message} tone="error" /><button className="secondary" disabled={busy} onClick={() => setRefresh(n => n + 1)}>Tải lại lời mời</button>
    {invitations.length === 0 && <section className="panel"><h2>Chưa có lời mời thực tập</h2></section>}
    {invitations.map(i => <section className="panel" key={i.invitationId}><h2>{i.title}</h2><p>{i.enterpriseName} · {i.state}</p><p>Hạn phản hồi: {new Date(i.expiresAt).toLocaleString('vi-VN')}</p>
      {i.state === 'PENDING' && <div className="button-row"><button disabled={busy || Date.now() >= Date.parse(i.expiresAt)} onClick={() => void command('/invitations/' + i.invitationId + '/decision', { decision: 'ACCEPT', expectedVersion: i.version, reasonCode: null, reasonText: null })}>Nhận lời mời</button>
        <button className="secondary" disabled={busy} onClick={() => void command('/invitations/' + i.invitationId + '/decision', { decision: 'DECLINE', expectedVersion: i.version, reasonCode: 'STUDENT_DECLINED', reasonText: null })}>Từ chối</button></div>}
      {i.state === 'ACCEPTED' && <div><p>Rút nhận lời sẽ hủy việc tham gia cơ hội thực tập này.</p><button className="secondary" disabled={busy} onClick={() => { if (window.confirm('CẢNH BÁO: Rút nhận lời sẽ HỦY BỎ hoàn toàn cơ hội thực tập này và không thể hoàn tác. Bạn có chắc chắn muốn hủy?')) void command('/invitations/' + i.invitationId + '/withdraw', { expectedVersion: i.version, reasonCode: 'STUDENT_WITHDREW', reasonText: null }); }}>Rút nhận lời</button></div>}
      <p>Lưu ý: Để chỉ thu hồi quyền xem thông tin cá nhân của Doanh nghiệp mà vẫn giữ vị trí thực tập, hãy bấm “Thu hồi quyền chia sẻ” ở bảng bên dưới.</p>
      <ConsentForm key={i.invitationId + ':' + refresh} invitation={i} consents={consents} busy={busy} command={command} />
    </section>)}
    <section className="panel"><h2>Quyền chia sẻ đã cấp</h2>{consents.length === 0 && <p>Chưa cấp quyền chia sẻ.</p>}{consents.map(c => <article key={c.consentId}><p>{c.fieldWhitelist.map(f => labels[f]).join(', ')} · hết hạn {new Date(c.expiresAt).toLocaleString('vi-VN')}</p>
      <p>{c.revokedAt ? 'Đã thu hồi' : c.replacedByConsentId ? 'Đã được thay thế' : Date.parse(c.expiresAt) <= Date.now() ? 'Đã hết hạn' : 'Đang có hiệu lực'}</p>
      {!c.revokedAt && !c.replacedByConsentId && <button className="secondary" disabled={busy} onClick={() => void command('/students/me/consents/' + c.consentId + '/revoke', { expectedLineageVersion: c.lineageVersion, reasonCode: 'STUDENT_REVOKED' })}>Thu hồi quyền chia sẻ</button>}</article>)}</section>
  </div>;
}
function ConsentForm({ invitation, consents, busy, command }: { invitation: Invitation; consents: Consent[]; busy: boolean; command: (path: string, data: unknown) => Promise<void> }) {
  const [policy, setPolicy] = useState<Policy | null>(null), [selected, setSelected] = useState<string[]>([]), [days, setDays] = useState(7);
  useEffect(() => { const c = new AbortController(); void api<Policy>('/school/terms/' + invitation.termId + '/profile-sharing-policy', { signal: c.signal }).then(setPolicy).catch(() => { if (!c.signal.aborted) setPolicy(null); }); return () => c.abort(); }, [invitation.termId]);
  const expiry = useRef<{ days: number; value: string } | null>(null);
  const lineage = Math.max(0, ...consents.filter(c => c.recipientOrgId === invitation.enterpriseOrgId && c.schoolOrgId === invitation.schoolOrgId && c.termId === invitation.termId).map(c => c.lineageVersion));
  return <form onSubmit={e => { e.preventDefault(); if (policy && selected.length) {
    if (expiry.current?.days !== days) expiry.current = { days, value: new Date(Date.now() + days * 86400000).toISOString() };
    void command('/students/me/consents', { invitationId: invitation.invitationId, purpose: 'INTERNSHIP_SELECTION', fieldWhitelist: selected, expiresAt: expiry.current.value, policyVersion: 'demo-consent-v1', expectedSchoolPolicyVersion: policy.version, expectedLineageVersion: lineage });
  } }}>
    <h3>Quyết định chia sẻ hồ sơ riêng</h3><p>Bạn cho phép nhà trường chia sẻ các thông tin đã chọn với {invitation.enterpriseName} để đánh giá thực tập. Nhà trường sẽ cấp hoặc cập nhật chia sẻ sau khi bạn đồng ý. Nhận lời mời không tự cấp quyền xem hồ sơ cho doanh nghiệp.</p>
    <fieldset disabled={busy || !policy}>{policy?.allowedFields.map(f => <label key={f}><input type="checkbox" checked={selected.includes(f)} onChange={e => setSelected(e.target.checked ? [...selected, f] : selected.filter(v => v !== f))} />{labels[f] || f}</label>)}
      <label>Số ngày hiệu lực<input type="number" min="1" max="30" required value={days} onChange={e => setDays(Number(e.target.value))} /></label>
      <button disabled={!selected.length}>Đồng ý chia sẻ các mục đã chọn</button>
    </fieldset>{!policy && <p>Chưa đọc được chính sách chia sẻ hiện hành.</p>}
  </form>;
}
