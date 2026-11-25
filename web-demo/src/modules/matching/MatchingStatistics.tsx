import { useEffect, useState } from 'react';
import { ApiError } from '../../api';
import { Notice } from '../../components';
import { workflowRead, workflowError } from '../internship/workflow-api';
import './matching-statistics.css';
type Statistics={totalExecutions:number;distinctActors:number;firstExecutionAt:string|null;lastExecutionAt:string|null};
const uuid=/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;
const time=(v:string|null)=>v?new Date(v).toLocaleString('vi-VN'):'Chưa có';
export function MatchingStatistics() {
  const [demand,setDemand]=useState(''),[from,setFrom]=useState(''),[until,setUntil]=useState('');
  const [query,setQuery]=useState<string|null>(null),[revision,setRevision]=useState(0),[value,setValue]=useState<Statistics>(),[error,setError]=useState(''),[loading,setLoading]=useState(false);
  useEffect(()=>{setValue(undefined);setError('');setLoading(query!==null);if(query===null)return;
    const c=new AbortController();void workflowRead<Statistics>('/admin/matching-statistics'+(query?'?'+query:''),undefined,c.signal).then(v=>{
      if(c.signal.aborted)return;
      if(![v.totalExecutions,v.distinctActors].every(n=>Number.isSafeInteger(n)&&n>=0) || v.distinctActors>v.totalExecutions
        || [v.firstExecutionAt,v.lastExecutionAt].some(s=>s!==null&&!Number.isFinite(Date.parse(s)))
        || (v.totalExecutions===0 ? v.firstExecutionAt!==null || v.lastExecutionAt!==null : !v.firstExecutionAt || !v.lastExecutionAt || Date.parse(v.firstExecutionAt)>Date.parse(v.lastExecutionAt)))throw new Error('Invalid statistics');
      setValue(v);
    }).catch(e=>{if(!c.signal.aborted){setValue(undefined);setError(e instanceof ApiError && [401,403].includes(e.status)?'Bạn không còn quyền xem thống kê Matching toàn hệ thống.':workflowError(e));}}).finally(()=>{if(!c.signal.aborted)setLoading(false);});return ()=>c.abort();
  },[query,revision]);
  const edit=(update:()=>void)=>{setQuery(null);setValue(undefined);setError('');update();};
  return <section className="panel stack matching-statistics" aria-label="Thống kê thực thi Matching"><h2>Thống kê thực thi Matching</h2>
    <p>Tổng hợp các lần đối chiếu hoàn tất đã ghi nhận trong nhật ký hệ thống. Số lượt này không thể hiện số sinh viên, số nhu cầu riêng biệt hoặc chất lượng mô hình AI.</p>
    <form onSubmit={e=>{e.preventDefault();setValue(undefined);setError('');
      if(demand.trim()&&!uuid.test(demand.trim())){setError('Mã nhu cầu phải có định dạng UUID.');return;}
      const start=from?new Date(from):null,end=until?new Date(until):null;
      if(start&&!Number.isFinite(start.getTime()) || end&&!Number.isFinite(end.getTime()) || start&&end&&start>=end){setError('Thời điểm bắt đầu phải trước thời điểm kết thúc.');return;}
      const q=new URLSearchParams();if(demand.trim())q.set('demandId',demand.trim().toLowerCase());if(start)q.set('from',start.toISOString());if(end)q.set('until',end.toISOString());setQuery(q.toString());setRevision(n=>n+1);
    }}><div className="matching-statistics-filters"><label>Mã nhu cầu (không bắt buộc)<input aria-label="Mã nhu cầu (không bắt buộc)" value={demand} maxLength={36} onChange={e=>edit(()=>setDemand(e.target.value))} /></label>
      <label>Từ thời điểm<input aria-label="Từ thời điểm" type="datetime-local" step="1" value={from} onChange={e=>edit(()=>setFrom(e.target.value))} /></label><label>Đến trước thời điểm<input aria-label="Đến trước thời điểm" type="datetime-local" step="1" value={until} onChange={e=>edit(()=>setUntil(e.target.value))} /></label></div>
      <p>Thời gian nhập theo múi giờ trên máy này. Khoảng thống kê bao gồm thời điểm bắt đầu và không bao gồm thời điểm kết thúc. Để trống để xem toàn bộ thời gian hoặc mọi nhu cầu.</p>
      <div className="button-row"><button disabled={loading}>Tải thống kê Matching</button><button type="button" className="secondary" onClick={()=>edit(()=>{setDemand('');setFrom('');setUntil('');})}>Xóa bộ lọc thống kê</button></div>
    </form><Notice message={error} tone="error" />{loading&&<p role="status">Đang tải thống kê…</p>}
    {value && <div aria-label="Kết quả thống kê Matching"><dl className="matching-statistics-values"><div><dt>Lượt đối chiếu hoàn tất</dt><dd>{value.totalExecutions.toLocaleString('vi-VN')}</dd></div><div><dt>Người thực thi khác nhau</dt><dd>{value.distinctActors.toLocaleString('vi-VN')}</dd></div><div><dt>Lần đầu trong bộ lọc</dt><dd>{time(value.firstExecutionAt)}</dd></div><div><dt>Lần cuối trong bộ lọc</dt><dd>{time(value.lastExecutionAt)}</dd></div></dl>
      {value.totalExecutions===0&&<p>Chưa có lượt đối chiếu hoàn tất trong bộ lọc này.</p>}</div>}
    {query===null&&!error&&<p>Chọn bộ lọc rồi tải thống kê để xem kết quả hiện hành.</p>}
  </section>;
}
