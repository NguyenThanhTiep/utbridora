import { expect, test, type Browser, type Page } from '@playwright/test';

test('week 3 school, student and enterprise use real share, decision, Placement and T3 APIs', async ({ browser }) => {
  test.skip(process.env.E2E_WEEK3 !== 'true', 'Run from EnterpriseProposalIT with its isolated MySQL');
  test.setTimeout(210000);
  const coop = process.env.E2E_COOPERATION!, proposal = process.env.E2E_PROPOSAL!;
  async function actor(browser: Browser, email: string) {
    const context = await browser.newContext(), page = await context.newPage();page.setDefaultTimeout(10000);await page.goto('/');
    await page.getByLabel('Email', {exact: true}).fill(email);await page.getByLabel('Mật khẩu', {exact: true}).fill(process.env.E2E_PASSWORD!);
    await page.getByRole('button', {name: 'Đăng nhập', exact: true}).click();
    await expect(page.getByRole('button', {name: 'Đăng xuất', exact: true})).toBeVisible();return {context, page};
  }
  async function openCooperation(page: Page, school: boolean, placements = false) {
    const org = school ? '00000000-0000-4000-8000-000000000011' : '00000000-0000-4000-8000-000000000012';
    const entry = page.getByRole('button', {name: school ? 'Hợp tác doanh nghiệp' : 'Nhu cầu thực tập', exact: true});
    if (await entry.count()) await entry.click();
    const navigation = page.getByRole('navigation', {name: 'Điều hướng chính'});
    await navigation.getByRole('button', {name: placements ? 'Tiếp nhận thực tập' : school ? 'Đề xuất sinh viên' : 'Ứng viên đề xuất', exact: true}).click();
    await page.getByLabel(school ? 'Nhà trường đang làm việc' : 'Doanh nghiệp đang làm việc', {exact: true}).selectOption(org);
    await page.getByLabel('Hợp tác đang xem', {exact: true}).selectOption(coop);
    await expect(page.getByRole('button', {name: placements ? 'Tải lại tiếp nhận thực tập' : school ? 'Tải lại tiến trình' : 'Tải lại ứng viên', exact: true})).toBeVisible();
  }
  const school = await actor(browser, 'school@example.test');
  const student = await actor(browser, 'student@example.test');
  const hr = await actor(browser, 'hr@example.test');
  try {
    await openCooperation(school.page, true);
    school.page.once('dialog', d => void d.accept());
    const issued = school.page.waitForResponse(r => r.url().endsWith(`/school/proposals/${proposal}/invite`) && r.request().method() === 'POST');
    await school.page.getByRole('button', {name: 'Gửi lời mời thực tập', exact: true}).click();
    const invitationResponse = await issued;expect(invitationResponse.status()).toBe(202);const invitation = await invitationResponse.json();
    await openCooperation(hr.page, false);await expect(hr.page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', {exact: true})).toBeVisible();
    await hr.page.getByRole('tab', {name: 'Lô đề xuất', exact: true}).click();
    await expect(hr.page.getByText('Chưa có dữ liệu lô đề xuất', {exact: true})).toBeVisible();
    await hr.page.getByRole('button', {name: 'Xem ứng viên được chia sẻ', exact: true}).click();

    await student.page.getByRole('button', {name: 'Lời mời & nguyện vọng', exact: true}).click();
    const invitations = await (await student.page.request.get('/api/v1/invitations/me?page=0&size=100')).json();
    const index = invitations.content.findIndex((i: {invitationId: string}) => i.invitationId === invitation.invitationId);expect(index).toBeGreaterThanOrEqual(0);
    const panel = student.page.locator('.workflow-surface > section.panel').nth(index);
    await panel.getByRole('button', {name: 'Nhận lời mời', exact: true}).click();await expect(panel.getByRole('button', {name: 'Rút nhận lời', exact: true})).toBeVisible();
    await hr.page.getByRole('button', {name: 'Tải lại ứng viên', exact: true}).click();await expect(hr.page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', {exact: true})).toBeVisible();
    await panel.getByRole('checkbox', {name: 'Kỹ năng', exact: true}).check();
    const granted = student.page.waitForResponse(r => r.url().endsWith('/students/me/consents') && r.request().method() === 'POST');
    await panel.getByRole('button', {name: 'Đồng ý chia sẻ các mục đã chọn', exact: true}).click();
    const grantResponse = await granted;expect(grantResponse.status(), await grantResponse.text()).toBe(201);
    const consent = await grantResponse.json();expect(consent.invitationId).toBe(invitation.invitationId);
    const expiryLabel = await student.page.evaluate(value => new Date(value).toLocaleString('vi-VN'), consent.expiresAt);
    const consentSection = student.page.locator('section.panel').filter({has: student.page.getByRole('heading', {name: 'Quyền chia sẻ đã cấp', exact: true})});
    // The seven-day expiry identifies this new grant among the earlier synthetic one-hour grants.
    // A global active label can belong to an old lineage while the new POST is still pending.
    const consentArticle = consentSection.locator('article').filter({hasText: 'hết hạn ' + expiryLabel});
    await expect(consentArticle).toHaveCount(1);
    await expect(consentArticle.getByText('Đang có hiệu lực', {exact: true})).toBeVisible();
    await school.page.getByRole('button', {name: 'Tải lại tiến trình', exact: true}).click();
    await school.page.getByRole('button', {name: 'Cấp chia sẻ', exact: true}).click();
    await school.page.getByRole('button', {name: 'Xác nhận cấp chia sẻ', exact: true}).click();
    await expect(school.page.getByText('Đã cấp chia sẻ', {exact: true})).toBeVisible();
    await hr.page.getByRole('button', {name: 'Tải lại ứng viên', exact: true}).click();
    await expect(hr.page.getByText(/JAVA · Cấp độ/)).toBeVisible();
    await expect(hr.page.locator('.proposal-profile').getByText('student@example.test')).toHaveCount(0);
    await hr.page.getByRole('tab', {name: 'Lô đề xuất', exact: true}).click();
    await expect(hr.page.getByRole('cell', {name: '1 hồ sơ được chia sẻ', exact: true})).toBeVisible();
    await hr.page.getByRole('button', {name: 'Xem ứng viên trong lô', exact: true}).click();
    await expect(hr.page.getByText(/JAVA · Cấp độ/)).toBeVisible();
    await hr.page.locator('.proposal-card').getByRole('button', {name: 'Tiếp nhận thực tập', exact: true}).click();
    await expect(hr.page.locator('.proposal-card .cooperation-state').getByText('Doanh nghiệp đã đồng ý', {exact: true})).toBeVisible();
    await school.page.getByRole('button', {name: 'Tải lại tiến trình', exact: true}).click();
    await openCooperation(school.page, true, true);
    await school.page.getByLabel('Ứng viên đã được doanh nghiệp đồng ý').selectOption(proposal);
    const effective = await school.page.getByLabel('Ngày bắt đầu', {exact: true}).inputValue();
    await school.page.getByLabel('Thời điểm có hiệu lực', {exact: true}).fill(`${effective}T00:00`);
    await school.page.getByRole('button', {name: 'Tạo hồ sơ chờ phê duyệt', exact: true}).click();
    await expect(school.page.getByRole('heading', {name: 'Tiếp nhận 1 · Chờ trường phê duyệt', exact: true})).toBeVisible();
    await school.page.getByRole('button', {name: 'Phê duyệt tiếp nhận', exact: true}).click();
    await school.page.getByLabel('Căn cứ thao tác tiếp nhận').fill('DEMO-WEEK3-BROWSER');
    await school.page.getByRole('button', {name: 'Xác nhận phê duyệt', exact: true}).click();
    await expect(school.page.getByRole('button', {name: 'Kích hoạt thực tập', exact: true})).toBeDisabled();
    await expect(async () => {
      await school.page.getByRole('button', {name: 'Kiểm tra bàn giao sang không gian thực tập', exact: true}).click();
      await expect(school.page.getByRole('link', {name: 'Mở hồ sơ thực tập', exact: true})).toBeVisible();
    }).toPass({timeout: 20000});
    await school.page.getByRole('link', {name: 'Mở hồ sơ thực tập', exact: true}).click();
    await expect(school.page.getByText('Trạng thái: Chờ bắt đầu', {exact: true})).toBeVisible();

    // Revocation blocks new profile reads without cancelling an already approved Placement.
    const revoked = student.page.waitForResponse(r => r.url().endsWith(`/students/me/consents/${consent.consentId}/revoke`) && r.request().method() === 'POST');
    await consentArticle.getByRole('button', {name: 'Thu hồi quyền chia sẻ', exact: true}).click();
    const revokeResponse = await revoked;expect(revokeResponse.status(), await revokeResponse.text()).toBe(200);
    expect(revokeResponse.request().postDataJSON().expectedLineageVersion).toBe(consent.lineageVersion);
    expect((await revokeResponse.json()).revokedAt).not.toBeNull();
    await expect(consentArticle.getByText('Đang có hiệu lực', {exact: true})).toHaveCount(0);
    await expect(consentArticle.getByText('Đã thu hồi', {exact: true})).toBeVisible();
    await hr.page.getByRole('button', {name: 'Tải lại ứng viên', exact: true}).click();
    await expect(hr.page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', {exact: true})).toBeVisible();
    await hr.page.getByRole('tab', {name: 'Lô đề xuất', exact: true}).click();
    await expect(hr.page.getByText('Chưa có dữ liệu lô đề xuất', {exact: true})).toBeVisible();
    await openCooperation(school.page, true, true);
    await expect(school.page.getByRole('heading', {name: 'Tiếp nhận 1 · Đã duyệt, chờ bắt đầu', exact: true})).toBeVisible();
    await school.page.getByRole('button', {name: 'Hủy tiếp nhận', exact: true}).click();
    await school.page.getByLabel('Căn cứ thao tác tiếp nhận').fill('DEMO-WEEK3-BROWSER-CANCEL');
    await school.page.getByRole('combobox', {name: 'Lý do hủy', exact: true}).selectOption('SCHOOL_CANCELLED');
    await school.page.getByRole('button', {name: 'Xác nhận hủy tiếp nhận', exact: true}).click();
    await expect(school.page.getByRole('heading', {name: 'Tiếp nhận 1 · Đã hủy', exact: true})).toBeVisible();
    // The existing ACTIVE fixture independently verifies HR navigation and assignment scope;
    // the new Placement above remains the future-effective APPROVED/cancel workflow.
    const handoff = await hr.page.request.get('/api/v1/enterprise/placements/00000000-0000-4000-8000-000000000099/internship-link', {
      headers: {'X-Organization-Id': '00000000-0000-4000-8000-000000000012'},
    });
    expect(handoff.ok()).toBeTruthy();const link = await handoff.json();expect(link.state).toBe('AVAILABLE');
    expect(link.route).toMatch(/^\/workspace\/cases\/[0-9a-f-]{36}$/i);await hr.page.goto(link.route);
    await expect(hr.page.getByText('Trạng thái: Đang thực tập', {exact: true})).toBeVisible();
    await expect(hr.page.getByRole('heading', {name: 'Nhóm DEMO-G1', exact: true})).toBeVisible();
  } finally {await Promise.all([school.context.close(), student.context.close(), hr.context.close()]);}
});
