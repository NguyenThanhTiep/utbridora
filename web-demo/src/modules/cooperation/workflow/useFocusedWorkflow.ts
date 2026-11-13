import { useEffect, useRef } from 'react';
import { api } from '../../../api';
export function useFocusedWorkflow<T>(org: string, path: string, id: string | undefined, revision: number,
  loaded: (value: T) => void, failed: () => void) {
  const handlers = useRef({ loaded, failed }); handlers.current = { loaded, failed };
  useEffect(() => {
    if (!id) return;
    const controller = new AbortController();
    void api<T>(path + '/' + encodeURIComponent(id), { signal: controller.signal, headers: { 'X-Organization-Id': org } })
      .then(value => { if (!controller.signal.aborted) handlers.current.loaded(value); })
      .catch(() => { if (!controller.signal.aborted) handlers.current.failed(); });
    return () => controller.abort();
  }, [org, path, id, revision]);
}
