import type { Grant, Me } from '../../src/api';
import type { Cohort, ContextAssignment, InternshipGroup } from '../../src/modules/internship/types';
// Shared synthetic wire examples, also round-tripped through DEV06's Java DTOs
// by WorkspaceFrontendContractTest. Never imported by the production entry.
import wire from './internship-wire.json' with { type: 'json' };

export const ids = {
  user: '00000000-0000-4000-8000-000000000001', enterprise: '00000000-0000-4000-8000-000000000002',
  school: '00000000-0000-4000-8000-000000000003', term: '00000000-0000-4000-8000-000000000004',
  hr: '00000000-0000-4000-8000-000000000005', mentor: '00000000-0000-4000-8000-000000000006',
  cohort: '00000000-0000-4000-8000-000000000007', group: '00000000-0000-4000-8000-000000000008',
  case: '00000000-0000-4000-8000-000000000009', other: '00000000-0000-4000-8000-000000000010'
};
export const me: Me = { userId: ids.user, email: 'mentor@example.test', displayName: 'Người kiểm thử T3',
  memberships: [{ role: 'ENTERPRISE', organizationId: ids.enterprise }] };
export const grants: Grant[] = [{ role: 'ENTERPRISE', organizationId: ids.enterprise, permissions: ['t3:workspace:read'] }];
export const hr: ContextAssignment = {
  id: ids.hr, kind: 'HR', userId: ids.user, active: true,
  scope: { schoolOrgId: ids.school, enterpriseOrgId: ids.enterprise, termId: ids.term, groupId: null, caseId: null },
  validFrom: '2026-01-01T00:00:00Z', validUntil: '2027-01-01T00:00:00Z', version: 1,
  grantedBy: ids.other, decisionRef: 'SYNTHETIC-TEST-ONLY'
};
export const mentor: ContextAssignment = { ...hr, id: ids.mentor, kind: 'MENTOR', scope: { ...hr.scope, groupId: ids.group } };
export const cohort: Cohort = wire.cohort;
export const group: InternshipGroup = wire.group;
export const groupWithRepeatedMentors: InternshipGroup = wire.groupWithRepeatedMentors;
export const emptyGroup: InternshipGroup = wire.emptyGroup;
