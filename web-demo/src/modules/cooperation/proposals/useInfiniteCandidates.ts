import { useCallback, useEffect, useRef, useState } from 'react';
import { ApiError } from '../../../api';
import type { ContractPage } from '../../student/types';
import { canLeaveDemand } from '../navigation';
import { readAll } from '../portal/data';
import { scopedRead, workflowError } from './api';
import type { EnterpriseProposal } from './types';

type CandidatePage = ContractPage<EnterpriseProposal>;
type ReadStatus = { scope: string; loading: boolean; loadingMore: boolean; error: string; moreError: string };
const batchSize = 10;

function checkedPage(value: CandidatePage, cooperationId: string, page: number): CandidatePage {
  if (!Array.isArray(value.content) || value.page !== page || !Number.isInteger(value.totalPages) || value.totalPages < 0
    || !Number.isInteger(value.totalElements) || value.totalElements < 0
    || value.content.some(item => !item.proposalItemId || item.cooperationId !== cooperationId)) throw new Error('Unexpected candidate page');
  return { ...value, content: [...new Map(value.content.map(item => [item.proposalItemId, item])).values()] };
}

/** Append bounded server pages without remounting decisions or mixing organization/filter scopes. */
export function useInfiniteCandidates(org: string, cooperationId: string, state: string, focusId: string, revision: number, blocked: boolean) {
  const [restart, setRestart] = useState(0);
  const scope = JSON.stringify([org, cooperationId, state, focusId, revision, restart]);
  const currentScope = useRef(scope); currentScope.current = scope;
  const isBlocked = useRef(blocked); isBlocked.current = blocked;
  const [snapshot, setSnapshot] = useState<{ scope: string; page: CandidatePage } | null>(null);
  const [status, setStatus] = useState<ReadStatus>({ scope, loading: true, loadingMore: false, error: '', moreError: '' });
  const request = useRef<{ scope: string; controller: AbortController } | null>(null);
  const sentinel = useRef<HTMLDivElement>(null);
  const result = snapshot?.scope === scope ? snapshot.page : null;
  const loading = status.scope !== scope || status.loading;
  const loadingMore = status.scope === scope && status.loadingMore;
  const error = status.scope === scope ? status.error : '';
  const moreError = status.scope === scope ? status.moreError : '';
  const hasMore = !!result && !focusId && result.page + 1 < result.totalPages;
  const path = `/enterprise/cooperations/${encodeURIComponent(cooperationId)}/proposals`;

  useEffect(() => {
    const controller = new AbortController(), task = { scope, controller };
    request.current?.controller.abort(); request.current = task;
    setSnapshot(null);
    setStatus({ scope, loading: true, loadingMore: false, error: '', moreError: '' });
    const promise = focusId ? readAll<EnterpriseProposal>(org, path, controller.signal).then(items => {
      const content = items.filter(item => item.proposalItemId === focusId);
      return { content, page: 0, size: batchSize, totalElements: content.length, totalPages: content.length ? 1 : 0 };
    }) : scopedRead<CandidatePage>(org, path + '?' + new URLSearchParams({ page: '0', size: String(batchSize), ...(state ? { state } : {}) }), controller.signal);
    void promise.then(value => {
      if (!controller.signal.aborted && currentScope.current === scope) setSnapshot({ scope, page: checkedPage(value, cooperationId, 0) });
    }).catch(reason => {
      if (!controller.signal.aborted && currentScope.current === scope) setStatus(old => ({ ...old, error: workflowError(reason) }));
    }).finally(() => {
      if (request.current === task) request.current = null;
      if (!controller.signal.aborted && currentScope.current === scope) setStatus(old => ({ ...old, loading: false }));
    });
    return () => { controller.abort(); if (request.current?.scope === scope) {request.current.controller.abort();request.current = null;} };
  }, [org, cooperationId, path, state, focusId, scope]);

  const loadMore = useCallback(async (retry = false) => {
    if (!result || !hasMore || loading || request.current || isBlocked.current || document.hidden || (moreError && !retry) || !canLeaveDemand()) return;
    const controller = new AbortController(), task = { scope, controller }, nextPage = result.page + 1;
    request.current = task;
    setStatus(old => ({ ...old, loadingMore: true, moreError: '' }));
    try {
      const value = checkedPage(await scopedRead<CandidatePage>(org, path + '?' + new URLSearchParams({ page: String(nextPage), size: String(batchSize), ...(state ? { state } : {}) }), controller.signal), cooperationId, nextPage);
      if (controller.signal.aborted || currentScope.current !== scope || isBlocked.current || !canLeaveDemand()) return;
      // A shrinking list can represent revoked shares. Re-read from the start instead of retaining removed rows.
      if (value.totalElements < result.totalElements || (value.totalPages > 0 && nextPage >= value.totalPages)) {
        setSnapshot(null); setRestart(n => n + 1); return;
      }
      setSnapshot(old => old?.scope === scope ? { scope, page: { ...value,
        content: [...new Map([...old.page.content, ...value.content].map(item => [item.proposalItemId, item])).values()],
      } } : old);
    } catch (reason) {
      if (controller.signal.aborted || currentScope.current !== scope) return;
      if (reason instanceof ApiError && [401, 403, 404].includes(reason.status)) {
        setSnapshot(null); setStatus(old => ({ ...old, error: workflowError(reason) }));
      } else setStatus(old => ({ ...old, moreError: workflowError(reason) }));
    } finally {
      if (request.current === task) request.current = null;
      if (!controller.signal.aborted && currentScope.current === scope) setStatus(old => ({ ...old, loadingMore: false }));
    }
  }, [result, hasMore, loading, moreError, scope, org, path, cooperationId, state]);

  useEffect(() => {
    if (!sentinel.current || !hasMore || loading || loadingMore || moreError || blocked || !('IntersectionObserver' in window)) return;
    const observer = new IntersectionObserver(entries => { if (entries.some(entry => entry.isIntersecting)) void loadMore(); }, { rootMargin: '0px 0px 120px 0px' });
    observer.observe(sentinel.current);
    return () => observer.disconnect();
  }, [hasMore, loading, loadingMore, moreError, blocked, loadMore]);

  return { result, loading, loadingMore, error, moreError, hasMore, loadMore, sentinel };
}
