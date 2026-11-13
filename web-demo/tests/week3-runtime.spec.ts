import { expect, test, type Browser, type Page } from '@playwright/test';

test('week 3 school, student and enterprise use real share, decision, Placement and T3 APIs', async ({ browser }) => {
  test.skip(process.env.E2E_WEEK3 !== 'true', 'Run from EnterpriseProposalIT with its isolated MySQL');
  test.setTimeout(210000);
  const coop = process.env.E2E_COOPERATION!, proposal = process.env.E2E_PROPOSAL!;
  async function actor(browser: Browser, email: string) {
    const context = await browser.newContext(), page = await context.newPage();page.setDefaultTimeout(10000);await page.goto('/');
    await page.getByLabel('Email', {exact: true}).fill(email);await page.getByLabel('Mật khẩu', {exact: true}).fill(process.env.E2E_PASSWORD!);
    await page.getByRole('button', {name: 'Đăng nhập', exact: true}).click();
    await expect(page.getByRole('heading', {name: 'Tài khoản của tôi', exact: true})).toBeVisible();return {context, page};
  }
  async function openCooperation(page: Page, school: boolean) {
    const side = school ? 'school' : 'enterprise';
    const org = school ? '00000000-0000-4000-8000-000000000011' : '00000000-0000-4000-8000-000000000012';
    const loaded = page.waitForResponse(r => r.url().includes(`/api/v1/${side}/cooperations?`) && r.request().method() === 'GET'
      && r.request().headers()['x-organization-id'] === org);
    await page.getByRole('button', {name: school ? 'Hợp tác doanh nghiệp' : 'Nhu cầu thực tập', exact: true}).click();
    await page.getByLabel(school ? 'Nhà trường' : 'Doanh nghiệp đang làm việc').selectOption(org);
    let response = await loaded, currentPage = 0;
    const panel = page.locator('section.panel').filter({has: page.getByRole('heading', {name: 'Đề nghị hợp tác', exact: true})});
    let position = -1;
    while (true) {
      expect(response.ok()).toBeTruthy();const result = await response.json();
      const index = result.content.findIndex((c: {cooperationId: string}) => c.cooperationId === coop);
      if (index >= 0) {position = currentPage * 25 + index; break;}
      expect(currentPage + 1).toBeLessThan(result.totalPages); currentPage++;
      const next = page.waitForResponse(r => r.url().includes(`/api/v1/${side}/cooperations?`) && new URL(r.url()).searchParams.get('page') === String(currentPage));
      await panel.getByRole('button', {name: 'Sau', exact: true}).click(); response = await next;
    }
    await panel.getByRole('button', {name: `Xem nội dung đề nghị ${position + 1}`, exact: true}).click();
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

    await student.page.getByRole('button', {name: 'Lời mời & nguyện vọng', exact: true}).click();
    const invitations = await (await student.page.request.get('/api/v1/invitations/me?page=0&size=100')).json();
    const index = invitations.content.findIndex((i: {invitationId: string}) => i.invitationId === invitation.invitationId);expect(index).toBeGreaterThanOrEqual(0);
    const panel = student.page.locator('.workflow-surface > section.panel').nth(index);
    await panel.getByRole('button', {name: 'Nhận lời mời', exact: true}).click();await expect(panel.getByRole('button', {name: 'Rút nhận lời', exact: true})).toBeVisible();
    await hr.page.getByRole('button', {name: 'Tải lại ứng viên', exact: true}).click();await expect(hr.page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', {exact: true})).toBeVisible();
    await panel.getByRole('checkbox', {name: 'Kỹ năng', exact: true}).check();
    await panel.getByRole('button', {name: 'Đồng ý chia sẻ các mục đã chọn', exact: true}).click();
    await expect(student.page.getByText('Đang có hiệu lực', {exact: true})).toBeVisible();
    await school.page.getByRole('button', {name: 'Tải lại tiến trình', exact: true}).click();
    await school.page.getByRole('button', {name: 'Cấp chia sẻ', exact: true}).click();
    await school.page.getByRole('button', {name: 'Xác nhận cấp chia sẻ', exact: true}).click();
    await expect(school.page.getByText('Đã cấp chia sẻ', {exact: true})).toBeVisible();
    await hr.page.getByRole('button', {name: 'Tải lại ứng viên', exact: true}).click();
    await expect(hr.page.getByText(/JAVA · Cấp độ/)).toBeVisible();
    await expect(hr.page.locator('.proposal-profile').getByText('student@example.test')).toHaveCount(0);
    await hr.page.getByRole('button', {name: 'Tiếp nhận thực tập', exact: true}).click();
    await expect(hr.page.locator('.proposal-card .cooperation-state').getByText('Doanh nghiệp đã đồng ý', {exact: true})).toBeVisible();
    await school.page.getByRole('button', {name: 'Tải lại tiến trình', exact: true}).click();
    await school.page.getByLabel('Ứng viên đã được doanh nghiệp đồng ý').selectOption(proposal);
    const effective = await school.page.getByLabel('Ngày bắt đầu', {exact: true}).inputValue();
    await school.page.getByLabel('Thời điểm có hiệu lực', {exact: true}).fill(`${effective}T00:00`);
    await school.page.getByRole('button', {name: 'Tạo hồ sơ chờ phê duyệt', exact: true}).click();
    await expect(school.page.getByText('Tiếp nhận 1 · Chờ trường phê duyệt', {exact: true})).toBeVisible();
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
    await student.page.getByRole('button', {name: 'Thu hồi quyền chia sẻ', exact: true}).click();
    await expect(student.page.getByText('Đang có hiệu lực', {exact: true})).toHaveCount(0);
    await hr.page.getByRole('button', {name: 'Tải lại ứng viên', exact: true}).click();
    await expect(hr.page.getByText('Chưa có hồ sơ được chia sẻ hợp lệ.', {exact: true})).toBeVisible();
    await openCooperation(school.page, true);
    await expect(school.page.getByText('Tiếp nhận 1 · Đã duyệt, chờ bắt đầu', {exact: true})).toBeVisible();
    await school.page.getByRole('button', {name: 'Hủy tiếp nhận', exact: true}).click();
    await school.page.getByLabel('Căn cứ thao tác tiếp nhận').fill('DEMO-WEEK3-BROWSER-CANCEL');
    await school.page.getByRole('combobox', {name: 'Lý do hủy', exact: true}).selectOption('SCHOOL_CANCELLED');
    await school.page.getByRole('button', {name: 'Xác nhận hủy tiếp nhận', exact: true}).click();
    await expect(school.page.getByText('Tiếp nhận 1 · Đã hủy', {exact: true})).toBeVisible();
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
