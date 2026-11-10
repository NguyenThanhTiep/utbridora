import { test, expect } from '@playwright/test';
import { canDiscoverWorkspace, cohortInScope, groupInScope, usableAssignments } from '../src/modules/internship/access';
import { pendingWorkspaceGateway, workspaceReads, WorkspacePendingError } from '../src/modules/internship/api';
import type { ContextAssignment, WorkspaceAssignment } from '../src/modules/internship/types';
import { cohort, grants, group, hr, ids, me, mentor } from './fixtures/internship';

const now = Date.parse('2026-11-05T10:00:00Z');
const allowed = (assignment: ContextAssignment) => usableAssignments([assignment], me, grants, now);
const asWorkspace = (assignment: ContextAssignment) => assignment as WorkspaceAssignment;

test('T3 unit: enterprise entry point does not confer assignment authority', () => {
  expect(canDiscoverWorkspace(me)).toBe(true);
  expect(usableAssignments([], me, grants, now)).toEqual([]);
  expect(canDiscoverWorkspace({ ...me, memberships: [{ role: 'ADMIN', organizationId: null }] })).toBe(false);
  expect(canDiscoverWorkspace({ ...me, memberships: [{ role: 'STUDENT', organizationId: ids.school }] })).toBe(false);
});

test('T3 unit: valid HR and group Mentor accepted; coordinator is not HR', () => {
  expect(allowed(hr)).toEqual([hr]); expect(allowed(mentor)).toEqual([mentor]);
  expect(allowed({ ...hr, kind: 'COORDINATOR' })).toEqual([]);
});

for (const [name, change] of [
  ['foreign actor', { userId: ids.other }], ['revoked assignment', { active: false }],
  ['not yet active', { validFrom: '2026-12-01T00:00:00Z' }],
  ['expired at exact boundary', { validUntil: '2026-11-05T10:00:00Z' }],
  ['invalid timestamp', { validUntil: 'invalid' }],
  ['missing enterprise', { scope: { ...hr.scope, enterpriseOrgId: null } }],
  ['foreign enterprise', { scope: { ...hr.scope, enterpriseOrgId: ids.other } }],
  ['case-only scope', { scope: { ...hr.scope, caseId: ids.case } }],
  ['mentor without group', { kind: 'MENTOR', scope: hr.scope }]
] as [string, Partial<ContextAssignment>][]) {
  test('T3 unit: rejects ' + name, () => { expect(allowed({ ...hr, ...change })).toEqual([]); });
}

test('T3 unit: inclusive start, exclusive end', () => {
  expect(usableAssignments([hr], me, grants, Date.parse(hr.validFrom))).toHaveLength(1);
  expect(usableAssignments([hr], me, grants, Date.parse(hr.validUntil))).toHaveLength(0);
});

test('T3 unit: membership, exact permission and matching grant org are all necessary', () => {
  expect(usableAssignments([hr], { ...me, memberships: [] }, grants, now)).toEqual([]);
  for (const badGrant of [
    { ...grants[0], role: 'ADMIN' }, { ...grants[0], organizationId: ids.other },
    { ...grants[0], permissions: ['t3:*'] }, { ...grants[0], permissions: ['internship:enterprise:task_write'] }
  ]) expect(usableAssignments([hr], me, [badGrant], now)).toEqual([]);
});

test('T3 unit: cohort must match enterprise, school and term', () => {
  expect(cohortInScope(cohort, asWorkspace(hr))).toBe(true);
  for (const field of ['enterpriseOrgId', 'schoolOrgId', 'termId']) {
    expect(cohortInScope({ ...cohort, [field]: ids.other }, asWorkspace(hr))).toBe(false);
  }
});

test('T3 unit: group must match cohort, assigned group and mentor identity', () => {
  expect(groupInScope(group, cohort, asWorkspace(mentor))).toBe(true);
  expect(groupInScope({ ...group, groupId: ids.other }, cohort, asWorkspace(mentor))).toBe(false);
  expect(groupInScope({ ...group, cohortId: ids.other }, cohort, asWorkspace(hr))).toBe(false);
  expect(groupInScope({ ...group, mentorUserIds: [ids.other] }, cohort, asWorkspace(mentor))).toBe(false);
  expect(groupInScope({ ...group, groupId: ids.other }, cohort,
    asWorkspace({ ...hr, scope: { ...hr.scope, groupId: ids.group } }))).toBe(false);
});

test('T3 unit: production gateway fails closed for every unintegrated read', async () => {
  const signal = new AbortController().signal;
  await expect(pendingWorkspaceGateway.readAssignments(signal)).rejects.toBeInstanceOf(WorkspacePendingError);
  await expect(pendingWorkspaceGateway.readCohorts(asWorkspace(hr), signal)).rejects.toBeInstanceOf(WorkspacePendingError);
  await expect(pendingWorkspaceGateway.readGroups(asWorkspace(hr), cohort, signal)).rejects.toBeInstanceOf(WorkspacePendingError);
});

test('T3 unit: contract reads preserve business page and use core transport', async () => {
  const original = globalThis.fetch;
  const calls: { path: string; init?: RequestInit }[] = [];
  const result = { content: [], page: 2, size: 25, totalElements: 0, totalPages: 0 };
  globalThis.fetch = async (input, init) => {
    calls.push({ path: String(input), init });
    return new Response(JSON.stringify(result), { status: 200, headers: { 'Content-Type': 'application/json' } });
  };
  try {
    const signal = new AbortController().signal;
    expect(await workspaceReads.cohorts(ids.term, 2, signal)).toEqual(result);
    await workspaceReads.cohortGroups(ids.cohort, 0, signal);
    await workspaceReads.mentorGroups(1, signal);
    expect(calls.map(call => call.path)).toEqual([
      `/api/v1/t3/cohorts?termId=${ids.term}&page=2&size=25`,
      `/api/v1/t3/cohorts/${ids.cohort}/groups?page=0&size=25`,
      '/api/v1/t3/mentors/me/groups?page=1&size=25'
    ]);
    for (const call of calls) {
      expect(call.init?.credentials).toBe('include'); expect(call.init?.signal).toBe(signal);
      expect(call.init?.cache).toBe('no-store');
    }
  } finally { globalThis.fetch = original; }
});
