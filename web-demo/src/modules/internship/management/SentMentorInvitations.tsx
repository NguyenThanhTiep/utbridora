import { useEffect, useState } from 'react';
import { ApiError, type Grant, type Me } from '../../../api';
import { Notice } from '../../../components';
import type { ContextAssignment, WorkspacePage } from '../types';
import { accessLost } from '../workflow-api';
import { invitationStateLabel } from '../displayLabels';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { ManagementPager, dateLabel, managementBase, shortId, useManagementRead, type GroupOption } from './read';
import './assignment-management.css';
type Invitation = { invitationId: string; version: number; state: string; enterpriseOrgId: string; termId: string; groupIds: string[]; expiresAt: string | null };
type Sent = { invitation: Invitation; email: string; recipientUserId: string | null };
const lost=(e:unknown)=>accessLost(e) || e instanceof ApiError && e.status===404;
export function SentMentorInvitations({me,grants}:{me:Me;grants:Grant[]}) {
  const [revision,setRevision]=useState(0),[selected,setSelected]=useState(''),[locked,setLocked]=useState(false),[now,setNow]=useState(Date.now());
  const assignments=useManagementRead<ContextAssignment[]>('/t3/context-assignments/me',undefined,revision);
  const usable=assignments.data?.filter(a=>a.kind==='HR' && a.userId===me.userId && a.active && !a.scope.groupId && !a.scope.caseId && Date.parse(a.validFrom)<=now && now<Date.parse(a.validUntil)
    && grants.some(g=>g.role==='ENTERPRISE' && g.organizationId===a.scope.enterpriseOrgId && g.permissions.includes('t3:mentor:invite'))) || [];
  const assignment=usable.find(a=>a.id===selected);
  useEffect(()=>{const timer=window.setInterval(()=>setNow(Date.now()),1000);return ()=>window.clearInterval(timer);},[]);
  return <div className="stack assignment-management"><section className="panel stack"><h2>Lời mời Mentor đã gửi</h2><p>Tra cứu lời mời do bạn gửi trong phân công HR toàn phạm vi. Mentor cần tự chấp thuận trước khi nhận quyền.</p>
    <Notice message={assignments.error} tone="error" /><label>Phân công HR quản lý lời mời<select aria-label="Phân công HR quản lý lời mời" value={assignment?.id || ''} disabled={locked || assignments.loading} onChange={e=>setSelected(e.target.value)}><option value="">Chọn phân công HR…</option>{usable.map(a=><option key={a.id} value={a.id}>Trường {shortId(a.scope.schoolOrgId!)} · DN {shortId(a.scope.enterpriseOrgId!)} · Kỳ {shortId(a.scope.termId!)}</option>)}</select></label>
    {assignments.data && !usable.length && <p>Chưa có phân công HR toàn phạm vi còn hiệu lực và quyền mời Mentor.</p>}
    <button className="secondary" disabled={locked} onClick={()=>{setSelected('');setRevision(n=>n+1);}}>Xác nhận lại quyền HR</button>
  </section>{assignment && <InvitationScope key={assignment.id+':'+revision} assignment={assignment} lock={setLocked} denied={()=>{setSelected('');setRevision(n=>n+1);}} />}</div>;
}
function InvitationScope({assignment,lock,denied}:{assignment:ContextAssignment;lock:(v:boolean)=>void;denied:()=>void}) {
  const [page,setPage]=useState(0),[revision,setRevision]=useState(0),[selected,setSelected]=useState(''),[reason,setReason]=useState(''),[confirmed,setConfirmed]=useState(false),[notice,setNotice]=useState(''),[stale,setStale]=useState(false);
  const [email,setEmail]=useState(''),[decision,setDecision]=useState(''),[groupIds,setGroupIds]=useState<string[]>([]);
  const query=new URLSearchParams({enterpriseOrgId:assignment.scope.enterpriseOrgId!,termId:assignment.scope.termId!}).toString();
  const base='/t3/mentor-invitations/sent';
  const rows=useManagementRead<WorkspacePage<Sent>>(base+'?'+query+'&page='+page,assignment.id,revision);
  const detail=useManagementRead<Sent>(selected?base+'/'+selected+'?'+query:null,assignment.id,revision);
  const groups=useManagementRead<WorkspacePage<GroupOption>>(managementBase+'/groups?'+query+'&schoolOrgId='+assignment.scope.schoolOrgId+'&size=100',assignment.id,revision);
  const [groupPage,setGroupPage]=useState(0);
  const moreGroups=useManagementRead<WorkspacePage<GroupOption>>(groupPage?managementBase+'/groups?'+query+'&schoolOrgId='+assignment.scope.schoolOrgId+'&size=100&page='+groupPage:null,assignment.id,revision);
  const [options,setOptions]=useState<GroupOption[]>([]);
  useEffect(()=>{setOptions(groups.data?.content || []);},[groups.data]);
  useEffect(()=>{if(moreGroups.data)setOptions(old=>[...new Map([...old,...moreGroups.data!.content].map(g=>[g.groupId,g])).values()]);},[moreGroups.data]);
  const command=useWorkflowCommand(assignment.id,denied,lost),busy=command.busy || command.pending;
  useEffect(()=>{lock(busy);return ()=>lock(false);},[busy,lock]);
  useEffect(()=>{if([rows.status,detail.status,groups.status,moreGroups.status].some(s=>s===403 || s===404))denied();},[rows.status,detail.status,groups.status,moreGroups.status]);
  useEffect(()=>{if(command.failureStatus===409)setStale(true);},[command.failureStatus]);
  useEffect(()=>{if(detail.data)setStale(false);},[detail.data]);
  useEffect(()=>{const refresh=()=>{if(!document.hidden && !busy){setConfirmed(false);setRevision(n=>n+1);}};window.addEventListener('focus',refresh);return ()=>window.removeEventListener('focus',refresh);},[busy]);
  const invitation=detail.data?.invitation;
  const validDetail=detail.data && invitation?.invitationId===selected && invitation.enterpriseOrgId===assignment.scope.enterpriseOrgId && invitation.termId===assignment.scope.termId;
  const changed=(message:string)=>{setNotice(message);setConfirmed(false);setSelected('');setRevision(n=>n+1);setGroupPage(0);};
  return <>
    <section className="panel stack"><h3>Danh sách lời mời đã gửi</h3><Notice message={rows.error || detail.error} tone="error" /><Notice message={notice} /><CommandFeedback command={command} />
      {rows.loading && <p role="status">Đang tải lời mời…</p>}
      <div className="assignment-table"><table><thead><tr><th>Người nhận</th><th>Trạng thái</th><th>Nhóm / thời hạn</th><th>Thao tác</th></tr></thead><tbody>{rows.data?.content.map(row=><tr key={row.invitation.invitationId}><td>{row.email}</td><td>{invitationStateLabel(row.invitation.state)}</td><td>{row.invitation.groupIds.length} nhóm<small>{row.invitation.expiresAt?dateLabel(row.invitation.expiresAt):'Cần rà soát danh tính'}</small></td><td><button className="secondary" disabled={busy} onClick={()=>{setSelected(row.invitation.invitationId);setConfirmed(false);setReason('');setRevision(n=>n+1);}}>Chi tiết lời mời</button></td></tr>)}</tbody></table></div>
      {rows.data?.totalElements===0 && <p>Chưa có lời mời do bạn gửi trong phạm vi này.</p>}<ManagementPager data={rows.data} disabled={busy || rows.loading} change={p=>{setSelected('');setPage(p);}} />
      <button className="secondary" disabled={busy} onClick={()=>{setConfirmed(false);command.setMessage('');setRevision(n=>n+1);setGroupPage(0);}}>Tải phiên bản lời mời hiện tại</button>
      {validDetail && invitation && <section className="assignment-detail stack" aria-label="Chi tiết lời mời"><h4>{detail.data!.email}</h4><dl><dt>Mã lời mời</dt><dd>{invitation.invitationId}</dd><dt>Trạng thái</dt><dd>{invitationStateLabel(invitation.state)}</dd><dt>Phiên bản</dt><dd>{invitation.version}</dd><dt>Nhóm hướng dẫn</dt><dd>{invitation.groupIds.map(id=>options.find(g=>g.groupId===id)?.code || shortId(id)).join(', ')}</dd></dl>
        {invitation.expiresAt && Date.parse(invitation.expiresAt)<=Date.now() && <p>Lời mời đã qua thời hạn chấp thuận. Trạng thái lưu vẫn theo máy chủ.</p>}
        {stale && <p role="alert">Tải phiên bản hiện tại trước khi thu hồi.</p>}
        {invitation.state!=='REVOKED' && <form onSubmit={e=>{e.preventDefault();if(!confirmed || stale)return;command.run<Invitation>('/t3/mentor-invitations/'+invitation.invitationId+'/revoke',{expectedVersion:invitation.version,reasonCode:reason},v=>{
          if(v.invitationId!==invitation.invitationId || v.state!=='REVOKED'){denied();return;}changed('Đã thu hồi lời mời theo kết quả máy chủ.');
        });}}><fieldset disabled={busy || stale}><p>Thu hồi lời mời đã chấp thuận sẽ thu hồi phân công Mentor phát sinh từ lời mời.</p><label>Mã lý do thu hồi lời mời<input aria-label="Mã lý do thu hồi lời mời" value={reason} onChange={e=>setReason(e.target.value.toUpperCase())} pattern="[A-Z0-9_]{3,64}" minLength={3} maxLength={64} required /></label><label><input type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)} />Tôi xác nhận thu hồi lời mời và các quyền phụ thuộc.</label><button disabled={!confirmed || !/^[A-Z0-9_]{3,64}$/.test(reason)}>Thu hồi lời mời đã chọn</button></fieldset></form>}
        {['NEW_IDENTITY_PENDING','ACCOUNT_PENDING','MEMBERSHIP_PENDING','REVOKED'].includes(invitation.state) && <button type="button" className="secondary" disabled={busy} onClick={()=>{setEmail(detail.data!.email);setGroupIds(invitation.groupIds.filter(id=>options.some(g=>g.groupId===id)));setDecision('');setNotice('Kiểm tra nhóm và căn cứ trước khi gửi lại. Máy chủ kiểm tra claim, cooldown và quyền hiện hành.');}}>Chuẩn bị gửi lại lời mời</button>}
      </section>}
    </section>
    <section className="panel stack"><h3>Gửi lời mời Mentor</h3><Notice message={groups.error || moreGroups.error} tone="error" />
      <form onSubmit={e=>{e.preventDefault();if(!groupIds.length || groupIds.some(id=>!options.some(g=>g.groupId===id)))return;command.run<Invitation>('/t3/mentor-invitations',{email:email.trim(),enterpriseOrgId:assignment.scope.enterpriseOrgId,termId:assignment.scope.termId,groupIds:[...groupIds].sort(),decisionRef:decision.trim()},v=>{
        if(v.enterpriseOrgId!==assignment.scope.enterpriseOrgId || v.termId!==assignment.scope.termId){denied();return;}changed(v.state==='REVIEW_REQUIRED'?'Danh tính cần được rà soát. Chưa cấp quyền Mentor.':'Đã gửi lời mời. Mentor cần tự chấp thuận để nhận quyền.');setEmail('');setDecision('');setGroupIds([]);
      });}}><fieldset disabled={busy}><label>Email Mentor được mời<input aria-label="Email Mentor được mời" type="email" required maxLength={254} value={email} onChange={e=>setEmail(e.target.value)} /></label><label>Căn cứ mời Mentor<input aria-label="Căn cứ mời Mentor" required maxLength={120} value={decision} onChange={e=>setDecision(e.target.value)} /></label>
        <fieldset><legend>Nhóm được mời hướng dẫn</legend>{options.map(g=><label key={g.groupId}><input type="checkbox" checked={groupIds.includes(g.groupId)} onChange={e=>setGroupIds(old=>e.target.checked?[...old,g.groupId]:old.filter(id=>id!==g.groupId))} />{g.code} · {g.cohortCode}</label>)}</fieldset>
        <button disabled={!groupIds.length || groupIds.some(id=>!options.some(g=>g.groupId===id)) || !decision.trim()}>Gửi lời mời Mentor</button></fieldset></form>
      {(moreGroups.data || groups.data) && groupPage+1<(moreGroups.data || groups.data)!.totalPages && <button className="secondary" disabled={busy || moreGroups.loading} onClick={()=>setGroupPage(n=>n+1)}>Tải thêm nhóm được phân công</button>}
      {!groups.loading && options.length===0 && <p>Chưa có nhóm trong phạm vi HR để gửi lời mời.</p>}
    </section>
  </>;
}
