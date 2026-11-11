import { api, ApiError, type Grant, type Me } from '../../api';
import { usableAssignments } from './access';
import { liveWorkspaceGateway, WorkspaceScopeError } from './api';

export type CaseRef = {
  caseId: string; placementId: string; schoolOrgId: string; enterpriseOrgId: string;
  termId: string; state: string; version: number; contextAssignmentId?: string;
};

// A deep link carries no authority. HR/Mentor reads must select a current assignment,
// and T3 still checks its parent chain and the case's actual group on every request.
export async function readCase(caseId: string, me: Me, grants: Grant[], signal: AbortSignal): Promise<CaseRef> {
  const path = `/t3/cases/${caseId}`;
  try { return await api<CaseRef>(path, { signal }); }
  catch (error) {
    if (!(error instanceof ApiError) || ![403, 404].includes(error.status) || signal.aborted
      || !me.memberships.some(m => m.role === 'ENTERPRISE')) throw error;
    const assignments = usableAssignments(await liveWorkspaceGateway.readAssignments(signal), me, grants, Date.now());
    for (const assignment of assignments) {
      if (signal.aborted) throw error;
      try {
        const value = await api<CaseRef>(path, { signal, headers: { 'X-Context-Assignment-Id': assignment.id } });
        if (value.schoolOrgId !== assignment.scope.schoolOrgId || value.enterpriseOrgId !== assignment.scope.enterpriseOrgId
          || value.termId !== assignment.scope.termId) throw new WorkspaceScopeError();
        return { ...value, contextAssignmentId: assignment.id };
      } catch (denied) {
        if (!(denied instanceof ApiError) || ![403, 404].includes(denied.status)) throw denied;
      }
    }
    throw error;
  }
}
