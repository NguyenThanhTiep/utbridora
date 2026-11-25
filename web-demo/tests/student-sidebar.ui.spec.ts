import { expect, test, type Page } from '@playwright/test';

// Isolated UI sessions; student page implementations remain those supplied by T2.
async function studentSession(page: Page, allowed = true) {
  const organizationId = '00000000-0000-4000-8000-000000000011';
  const userId = '00000000-0000-4000-8000-000000000002';
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/auth/me') return route.fulfill({ json: { userId, displayName: 'Nguyễn Minh An', email: 'student@example.test',
      memberships: [{ role: 'STUDENT', organizationId }] } });
    if (path === '/auth/me/permissions') return route.fulfill({ json: allowed ? [{ role: 'STUDENT', organizationId, permissions: ['student_profile:self:read', 'student_profile:self:write'] }] : [] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'sidebar-ui-csrf' } });
    if (path === '/terms' || path === '/invitations/me' || path === '/preferences/me') return route.fulfill({ json: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');
}

for (const width of [1440, 390, 320]) test(`student keeps T2 pages and uses the shared sidebar at ${width}px`, async ({ page }) => {
  const crashes: string[] = [];
  page.on('pageerror', error => crashes.push(error.message));
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await studentSession(page);
  await expect(page.locator('.workspace-student .topbar-title')).toHaveText('Tổng quan');
  const sidebar = page.locator('.sidebar'), navigation = page.getByRole('navigation', { name: 'Điều hướng chính', includeHidden: true });
  expect(await sidebar.evaluate(node => getComputedStyle(node).backgroundImage)).toContain('linear-gradient');
  const titles = ['Tổng quan', 'Thông tin chung', 'Năng lực', 'Cơ hội thực tập', 'Lời mời & nguyện vọng', 'Nhiệm vụ & sản phẩm', 'Báo cáo & đánh giá'];
  for (const title of titles) {
    const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
    if (await toggle.isVisible()) await toggle.click();
    await navigation.getByRole('button', { name: title, exact: true }).click();
    await expect(page.locator('.topbar-title')).toHaveText(title);
    await expect(page.locator('.content .page-header')).toHaveCount(0);
    await expect(navigation.locator('[aria-current="page"]')).toHaveText(title);
    await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  }
  await page.getByRole('button', { name: 'Mở menu tài khoản', exact: true }).click();
  await expect(page.locator('.account-menu-user')).toContainText('student@example.test');
  await page.keyboard.press('Escape');
  await expect(page.locator('.account-menu')).toHaveCount(0);
  await expect(page.getByRole('button', { name: 'Mở menu tài khoản', exact: true })).toBeFocused();
  if (width < 900) {
    await page.getByRole('button', { name: 'Mở menu điều hướng', exact: true }).click();
    await expect.poll(() => sidebar.evaluate(node => node.scrollWidth <= node.clientWidth)).toBe(true);
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', { name: 'Mở menu điều hướng', exact: true })).toBeFocused();
  }
  expect(crashes).toEqual([]);
  await page.screenshot({ path: `../target/t2-student-sidebar-${width}.png`, fullPage: true, animations: 'disabled' });
});

test('student menu remains unavailable without a scoped student grant', async ({ page }) => {
  await studentSession(page, false);
  await expect(page.locator('.workspace')).toBeVisible();
  await expect(page.locator('.student-navigation')).toHaveCount(0);
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Tổng quan', exact: true })).toHaveCount(0);
});

test('student receives shared action and pointer feedback without changing its page palette', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'no-preference' });
  await studentSession(page);
  const hero = page.locator('.student-dash-hero');
  const palette = await hero.evaluate(node => getComputedStyle(node).backgroundImage);
  const action = page.locator('.student-dash-cta');
  await action.hover();
  expect(await action.evaluate(node => getComputedStyle(node, '::before').animationName)).toContain('action-light-sweep');
  const panel = page.locator('.student-page .panel').first();
  const frame = (await panel.boundingBox())!;
  await page.mouse.move(frame.x + 8, frame.y + 8);
  await expect(panel).toHaveAttribute('data-portal-glow', '');
  const account = page.getByRole('button', { name: 'Mở menu tài khoản', exact: true });
  await account.hover();
  expect(await account.evaluate(node => getComputedStyle(node, '::after').animationName)).toContain('action-light-sweep');
  expect(await hero.evaluate(node => getComputedStyle(node).backgroundImage)).toBe(palette);
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await expect(panel).not.toHaveAttribute('data-portal-glow');
  expect(await action.evaluate(node => getComputedStyle(node, '::before').animationName)).toBe('none');
});

for (const width of [1440, 390, 320]) test(`student sharing choices keep compact checkboxes and explicit consent at ${width}px`, async ({ page }) => {
  await page.setViewportSize({ width, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await studentSession(page);
  await expect(page.locator('.workspace-student .topbar-title')).toHaveText('Tổng quan');
  const fields = ['majorCode', 'skills', 'projects', 'certificates', 'email', 'phone', 'gpa'];
  const record = { invitationId: '00000000-0000-4000-8000-000000000101',
    proposalItemId: '00000000-0000-4000-8000-000000000201', placementCandidateId: '00000000-0000-4000-8000-000000000201',
    studentUserId: '00000000-0000-4000-8000-000000000002', schoolOrgId: '00000000-0000-4000-8000-000000000011',
    enterpriseOrgId: '00000000-0000-4000-8000-000000000012', termId: '00000000-0000-4000-8000-000000000021',
    opportunityId: '00000000-0000-4000-8000-000000000081', title: 'Thực tập phát triển phần mềm', enterpriseName: 'Doanh nghiệp thử nghiệm',
    state: 'ACCEPTED', expiresAt: '2099-01-01T00:00:00Z', decidedAt: null, decisionReasonCode: null, version: 2, placementId: null };
  let consentWrites = 0;
  page.on('request', request => { if (request.method() === 'POST' && request.url().includes('/students/me/consents')) consentWrites++; });
  const rows = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 });
  await page.route('**/api/v1/invitations/me?**', route => route.fulfill({ json: rows([record]) }));
  await page.route('**/api/v1/students/me/consents?**', route => route.fulfill({ json: rows([]) }));
  await page.route('**/api/v1/school/terms/*/profile-sharing-policy', route => route.fulfill({ json: {
    schoolOrgId: record.schoolOrgId, termId: record.termId, version: 3, allowedFields: fields, updatedAt: null, updatedByUserId: null,
  } }));
  const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await toggle.isVisible()) await toggle.click();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Lời mời & nguyện vọng', exact: true }).click();
  const form = page.locator('.student-consent-form'), choices = form.getByRole('checkbox');
  await expect(choices).toHaveCount(fields.length);
  const submit = form.getByRole('button', { name: 'Đồng ý chia sẻ các mục đã chọn', exact: true });
  await expect(submit).toBeDisabled();
  for (const box of await choices.all()) {
    await expect(box).not.toBeChecked();
    const bounds = (await box.boundingBox())!;
    expect(bounds.width).toBeGreaterThanOrEqual(16);
    expect(bounds.width).toBeLessThanOrEqual(20);
    expect(bounds.height).toBeGreaterThanOrEqual(16);
    expect(bounds.height).toBeLessThanOrEqual(20);
  }
  await form.getByText('Kỹ năng', { exact: true }).click();
  await expect(form.getByRole('checkbox', { name: 'Kỹ năng', exact: true })).toBeChecked();
  await expect(submit).toBeEnabled();
  await form.getByRole('checkbox', { name: 'Kỹ năng', exact: true }).focus();
  await page.keyboard.press('Space');
  await expect(submit).toBeDisabled();
  expect(consentWrites).toBe(0);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
  await form.screenshot({ path: `../target/student-sharing-choices-${width}.png`, animations: 'disabled' });
});
