import { useEffect, useState } from 'react';
import { api } from '../api';

type OrganizationOption = { id: string; name: string; type: 'ENTERPRISE' | 'UNIVERSITY' };

export function useOrganizationNames(enabled = true): Record<string, string> {
  const [names, setNames] = useState<Record<string, string>>({});
  useEffect(() => {
    if (!enabled) return;
    const controller = new AbortController();
    void api<OrganizationOption[]>('/cooperation/organizations', { signal: controller.signal })
      .then(rows => {
        if (controller.signal.aborted || !Array.isArray(rows)) return;
        const map = Object.fromEntries(rows.filter(row => row.id && row.name?.trim()).map(row => [row.id, row.name.trim()]));
        setNames(map);
      })
      .catch(() => { /* Keep silent fallback to reference IDs when catalog lookup is unavailable. */ });
    return () => controller.abort();
  }, [enabled]);
  return enabled ? names : {};
}
