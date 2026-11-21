import { useEffect, useState } from 'react';
import { gradeAccessLost, previewTotal, rubricValid, scoresValid, studentEvaluation, toDraft, wireScores,
  type Evaluation, type Rubric, type ScoreDraft, type StudentResult, type VisibilityPolicy } from '../evaluation';
import { workflowError, workflowPages, workflowRead } from '../workflow-api';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { textWithin, validVersion, type ReportFile } from '../workflow';
import { ReportEvidence } from './ReportEvidence';

type Scope = { caseId: string; contextId?: string; denied: () => void };
export function EvaluationView({ value, contextId, denied }: { value: Evaluation; contextId?: string; denied: () => void }) {
  return <section className="panel stack" aria-label="Nội dung đánh giá">
    <p>Trạng thái: {value.state} · Revision {value.revisionNo} · Version {value.version}</p>
    <p>Phiên bản rubric: {value.template.templateVersion}</p><h3>Điểm tổng: {value.total100.toFixed(2)} / 100</h3>
    {value.publishedAt && <p>Công bố: {new Date(value.publishedAt).toLocaleString('vi-VN')} · Căn cứ: {value.decisionRef}</p>}
    {value.template.criteria.map(c => { const s=value.scores.find(v => v.criterionCode === c.criterionCode); return <article key={c.criterionCode}>
      <h4>{c.label} · {c.weight}%</h4><p>Điểm: {s?.score} / 5</p><p className="t3-preserve-text">{s?.comment || 'Không có nhận xét'}</p>
      {!!s?.evidenceFileIds.length && <ReportEvidence evaluation reportId={value.evaluationId} fileIds={s.evidenceFileIds}
        contextId={contextId} editable={false} disabled={false} changed={() => {}} denied={denied} />}
    </article>; })}
  </section>;
}

export function MentorEvaluation({ caseId, termId, contextId, denied }: Scope & { termId: string }) {
  const [value,setValue]=useState<Evaluation | null>(null), [templates,setTemplates]=useState<Rubric[]>([]);
  const [templateKey,setTemplateKey]=useState(''), [scores,setScores]=useState<ScoreDraft[]>([]), [dirty,setDirty]=useState(false);
  const [loading,setLoading]=useState(true), [error,setError]=useState(''), [refresh,setRefresh]=useState(0);
  const [historical,setHistorical]=useState<Evaluation | null>(null), [history,setHistory]=useState<Evaluation[]>([]), [version,setVersion]=useState('');
  const command=useWorkflowCommand(contextId,denied,gradeAccessLost);
  useEffect(() => {
    const c=new AbortController();setLoading(true);setError('');setValue(null);setHistorical(null);setHistory([]);
    void (async () => {
      const current=await workflowRead<Evaluation | undefined>('/t3/evaluations?caseId='+caseId,contextId,c.signal);
      if (c.signal.aborted) return;
      if (current) {
        if (current.caseId!==caseId || !rubricValid(current.template)) {denied();return;}
        setValue(current);
        if (!dirty) setScores(toDraft(current).sort((a,b)=>current.template.criteria.findIndex(c=>c.criterionCode===a.criterionCode)-current.template.criteria.findIndex(c=>c.criterionCode===b.criterionCode)));
        const list=await workflowPages<Evaluation>('/t3/evaluations/'+current.evaluationId+'/history',contextId,c.signal);
        if (c.signal.aborted) return;
        if (list.some(v=>v.caseId!==caseId || v.evaluationId!==current.evaluationId)) {denied();return;}
        setHistory(list);
        if (version) {
          const old=await workflowRead<Evaluation>('/t3/evaluations/'+current.evaluationId+'?version='+version,contextId,c.signal);
          if (c.signal.aborted) return;
          if (old.caseId!==caseId || old.evaluationId!==current.evaluationId || old.version!==Number(version)) {denied();return;}
          setHistorical(old);
        }
      } else {
        const list=await workflowPages<Rubric>('/t3/rubric-templates?termId='+termId,contextId,c.signal);
        if (c.signal.aborted) return;
        if (list.some(t=>!rubricValid(t))) {setError('Rubric không hợp lệ. Liên hệ người phụ trách.');return;}
        setTemplates(list);
      }
    })().catch(e=>{if (!c.signal.aborted) {setValue(null);setHistory([]);setHistorical(null);if(gradeAccessLost(e)) denied();else setError(workflowError(e));}})
      .finally(()=>{if(!c.signal.aborted)setLoading(false);});
    return ()=>c.abort();
  },[caseId,termId,contextId,refresh,version]);
  const template=value?.template || templates.find(t=>t.templateId+':'+t.templateVersion===templateKey);
  const valid=!!template && scoresValid(template,scores);
  const blocked=loading || !!error || command.busy || command.pending;
  const done=()=>{setDirty(false);setVersion('');setRefresh(n=>n+1);};
  const change=(index:number,patch:Partial<ScoreDraft>)=>{setDirty(true);setScores(old=>old.map((s,i)=>i===index?{...s,...patch}:s));};
  return <section className="stack" aria-label="Mentor chấm rubric"><h2>Chấm đánh giá thực tập</h2>
    <div className="button-row"><button type="button" className="secondary" disabled={command.busy || command.pending} onClick={()=>setRefresh(n=>n+1)}>Tải lại đánh giá</button></div>
    {loading && <p role="status">Đang tải đánh giá…</p>}{error && <p role="alert">{error}</p>}<CommandFeedback command={command}/>
    {!loading && !error && <>
      {!value && <label>Rubric áp dụng<select value={templateKey} disabled={blocked} onChange={e=>{
        const t=templates.find(t=>t.templateId+':'+t.templateVersion===e.target.value);setTemplateKey(e.target.value);setDirty(false);
        setScores(t?.criteria.map(c=>({criterionCode:c.criterionCode,score:'',comment:'',evidenceFileIds:[]})) || []);
      }}><option value="">Chọn rubric…</option>{templates.map(t=><option key={t.templateId+':'+t.templateVersion} value={t.templateId+':'+t.templateVersion}>Rubric …{t.templateId.slice(-6)} · Phiên bản {t.templateVersion}</option>)}</select></label>}
      {!value && templates.length===0 && <p>Chưa có rubric cho kỳ này. Người phụ trách cần cấp rubric trước khi chấm.</p>}
      {value && <><label>Lịch sử đánh giá<select value={version} disabled={blocked} onChange={e=>setVersion(e.target.value)}>
        <option value="">Bản hiện tại</option>{history.map(v=><option key={v.version} value={v.version}>Version {v.version} · Revision {v.revisionNo} · {v.state}</option>)}</select></label>
        <EvaluationView value={historical || value} contextId={contextId} denied={denied}/></>}
      {!version && template && (!value || value.state==='DRAFT') && <>
        <form onSubmit={e=>{e.preventDefault();if(!valid || !template)return;
          command.run<Evaluation>(value?'/t3/evaluations/'+value.evaluationId:'/t3/evaluations',value?
            {expectedVersion:value.version,scores:wireScores(scores)}:{caseId,templateId:template.templateId,templateVersion:template.templateVersion,scores:wireScores(scores)},
            saved=>{if(saved.caseId!==caseId || value && saved.evaluationId!==value.evaluationId){denied();return;}done();},value?'PUT':'POST');
        }}><fieldset disabled={blocked}><legend>{value?'Sửa bản nháp đánh giá':'Chấm rubric mới'}</legend>
          {template.criteria.map((c,index)=><div className="panel stack" key={c.criterionCode}><h3>{c.label} · Trọng số {c.weight}%</h3>
            <label>Điểm {c.label} (0–5)<input type="text" inputMode="decimal" required value={scores[index]?.score || ''} onChange={e=>change(index,{score:e.target.value})}/></label>
            <label>Nhận xét {c.label}<textarea aria-label={'Nhận xét '+c.label} value={scores[index]?.comment || ''} onChange={e=>change(index,{comment:e.target.value})}/></label>
          </div>)}
          <p>Điểm có tối đa 2 chữ số thập phân; nhận xét tối đa 2.000 ký tự.</p>
          <p>Điểm dự kiến: {template ? previewTotal(template,scores) ?? 'Chưa đủ điểm hợp lệ' : '—'}</p>
          <button disabled={!valid || !!value && !dirty}>{value?'Lưu đánh giá':'Tạo bản nháp đánh giá'}</button>
        </fieldset></form>
        {value && <><EvaluationUploads value={value} scores={scores} changed={change} contextId={contextId} denied={denied} command={command} blocked={blocked}/>
          <button type="button" disabled={blocked || dirty || !validVersion(value.version)} onClick={()=>command.run('/t3/evaluations/'+value.evaluationId+'/submit',{expectedVersion:value.version},done)}>Nộp đánh giá</button>
          {dirty && <p>Lưu thay đổi trước khi nộp đánh giá.</p>}</>}
      </>}
    </>}
  </section>;
}

function EvaluationUploads({value,scores,changed,contextId,denied,command,blocked}: {
  value:Evaluation;scores:ScoreDraft[];changed:(index:number,patch:Partial<ScoreDraft>)=>void;contextId?:string;denied:()=>void;
  command:ReturnType<typeof useWorkflowCommand>;blocked:boolean;
}) {
  const [index,setIndex]=useState(0),[file,setFile]=useState<File|null>(null),[reset,setReset]=useState(0);
  const valid=!!file && file.size>0 && file.size<=10*1024*1024 && ['application/pdf','image/png','image/jpeg'].includes(file.type);
  return <section className="stack"><h3>Minh chứng theo tiêu chí</h3>
    {scores.map((s,i)=><div key={s.criterionCode}><h4>{value.template.criteria.find(c=>c.criterionCode===s.criterionCode)?.label}</h4>
      <ReportEvidence evaluation reportId={value.evaluationId} fileIds={s.evidenceFileIds} contextId={contextId} editable disabled={blocked}
        changed={ids=>changed(i,{evidenceFileIds:ids})} denied={denied}/></div>)}
    <form onSubmit={e=>{e.preventDefault();if(!valid || !file || scores[index].evidenceFileIds.length>=20)return;
      command.upload<ReportFile>(value.evaluationId,file,result=>{
        if(result.resource.resourceId!==value.evaluationId || result.resource.resourceType!=='EVALUATION' || result.purpose!=='EVIDENCE'){denied();return;}
        changed(index,{evidenceFileIds:[...new Set([...scores[index].evidenceFileIds,result.fileId])]});setFile(null);setReset(n=>n+1);
      },{resourceType:'EVALUATION',purpose:'EVIDENCE'});
    }}><fieldset disabled={blocked}><legend>Tải minh chứng đánh giá</legend>
      <label>Tiêu chí nhận minh chứng<select value={index} onChange={e=>setIndex(Number(e.target.value))}>{scores.map((s,i)=><option key={s.criterionCode} value={i}>{value.template.criteria.find(c=>c.criterionCode===s.criterionCode)?.label}</option>)}</select></label>
      <label>Tệp minh chứng đánh giá<input key={reset} type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={e=>setFile(e.target.files?.[0] || null)}/></label>
      <p>PDF/PNG/JPEG, tối đa 10 MiB và 20 tệp mỗi tiêu chí. Lưu đánh giá sau khi tải lên. Có thể nộp không kèm file; file đính kèm cần được quét sạch.</p>
      <button disabled={!valid || scores[index]?.evidenceFileIds.length>=20}>Tải tệp đánh giá</button>
    </fieldset></form>
  </section>;
}

export function VisibilityEditor({path,contextId,validate,denied,title}: {
  path:string;contextId?:string;validate:(p:VisibilityPolicy)=>boolean;denied:()=>void;title:string;
}) {
  const [policy,setPolicy]=useState<VisibilityPolicy|null>(null),[enabled,setEnabled]=useState(false),[error,setError]=useState(''),[refresh,setRefresh]=useState(0);
  const command=useWorkflowCommand(contextId,denied,gradeAccessLost);
  useEffect(()=>{const c=new AbortController();setPolicy(null);setError('');
    void workflowRead<VisibilityPolicy>(path,contextId,c.signal).then(p=>{if(c.signal.aborted)return;
      if(!validate(p) || !Number.isSafeInteger(p.version) || p.version<0){denied();return;}setPolicy(p);setEnabled(p.enabled);
    }).catch(e=>{if(!c.signal.aborted){if(gradeAccessLost(e))denied();else setError(workflowError(e));}});return ()=>c.abort();
  },[path,contextId,refresh]);
  return <section className="panel stack" aria-label={title}><h3>{title}</h3>
    <div className="button-row"><button className="secondary" disabled={command.busy || command.pending} onClick={()=>setRefresh(n=>n+1)}>Tải lại chính sách</button></div>
    {error && <p role="alert">{error}</p>}{!policy && !error && <p role="status">Đang xác nhận quyền và chính sách…</p>}
    {policy && <form onSubmit={e=>{e.preventDefault();command.run<VisibilityPolicy>(path,{expectedVersion:policy.version,enabled},p=>{
      if(!validate(p)){denied();return;}setRefresh(n=>n+1);
    },'PUT');}}><fieldset disabled={command.busy || command.pending}>
      <p>Trạng thái đã lưu: {policy.enabled?'Bật':'Tắt'} · Version {policy.version}</p>
      <label><input type="checkbox" checked={enabled} onChange={e=>setEnabled(e.target.checked)}/>Cho phép sinh viên xem điểm</label>
      <button disabled={policy.version>0 && enabled===policy.enabled}>Lưu quyền xem điểm</button>
    </fieldset></form>}<CommandFeedback command={command}/>
  </section>;
}

export function StudentEvaluation({caseId,denied}:Scope) {
  const [value,setValue]=useState<Evaluation|null>(null),[loading,setLoading]=useState(true),[error,setError]=useState(''),[refresh,setRefresh]=useState(0);
  useEffect(()=>{const c=new AbortController();setValue(null);setLoading(true);setError('');
    void workflowRead<StudentResult>('/t3/me/results?caseId='+caseId,undefined,c.signal).then(result=>{
      if(c.signal.aborted)return;setValue(studentEvaluation(result,caseId));
    }).catch(e=>{if(!c.signal.aborted){setValue(null);if(gradeAccessLost(e))denied();else setError('Không thể xác nhận kết quả. Vui lòng tải lại.');}})
      .finally(()=>{if(!c.signal.aborted)setLoading(false);});return ()=>c.abort();
  },[caseId,refresh]);
  useEffect(()=>{const refresh=()=>{setValue(null);setLoading(true);setRefresh(n=>n+1);};
    window.addEventListener('focus',refresh);return ()=>window.removeEventListener('focus',refresh);
  },[]);
  return <section className="stack t3-student-eval" aria-label="Kết quả của sinh viên"><h2>Kết quả thực tập của tôi</h2>
    <div className="button-row"><button className="secondary" onClick={()=>{setValue(null);setLoading(true);setRefresh(n=>n+1);}}>Tải lại kết quả</button></div>
    {loading?<p role="status">Đang kiểm tra quyền xem kết quả…</p>:error?<p role="alert">{error}</p>
      :value?<EvaluationView value={value} denied={denied}/>:<p>Kết quả chưa được phép hiển thị.</p>}
  </section>;
}

export function SchoolEvaluations({schoolId,termId,denied,canPublish,canPolicy}: {
  schoolId:string;termId:string;denied:()=>void;canPublish:boolean;canPolicy:boolean;
}) {
  const [rows,setRows]=useState<Evaluation[]>([]),[loading,setLoading]=useState(false),[error,setError]=useState('');
  const [refresh,setRefresh]=useState(0),[selected,setSelected]=useState(''),[state,setState]=useState('');
  useEffect(()=>{if(!canPublish)return;const c=new AbortController();setRows([]);setSelected('');setLoading(true);setError('');
    void workflowPages<Evaluation>('/t3/school/evaluations?termId='+termId+(state?'&state='+state:''),undefined,c.signal)
      .then(v=>{if(!c.signal.aborted)setRows(v);}).catch(e=>{if(!c.signal.aborted){if(gradeAccessLost(e))denied();else setError(workflowError(e));}})
      .finally(()=>{if(!c.signal.aborted)setLoading(false);});return ()=>c.abort();
  },[termId,state,refresh,canPublish]);
  const value=rows.find(v=>v.evaluationId===selected);
  return <div className="stack">{canPolicy && <VisibilityEditor title="Chính sách nhà trường" path={'/t3/schools/'+schoolId+'/terms/'+termId+'/result-visibility'}
    validate={p=>p.schoolOrgId===schoolId && p.termId===termId} denied={denied}/>}
    {canPublish && <section className="panel stack t3-school-evaluations"><h2>Công bố đánh giá</h2>
      <label>Lọc trạng thái đánh giá<select value={state} onChange={e=>setState(e.target.value)}><option value="">Tất cả</option>{['DRAFT','SUBMITTED','PUBLISHED'].map(s=><option key={s}>{s}</option>)}</select></label>
      <div className="button-row"><button className="secondary" onClick={()=>setRefresh(n=>n+1)}>Tải danh sách đánh giá</button></div>
      {loading && <p role="status">Đang tải danh sách…</p>}{error && <p role="alert">{error}</p>}
      {!loading && !error && rows.length===0 && <p>Chưa có đánh giá trong phạm vi này.</p>}
      {rows.length > 0 && <div className="t3-report-list">{rows.map(v=><button className={'secondary t3-report-row-btn' + (selected === v.evaluationId ? ' is-selected' : '')} key={v.evaluationId} onClick={()=>setSelected(v.evaluationId)}>Hồ sơ …{v.caseId.slice(-6)} · {v.state} · Version {v.version}</button>)}</div>}
      {value && <SchoolDecision key={value.evaluationId+':'+value.version+':'+refresh} value={value} denied={denied} done={()=>setRefresh(n=>n+1)}/>}
    </section>}
  </div>;
}
function SchoolDecision({value,denied,done}:{value:Evaluation;denied:()=>void;done:()=>void}) {
  const [decision,setDecision]=useState(''),[reason,setReason]=useState('');const command=useWorkflowCommand(undefined,denied,gradeAccessLost);
  return <section className="stack t3-decision-card" aria-label="Quyết định công bố"><EvaluationView value={value} denied={denied}/>
    {['SUBMITTED','PUBLISHED'].includes(value.state) && <fieldset disabled={command.busy || command.pending}>
      <label>Căn cứ quyết định<input value={decision} onChange={e=>setDecision(e.target.value)}/></label>
      {value.state==='SUBMITTED' && <button disabled={!textWithin(decision,1,120)} onClick={()=>command.run('/t3/evaluations/'+value.evaluationId+'/publish',{expectedVersion:value.version,decisionRef:decision.trim()},done)}>Công bố điểm</button>}
      <label>Mã lý do tạo revision<input value={reason} onChange={e=>setReason(e.target.value)}/></label>
      <button disabled={!textWithin(decision,1,120) || !/^[A-Z0-9_]{3,64}$/.test(reason)} onClick={()=>command.run('/t3/evaluations/'+value.evaluationId+'/revisions',
        {expectedVersion:value.version,decisionRef:decision.trim(),reasonCode:reason},done)}>Tạo revision đánh giá</button>
      <p>Revision mới giữ kết quả đã công bố trước đó cho đến khi trường công bố lại.</p>
    </fieldset>}<CommandFeedback command={command}/>
  </section>;
}
