import { useEffect, useState } from 'react';
import { readAll } from '../portal/data';

export function useSchoolNames(org: string, enabled = true) {
  const [data, setData] = useState<{ org: string; names: Record<string, string> }>({ org: '', names: {} });
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    void readAll<{ schoolOrgId: string; name: string }>(org, '/enterprise/demand-references/schools', controller.signal)
      .then(rows => { if (!controller.signal.aborted) setData({ org, names: Object.fromEntries(rows.map(row => [row.schoolOrgId, row.name])) }); })
      .catch(() => { if (!controller.signal.aborted) setData({ org, names: {} }); });
    return () => controller.abort();
  }, [org, enabled]);
  return enabled && data.org === org ? data.names : {};
}
