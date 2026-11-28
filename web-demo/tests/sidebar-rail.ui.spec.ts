import { expect, test, type Page } from '@playwright/test';

const enterprise = '11111111-1111-4111-8111-111111111111';
const school = '22222222-2222-4222-8222-222222222222';
const term = '44444444-4444-4444-8444-444444444444';
const coopId = '55555555-5555-4555-8555-555555555555';
const pageOf = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 });

async function mockSession(page: Page, role: 'ADMIN' | 'ENTERPRISE' | 'SCHOOL_STAFF' | 'STUDENT', withUnread = false) {
  const organizationId = role === 'ADMIN' ? null : role === 'ENTERPRISE' ? enterprise : school;
  const permissions = role === 'ADMIN'
    ? ['account:admin:status', 'membership:admin:write', 'audit:global:read', 'taxonomy:skills:read']
    : role === 'STUDENT'
      ? ['student_profile:self:read', 'student_profile:self:write']
      : ['cooperation:enterprise:create', 'cooperation:school:review', 't3:workspace:read'];

  await page.route('**/api/v1/**', route => {
    const url = new URL(route.request().url());
    const path = url.pathname.replace('/api/v1', '');
    if (path === '/auth/me') {
      return route.fulfill({
        json: {
          userId: `${role.toLowerCase()}-user`,
          displayName: `${role} Tester`,
          email: `${role.toLowerCase()}@example.test`,
          memberships: [{ role, organizationId }],
        },
      });
    }
    if (path === '/auth/me/permissions') return route.fulfill({ json: [{ role, organizationId, permissions }] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'rail-ui-csrf' } });
    if (path === '/cooperation/organizations') {
      return route.fulfill({
        json: role === 'SCHOOL_STAFF'
          ? [{ id: school, name: 'Đại học Giao thông', type: 'UNIVERSITY' }]
          : [{ id: enterprise, name: 'Công ty Công nghệ', type: 'ENTERPRISE' }],
      });
    }
    if (path === '/enterprise/demand-references/schools') return route.fulfill({ json: pageOf([{ schoolOrgId: school, name: 'Đại học Giao thông' }]) });
    if (path === '/enterprise/demand-references/terms' || path === '/terms') return route.fulfill({ json: pageOf([{ termId: term, termCode: 'HK1-2026' }]) });

    if (withUnread) {
      const ver = {
        verificationId: 'ver-1',
        enterpriseOrgId: enterprise,
        schoolOrgId: school,
        state: 'NEEDS_CHANGES',
        version: 2,
        data: {
          schoolOrgId: school,
          previousVerificationId: null,
          registrationCode: '0312345678',
          registeredName: 'Công ty Công nghệ',
          address: 'TP.HCM',
          contactName: 'An',
          contactEmail: 'an@example.test',
          contactPhone: '0901234567',
          evidenceFileIds: [],
        },
        missingFields: ['evidenceFileIds'],
        decisionRef: 'REF-1',
        submittedAt: '2026-11-09T01:00:00Z',
        decidedAt: '2026-11-10T02:00:00Z',
      };
      const demand = {
        demandId: 'demand-1',
        enterpriseOrgId: enterprise,
        cooperationId: coopId,
        state: 'READY',
        version: 1,
        criteriaVersion: 1,
        validationState: 'VALID',
        softDeletedAt: null,
        data: {
          schoolOrgId: school,
          termId: term,
          title: 'Thực tập Frontend',
          quantity: 2,
          description: 'Mô tả',
          location: null,
          benefits: null,
          deadline: '2027-01-01T00:00:00Z',
          required: { majorCodes: [], skills: [], dateRange: { startDate: '2027-02-01', endDate: '2027-05-01' } },
          preferred: [],
          taxonomyVersion: 'v1',
          academicCatalogVersion: 'v1',
        },
      };
      const coop = {
        cooperationId: coopId,
        demandId: 'demand-1',
        schoolOrgId: school,
        enterpriseOrgId: enterprise,
        termId: term,
        state: 'APPROVED',
        submissionNo: 1,
        version: 2,
        submittedAt: '2026-11-09T03:00:00Z',
      };
      if (path.endsWith('/verifications')) return route.fulfill({ json: pageOf([ver]) });
      if (path === '/enterprise/demands') return route.fulfill({ json: pageOf([demand]) });
      if (path.endsWith('/cooperations')) return route.fulfill({ json: pageOf([coop]) });
      if (path.endsWith('/proposals')) {
        return route.fulfill({
          json: pageOf([{
            proposalItemId: 'prop-1',
            cooperationId: coopId,
            state: 'SHARED',
            version: 1,
            shareId: 'share-1',
            reservationUntil: '2030-01-01T00:00:00Z',
            placementId: null,
            shareIsActive: true,
            eligibilityVersion: 1,
            studentUserId: 'stu-1',
          }]),
        });
      }
    }

    if (route.request().method() === 'GET') return route.fulfill({ json: pageOf([]) });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');
  await expect(page.locator('.workspace')).toBeVisible();
}

test('admin sidebar toggles desktop icon-rail mode and glides active pill smoothly', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mockSession(page, 'ADMIN');

  const workspace = page.locator('.workspace');
  const sidebar = page.locator('#main-navigation');
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  const track = page.locator('.u417-nav-track');
  const toggle = page.getByRole('button', { name: 'Thu gọn thanh điều hướng', exact: true });

  await expect(toggle).toBeVisible();
  await expect(toggle).toHaveAttribute('aria-expanded', 'true');
  expect((await sidebar.boundingBox())!.width).toBeCloseTo(276, 0);

  await expect(track).toHaveAttribute('data-u417-ready', 'true');
  await nav.getByRole('button', { name: 'Tổng quan quản trị', exact: true }).click();
  await expect.poll(async () => track.evaluate(node => parseFloat(getComputedStyle(node).getPropertyValue('--u417-py')))).toBeGreaterThan(0);
  const overviewPy = await track.evaluate(node => parseFloat(getComputedStyle(node).getPropertyValue('--u417-py')));

  await nav.getByRole('button', { name: 'Nhật ký hệ thống', exact: true }).click();
  await expect.poll(async () => track.evaluate(node => parseFloat(getComputedStyle(node).getPropertyValue('--u417-py')))).toBeGreaterThan(overviewPy + 100);

  await toggle.click();
  await expect(workspace).toHaveClass(/is-rail/);
  const expandBtn = page.getByRole('button', { name: 'Mở rộng thanh điều hướng', exact: true });
  await expect(expandBtn).toHaveAttribute('aria-expanded', 'false');
  await expect.poll(async () => Math.round((await sidebar.boundingBox())!.width)).toBe(76);
  await expect(nav.getByRole('button', { name: 'Tổ chức & lời mời', exact: true }).locator('span')).toHaveCSS('opacity', '0');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  await nav.getByRole('button', { name: 'Danh mục kỹ năng', exact: true }).click();
  await expect(page.locator('.admin-location strong')).toHaveText('Danh mục kỹ năng');

  await expandBtn.click();
  await expect(workspace).not.toHaveClass(/is-rail/);
  await expect.poll(async () => Math.round((await sidebar.boundingBox())!.width)).toBe(276);
});

test('enterprise sidebar keeps notification pill badge without altering accessible name in expanded and rail modes', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mockSession(page, 'ENTERPRISE', true);

  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  const notifBtn = nav.getByRole('button', { name: 'Thông báo', exact: true });
  await expect(notifBtn).toBeVisible();
  await expect(notifBtn).toHaveText('Thông báo');

  const badge = notifBtn.locator('.u417-bd');
  await expect(badge).toHaveAttribute('data-badge', '3');

  await page.getByRole('button', { name: 'Thu gọn thanh điều hướng', exact: true }).click();
  await expect(page.locator('.workspace')).toHaveClass(/is-rail/);
  await expect.poll(async () => Math.round((await page.locator('#main-navigation').boundingBox())!.width)).toBe(76);
  await expect(badge).toBeVisible();
  await expect(notifBtn).toHaveCSS('position', 'relative');
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);
});

test('school sidebar supports accordion pill fallback and clicking a group in rail mode expands cleanly', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mockSession(page, 'SCHOOL_STAFF');

  const workspace = page.locator('.workspace');
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  const track = page.locator('.u417-nav-track');

  await nav.getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await nav.getByRole('button', { name: 'Duyệt hợp tác', exact: true }).click();
  await expect.poll(async () => track.evaluate(node => parseFloat(getComputedStyle(node).getPropertyValue('--u417-py')))).toBeGreaterThan(150);
  const subPy = await track.evaluate(node => parseFloat(getComputedStyle(node).getPropertyValue('--u417-py')));

  // Closing the parent group falls back indicator pill cleanly onto the parent toggle.
  await nav.getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await expect.poll(async () => track.evaluate(node => parseFloat(getComputedStyle(node).getPropertyValue('--u417-py')))).toBeLessThan(subPy - 20);

  await page.getByRole('button', { name: 'Thu gọn thanh điều hướng', exact: true }).click();
  await expect(workspace).toHaveClass(/is-rail/);

  // Clicking a School accordion icon while collapsed expands the rail and reveals sub-items.
  await nav.getByRole('button', { name: 'Đợt thực tập', exact: true }).click();
  await expect(workspace).not.toHaveClass(/is-rail/);
  await expect(nav.getByRole('button', { name: 'Đối chiếu hồ sơ', exact: true })).toBeVisible();
});

test('student sidebar supports ArrowDown, ArrowUp, Home and End keyboard navigation', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mockSession(page, 'STUDENT');

  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  const firstBtn = nav.getByRole('button', { name: 'Tổng quan', exact: true });
  const secondBtn = nav.getByRole('button', { name: 'Thông tin chung', exact: true });
  const lastBtn = nav.locator('button.nav').last();

  await firstBtn.focus();
  await page.keyboard.press('ArrowDown');
  await expect(secondBtn).toBeFocused();
  await page.keyboard.press('End');
  await expect(lastBtn).toBeFocused();
  await page.keyboard.press('ArrowDown');
  await expect(firstBtn).toBeFocused();
  await page.keyboard.press('ArrowUp');
  await expect(lastBtn).toBeFocused();
  await page.keyboard.press('Home');
  await expect(firstBtn).toBeFocused();
});

test('mobile keeps rail toggle hidden and reduced motion disables transitions without breaking pill placement', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });
  await mockSession(page, 'ADMIN');

  const ind = page.locator('.u417-ind');
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await nav.getByRole('button', { name: 'Tổ chức & lời mời', exact: true }).click();
  await expect(ind).toHaveCSS('transition-duration', '0s');
  const transform = await ind.evaluate(node => getComputedStyle(node).transform);
  expect(transform).not.toBe('none');

  await page.getByRole('button', { name: 'Thu gọn thanh điều hướng', exact: true }).click();
  await expect(page.locator('.workspace')).toHaveClass(/is-rail/);

  await page.setViewportSize({ width: 390, height: 844 });
  await expect(page.locator('.u417-rail-toggle')).toBeHidden();
  await page.getByRole('button', { name: 'Mở menu điều hướng', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Đóng menu điều hướng', exact: true })).toBeVisible();
  await expect(nav.getByRole('button', { name: 'Tài khoản & phân quyền', exact: true }).locator('span')).toHaveCSS('opacity', '1');
});

test('clicking a function keeps sidebar ready, clicking into the page auto-collapses to rail with floating tooltip and glow', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await mockSession(page, 'SCHOOL_STAFF');

  const workspace = page.locator('.workspace');
  const sidebar = page.locator('#main-navigation');
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });

  // Clicking sidebar function shows page cleanly with sidebar open.
  await nav.getByRole('button', { name: 'Doanh nghiệp đối tác', exact: true }).click();
  await nav.getByRole('button', { name: 'Duyệt hợp tác', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Duyệt hợp tác', level: 2, exact: true })).toBeVisible();
  await expect(workspace).not.toHaveClass(/is-rail/);

  // Pointer glow activates inside dark-teal sidebar.
  const sbBox = (await sidebar.boundingBox())!;
  await page.mouse.move(sbBox.x + 40, sbBox.y + 120);
  await expect(sidebar).toHaveAttribute('data-u417-glow', 'true');

  // Clicking into the main page area automatically collapses sidebar into 76px icon rail.
  await page.locator('.workspace-main').locator('h2', { hasText: 'Duyệt hợp tác' }).click();
  await expect(workspace).toHaveClass(/is-rail/);
  await expect.poll(async () => Math.round((await sidebar.boundingBox())!.width)).toBe(76);

  // Hovering an icon in collapsed rail shows the floating glass tooltip outside overflow-y nav.
  const overviewBtn = nav.getByRole('button', { name: 'Tổng quan', exact: true });
  await overviewBtn.hover();
  const tip = page.locator('.u417-rail-tip');
  await expect(tip).toBeVisible();
  await expect(tip).toHaveText('Tổng quan');
  expect((await tip.boundingBox())!.x).toBeGreaterThanOrEqual(76);

  // Clicking a School accordion group in rail mode expands again so user can pick another function, then clicking page collapses again.
  await nav.getByRole('button', { name: 'Đợt thực tập', exact: true }).click();
  await expect(workspace).not.toHaveClass(/is-rail/);
  await nav.getByRole('button', { name: 'Đối chiếu hồ sơ', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đối chiếu hồ sơ', level: 2, exact: true })).toBeVisible();
  await expect(workspace).not.toHaveClass(/is-rail/);
  await page.locator('.workspace-main').locator('h2', { hasText: 'Đối chiếu hồ sơ' }).click();
  await expect(workspace).toHaveClass(/is-rail/);
});
