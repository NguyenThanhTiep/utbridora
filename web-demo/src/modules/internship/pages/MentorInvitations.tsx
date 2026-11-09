import { useEffect, useRef, useState } from 'react';
import { api } from '../../../api';
import { Notice } from '../../../components';
import { newIdempotencyKey } from '../../student/api';
import type { ContractPage } from '../../student/types';
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
  return <section className="panel workflow-surface"><h2>Lời mời hướng dẫn thực tập</h2><Notice message={message} />{items.length === 0 && <p>Chưa có lời mời hướng dẫn cần xử lý.</p>}{items.map(i => <article key={i.invitationId}><p>{i.groupIds.length} nhóm · {i.state}{i.expiresAt ? ' · hạn ' + new Date(i.expiresAt).toLocaleString('vi-VN') : ''}</p>{i.state === 'MEMBERSHIP_PENDING' && <button disabled={busy} onClick={() => void accept(i)}>Chấp thuận hướng dẫn</button>}</article>)}</section>;
}
export function MentorInvitationForm({ assignment, group }: { assignment: WorkspaceAssignment; group: InternshipGroup }) {
  const [email, setEmail] = useState(''), [decision, setDecision] = useState(''), [message, setMessage] = useState(''), [busy, setBusy] = useState(false);
  const pending = useRef<{ body: string; key: string } | null>(null);
  return <form className="panel workflow-surface" onSubmit={e => { e.preventDefault();
    const data = { email, enterpriseOrgId: assignment.scope.enterpriseOrgId, termId: assignment.scope.termId, groupIds: [group.groupId], decisionRef: decision }, body = JSON.stringify(data);
    if (pending.current?.body !== body) pending.current = { body, key: newIdempotencyKey() }; setBusy(true); setMessage('');
    void api<Invitation>('/t3/mentor-invitations', { method: 'POST', body, headers: { 'X-Context-Assignment-Id': assignment.id, 'Idempotency-Key': pending.current.key } }).then(i => { pending.current = null; setMessage(i.state === 'REVIEW_REQUIRED' ? 'Tài khoản cần được rà soát trước khi nhận lời mời.' : 'Đã gửi lời mời. Mentor cần tự chấp thuận trước khi nhận quyền.'); }).catch(() => setMessage('Chưa gửi được lời mời. Kiểm tra phân công và thử lại.')).finally(() => setBusy(false));
  }}><h3>Mời Mentor cho nhóm {group.code}</h3><Notice message={message} /><fieldset disabled={busy}><label>Email Mentor<input type="email" required value={email} onChange={e => setEmail(e.target.value)} /></label><label>Căn cứ phân công<input required maxLength={120} value={decision} onChange={e => setDecision(e.target.value)} /></label><button>Gửi lời mời hướng dẫn</button></fieldset></form>;
}
