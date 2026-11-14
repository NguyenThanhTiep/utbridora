import { useCallback, useEffect, useState, type FormEvent } from 'react';
import { Notice } from '../../../components';
import type { WorkspaceAssignment, InternshipGroup, WorkspacePage } from '../types';
import type { InternshipTask as Task } from '../workflow';
import { accessLost, workflowError, workflowRead } from '../workflow-api';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { SubmissionWorkspace } from './SubmissionWorkspace';
import { WeeklyReports } from './WeeklyReports';
import '../styles.css';
type CalendarEvent = { taskId: string; assignmentId: string | null; title: string; dueAt: string; state: string | null };
const states = ['NEW', 'IN_PROGRESS', 'SUBMITTED', 'REVISION_REQUIRED', 'COMPLETED'];
const names: Record<string, string> = { NEW: 'Chưa bắt đầu', IN_PROGRESS: 'Đang làm', SUBMITTED: 'Đã nộp', REVISION_REQUIRED: 'Cần sửa', COMPLETED: 'Hoàn thành' };
// showReports=false: S07 sinh viên (T2-10) mở báo cáo tuần ở S08; Mentor/HR vẫn mở báo cáo ngay trong bảng việc.
type BoardProps = { assignment?: WorkspaceAssignment; group?: InternshipGroup; student?: boolean; showReports?: boolean };
export function TaskBoard(props: BoardProps) {
  return <ScopedTaskBoard key={(props.assignment?.id || 'student') + ':' + (props.assignment?.version || '') + ':' + (props.group?.groupId || '')} {...props} />;
}
function ScopedTaskBoard(props: BoardProps) {
  const [denied, setDenied] = useState(false);
  const invalidate = useCallback(() => setDenied(true), []);
  useEffect(() => { window.addEventListener('session-expired', invalidate); return () => window.removeEventListener('session-expired', invalidate); }, [invalidate]);
  return denied ? <section className="panel"><p role="alert">Phiên hoặc quyền truy cập đã thay đổi. Dữ liệu của phạm vi này đã được đóng.</p>
    <button onClick={() => setDenied(false)}>Xác nhận lại quyền truy cập</button></section> : <TaskBoardContent {...props} denied={invalidate} />;
}
function TaskBoardContent({ assignment, group, student = false, showReports = true, denied }: BoardProps & { denied: () => void }) {
  const [tasks, setTasks] = useState<Task[]>([]), [refresh, setRefresh] = useState(0), [page, setPage] = useState(0), [total, setTotal] = useState(0);
  const [state, setState] = useState(''), [person, setPerson] = useState(''), [until, setUntil] = useState(''), [selected, setSelected] = useState('');
  const [message, setMessage] = useState(''), [events, setEvents] = useState<CalendarEvent[]>([]);
  const [artifactAssignment, setArtifactAssignment] = useState(''), [reports, setReports] = useState(false), [calendarTask, setCalendarTask] = useState('');
  const [month, setMonth] = useState(new Date().toISOString().slice(0, 7));
  const mutation = useWorkflowCommand(assignment?.id, denied), busy = mutation.busy || mutation.pending;
  useEffect(() => {
    const c = new AbortController(); setMessage(''); setTasks([]);
    const params = new URLSearchParams({ page: String(page), size: '25' });
    if (group) params.set('groupId', group.groupId); if (state) params.set('state', state); if (until) params.set('dueUntil', new Date(until + 'T23:59:59').toISOString());
    void workflowRead<WorkspacePage<Task>>('/t3/tasks?' + params, assignment?.id, c.signal).then(p => { if (!c.signal.aborted) { setTasks(p.content); setTotal(p.totalPages); } }).catch(error => { if (!c.signal.aborted) { if (accessLost(error)) denied(); else setMessage(workflowError(error)); } });
    return () => c.abort();
  }, [assignment?.id, group?.groupId, refresh, page, state, until]);
  useEffect(() => {
    const c = new AbortController(); setEvents([]); const from = new Date(month + '-01T00:00:00'); if (!Number.isFinite(from.getTime())) return;
    const end = new Date(from); end.setMonth(end.getMonth() + 1);
    void (async () => { const all: CalendarEvent[] = []; let index = 0, pages = 1; while (index < pages) {
      const p = await workflowRead<WorkspacePage<CalendarEvent>>('/t3/calendar?' + new URLSearchParams({ from: from.toISOString(), until: end.toISOString(), page: String(index), size: '100' }), assignment?.id, c.signal); all.push(...p.content); index++; pages = p.totalPages;
    } if (!c.signal.aborted) setEvents(all); })().catch(error => { if (!c.signal.aborted) { if (accessLost(error)) denied(); else setMessage(workflowError(error)); } });
    return () => c.abort();
  }, [assignment?.id, month, refresh]);
  useEffect(() => {
    if (!calendarTask) return;
    const c = new AbortController();
    void workflowRead<Task>('/t3/tasks/' + calendarTask, assignment?.id, c.signal).then(task => {
      if (!c.signal.aborted) { setTasks(current => [...current.filter(t => t.taskId !== task.taskId), task]); setSelected(task.taskId); }
    }).catch(error => { if (!c.signal.aborted) { if (accessLost(error)) denied(); else setMessage(workflowError(error)); } });
    return () => c.abort();
  }, [calendarTask, assignment?.id]);
  const command = async (path: string, method: string, data: unknown) => { mutation.run(path, data, () => setRefresh(n => n + 1), method); };
  const detail = tasks.find(t => t.taskId === selected), people = [...new Set(tasks.flatMap(t => t.assignments.map(a => a.caseId)))];
  const date = new Date(month + '-01T00:00:00'), days = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate();
  return <div className="stack workflow-surface"><h3>Bảng công việc</h3><Notice message={message} tone="error" />
    <CommandFeedback command={mutation} />
    {showReports && <button className="secondary" onClick={() => setReports(value => !value)}>{reports ? 'Đóng báo cáo tuần' : 'Mở báo cáo tuần'}</button>}
    {showReports && reports && <WeeklyReports contextId={assignment?.id} caseIds={group?.caseIds} student={student} mentor={assignment?.kind === 'MENTOR'} denied={denied} />}
    <div className="button-row"><label>Trạng thái<select value={state} onChange={e => { setState(e.target.value); setPage(0); }}><option value="">Tất cả</option>{states.map(s => <option value={s} key={s}>{names[s]}</option>)}</select></label>
      <label>Hồ sơ được giao<select value={person} onChange={e => setPerson(e.target.value)}><option value="">Tất cả</option>{people.map(p => <option key={p} value={p}>Hồ sơ …{p.slice(-6)}</option>)}</select></label>
      <label>Hạn đến ngày<input type="date" value={until} onChange={e => { setUntil(e.target.value); setPage(0); }} /></label>
      <button className="secondary" disabled={busy} onClick={() => setRefresh(n => n + 1)}>Tải lại bảng việc</button></div>
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit,minmax(170px,1fr))', gap: 12 }}>{states.map(s => <section className="panel" key={s}><h4>{names[s]}</h4>
      {tasks.flatMap(t => t.assignments.filter(a => a.state === s && (!state || state === s) && (!person || person === a.caseId)).map(a => <article key={a.assignmentId}>
        <button type="button" className="text-button" onClick={() => { setSelected(t.taskId); setArtifactAssignment(a.assignmentId); setCalendarTask(''); }}>{t.title}</button><p>Hạn: {new Date(t.dueAt).toLocaleString('vi-VN')}</p>
        {student && ['NEW', 'REVISION_REQUIRED'].includes(a.state) && <button disabled={busy} onClick={() => void command('/t3/assignments/' + a.assignmentId + '/status', 'PATCH', { expectedVersion: a.version, state: 'IN_PROGRESS' })}>Bắt đầu làm</button>}
      </article>))}</section>)}</div>
    <div className="button-row"><button className="secondary" disabled={page === 0} onClick={() => setPage(n => n - 1)}>Trước</button><span>Trang {page + 1}/{Math.max(1, total)}</span><button className="secondary" disabled={page + 1 >= total} onClick={() => setPage(n => n + 1)}>Sau</button></div>
    {detail && <section className="panel"><h3>{detail.title}</h3><p>{detail.description}</p><p>Ưu tiên: {{ LOW: 'Thấp', NORMAL: 'Bình thường', HIGH: 'Cao' }[detail.priority]} · Hạn: {new Date(detail.dueAt).toLocaleString('vi-VN')}</p><ul>{detail.assignments.map(a => <li key={a.assignmentId}>
      <button className="text-button" onClick={() => setArtifactAssignment(a.assignmentId)}>Hồ sơ …{a.caseId.slice(-6)}: {names[a.state]} · Bài nộp và trao đổi</button></li>)}</ul>
      {detail.assignments.filter(a => a.assignmentId === artifactAssignment).map(a => <SubmissionWorkspace key={a.assignmentId} taskId={detail.taskId} assignment={a}
        contextId={assignment?.id} student={student} mentor={assignment?.kind === 'MENTOR'} denied={denied}
        updated={value => {
          setTasks(current => current.map(task => task.taskId === detail.taskId ? { ...task, assignments: task.assignments.map(item => item.assignmentId === value.assignmentId ? value : item) } : task));
          setEvents(current => current.map(event => event.assignmentId === value.assignmentId ? { ...event, state: value.state } : event));
        }} />)}
      <button className="secondary" onClick={() => { setSelected(''); setCalendarTask(''); }}>Đóng chi tiết</button></section>}
    {assignment?.kind === 'MENTOR' && group && <CreateTask key={group.groupId + ':' + refresh} group={group} busy={busy} command={command} />}
    <section className="panel"><h3>Lịch công việc</h3><label>Tháng<input type="month" value={month} onChange={e => setMonth(e.target.value)} /></label>
      <div style={{ overflowX: 'auto' }}><div role="grid" aria-label="Lịch hạn công việc" style={{ display: 'grid', minWidth: 560, gridTemplateColumns: 'repeat(7,minmax(0,1fr))', gap: 6 }}>
        {['Thứ 2', 'Thứ 3', 'Thứ 4', 'Thứ 5', 'Thứ 6', 'Thứ 7', 'Chủ nhật'].map(day => <strong role="columnheader" key={day}>{day}</strong>)}
        {Array.from({ length: Number.isFinite(date.getTime()) ? (date.getDay() + 6) % 7 : 0 }, (_, n) => <span aria-hidden="true" key={'blank-' + n} />)}
        {Array.from({ length: Number.isFinite(days) ? days : 0 }, (_, n) => { const day = n + 1; return <div role="gridcell" className="panel" key={day}><strong>{day}</strong>{events.filter(e => new Date(e.dueAt).getDate() === day).map(e => <button key={e.taskId + e.assignmentId} className="text-button" onClick={() => { setSelected(''); setArtifactAssignment(e.assignmentId || ''); setCalendarTask(e.taskId); }}>{e.title}{e.state ? ': ' + names[e.state] : ''}</button>)}</div>; })}
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
