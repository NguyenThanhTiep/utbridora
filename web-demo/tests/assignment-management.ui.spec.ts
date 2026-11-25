import { test, expect, type Page } from '@playwright/test';
import { openWorkspacePage } from './helpers/workspace';

// UI fixtures only. AssignmentManagementIT separately exercises the same controls with real MySQL/APIs.
const ids = { actor: '00000000-0000-4000-8000-000000000001', school: '00000000-0000-4000-8000-000000000002',
  enterprise: '00000000-0000-4000-8000-000000000003', term: '00000000-0000-4000-8000-000000000004',
  target: '00000000-0000-4000-8000-000000000005', grant: '00000000-0000-4000-8000-000000000006',
  group: '00000000-0000-4000-8000-000000000007', case: '00000000-0000-4000-8000-000000000008' };
const pageOf = (content: unknown[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: content.length ? 1 : 0 });
async function fixtures(page: Page, mode: 'SCHOOL' | 'HR') {
  await page.clock.install({ time: new Date('2026-11-25T03:00:00Z') });
  let assigned = false, revoked = false;
  const role = mode === 'SCHOOL' ? 'SCHOOL_STAFF' : 'ENTERPRISE', organizationId = mode === 'SCHOOL' ? ids.school : ids.enterprise;
  const grant = { id: ids.grant, kind: 'COORDINATOR', userId: ids.target, scope: { schoolOrgId: ids.school, enterpriseOrgId: ids.enterprise, termId: ids.term, groupId: null, caseId: null },
    active: true, validFrom: '2026-11-25T03:00:00Z', validUntil: '2026-11-30T03:00:00Z', version: 1, grantedBy: ids.actor, decisionRef: 'UI-DECISION' };
  const commands: { path: string; method: string; body: Record<string, unknown>; key?: string }[] = [];
  await page.route('**/api/v1/**', async route => {
    const request = route.request(), url = new URL(request.url()), path = url.pathname.replace('/api/v1', '');
    const respond = (json: unknown, status = 200) => route.fulfill({ json, status });
    if (path === '/auth/me') return respond({ userId: ids.actor, email: 'synthetic@example.test', displayName: 'Người thử giao diện', memberships: [{ role, organizationId }] });
    if (path === '/auth/me/permissions') return respond([{ role, organizationId, permissions: ['t3:coordinator:confirm', 't3:mentors:manage', 't3:hr:manage', 't3:workspace:read'] }]);
    if (path === '/auth/csrf') return respond({ headerName: 'X-CSRF-TOKEN', token: 'ui-fixture' });
    if (path === '/cooperation/organizations') return respond([{ id: ids.school, name: 'Trường kiểm thử', type: 'UNIVERSITY' }, { id: ids.enterprise, name: 'Doanh nghiệp kiểm thử', type: 'ENTERPRISE' }]);
    if (path === '/t3/assignment-management/scopes') return respond(pageOf([{ id: ids.grant, mode, schoolOrgId: ids.school, enterpriseOrgId: mode === 'SCHOOL' ? null : ids.enterprise,
      termId: ids.term, termCode: 'TERM-REFERENCE-00000000-0000-4000-8000-000000000004', validFrom: '2026-11-01T00:00:00Z', validUntil: '2026-11-30T03:00:00Z', contextId: mode === 'HR' ? ids.grant : null }]));
    if (path === '/t3/assignment-management/enterprises') return respond(pageOf([{ enterpriseOrgId: ids.enterprise, name: 'Doanh nghiệp kiểm thử', canConfirm: true }]));
    if (path === '/t3/assignment-management/candidates') return respond(pageOf([{ userId: ids.target, displayName: 'Coordinator nhận quyền' }]));
    if (path === '/t3/assignment-management/assignments') return respond(pageOf(assigned && (!revoked || url.searchParams.get('includeInactive') === 'true') ? [{ assignment: { ...grant, active: !revoked }, displayName: 'Coordinator nhận quyền', effective: !revoked, canRevoke: !revoked }] : []));
    if (path === '/t3/assignment-management/assignments/' + ids.grant) return respond({ assignment: { ...grant, active: !revoked }, displayName: 'Coordinator nhận quyền', effective: !revoked, canRevoke: !revoked });
    if (path === '/t3/assignment-management/cases') return respond(pageOf([{ caseId: ids.case, groupId: ids.group, groupCode: 'G-01', state: 'IN_PROGRESS', version: 2 }]));
    if (path === '/t3/assignment-management/cases/' + ids.case + '/mentors') return respond(pageOf([{ userId: ids.target, displayName: 'Mentor đủ điều kiện' }]));
    if (request.method() !== 'GET' && path.startsWith('/t3/')) {
      commands.push({ path, method: request.method(), body: request.postDataJSON(), key: request.headers()['idempotency-key'] });
      if (path === '/t3/coordinators/confirm') { assigned = true; return respond(grant, 201); }
      if (path === '/t3/context-assignments/' + ids.grant) { revoked = true; return respond({ id: ids.grant, active: false, version: 2 }); }
      if (path.endsWith('/mentor-handover')) return respond({ resourceId: ids.grant, version: 3, changedAt: '2026-11-25T03:00:00Z' });
    }
    return respond({ errorCode: 'RESOURCE_NOT_FOUND' }, 404);
  });
  await page.goto('/'); await openWorkspacePage(page, 'Phân công thực tập');
  return commands;
}
test('scope and candidate selects remain labelled after async options; confirmation and revocation work at 390px', async ({ page }) => {
  const commands = await fixtures(page, 'SCHOOL');
  await page.getByLabel('Doanh nghiệp', { exact: true }).selectOption(ids.enterprise);
  await page.getByLabel('Người nhận Coordinator', { exact: true }).selectOption(ids.target);
  await page.getByLabel('Căn cứ quyết định', { exact: true }).fill('UI-DECISION');
  await page.getByRole('button', { name: 'Xác nhận Coordinator', exact: true }).click();
  await expect(page.getByText('Đã xác nhận Coordinator. Người nhận cần đăng nhập lại để cập nhật quyền.', { exact: true })).toBeVisible();
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.getByRole('row').filter({ hasText: 'Coordinator nhận quyền' }).getByRole('button', { name: 'Chi tiết phân công' }).click();
  await page.getByLabel('Mã lý do thu hồi', { exact: true }).fill('TEAM_CHANGED');
  await expect(page.getByRole('button', { name: 'Thu hồi phân công', exact: true })).toBeDisabled();
  await page.getByRole('checkbox', { name: 'Tôi xác nhận thu hồi và hiểu ảnh hưởng tới quyền phụ thuộc.', exact: true }).check();
  await page.getByRole('button', { name: 'Thu hồi phân công', exact: true }).click();
  await expect.poll(() => commands.length).toBe(2);
  expect(commands[0]).toMatchObject({ path: '/t3/coordinators/confirm', body: { userId: ids.target, enterpriseOrgId: ids.enterprise, termId: ids.term } });
  expect(commands[1]).toMatchObject({ path: '/t3/context-assignments/' + ids.grant, method: 'DELETE', body: { expectedVersion: 1, reasonCode: 'TEAM_CHANGED' } });
  expect(commands.every(c => !!c.key)).toBe(true);
});
test('HR can select the case and eligible mentor using accessible labels at 390px', async ({ page }) => {
  const commands = await fixtures(page, 'HR');
  await page.getByLabel('Hồ sơ cần bàn giao', { exact: true }).selectOption(ids.case);
  await page.getByLabel('Mentor tiếp nhận', { exact: true }).selectOption(ids.target);
  await page.getByLabel('Mã lý do bàn giao', { exact: true }).fill('MENTOR_HANDOVER');
  await page.getByRole('button', { name: 'Xác nhận bàn giao Mentor', exact: true }).click();
  await expect.poll(() => commands.length).toBe(1);
  expect(commands[0]).toMatchObject({ path: '/t3/cases/' + ids.case + '/mentor-handover', body: { caseId: ids.case, newMentorUserId: ids.target, expectedCaseVersion: 2, reasonCode: 'MENTOR_HANDOVER' } });
  await page.setViewportSize({ width: 390, height: 844 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
