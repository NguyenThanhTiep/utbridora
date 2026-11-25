import { useViewRouting } from '../../../routing/BrowserRouting';
import { useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api';
import { Icon, LoadingState, Notice } from '../../../components';
import { askConfirm } from '../../../confirm';
import { StudentNote, orgInitials } from '../components/StudentUI';
import { decideInvitation, getInvitation, getSharingPolicy, grantConsent, listMyConsents, listMyInvitations, newIdempotencyKey, revokeConsent, withdrawInvitation } from '../api';
import type { ConsentGrant, InternshipInvitation, InvitationState, ProfileField, SchoolSharingPolicy } from '../types';
import '../styles.css';
const stateLabels: Record<InvitationState, string> = { PENDING: 'Chờ phản hồi', ACCEPTED: 'Đã nhận lời', DECLINED: 'Đã từ chối', EXPIRED: 'Đã hết hạn', WITHDRAWN: 'Đã rút nhận lời' };
const stateTones: Record<InvitationState, string> = { PENDING: 'tone-neutral', ACCEPTED: 'tone-success', DECLINED: 'tone-neutral', EXPIRED: 'tone-neutral', WITHDRAWN: 'tone-neutral' };
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
// Thời gian còn lại để phản hồi, tính theo giờ máy từ expiresAt của lời mời.
const timeLeft = (expiresAt: string) => { const hours = Math.floor((Date.parse(expiresAt) - Date.now()) / 3_600_000);
  return hours >= 48 ? 'Còn ' + Math.floor(hours / 24) + ' ngày' : hours >= 1 ? 'Còn ' + hours + ' giờ' : 'Còn dưới 1 giờ'; };
type Step = { label: string; value: string; tone: 'done' | 'current' | 'idle'; icon: string };
// Dải tình trạng của một lời mời, chỉ suy từ dữ liệu đã đọc: trạng thái lời mời (BC23), quyền chia sẻ còn hiệu lực gắn với lời mời (BC24)
// và placementId. Ba ô là tình trạng song song, không phải thứ tự bắt buộc: chia sẻ hồ sơ là thao tác riêng, không điều kiện Placement.
function steps(i: InternshipInvitation, open: boolean, shared: boolean): Step[] {
  const answer: Step = open ? { label: 'Phản hồi', value: 'Chờ bạn phản hồi', tone: 'current', icon: 'clock' }
    : i.state === 'ACCEPTED' ? { label: 'Phản hồi', value: 'Đã nhận lời', tone: 'done', icon: 'check-circle' }
    : { label: 'Phản hồi', value: i.state === 'DECLINED' ? 'Đã từ chối' : i.state === 'WITHDRAWN' ? 'Đã rút nhận lời' : 'Đã hết hạn', tone: 'idle', icon: 'close' };
  const active = open || i.state === 'ACCEPTED';
  return [answer,
    shared ? { label: 'Chia sẻ hồ sơ', value: 'Đã đồng ý chia sẻ', tone: 'done', icon: 'shield' } : { label: 'Chia sẻ hồ sơ', value: active ? 'Chưa chia sẻ' : 'Không áp dụng', tone: 'idle', icon: 'shield' },
    i.placementId ? { label: 'Placement', value: 'Đã tạo', tone: 'done', icon: 'briefcase' }
      : { label: 'Placement', value: i.state === 'ACCEPTED' ? 'Chờ nhà trường tạo' : 'Không áp dụng', tone: i.state === 'ACCEPTED' ? 'current' : 'idle', icon: 'briefcase' }];
}
type Command = (operation: string, body: unknown, send: (key: string) => Promise<unknown>) => Promise<boolean>;
export function InvitationConsentPage() {
  const routing=useViewRouting('student-invitations');
  const selectedId=routing?.route.studentTab!=='preferences'?routing?.route.detailId:undefined;
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
  const isOpen = (i: InternshipInvitation) => i.state === 'PENDING' && Date.now() < Date.parse(i.expiresAt);
  const isActive = (c: ConsentGrant) => consentStatus(c) === 'Đang có hiệu lực';
  // Frame 477:11949 (lời mời) và 477:12029 (quyền chia sẻ) dựng thành "thư mời thực tập": thanh tóm tắt + nút tải lại ở đầu;
  // mỗi lời mời là một thẻ thư có logo chữ doanh nghiệp, viền theo trạng thái (vàng: cần phản hồi, xanh: đã nhận, xám: đã đóng),
  // đếm ngược hạn phản hồi, dải 3 ô tình trạng, nút phản hồi, khối cấp quyền riêng. Các thẻ lời mời là section.panel con trực tiếp
  // của .workflow-surface theo thứ tự API (kiểm thử runtime chọn theo vị trí) nên thanh tóm tắt không dùng section.panel.
  return <div className="stack workflow-surface student-invitations"><Notice message={message} tone="error" />
    <div className="student-invite-summary">
      {invitations && invitations.length > 0 && <ul className="student-invite-stats" aria-label="Tóm tắt lời mời">
        <li><Icon name="mail" size={16} /><b>{invitations.length}</b> lời mời</li>
        <li className="tone-amber"><Icon name="clock" size={16} /><b>{invitations.filter(isOpen).length}</b> chờ phản hồi</li>
        <li className="tone-green"><Icon name="shield" size={16} /><b>{consents.filter(isActive).length}</b> quyền chia sẻ còn hiệu lực</li></ul>}
      <button className="secondary" disabled={busy} onClick={() => { setMessage(''); setRefresh(n => n + 1); }}><Icon name="refresh" size={16} />Tải lại lời mời</button></div>
    {invitations === null && !loadFailed && <LoadingState label="Đang tải lời mời…" />}
    {invitations?.length === 0 && <section className="panel student-invite-empty"><span className="student-invite-empty-icon" aria-hidden="true"><Icon name="mail" size={26} /></span>
      <h2>Chưa có lời mời thực tập</h2><p className="student-list-meta">Khi có lời mời thực tập dành cho bạn, lời mời sẽ hiện ở đây để bạn xem và phản hồi.</p></section>}
    {selectedId && invitations && !loadFailed && !invitations.some(i=>i.invitationId===selectedId) && <Notice message="Lời mời không tồn tại hoặc không còn thuộc phạm vi được phép xem." tone="error" />}
    {invitations?.filter(i=>!selectedId || i.invitationId===selectedId).map(i => { const open = isOpen(i), shared = consents.some(c => c.invitationId === i.invitationId && isActive(c));
      return <section className={'panel student-offer ' + (open ? 'is-open' : i.state === 'ACCEPTED' ? 'is-accepted' : 'is-closed')} key={i.invitationId}>
        <div className="student-offer-head">
          <span className="student-offer-logo" aria-hidden="true">{orgInitials(i.enterpriseName)}</span>
          <div className="student-offer-title"><h2>{i.title}</h2>
            <p className="student-invite-meta">{i.enterpriseName} · <span className={'student-status ' + (open ? 'tone-warning' : stateTones[i.state])}>{stateLabels[i.state] || i.state}</span></p></div>
          {open && <span className="student-offer-countdown"><Icon name="clock" size={15} />{timeLeft(i.expiresAt)}</span>}
        </div>
        <p className="student-invite-deadline"><Icon name="calendar" size={14} />Hạn phản hồi: {time(i.expiresAt)}</p>
        {i.state === 'PENDING' && !open && <p className="student-list-meta">Đã quá hạn phản hồi.</p>}
        <ul className="student-offer-steps" aria-label="Tình trạng lời mời">{steps(i, open, shared).map(step => <li key={step.label} className={'is-' + step.tone}>
          <span className="student-offer-step-icon" aria-hidden="true"><Icon name={step.icon} size={16} /></span><span><small>{step.label}</small><b>{step.value}</b></span></li>)}</ul>
        {open && <div className="button-row student-offer-actions"><button className="student-btn-wide" disabled={busy} onClick={() => decide(i, 'ACCEPT')}><Icon name="check" size={17} />Nhận lời mời</button>
          <button className="secondary student-btn-wide" disabled={busy} onClick={async () => { if (await askConfirm({ title: 'Từ chối lời mời?', message: 'Từ chối lời mời này? Sau khi từ chối bạn không thể nhận lại lời mời.', confirmLabel: 'Từ chối lời mời', tone: 'danger' })) decide(i, 'DECLINE'); }}>Từ chối</button></div>}
        {i.state === 'ACCEPTED' && (i.placementId ? <p className="student-list-meta">Đã có Placement nên không thể rút nhận lời.</p>
          : <div className="button-row student-offer-actions"><button className="secondary student-btn-wide student-danger-outline" disabled={busy} onClick={async () => { if (await askConfirm({ title: 'Rút nhận lời?', message: 'CẢNH BÁO: Rút nhận lời sẽ HỦY BỎ cơ hội thực tập này và không thể hoàn tác. Bạn có chắc chắn muốn rút?', confirmLabel: 'Rút nhận lời', tone: 'danger' })) withdraw(i); }}>Rút nhận lời</button></div>)}
        {(open || i.state === 'ACCEPTED') && <StudentNote>Để chỉ thu hồi quyền xem thông tin cá nhân của doanh nghiệp mà vẫn giữ vị trí thực tập, hãy chọn “Thu hồi quyền chia sẻ” bên dưới.</StudentNote>}
        <InvitationDetail key={i.invitationId + ':' + i.version} invitationId={i.invitationId} />
        {(open || i.state === 'ACCEPTED') && <ConsentForm key={i.invitationId} invitation={i} consents={consents} busy={busy} refresh={refresh} command={command} />}
      </section>; })}
    {/* Sổ quyền chia sẻ: mỗi quyền là thẻ có khiên, doanh nghiệp + mục chia sẻ + hạn, nhãn trạng thái, nút thu hồi (chỉ khi còn hiệu lực). */}
    <section className="panel student-consent-ledger"><h2>Quyền chia sẻ đã cấp</h2>{consents.length === 0 && <p className="student-list-meta">Chưa cấp quyền chia sẻ.</p>}{consents.map(c => { const status = consentStatus(c);
      return <article className={'student-row student-consent' + (status === 'Đang có hiệu lực' ? ' is-active' : '')} key={c.consentId}>
        <span className="student-consent-icon" aria-hidden="true"><Icon name="shield" size={18} /></span>
        <p><b>{invitations?.find(i => i.invitationId === c.invitationId)?.enterpriseName ?? 'Doanh nghiệp'}</b>: {c.fieldWhitelist.map(f => labels[f] || f).join(', ')} · hết hạn {time(c.expiresAt)}</p>
        <p className={'student-status ' + (status === 'Đang có hiệu lực' ? 'tone-success' : 'tone-neutral')}>{status}</p>
        {status === 'Đang có hiệu lực' && <button className="secondary student-danger-outline" disabled={busy} onClick={() => revoke(c)}>Thu hồi quyền chia sẻ</button>}</article>; })}
      <StudentNote tone="warning">Hồ sơ không được chia sẻ nếu bạn từ chối, thu hồi quyền, quyền hết hạn hoặc chưa chọn thông tin cho phép.</StudentNote></section>
  </div>;
}
// S06-I: đọc lại chi tiết mới nhất từ T5 khi sinh viên mở; đổi version thì đóng lại.
function InvitationDetail({ invitationId }: { invitationId: string }) {
  const routing=useViewRouting('student-invitations');
  const [localOpen,setLocalOpen]=useState(false),[detail,setDetail]=useState<InternshipInvitation | 'loading' | 'error' | null>(null);
  const open=routing?routing.route.detailId===invitationId:localOpen;
  useEffect(()=>{
    if(!open){setDetail(null);return;}
    const c=new AbortController();setDetail('loading');
    void getInvitation(invitationId,c.signal).then(value=>{if(!c.signal.aborted)setDetail(value);}).catch(()=>{if(!c.signal.aborted)setDetail('error');});
    return ()=>c.abort();
  },[invitationId,open]);
  const toggle=()=>{if(routing)routing.go({...routing.route,detailId:open?undefined:invitationId});else setLocalOpen(!open);};
  return <div className="student-invitation-detail"><button type="button" className="text-button student-detail-toggle" aria-expanded={open} onClick={toggle}>{open ? 'Ẩn chi tiết' : 'Xem chi tiết'}<Icon name="chevron-down" size={15} /></button>
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
  return <form className="student-consent-form" onSubmit={e => { e.preventDefault(); if (policy && chosen.length && validDays) {
    if (expiry.current?.days !== days) expiry.current = { days, value: new Date(Date.now() + days * 86400000).toISOString() };
    // policyVersion theo ví dụ contract (docs/contracts/v1.1/examples.json); chờ DEV01 chốt mã chính thức.
    const body = { invitationId: invitation.invitationId, purpose: 'INTERNSHIP_SELECTION' as const, fieldWhitelist: chosen, expiresAt: expiry.current.value, policyVersion: 'demo-consent-v1', expectedSchoolPolicyVersion: policy.version, expectedLineageVersion: lineage };
    void command('consent:' + invitation.invitationId, body, key => grantConsent(body, key)).then(ok => { if (ok) { setSelected([]); expiry.current = null; } });
  } }}>
    <h3><span className="student-consent-icon" aria-hidden="true"><Icon name="shield" size={18} /></span>Quyết định chia sẻ hồ sơ riêng</h3><p className="student-list-meta">Bạn cho phép nhà trường chia sẻ các thông tin đã chọn với {invitation.enterpriseName} để đánh giá thực tập. Nhà trường sẽ cấp hoặc cập nhật chia sẻ sau khi bạn đồng ý. Nhận lời mời không tự cấp quyền xem hồ sơ cho doanh nghiệp.</p>
    <fieldset className="student-consent-fields" disabled={busy || !policy}><p className="student-consent-label">Chọn những thông tin muốn chia sẻ (không mục nào được chọn sẵn)</p>
      {policy?.allowedFields.map(f => <label key={f}><input type="checkbox" checked={selected.includes(f)} onChange={e => setSelected(e.target.checked ? [...selected, f] : selected.filter(v => v !== f))} />{labels[f] || f}</label>)}
      <label className="student-consent-days">Số ngày hiệu lực<input type="number" min="1" max="30" required value={days} onChange={e => setDays(Number(e.target.value))} /></label>
      <p className="student-list-meta">Chọn từ 1 đến 30 ngày. Sau khi cấp quyền, bạn có thể thu hồi bất cứ lúc nào; thu hồi chặn các lần truy cập tiếp theo.</p>
      <button className="student-btn-wide" disabled={!chosen.length || !validDays}>Đồng ý chia sẻ các mục đã chọn</button>
    </fieldset>{!policy && <p className="student-list-meta">Chưa đọc được chính sách chia sẻ hiện hành.</p>}
  </form>;
}
