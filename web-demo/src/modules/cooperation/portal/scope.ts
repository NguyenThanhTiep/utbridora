import type { Demand } from '../types';
import type { CooperationRecord } from '../workflow/types';

export type PortalDataScope = { schoolOrgId?: string; termId?: string };

export function scopeQuery({ schoolOrgId, termId }: PortalDataScope) {
  return new URLSearchParams({ ...(schoolOrgId ? { schoolOrgId } : {}), ...(termId ? { termId } : {}) });
}

export function demandInScope(row: Demand, scope: PortalDataScope) {
  return (!scope.schoolOrgId || row.data.schoolOrgId === scope.schoolOrgId)
    && (!scope.termId || row.data.termId === scope.termId);
}

export function cooperationInScope(row: CooperationRecord, scope: PortalDataScope) {
  return (!scope.schoolOrgId || row.schoolOrgId === scope.schoolOrgId)
    && (!scope.termId || row.termId === scope.termId);
}
