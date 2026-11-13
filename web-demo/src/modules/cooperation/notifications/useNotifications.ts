import { useCallback, useEffect, useMemo, useState } from 'react';
import type { CooperationRecord, VerificationRecord } from '../WorkflowViews';
import { readAll, type Resource } from '../portal/data';
import type { Demand } from '../types';
import type { EnterpriseProposal, SchoolProposal } from '../proposals/types';
import { workflowError } from '../proposals/api';
import { projectNotifications, readStorageKey, saveReadIds, storedReadIds, type NotificationScope, type ProposalNotificationSource } from './notificationService';

export function useNotifications(scope: NotificationScope, sources: {
  cooperations: Resource<CooperationRecord>; verifications: Resource<VerificationRecord>; demands: Resource<Demand>;
}) {
  const scopeKey = `${scope.side}:${scope.viewerId}:${scope.org}`;
  const [readState, setReadState] = useState<{ key: string; ids: string[] }>({ key: '', ids: [] });
  const [proposals, setProposals] = useState<{ key: string; groups: ProposalNotificationSource[]; loading: boolean; errors: string[] }>({ key: '', groups: [], loading: true, errors: [] });
  useEffect(() => {
    const sync = () => setReadState({ key: scopeKey, ids: storedReadIds(scope.org) });
    const changed = (event: StorageEvent) => { if (event.key === null || event.key === readStorageKey(scope.org)) sync(); };
    sync(); window.addEventListener('storage', changed);
    return () => window.removeEventListener('storage', changed);
  }, [scopeKey, scope.org]);
  useEffect(() => {
    if (sources.cooperations.loading) return;
    const controller = new AbortController();
    const approved = sources.cooperations.items.filter(row => row.state === 'APPROVED');
    setProposals(previous => ({ key: scopeKey, groups: previous.key === scopeKey ? previous.groups : [], loading: true, errors: [] }));
    void (async () => {
      const groups: ProposalNotificationSource[] = [], errors: string[] = [];
      let next = 0;
      await Promise.all(Array.from({ length: Math.min(4, approved.length) }, async () => {
        while (next < approved.length && !controller.signal.aborted) {
          const cooperation = approved[next++];
          try {
            const items = await readAll<EnterpriseProposal | SchoolProposal>(scope.org,
              `/${scope.side}/cooperations/${encodeURIComponent(cooperation.cooperationId)}/proposals${scope.side === 'school' ? '?state=ENTERPRISE_ACCEPTED' : ''}`, controller.signal);
            groups.push({ cooperation, items });
          } catch (error) { if (!controller.signal.aborted) errors.push(workflowError(error)); }
        }
      }));
      if (!controller.signal.aborted) setProposals({ key: scopeKey, groups, loading: false, errors: [...new Set(errors)] });
    })();
    return () => controller.abort();
  }, [scopeKey, scope.org, scope.side, sources.cooperations.items, sources.cooperations.loading]);
  const groups = proposals.key === scopeKey ? proposals.groups : [];
  const items = useMemo(() => projectNotifications(scope, sources.verifications.items, sources.cooperations.items, sources.demands.items, groups),
    [scopeKey, scope.schoolOrgId, scope.termId, sources.verifications.items, sources.cooperations.items, sources.demands.items, groups]);
  const read = useMemo(() => new Set(readState.key === scopeKey ? readState.ids : []), [readState, scopeKey]);
  const markRead = useCallback((ids: string[]) => {
    const allowed = new Set(items.map(row => row.id));
    setReadState(previous => ({ key: scopeKey, ids: [...new Set([...(previous.key === scopeKey ? previous.ids : []), ...saveReadIds(scope.org, ids.filter(id => allowed.has(id)))])] }));
  }, [scopeKey, scope.org, items]);
  const loading = sources.cooperations.loading || sources.verifications.loading || proposals.key !== scopeKey || proposals.loading;
  const errors = [...new Set([sources.cooperations.error, sources.verifications.error, ...proposals.errors].filter(Boolean))];
  return { items, read, unread: items.filter(row => !read.has(row.id)).length, loading, errors, markRead, markAllRead: () => markRead(items.map(row => row.id)) };
}
export type NotificationStore = ReturnType<typeof useNotifications>;
