import { useEffect, useState } from 'react';
import { workflowError, workflowRead } from '../workflow-api';
import type { ContextAssignment, WorkspacePage } from '../types';

export const managementBase = '/t3/assignment-management';
export type ManagementScope = { id: string; mode: 'SCHOOL' | 'COORDINATOR' | 'HR'; schoolOrgId: string;
  enterpriseOrgId: string | null; termId: string; termCode: string; validFrom: string; validUntil: string; contextId: string | null };
export type Member = { userId: string; displayName: string };
export type ManagedAssignment = { assignment: ContextAssignment; displayName: string; effective: boolean; canRevoke: boolean };
export type GroupOption = { groupId: string; code: string; cohortCode: string };
export type CaseOption = { caseId: string; groupId: string; groupCode: string; state: string; version: number };
export function useManagementRead<T>(path: string | null, contextId?: string, revision = 0) {
  const [state, setState] = useState<{ path: string | null; contextId?: string; revision: number; data?: T; error: string; loading: boolean }>(
    { path: null, revision: -1, error: '', loading: false });
  useEffect(() => {
    const c = new AbortController();
    setState({ path, contextId, revision, error: '', loading: !!path });
    if (path) void workflowRead<T>(path, contextId, c.signal)
      .then(data => { if (!c.signal.aborted) setState({ path, contextId, revision, data, error: '', loading: false }); })
      .catch(error => { if (!c.signal.aborted) setState({ path, contextId, revision, error: workflowError(error), loading: false }); });
    return () => c.abort();
  }, [path, contextId, revision]);
  return state.path === path && state.contextId === contextId && state.revision === revision ? state : { data: undefined, error: '', loading: !!path };
}
export function ManagementPager<T>({ data, disabled, change }: { data?: WorkspacePage<T>; disabled: boolean; change: (page: number) => void }) {
  return data && <div className="assignment-pager" aria-label="Phân trang">
    <button type="button" className="secondary" disabled={disabled || data.page === 0} onClick={() => change(data.page - 1)}>Trang trước</button>
    <span>Trang {data.page + 1}/{Math.max(1, data.totalPages)} · {data.totalElements} kết quả</span>
    <button type="button" className="secondary" disabled={disabled || data.page + 1 >= data.totalPages} onClick={() => change(data.page + 1)}>Trang sau</button>
  </div>;
}
export const shortId = (id: string) => '…' + id.slice(-8);
export const dateLabel = (v: string) => new Date(v).toLocaleString('vi-VN');
export const localDateTime = (v: string) => {
  const d = new Date(v); return new Date(d.getTime() - d.getTimezoneOffset() * 60_000).toISOString().slice(0, 16);
};
