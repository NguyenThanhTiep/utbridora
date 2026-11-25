import { expect, test } from '@playwright/test';
import { openWorkspacePage } from './helpers/workspace';
import { cohort, grants, hr, ids, me, mockContextLabels } from './fixtures/internship';

for (const author of [false, true]) for (const width of [1552, 1024, 768, 390, 320]) {
  test(`HR header contains its labels and scope with demand permission ${author} at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 });
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.clock.install({ time: new Date('2026-11-19T10:00:00Z') });
    const pageOf = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 });
    await page.route('**/api/v1/**', route => route.fulfill({ json: pageOf([]) }));
    await mockContextLabels(page);
    await page.route('**/api/v1/auth/me', route => route.fulfill({ json: me }));
    await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [{ ...grants[0],
      permissions: author ? [...grants[0].permissions, 'cooperation:enterprise:create'] : grants[0].permissions }] }));
    await page.route('**/api/v1/t3/context-assignments/me', route => route.fulfill({ json: [hr] }));
    await page.route('**/api/v1/t3/cohorts?*', route => route.fulfill({ json: pageOf([cohort]) }));
    await page.goto('/');
    await openWorkspacePage(page, 'Không gian thực tập');
    await page.getByLabel('Phân công đang làm việc', { exact: true }).selectOption(ids.hr);
    const header = page.locator('.t3-topbar');
    const label = header.locator('label[for="internship-assignment"]');
    const account = header.getByRole('button', { name: /Mở menu tài khoản$/ });
    const checkFrame = async () => {
      await page.evaluate(() => { document.documentElement.style.scrollBehavior = 'auto'; window.scrollTo(0, 0); });
      await expect.poll(() => page.evaluate(() => {
        const box = (selector: string) => document.querySelector(selector)!.getBoundingClientRect();
        const frame = box('.t3-topbar'), scope = box('.t3-context-bar'), text = box('label[for="internship-assignment"]');
        return scope.top >= frame.top - 1 && scope.bottom <= frame.bottom + 1
          && text.top >= frame.top && text.bottom <= frame.bottom
          && box('#main-content').top >= frame.bottom - 1 && document.documentElement.scrollWidth <= innerWidth;
      })).toBe(true);
    };
    await expect(label).toBeVisible();
    await expect(account).toBeVisible();
    await checkFrame();
    const details = header.locator('.t3-context-details');
    await details.locator('summary').click();
    await expect(details.getByText('Mã kỳ thực tập', { exact: true })).toBeVisible();
    await checkFrame();
    await details.locator('summary').click();
    await account.click();
    await expect(header.getByRole('button', { name: 'Đăng xuất', exact: true })).toBeVisible();
    await account.click();
    await page.evaluate(() => { document.body.style.minHeight = '1800px'; window.scrollTo(0, 160); });
    await expect(header).toHaveClass(/is-condensed/);
    if (width <= 600) await expect.poll(async () => (await header.boundingBox())!.height).toBeLessThan(100);
    else await expect(label).toBeVisible();
    await expect(account).toBeVisible();
  });
}
