import { EvaluationView } from './EvaluationView';
export { EvaluationView } from './EvaluationView';
export { SchoolEvaluations, VisibilityEditor } from './SchoolGradePanels';
import { useEffect, useState } from 'react';
import { gradeAccessLost, previewTotal, rubricValid, scoresValid, studentEvaluation, toDraft, wireScores,
  type Evaluation, type Rubric, type ScoreDraft, type StudentResult } from '../evaluation';
import { workflowError, workflowPages, workflowRead } from '../workflow-api';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { formatEntityShort } from '../displayLabels';
import { validVersion, type ReportFile } from '../workflow';
import { ReportEvidence } from './ReportEvidence';

type Scope = { caseId: string; contextId?: string; denied: () => void };

export function MentorEvaluation({ caseId, termId, contextId, denied }: Scope & { termId: string }) {
  const [value,setValue]=useState<Evaluation | null>(null), [templates,setTemplates]=useState<Rubric[]>([]);
  const [templateKey,setTemplateKey]=useState(''), [scores,setScores]=useState<ScoreDraft[]>([]), [dirty,setDirty]=useState(false);
  const [loading,setLoading]=useState(true), [error,setError]=useState(''), [refresh,setRefresh]=useState(0);
  const [historical,setHistorical]=useState<Evaluation | null>(null), [history,setHistory]=useState<Evaluation[]>([]), [version,setVersion]=useState('');
  const [evidenceCriterion, setEvidenceCriterion] = useState(0);
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
  return <section className="stack" aria-label="Mentor chấm rubric"><div className="t3-card-heading"><h2>Chấm đánh giá thực tập</h2>
    <button type="button" className="secondary" disabled={command.busy || command.pending} onClick={()=>setRefresh(n=>n+1)}>Tải lại đánh giá</button></div>
    {loading && <p role="status">Đang tải đánh giá…</p>}{error && <p role="alert">{error}</p>}<CommandFeedback command={command}/>
    {!loading && !error && <>
      {!value && <label>Rubric áp dụng<select value={templateKey} disabled={blocked} onChange={e=>{
        const t=templates.find(t=>t.templateId+':'+t.templateVersion===e.target.value);setTemplateKey(e.target.value);setDirty(false);
        setScores(t?.criteria.map(c=>({criterionCode:c.criterionCode,score:'',comment:'',evidenceFileIds:[]})) || []);
      }}><option value="">Chọn rubric…</option>{templates.map(t=><option key={t.templateId+':'+t.templateVersion} value={t.templateId+':'+t.templateVersion}>Rubric {formatEntityShort(t.templateId)} · Phiên bản {t.templateVersion}</option>)}</select></label>}
      {!value && templates.length===0 && <p>Chưa có rubric cho kỳ này. Người phụ trách cần cấp rubric trước khi chấm.</p>}
      {value && <><label>Lịch sử đánh giá<select value={version} disabled={blocked} onChange={e=>setVersion(e.target.value)}>
        <option value="">Bản hiện tại</option>{history.map(v=><option key={v.version} value={v.version}>Version {v.version} · Revision {v.revisionNo} · {v.state}</option>)}</select></label>
         {historical || value.state !== 'DRAFT'
          ? <EvaluationView value={historical || value} contextId={contextId} denied={denied}/>
          : <header className="t3-card-heading"><h3>Điểm tổng: {value.total100.toFixed(2)} / 100</h3>
              <small>Trạng thái: {value.state} · Revision {value.revisionNo} · Version {value.version} · Rubric {value.template.templateVersion}</small></header>}</>}
      {!version && template && (!value || value.state==='DRAFT') && <>
        <form role="region" aria-label="Nội dung đánh giá" onSubmit={e=>{e.preventDefault();if(!valid || !template)return;
          command.run<Evaluation>(value?'/t3/evaluations/'+value.evaluationId:'/t3/evaluations',value?
            {expectedVersion:value.version,scores:wireScores(scores)}:{caseId,templateId:template.templateId,templateVersion:template.templateVersion,scores:wireScores(scores)},
            saved=>{if(saved.caseId!==caseId || value && saved.evaluationId!==value.evaluationId){denied();return;}done();},value?'PUT':'POST');
        }}><fieldset disabled={blocked}><legend>{value?'Sửa bản nháp đánh giá':'Chấm rubric mới'}</legend>
          {value && <label>Tiêu chí nhận minh chứng<select value={evidenceCriterion} onChange={e=>setEvidenceCriterion(Number(e.target.value))}>
            {template.criteria.map((c,i)=><option key={c.criterionCode} value={i}>{c.label}</option>)}</select></label>}
          {template.criteria.map((c,index)=><div className="panel stack t3-criterion-card" key={c.criterionCode}>
            <div className="t3-criterion-head">
              <h3>{c.label} <span className="t3-weight">Trọng số {c.weight}%</span></h3>
              <label className="t3-criterion-score"><span>Điểm (0–5)</span><input type="text" inputMode="decimal" required aria-label={'Điểm ' + c.label + ' (0–5)'} placeholder="0–5" value={scores[index]?.score || ''} onChange={e=>change(index,{score:e.target.value})}/></label>
            </div>
            <label>Nhận xét {c.label}<textarea aria-label={'Nhận xét '+c.label} value={scores[index]?.comment || ''} onChange={e=>change(index,{comment:e.target.value})}/></label>
            {value && <div className="t3-criterion-evidence">
              {scores[index] && <ReportEvidence evaluation reportId={value.evaluationId} contextId={contextId} denied={denied}
                fileIds={scores[index].evidenceFileIds} editable disabled={blocked} changed={ids=>change(index,{evidenceFileIds:ids})}/>}
              {index === evidenceCriterion && <EvaluationUploads value={value} score={scores[index]} command={command}
                denied={denied} blocked={blocked} changed={ids=>change(index,{evidenceFileIds:ids})}/>}
            </div>}
          </div>)}
          <footer className="t3-score-footer">
            <p className="t3-score-strip"><span>Điểm dự kiến: {previewTotal(template,scores) ?? 'Chưa đủ điểm hợp lệ'}</span> / 100</p>
            <small className="t3-score-hint">Điểm tổng tính theo trọng số, làm tròn đến hai chữ số thập phân. Nhận xét tối đa 2.000 ký tự.</small>
            <div className="button-row">
              <button disabled={!valid || !!value && !dirty}>{value?'Lưu đánh giá':'Tạo bản nháp đánh giá'}</button>
              {value && <button type="button" className="secondary" disabled={blocked || dirty || !validVersion(value.version)}
                onClick={()=>command.run('/t3/evaluations/'+value.evaluationId+'/submit',{expectedVersion:value.version},done)}>Nộp đánh giá</button>}
            </div>
            {dirty && value && <p>Lưu thay đổi trước khi nộp đánh giá.</p>}
          </footer>
        </fieldset></form>
      </>}
    </>}
  </section>;
}

function EvaluationUploads({ value, score, changed, denied, command, blocked }: {
  value: Evaluation; score: ScoreDraft; changed: (ids: string[]) => void; denied: () => void;
  command: ReturnType<typeof useWorkflowCommand>; blocked: boolean;
}) {
  const [file,setFile]=useState<File|null>(null), [reset,setReset]=useState(0);
  const valid=!!file && file.size>0 && file.size<=10*1024*1024 && ['application/pdf','image/png','image/jpeg'].includes(file.type);
  function upload() {
    if (!file || !valid || blocked || score.evidenceFileIds.length >= 20) return;
    command.upload<ReportFile>(value.evaluationId,file,result=>{
      if(result.resource.resourceId!==value.evaluationId || result.resource.resourceType!=='EVALUATION' || result.purpose!=='EVIDENCE'){denied();return;}
      changed([...new Set([...score.evidenceFileIds,result.fileId])]);setFile(null);setReset(n=>n+1);
    },{resourceType:'EVALUATION',purpose:'EVIDENCE'});
  }
  return <div className="stack t3-rubric-upload">
    <label>Tệp minh chứng đánh giá<span className="t3-placement-file">
      <input key={reset} aria-label="Tệp minh chứng đánh giá" type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={e=>setFile(e.target.files?.[0] || null)}/>
      <span className="t3-file-prompt" aria-hidden="true"><b>Kéo thả tệp hoặc chọn tệp</b><span>{file?.name || 'PDF, PNG, JPEG · Tối đa 10 MiB'}</span></span>
    </span></label>
    <small>Tối đa 20 tệp mỗi tiêu chí. Lưu đánh giá sau khi tải lên.</small>
    <button type="button" className="secondary" disabled={blocked || !valid || score.evidenceFileIds.length>=20} onClick={upload}>Tải tệp đánh giá</button>
  </div>;
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
  return <section className="stack" aria-label="Kết quả của sinh viên"><h2>Kết quả thực tập của tôi</h2>
    <button className="secondary" onClick={()=>{setValue(null);setLoading(true);setRefresh(n=>n+1);}}>Tải lại kết quả</button>
    {loading?<p role="status">Đang kiểm tra quyền xem kết quả…</p>:error?<p role="alert">{error}</p>
      :value?<EvaluationView value={value} denied={denied} student/>:<p>Kết quả chưa được phép hiển thị.</p>}
  </section>;
}
