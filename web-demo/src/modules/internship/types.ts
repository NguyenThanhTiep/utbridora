// Wire DTOs: Contract Pack v1.0, C.0, CP-AUTHZ1 and CP-WS1.
export type WorkspaceScope = {
  schoolOrgId: string; enterpriseOrgId: string | null; termId: string;
  groupId: string | null; caseId: string | null;
};
export type ContextAssignment = {
  id: string; kind: 'COORDINATOR' | 'HR' | 'MENTOR'; userId: string;
  scope: WorkspaceScope; active: boolean; validFrom: string; validUntil: string;
  version: number; grantedBy: string; decisionRef: string;
};
export type WorkspaceAssignment = ContextAssignment & { kind: 'HR' | 'MENTOR' };
export type Cohort = {
  cohortId: string; enterpriseOrgId: string; schoolOrgId: string; termId: string;
  code: string; name: string; version: number;
};
export type InternshipGroup = {
  groupId: string; cohortId: string; code: string; caseIds: string[];
  // CP-WS1 / DEV06 GroupDto permits repeated mentor IDs; count people distinctly.
  mentorUserIds: string[]; version: number;
};
// Business pages use `page`, unlike the legacy core Spring Page (`number`).
export type WorkspacePage<T> = {
  content: T[]; page: number; size: number; totalElements: number; totalPages: number;
};
// Local integration boundary, NOT a new REST contract. The future owner adapter
// must recheck parent confirmation, membership and assignment on every read.
export interface WorkspaceGateway {
  readAssignments(signal: AbortSignal): Promise<ContextAssignment[]>;
  readCohorts(assignment: WorkspaceAssignment, signal: AbortSignal): Promise<Cohort[]>;
  readGroups(assignment: WorkspaceAssignment, cohort: Cohort, signal: AbortSignal): Promise<InternshipGroup[]>;
}
