import { expect, test, type Page } from '@playwright/test';
// Browser contract fixtures; backend privacy/authorization is covered separately in EnterpriseProposalIT.
const fixture = '/tests/fixtures/proposals.html';
const item = { proposalItemId: 'proposal-1', cooperationId: 'coop-1', state: 'SHARED', version: 4, shareId: 'share-1', reservationUntil: '2026-11-11T00:00:00Z' };
function pageOf(content: unknown[]) { return { content, page: 0, size: 10, totalElements: content.length, totalPages: content.length ? 1 : 0 }; }
async function setup(page: Page) {
  await page.clock.install({ time: new Date('2026-11-10T10:00:00Z') });
  await page.route('**/api/v1/auth/csrf', route => route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-token' } }));
  await page.route('**/api/v1/enterprise/cooperations/coop-1/proposals?*', route => route.fulfill({ json: pageOf([item]) }));
  await page.route('**/api/v1/enterprise/proposals/proposal-1/profile', route => route.fulfill({ json: {
    profileVersion: 2, validUntil: item.reservationUntil, allowedFields: ['majorCode', 'skills'], data: { majorCode: 'IT',
      skills: [{ kind: 'CANONICAL', skill: { skillCode: 'JAVA', level: 3, yearsPractice: 2 } }],
      projects: [{ projectId: 'hidden', title: 'PRIVATE-PROJECT', description: '', role: '', skillCodes: [] }],
      email: 'PRIVATE-EMAIL@example.test', studentUserId: 'PRIVATE-IDENTITY' },
  } }));
}
test('enterprise sees only whitelist fields and acceptance updates the card', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/enterprise/proposals/proposal-1/decision', async route => {
    expect(route.request().headers()['x-organization-id']).toBe('enterprise-1');
    expect(route.request().headers()['x-csrf-token']).toBe('fixture-token');
    expect(route.request().postDataJSON()).toEqual({ expectedVersion: 4, decision: 'ACCEPT' });
    await page.route('**/api/v1/enterprise/cooperations/coop-1/proposals?*', r => r.fulfill({ json: pageOf([{ ...item, state: 'ENTERPRISE_ACCEPTED', version: 5 }]) }));
    await route.fulfill({ json: { ...item, state: 'ENTERPRISE_ACCEPTED', version: 5 } });
  });
  await page.goto(fixture);
  await expect(page.getByText('JAVA', { exact: false })).toBeVisible();
  await expect(page.getByText(/PRIVATE-/)).toHaveCount(0);
  await page.getByRole('button', { name: 'Tiếp nhận thực tập' }).click();
  await expect(page.locator('.proposal-card .cooperation-state').getByText('Doanh nghiệp đã đồng ý', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Tiếp nhận thực tập' })).toHaveCount(0);
});
test('manual refresh fetches newly shared candidates and removes profiles after access is revoked', async ({ page }) => {
  await setup(page);
  let visible: typeof item[] = [], reads = 0;
  await page.route('**/api/v1/enterprise/cooperations/coop-1/proposals?*', route => {
    reads++;
    expect(route.request().method()).toBe('GET');
    expect(route.request().headers()['x-organization-id']).toBe('enterprise-1');
    const query = new URL(route.request().url()).searchParams;
    expect(query.get('page')).toBe('0'); expect(query.get('size')).toBe('10');
    return route.fulfill({ json: pageOf(visible) });
  });
  await page.goto(fixture);
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.')).toBeVisible();
  expect(reads).toBe(1);

  visible = [item];
  await page.getByRole('button', { name: 'Tải lại ứng viên', exact: true }).click();
  await expect(page.getByText('JAVA', { exact: false })).toBeVisible();
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.')).toHaveCount(0);
  expect(reads).toBe(2);

  visible = [];
  await page.getByRole('button', { name: 'Tải lại ứng viên', exact: true }).click();
  await expect(page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.')).toBeVisible();
  await expect(page.getByText('JAVA', { exact: false })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Tiếp nhận thực tập' })).toHaveCount(0);
  expect(reads).toBe(3);
});
test('decline requires a selected reason', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/enterprise/proposals/proposal-1/decision', async route => {
    expect(route.request().postDataJSON()).toEqual({ expectedVersion: 4, decision: 'DECLINE', reasonCode: 'SCHEDULE_CONFLICT' });
    await page.route('**/api/v1/enterprise/cooperations/coop-1/proposals?*', r => r.fulfill({ json: pageOf([{ ...item, state: 'DECLINED', version: 5 }]) }));
    await route.fulfill({ json: { ...item, state: 'DECLINED', version: 5 } });
  });
  await page.goto(fixture); await page.getByRole('button', { name: 'Từ chối', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Xác nhận từ chối' })).toBeDisabled();
  await page.getByLabel('Lý do từ chối').selectOption('SCHEDULE_CONFLICT');
  await page.getByRole('button', { name: 'Xác nhận từ chối' }).click();
  await expect(page.locator('.proposal-card .cooperation-state').getByText('Doanh nghiệp đã từ chối', { exact: true })).toBeVisible();
});

test('enterprise state filter requests the server and resets pagination', async ({ page }) => {
  await setup(page);const requests: {page: number; state: string | null}[] = [];
  const rows = Array.from({length: 12}, (_, n) => ({...item, proposalItemId: `proposal-${n + 1}`, state: n === 0 ? 'DECLINED' : 'ENTERPRISE_ACCEPTED'}));
  await page.route('**/api/v1/enterprise/proposals/*/profile', r => r.fulfill({json: {profileVersion: 2, validUntil: item.reservationUntil, allowedFields: ['skills'], data: {skills: []}}}));
  await page.route('**/api/v1/enterprise/cooperations/coop-1/proposals?*', r => {
    const q = new URL(r.request().url()).searchParams, pageIndex = Number(q.get('page')), state = q.get('state');requests.push({page: pageIndex, state});
    const visible = state ? rows.filter(row => row.state === state) : rows;
    return r.fulfill({json: {content: visible.slice(pageIndex * 10, pageIndex * 10 + 10), page: pageIndex, size: 10, totalElements: visible.length, totalPages: Math.ceil(visible.length / 10)}});
  });
  await page.goto(fixture);await page.getByRole('button', {name: 'Trang ứng viên sau', exact: true}).click();
  await expect(page.getByText('Trang 2/2', {exact: true})).toBeVisible();
  await page.getByRole('combobox', {name: 'Lọc trạng thái ứng viên', exact: true}).selectOption('DECLINED');
  await expect(page.locator('.proposal-card .cooperation-state').getByText('Doanh nghiệp đã từ chối', {exact: true})).toBeVisible();
  expect(requests.at(-1)).toEqual({page: 0, state: 'DECLINED'});
  await expect(page.getByRole('heading', {name: 'Ứng viên 1', exact: true})).toBeVisible();
  await expect(page.getByRole('button', {name: 'Trang ứng viên sau', exact: true})).toHaveCount(0);
});

for (const field of ['cv', 'evidence'] as const) test(`${field} download rechecks the profile and uses the authenticated binary File API with a neutral filename`, async ({ page }) => {
  await setup(page);const fileId = '00000000-0000-4000-8000-000000000081', pdf = '%PDF-1.4\nsynthetic test\n%%EOF';let profileReads = 0, downloads = 0;
  await page.route('**/api/v1/enterprise/proposals/proposal-1/profile', r => {
    const file = {fileId, contentType: 'application/pdf', sizeBytes: pdf.length, scanStatus: 'CLEAN', originalName: 'PRIVATE-STUDENT.pdf'};
    profileReads++;return r.fulfill({json: {profileVersion: 2, validUntil: item.reservationUntil, allowedFields: [field],
      data: {[field]: field === 'cv' ? file : [file]}}});
  });
  await page.route(`**/api/v1/files/${fileId}/content`, r => {
    downloads++;expect(r.request().method()).toBe('GET');expect(r.request().headers()['x-organization-id']).toBe('enterprise-1');
    return r.fulfill({contentType: 'application/pdf', body: pdf});
  });
  await page.goto(fixture);const downloaded = page.waitForEvent('download');
  await page.getByRole('button', {name: field === 'cv' ? 'Tải CV' : 'Tải minh chứng', exact: true}).click();
  expect((await downloaded).suggestedFilename()).toBe(`${fileId}.pdf`);expect(profileReads).toBeGreaterThanOrEqual(2);expect(downloads).toBe(1);
});

test('revoked profile prevents a CV content request and an unavailable File API never fabricates a download', async ({ page }) => {
  await setup(page);const fileId = '00000000-0000-4000-8000-000000000081';let revoked = false, contentRequests = 0;
  await page.route('**/api/v1/enterprise/proposals/proposal-1/profile', r => revoked
    ? r.fulfill({status: 403, json: {errorCode: 'ACCESS_DENIED'}})
    : r.fulfill({json: {profileVersion: 2, validUntil: item.reservationUntil, allowedFields: ['cv'], data: {cv: {fileId, contentType: 'application/pdf', sizeBytes: 100, scanStatus: 'CLEAN'}}}}));
  await page.route(`**/api/v1/files/${fileId}/content`, r => {contentRequests++;return r.fulfill({status: 503, json: {errorCode: 'DEPENDENCY_UNAVAILABLE'}});});
  await page.goto(fixture);await page.getByRole('button', {name: 'Tải CV', exact: true}).click();
  await expect(page.getByText('Chưa tải được tệp. Vui lòng thử lại sau.', {exact: true})).toBeVisible();expect(contentRequests).toBe(1);
  revoked = true;await page.getByRole('button', {name: 'Tải CV', exact: true}).click();
  await expect(page.getByRole('button', {name: 'Kiểm tra lại ứng viên', exact: true})).toBeVisible();
  await expect(page.getByRole('button', {name: 'Tải CV', exact: true})).toHaveCount(0);expect(contentRequests).toBe(1);
});
test('lost response retries the exact key and payload and blocks a second decision', async ({ page }) => {
  await setup(page); const attempts: { key: string; body: string | null }[] = [];
  await page.route('**/api/v1/enterprise/proposals/proposal-1/decision', async route => {
    attempts.push({ key: route.request().headers()['idempotency-key'], body: route.request().postData() });
    if (attempts.length === 1) return route.abort('failed');
    await route.fulfill({ json: { ...item, state: 'ENTERPRISE_ACCEPTED', version: 5 } });
  });
  await page.goto(fixture); await page.getByRole('button', { name: 'Tiếp nhận thực tập' }).click();
  await expect(page.getByRole('button', { name: 'Tiếp nhận thực tập' })).toBeDisabled();
  await expect(page.getByRole('button', { name: 'Tải lại ứng viên' })).toBeDisabled();
  expect(await page.evaluate(() => window.dispatchEvent(new Event('demand-before-leave', {cancelable: true})))).toBe(false);
  await page.getByRole('button', { name: 'Thử lại quyết định đã gửi' }).click();
  await expect.poll(() => attempts.length).toBe(2); expect(attempts[0]).toEqual(attempts[1]); expect(attempts[0].key).toMatch(/^[0-9a-f-]{36}$/);
});
test('version conflict clears the profile and forces a reload', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/enterprise/proposals/proposal-1/decision', route => route.fulfill({ status: 409, json: { errorCode: 'VERSION_CONFLICT' } }));
  await page.goto(fixture); await page.getByRole('button', { name: 'Tiếp nhận thực tập' }).click();
  await expect(page.getByRole('button', { name: 'Kiểm tra lại ứng viên' })).toBeVisible();
  await expect(page.getByText('JAVA', { exact: false })).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Tiếp nhận thực tập' })).toHaveCount(0);
});
test('expired reservation and revoked profile cannot be decided', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/enterprise/cooperations/coop-1/proposals?*', route => route.fulfill({ json: pageOf([{ ...item, reservationUntil: '2026-11-09T00:00:00Z' }]) }));
  await page.goto(fixture); await expect(page.getByRole('button', { name: 'Tiếp nhận thực tập' })).toBeDisabled();
  await expect(page.getByText('Đã hết thời gian giữ chỗ.')).toBeVisible();
  await page.route('**/api/v1/enterprise/proposals/proposal-1/profile', route => route.fulfill({ status: 403, json: { errorCode: 'ACCESS_DENIED' } }));
  await page.getByRole('button', { name: 'Tải lại ứng viên' }).click();
  await expect(page.getByText(/hồ sơ đã ngừng được chia sẻ/)).toBeVisible();
  await expect(page.getByText('JAVA', { exact: false })).toHaveCount(0);
});

test('shared profile expires on screen and focus rechecks revoked authority', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/enterprise/proposals/proposal-1/profile', route => route.fulfill({json: {
    profileVersion: 2, validUntil: '2026-11-10T10:00:03Z', allowedFields: ['phone', 'certificates'],
    data: {phone: '0901234567', certificates: [{certificateId: 'cert-1', title: 'Java Certificate', issuer: 'School', issuedOn: '2026-01-01', expiresOn: null}], email: 'PRIVATE-EMAIL'},
  }}));
  await page.goto(fixture);await expect(page.getByText('Java Certificate', {exact: true})).toBeVisible();
  await expect(page.getByText('0901234567', {exact: false})).toBeVisible();await expect(page.getByText('PRIVATE-EMAIL')).toHaveCount(0);
  await page.clock.fastForward(4000);await expect(page.getByText('Quyền xem hồ sơ đã hết hạn.', {exact: true})).toBeVisible();
  await expect(page.getByText('Java Certificate', {exact: true})).toHaveCount(0);
  await expect(page.getByRole('button', {name: 'Tiếp nhận thực tập'})).toBeDisabled();
  await page.route('**/api/v1/enterprise/proposals/proposal-1/profile', route => route.fulfill({status: 403, json: {errorCode: 'ACCESS_DENIED'}}));
  await page.evaluate(() => {window.dispatchEvent(new Event('blur'));window.dispatchEvent(new Event('focus'));});
  await expect(page.getByText(/hồ sơ đã ngừng được chia sẻ/)).toBeVisible();
  await expect(page.getByText('0901234567', {exact: false})).toHaveCount(0);
});

test('focus revalidation preserves an uncertain enterprise decision key', async ({ page }) => {
  await setup(page);const attempts: {key: string;body: string | null}[] = [];
  await page.route('**/api/v1/enterprise/proposals/proposal-1/decision', route => {
    attempts.push({key: route.request().headers()['idempotency-key'], body: route.request().postData()});
    return attempts.length === 1 ? route.abort('failed') : route.fulfill({json: {...item, state: 'ENTERPRISE_ACCEPTED', version: 5}});
  });
  await page.goto(fixture);await page.getByRole('button', {name: 'Tiếp nhận thực tập'}).click();
  await expect(page.getByRole('button', {name: 'Thử lại quyết định đã gửi'})).toBeVisible();
  await page.evaluate(() => {window.dispatchEvent(new Event('blur'));window.dispatchEvent(new Event('focus'));});
  await page.getByRole('button', {name: 'Thử lại quyết định đã gửi'}).click();
  await expect.poll(() => attempts.length).toBe(2);expect(attempts[0]).toEqual(attempts[1]);
});

test('enterprise aggregate consumes only the protected T4 bands and rejects raw counters', async ({ page }) => {
  await setup(page);let unsafe = false;
  await page.route('**/api/v1/matching/evaluate', route => {
    expect(route.request().postDataJSON()).toEqual({demandId: 'demand-1', expectedDemandVersion: 2, expectedCriteriaVersion: 1});
    return route.fulfill({json: {audience: 'ENTERPRISE', release: {releaseId: 'release-1', matchRunId: 'run-1', eligibleBand: '5-9'}, ...(unsafe ? {rawCount: 7, students: ['PRIVATE-STUDENT']} : {})}});
  });
  await page.goto(fixture + '?aggregate');await page.getByRole('button', {name: 'Xem thống kê đối chiếu', exact: true}).click();
  await expect(page.getByText('5–9 ứng viên', {exact: true})).toBeVisible();
  unsafe = true;await page.getByRole('button', {name: 'Xem thống kê đối chiếu', exact: true}).click();
  await expect(page.getByText('Chưa nhận được bản thống kê được bảo vệ hợp lệ.', {exact: true})).toBeVisible();
  await expect(page.getByText('5–9 ứng viên', {exact: true})).toHaveCount(0);await expect(page.getByText('PRIVATE-STUDENT')).toHaveCount(0);
});

test('case deep link opens the exact T3 resource and respects denied reads', async ({ page }) => {
  const caseId = '00000000-0000-4000-8000-000000000099';
  await page.route('**/api/v1/auth/me', route => route.fulfill({json: {userId: 'school-staff', email: 'school@example.test', displayName: 'Cán bộ trường', memberships: [{role: 'SCHOOL_STAFF', organizationId: 'school-1'}]}}));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({json: [{role: 'SCHOOL_STAFF', organizationId: 'school-1', permissions: []}]}));
  let denied = false;
  await page.route(`**/api/v1/t3/cases/${caseId}`, route => denied
    ? route.fulfill({status: 404, json: {errorCode: 'RESOURCE_NOT_FOUND'}})
    : route.fulfill({json: {caseId, placementId: 'placement-1', schoolOrgId: 'school-1', enterpriseOrgId: 'enterprise-1', termId: 'term-1', state: 'WAITING_START', version: 1}}));
  await page.goto(`/workspace/cases/${caseId}`);await expect(page.getByText('Trạng thái: Chờ bắt đầu', {exact: true})).toBeVisible();
  await expect(page).toHaveURL(new RegExp(`/workspace/cases/${caseId}$`));
  denied = true;await page.getByRole('button', {name: 'Tải lại hồ sơ thực tập'}).click();
  await expect(page.getByText('Không đọc được hồ sơ thực tập. Kiểm tra phân công và quyền truy cập hiện hành.', {exact: true})).toBeVisible();
  await expect(page.getByText('Trạng thái: Chờ bắt đầu', {exact: true})).toHaveCount(0);
});

test('HR case deep link selects a current assignment and preserves T3 denial after revocation', async ({ page }) => {
  const caseId = '00000000-0000-4000-8000-000000000099';
  const membership = {role: 'ENTERPRISE', organizationId: 'enterprise-1'};
  await page.route('**/api/v1/auth/me', r => r.fulfill({json: {userId: 'hr-1', email: 'hr@example.test', displayName: 'HR', memberships: [membership]}}));
  await page.route('**/api/v1/auth/me/permissions', r => r.fulfill({json: [{...membership, permissions: ['t3:workspace:read']}]}));
  const assignment = {kind: 'HR', userId: 'hr-1', active: true, version: 1, grantedBy: 'coordinator-1', decisionRef: 'TEST',
    validFrom: '2020-01-01T00:00:00Z', validUntil: '2099-01-01T00:00:00Z',
    scope: {schoolOrgId: 'school-1', enterpriseOrgId: 'enterprise-1', termId: 'term-1', groupId: null, caseId: null}};
  await page.route('**/api/v1/t3/context-assignments/me', r => r.fulfill({json: [
    {...assignment, id: 'expired', validUntil: '2020-01-02T00:00:00Z'},
    {...assignment, id: 'other-scope', scope: {...assignment.scope, termId: 'other-term'}},
    {...assignment, id: 'current-scope'},
  ]}));
  await page.route('**/api/v1/t3/cohorts?*', r => r.fulfill({json: pageOf([])}));
  let denied = false;const selected: string[] = [];
  await page.route(`**/api/v1/t3/cases/${caseId}`, r => {
    const context = r.request().headers()['x-context-assignment-id'] || '';selected.push(context);
    return context === 'current-scope' && !denied
      ? r.fulfill({json: {...assignment.scope, caseId, placementId: 'placement-1', state: 'IN_PROGRESS', version: 1}})
      : r.fulfill({status: 404, json: {errorCode: 'RESOURCE_NOT_FOUND'}});
  });
  await page.goto(`/workspace/cases/${caseId}`);
  await expect(page.getByText('Trạng thái: Đang thực tập', {exact: true})).toBeVisible();
  expect(selected).toContain('');expect(selected.filter(Boolean)).toEqual(['other-scope', 'current-scope']);
  denied = true;await page.getByRole('button', {name: 'Tải lại hồ sơ thực tập'}).click();
  await expect(page.getByText('Không đọc được hồ sơ thực tập. Kiểm tra phân công và quyền truy cập hiện hành.', {exact: true})).toBeVisible();
  await expect(page.getByText('Trạng thái: Đang thực tập', {exact: true})).toHaveCount(0);
});
test('school progress and capacity distinguish acceptance from placement', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', route => route.fulfill({ json: pageOf([
    { ...item, studentUserId: 'school-student-1', invitationId: 'invite-1', placementId: null, state: 'ENTERPRISE_ACCEPTED', shareIsActive: true, shareNeedsUpdate: false, activeConsentId: 'consent-1', activeConsentVersion: 1, activeConsentExpiresAt: item.reservationUntil, shareableFields: ['skills'] },
  ]) }));
  await page.route('**/api/v1/school/cooperations/coop-1/capacity', route => route.fulfill({ json: { quantity: 3, activeReservations: 1, placedCount: 0, openForNomination: 2 } }));
  await page.route('**/api/v1/school/cooperations/coop-1/publication', route => route.fulfill({ json: { cooperationId: 'coop-1', publishedAt: null, closedAt: null, version: 3 } }));
  await page.goto(fixture + '?school');
  await expect(page.locator('.proposal-capacity dd')).toHaveText(['3', '1', '0', '2']);
  const row = page.getByRole('row').filter({ hasText: 'school-student-1' });
  await expect(row).toContainText('Sinh viên đã đồng ý'); await expect(row).toContainText('Đã cấp chia sẻ'); await expect(row).toContainText('Doanh nghiệp đã đồng ý');
  await expect(page.getByLabel('Thời điểm công bố')).toBeVisible();
});

test('school separates consent evidence from sharing and filters proposals without changing capacity', async ({ page }) => {
  await setup(page);const until = '2026-11-10T10:01:00Z';
  const accepted = {...item, studentUserId: 'student-accepted', state: 'ACCEPTED', invitationId: 'invite-1', shareId: null,
    activeConsentId: 'consent-1', activeConsentVersion: 1, activeConsentExpiresAt: until, shareableFields: ['skills'], shareIsActive: false, shareNeedsUpdate: false};
  const reserved = {...accepted, proposalItemId: 'proposal-2', studentUserId: 'student-reserved', state: 'RESERVED', invitationId: null,
    activeConsentId: null, activeConsentVersion: null, activeConsentExpiresAt: null, shareableFields: []};
  await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', r => {
    const state = new URL(r.request().url()).searchParams.get('state'), rows = [accepted, reserved];
    return r.fulfill({json: pageOf(state ? rows.filter(row => row.state === state) : rows)});
  });
  await page.route('**/api/v1/school/cooperations/coop-1/capacity', r => r.fulfill({json: {quantity: 3, activeReservations: 2, placedCount: 0, openForNomination: 1}}));
  await page.route('**/api/v1/school/cooperations/coop-1/publication', r => r.fulfill({json: {cooperationId: 'coop-1', version: 3, publishedAt: null, closedAt: null}}));
  await page.goto(fixture + '?school');
  await expect(page.getByRole('columnheader', {name: 'Sinh viên đồng ý chia sẻ', exact: true})).toBeVisible();
  const row = page.getByRole('row').filter({hasText: 'student-accepted'});
  await expect(row.locator('td').nth(1)).toContainText('Đồng ý chia sẻ còn hiệu lực.');
  await expect(row.locator('td').nth(1)).toContainText('Thông tin có thể chia sẻ: Kỹ năng');
  await expect(row.locator('td').nth(2)).toContainText('Chưa chia sẻ');
  await page.clock.fastForward(61000);await expect(row.locator('td').nth(1)).toContainText('Chưa có đồng ý chia sẻ còn hiệu lực.');
  await page.getByRole('combobox', {name: 'Lọc trạng thái đề xuất', exact: true}).selectOption('RESERVED');
  await expect(page.getByRole('row').filter({hasText: 'student-accepted'})).toHaveCount(0);
  await expect(page.getByRole('row').filter({hasText: 'student-reserved'})).toBeVisible();
  await expect(page.locator('.proposal-capacity dd')).toHaveText(['3', '2', '0', '1']);
});

const schoolItem = { ...item, studentUserId: 'school-student-1', invitationId: 'invite-1', placementId: null,
  activeConsentId: 'consent-new', activeConsentVersion: 3, activeConsentExpiresAt: '2026-11-10T12:00:00Z',
  shareableFields: ['skills'], shareIsActive: false, shareNeedsUpdate: true };
async function schoolSetup(page: Page, proposal: object) {
  await setup(page);
  await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', route => route.fulfill({ json: pageOf([proposal]) }));
  await page.route('**/api/v1/school/cooperations/coop-1/capacity', route => route.fulfill({ json: { quantity: 3, activeReservations: 1, placedCount: 0, openForNomination: 2 } }));
  await page.route('**/api/v1/school/cooperations/coop-1/publication', route => route.fulfill({ json: { cooperationId: 'coop-1', publishedAt: null, closedAt: null, version: 3 } }));
}
test('consent remains separate from reservation and expires correctly for a 30-day grant', async ({ page }) => {
  await schoolSetup(page, {...schoolItem, activeConsentExpiresAt: '2026-12-10T10:00:00Z'});
  await page.goto(fixture + '?school');const cell = page.getByRole('row').filter({hasText: 'school-student-1'}).locator('td').nth(1);
  // Each fake-clock jump must stay below the browser's signed 32-bit timer limit.
  await page.clock.fastForward(24 * 86400000);await page.clock.fastForward(2 * 86400000);
  await expect(cell).toContainText('Đồng ý chia sẻ còn hiệu lực.');
  await page.clock.fastForward(5 * 86400000);await expect(cell).toContainText('Chưa có đồng ý chia sẻ còn hiệu lực.');
});
for (const first of [true, false]) test(first ? 'school explicitly grants the first share with actual consent version' : 'school re-shares only new consent fields within its expiry', async ({ page }) => {
  const proposal = { ...schoolItem, state: first ? 'ACCEPTED' : 'SHARED', shareId: first ? null : 'old-share', shareNeedsUpdate: !first };
  await schoolSetup(page, proposal);
  let submitted = false;
  await page.route('**/api/v1/school/proposals/proposal-1/shares', async route => {
    const body = route.request().postDataJSON();submitted = true;
    expect(route.request().headers()['x-organization-id']).toBe('school-1');
    expect(body).toMatchObject({ proposalItemId: 'proposal-1', consentId: 'consent-new', expectedConsentVersion: 3, expectedProposalVersion: 4, fieldWhitelist: ['skills'] });
    expect(Date.parse(body.expiresAt)).toBeGreaterThan(Date.parse('2026-11-10T10:00:00Z'));
    expect(Date.parse(body.expiresAt)).toBeLessThanOrEqual(Date.parse(schoolItem.activeConsentExpiresAt));
    await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', r => r.fulfill({ json: pageOf([{ ...proposal, shareId: 'new-share', version: 5, state: 'SHARED', shareIsActive: true, shareNeedsUpdate: false }]) }));
    await route.fulfill({ status: 201, json: { shareId: 'new-share' } });
  });
  await page.goto(fixture + '?school');
  await page.getByRole('button', { name: first ? 'Cấp chia sẻ' : 'Cập nhật chia sẻ', exact: true }).click();
  await expect(page.getByRole('checkbox', { name: 'Kỹ năng', exact: true })).toBeChecked();
  await expect(page.getByRole('checkbox', { name: 'Ngành', exact: true })).toHaveCount(0);
  expect(submitted).toBe(false);
  await page.getByRole('button', { name: 'Xác nhận cấp chia sẻ' }).click();
  await expect(page.getByText('Đã cấp chia sẻ', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cập nhật chia sẻ', exact: true })).toHaveCount(0);
});
test('school waits for active consent and hides unnecessary updates for an active share', async ({ page }) => {
  await schoolSetup(page, { ...schoolItem, activeConsentId: null, activeConsentVersion: null, activeConsentExpiresAt: null, shareableFields: [], shareNeedsUpdate: false });
  await page.goto(fixture + '?school');
  await expect(page.getByText('Chờ sinh viên cấp quyền chia sẻ còn hiệu lực.')).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cập nhật chia sẻ', exact: true })).toHaveCount(0);
  await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', route => route.fulfill({ json: pageOf([{ ...schoolItem, state: 'ENTERPRISE_ACCEPTED', shareIsActive: true, shareNeedsUpdate: false }]) }));
  await page.getByRole('button', { name: 'Tải lại tiến trình' }).click();
  await expect(page.getByRole('cell', { name: 'Doanh nghiệp đã đồng ý', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cập nhật chia sẻ', exact: true })).toHaveCount(0);
});
test('school updates sharing after enterprise acceptance without changing that decision', async ({ page }) => {
  const proposal = { ...schoolItem, state: 'ENTERPRISE_ACCEPTED', version: 5 };
  await schoolSetup(page, proposal);
  let decisions = 0;
  await page.route('**/api/v1/enterprise/proposals/*/decision', route => { decisions++; return route.fulfill({ status: 500, json: {} }); });
  await page.route('**/api/v1/school/proposals/proposal-1/shares', async route => {
    expect(route.request().postDataJSON()).toMatchObject({ expectedProposalVersion: 5, consentId: 'consent-new', expectedConsentVersion: 3, fieldWhitelist: ['skills'] });
    await page.route('**/api/v1/school/cooperations/coop-1/proposals?*', r => r.fulfill({ json: pageOf([{ ...proposal, shareId: 'new-share', version: 6, shareIsActive: true, shareNeedsUpdate: false }]) }));
    await route.fulfill({ status: 201, json: { shareId: 'new-share' } });
  });
  await page.goto(fixture + '?school');
  await expect(page.getByText('Doanh nghiệp đã đồng ý tiếp nhận. Cập nhật chia sẻ vẫn giữ nguyên quyết định này.')).toBeVisible();
  await page.getByRole('button', { name: 'Cập nhật chia sẻ', exact: true }).click();
  await page.getByRole('button', { name: 'Xác nhận cấp chia sẻ' }).click();
  await expect(page.getByText('Đã cấp chia sẻ', { exact: true })).toBeVisible();
  await expect(page.getByRole('cell', { name: 'Doanh nghiệp đã đồng ý', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Cập nhật chia sẻ', exact: true })).toHaveCount(0);
  expect(decisions).toBe(0);
});
test('student withdraw confirmation cancels the opportunity, while consent revoke uses a separate action', async ({ page }) => {
  await setup(page);
  await page.route('**/api/v1/invitations/me?*', route => route.fulfill({ json: pageOf([{ invitationId: 'invite-1', title: 'Lời mời thử nghiệm', enterpriseName: 'Doanh nghiệp mẫu', enterpriseOrgId: 'enterprise-1', schoolOrgId: 'school-1', termId: 'term-1', state: 'ACCEPTED', version: 2, expiresAt: item.reservationUntil }]) }));
  await page.route('**/api/v1/students/me/consents?*', route => route.fulfill({ json: pageOf([{ consentId: 'consent-1', invitationId: 'invite-1', recipientOrgId: 'enterprise-1', schoolOrgId: 'school-1', termId: 'term-1', fieldWhitelist: ['skills'], lineageVersion: 3, expiresAt: item.reservationUntil, revokedAt: null, replacedByConsentId: null }]) }));
  await page.route('**/api/v1/school/terms/term-1/profile-sharing-policy', route => route.fulfill({ json: { version: 0, allowedFields: ['skills'] } }));
  let withdrawals = 0, revocations = 0;
  await page.route('**/api/v1/invitations/invite-1/withdraw', route => { withdrawals++; return route.fulfill({ json: {} }); });
  await page.route('**/api/v1/students/me/consents/consent-1/revoke', route => {
    revocations++; expect(route.request().postDataJSON()).toEqual({ expectedLineageVersion: 3, reasonCode: 'STUDENT_REVOKED' }); return route.fulfill({ json: {} });
  });
  await page.goto(fixture + '?student');
  await expect(page.getByText(/Để chỉ thu hồi quyền xem thông tin cá nhân/)).toBeVisible();
  page.once('dialog', dialog => { expect(dialog.message()).toContain('không thể hoàn tác'); expect(dialog.message()).toContain('HỦY BỎ'); void dialog.dismiss(); });
  await page.getByRole('button', { name: 'Rút nhận lời', exact: true }).click();expect(withdrawals).toBe(0);
  await page.getByRole('button', { name: 'Thu hồi quyền chia sẻ', exact: true }).click();
  await expect.poll(() => revocations).toBe(1);expect(withdrawals).toBe(0);
  page.once('dialog', dialog => void dialog.accept());
  await page.getByRole('button', { name: 'Rút nhận lời', exact: true }).click();
  await expect.poll(() => withdrawals).toBe(1);
});
