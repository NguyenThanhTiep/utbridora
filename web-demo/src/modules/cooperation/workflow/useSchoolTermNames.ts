import { useEffect, useState } from 'react';
import { readAll } from '../portal/data';

export function useSchoolTermNames(org: string, enabled: boolean) {
  const [result, setResult] = useState<{ org: string; names: Record<string, string> }>({ org: '', names: {} });
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    void readAll<{ termId: string; schoolOrgId: string; termCode: string }>(org,
      '/terms?' + new URLSearchParams({ schoolOrgId: org }), controller.signal)
      .then(rows => {
        if (!controller.signal.aborted) setResult({ org, names: Object.fromEntries(rows
          .filter(row => row.schoolOrgId === org && row.termId && row.termCode).map(row => [row.termId, row.termCode])) });
      }).catch(() => { if (!controller.signal.aborted) setResult({ org, names: {} }); });
    return () => controller.abort();
  }, [org, enabled]);
  return enabled && result.org === org ? result.names : {};
}
