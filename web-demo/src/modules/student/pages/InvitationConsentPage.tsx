import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api';
import { LoadingState, Notice } from '../../../components';
import { decideInvitation, getInvitation, getSharingPolicy, grantConsent, listMyConsents, listMyInvitations, newIdempotencyKey, revokeConsent, withdrawInvitation } from '../api';
import type { ConsentGrant, InternshipInvitation, InvitationState, ProfileField, SchoolSharingPolicy } from '../types';
import '../styles.css';
const stateLabels: Record<InvitationState, string> = { PENDING: 'Chờ phản hồi', ACCEPTED: 'Đã nhận lời', DECLINED: 'Đã từ chối', EXPIRED: 'Đã hết hạn', WITHDRAWN: 'Đã rút nhận lời' };
const reasonLabels: Record<string, string> = { STUDENT_DECLINED: 'Sinh viên từ chối', STUDENT_WITHDREW: 'Sinh viên rút nhận lời' };
const labels: Record<ProfileField, string> = { majorCode: 'Ngành', skills: 'Kỹ năng', projects: 'Dự án', certificates: 'Chứng chỉ', cv: 'CV', evidence: 'Minh chứng', displayName: 'Họ tên', studentCode: 'Mã sinh viên', email: 'Email', phone: 'Điện thoại', gpa: 'Điểm trung bình', transcript: 'Bảng điểm' };
// Mã lỗi theo BC23/BC24 và ledger dùng chung; không hiện nguyên văn message của server.
const errorMessages: Record<string, string> = {
  INVITATION_EXPIRED: 'Lời mời đã hết hạn phản hồi.',
  INVITATION_STATE_CONFLICT: 'Lời mời vừa đổi trạng thái; đã tải lại trạng thái mới.',
  PLACEMENT_ALREADY_EXISTS: 'Lời mời đã có Placement nên không thể rút nhận lời.',
  VERSION_CONFLICT: 'Dữ liệu vừa thay đổi; đã tải lại bản mới nhất, kiểm tra rồi thao tác lại.',
  CONSENT_CHANGED: 'Quyền chia sẻ vừa thay đổi; đã tải lại, lựa chọn của bạn vẫn được giữ.',
  CONSENT_INVALID: 'Quyền chia sẻ này không còn hiệu lực để thu hồi.',
  CONSENT_EXPIRY_INVALID: 'Thời hạn chia sẻ phải từ 1 đến 30 ngày.',
  CONSENT_FIELDS_NOT_ALLOWED: 'Có mục nhà trường không còn cho phép chia sẻ; đã tải lại chính sách.',
  REQUEST_IN_PROGRESS: 'Yêu cầu trước đang được xử lý. Đợi giây lát rồi tải lại.'
};
// Các lỗi này nghĩa là dữ liệu trên màn đã cũ: tải lại nhưng giữ lựa chọn đang nhập.
const STALE_CODES = new Set(['INVITATION_EXPIRED', 'INVITATION_STATE_CONFLICT', 'PLACEMENT_ALREADY_EXISTS', 'VERSION_CONFLICT', 'CONSENT_CHANGED', 'CONSENT_INVALID', 'CONSENT_FIELDS_NOT_ALLOWED']);
const messageOf = (error: unknown) => !(error instanceof ApiError) ? (error instanceof Error ? error.message : 'Thao tác chưa hoàn tất.')
  : (error.errorCode && errorMessages[error.errorCode]) || error.message + (error.requestId ? ' Mã yêu cầu: ' + error.requestId + '.' : '');
const time = (value: string) => new Date(value).toLocaleString('vi-VN');
const consentStatus = (c: ConsentGrant) => c.revokedAt ? 'Đã thu hồi' : c.replacedByConsentId ? 'Đã được thay thế' : Date.parse(c.expiresAt) <= Date.now() ? 'Đã hết hạn' : 'Đang có hiệu lực';
type Command = (operation: string, body: unknown, send: (key: string) => Promise<unknown>) => Promise<boolean>;
export function InvitationConsentPage() {
  const [invitations, setInvitations] = useState<InternshipInvitation[] | null>(null), [consents, setConsents] = useState<ConsentGrant[]>([]), [refresh, setRefresh] = useState(0);
  const [message, setMessage] = useState(''), [busy, setBusy] = useState(false), [loadFailed, setLoadFailed] = useState(false);
  const pending = useRef(new Map<string, string>());
  useEffect(() => {
    const c = new AbortController();
    void Promise.all([listMyInvitations(c.signal), listMyConsents(c.signal)]).then(([i, s]) => { if (!c.signal.aborted) { setInvitations(i); setConsents(s); setLoadFailed(false); } })
      .catch(() => { if (!c.signal.aborted) { setLoadFailed(true); setMessage('Không tải được lời mời hoặc quyền chia sẻ.'); } });
    return () => c.abort();
  }, [refresh]);
  // Cùng thao tác, cùng nội dung thì dùng lại Idempotency-Key để thử lại an toàn khi chưa rõ kết quả.
  const command: Command = async (operation, body, send) => {
    const ref = operation + JSON.stringify(body); if (!pending.current.has(ref)) pending.current.set(ref, newIdempotencyKey());
    setBusy(true); setMessage('');
    try { await send(pending.current.get(ref)!); pending.current.delete(ref); setRefresh(n => n + 1); return true; }
    catch (error) { setMessage(messageOf(error)); if (error instanceof ApiError && error.errorCode && STALE_CODES.has(error.errorCode)) setRefresh(n => n + 1); return false; }
    finally { setBusy(false); }
  };
  const decide = (i: InternshipInvitation, decision: 'ACCEPT' | 'DECLINE') => {
    const body = { decision, expectedVersion: i.version, reasonCode: decision === 'DECLINE' ? 'STUDENT_DECLINED' : null, reasonText: null };
    void command('decision:' + i.invitationId, body, key => decideInvitation(i.invitationId, body, key));
  };
  const withdraw = (i: InternshipInvitation) => {
    const body = { expectedVersion: i.version, reasonCode: 'STUDENT_WITHDREW', reasonText: null };
    void command('withdraw:' + i.invitationId, body, key => withdrawInvitation(i.invitationId, body, key));
  };
  const revoke = (c: ConsentGrant) => {
    const body = { expectedLineageVersion: c.lineageVersion, reasonCode: 'STUDENT_REVOKED' };
    void command('revoke:' + c.consentId, body, key => revokeConsent(c.consentId, body, key));
  };
  return <div className="stack workflow-surface"><Notice message={message} tone="error" /><button className="secondary" disabled={busy} onClick={() => { setMessage(''); setRefresh(n => n + 1); }}>Tải lại lời mời</button>
    {invitations === null && !loadFailed && <LoadingState label="Đang tải lời mời…" />}
    {invitations?.length === 0 && <section className="panel"><h2>Chưa có lời mời thực tập</h2></section>}
    {invitations?.map(i => { const open = i.state === 'PENDING' && Date.now() < Date.parse(i.expiresAt);
      return <section className="panel" key={i.invitationId}><h2>{i.title}</h2><p>{i.enterpriseName} · {stateLabels[i.state] || i.state}</p><p>Hạn phản hồi: {time(i.expiresAt)}</p>
        {i.state === 'PENDING' && !open && <p>Đã quá hạn phản hồi.</p>}
        {open && <div className="button-row"><button disabled={busy} onClick={() => decide(i, 'ACCEPT')}>Nhận lời mời</button>
          <button className="secondary" disabled={busy} onClick={() => { if (window.confirm('Từ chối lời mời này? Sau khi từ chối bạn không thể nhận lại lời mời.')) decide(i, 'DECLINE'); }}>Từ chối</button></div>}
        {i.state === 'ACCEPTED' && (i.placementId ? <p>Đã có Placement nên không thể rút nhận lời.</p>
          : <button className="secondary" disabled={busy} onClick={() => { if (window.confirm('CẢNH BÁO: Rút nhận lời sẽ HỦY BỎ cơ hội thực tập này và không thể hoàn tác. Bạn có chắc chắn muốn rút?')) withdraw(i); }}>Rút nhận lời</button>)}
        <p>Để chỉ thu hồi quyền xem thông tin cá nhân của doanh nghiệp mà vẫn giữ vị trí thực tập, hãy chọn “Thu hồi quyền chia sẻ” bên dưới.</p>
        <InvitationDetail key={i.invitationId + ':' + i.version} invitationId={i.invitationId} />
        {(open || i.state === 'ACCEPTED') && <ConsentForm key={i.invitationId} invitation={i} consents={consents} busy={busy} refresh={refresh} command={command} />}
      </section>; })}
    <section className="panel"><h2>Quyền chia sẻ đã cấp</h2>{consents.length === 0 && <p>Chưa cấp quyền chia sẻ.</p>}{consents.map(c => { const status = consentStatus(c);
      return <article key={c.consentId}><p>{invitations?.find(i => i.invitationId === c.invitationId)?.enterpriseName ?? 'Doanh nghiệp'}: {c.fieldWhitelist.map(f => labels[f] || f).join(', ')} · hết hạn {time(c.expiresAt)}</p>
        <p>{status}</p>
        {status === 'Đang có hiệu lực' && <button className="secondary" disabled={busy} onClick={() => revoke(c)}>Thu hồi quyền chia sẻ</button>}</article>; })}</section>
  </div>;
}
// S06-I: đọc lại chi tiết mới nhất từ T5 khi sinh viên mở; đổi version thì đóng lại.
function InvitationDetail({ invitationId }: { invitationId: string }) {
  const [detail, setDetail] = useState<InternshipInvitation | 'loading' | 'error' | null>(null);
  const toggle = () => {
    if (detail !== null) { setDetail(null); return; }
    setDetail('loading'); void getInvitation(invitationId).then(setDetail).catch(() => setDetail('error'));
  };
  return <div className="student-invitation-detail"><button type="button" className="text-button" aria-expanded={detail !== null} onClick={toggle}>{detail === null ? 'Xem chi tiết' : 'Ẩn chi tiết'}</button>
    {detail === 'loading' && <LoadingState label="Đang tải chi tiết…" />}
    {detail === 'error' && <p className="student-error-note">Không tải được chi tiết lời mời. Ẩn rồi mở lại để thử lại.</p>}
    {typeof detail === 'object' && detail !== null && <dl>
      <div><dt>Doanh nghiệp</dt><dd>{detail.enterpriseName}</dd></div>
      <div><dt>Trạng thái</dt><dd>{stateLabels[detail.state] || detail.state}</dd></div>
      <div><dt>Hạn phản hồi</dt><dd>{time(detail.expiresAt)}</dd></div>
      <div><dt>Phản hồi lúc</dt><dd>{detail.decidedAt ? time(detail.decidedAt) : 'Chưa phản hồi'}</dd></div>
      {detail.decisionReasonCode && <div><dt>Lý do</dt><dd>{reasonLabels[detail.decisionReasonCode] || detail.decisionReasonCode}</dd></div>}
      <div><dt>Placement</dt><dd>{detail.placementId ? 'Đã có Placement' : 'Chưa có Placement'}</dd></div>
    </dl>}</div>;
}
function ConsentForm({ invitation, consents, busy, refresh, command }: { invitation: InternshipInvitation; consents: ConsentGrant[]; busy: boolean; refresh: number; command: Command }) {
  const [policy, setPolicy] = useState<SchoolSharingPolicy | null>(null), [selected, setSelected] = useState<ProfileField[]>([]), [days, setDays] = useState(7);
  // Tải lại chính sách cùng trang để sau VERSION_CONFLICT/CONSENT_FIELDS_NOT_ALLOWED dùng bản mới nhất.
  useEffect(() => { const c = new AbortController(); void getSharingPolicy(invitation.termId, c.signal).then(setPolicy).catch(() => { if (!c.signal.aborted) setPolicy(null); }); return () => c.abort(); }, [invitation.termId, refresh]);
  const expiry = useRef<{ days: number; value: string } | null>(null);
  const chosen = selected.filter(f => policy?.allowedFields.includes(f)), validDays = Number.isInteger(days) && days >= 1 && days <= 30;
  // Lineage dùng chung theo trường/kỳ/doanh nghiệp; không đặt lại về 0 cho từng lời mời.
  const lineage = Math.max(0, ...consents.filter(c => c.recipientOrgId === invitation.enterpriseOrgId && c.schoolOrgId === invitation.schoolOrgId && c.termId === invitation.termId).map(c => c.lineageVersion));
  return <form onSubmit={e => { e.preventDefault(); if (policy && chosen.length && validDays) {
    if (expiry.current?.days !== days) expiry.current = { days, value: new Date(Date.now() + days * 86400000).toISOString() };
    // policyVersion theo ví dụ contract (docs/contracts/v1.1/examples.json); chờ DEV01 chốt mã chính thức.
    const body = { invitationId: invitation.invitationId, purpose: 'INTERNSHIP_SELECTION' as const, fieldWhitelist: chosen, expiresAt: expiry.current.value, policyVersion: 'demo-consent-v1', expectedSchoolPolicyVersion: policy.version, expectedLineageVersion: lineage };
    void command('consent:' + invitation.invitationId, body, key => grantConsent(body, key)).then(ok => { if (ok) { setSelected([]); expiry.current = null; } });
  } }}>
    <h3>Quyết định chia sẻ hồ sơ riêng</h3><p>Bạn cho phép nhà trường chia sẻ các thông tin đã chọn với {invitation.enterpriseName} để đánh giá thực tập. Nhà trường sẽ cấp hoặc cập nhật chia sẻ sau khi bạn đồng ý. Nhận lời mời không tự cấp quyền xem hồ sơ cho doanh nghiệp.</p>
    <fieldset disabled={busy || !policy}>{policy?.allowedFields.map(f => <label key={f}><input type="checkbox" checked={selected.includes(f)} onChange={e => setSelected(e.target.checked ? [...selected, f] : selected.filter(v => v !== f))} />{labels[f] || f}</label>)}
      <label>Số ngày hiệu lực<input type="number" min="1" max="30" required value={days} onChange={e => setDays(Number(e.target.value))} /></label>
      <button disabled={!chosen.length || !validDays}>Đồng ý chia sẻ các mục đã chọn</button>
    </fieldset>{!policy && <p>Chưa đọc được chính sách chia sẻ hiện hành.</p>}
  </form>;
}
