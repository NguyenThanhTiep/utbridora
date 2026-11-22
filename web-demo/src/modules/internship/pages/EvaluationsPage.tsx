import { useEffect, useState, type ReactNode } from 'react';
import { ApiError, type Grant, type Me } from '../../../api';
import { useOrganizationNames } from '../../useOrganizationNames';
import type { ContextAssignment } from '../types';
import { workflowError, workflowPages, workflowRead } from '../workflow-api';
import { gradeAccessLost } from '../evaluation';
import { uuidPattern, type InternshipTask } from '../workflow';
import { MentorEvaluation, SchoolEvaluations, StudentEvaluation, VisibilityEditor } from './EvaluationPanels';
import { useReveal } from '../../../useReveal';
import '../styles.css';

type Mode='ENTERPRISE'|'SCHOOL_STAFF'|'STUDENT';
const modeNames:Record<Mode,string>={ENTERPRISE:'Mentor / HR',SCHOOL_STAFF:'Nhà trường',STUDENT:'Sinh viên'};
export function EvaluationsPage({me,grants}:{me:Me;grants:Grant[]}) {
  const modes=(['ENTERPRISE','SCHOOL_STAFF','STUDENT'] as Mode[]).filter(role=>grants.some(g=>g.role===role && !!g.organizationId));
  const [mode,setMode]=useState<Mode>(modes[0]),[generation,setGeneration]=useState(0),[closed,setClosed]=useState(false);
  const denied=()=>setClosed(true);
  useEffect(()=>{const close=()=>setClosed(true);window.addEventListener('session-expired',close);return ()=>window.removeEventListener('session-expired',close);},[]);
  if(!modes.includes(mode))return <p>Chưa có vai trò phù hợp để xem đánh giá.</p>;
  return <div className="stack t3-evaluations">
    {modes.length>1 && <label>Vai trò đánh giá<select value={mode} onChange={e=>{setMode(e.target.value as Mode);setClosed(false);setGeneration(n=>n+1);}}>{modes.map(m=><option key={m} value={m}>{modeNames[m]}</option>)}</select></label>}
    {closed?<section className="panel"><p role="alert">Quyền truy cập đã thay đổi. Dữ liệu đánh giá đã được đóng.</p>
      <button onClick={()=>{setClosed(false);setGeneration(n=>n+1);}}>Xác nhận lại quyền đánh giá</button></section>
      :<div key={me.userId+':'+mode+':'+generation}>
        {mode==='ENTERPRISE'?<EnterpriseEvaluations me={me} grants={grants} denied={denied}/>
          :mode==='SCHOOL_STAFF'?<SchoolScope grants={grants} denied={denied}/>
          :<CaseChooser student denied={denied}>{caseId=><StudentEvaluation caseId={caseId} denied={denied}/>}</CaseChooser>}
      </div>}
  </div>;
}

function CaseChooser({children,contextId,student=false,fixedCase,mentorGroup,denied}:{
  children:(id:string)=>ReactNode;contextId?:string;student?:boolean;fixedCase?:string|null;mentorGroup?:string|null;denied:()=>void;
}) {
  const [choices,setChoices]=useState<string[]>([]),[selected,setSelected]=useState(fixedCase || ''),[input,setInput]=useState('');
  const [message,setMessage]=useState('');
  // Chọn hoặc nhập mã hồ sơ thì cuộn tới khối đánh giá của hồ sơ đó.
  const reveal=useReveal<HTMLDivElement>();
  useEffect(()=>{if(fixedCase || !student && !mentorGroup)return;const c=new AbortController();
    const read=student?workflowPages<InternshipTask>('/t3/tasks',undefined,c.signal).then(tasks=>tasks.flatMap(t=>t.assignments.map(a=>a.caseId)))
      :workflowPages<{groupId:string;caseIds:string[]}>('/t3/mentors/me/groups',contextId,c.signal).then(groups=>groups.filter(g=>g.groupId===mentorGroup).flatMap(g=>g.caseIds));
    void read.then(ids=>{if(!c.signal.aborted)setChoices([...new Set(ids)]);})
      .catch(e=>{if(!c.signal.aborted){if(gradeAccessLost(e))denied();else setMessage('Không tải được danh sách hồ sơ. Có thể nhập mã hồ sơ đã được cấp.');}});
    return ()=>c.abort();
  },[student,mentorGroup,contextId,fixedCase]);
  return <section className="stack"><div className="panel stack"><h2>Chọn hồ sơ thực tập</h2>
    {message && <p role="alert">{message}</p>}
    {choices.length>0 && <label>{student?'Hồ sơ từ nhiệm vụ':'Hồ sơ trong nhóm'}<select value={selected} onChange={e=>{setSelected(e.target.value);if(e.target.value)reveal.request();}}><option value="">Chọn hồ sơ…</option>
      {choices.map(id=><option key={id} value={id}>Hồ sơ …{id.slice(-6)}</option>)}</select></label>}
    {fixedCase?<p>Hồ sơ được phân công: {fixedCase}</p>:<form onSubmit={e=>{e.preventDefault();if(uuidPattern.test(input)){setSelected(input.toLowerCase());reveal.request();}}}>
      <label>Mã hồ sơ thực tập<input value={input} required onChange={e=>{setInput(e.target.value);setSelected('');}}/></label>
      <p>Dùng mã hồ sơ được cấp. Hệ thống xác nhận quyền khi mở dữ liệu.</p>
      <button disabled={!uuidPattern.test(input)}>Mở đánh giá hồ sơ</button>
    </form>}
  </div>{selected && <div key={selected+':'+(contextId || '')} ref={reveal.ref}>{children(selected)}</div>}</section>;
}

function EnterpriseEvaluations({me,grants,denied}:{me:Me;grants:Grant[];denied:()=>void}) {
  const [assignments,setAssignments]=useState<ContextAssignment[]>([]),[selected,setSelected]=useState(''),[error,setError]=useState('');
  const [loading,setLoading]=useState(true),[refresh,setRefresh]=useState(0),[now,setNow]=useState(Date.now());
  useEffect(()=>{const c=new AbortController();setAssignments([]);setSelected('');setLoading(true);setError('');
    void workflowRead<ContextAssignment[]>('/t3/context-assignments/me',undefined,c.signal).then(items=>{if(!c.signal.aborted){setNow(Date.now());setAssignments(items);}})
      .catch(e=>{if(!c.signal.aborted){if(gradeAccessLost(e))denied();else setError(workflowError(e));}})
      .finally(()=>{if(!c.signal.aborted)setLoading(false);});return ()=>c.abort();
  },[refresh,me.userId]);
  useEffect(()=>{const next=assignments.flatMap(a=>[Date.parse(a.validFrom),Date.parse(a.validUntil)]).filter(t=>t>now).sort((a,b)=>a-b)[0];
    if(!next)return;const timer=window.setTimeout(()=>setNow(Date.now()),Math.min(next-Date.now()+1,2147483647));return ()=>clearTimeout(timer);
  },[assignments,now]);
  const usable=assignments.filter(a=>a.userId===me.userId && ['MENTOR','HR'].includes(a.kind) && a.active
    && Date.parse(a.validFrom)<=now && now<Date.parse(a.validUntil) && a.scope.enterpriseOrgId && a.scope.schoolOrgId && a.scope.termId
    && grants.some(g=>g.role==='ENTERPRISE' && g.organizationId===a.scope.enterpriseOrgId && g.permissions.includes(a.kind==='MENTOR'?'internship:enterprise:evaluate':'t3:grade_visibility:manage')));
  const assignment=usable.find(a=>a.id===selected);
  return <div className="stack"><section className="panel stack"><h2>Phân công đánh giá</h2>
    <div className="button-row"><button type="button" className="secondary" onClick={()=>setRefresh(n=>n+1)}>Tải lại phân công đánh giá</button></div>
    {loading?<p role="status">Đang xác nhận phân công…</p>:error?<p role="alert">{error}</p>:<label>Phân công đánh giá<select value={assignment?.id || ''} onChange={e=>setSelected(e.target.value)}>
      <option value="">Chọn phân công…</option>{usable.map(a=><option key={a.id} value={a.id}>{a.kind} · Kỳ …{a.scope.termId.slice(-6)} · {a.scope.caseId?'Hồ sơ …'+a.scope.caseId.slice(-6):a.scope.groupId?'Nhóm …'+a.scope.groupId.slice(-6):'Toàn doanh nghiệp'}</option>)}</select></label>}
    {!loading && !error && usable.length===0 && <p>Chưa có phân công Mentor/HR phù hợp còn hiệu lực.</p>}
  </section>{assignment && <div key={assignment.id+':'+assignment.version+':'+refresh}>
    {assignment.kind==='MENTOR'?<CaseChooser fixedCase={assignment.scope.caseId} mentorGroup={assignment.scope.groupId} contextId={assignment.id} denied={denied}>{caseId=><MentorEvaluation caseId={caseId} termId={assignment.scope.termId} contextId={assignment.id} denied={denied}/>}</CaseChooser>
      :<HrVisibility assignment={assignment} denied={denied}/>}
  </div>}</div>;
}
function HrVisibility({assignment,denied}:{assignment:ContextAssignment;denied:()=>void}) {
  const {scope}=assignment,restricted=!!scope.groupId || !!scope.caseId;
  const [mode,setMode]=useState(restricted?'CASE':'ORGANIZATION_TERM');
  const policy=(caseId:string|null)=><VisibilityEditor title={caseId?'Quyền xem điểm theo hồ sơ':'Quyền xem điểm doanh nghiệp/kỳ'} contextId={assignment.id}
    path={'/t3/organizations/'+scope.enterpriseOrgId+'/grade-visibility?termId='+scope.termId+(caseId?'&caseId='+caseId:'')}
    validate={p=>p.enterpriseOrgId===scope.enterpriseOrgId && p.termId===scope.termId && p.caseId===caseId && p.scope===(caseId?'CASE':'ORGANIZATION_TERM')} denied={denied}/>;
  return <section className="stack"><h2>Cấu hình hiển thị kết quả</h2><p>Chọn phạm vi để xem và thay đổi quyền hiển thị điểm của doanh nghiệp. Chính sách riêng của hồ sơ được ưu tiên, kể cả khi tắt.</p>
    {!restricted && <label>Phạm vi chính sách<select value={mode} onChange={e=>setMode(e.target.value)}><option value="ORGANIZATION_TERM">Doanh nghiệp và kỳ</option><option value="CASE">Một hồ sơ</option></select></label>}
    {mode==='CASE'?<CaseChooser fixedCase={scope.caseId} contextId={assignment.id} denied={denied}>{id=>policy(id)}</CaseChooser>:policy(null)}
  </section>;
}
function SchoolScope({grants,denied}:{grants:Grant[];denied:()=>void}) {
  const schools=grants.filter(g=>g.role==='SCHOOL_STAFF' && !!g.organizationId && (g.permissions.includes('t3:grades:publish') || g.permissions.includes('t3:result_policy:manage')));
  const orgNames=useOrganizationNames(schools.length>0);
  const [school,setSchool]=useState(schools[0]?.organizationId || '');
  const current=schools.find(g=>g.organizationId===school);
  return <div className="stack"><section className="panel stack">
    {schools.length>0
      ? <label>Trường quản lý đánh giá<select value={school} onChange={e=>setSchool(e.target.value)}>
        {schools.map(g=><option key={g.organizationId} value={g.organizationId!}>{orgNames[g.organizationId!] || ('Trường …'+g.organizationId!.slice(-6))}</option>)}</select></label>
      : <p className="empty-inline">Chưa có quyền công bố hoặc quản lý chính sách kết quả.</p>}
  </section>
    {current && <SchoolTerm key={school} schoolId={school} grant={current} denied={denied}/>}
  </div>;
}
function SchoolTerm({schoolId,grant,denied}:{schoolId:string;grant:Grant;denied:()=>void}) {
  const [terms,setTerms]=useState<{termId:string;schoolOrgId:string;termCode:string}[]>([]),[term,setTerm]=useState(''),[input,setInput]=useState(''),[error,setError]=useState('');
  const [loading,setLoading]=useState(true);
  useEffect(()=>{const c=new AbortController();setLoading(true);setError('');
    void workflowPages<{termId:string;schoolOrgId:string;termCode:string}>('/terms?schoolOrgId='+schoolId,undefined,c.signal).then(v=>{
      if(c.signal.aborted)return;if(v.some(t=>t.schoolOrgId!==schoolId)){denied();return;}setTerms(v);
    // TermReference requires a separate T5 permission. Its 403/404 does not revoke grade delegation.
    }).catch(e=>{if(!c.signal.aborted){if(e instanceof ApiError && e.status===401)denied();else setError('Không tải được danh sách kỳ. Nhập mã kỳ trong phân công của bạn để kiểm tra quyền.');}})
      .finally(()=>{if(!c.signal.aborted)setLoading(false);});return ()=>c.abort();
  },[schoolId]);
  return <div className="stack"><section className="panel stack">
    <h2>Phạm vi nhà trường</h2>
    {loading && <p role="status">Đang tải kỳ đánh giá…</p>}
    {!loading && !error && terms.length===0 && <p>Chưa có kỳ đánh giá. Bạn có thể nhập mã kỳ đã được phân công bên dưới.</p>}
    {terms.length>0 && <label>Kỳ đánh giá<select value={term} onChange={e=>setTerm(e.target.value)}><option value="">Chọn kỳ…</option>{terms.map(t=><option key={t.termId} value={t.termId}>{t.termCode}</option>)}</select></label>}
    {error && <p role="alert">{error}</p>}
    <details><summary>Nhập mã kỳ được phân công</summary><form onSubmit={e=>{e.preventDefault();if(uuidPattern.test(input))setTerm(input.toLowerCase());}}>
      <label>Mã kỳ thực tập<input value={input} onChange={e=>{setInput(e.target.value);setTerm('');}}/></label><button disabled={!uuidPattern.test(input)}>Mở kỳ đánh giá</button>
    </form></details><p>Quyền công bố và quyền xem điểm được kiểm tra theo phân công trường/kỳ hiện hành.</p>
  </section>{term && <SchoolEvaluations key={schoolId+':'+term} schoolId={schoolId} termId={term} denied={denied}
    canPublish={grant.permissions.includes('t3:grades:publish') && grant.permissions.includes('t3:workspace:read')} canPolicy={grant.permissions.includes('t3:result_policy:manage')}/>}</div>;
}
