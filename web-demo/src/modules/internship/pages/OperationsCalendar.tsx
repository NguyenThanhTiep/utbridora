import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import { workflowPages, workflowError } from '../workflow-api';
import type { CalendarEvent, OperationsScope } from '../operations-types';
const lost=(e:unknown)=>e instanceof ApiError && [401,403,404].includes(e.status) && e.errorCode!=='CSRF_INVALID';
const stateLabels:Record<string,string>={NEW:'Mới giao',IN_PROGRESS:'Đang thực hiện',SUBMITTED:'Chờ nhận xét',REVISION_REQUIRED:'Cần bổ sung',COMPLETED:'Hoàn thành'};
const inputDate=(date:Date)=>new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);
export function OperationsCalendar({scope,denied,caseId}:{scope:OperationsScope;denied:()=>void;caseId?:string}) {
  const [from,setFrom]=useState(()=>inputDate(new Date(Date.now()-7*86400000))),[until,setUntil]=useState(()=>inputDate(new Date(Date.now()+14*86400000))),[range,setRange]=useState({from:new Date(Date.now()-7*86400000).toISOString(),until:new Date(Date.now()+14*86400000).toISOString()});
  const [rows,setRows]=useState<CalendarEvent[]>([]),[error,setError]=useState(''),[loading,setLoading]=useState(false);
  useEffect(()=>{const c=new AbortController();setRows([]);setError('');setLoading(true);
    void workflowPages<CalendarEvent>('/t3/calendar?'+new URLSearchParams({...range,...(caseId?{caseId}:{})}),scope.contextId,c.signal).then(v=>{if(!c.signal.aborted)setRows(v);})
      .catch(e=>{if(!c.signal.aborted){if(lost(e))denied();else setError(workflowError(e));}}).finally(()=>{if(!c.signal.aborted)setLoading(false);});return ()=>c.abort();},[scope,range,caseId]);
  return <section className="panel stack"><h2>Lịch hạn nhiệm vụ</h2><form className="t3-inline-form" onSubmit={e=>{e.preventDefault();const a=new Date(from),b=new Date(until);if(!Number.isFinite(a.getTime()) || !Number.isFinite(b.getTime()) || a>=b || b.getTime()-a.getTime()>62*86400000){setError('Chọn khoảng thời gian tăng dần, tối đa 62 ngày.');return;}setRange({from:a.toISOString(),until:b.toISOString()});}}>
    <label>Từ thời điểm<input type="datetime-local" value={from} required onChange={e=>setFrom(e.target.value)}/></label><label>Đến thời điểm<input type="datetime-local" value={until} required onChange={e=>setUntil(e.target.value)}/></label><button>Xem lịch nhiệm vụ</button></form>
    {error && <p role="alert">{error}</p>}{loading && <p role="status">Đang tải lịch…</p>}{!loading && !error && rows.length===0 && <p>Không có nhiệm vụ trong khoảng này.</p>}
    <ul className="t3-calendar">{rows.map(row=><li key={row.taskId+':'+(row.assignmentId || '')}><strong>{row.title}</strong><span>{new Date(row.dueAt).toLocaleString('vi-VN')}{row.state?' · '+(stateLabels[row.state] || row.state):''}</span></li>)}</ul>
  </section>;
}
