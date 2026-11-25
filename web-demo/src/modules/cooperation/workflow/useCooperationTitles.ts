import { useEffect, useRef, useState } from 'react';
import { api } from '../../../api';
import type { Cooperation, Detail } from './types';

/** Resolve titles through the existing scoped read API; never use enterprise drafts for school labels. */
export function useCooperationTitles(org: string, enabled: boolean, items: Cooperation[]): { key: string; names: Record<string, string>; loading: boolean } {
  const cache = useRef({ org: '', titles: new Map<string, { version: number; title: string }>() });
  const key = org + ':' + enabled + ':' + items.map(item => item.cooperationId + '/' + item.version).join(',');
  const [result, setResult] = useState<{ key: string; names: Record<string, string>; loading: boolean }>({ key: '', names: {}, loading: false });
  useEffect(() => {
    const controller = new AbortController();
    if (cache.current.org !== org) cache.current = { org, titles: new Map() };
    const names: Record<string, string> = {};
    const pending = enabled ? items.filter(item => {
      const saved = cache.current.titles.get(item.cooperationId);
      if (saved?.version === item.version) { names[item.cooperationId] = saved.title; return false; }
      return true;
    }) : [];
    setResult({ key, names, loading: pending.length > 0 });
    let next = 0;
    async function worker() {
      while (!controller.signal.aborted && next < pending.length) {
        const item = pending[next++];
        try {
          const detail = await api<Detail>('/school/cooperations/' + encodeURIComponent(item.cooperationId),
            { signal: controller.signal, headers: { 'X-Organization-Id': org } });
          if (controller.signal.aborted) return;
          if (detail.cooperation.cooperationId !== item.cooperationId || detail.cooperation.demandId !== item.demandId || detail.cooperation.schoolOrgId !== org
            || detail.cooperation.version !== item.version || detail.cooperation.state === 'DRAFT'
            || detail.demand.demandId !== item.demandId || detail.demand.data.schoolOrgId !== org) continue;
          const title = detail.demand.data.title.trim();
          if (!title) continue;
          cache.current.titles.delete(item.cooperationId);
          cache.current.titles.set(item.cooperationId, { version: item.version, title });
          if (cache.current.titles.size > 200) cache.current.titles.delete(cache.current.titles.keys().next().value!);
          setResult(old => old.key === key ? { ...old, names: { ...old.names, [item.cooperationId]: title } } : old);
        } catch { /* A failed title lookup must not block the list or its detail action. */ }
      }
    }
    void Promise.all(Array.from({ length: Math.min(3, pending.length) }, worker)).then(() => {
      if (!controller.signal.aborted) setResult(old => old.key === key ? { ...old, loading: false } : old);
    });
    return () => controller.abort();
  }, [key, org, enabled]);
  return result.key === key ? result : { key, names: {}, loading: enabled && items.length > 0 };
}
