import { expect, test, type Page } from '@playwright/test';

const roles = ['student', 'school', 'enterprise'] as const;
const displayName = 'Nguyễn Minh An — Tài khoản kiểm tra thanh đầu trang';

// Isolated presentation fixtures; no business data or authentication is written.
async function session(page: Page, portal: typeof roles[number]) {
  const organizationId = '00000000-0000-4000-8000-000000000011';
  const role = { student: 'STUDENT', school: 'SCHOOL_STAFF', enterprise: 'ENTERPRISE' }[portal];
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/auth/me') return route.fulfill({ json: { userId: '00000000-0000-4000-8000-000000000002', displayName,
      email: 'topbar@example.test', memberships: [{ role, organizationId }] } });
    if (path === '/auth/me/permissions') return route.fulfill({ json: [{ role, organizationId,
      permissions: ['student_profile:self:read', 'student_profile:self:write', 'cooperation:enterprise:create', 'cooperation:school:review'] }] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'topbar-ui-token' } });
    if (path === '/cooperation/organizations') return route.fulfill({ json: [{ id: organizationId, name: 'Tổ chức kiểm tra', type: portal === 'enterprise' ? 'ENTERPRISE' : 'UNIVERSITY' }] });
    if (path === '/terms' || /\/(demands|cooperations|verifications|me)$/.test(path))
      return route.fulfill({ json: { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 } });
    return route.fulfill({ status: 404, json: { errorCode: 'RESOURCE_NOT_FOUND' } });
  });
  await page.goto('/');
  await expect(page.locator('.workspace-topbar .topbar-title')).toHaveText('Tổng quan');
}

async function appearance(page: Page) {
  return page.locator('.workspace-topbar').evaluate(bar => {
    const pick = (node: Element, names: string[]) => {
      const style = getComputedStyle(node);
      return Object.fromEntries(names.map(name => [name, style.getPropertyValue(name)]));
    };
    const child = (selector: string) => bar.querySelector(selector)!;
    return {
      frame: pick(bar, ['height', 'padding', 'margin', 'border', 'border-radius', 'background-color', 'box-shadow', 'gap']),
      kicker: pick(child('.topbar-kicker'), ['font-size', 'font-weight', 'line-height', 'letter-spacing', 'color']),
      title: pick(child('.topbar-title'), ['font-size', 'font-weight', 'line-height', 'color']),
      date: pick(child('.topbar-date'), ['display', 'font-size', 'line-height', 'color', 'gap']),
      account: pick(child('.workspace-account-trigger'), ['height', 'padding', 'gap', 'border', 'border-radius', 'background-color', 'color', 'box-shadow']),
      avatar: pick(child('.workspace-account-trigger > span:first-child'), ['width', 'height', 'border', 'border-radius', 'font-size', 'font-weight', 'background-color', 'color', 'box-shadow']),
      name: pick(child('.workspace-account-label'), ['display', 'max-width', 'font-size', 'font-weight', 'line-height', 'color']),
      caret: pick(child('.workspace-account-caret'), ['display', 'width', 'height', 'color', 'transform']),
    };
  });
}

for (const width of [1440, 1024, 768, 390, 320]) test(`all three portal topbars share appearance and account interaction at ${width}px`, async ({ browser, baseURL }) => {
  const snapshots: { normal: unknown; open: unknown }[] = [];
  for (const role of roles) {
    const page = await browser.newPage({ baseURL, viewport: { width, height: 900 } });
    try {
      await page.emulateMedia({ reducedMotion: 'reduce' });
      await session(page, role);
      const account = page.locator('.workspace-topbar .workspace-account-trigger');
      await expect(page.locator('.workspace-account-label')).toHaveAttribute('title', displayName);
      await expect(account.locator('span').first()).toHaveText({ student: 'SV', school: 'NT', enterprise: 'DN' }[role]);
      await expect(page.locator('.workspace-account-label')).toHaveText({ student: 'Sinh viên', school: 'Nhà trường', enterprise: 'Doanh nghiệp' }[role]);
      await expect(account).toHaveCSS('border-radius', '10px');
      await expect(account.locator('span').first()).toHaveCSS('background-color', 'rgb(11, 146, 124)');
      const normal = await appearance(page);
      await page.locator('.workspace-topbar').screenshot({ path: `../target/shared-topbar-${role}-${width}.png`, animations: 'disabled' });
      await account.click();
      const popup = page.locator(role === 'student' ? '#account-menu' : '.role-menu-popover');
      await expect(popup).toBeVisible();
      await expect(popup).toContainText('topbar@example.test');
      const open = await appearance(page);
      const bounds = (await popup.boundingBox())!;
      expect(bounds.x).toBeGreaterThanOrEqual(0);
      expect(bounds.x + bounds.width).toBeLessThanOrEqual(width);
      await page.keyboard.press('Escape');
      await expect(account).toBeFocused();
      await expect(popup).toHaveCount(0);
      await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBeTruthy();
      snapshots.push({ normal, open });
    } finally { await page.close(); }
  }
  expect(snapshots[1], 'School uses the same header presentation as Student').toEqual(snapshots[0]);
  expect(snapshots[2], 'Enterprise uses the same header presentation as Student').toEqual(snapshots[0]);
});
