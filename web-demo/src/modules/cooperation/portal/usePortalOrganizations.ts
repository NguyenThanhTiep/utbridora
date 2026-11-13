import { useEffect, useState } from 'react';
import { api } from '../../../api';
import type { PortalSide } from './navigation';

type OrganizationOption = { id: string; name: string; type: 'ENTERPRISE' | 'UNIVERSITY' };
export function usePortalOrganizations(viewerId: string, side: PortalSide, ids: string[]) {
  const key = `${viewerId}:${side}:${[...ids].sort().join(',')}`;
  const [state, setState] = useState<{ key: string; names: Record<string, string>; loading: boolean }>({ key: '', names: {}, loading: true });
  useEffect(() => {
    const controller = new AbortController();
    void api<OrganizationOption[]>('/cooperation/organizations', { signal: controller.signal }).then(rows => {
      if (controller.signal.aborted) return;
      const type = side === 'enterprise' ? 'ENTERPRISE' : 'UNIVERSITY';
      const names = Object.fromEntries(rows.filter(row => ids.includes(row.id) && row.type === type && row.name.trim()).map(row => [row.id, row.name]));
      setState({ key, names, loading: false });
    }).catch(() => { if (!controller.signal.aborted) setState({ key, names: {}, loading: false }); });
    return () => controller.abort();
  }, [key]);
  return state.key === key ? state : { names: {} as Record<string, string>, loading: true };
}
