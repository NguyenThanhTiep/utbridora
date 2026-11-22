import { expect, type Page } from '@playwright/test';

export async function expectSignedIn(page: Page) {
  await expect(page.locator('.workspace')).toBeVisible();
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính', includeHidden: true })).toHaveCount(1);
}

/** Follow the visible UI, including the school accordion and phone drawer. */
export async function openWorkspacePage(page: Page, title: string) {
  await expectSignedIn(page);
  const toggle = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await toggle.isVisible() && await toggle.getAttribute('aria-expanded') !== 'true') await toggle.click();
  const navigation = page.getByRole('navigation', { name: 'Điều hướng chính' });
  const entry = navigation.getByRole('button', { name: title, exact: true, includeHidden: true });
  // The enterprise T3 submenu is mounted only after its parent is opened.
  if (await entry.count() === 0) {
    const internship = navigation.locator('button.internship-parent');
    if (await internship.count() === 1 && await internship.getAttribute('aria-expanded') !== 'true') {
      await internship.click();
      // Opening the parent also navigates, which closes the phone drawer.
      if (await toggle.isVisible() && await toggle.getAttribute('aria-expanded') !== 'true') await toggle.click();
    }
  }
  await expect(entry).toHaveCount(1);
  if (!await entry.isVisible()) {
    const group = navigation.locator('.school-nav-group').filter({ has: page.getByRole('button', { name: title, exact: true, includeHidden: true }) });
    await expect(group).toHaveCount(1);
    const expand = group.locator('.school-nav-group-toggle');
    if (await expand.getAttribute('aria-expanded') !== 'true') await expand.click();
  }
  await expect(entry).toBeVisible();
  await entry.click();
}

export async function signOutFromAccountMenu(page: Page) {
  const trigger = page.locator('.topbar').getByRole('button', { name: /Mở menu tài khoản$/ });
  await expect(trigger).toBeVisible();
  if (await trigger.getAttribute('aria-expanded') !== 'true') await trigger.click();
  await page.locator('.role-menu-popover, #account-menu').getByRole('button', { name: 'Đăng xuất', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
}
