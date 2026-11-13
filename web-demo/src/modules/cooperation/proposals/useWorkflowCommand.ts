import { useEffect, useRef, useState } from 'react';
import { api, ApiError } from '../../../api';
import { newIdempotencyKey } from '../../student/api';
import { workflowError } from './api';

/** A lost response keeps the exact body and key until the same command is resolved. */
export function useWorkflowCommand<T>(org: string, success: (value: T) => void, invalidated: (error: unknown) => void,
  { guardNavigation = true }: { guardNavigation?: boolean } = {}) {
  const [busy, setBusy] = useState(false), [retryable, setRetryable] = useState(false), [message, setMessage] = useState('');
  const running = useRef(false), mounted = useRef(true);
  const pending = useRef<{ path: string; method: string; body: string; key: string } | null>(null);
  const handlers = useRef({ success, invalidated }); handlers.current = { success, invalidated };
  useEffect(() => { mounted.current = true; return () => { mounted.current = false; }; }, []);
  useEffect(() => {
    if (!guardNavigation) return;
    const leave = (event: Event) => {
      if (running.current || pending.current) {
        event.preventDefault();setMessage('Đang xử lý hoặc chưa xác định được kết quả. Hãy thử lại thao tác đã gửi trước khi rời màn hình.');
      }
    };
    const unload = (event: BeforeUnloadEvent) => {
      if (running.current || pending.current) {event.preventDefault();event.returnValue = '';}
    };
    window.addEventListener('demand-before-leave', leave);window.addEventListener('beforeunload', unload);
    return () => {window.removeEventListener('demand-before-leave', leave);window.removeEventListener('beforeunload', unload);};
  }, [guardNavigation]);
  const execute = async () => {
    if (running.current || !pending.current) return;
    const request = pending.current; running.current = true; setBusy(true); setMessage('');
    try {
      const value = await api<T>(request.path, { method: request.method, body: request.body,
        headers: { 'X-Organization-Id': org, 'Idempotency-Key': request.key } });
      pending.current = null;
      if (mounted.current) { setRetryable(false); handlers.current.success(value); }
    } catch (error) {
      if (!mounted.current) return;
      const definitive = error instanceof ApiError && [400, 401, 403, 404, 409, 422].includes(error.status)
        && error.errorCode !== 'REQUEST_IN_PROGRESS';
      if (definitive) { pending.current = null; setRetryable(false); handlers.current.invalidated(error); }
      else setRetryable(true);
      setMessage(workflowError(error));
    } finally { running.current = false; if (mounted.current) setBusy(false); }
  };
  return { busy, retryable, blocked: busy || retryable, message, retry: execute,
    submit: async (path: string, method: string, payload: unknown) => {
      if (running.current || pending.current) return;
      pending.current = { path, method, body: JSON.stringify(payload), key: newIdempotencyKey() };
      await execute();
    },
  };
}
