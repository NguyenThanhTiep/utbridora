import { useEffect, useState } from 'react';
import type { Cohort, WorkspaceAssignment, InternshipGroup } from '../types';
import { PagerNav } from '../../../ui/PagerNav';
import { FileDropzone } from '../../../ui/FileDropzone';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { uuidPattern, validVersion } from '../workflow';

type Preview = {previewId:string;cohortVersion:number;fileHash:string;expiresAt:string;rows:{rowNo:number;placementId:string|null;status:'VALID'|'INVALID';errorCode:string|null}[]};
type Batch = {cohortId:string;version:number;items:{placementId:string;caseId:string|null;outcome:string;reasonCode:string}[]};
type Plan = {planId:string;cohortVersion:number;expiresAt:string;groups:{ordinal:number;caseIds:string[]}[]};
const rowErrors:Record<string,string> = {INVALID_IDENTIFIER:'Mã không hợp lệ',AMBIGUOUS_STUDENT:'Có nhiều tiếp nhận phù hợp, cần dùng mã Placement',PLACEMENT_UNAVAILABLE:'Tiếp nhận không còn hợp lệ, bị trùng hoặc không thuộc phạm vi'};

export function CohortCreator({assignment,changed,denied}:{assignment:WorkspaceAssignment;changed:(id?:string)=>void;denied:()=>void}) {
  const [code,setCode]=useState(''),[name,setName]=useState('');const command=useWorkflowCommand(assignment.id,denied);
  return <details className="t3-operation-card t3-cohort-creator"><summary>Tạo đợt thực tập</summary><form className="stack" onSubmit={e=>{
    e.preventDefault();command.run<Cohort>('/t3/cohorts',{termId:assignment.scope.termId,code:code.trim(),name:name.trim()},value=>{
      if(value.schoolOrgId!==assignment.scope.schoolOrgId || value.enterpriseOrgId!==assignment.scope.enterpriseOrgId || value.termId!==assignment.scope.termId){denied();return;}changed(value.cohortId);
    });
  }}><fieldset disabled={command.busy || command.pending}><label>Mã đợt<input value={code} maxLength={64} required onChange={e=>setCode(e.target.value)}/></label>
    <label>Tên đợt<input value={name} maxLength={200} required onChange={e=>setName(e.target.value)}/></label><button disabled={!code.trim() || !name.trim()}>Tạo đợt trong kỳ đã chọn</button></fieldset><CommandFeedback command={command}/></form></details>;
}

export function CohortManagement({assignment,cohort,changed,denied,fileImportOnly=false}:{assignment:WorkspaceAssignment;cohort:Cohort;changed:()=>void;denied:()=>void;fileImportOnly?:boolean}) {
  const [file,setFile]=useState<File|null>(null),[preview,setPreview]=useState<Preview|null>(null),[selected,setSelected]=useState<Set<string>>(new Set()),[page,setPage]=useState(0);
  const [result,setResult]=useState<Batch|null>(null),[plan,setPlan]=useState<Plan|null>(null),[groupSize,setGroupSize]=useState('10'),[message,setMessage]=useState('');
  const command=useWorkflowCommand(assignment.id,denied);const blocked=command.busy || command.pending;
  useEffect(()=>{
    const ends=[preview?.expiresAt,plan?.expiresAt].filter((v):v is string=>!!v).map(Date.parse).sort((a,b)=>a-b)[0];if(!ends)return;
    const timer=window.setTimeout(()=>{setPreview(null);setSelected(new Set());setPlan(null);setMessage('Bản xem trước đã hết hạn. Hãy tạo bản mới.');},Math.max(0,Math.min(ends-Date.now()+1,2147483647)));
    return ()=>clearTimeout(timer);
  },[preview,plan]);
  const validIds=preview?.rows.filter(r=>r.status==='VALID' && !!r.placementId).map(r=>r.placementId!) || [];
  const rows=preview?.rows.slice(page*25,page*25+25) || [];
  return <div className="stack t3-cohort-management">
    <section className="t3-operation-card stack">{!fileImportOnly && <h3>Tiếp nhận Placement từ danh sách</h3>}
      <p>Tệp CSV/XLSX tối đa 5 MiB, 1.000 dòng. Dùng cột <code>placementId</code> hoặc <code>studentUserId</code>; mỗi dòng điền một loại mã. Chỉ tiếp nhận sinh viên đã có Placement hợp lệ trong phạm vi đợt.</p>
      {message && <p role="status">{message}</p>}
      <form onSubmit={e=>{e.preventDefault();if(!file)return;setPreview(null);setSelected(new Set());setResult(null);setPlan(null);setMessage('');
        const body=new FormData();body.set('file',file);body.set('expectedCohortVersion',String(cohort.version));
        command.multipart<Preview>('/t3/cohorts/'+cohort.cohortId+'/placements/preview',body,value=>{
          if(value.cohortVersion!==cohort.version || !uuidPattern.test(value.previewId) || !/^[a-f0-9]{64}$/.test(value.fileHash) || !Array.isArray(value.rows) || value.rows.length>1000 || value.rows.some(r=>!Number.isInteger(r.rowNo) || !['VALID','INVALID'].includes(r.status) || r.status==='VALID' && (!r.placementId || !uuidPattern.test(r.placementId)))){denied();return;}
          setPreview(value);setSelected(new Set());setPage(0);
        });
      }}><fieldset disabled={blocked || !!result}><FileDropzone label="Tệp danh sách Placement" aria-label="Tệp danh sách Placement"
        hint="CSV hoặc XLSX · Tối đa 5 MiB" maxBytes={5*1024*1024} accept=".csv,.xlsx" file={file}
        state={command.busy ? 'processing' : 'selected'} disabled={blocked || !!result}
        onChange={e=>{const next=e.target.files?.[0] || null;setFile(next);setPreview(null);setSelected(new Set());setMessage(next && next.size>5*1024*1024?'Tệp vượt quá 5 MiB.':'');}}/>
        <button disabled={!file || file.size>5*1024*1024 || !validVersion(cohort.version)}>Xem trước danh sách tiếp nhận</button></fieldset></form>
      {preview && <div className="stack"><p>{validIds.length} dòng hợp lệ / {preview.rows.length} dòng. Hết hạn: {new Date(preview.expiresAt).toLocaleString('vi-VN')}.</p>
        <fieldset disabled={blocked}><label><input type="checkbox" checked={validIds.length>0 && validIds.every(id=>selected.has(id))} onChange={e=>setSelected(new Set(e.target.checked?validIds:[]))}/>Chọn tất cả dòng hợp lệ</label>
          <div className="t3-table-scroll"><table><caption>Kết quả kiểm tra danh sách Placement</caption><thead><tr><th>Chọn</th><th>Dòng</th><th>Placement</th><th>Kết quả</th></tr></thead><tbody>{rows.map(r=><tr key={r.rowNo}>
            <td><input type="checkbox" aria-label={'Chọn dòng '+r.rowNo} disabled={r.status!=='VALID'} checked={!!r.placementId && selected.has(r.placementId)} onChange={e=>setSelected(old=>{const next=new Set(old);if(r.placementId){if(e.target.checked)next.add(r.placementId);else next.delete(r.placementId);}return next;})}/></td>
            <td>{r.rowNo}</td><td><code>{r.placementId || '—'}</code></td><td><span className="t3-preview-result" data-valid={r.status === 'VALID'}>{r.status==='VALID'?'Hợp lệ':rowErrors[r.errorCode || ''] || 'Không hợp lệ'}</span></td></tr>)}</tbody></table></div>
          <PagerNav page={page} totalPages={Math.ceil(preview.rows.length / 25)} totalElements={preview.rows.length} unitLabel="dòng" prevLabel="Trang trước" nextLabel="Trang sau" ariaLabel="Phân trang xem trước danh sách" onChange={setPage} />
          <button disabled={selected.size===0} onClick={()=>command.run<Batch>('/t3/cohorts/'+cohort.cohortId+'/members:batch',{expectedVersion:cohort.version,placementIds:[...selected].sort(),previewId:preview.previewId,fileHash:preview.fileHash},value=>{
            if(value.cohortId!==cohort.cohortId || !validVersion(value.version)){denied();return;}setResult(value);setPreview(null);setSelected(new Set());setMessage('Đã xử lý danh sách. Tải lại đợt để nhận phiên bản mới trước thao tác tiếp.');
          })}>Xác nhận tiếp nhận {selected.size} Placement</button></fieldset></div>}
      {result && <div role="status"><p>Đã thêm {result.items.filter(i=>i.outcome==='ADDED').length}; đã có {result.items.filter(i=>i.outcome==='ALREADY_PRESENT').length}; không hợp lệ {result.items.filter(i=>i.outcome==='INVALID').length}.</p>
        <button type="button" onClick={changed}>Tải phiên bản đợt mới</button></div>}
    </section>
    {!fileImportOnly && <section className="t3-operation-card stack"><h3>Chia nhóm tự động</h3><p>Xem trước cách chia trước khi xác nhận. Sinh viên chưa chia hết sẽ nằm trong nhóm cuối.</p>
      <fieldset disabled={blocked || !!result}><label>Số sinh viên mỗi nhóm<input type="number" min={1} max={1000} value={groupSize} onChange={e=>{setGroupSize(e.target.value);setPlan(null);}}/></label>
        <button disabled={!Number.isInteger(Number(groupSize)) || Number(groupSize)<1 || Number(groupSize)>1000} onClick={()=>command.run<Plan>('/t3/cohorts/'+cohort.cohortId+'/groups:auto',{mode:'PREVIEW',groupSize:Number(groupSize),expectedCohortVersion:cohort.version},value=>{
          if(value.cohortVersion!==cohort.version || !uuidPattern.test(value.planId) || !Array.isArray(value.groups) || value.groups.some(g=>!Array.isArray(g.caseIds) || g.caseIds.some(id=>!uuidPattern.test(id)))){denied();return;}setPlan(value);
        })}>Xem trước chia nhóm</button>
        {plan && <div><ul>{plan.groups.map(g=><li key={g.ordinal}>Nhóm {g.ordinal}: {g.caseIds.length} hồ sơ</li>)}</ul><p>Hết hạn: {new Date(plan.expiresAt).toLocaleString('vi-VN')}</p>
          <button onClick={()=>command.run<InternshipGroup[]>('/t3/cohorts/'+cohort.cohortId+'/groups:auto',{mode:'CONFIRM',planId:plan.planId,expectedCohortVersion:cohort.version},value=>{
            if(!Array.isArray(value) || value.some(g=>g.cohortId!==cohort.cohortId)){denied();return;}changed();
          })}>Xác nhận tạo {plan.groups.length} nhóm</button></div>}
      </fieldset>
    </section>}<CommandFeedback command={command}/>
  </div>;
}
