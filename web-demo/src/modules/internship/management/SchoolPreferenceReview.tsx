import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import { scopedRead } from '../../cooperation/proposals/api';
import { useOrganizationNames } from '../../useOrganizationNames';
import type { WorkspacePage } from '../types';
import { accessLost, workflowError } from '../workflow-api';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { ManagementPager, shortId, dateLabel, useManagementRead } from './read';
import './assignment-management.css';

type Scope = { schoolOrgId: string; termId: string; termCode: string };
type Preference = { preferenceId: string; studentUserId: string; schoolOrgId: string; termId: string; opportunityId: string; intentVersion: number; version: number; status: string; message: string | null; createdAt: string; reviewedAt: string | null; withdrawnAt: string | null };
const names: Record<string,string> = { SUBMITTED: 'Chờ rà soát', REVIEWED: 'Đã rà soát', WITHDRAWN: 'Sinh viên đã rút' };
export function SchoolPreferenceReview() {
  const [page,setPage]=useState(0),[selected,setSelected]=useState(''),[revision,setRevision]=useState(0),[locked,setLocked]=useState(false);
  const scopes=useManagementRead<WorkspacePage<Scope>>('/school/preferences/scopes?page='+page,undefined,revision);
  const scope=scopes.data?.content.find(s=>s.schoolOrgId+':'+s.termId===selected);const orgs=useOrganizationNames();
  return <div className="stack assignment-management"><section className="panel stack"><h2>Rà soát nguyện vọng sinh viên</h2>
    <p>Rà soát ghi nhận ý định của sinh viên. Đề xuất, lời mời và quyết định tiếp nhận được xử lý riêng.</p><Notice message={scopes.error} tone="error" />
    <label>Phạm vi rà soát nguyện vọng<select aria-label="Phạm vi rà soát nguyện vọng" value={scope ? selected : ''} disabled={locked || scopes.loading} onChange={e=>setSelected(e.target.value)}><option value="">Chọn trường và kỳ…</option>
      {scopes.data?.content.map(s=><option key={s.schoolOrgId+':'+s.termId} value={s.schoolOrgId+':'+s.termId}>{orgs[s.schoolOrgId] || 'Trường '+shortId(s.schoolOrgId)} · {s.termCode}</option>)}</select></label>
    {scopes.data?.totalElements===0 && <p>Chưa có ủy nhiệm rà soát nguyện vọng còn hiệu lực.</p>}
    <ManagementPager data={scopes.data} disabled={locked || scopes.loading} change={p=>{setSelected('');setPage(p);}} />
    <button className="secondary" disabled={locked} onClick={()=>{setSelected('');setRevision(n=>n+1);}}>Xác nhận lại quyền rà soát</button>
  </section>{scope && <PreferenceScope key={selected+':'+revision} scope={scope} lock={setLocked} denied={()=>{setSelected('');setRevision(n=>n+1);}} />}</div>;
}
function PreferenceScope({scope,lock,denied}:{scope:Scope;lock:(v:boolean)=>void;denied:()=>void}) {
  const [page,setPage]=useState(0),[status,setStatus]=useState('SUBMITTED'),[revision,setRevision]=useState(0),[id,setId]=useState('');
  const [rows,setRows]=useState<WorkspacePage<Preference>>(),[detail,setDetail]=useState<Preference>(),[error,setError]=useState(''),[reason,setReason]=useState(''),[confirm,setConfirm]=useState(false),[saved,setSaved]=useState('');
  const [loading,setLoading]=useState(false),[stale,setStale]=useState(false);
  const command=useWorkflowCommand(undefined,denied,e=>accessLost(e) || e instanceof ApiError && e.status===404);
  const busy=command.busy || command.pending;
  useEffect(()=>{lock(busy);return ()=>lock(false);},[busy,lock]);
  useEffect(()=>{if(command.failureStatus===409)setStale(true);},[command.failureStatus]);
  useEffect(()=>{const c=new AbortController();setRows(undefined);setDetail(undefined);setError('');setLoading(true);
    const query=new URLSearchParams({termId:scope.termId,page:String(page),...(status?{status}:{})});
    void scopedRead<WorkspacePage<Preference>>(scope.schoolOrgId,'/school/preferences?'+query,c.signal).then(value=>{
      if(c.signal.aborted)return;if(value.content.some(v=>v.schoolOrgId!==scope.schoolOrgId || v.termId!==scope.termId)){denied();return;}setRows(value);
    }).catch(e=>{if(!c.signal.aborted){if(accessLost(e) || e instanceof ApiError && e.status===404)denied();else setError(workflowError(e));}}).finally(()=>{if(!c.signal.aborted)setLoading(false);});return ()=>c.abort();
  },[scope.schoolOrgId,scope.termId,page,status,revision]);
  useEffect(()=>{if(!id)return;const c=new AbortController();setDetail(undefined);setConfirm(false);
    void scopedRead<Preference>(scope.schoolOrgId,'/school/preferences/'+id,c.signal).then(v=>{
      if(c.signal.aborted)return;if(v.preferenceId!==id || v.schoolOrgId!==scope.schoolOrgId || v.termId!==scope.termId){denied();return;}setDetail(v);setStale(false);
    }).catch(e=>{if(!c.signal.aborted){if(accessLost(e) || e instanceof ApiError && e.status===404)denied();else setError(workflowError(e));}});return ()=>c.abort();
  },[id,revision,scope.schoolOrgId,scope.termId]);
  useEffect(()=>{const refresh=()=>{if(!document.hidden && !busy)setRevision(n=>n+1);};window.addEventListener('focus',refresh);return ()=>window.removeEventListener('focus',refresh);},[busy]);
  return <section className="panel stack"><h3>Nguyện vọng · {scope.termCode}</h3><Notice message={error} tone="error" /><Notice message={saved} /><CommandFeedback command={command} />
    <label>Trạng thái nguyện vọng<select aria-label="Trạng thái nguyện vọng" disabled={busy} value={status} onChange={e=>{setStatus(e.target.value);setPage(0);setId('');}}><option value="">Tất cả</option>{Object.entries(names).map(([v,n])=><option value={v} key={v}>{n}</option>)}</select></label>
    {loading && <p role="status">Đang tải nguyện vọng…</p>}
    <div className="assignment-table"><table><thead><tr><th>Sinh viên / cơ hội</th><th>Trạng thái</th><th>Ngày gửi</th><th>Thao tác</th></tr></thead><tbody>{rows?.content.map(v=><tr key={v.preferenceId}><td>Sinh viên {shortId(v.studentUserId)}<small>Cơ hội {shortId(v.opportunityId)}</small></td><td>{names[v.status] || v.status}</td><td>{dateLabel(v.createdAt)}</td><td><button className="secondary" disabled={busy} onClick={()=>{setId(v.preferenceId);setReason('');setConfirm(false);setRevision(n=>n+1);}}>Xem nguyện vọng</button></td></tr>)}</tbody></table></div>
    {rows?.totalElements===0 && <p>Chưa có nguyện vọng trong bộ lọc này.</p>}<ManagementPager data={rows} disabled={busy || loading} change={p=>{setPage(p);setId('');}} />
    <button className="secondary" disabled={busy} onClick={()=>{command.setMessage('');setRevision(n=>n+1);}}>Tải phiên bản nguyện vọng hiện tại</button>
    {detail && <section className="assignment-detail stack" aria-label="Chi tiết nguyện vọng"><h4>Chi tiết nguyện vọng</h4><dl><dt>Mã nguyện vọng</dt><dd>{detail.preferenceId}</dd><dt>Mã sinh viên</dt><dd>{detail.studentUserId}</dd><dt>Mã cơ hội</dt><dd>{detail.opportunityId}</dd><dt>Trạng thái</dt><dd>{names[detail.status]}</dd><dt>Phiên bản ý định / dữ liệu</dt><dd>{detail.intentVersion} / {detail.version}</dd></dl><p style={{whiteSpace:'pre-wrap'}}>{detail.message || 'Không có lời nhắn'}</p>
      {detail.reviewedAt && <p>Đã rà soát: {dateLabel(detail.reviewedAt)}</p>}{detail.withdrawnAt && <p>Đã rút: {dateLabel(detail.withdrawnAt)}</p>}
      {stale && <p role="alert">Tải phiên bản hiện tại trước khi rà soát lại.</p>}
      {detail.status==='SUBMITTED' && <form onSubmit={e=>{e.preventDefault();if(!confirm || stale)return;command.run<Preference>('/school/preferences/'+detail.preferenceId+'/review',{expectedVersion:detail.version,reasonCode:reason},v=>{
        if(v.preferenceId!==detail.preferenceId || v.schoolOrgId!==scope.schoolOrgId || v.termId!==scope.termId){denied();return;}setConfirm(false);setSaved('Đã ghi nhận rà soát nguyện vọng.');setRevision(n=>n+1);
      });}}><fieldset disabled={busy || stale}><label>Mã lý do rà soát<input aria-label="Mã lý do rà soát" value={reason} onChange={e=>setReason(e.target.value.toUpperCase())} pattern="[A-Z0-9_]{3,64}" minLength={3} maxLength={64} required /></label>
        <label><input type="checkbox" checked={confirm} onChange={e=>setConfirm(e.target.checked)} />Tôi xác nhận đã rà soát ý định này.</label><button disabled={!confirm || !/^[A-Z0-9_]{3,64}$/.test(reason)}>Ghi nhận đã rà soát</button></fieldset></form>}
    </section>}
  </section>;
}
