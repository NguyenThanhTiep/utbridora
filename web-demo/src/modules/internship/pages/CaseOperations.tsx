import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { workflowPages, workflowRead, workflowError } from '../workflow-api';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { uuidPattern } from '../workflow';
import { caseStateLabel, formatEntityShort, formatUserLabel, issueCategoryLabel, issueStateLabel } from '../displayLabels';
import { terminal, type CaseRef, type HiringInterest, type Issue, type OperationsScope, type QualityTemplate, type QualityFeedback } from '../operations-types';
import { OperationsCalendar } from './OperationsCalendar';

const lost=(e:unknown)=>e instanceof ApiError && [401,403,404].includes(e.status) && e.errorCode!=='CSRF_INVALID';
export function CaseOperations({scope,initialCaseId='',onDenied}:{scope:OperationsScope;initialCaseId?:string;onDenied?:()=>void}) {
  const [input,setInput]=useState(initialCaseId),[id,setId]=useState(initialCaseId),[value,setValue]=useState<CaseRef|null>(null),[error,setError]=useState(''),[revision,setRevision]=useState(0),[message,setMessage]=useState('');
  const denied=()=>{setValue(null);setId('');setMessage('');setError('Không còn đọc được hồ sơ trong phạm vi này.');onDenied?.();};
  useEffect(()=>{
    if(!id)return;const c=new AbortController();setError('');
    void workflowRead<CaseRef>('/t3/cases/'+id,scope.contextId,c.signal).then(v=>{
      if(c.signal.aborted)return;
      if(v.caseId!==id || v.schoolOrgId!==scope.schoolId || v.termId!==scope.termId || scope.enterpriseId && v.enterpriseOrgId!==scope.enterpriseId){denied();return;}setValue(v);
    }).catch(e=>{if(!c.signal.aborted){setValue(null);if(lost(e))denied();else setError(workflowError(e));}});
    return ()=>c.abort();
  },[id,revision,scope]);
  useEffect(()=>{const check=()=>{if(!document.hidden)setRevision(n=>n+1);};window.addEventListener('focus',check);document.addEventListener('visibilitychange',check);
    return ()=>{window.removeEventListener('focus',check);document.removeEventListener('visibilitychange',check);};},[]);
  return <section className="panel stack t3-case-operations"><h2>Vận hành hồ sơ thực tập</h2>
    {!initialCaseId && <form onSubmit={e=>{e.preventDefault();setValue(null);setMessage('');setError('');if(uuidPattern.test(input)){setId(input.toLowerCase());setRevision(n=>n+1);}}}>
      <label>Mã Case được cấp<input value={input} onChange={e=>{setInput(e.target.value);setId('');setValue(null);setMessage('');}} required/></label>
      <button disabled={!uuidPattern.test(input)}>Mở hồ sơ vận hành</button></form>}
    {error && <p role="alert">{error}</p>}{message && <p role="status">{message}</p>}
    {id && !value && !error && <p role="status">Đang xác nhận hồ sơ…</p>}
    {value && <div key={value.caseId+':'+value.version} className="stack"><div className="t3-case-header"><h3 title={value.caseId}>Hồ sơ …{value.caseId.slice(-6)}</h3><p><span className="t3-case-state" data-state={value.state}>{caseStateLabel(value.state)}</span> <small className="t3-tech-meta">Trạng thái: {value.state}</small></p>
      <p className="t3-placement-badge" title={value.placementId}>Mã tiếp nhận: <code>{formatEntityShort(value.placementId)}</code></p><button className="secondary" onClick={()=>setRevision(n=>n+1)}>Tải lại hồ sơ vận hành</button></div>
      <div className="t3-operations-cards"><IssuePanel scope={scope} value={value} denied={denied}/>
      {scope.mode==='school' && <OperationsCalendar scope={scope} caseId={value.caseId} denied={denied}/>}
      {scope.mode!=='mentor' && <InterestPanel scope={scope} value={value} denied={denied}/>}
      {scope.mode==='school' && scope.permissions.includes('t3:cases:close') && <ClosePanel scope={scope} value={value} denied={denied} closed={v=>{setValue(v);setMessage('Đã đóng Case. Kết quả Placement sẽ được đồng bộ qua sự kiện; có thể theo dõi tại màn tiếp nhận.');}}/>}</div>
    </div>}
  </section>;
}

function IssuePanel({scope,value,denied}:{scope:OperationsScope;value:CaseRef;denied:()=>void}) {
  const [rows,setRows]=useState<Issue[]>([]),[filter,setFilter]=useState(''),[refresh,setRefresh]=useState(0),[error,setError]=useState('');
  const [owner,setOwner]=useState(scope.userId),[description,setDescription]=useState(''),[category,setCategory]=useState('COMMUNICATION');
  const command=useWorkflowCommand(scope.contextId,denied,lost);const writable=!terminal(value) && scope.permissions.includes('t3:coordination:manage');
  useEffect(()=>{const c=new AbortController();setRows([]);setError('');
    void workflowPages<Issue>('/t3/school/issues?caseId='+value.caseId+(filter?'&state='+filter:''),scope.contextId,c.signal).then(v=>{
      if(c.signal.aborted)return;if(v.some(r=>r.caseId!==value.caseId)){denied();return;}setRows(v);
    }).catch(e=>{if(!c.signal.aborted){if(lost(e))denied();else setError(workflowError(e));}});return ()=>c.abort();
  },[value.caseId,scope.contextId,filter,refresh]);
  return <section className="t3-operation-card stack t3-coordination-card"><h3>Yêu cầu phối hợp</h3>{error && <p role="alert">{error}</p>}
    <div className="t3-issue-columns"><div className="stack"><label>Lọc trạng thái phối hợp<select value={filter} onChange={e=>setFilter(e.target.value)}><option value="">Tất cả</option><option value="OPEN">Mới mở</option><option value="IN_PROGRESS">Đang xử lý</option><option value="RESOLVED">Đã giải quyết</option></select></label>
    {writable && scope.mode==='school' && <form className="stack" onSubmit={e=>{e.preventDefault();command.run<Issue>('/t3/school/issues',{caseId:value.caseId,category,description:description.trim(),ownerUserId:owner.toLowerCase()},v=>{
      if(v.caseId!==value.caseId){denied();return;}setDescription('');setRefresh(n=>n+1);
    });}}><fieldset disabled={command.busy || command.pending}><legend>Tạo yêu cầu phối hợp</legend><label>Loại vấn đề<select value={category} onChange={e=>setCategory(e.target.value)}><option value="DELAY">Chậm tiến độ</option><option value="COMMUNICATION">Trao đổi/phối hợp</option><option value="OTHER">Khác</option></select></label>
      <label>Nội dung vấn đề<textarea value={description} maxLength={5000} required onChange={e=>setDescription(e.target.value)}/></label><label>Mã người phụ trách<input value={owner} onChange={e=>setOwner(e.target.value)} required/></label>
      <p>Người phụ trách phải là cán bộ trường, HR hoặc Mentor đang được phân công hợp lệ cho Case.</p><button disabled={!description.trim() || !uuidPattern.test(owner)}>Tạo yêu cầu phối hợp</button></fieldset></form>}
    </div><div className="stack">    {rows.length===0 && !error && <p>Chưa có yêu cầu trong bộ lọc này.</p>}
    {rows.map(row=><article className="t3-issue" key={row.issueId}><h4 aria-label={row.category + ' · ' + row.state}>{issueCategoryLabel(row.category)} · {issueStateLabel(row.state)}</h4><p className="t3-preserve-text">{row.description}</p>
      <p>Phụ trách: <span title={row.ownerUserId}>{formatUserLabel(row.ownerUserId, scope.userId, 'Cán bộ')}</span></p>
      {writable && scope.mode!=='mentor' && row.state!=='RESOLVED' && <button disabled={command.busy || command.pending} onClick={()=>command.run<Issue>('/t3/school/issues/'+row.issueId,{expectedVersion:row.version,state:row.state==='OPEN'?'IN_PROGRESS':'RESOLVED'},v=>{
        if(v.issueId!==row.issueId || v.caseId!==value.caseId){denied();return;}setRefresh(n=>n+1);
      },'PATCH')}>{row.state==='OPEN'?'Bắt đầu xử lý':'Đánh dấu đã giải quyết'}</button>}</article>)}
    </div></div>
    <CommandFeedback command={command}/>
  </section>;
}

function InterestPanel({scope,value,denied}:{scope:OperationsScope;value:CaseRef;denied:()=>void}) {
  const [current,setCurrent]=useState<HiringInterest|null>(null),[interested,setInterested]=useState(false),[note,setNote]=useState(''),[error,setError]=useState('');
  const command=useWorkflowCommand(scope.contextId,denied,lost);
  useEffect(()=>{const c=new AbortController();void workflowRead<HiringInterest>('/t3/cases/'+value.caseId+'/hiring-interest',scope.contextId,c.signal).then(v=>{
    if(c.signal.aborted)return;if(v.caseId!==value.caseId){denied();return;}setCurrent(v);setInterested(v.interested);setNote(v.note || '');
  }).catch(e=>{if(!c.signal.aborted){setCurrent(null);if(lost(e))denied();else setError(workflowError(e));}});return ()=>c.abort();},[value.caseId,scope.contextId]);
  const writable=scope.mode==='hr' && scope.permissions.includes('t3:hiring_interest:manage') && !terminal(value);
  return <section className="t3-operation-card stack t3-hiring-interest"><h3>Quan tâm tuyển dụng</h3><p>Đây là ý định của doanh nghiệp, không phải quyết định tuyển dụng và không thay đổi kết quả thực tập.</p>
    {error && <p role="alert">{error}</p>}{current && (writable?<form onSubmit={e=>{e.preventDefault();command.run<HiringInterest>('/t3/cases/'+value.caseId+'/hiring-interest',{expectedVersion:current.version,expectedCaseVersion:value.version,interested,note:note.trim() || null},v=>{
      if(v.caseId!==value.caseId){denied();return;}setCurrent(v);setInterested(v.interested);setNote(v.note || '');command.setMessage('Đã lưu ý định tuyển dụng.');
    },'PUT');}}><fieldset disabled={command.busy || command.pending}><label><input type="checkbox" checked={interested} onChange={e=>setInterested(e.target.checked)}/>Doanh nghiệp quan tâm tuyển dụng</label>
      <label>Ghi chú quan tâm tuyển dụng<textarea value={note} maxLength={2000} onChange={e=>setNote(e.target.value)}/></label><button>Lưu quan tâm tuyển dụng</button></fieldset></form>:<div><p>{current.version===0?'Chưa có ghi nhận':current.interested?'Có quan tâm tuyển dụng':'Chưa quan tâm tuyển dụng'}</p><p className="t3-preserve-text">{current.note || 'Không có ghi chú'}</p>{terminal(value) && <p>Case đã đóng; nội dung này chỉ được xem.</p>}</div>)}
    <CommandFeedback command={command}/>
  </section>;
}

function ClosePanel({scope,value,denied,closed}:{scope:OperationsScope;value:CaseRef;denied:()=>void;closed:(v:CaseRef)=>void}) {
  const [decision,setDecision]=useState('NORMAL_COMPLETION'),[ref,setRef]=useState(''),[reason,setReason]=useState('');
  const command=useWorkflowCommand(undefined,denied,lost);
  if(terminal(value))return <section className="t3-operation-card t3-close-card"><h3>Tổng kết Case</h3><p>Case đã ở trạng thái kết thúc. Lịch sử và các dữ liệu đã lưu được giữ nguyên.</p></section>;
  return <section className="t3-operation-card stack t3-close-card"><h3>Tổng kết và đóng Case</h3><p>Hoàn thành bình thường cần tới ngày kết thúc, toàn bộ nhiệm vụ hoàn thành, đủ báo cáo tuần đã duyệt và đánh giá hiện hành đã công bố. Kết thúc sớm cần quyết định hợp lệ của nhà trường.</p>
    <form onSubmit={e=>{e.preventDefault();command.run<CaseRef>('/t3/cases/'+value.caseId+'/close',{expectedVersion:value.version,decision,decisionRef:ref.trim(),reasonCode:reason},v=>{if(v.caseId!==value.caseId || !terminal(v)){denied();return;}closed(v);});}}>
      <fieldset disabled={command.busy || command.pending}><label>Hình thức kết thúc<select value={decision} onChange={e=>setDecision(e.target.value)}><option value="NORMAL_COMPLETION">Hoàn thành bình thường</option><option value="EARLY_TERMINATION">Kết thúc sớm</option></select></label>
        <label>Căn cứ quyết định đóng Case<input value={ref} maxLength={120} required onChange={e=>setRef(e.target.value)}/></label><label>Mã lý do đóng Case<input value={reason} maxLength={64} required placeholder="NORMAL_COMPLETION" onChange={e=>setReason(e.target.value)}/></label>
        <p>Sau khi xác nhận, Case sẽ khóa các thao tác ghi mới. Đây không phải quyết định công nhận tín chỉ.</p><button disabled={!ref.trim() || !/^[A-Z0-9_]{3,64}$/.test(reason)}>Xác nhận đóng Case</button></fieldset>
    </form><CommandFeedback command={command}/>
  </section>;
}

export function EnterpriseQualityForm({scope,denied}:{scope:OperationsScope;denied:()=>void}) {
  const [templates,setTemplates]=useState<QualityTemplate[]>([]),[selected,setSelected]=useState(''),[enterprise,setEnterprise]=useState(''),[ratings,setRatings]=useState<Record<string,{stars:number;comment:string}>>({}),[note,setNote]=useState(''),[error,setError]=useState(''),[receipt,setReceipt]=useState<QualityFeedback|null>(null);
  const command=useWorkflowCommand(undefined,denied,lost);const template=templates.find(t=>t.templateId+':'+t.version===selected);
  useEffect(()=>{const c=new AbortController();void workflowPages<QualityTemplate>('/t3/enterprise-quality/templates?termId='+scope.termId,undefined,c.signal).then(v=>{
    if(!c.signal.aborted)setTemplates(v);
  }).catch(e=>{if(!c.signal.aborted){if(lost(e))denied();else setError(workflowError(e));}});return ()=>c.abort();},[scope.termId]);
  return <section className="panel stack t3-enterprise-quality"><h2>Đánh giá chất lượng doanh nghiệp</h2><p>Rubric hợp tác riêng của trường; điểm sao không tham gia điểm sinh viên hoặc xác minh doanh nghiệp.</p>
    {error && <p role="alert">{error}</p>}{templates.length===0 && !error && <p>Chưa có rubric chất lượng cho kỳ này. Liên hệ đơn vị quản lý để cấp template có phiên bản.</p>}
    {templates.length>0 && <form onSubmit={e=>{e.preventDefault();if(!template)return;command.run<QualityFeedback>('/t3/school/enterprise-feedback',{enterpriseOrgId:enterprise.toLowerCase(),termId:scope.termId,templateId:template.templateId,templateVersion:template.version,
      ratings:template.criteria.map(c=>({criterionCode:c.code,stars:ratings[c.code]?.stars || 3,comment:ratings[c.code]?.comment.trim() || null})),note:note.trim() || null},v=>{
      if(v.schoolOrgId!==scope.schoolId || v.input.termId!==scope.termId || v.input.enterpriseOrgId!==enterprise.toLowerCase()){denied();return;}setReceipt(v);
    });}}><fieldset disabled={command.busy || command.pending}><label>Mã doanh nghiệp được đánh giá<input value={enterprise} onChange={e=>{setEnterprise(e.target.value);setReceipt(null);}} required/></label>
      <label>Rubric chất lượng<select value={selected} onChange={e=>{setSelected(e.target.value);setRatings({});setReceipt(null);}}><option value="">Chọn rubric…</option>{templates.map(t=><option key={t.templateId+':'+t.version} value={t.templateId+':'+t.version}>Rubric …{t.templateId.slice(-6)} · Phiên bản {t.version}</option>)}</select></label>
      {template?.criteria.map(c=><div className="t3-quality-criterion" key={c.code}><span aria-hidden="true" className="t3-stars">{'★'.repeat(ratings[c.code]?.stars || 3)}{'☆'.repeat(5 - (ratings[c.code]?.stars || 3))}</span><label>{c.label}<select value={ratings[c.code]?.stars || 3} onChange={e=>setRatings(old=>({...old,[c.code]:{stars:Number(e.target.value),comment:old[c.code]?.comment || ''}}))}>{[1,2,3,4,5].map(n=><option value={n} key={n}>{n} sao</option>)}</select></label>
        <label>Nhận xét — {c.label}<textarea maxLength={2000} value={ratings[c.code]?.comment || ''} onChange={e=>setRatings(old=>({...old,[c.code]:{stars:old[c.code]?.stars || 3,comment:e.target.value}}))}/></label></div>)}
      <label>Ghi chú đánh giá doanh nghiệp<textarea maxLength={2000} value={note} onChange={e=>setNote(e.target.value)}/></label><button disabled={!template || !uuidPattern.test(enterprise)}>Lưu đánh giá doanh nghiệp</button></fieldset></form>}
    {receipt && <p role="status">Đã lưu đánh giá doanh nghiệp, mã {receipt.id}, phiên bản {receipt.version}.</p>}<CommandFeedback command={command}/>
  </section>;
}
