import { useEffect, useState } from 'react';
import { ApiError } from '../../../api';
import type { InternshipTask, Review, Submission, TaskAssignment, TaskComment } from '../workflow';
import { httpsArtifact, textWithin, validVersion } from '../workflow';
import { accessLost, workflowError, workflowPages, workflowRead } from '../workflow-api';
import { CommandFeedback, useWorkflowCommand } from '../useWorkflowCommand';

export function SubmissionWorkspace({ taskId, assignment, contextId, student, mentor, denied, updated }: {
  taskId: string; assignment: TaskAssignment; contextId?: string; student: boolean; mentor: boolean;
  denied: () => void; updated: (value: TaskAssignment) => void;
}) {
  const [current, setCurrent] = useState(assignment), [refresh, setRefresh] = useState(0);
  const [history, setHistory] = useState<Submission[]>([]), [comments, setComments] = useState<TaskComment[]>([]);
  const [historyError, setHistoryError] = useState(''), [commentError, setCommentError] = useState(''), [currentError, setCurrentError] = useState('');
  const [loading, setLoading] = useState(true), [currentReady, setCurrentReady] = useState(false);
  const [url, setUrl] = useState(''), [note, setNote] = useState(''), [comment, setComment] = useState('');
  const [verdict, setVerdict] = useState<Review['verdict']>('REQUEST_REVISION'), [reviewText, setReviewText] = useState('');
  const [selectedReview, setSelectedReview] = useState('');
  const command = useWorkflowCommand(contextId, denied);
  useEffect(() => {
    const c = new AbortController(); setLoading(true); setCurrentReady(false); setCurrentError(''); setHistoryError(''); setCommentError('');
    // Read assignment from its canonical task, not from a remembered optimistic status.
    void workflowRead<InternshipTask>('/t3/tasks/' + taskId, contextId, c.signal).then(task => {
      if (c.signal.aborted) return;
      const value = task.assignments.find(a => a.assignmentId === assignment.assignmentId && a.caseId === assignment.caseId);
      if (!value) { denied(); return; }
      setCurrent(value); updated(value); setCurrentReady(true);
    }).catch(error => { if (!c.signal.aborted) { if (accessLost(error)) denied(); else setCurrentError(workflowError(error)); } });
    void workflowPages<Submission>('/t3/assignments/' + assignment.assignmentId + '/submissions', contextId, c.signal).then(items => {
      if (c.signal.aborted) return;
      if (items.some(item => item.assignmentId !== assignment.assignmentId)) { denied(); return; }
      setHistory(items);
    }).catch(error => { if (!c.signal.aborted) { setHistory([]); if (accessLost(error)) denied(); else setHistoryError(workflowError(error)); } })
      .finally(() => { if (!c.signal.aborted) setLoading(false); });
    void workflowPages<TaskComment>('/t3/assignments/' + assignment.assignmentId + '/comments', contextId, c.signal).then(items => {
      if (c.signal.aborted) return;
      if (items.some(item => item.assignmentId !== assignment.assignmentId)) { denied(); return; }
      setComments(items);
    }).catch(error => { if (!c.signal.aborted) { setComments([]); if (accessLost(error)) denied(); else setCommentError(workflowError(error)); } });
    return () => c.abort();
    // This component is keyed by scope/assignment. Reload retains user-entered text.
  }, [taskId, assignment.assignmentId, contextId, refresh]);
  const reload = () => setRefresh(n => n + 1), blocked = command.busy || command.pending || !currentReady || !validVersion(current.version);
  const reviewedSubmission = history.find(item => item.submissionId === selectedReview)
    || history.reduce<Submission | undefined>((latest, item) => !latest || item.versionNo > latest.versionNo ? item : latest, undefined);
  return <section className="panel stack t3-workflow" aria-label="Bài nộp và trao đổi">
    <h4>Bài nộp · hồ sơ …{current.caseId.slice(-6)}</h4>
    <p>Trạng thái: {current.state} · Phiên bản phân công: {current.version}</p>
    <button type="button" className="secondary" disabled={command.busy} onClick={reload}>Tải phiên bản hiện tại</button>
    {currentError && <p role="alert">{currentError}</p>}
    <CommandFeedback command={command} />
    {student && ['NEW', 'REVISION_REQUIRED'].includes(current.state) && <button disabled={blocked}
      onClick={() => command.run('/t3/assignments/' + current.assignmentId + '/status',
        { expectedVersion: current.version, state: 'IN_PROGRESS' }, reload, 'PATCH')}>Bắt đầu bản làm tiếp theo</button>}
    {student && current.state === 'IN_PROGRESS' && <form onSubmit={event => {
      event.preventDefault(); if (!httpsArtifact(url) || !textWithin(note, 0, 2000)) return;
      command.run('/t3/assignments/' + current.assignmentId + '/submissions',
        { expectedAssignmentVersion: current.version, fileIds: [], url, note: note.trim() || null }, () => { setUrl(''); setNote(''); setSelectedReview(''); reload(); });
    }}><fieldset disabled={blocked}><legend>Nộp bài bằng URL</legend>
      <label>Đường dẫn bài nộp HTTPS<input type="url" required value={url} onChange={event => setUrl(event.target.value)} /></label>
      {url && !httpsArtifact(url) && <p role="alert">Dùng URL https:// hợp lệ, không chứa tài khoản hoặc mật khẩu.</p>}
      <label>Ghi chú bài nộp<textarea value={note} onChange={event => setNote(event.target.value)} /></label>
      {!textWithin(note, 0, 2000) && <p role="alert">Ghi chú tối đa 2.000 ký tự.</p>}
      <button disabled={!httpsArtifact(url) || !textWithin(note, 0, 2000)}>Gửi bài nộp</button>
    </fieldset></form>}
    <h4>Lịch sử bài nộp</h4>
    {loading ? <p role="status">Đang tải bài nộp…</p> : historyError ? <p role="alert">{historyError}</p>
      : history.length === 0 ? <p>Chưa có bài nộp.</p> : history.map(item => <article className="panel" key={item.submissionId}>
        <h5>Bản nộp v{item.versionNo}{item.submissionId === current.currentSubmissionId ? ' · Hiện tại' : ''}</h5>
        <p>{new Date(item.submittedAt).toLocaleString('vi-VN')} · {item.lateFlag ? 'Nộp trễ' : 'Đúng hạn'}</p>
        {item.url && (httpsArtifact(item.url) ? <a href={item.url} target="_blank" rel="noopener noreferrer">Mở URL bản v{item.versionNo}</a> : <p>URL không hợp lệ; không mở liên kết.</p>)}
        {item.note && <p className="t3-preserve-text">{item.note}</p>}
        {item.fileIds.length > 0 && <p>Bài nộp có {item.fileIds.length} tệp. Chức năng mở tệp đang chờ tích hợp.</p>}
        <button type="button" className="secondary" aria-pressed={reviewedSubmission?.submissionId === item.submissionId}
          onClick={() => setSelectedReview(item.submissionId)}>Xem nhận xét bản v{item.versionNo}</button>
        {reviewedSubmission?.submissionId === item.submissionId && <SubmissionFeedback key={item.submissionId}
          submission={item} contextId={contextId} denied={denied} />}
        {mentor && current.state === 'SUBMITTED' && current.currentSubmissionId === item.submissionId && <form onSubmit={event => {
          event.preventDefault(); if (!textWithin(reviewText, 1, 2000)) return;
          command.run<Review>('/t3/submissions/' + item.submissionId + '/reviews',
            { expectedAssignmentVersion: current.version, verdict, comment: reviewText.trim() }, () => { setSelectedReview(item.submissionId); setReviewText(''); reload(); });
        }}><fieldset disabled={blocked}><legend>Nhận xét bản nộp hiện tại</legend>
          <label>Kết quả review<select value={verdict} onChange={event => setVerdict(event.target.value as Review['verdict'])}>
            <option value="REQUEST_REVISION">Yêu cầu sửa</option><option value="ACCEPT">Chấp nhận</option></select></label>
          <label>Nhận xét bài nộp<textarea required value={reviewText} onChange={event => setReviewText(event.target.value)} /></label>
          <button disabled={!textWithin(reviewText, 1, 2000)}>Gửi nhận xét bài nộp</button>
        </fieldset></form>}
      </article>)}
    <h4>Trao đổi về nhiệm vụ</h4>
    {commentError && <p role="alert">{commentError}</p>}
    {comments.map(item => <article key={item.commentId}><p>{item.authorUserId} · {new Date(item.createdAt).toLocaleString('vi-VN')}</p><p className="t3-preserve-text">{item.text}</p></article>)}
    {(student || mentor) && <form onSubmit={event => { event.preventDefault(); if (!textWithin(comment, 1, 2000)) return;
      command.run('/t3/assignments/' + current.assignmentId + '/comments', { text: comment.trim() }, () => { setComment(''); reload(); });
    }}><fieldset disabled={blocked}><label>Nội dung trao đổi<textarea required value={comment} onChange={event => setComment(event.target.value)} /></label>
      <button disabled={!textWithin(comment, 1, 2000)}>Gửi trao đổi</button></fieldset></form>}
  </section>;
}

function SubmissionFeedback({ submission, contextId, denied }: {
  submission: Submission; contextId?: string; denied: () => void;
}) {
  const [review, setReview] = useState<Review | null>(null), [loading, setLoading] = useState(true);
  const [message, setMessage] = useState(''), [retry, setRetry] = useState(0);
  useEffect(() => {
    const c = new AbortController(); setReview(null); setLoading(true); setMessage('');
    // DEV05 additive GET returns one immutable ReviewDto, or 204 when not reviewed.
    void workflowRead<Review | undefined>('/t3/submissions/' + encodeURIComponent(submission.submissionId) + '/reviews', contextId, c.signal)
      .then(value => {
        if (c.signal.aborted) return;
        if (value === undefined) return;
        if (!value || value.submissionId !== submission.submissionId || value.reviewedVersionNo !== submission.versionNo
          || !['ACCEPT', 'REQUEST_REVISION'].includes(value.verdict)) { denied(); return; }
        setReview(value);
      }).catch(error => {
        if (c.signal.aborted) return;
        if (accessLost(error) || error instanceof ApiError && error.status === 404) denied();
        else setMessage(workflowError(error));
      }).finally(() => { if (!c.signal.aborted) setLoading(false); });
    return () => c.abort();
  }, [submission.submissionId, submission.versionNo, contextId, retry]);
  return <section aria-label="Nhận xét bài nộp đã lưu">
    <h5>Nhận xét đã lưu · bản v{submission.versionNo}</h5>
    {loading ? <p role="status">Đang tải nhận xét…</p> : message ? <>
      <p role="alert">{message}</p><button type="button" onClick={() => setRetry(n => n + 1)}>Tải lại nhận xét bài nộp</button>
    </> : review ? <>
      <p>Kết quả: {review.verdict === 'ACCEPT' ? 'Chấp nhận' : 'Yêu cầu sửa'}</p>
      <p>Người nhận xét: {review.reviewerUserId} · {new Date(review.createdAt).toLocaleString('vi-VN')}</p>
      <p className="t3-preserve-text">{review.comment}</p>
    </> : <p>Bản nộp này chưa có nhận xét.</p>}
  </section>;
}
