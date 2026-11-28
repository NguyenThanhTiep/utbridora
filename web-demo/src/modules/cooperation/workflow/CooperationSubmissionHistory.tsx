import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { Notice } from '../../../components';
import { PagerNav } from '../../../ui/PagerNav';
import type { ContractPage } from '../../student/types';
import { scopedRead, workflowError } from '../proposals/api';
import './submission-history.css';
type Submission = { submissionNo:number; submittedAt:string; demandVersion:number; criteriaVersion:number; quantitySnapshot:number; decision:string|null; decidedAt:string|null; reasonCode:string|null; decisionRef:string|null };
const labels:Record<string,string>={APPROVE:'Đã duyệt',REJECT:'Từ chối',REQUEST_INFO:'Yêu cầu bổ sung'};
const date=(s:string|null)=>s?new Date(s).toLocaleString('vi-VN'):'—';
export function CooperationSubmissionHistory(props:{org:string;cooperationId:string;disabled?:boolean}) {
  return <HistoryScope key={props.org+':'+props.cooperationId} {...props} />;
}
function HistoryScope({org,cooperationId,disabled=false}:{org:string;cooperationId:string;disabled?:boolean}) {
  const [open,setOpen]=useState(false),[page,setPage]=useState(0),[revision,setRevision]=useState(0);
  const [result,setResult]=useState<ContractPage<Submission>>(),[loading,setLoading]=useState(false),[error,setError]=useState('');
  useEffect(()=>{
    if(!open)return;const c=new AbortController();setResult(undefined);setError('');setLoading(true);
    void scopedRead<ContractPage<Submission>>(org,'/school/cooperations/'+encodeURIComponent(cooperationId)+'/submission-history?page='+page+'&size=25',AbortSignal.any([c.signal,AbortSignal.timeout(20000)]))
      .then(value=>{if(c.signal.aborted)return;
        if(!Array.isArray(value.content) || value.page!==page || !Number.isSafeInteger(value.totalPages) || value.totalPages<0 || !Number.isSafeInteger(value.totalElements) || value.totalElements<0
          || value.content.some(v=>![v.submissionNo,v.demandVersion,v.criteriaVersion].every(n=>Number.isSafeInteger(n)&&n>0) || !Number.isSafeInteger(v.quantitySnapshot) || v.quantitySnapshot<0 || !Number.isFinite(Date.parse(v.submittedAt))))throw new Error('Invalid submission history');
        setResult(value);
      }).catch(e=>{if(!c.signal.aborted){setResult(undefined);setError(e instanceof ApiError && [401,403,404].includes(e.status)?'Lịch sử không còn truy cập được trong phạm vi trường và kỳ hiện hành.':workflowError(e));}})
      .finally(()=>{if(!c.signal.aborted)setLoading(false);});return ()=>c.abort();
  },[org,cooperationId,open,page,revision]);
  useEffect(()=>{const refresh=()=>{if(!document.hidden && !disabled)setRevision(n=>n+1);};window.addEventListener('focus',refresh);return ()=>window.removeEventListener('focus',refresh);},[disabled]);
  return <section className="cooperation-submission-history stack" aria-label="Lịch sử các lần gửi hợp tác"><h3>Lịch sử các lần gửi hợp tác</h3>
    <p>Đối chiếu phiên bản nhu cầu, tiêu chí, chỉ tiêu và quyết định của từng lần gửi. Nội dung đang xét duyệt được hiển thị riêng phía trên.</p>
    <button type="button" className="secondary" disabled={disabled || loading} aria-expanded={open} onClick={()=>{setOpen(true);setRevision(n=>n+1);}}>Tải lịch sử các lần gửi</button>
    {loading && <p role="status">Đang tải lịch sử…</p>}<Notice message={error} tone="error" />
    {open && result && <><div className="submission-history-table"><table><thead><tr><th>Lần gửi / thời điểm</th><th>Phiên bản nhu cầu / tiêu chí</th><th>Chỉ tiêu lúc gửi</th><th>Quyết định</th><th>Căn cứ / lý do</th></tr></thead><tbody>{result.content.map(v=><tr key={v.submissionNo}>
      <td>Lần gửi {v.submissionNo}<small>{date(v.submittedAt)}</small></td><td>{v.demandVersion} / {v.criteriaVersion}</td><td>{v.quantitySnapshot}</td><td>{v.decision?labels[v.decision] || v.decision:'Chưa có quyết định'}<small>{date(v.decidedAt)}</small></td><td>{v.decisionRef || '—'}<small>{v.reasonCode || '—'}</small></td>
    </tr>)}</tbody></table></div>{result.totalElements===0 && <p>Chưa có lần gửi trong lịch sử được phép xem.</p>}
    <PagerNav page={page} totalPages={result.totalPages} totalElements={result.totalElements} unitLabel="lần gửi" busy={disabled || loading} prevLabel="Lịch sử trước" nextLabel="Lịch sử sau" ariaLabel="Phân trang lịch sử" onChange={setPage} /></>}
  </section>;
}
