import { useEffect, useRef, useState, type FormEvent } from 'react';
import { api } from '../../../api';
import { Notice } from '../../../components';
import { newIdempotencyKey } from '../../student/api';
import type { WorkspaceAssignment, InternshipGroup, WorkspacePage } from '../types';
type Assignment = { assignmentId: string; caseId: string; studentUserId: string; state: string; version: number };
type Task = { taskId: string; title: string; description: string; groupId: string; dueAt: string; priority: 'LOW' | 'NORMAL' | 'HIGH'; assignments: Assignment[] };
type CalendarEvent = { taskId: string; assignmentId: string | null; title: string; dueAt: string; state: string | null };
const states = ['NEW', 'IN_PROGRESS', 'SUBMITTED', 'REVISION_REQUIRED', 'COMPLETED'];
const names: Record<string, string> = { NEW: 'Chưa bắt đầu', IN_PROGRESS: 'Đang làm', SUBMITTED: 'Đã nộp', REVISION_REQUIRED: 'Cần sửa', COMPLETED: 'Hoàn thành' };
export function TaskBoard({ assignment, group, student = false }: { assignment?: WorkspaceAssignment; group?: InternshipGroup; student?: boolean }) {
  const [tasks, setTasks] = useState<Task[]>([]), [refresh, setRefresh] = useState(0), [page, setPage] = useState(0), [total, setTotal] = useState(0);
  const [state, setState] = useState(''), [person, setPerson] = useState(''), [until, setUntil] = useState(''), [selected, setSelected] = useState('');
  const [message, setMessage] = useState(''), [busy, setBusy] = useState(false), [events, setEvents] = useState<CalendarEvent[]>([]);
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7));
  const pending = useRef(new Map<string, string>());
  const headers: Record<string, string> = assignment ? { 'X-Context-Assignment-Id': assignment.id } : {};
  useEffect(() => {
    const c = new AbortController(); setMessage(''); setTasks([]);
    const params = new URLSearchParams({ page: String(page), size: '25' });
    if (group) params.set('groupId', group.groupId); if (state) params.set('state', state); if (until) params.set('dueUntil', new Date(until + 'T23:59:59').toISOString());
    void api<WorkspacePage<Task>>('/t3/tasks?' + params, { signal: c.signal, headers }).then(p => { if (!c.signal.aborted) { setTasks(p.content); setTotal(p.totalPages); } }).catch(() => { if (!c.signal.aborted) setMessage('Không tải được bảng việc trong phạm vi hiện tại.'); });
    return () => c.abort();
  }, [assignment?.id, group?.groupId, refresh, page, state, until]);
  useEffect(() => {
    const c = new AbortController(); setEvents([]); const from = new Date(month + '-01T00:00:00'); if (!Number.isFinite(from.getTime())) return;
    const end = new Date(from); end.setMonth(end.getMonth() + 1);
    void (async () => { const all: CalendarEvent[] = []; let index = 0, pages = 1; while (index < pages) {
      const p = await api<WorkspacePage<CalendarEvent>>('/t3/calendar?' + new URLSearchParams({ from: from.toISOString(), until: end.toISOString(), page: String(index), size: '100' }), { headers, signal: c.signal }); all.push(...p.content); index++; pages = p.totalPages;
    } if (!c.signal.aborted) setEvents(all); })().catch(() => { if (!c.signal.aborted) setMessage('Chưa tải được lịch công việc.'); });
    return () => c.abort();
  }, [assignment?.id, month, refresh]);
  const command = async (path: string, method: string, data: unknown) => {
    const body = JSON.stringify(data), ref = path + body; if (!pending.current.has(ref)) pending.current.set(ref, newIdempotencyKey());
    setBusy(true); setMessage(''); try { await api(path, { method, body, headers: { ...headers, 'Idempotency-Key': pending.current.get(ref)! } }); pending.current.delete(ref); setRefresh(n => n + 1); }
    catch { setMessage('Chưa thực hiện được thao tác. Kiểm tra phân công, trạng thái thực tập và phiên bản dữ liệu rồi thử lại.'); }
    finally { setBusy(false); }
  };
  const detail = tasks.find(t => t.taskId === selected), people = [...new Set(tasks.flatMap(t => t.assignments.map(a => a.caseId)))];
  const date = new Date(month + '-01T00:00:00'), days = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
  return <div className="stack workflow-surface"><h3>Bảng công việc</h3><Notice message={message} tone="error" />
    <div className="button-row"><label>Trạng thái<select value={state} onChange={e => { setState(e.target.value); setPage(0); }}><option value="">Tất cả</option>{states.map(s => <option value={s} key={s}>{names[s]}</option>)}</select></label>
      <label>Hồ sơ được giao<select value={person} onChange={e => setPerson(e.target.value)}><option value="">Tất cả</option>{people.map(p => <option key={p} value={p}>Hồ sơ …{p.slice(-6)}</option>)}</select></label>
      <label>Hạn đến ngày<input type="date" value={until} onChange={e => { setUntil(e.target.value); setPage(0); }} /></label>
      <button className="secondary" disabled={busy} onClick={() => setRefresh(n => n + 1)}>Tải lại bảng việc</button></div>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(170px,1fr))', gap: 12 }}>{states.map(s => <section className="panel" key={s}><h4>{names[s]}</h4>
      {tasks.flatMap(t => t.assignments.filter(a => a.state === s && (!state || state === s) && (!person || person === a.caseId)).map(a => <article key={a.assignmentId}>
        <button type="button" className="text-button" onClick={() => setSelected(t.taskId)}>{t.title}</button><p>Hạn: {new Date(t.dueAt).toLocaleString('vi-VN')}</p>
        {student && ['NEW', 'REVISION_REQUIRED'].includes(a.state) && <button disabled={busy} onClick={() => void command('/t3/assignments/' + a.assignmentId + '/status', 'PATCH', { expectedVersion: a.version, state: 'IN_PROGRESS' })}>Bắt đầu làm</button>}
      </article>))}</section>)}</div>
    <div className="button-row"><button className="secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>Trước</button><span>Trang {page + 1}/{Math.max(1, total)}</span><button className="secondary" disabled={page + 1 >= total} onClick={() => setPage(n => n + 1)}>Sau</button></div>
    {detail && <section className="panel"><h3>{detail.title}</h3><p>{detail.description}</p><p>Ưu tiên: {{ LOW: 'Thấp', NORMAL: 'Bình thường', HIGH: 'Cao' }[detail.priority]} · Hạn: {new Date(detail.dueAt).toLocaleString('vi-VN')}</p><ul>{detail.assignments.map(a => <li key={a.assignmentId}>Hồ sơ …{a.caseId.slice(-6)}: {names[a.state]}</li>)}</ul><button className="secondary" onClick={() => setSelected('')}>Đóng chi tiết</button></section>}
    {assignment?.kind === 'MENTOR' && group && <CreateTask key={group.groupId + ':' + refresh} group={group} busy={busy} command={command} />}
    <section className="panel"><h3>Lịch công việc</h3><label>Tháng<input type="month" value={month} onChange={e => setMonth(e.target.value)} /></label>
      <div style={{ overflowX: 'auto' }}><div role="grid" aria-label="Lịch hạn công việc" style={{ display: 'grid', minWidth: 560, gridTemplateColumns: 'repeat(7,minmax(0,1fr))', gap: 6 }}>
        {['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'].map(day => <strong role="columnheader" key={day}>{day}</strong>)}
        {Array.from({ length: Number.isFinite(date.getTime()) ? (date.getDay() + 6) % 7 : 0 }, (_, n) => <span aria-hidden="true" key={'blank-' + n} />)}
        {Array.from({ length: Number.isFinite(days) ? days : 0 }, (_, n) => { const day = n + 1; return <div role="gridcell" className="panel" key={day}><strong>{day}</strong>{events.filter(e => new Date(e.dueAt).getDate() === day).map(e => <button key={e.taskId + e.assignmentId} className="text-button" onClick={() => { setSelected(e.taskId); if (!tasks.some(t => t.taskId === e.taskId)) void api<Task>('/t3/tasks/' + e.taskId, { headers }).then(t => setTasks(current => [...current, t])).catch(() => setMessage('Không đọc được nhiệm vụ.')); }}>{e.title}{e.state ? ': ' + names[e.state] : ''}</button>)}</div>; })}
      </div></div></section>
  </div>;
}
function CreateTask({ group, busy, command }: { group: InternshipGroup; busy: boolean; command: (path: string, method: string, data: unknown) => Promise<void> }) {
  const [title, setTitle] = useState(''), [description, setDescription] = useState(''), [due, setDue] = useState(''), [priority, setPriority] = useState('NORMAL'), [cases, setCases] = useState<string[]>([]);
  const submit = (event: FormEvent) => { event.preventDefault(); if (cases.length) void command('/t3/tasks', 'POST', { groupId: group.groupId, title, description, dueAt: new Date(due).toISOString(), priority, caseIds: cases, lateAllowed: true }); };
  return <form className="panel" onSubmit={submit}><h3>Giao nhiệm vụ cho nhóm {group.code}</h3><fieldset disabled={busy}>
    <label>Tiêu đề<input required maxLength={200} value={title} onChange={e => setTitle(e.target.value)} /></label><label>Mô tả<textarea required maxLength={10000} value={description} onChange={e => setDescription(e.target.value)} /></label>
    <label>Hạn hoàn thành<input type="datetime-local" required value={due} onChange={e => setDue(e.target.value)} /></label><label>Ưu tiên<select value={priority} onChange={e => setPriority(e.target.value)}><option value="LOW">Thấp</option><option value="NORMAL">Bình thường</option><option value="HIGH">Cao</option></select></label>
    <fieldset><legend>Hồ sơ được giao</legend>{group.caseIds.map(id => <label key={id}><input type="checkbox" checked={cases.includes(id)} onChange={e => setCases(e.target.checked ? [...cases, id] : cases.filter(v => v !== id))} />Hồ sơ …{id.slice(-6)}</label>)}</fieldset>
    <button disabled={!cases.length}>Giao nhiệm vụ</button></fieldset></form>;
}
