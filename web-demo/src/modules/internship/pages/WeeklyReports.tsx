import { useEffect, useState } from 'react';
import { accessLost, workflowError, workflowPages, workflowRead } from '../workflow-api';
import { mondayDate, textWithin, uuidPattern, validVersion, type InternshipTask, type WeeklyReport, type ReportReview, type ReportFile } from '../workflow';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';
import { ReportEvidence } from './ReportEvidence';

export function WeeklyReports({ contextId, caseIds, student, mentor, denied }: {
  contextId?: string; caseIds?: string[]; student: boolean; mentor: boolean; denied: () => void;
}) {
  const [choices, setChoices] = useState(caseIds || []), [caseId, setCaseId] = useState(''), [lookup, setLookup] = useState('');
  const [message, setMessage] = useState(''), [attempt, setAttempt] = useState(0), [lookupId, setLookupId] = useState('');
  useEffect(() => {
    if (caseIds) { setChoices(caseIds); return; }
    const c = new AbortController(); setMessage('');
    // All pages, independent of board filters. Canonical API still enforces student ownership.
    void workflowPages<InternshipTask>('/t3/tasks', undefined, c.signal).then(tasks => {
      if (!c.signal.aborted) setChoices([...new Set(tasks.flatMap(task => task.assignments.map(a => a.caseId)))]);
    }).catch(error => { if (!c.signal.aborted) { if (accessLost(error)) denied(); else setMessage(workflowError(error)); } });
    return () => c.abort();
  }, [caseIds, attempt]);
  useEffect(() => {
    if (!lookupId || !student) return;
    const c = new AbortController(); setMessage('');
    void workflowRead<{ caseId: string }>('/t3/cases/' + lookupId, undefined, c.signal).then(value => {
      if (c.signal.aborted) return;
      if (value.caseId !== lookupId) { denied(); return; }
      setChoices(previous => [...new Set([...previous, value.caseId])]); setCaseId(value.caseId);
    }).catch(error => { if (!c.signal.aborted) { if (accessLost(error)) denied(); else setMessage(workflowError(error)); } });
    return () => c.abort();
  }, [lookupId, attempt, student]);
  return <section className="panel stack t3-workflow"><h3>Báo cáo tuần</h3>
    {message && <p role="alert">{message}</p>}
    <label>Hồ sơ cho báo cáo<select value={caseId} onChange={event => setCaseId(event.target.value)}>
      <option value="">Chọn hồ sơ thực tập…</option>{choices.map(id => <option key={id} value={id}>Hồ sơ …{id.slice(-6)}</option>)}
    </select></label>
    {student && <details><summary>Tìm hồ sơ chưa có nhiệm vụ</summary><form onSubmit={event => { event.preventDefault();
      if (uuidPattern.test(lookup)) { setLookupId(lookup); setAttempt(n => n + 1); }
    }}><label>Mã hồ sơ thực tập<input required value={lookup} onChange={event => setLookup(event.target.value)} /></label>
      <button disabled={!uuidPattern.test(lookup)}>Xác nhận hồ sơ</button></form></details>}
    {caseId && choices.includes(caseId) && <ReportList key={caseId} caseId={caseId} contextId={contextId} student={student} mentor={mentor} denied={denied} />}
  </section>;
}

function ReportList({ caseId, contextId, student, mentor, denied }: {
  caseId: string; contextId?: string; student: boolean; mentor: boolean; denied: () => void;
}) {
  const [items, setItems] = useState<WeeklyReport[]>([]), [message, setMessage] = useState(''), [loading, setLoading] = useState(true);
  const [refresh, setRefresh] = useState(0), [selected, setSelected] = useState(''), [week, setWeek] = useState('');
  const [completedWork, setCompletedWork] = useState(''), [difficulties, setDifficulties] = useState(''), [nextPlan, setNextPlan] = useState('');
  const command = useWorkflowCommand(contextId, denied);
  useEffect(() => {
    const c = new AbortController(); setLoading(true); setMessage('');
    void workflowPages<WeeklyReport>('/t3/reports?caseId=' + caseId, contextId, c.signal).then(value => {
      if (c.signal.aborted) return;
      if (value.some(item => item.caseId !== caseId)) { denied(); return; }
      setItems(value);
    }).catch(error => { if (!c.signal.aborted) { setItems([]); setSelected(''); if (accessLost(error)) denied(); else setMessage(workflowError(error)); } })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [caseId, contextId, refresh]);
  const valid = mondayDate(week) && reportTextValid(completedWork, difficulties, nextPlan);
  return <div className="stack"><button className="secondary" disabled={loading || command.busy} onClick={() => setRefresh(n => n + 1)}>Tải danh sách báo cáo</button>
    {loading && <p role="status">Đang tải báo cáo…</p>}{message && <p role="alert">{message}</p>}
    {!loading && !message && items.length === 0 && <p>Chưa có báo cáo cho hồ sơ này.</p>}
    {items.map(item => <button className="secondary" key={item.reportId} onClick={() => setSelected(item.reportId)}>
      Tuần {item.weekStart} · Bản {item.revisionNo} · {item.state}</button>)}
    {selected && <ReportEditor key={selected} reportId={selected} caseId={caseId} contextId={contextId} student={student} mentor={mentor}
      denied={denied} changed={() => setRefresh(n => n + 1)} />}
    {student && <form onSubmit={event => { event.preventDefault(); if (!valid) return;
      command.run<WeeklyReport>('/t3/reports', { caseId, weekStart: week, completedWork: completedWork.trim(),
        difficulties: difficulties.trim(), nextPlan: nextPlan.trim(), evidenceFileIds: [] }, value => {
        if (value.caseId !== caseId) { denied(); return; }
        setSelected(value.reportId); setRefresh(n => n + 1); setCompletedWork(''); setDifficulties(''); setNextPlan('');
      });
    }}><fieldset disabled={command.busy || command.pending}><legend>Tạo báo cáo tuần</legend>
      <label>Thứ Hai bắt đầu tuần<input type="date" required value={week} onChange={event => setWeek(event.target.value)} /></label>
      {week && !mondayDate(week) && <p role="alert">Chọn ngày thứ Hai. Tuần phải giao với thời gian thực tập.</p>}
      <ReportFields completedWork={completedWork} difficulties={difficulties} nextPlan={nextPlan}
        completed={setCompletedWork} difficulty={setDifficulties} plan={setNextPlan} />
      <button disabled={!valid}>Tạo bản nháp báo cáo</button></fieldset></form>}
    <CommandFeedback command={command} />
  </div>;
}

function ReportEditor({ reportId, caseId, contextId, student, mentor, denied, changed }: {
  reportId: string; caseId: string; contextId?: string; student: boolean; mentor: boolean; denied: () => void; changed: () => void;
}) {
  const [current, setCurrent] = useState<WeeklyReport | null>(null), [view, setView] = useState<WeeklyReport | null>(null);
  const [revision, setRevision] = useState(''), [refresh, setRefresh] = useState(0), [message, setMessage] = useState(''), [loading, setLoading] = useState(true);
  const [completedWork, setCompletedWork] = useState(''), [difficulties, setDifficulties] = useState(''), [nextPlan, setNextPlan] = useState('');
  const [dirty, setDirty] = useState(false), [comment, setComment] = useState(''), [reason, setReason] = useState('');
  const [review, setReview] = useState<ReportReview | null>(null), [reviewError, setReviewError] = useState('');
  const [evidence, setEvidence] = useState<string[]>([]), [upload, setUpload] = useState<File | null>(null), [uploadReset, setUploadReset] = useState(0);
  const command = useWorkflowCommand(contextId, denied);
  useEffect(() => {
    const c = new AbortController(); setLoading(true); setMessage(''); setView(null); setReview(null); setReviewError('');
    void (async () => {
      const latest = await workflowRead<WeeklyReport>('/t3/reports/' + reportId, contextId, c.signal);
      const selected = revision ? await workflowRead<WeeklyReport>('/t3/reports/' + reportId + '?revisionNo=' + revision, contextId, c.signal) : latest;
      if (c.signal.aborted) return;
      if ([latest, selected].some(item => item.caseId !== caseId || item.reportId !== reportId)
        || revision && selected.revisionNo !== Number(revision)) { denied(); return; }
      setCurrent(latest); setView(selected);
      // Explicit refresh after 409 updates expectedVersion but preserves unsaved text.
      if (!dirty) { setCompletedWork(latest.completedWork); setDifficulties(latest.difficulties); setNextPlan(latest.nextPlan); setEvidence(latest.evidenceFileIds); }
      if (selected.state === 'REVIEWED') {
        try {
          const feedback = await workflowRead<ReportReview>('/t3/reports/' + reportId + '/reviews?revisionNo=' + selected.revisionNo, contextId, c.signal);
          if (c.signal.aborted) return;
          if (feedback.reportId !== reportId || feedback.revisionNo !== selected.revisionNo) { denied(); return; }
          setReview(feedback);
        } catch (error) { if (!c.signal.aborted) { if (accessLost(error)) denied(); else setReviewError(workflowError(error)); } }
      }
    })().catch(error => { if (!c.signal.aborted) { setCurrent(null); if (accessLost(error)) denied(); else setMessage(workflowError(error)); } })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [reportId, caseId, contextId, revision, refresh]);
  const done = () => { setDirty(false); setRefresh(n => n + 1); changed(); };
  const blocked = loading || command.busy || command.pending || !current || !validVersion(current.version);
  const isCurrent = !!view && !!current && !revision && view.revisionNo === current.revisionNo;
  const uploadValid = !!upload && upload.size > 0 && upload.size <= 10 * 1024 * 1024
    && ['application/pdf', 'image/png', 'image/jpeg'].includes(upload.type);
  return <section className="panel stack" aria-label="Chi tiết báo cáo">
    <h4>Chi tiết báo cáo tuần</h4>
    <button className="secondary" disabled={command.busy} onClick={() => setRefresh(n => n + 1)}>Tải phiên bản báo cáo hiện tại</button>
    <label>Phiên bản báo cáo<select disabled={command.busy || command.pending} value={revision} onChange={event => setRevision(event.target.value)}>
      <option value="">Bản hiện tại</option>{Array.from({ length: current?.revisionNo || 0 }, (_, i) => i + 1)
        .map(value => <option key={value} value={value}>Bản {value} (chỉ đọc)</option>)}
    </select></label>
    {loading && <p role="status">Đang tải phiên bản báo cáo…</p>}{message && <p role="alert">{message}</p>}
    <CommandFeedback command={command} />
    {view && <><p>Tuần {view.weekStart} · Bản {view.revisionNo} · {view.state} · Version {view.version}</p>
      <p>Hạn nộp: {new Date(view.dueAt).toLocaleString('vi-VN')} · {view.lateFlag ? 'Nộp trễ' : 'Chưa trễ'}</p>
      {view.submittedAt && <p>Đã nộp: {new Date(view.submittedAt).toLocaleString('vi-VN')}</p>}
      {view.reviewedAt && <p>Đã nhận xét: {new Date(view.reviewedAt).toLocaleString('vi-VN')}</p>}
      <ReportEvidence key={view.revisionNo} reportId={reportId} contextId={contextId}
        fileIds={student && isCurrent && view.state === 'DRAFT' ? evidence : view.evidenceFileIds}
        editable={student && isCurrent && view.state === 'DRAFT'} disabled={!!blocked}
        changed={ids => { setDirty(true); setEvidence(ids); }} denied={denied} />
      {reviewError && <p role="alert">Không tải được nhận xét đã lưu. {reviewError}</p>}
      {review && <section aria-label="Nhận xét báo cáo đã lưu"><h5>Nhận xét của Mentor · Bản {review.revisionNo}</h5>
        <p>Người nhận xét: {review.reviewerUserId} · {new Date(review.reviewedAt).toLocaleString('vi-VN')}</p>
        <p className="t3-preserve-text">{review.comment}</p></section>}
      {student && isCurrent && view.state === 'DRAFT' ? <>
        <form onSubmit={event => { event.preventDefault(); if (!current || !reportTextValid(completedWork, difficulties, nextPlan)) return;
          command.run('/t3/reports/' + reportId, { expectedVersion: current.version, completedWork: completedWork.trim(),
            difficulties: difficulties.trim(), nextPlan: nextPlan.trim(), evidenceFileIds: evidence }, done, 'PATCH');
        }}><fieldset disabled={blocked}><legend>Sửa bản nháp hiện tại</legend>
          <ReportFields completedWork={completedWork} difficulties={difficulties} nextPlan={nextPlan}
            completed={v => { setDirty(true); setCompletedWork(v); }} difficulty={v => { setDirty(true); setDifficulties(v); }} plan={v => { setDirty(true); setNextPlan(v); }} />
          <button disabled={!dirty || !reportTextValid(completedWork, difficulties, nextPlan)}>Lưu bản nháp</button></fieldset></form>
        <form onSubmit={event => { event.preventDefault(); if (!uploadValid || !upload || evidence.length >= 20) return;
          command.upload<ReportFile>(reportId, upload, file => {
            if (file.resource.resourceId !== reportId || file.resource.resourceType !== 'WEEKLY_REPORT' || file.purpose !== 'WEEKLY_REPORT') { denied(); return; }
            setEvidence(ids => [...new Set([...ids, file.fileId])]); setDirty(true); setUpload(null); setUploadReset(n => n + 1);
          });
        }}><fieldset disabled={!!blocked || evidence.length >= 20}><legend>Thêm minh chứng vào bản nháp</legend>
          <label>Chọn tệp minh chứng<input key={uploadReset} type="file" accept=".pdf,.png,.jpg,.jpeg" onChange={event => setUpload(event.target.files?.[0] || null)} /></label>
          <p>PDF, PNG hoặc JPEG; tối đa 10 MiB/tệp, 20 tệp/báo cáo. Sau khi tải lên, lưu bản nháp để gắn minh chứng.</p>
          {upload && !uploadValid && <p role="alert">Tệp phải đúng định dạng, không rỗng và không vượt quá 10 MiB.</p>}
          <button disabled={!uploadValid}>Tải lên minh chứng</button></fieldset></form>
        <button disabled={blocked || dirty} onClick={() => current && command.run('/t3/reports/' + reportId + '/submit', { expectedVersion: current.version }, done)}>Nộp báo cáo tuần</button>
        {dirty && <p>Lưu nội dung vừa sửa trước khi nộp báo cáo.</p>}
      </> : <dl className="t3-preserve-text"><dt>Việc đã làm</dt><dd>{view.completedWork}</dd><dt>Khó khăn</dt><dd>{view.difficulties || 'Không có'}</dd><dt>Kế hoạch tiếp theo</dt><dd>{view.nextPlan}</dd></dl>}
      {student && isCurrent && ['SUBMITTED', 'REVIEWED'].includes(view.state) && <form onSubmit={event => {
        event.preventDefault(); if (current && /^[A-Z0-9_]{3,64}$/.test(reason)) command.run('/t3/reports/' + reportId + '/revisions', { expectedVersion: current.version, reasonCode: reason }, done);
      }}><fieldset disabled={blocked}><label>Mã lý do sửa báo cáo<input required value={reason} onChange={event => setReason(event.target.value)} /></label>
        <p>Dùng 3–64 ký tự A–Z, 0–9 hoặc dấu gạch dưới, ví dụ UPDATE_PROGRESS.</p>
        <button disabled={!/^[A-Z0-9_]{3,64}$/.test(reason)}>Tạo bản sửa báo cáo</button></fieldset></form>}
      {mentor && isCurrent && view.state === 'SUBMITTED' && <form onSubmit={event => {
        event.preventDefault(); if (current && textWithin(comment, 1, 2000)) command.run('/t3/reports/' + reportId + '/reviews',
          { expectedVersion: current.version, comment: comment.trim() }, () => { setComment(''); done(); });
      }}><fieldset disabled={blocked}><label>Nhận xét báo cáo<textarea required value={comment} onChange={event => setComment(event.target.value)} /></label>
        <button disabled={!textWithin(comment, 1, 2000)}>Gửi nhận xét báo cáo</button></fieldset></form>}
    </>}
  </section>;
}

function reportTextValid(completed: string, difficulty: string, plan: string) {
  return textWithin(completed, 1, 10000) && textWithin(difficulty, 0, 5000) && textWithin(plan, 1, 5000);
}
function ReportFields({ completedWork, difficulties, nextPlan, completed, difficulty, plan }: {
  completedWork: string; difficulties: string; nextPlan: string; completed: (v: string) => void; difficulty: (v: string) => void; plan: (v: string) => void;
}) {
  return <><label>Việc đã làm<textarea required value={completedWork} onChange={e => completed(e.target.value)} /></label>
    <label>Khó khăn<textarea value={difficulties} onChange={e => difficulty(e.target.value)} /></label>
    <label>Kế hoạch tiếp theo<textarea required value={nextPlan} onChange={e => plan(e.target.value)} /></label>
    <p className="muted">Giới hạn: việc đã làm 10.000 ký tự; khó khăn và kế hoạch 5.000 ký tự mỗi mục.</p></>;
}
