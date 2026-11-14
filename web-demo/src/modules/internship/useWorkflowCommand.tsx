import { useEffect, useRef, useState } from 'react';
import { accessLost, executeAttempt, newAttempt, uncertainOutcome, workflowError, type WorkflowAttempt } from './workflow-api';
import { newIdempotencyKey } from '../student/api';

type Pending = { attempt: WorkflowAttempt; success: (value: unknown) => void };
export function useWorkflowCommand(contextId: string | undefined, denied: () => void) {
  const lifecycle = useRef<AbortController | null>(null), active = useRef(false), saved = useRef<Pending | null>(null);
  const [busy, setBusy] = useState(false), [pending, setPending] = useState(false), [message, setMessage] = useState('');
  useEffect(() => { const c = new AbortController(); lifecycle.current = c; return () => { c.abort(); saved.current = null; }; }, []);
  const execute = async (item: Pending) => {
    const signal = lifecycle.current?.signal; if (!signal || signal.aborted || active.current) return;
    active.current = true; setBusy(true); setMessage('');
    try {
      const value = await executeAttempt(item.attempt, contextId, signal);
      if (signal.aborted) return;
      saved.current = null; setPending(false); item.success(value);
    } catch (error) {
      if (signal.aborted) return;
      if (accessLost(error)) { saved.current = null; denied(); return; }
      saved.current = uncertainOutcome(error) ? item : null;
      setPending(saved.current !== null); setMessage(workflowError(error));
    } finally { active.current = false; if (!signal.aborted) setBusy(false); }
  };
  return { busy, pending, message, setMessage,
    run: <T,>(path: string, payload: unknown, success: (value: T) => void, method = 'POST') => {
      if (saved.current || active.current) return;
      const item = { attempt: newAttempt(path, method, payload), success: success as (value: unknown) => void };
      // Ref prevents two clicks before React has rendered the disabled button.
      saved.current = item; void execute(item);
    },
    upload: <T,>(resourceId: string, file: File, success: (value: T) => void) => {
      if (saved.current || active.current) return;
      const body = new FormData(); body.set('file', file); body.set('resourceType', 'WEEKLY_REPORT');
      body.set('resourceId', resourceId); body.set('purpose', 'WEEKLY_REPORT');
      const item: Pending = { attempt: { path: '/files', method: 'POST', body, key: newIdempotencyKey() }, success: success as (value: unknown) => void };
      saved.current = item; void execute(item);
    },
    retry: () => { if (saved.current) void execute(saved.current); }
  };
}
export function CommandFeedback({ command }: { command: ReturnType<typeof useWorkflowCommand> }) {
  return <>{command.message && <p role="alert">{command.message}</p>}
    {command.busy && <p role="status">Đang xử lý…</p>}
    {command.pending && <div><p>Thao tác chưa có kết quả chắc chắn. Thử lại sẽ gửi đúng nội dung và mã yêu cầu trước đó.</p>
      <button type="button" disabled={command.busy} onClick={command.retry}>Thử lại thao tác vừa gửi</button></div>}</>;
}
