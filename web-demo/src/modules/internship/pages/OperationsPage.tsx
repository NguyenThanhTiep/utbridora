import { useEffect, useMemo, useState } from 'react';
import { ApiError, type Grant, type Me } from '../../../api';
import type { ContextAssignment } from '../types';
import { workflowError, workflowPages, workflowRead } from '../workflow-api';
import type { OperationsScope, Summary } from '../operations-types';
import { uuidPattern } from '../workflow';
import { CaseOperations, EnterpriseQualityForm } from './CaseOperations';
import { OperationsCalendar } from './OperationsCalendar';
import { SchoolEvaluations } from './EvaluationPanels';
import '../styles.css';

const lost=(e:unknown)=>e instanceof ApiError && [401,403,404].includes(e.status) && e.errorCode!=='CSRF_INVALID';
const stateLabels:Record<string,string>={NEW:'Mới giao',IN_PROGRESS:'Đang thực hiện',SUBMITTED:'Chờ nhận xét',REVISION_REQUIRED:'Cần bổ sung',COMPLETED:'Hoàn thành'};

export function OperationsPage({me,grants}:{me:Me;grants:Grant[]}) {
  const schools=grants.filter(g=>g.role==='SCHOOL_STAFF' && !!g.organizationId && g.permissions.includes('t3:workspace:read'));
  const enterprise=grants.some(g=>g.role==='ENTERPRISE' && g.permissions.includes('t3:workspace:read'));
  const [mode,setMode]=useState<'school'|'enterprise'>(schools.length?'school':'enterprise'),[closed,setClosed]=useState(false),[generation,setGeneration]=useState(0);
  useEffect(()=>{const clear=()=>setClosed(true);window.addEventListener('session-expired',clear);return ()=>window.removeEventListener('session-expired',clear);},[]);
  if(!schools.length && !enterprise)return <section className="panel"><p>Chưa có vai trò phù hợp để quản lý thực tập.</p></section>;
  return <div className="stack t3-operations"><section className="panel stack"><h1>Quản lý và tổng kết thực tập</h1>
    <p>Theo dõi tiến độ, yêu cầu phối hợp và tổng kết trong phạm vi đã được phân công.</p>
    {schools.length>0 && enterprise && <label>Vai trò quản lý thực tập<select value={mode} onChange={e=>{setMode(e.target.value as typeof mode);setClosed(false);setGeneration(n=>n+1);}}><option value="school">Nhà trường</option><option value="enterprise">HR / Mentor</option></select></label>}
    <button className="secondary" onClick={()=>{setClosed(false);setGeneration(n=>n+1);}}>Xác nhận lại quyền quản lý</button>
  </section>{closed?<section className="panel"><p role="alert">Quyền truy cập đã thay đổi. Dữ liệu vận hành đã được đóng; hãy xác nhận lại quyền.</p></section>
    :<div key={me.userId+':'+mode+':'+generation}>{mode==='school'?<SchoolOperations userId={me.userId} grants={schools} denied={()=>setClosed(true)}/>
      :<EnterpriseOperations me={me} grants={grants} denied={()=>setClosed(true)}/>}</div>}
  </div>;
}

function SchoolOperations({userId,grants,denied}:{userId:string;grants:Grant[];denied:()=>void}) {
  const [school,setSchool]=useState(grants[0]?.organizationId || '');const grant=grants.find(g=>g.organizationId===school);
  return <div className="stack"><section className="panel"><label>Trường quản lý thực tập<select value={school} onChange={e=>setSchool(e.target.value)}>{grants.map(g=><option key={g.organizationId} value={g.organizationId!}>Trường …{g.organizationId!.slice(-6)}</option>)}</select></label></section>
    {grant && <SchoolTerm key={school} userId={userId} school={school} grant={grant} denied={denied}/>}</div>;
}
function SchoolTerm({school,userId,grant,denied}:{school:string;userId:string;grant:Grant;denied:()=>void}) {
  const [terms,setTerms]=useState<{termId:string;schoolOrgId:string;termCode:string}[]>([]),[term,setTerm]=useState(''),[error,setError]=useState('');
  useEffect(()=>{const c=new AbortController();void workflowPages<{termId:string;schoolOrgId:string;termCode:string}>('/terms?schoolOrgId='+school,undefined,c.signal).then(v=>{
    if(c.signal.aborted)return;if(v.some(t=>t.schoolOrgId!==school)){denied();return;}setTerms(v);
  }).catch(e=>{if(!c.signal.aborted){if(lost(e))denied();else setError(workflowError(e));}});return ()=>c.abort();},[school]);
  const scope=useMemo<OperationsScope>(()=>({schoolId:school,termId:term,mode:'school',permissions:grant.permissions,userId}),[school,term,grant.permissions,userId]);
  return <div className="stack"><section className="panel"><label>Kỳ quản lý thực tập<select value={term} onChange={e=>setTerm(e.target.value)}><option value="">Chọn kỳ…</option>{terms.map(t=><option key={t.termId} value={t.termId}>{t.termCode}</option>)}</select></label>{error && <p role="alert">{error}</p>}</section>
    {term && <ScopeOperations key={school+':'+term} scope={scope} denied={denied}/>}</div>;
}
function EnterpriseOperations({me,grants,denied}:{me:Me;grants:Grant[];denied:()=>void}) {
  const [items,setItems]=useState<ContextAssignment[]>([]),[selected,setSelected]=useState(''),[error,setError]=useState(''),[now,setNow]=useState(Date.now());
  useEffect(()=>{const c=new AbortController();void workflowRead<ContextAssignment[]>('/t3/context-assignments/me',undefined,c.signal).then(v=>{if(!c.signal.aborted)setItems(v);})
    .catch(e=>{if(!c.signal.aborted){if(lost(e))denied();else setError(workflowError(e));}});return ()=>c.abort();},[me.userId]);
  useEffect(()=>{const future=items.flatMap(a=>[Date.parse(a.validFrom),Date.parse(a.validUntil)]).filter(n=>n>now).sort((a,b)=>a-b)[0];if(!future)return;
    const timer=setTimeout(()=>setNow(Date.now()),Math.min(future-Date.now()+1,2147483647));return ()=>clearTimeout(timer);},[items,now]);
  const usable=items.filter(a=>a.userId===me.userId && a.active && ['HR','MENTOR'].includes(a.kind) && Date.parse(a.validFrom)<=now && now<Date.parse(a.validUntil)
    && grants.some(g=>g.role==='ENTERPRISE' && g.organizationId===a.scope.enterpriseOrgId && g.permissions.includes('t3:workspace:read')));
  const assignment=usable.find(a=>a.id===selected),grant=grants.find(g=>g.role==='ENTERPRISE' && g.organizationId===assignment?.scope.enterpriseOrgId);
  const scope=useMemo<OperationsScope|null>(()=>assignment && grant?{schoolId:assignment.scope.schoolOrgId!,enterpriseId:assignment.scope.enterpriseOrgId!,termId:assignment.scope.termId!,contextId:assignment.id,mode:assignment.kind==='HR'?'hr':'mentor',permissions:grant.permissions,userId:me.userId}:null,[assignment,grant,me.userId]);
  return <div className="stack"><section className="panel"><label>Phân công quản lý thực tập<select value={assignment?.id || ''} onChange={e=>setSelected(e.target.value)}><option value="">Chọn phân công…</option>{usable.map(a=><option key={a.id} value={a.id}>{a.kind} · Trường …{a.scope.schoolOrgId?.slice(-6)} · Kỳ …{a.scope.termId?.slice(-6)}</option>)}</select></label>{error && <p role="alert">{error}</p>}{!error && usable.length===0 && <p>Chưa có phân công HR/Mentor còn hiệu lực.</p>}</section>
    {scope && <ScopeOperations key={assignment!.id+':'+assignment!.version} scope={scope} denied={denied}/>}</div>;
}

export function ScopeOperations({scope,denied,caseId}:{scope:OperationsScope;denied:()=>void;caseId?:string}) {
  const [showEvaluations,setShowEvaluations]=useState(false),[evaluationDenied,setEvaluationDenied]=useState(false);
  return <div className="stack"><Dashboard scope={scope} denied={denied}/>{scope.mode!=='school' && <OperationsCalendar scope={scope} denied={denied}/>}
    {scope.mode==='school' && scope.permissions.includes('t3:enterprise_quality:school:write') && <EnterpriseQualityForm scope={scope} denied={denied}/>}
    <CaseOperations scope={scope} initialCaseId={caseId} onDenied={caseId ? denied : undefined}/>
    {scope.mode==='school' && <section className="panel stack"><h2>Đánh giá sinh viên trong kỳ</h2><button className="secondary" onClick={()=>{setShowEvaluations(v=>!v);setEvaluationDenied(false);}}>Xem danh sách đánh giá thực tập</button>
      {evaluationDenied && <p role="alert">Chưa có quyền xem điểm trong phạm vi này. Các chức năng vận hành khác vẫn theo quyền riêng.</p>}
      {showEvaluations && <SchoolEvaluations schoolId={scope.schoolId} termId={scope.termId} denied={()=>{setShowEvaluations(false);setEvaluationDenied(true);}}
        canPublish={scope.permissions.includes('t3:grades:publish')} canPolicy={scope.permissions.includes('t3:result_policy:manage')}/>}</section>}
  </div>;
}
function Dashboard({scope,denied}:{scope:OperationsScope;denied:()=>void}) {
  const [value,setValue]=useState<Summary|null>(null),[error,setError]=useState(''),[revision,setRevision]=useState(0),[filter,setFilter]=useState(''),[cohort,setCohort]=useState('');
  useEffect(()=>{const c=new AbortController();setValue(null);setError('');void workflowRead<Summary>('/t3/'+scope.mode+'/dashboard?termId='+scope.termId+(cohort?'&cohortId='+cohort:''),scope.contextId,c.signal).then(v=>{
    if(c.signal.aborted)return;
    if(!v.assignmentCounts || v.assignmentCounts.length!==5 || new Set(v.assignmentCounts.map(x=>x.state)).size!==5 || v.assignmentCounts.some(x=>!(x.state in stateLabels) || !Number.isSafeInteger(x.count) || x.count<0)
      || [v.cohortCount,v.caseCount,v.groupCount,v.mentorCount,v.lateAssignmentCount,v.pendingReviewCount].some(n=>!Number.isSafeInteger(n) || n<0)){denied();return;}setValue(v);
  }).catch(e=>{if(!c.signal.aborted){setValue(null);if(lost(e))denied();else setError(workflowError(e));}});return ()=>c.abort();},[scope,revision,cohort]);
  useEffect(()=>{const check=()=>{if(!document.hidden)setRevision(n=>n+1);};window.addEventListener('focus',check);document.addEventListener('visibilitychange',check);return ()=>{window.removeEventListener('focus',check);document.removeEventListener('visibilitychange',check);};},[]);
  const total=value?.assignmentCounts.reduce((n,x)=>n+x.count,0) || 0;
  return <section className="panel stack"><h2>Tiến độ thực tập</h2><form className="t3-inline-form" onSubmit={e=>{e.preventDefault();setCohort(filter.toLowerCase());}}><label>Mã đợt cần lọc (không bắt buộc)<input value={filter} onChange={e=>setFilter(e.target.value)}/></label><button disabled={!!filter && !uuidPattern.test(filter)}>Lọc tiến độ</button><button type="button" className="secondary" onClick={()=>setRevision(n=>n+1)}>Tải lại tiến độ</button></form>
    {error && <p role="alert">{error}</p>}{!value && !error && <p role="status">Đang tổng hợp tiến độ…</p>}
    {value && <><dl className="t3-kpis">{[['Đợt',value.cohortCount],['Hồ sơ thực tập',value.caseCount],['Nhóm',value.groupCount],['Mentor hiện hành',value.mentorCount],['Nhiệm vụ chậm',value.lateAssignmentCount],['Chờ nhận xét',value.pendingReviewCount]].map(([label,count])=><div key={label}><dt>{label}</dt><dd>{count}</dd></div>)}</dl>
      <div className="stack" aria-label="Phân bố tiến độ nhiệm vụ">{value.assignmentCounts.map(row=><div className="t3-progress-row" key={row.state}><span>{stateLabels[row.state]}: {row.count}</span><progress max={Math.max(total,1)} value={row.count} aria-label={stateLabels[row.state]}/></div>)}</div></>}
  </section>;
}
