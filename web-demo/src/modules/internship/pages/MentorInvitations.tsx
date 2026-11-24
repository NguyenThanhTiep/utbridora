import { useEffect, useRef, useState } from 'react';
import { api } from '../../../api';
import { EmptyState, Icon, Notice } from '../../../components';
import { newIdempotencyKey } from '../../student/api';
import type { ContractPage } from '../../student/types';
import { formatEntityShort, formatGroupCode, invitationStateLabel } from '../displayLabels';
import type { WorkspaceAssignment, InternshipGroup } from '../types';
type Invitation = { invitationId: string; version: number; state: string; groupIds: string[]; expiresAt: string | null };
export function MentorInvitations() {
  const [items, setItems] = useState<Invitation[]>([]), [message, setMessage] = useState(''), [busy, setBusy] = useState(false);
  const keys = useRef(new Map<string, string>());
  useEffect(() => { const c = new AbortController(); void api<ContractPage<Invitation>>('/t3/mentor-invitations/me?size=100', { signal: c.signal }).then(p => setItems(p.content)).catch(() => { if (!c.signal.aborted) setMessage('Không tải được lời mời hướng dẫn.'); }); return () => c.abort(); }, []);
  const accept = async (i: Invitation) => {
    if (!keys.current.has(i.invitationId)) keys.current.set(i.invitationId, newIdempotencyKey()); setBusy(true);
    try { await api('/t3/mentor-invitations/' + i.invitationId + '/accept', { method: 'POST', body: JSON.stringify({ expectedVersion: i.version }), headers: { 'Idempotency-Key': keys.current.get(i.invitationId)! } }); setItems(current => current.filter(v => v.invitationId !== i.invitationId)); setMessage('Đã chấp thuận hướng dẫn. Đăng nhập lại để cập nhật phân công.'); }
    catch { setMessage('Chưa chấp thuận được. Lời mời hoặc quyền của người mời có thể đã thay đổi.'); } finally { setBusy(false); }
  };
  return <section className="panel workflow-surface t3-mentor-invitations"><h2 className="t3-section-title"><Icon name="mail" />Lời mời hướng dẫn thực tập</h2><p className="muted">Kiểm tra nhóm được phân công và thời hạn trước khi chấp thuận.</p><Notice message={message} />{items.length === 0 && <EmptyState icon="mail" title="Chưa có lời mời hướng dẫn cần xử lý." />}{items.map(i => <article className="t3-invitation" key={i.invitationId}>
    <Icon name="mail" /><div><strong>{i.groupIds.length} nhóm</strong> <span className="t3-invitation-state">{invitationStateLabel(i.state)}</span>
    {i.expiresAt && <p>Hạn chấp nhận · <time dateTime={i.expiresAt}>{new Date(i.expiresAt).toLocaleString('vi-VN')}</time></p>}</div>{i.state === 'MEMBERSHIP_PENDING' && <button disabled={busy} onClick={() => void accept(i)}>Chấp thuận hướng dẫn</button>}</article>)}</section>;
}
export function MentorInvitationForm({ assignment, group, groups = [group] }: {
  assignment: WorkspaceAssignment; group: InternshipGroup; groups?: InternshipGroup[];
}) {
  const [email, setEmail] = useState(''), [decision, setDecision] = useState('');
  const [selected, setSelected] = useState<string[]>([group.groupId]);
  const [message, setMessage] = useState(''), [busy, setBusy] = useState(false);
  const [lastInvite, setLastInvite] = useState<Invitation | null>(null);
  const [revokeReason, setRevokeReason] = useState('');
  const pending = useRef<{ body: string; key: string } | null>(null);
  const revokePending = useRef<{ body: string; key: string } | null>(null);
  async function revoke() {
    if (!lastInvite || !/^[A-Z0-9_]{3,64}$/.test(revokeReason.trim())) return;
    const body = JSON.stringify({ expectedVersion: lastInvite.version, reasonCode: revokeReason.trim() });
    if (revokePending.current?.body !== body) revokePending.current = { body, key: newIdempotencyKey() };
    setBusy(true); setMessage('');
    try {
      const value = await api<Invitation>('/t3/mentor-invitations/' + lastInvite.invitationId + '/revoke', {
        method: 'POST', body: revokePending.current.body,
        headers: { 'X-Context-Assignment-Id': assignment.id, 'Idempotency-Key': revokePending.current.key }
      });
      revokePending.current = null; setLastInvite(value); setMessage('Đã thu hồi lời mời và phân công liên quan theo kết quả backend.');
    } catch { setMessage('Chưa thu hồi được. Kiểm tra trạng thái lời mời và quyền HR rồi thử lại.'); }
    finally { setBusy(false); }
  }
  return <form className="panel workflow-surface t3-mentor-invitation-form" onSubmit={e => { e.preventDefault();
    if (!selected.length) { setMessage('Chọn ít nhất một nhóm trong phạm vi HR.'); return; }
    const data = { email: email.trim(), enterpriseOrgId: assignment.scope.enterpriseOrgId,
      termId: assignment.scope.termId, groupIds: [...selected].sort(), decisionRef: decision.trim() }, body = JSON.stringify(data);
    if (pending.current?.body !== body) pending.current = { body, key: newIdempotencyKey() }; setBusy(true); setMessage('');
    void api<Invitation>('/t3/mentor-invitations', { method: 'POST', body: pending.current.body,
      headers: { 'X-Context-Assignment-Id': assignment.id, 'Idempotency-Key': pending.current.key } })
      .then(i => { pending.current = null; setLastInvite(i); setMessage(i.state === 'REVIEW_REQUIRED'
        ? 'Tài khoản cần được rà soát trước khi nhận lời mời.'
        : 'Đã gửi lời mời. Mentor cần tự chấp thuận trước khi nhận quyền.'); })
      .catch(() => setMessage('Chưa gửi được lời mời. Kiểm tra phân công và thử lại.'))
      .finally(() => setBusy(false));
  }}><h3>Mời Mentor cho nhóm {formatGroupCode(group.code)}</h3><Notice message={message} />
    <fieldset disabled={busy}><label>Email Mentor<input type="email" required value={email} onChange={e => setEmail(e.target.value)} /></label>
      <label>Căn cứ phân công<input required maxLength={120} value={decision} onChange={e => setDecision(e.target.value)} /></label>
      <fieldset><legend>Nhóm được mời hướng dẫn</legend>{groups.map(item => <label key={item.groupId}>
        <input type="checkbox" checked={selected.includes(item.groupId)}
          onChange={e => setSelected(current => e.target.checked ? [...current, item.groupId] : current.filter(id => id !== item.groupId))} />
        <span title={item.code}>{formatGroupCode(item.code)}</span></label>)}</fieldset>
      <button>Gửi lời mời hướng dẫn</button></fieldset>
    {lastInvite && <p role="status">Lời mời {formatEntityShort(lastInvite.invitationId)} · {invitationStateLabel(lastInvite.state)} · {lastInvite.groupIds.length} nhóm.</p>}
    {lastInvite && lastInvite.state !== 'REVOKED' && <div className="stack">
      <label>Mã lý do thu hồi<input value={revokeReason}
        onChange={e => { setRevokeReason(e.target.value.toUpperCase()); revokePending.current = null; }}
        pattern="[A-Z0-9_]{3,64}" minLength={3} maxLength={64} placeholder="ASSIGNMENT_CHANGED" /></label>
      <button type="button" className="secondary" disabled={busy || !/^[A-Z0-9_]{3,64}$/.test(revokeReason.trim())}
        onClick={() => void revoke()}>Thu hồi lời mời vừa gửi</button>
    </div>}
  </form>;
}
