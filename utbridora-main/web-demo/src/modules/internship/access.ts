import type { Grant, Me } from '../../api';
import type { Cohort, ContextAssignment, InternshipGroup, WorkspaceAssignment } from './types';

export const workspacePermission = 't3:workspace:read';

// Discovering the entry point confers no HR/Mentor authority.
export function canDiscoverWorkspace(me: Me): boolean {
  return me.memberships.some(member => member.role === 'ENTERPRISE' && !!member.organizationId);
}

export function usableAssignments(
  assignments: ContextAssignment[], me: Me, grants: Grant[], now: number
): WorkspaceAssignment[] {
  return assignments.filter((item): item is WorkspaceAssignment => {
    const scope = item.scope;
    return (item.kind === 'HR' || item.kind === 'MENTOR') && item.userId === me.userId && item.active
      && !!scope.enterpriseOrgId && !!scope.schoolOrgId && !!scope.termId
      // Case-only authority must not open an entire group's overview.
      && scope.caseId === null && (item.kind !== 'MENTOR' || !!scope.groupId)
      && Date.parse(item.validFrom) <= now && now < Date.parse(item.validUntil)
      && me.memberships.some(member => member.role === 'ENTERPRISE' && member.organizationId === scope.enterpriseOrgId)
      && grants.some(grant => grant.role === 'ENTERPRISE' && grant.organizationId === scope.enterpriseOrgId
        && grant.permissions.includes(workspacePermission));
  });
}

export function cohortInScope(cohort: Cohort, assignment: WorkspaceAssignment): boolean {
  return cohort.enterpriseOrgId === assignment.scope.enterpriseOrgId
    && cohort.schoolOrgId === assignment.scope.schoolOrgId && cohort.termId === assignment.scope.termId;
}

export function groupInScope(group: InternshipGroup, cohort: Cohort, assignment: WorkspaceAssignment): boolean {
  return cohortInScope(cohort, assignment) && group.cohortId === cohort.cohortId
    && (assignment.scope.groupId === null || group.groupId === assignment.scope.groupId)
    && (assignment.kind !== 'MENTOR' || group.mentorUserIds.includes(assignment.userId));
}
