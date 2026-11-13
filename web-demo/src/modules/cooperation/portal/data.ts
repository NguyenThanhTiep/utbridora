import { useCallback, useEffect, useState } from 'react';
import { api } from '../../../api';
import { workflowError } from '../proposals/api';
import type { Demand } from '../types';
import type { CooperationRecord, VerificationRecord } from '../WorkflowViews';
import type { PortalSide } from './navigation';

export type Resource<T> = { items: T[]; loading: boolean; error: string };
type Data = { key: string; demands: Resource<Demand>; cooperations: Resource<CooperationRecord>; verifications: Resource<VerificationRecord> };
const empty = <T,>(loading = true): Resource<T> => ({ items: [], loading, error: '' });

/** Read every page before showing totals; a single page never stands in for a global KPI. */
export async function readAll<T>(org: string, path: string, signal: AbortSignal): Promise<T[]> {
  const rows: T[] = [];
  for (let page = 0; ; page++) {
    const value = await api<{ content: T[]; totalPages: number }>(`${path}${path.includes('?') ? '&' : '?'}page=${page}&size=100`,
      { signal, headers: { 'X-Organization-Id': org } });
    rows.push(...value.content);
    if (page + 1 >= value.totalPages) return rows;
    if (page >= 10000) throw new Error('Không thể tải đầy đủ danh sách.');
  }
}

export function usePortalData(org: string, side: PortalSide) {
  const key = side + ':' + org;
  const [revision, setRevision] = useState(0);
  const [data, setData] = useState<Data>({ key: '', demands: empty(), cooperations: empty(), verifications: empty() });
  const reload = useCallback(() => setRevision(value => value + 1), []);
  useEffect(() => {
    const refreshVisible = () => { if (!document.hidden) reload(); };
    const timer = window.setInterval(refreshVisible, 30000);
    window.addEventListener('cooperation-updated', reload);
    window.addEventListener('cooperation-data-updated', reload);
    window.addEventListener('focus', refreshVisible); document.addEventListener('visibilitychange', refreshVisible);
    return () => { window.clearInterval(timer); window.removeEventListener('cooperation-updated', reload); window.removeEventListener('cooperation-data-updated', reload);
      window.removeEventListener('focus', refreshVisible); document.removeEventListener('visibilitychange', refreshVisible); };
  }, [reload]);
  useEffect(() => {
    const controller = new AbortController();
    // Background refresh must not unmount a form with an uncertain command still awaiting retry.
    setData(previous => previous.key === key ? previous : { key, demands: empty(side === 'enterprise'), cooperations: empty(), verifications: empty() });
    function load(name: 'demands' | 'cooperations' | 'verifications', path: string) {
      void readAll(org, path, controller.signal).then(items => {
        if (!controller.signal.aborted) setData(old => ({ ...old, [name]: { items, loading: false, error: '' } }));
      }).catch(error => {
        if (!controller.signal.aborted) setData(old => ({ ...old, [name]: { items: [], loading: false, error: workflowError(error) } }));
      });
    }
    if (side === 'enterprise') load('demands', '/enterprise/demands');
    load('cooperations', `/${side}/cooperations`);
    load('verifications', `/${side}/verifications`);
    return () => controller.abort();
  }, [key, org, side, revision]);
  return { ...(data.key === key ? data : { key, demands: empty<Demand>(), cooperations: empty<CooperationRecord>(), verifications: empty<VerificationRecord>() }), reload };
}
