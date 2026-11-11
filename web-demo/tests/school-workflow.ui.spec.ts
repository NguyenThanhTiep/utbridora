import { expect, test, type Page } from '@playwright/test';
// UI API contract tests. Placement API availability/transactions belong to DEV10's runtime verification.
const url = '/tests/fixtures/proposals.html?school&workflow';
const pageOf = (content: object[]) => ({ content, page: 0, size: 25, totalElements: content.length, totalPages: content.length ? 1 : 0 });
const reserved = { proposalItemId: 'proposal-a', batchId: 'batch-1', studentUserId: 'student-a', cooperationId: 'coop-1', state: 'RESERVED', version: 1, reservationUntil: '2026-11-11T10:00:00Z',
  invitationId: null as string | null, shareId: null as string | null, placementId: null as string | null, activeConsentId: null, activeConsentVersion: null, activeConsentExpiresAt: null,
  shareableFields: [], shareIsActive: false, shareNeedsUpdate: false, shareVersion: null as number | null, eligibilityVersion: 7 };
function candidate(id: string, code: string, eligible: boolean, status = 'MATCH', reason: string | null = null) {
  return { studentCode: code, majorCode: 'SE', eligibilityVersion: 7, selectable: eligible, reasonCode: reason,
    match: { studentUserId: id, overallStatus: status, fitScore: 90, details: [{ criterionCode: 'MAJOR', type: 'REQUIRED', status: status === 'MATCH' ? 'PASS' : status === 'MISSING_DATA' ? 'UNKNOWN' : 'FAIL' }] } };
}
async function setup(page: Page) {
  await page.clock.install({ time: new Date('2026-11-10T10:00:00Z') });
  await page.route('**/api/v1/auth/csrf', r => r.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'test-token' } }));
  await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', r => r.fulfill({ json: pageOf([]) }));
  await page.route('**/api/v1/school/cooperations/coop-1/capacity', r => r.fulfill({ json: { quantity: 3, placedCount: 0, activeReservations: 0, openForNomination: 3, declinedCount: 0 } }));
  await page.route('**/api/v1/school/cooperations/coop-1/publication', r => r.fulfill({ json: { cooperationId: 'coop-1', version: 3, publishedAt: null, closedAt: null } }));
  await page.route('**/api/v1/school/placements**', r => r.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } }));
}
test('school selects eligible candidates, nominates once, adds to the same batch, and sends invitations', async ({ page }) => {
  await setup(page);let nominated = false, invited = false, runNumber = 0;const requests: Record<string, unknown>[] = [];
  await page.route('**/api/v1/matching/evaluate', r => {
    expect(r.request().postDataJSON()).toEqual({ demandId: 'demand-1', expectedDemandVersion: 2, expectedCriteriaVersion: 1 });runNumber++;
    return r.fulfill({ json: { audience: 'SCHOOL', summary: { run: { matchRunId: `run-${runNumber}`, demandId: 'demand-1', state: 'COMPLETED' }, matched: 2, missingData: 1, notMatched: 1 } } });
  });
  await page.route('**/api/v1/school/cooperations/coop-1/nomination-candidates?*', r => {
    const filter = new URL(r.request().url()).searchParams.get('overallStatus');
    const rows = filter === 'MATCH' ? [candidate('student-a', 'SV001', !nominated, 'MATCH', nominated ? 'PROPOSAL_ALREADY_EXISTS' : null), candidate('student-b', 'SV002', true)]
      : [candidate('student-c', 'SV003', false, filter || 'MISSING_DATA', 'CANDIDATE_NOT_MATCH')];
    return r.fulfill({ json: { cooperationId: 'coop-1', cooperationVersion: 3, demandVersion: 2, criteriaVersion: 1, batchVersion: nominated ? 1 : null,
      capacity: { openForNomination: nominated ? 2 : 3 }, run: { matchRunId: `run-${runNumber}` }, candidates: pageOf(rows) } });
  });
  await page.route('**/api/v1/matching/runs/*/nominate', async r => {
    const body = r.request().postDataJSON();requests.push(body);expect(r.request().headers()['x-csrf-token']).toBe('test-token');expect(r.request().headers()['x-organization-id']).toBe('school-1');
    expect(body).toMatchObject({ expectedDemandVersion: 2, expectedCriteriaVersion: 1, expectedCooperationVersion: 3, expectedBatchVersion: nominated ? 1 : null });
    nominated = true;
    await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', q => q.fulfill({ json: pageOf([{ ...reserved, state: invited ? 'INVITED' : 'RESERVED', invitationId: invited ? 'invite-a' : null, version: invited ? 2 : 1 }]) }));
    await r.fulfill({ status: 201, json: { batchId: 'batch-1', batchVersion: requests.length, items: [{ proposalItemId: 'proposal-a' }] } });
  });
  await page.route('**/api/v1/school/proposals/proposal-a/invite', r => {
    expect(r.request().postDataJSON()).toEqual({ expectedVersion: 1, expiresAt: null });invited = true;
    return r.fulfill({ status: 202, json: { invitationId: 'invite-a', state: 'PENDING' } });
  });
  await page.goto(url);await page.getByRole('button', { name: 'Tìm sinh viên phù hợp', exact: true }).click();
  await page.getByLabel('Kết quả đối chiếu').selectOption('MISSING_DATA');await expect(page.getByRole('checkbox', { name: 'Chọn SV003' })).toBeDisabled();
  await page.getByLabel('Kết quả đối chiếu').selectOption('NOT_MATCH');await expect(page.getByRole('checkbox', { name: 'Chọn SV003' })).toBeDisabled();
  await page.getByLabel('Kết quả đối chiếu').selectOption('MATCH');await page.getByRole('checkbox', { name: 'Chọn SV001' }).check();
  page.once('dialog', d => void d.dismiss());await page.getByRole('button', { name: 'Tạo đề xuất cho sinh viên đã chọn' }).click();expect(requests).toHaveLength(0);
  page.once('dialog', d => void d.accept());await page.getByRole('button', { name: 'Tạo đề xuất cho sinh viên đã chọn' }).click();
  await expect(page.getByRole('button', { name: 'Gửi lời mời thực tập', exact: true })).toBeVisible();expect(requests[0].studentUserIds).toEqual(['student-a']);
  page.once('dialog', d => void d.accept());await page.getByRole('button', { name: 'Gửi lời mời thực tập', exact: true }).click();
  await expect(page.getByText('Đang chờ sinh viên', { exact: true })).toBeVisible();await expect(page.getByRole('button', { name: 'Nhận lời mời', exact: true })).toHaveCount(0);
  await page.getByRole('button', { name: 'Tìm sinh viên phù hợp', exact: true }).click();await expect(page.getByRole('checkbox', { name: 'Chọn SV001' })).toBeDisabled();
  await page.getByRole('checkbox', { name: 'Chọn SV002' }).check();page.once('dialog', d => void d.accept());await page.getByRole('button', { name: 'Tạo đề xuất cho sinh viên đã chọn' }).click();
  await expect.poll(() => requests.length).toBe(2);expect(requests[1].expectedBatchVersion).toBe(1);
});
test('T2 eligibility confirmation is explicit and candidate selection respects remaining slots', async ({ page }) => {
  await setup(page);let approved = false;
  await page.route('**/api/v1/matching/evaluate', r => r.fulfill({ json: { summary: { run: { matchRunId: 'run-1' } } } }));
  await page.route('**/api/v1/school/cooperations/coop-1/nomination-candidates?*', r => r.fulfill({ json: {
    cooperationId: 'coop-1', cooperationVersion: 3, demandVersion: 2, criteriaVersion: 1, batchVersion: null, capacity: { openForNomination: 1 }, run: { matchRunId: 'run-1' },
    candidates: pageOf([candidate('student-a', 'SV001', true), candidate('student-b', 'SV002', approved, 'MATCH', approved ? null : 'SCHOOL_APPROVAL_REQUIRED')]),
  } }));
  await page.route('**/api/v1/school/student-eligibilities/decisions', r => {
    expect(r.request().postDataJSON()).toEqual({ studentUserId: 'student-b', termId: 'term-1', expectedVersion: 7, schoolApproved: true, reasonCode: 'SCHOOL_ELIGIBLE', decisionRef: 'QD-ELIGIBLE-01' });approved = true;
    return r.fulfill({ json: { studentUserId: 'student-b', schoolApproved: true } });
  });
  await page.goto(url);await page.getByRole('button', { name: 'Tìm sinh viên phù hợp', exact: true }).click();await expect(page.getByRole('checkbox', { name: 'Chọn SV002' })).toBeDisabled();
  await page.getByRole('button', { name: 'Xác nhận điều kiện cho SV002' }).click();await page.getByLabel('Căn cứ xác nhận điều kiện').fill('QD-ELIGIBLE-01');
  await page.getByRole('button', { name: 'Xác nhận sinh viên đủ điều kiện', exact: true }).click();await expect(page.getByRole('checkbox', { name: 'Chọn SV002' })).toBeEnabled();
  await page.getByRole('checkbox', { name: 'Chọn SV001' }).check();await expect(page.getByRole('checkbox', { name: 'Chọn SV002' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Chọn tất cả đủ điều kiện trên trang' })).toBeDisabled();
  await expect(page.getByText('Chưa truy cập được chức năng tiếp nhận thực tập.', { exact: false })).toBeVisible();
});
test('school revokes a share using its current version without revoking student consent', async ({ page }) => {
  await setup(page);let revoked = false;
  await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', r => r.fulfill({ json: pageOf([{ ...reserved, state: 'SHARED', invitationId: 'invite-a', shareId: 'share-a', shareIsActive: !revoked,
    activeConsentId: 'consent-a', activeConsentVersion: 1, activeConsentExpiresAt: reserved.reservationUntil, shareableFields: ['skills'], shareNeedsUpdate: revoked, shareVersion: revoked ? 7 : 6 }]) }));
  await page.route('**/api/v1/school/profile-shares/share-a/revoke', r => {
    expect(r.request().postDataJSON()).toEqual({ expectedVersion: 6, reasonCode: 'SCHOOL_REVIEW_REQUIRED' });revoked = true;
    return r.fulfill({ json: { shareId: 'share-a', revokedAt: '2026-11-10T10:00:00Z' } });
  });
  let consentRequests = 0;await page.route('**/api/v1/students/me/consents/**', r => { consentRequests++;return r.fulfill({status: 500}); });
  await page.goto(url);await page.getByRole('button', {name: 'Thu hồi chia sẻ hồ sơ', exact: true}).click();await page.getByLabel('Lý do thu hồi').selectOption('SCHOOL_REVIEW_REQUIRED');
  await page.getByRole('button', {name: 'Xác nhận thu hồi chia sẻ', exact: true}).click();await expect(page.getByText('Chia sẻ cũ không còn hiệu lực')).toBeVisible();
  await expect(page.getByRole('button', {name: 'Cập nhật chia sẻ', exact: true})).toBeVisible();expect(consentRequests).toBe(0);
});

test('Placement UI separates create, approve, activate, handoff and cancel; enterprise remains read-only', async ({ page }) => {
  await setup(page);
  const accepted = { ...reserved, state: 'ENTERPRISE_ACCEPTED', version: 5, shareId: 'share-a', shareIsActive: true, eligibilityVersion: 7 };
  await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', r => r.fulfill({ json: pageOf([accepted]) }));
  type Placement = { placementId: string; schoolOrgId: string; enterpriseOrgId: string; cooperationId: string; proposalItemId: string; state: string; version: number; effectiveAt: string; startDate: string; endDate: string };
  let placements: Placement[] = [], handedOff = false;const commands: string[] = [];
  await page.route('**/api/v1/school/placements**', r => {
    if (r.request().method() === 'POST') {
      const body = r.request().postDataJSON();expect(body).toMatchObject({proposalItemId: 'proposal-a', expectedProposalVersion: 5, expectedEligibilityVersion: 7, startDate: '2026-11-24', endDate: '2027-02-24'});commands.push('create');
      placements = [{placementId: 'placement-a', schoolOrgId: 'school-1', enterpriseOrgId: 'enterprise-1', cooperationId: 'coop-1', proposalItemId: 'proposal-a', state: 'PENDING_APPROVAL', version: 1, effectiveAt: '2026-11-11T10:00:00Z', startDate: body.startDate, endDate: body.endDate}];
      return r.fulfill({status: 201, json: placements[0]});
    }
    if (r.request().url().includes('/internship-link')) return r.fulfill({json: {state: handedOff ? 'AVAILABLE' : 'WAITING_HANDOFF', route: handedOff ? '/workspace/cases/00000000-0000-4000-8000-000000000099' : null}});
    if (r.request().url().includes('/summary')) return r.fulfill({json: {demandId: 'demand-1', cooperationId: 'coop-1', target: 3, placed: 0, pending: 1, remaining: 3}});
    return r.fulfill({json: pageOf(placements)});
  });
  await page.route('**/api/v1/placements/placement-a/*', r => {
    const action = r.request().url().split('/').pop()!;const body = r.request().postDataJSON();expect(body.expectedVersion).toBe(placements[0].version);expect(body.decisionRef).toBeTruthy();commands.push(action);
    placements = [{...placements[0], state: action === 'approve' ? 'APPROVED' : action === 'activate' ? 'ACTIVE' : 'CANCELLED', version: placements[0].version + 1}];
    return r.fulfill({json: placements[0]});
  });
  await page.goto(url);await page.getByLabel('Ứng viên đã được doanh nghiệp đồng ý').selectOption('proposal-a');await page.getByLabel('Thời điểm có hiệu lực').fill('2026-11-11T10:00');
  await page.getByRole('button', {name: 'Tạo hồ sơ chờ phê duyệt', exact: true}).click();await expect(page.getByText('Tiếp nhận 1 · Chờ trường phê duyệt')).toBeVisible();expect(commands).toEqual(['create']);
  await page.getByRole('button', {name: 'Phê duyệt tiếp nhận', exact: true}).click();await page.getByLabel('Căn cứ thao tác tiếp nhận').fill('QD-APPROVE');await page.getByRole('button', {name: 'Xác nhận phê duyệt', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Kích hoạt thực tập', exact: true})).toBeDisabled();expect(commands).toEqual(['create', 'approve']);
  await page.clock.fastForward(24 * 3600 * 1000 + 1000);await page.getByRole('button', {name: 'Kích hoạt thực tập', exact: true}).click();await page.getByLabel('Căn cứ thao tác tiếp nhận').fill('QD-START');
  await page.getByRole('button', {name: 'Xác nhận kích hoạt', exact: true}).click();await expect(page.getByText('Tiếp nhận 1 · Đang thực tập')).toBeVisible();
  await page.getByRole('button', {name: 'Kiểm tra bàn giao sang không gian thực tập'}).click();await expect(page.getByText('Đang chờ bàn giao sang không gian thực tập.')).toBeVisible();
  handedOff = true;await page.getByRole('button', {name: 'Kiểm tra bàn giao sang không gian thực tập'}).click();await expect(page.getByRole('link', {name: 'Mở hồ sơ thực tập'})).toHaveAttribute('href', '/workspace/cases/00000000-0000-4000-8000-000000000099');
  await page.getByRole('button', {name: 'Hủy tiếp nhận', exact: true}).click();await page.getByLabel('Căn cứ thao tác tiếp nhận').fill('QD-CANCEL');await page.getByRole('combobox', {name: 'Lý do hủy', exact: true}).selectOption('SCHOOL_CANCELLED');
  await page.getByRole('button', {name: 'Xác nhận hủy tiếp nhận', exact: true}).click();await expect(page.getByText('Tiếp nhận 1 · Đã hủy')).toBeVisible();expect(commands).toEqual(['create', 'approve', 'activate', 'cancel']);
  await page.route('**/api/v1/enterprise/placements**', r => r.fulfill({json: r.request().url().includes('/summary') ? {demandId: 'demand-1', cooperationId: 'coop-1', target: 3, placed: 0, pending: 0, remaining: 3} : pageOf(placements)}));
  await page.goto('/tests/fixtures/proposals.html?enterprise-placement');await expect(page.getByText('Tiếp nhận 1 · Đã hủy')).toBeVisible();
  await expect(page.getByRole('button', {name: 'Tạo hồ sơ chờ phê duyệt', exact: true})).toHaveCount(0);await expect(page.getByRole('button', {name: 'Hủy tiếp nhận', exact: true})).toHaveCount(0);
});
