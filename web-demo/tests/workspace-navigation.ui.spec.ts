import { expect, test } from '@playwright/test';
import { expectSignedIn, openWorkspacePage, signOutFromAccountMenu } from './helpers/workspace';

for (const width of [1440, 390]) for (const role of ['SCHOOL_STAFF', 'STUDENT', 'ENTERPRISE', 'ADMIN'])
  test(`CI follows visible navigation and account logout for ${role} at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 900 }); await page.emulateMedia({ reducedMotion: 'reduce' });
    let authenticated = true;
    const organizationId = role === 'ADMIN' ? null : '00000000-0000-4000-8000-000000000011';
    await page.route('**/api/v1/**', route => {
      const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '');
      if (path === '/auth/me') return route.fulfill({ status: authenticated ? 200 : 401, json: authenticated ? {
        userId: 'test-user', displayName: 'Navigation tester', email: 'navigation@example.test', memberships: [{ role, organizationId }],
      } : {} });
      if (path === '/auth/me/permissions') return route.fulfill({ json: [{ role, organizationId,
        permissions: ['student_profile:self:read', 'student_profile:self:write', 'cooperation:enterprise:create', 'cooperation:school:review',
          'school:delegation:manage', 't3:workspace:read', ...(role === 'ADMIN' ? ['membership:admin:write'] : [])] }] });
      if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'navigation-ui-token' } });
      if (path === '/t3/context-assignments/me') return route.fulfill({ json: [] });
      if (path === '/auth/logout' && request.method() === 'POST') { authenticated = false; return route.fulfill({ status: 204 }); }
      if (request.method() === 'GET') return route.fulfill({ json: { content: [], page: 0, number: 0, size: 100, totalElements: 0, totalPages: 0 } });
      return route.fulfill({ status: 400, json: { errorCode: 'SCHEMA_VALIDATION' } });
    });
    await page.goto('/'); await expectSignedIn(page);
    if (role === 'SCHOOL_STAFF') {
      for (const title of ['Phân công quyền cán bộ', 'Duyệt hợp tác', 'Đề xuất sinh viên', 'Quản lý thực tập', 'Đánh giá thực tập']) {
        await openWorkspacePage(page, title);
        await expect(page.getByRole('heading', { name: title, exact: true, level: 1 })).toBeVisible();
      }
    } else if (role === 'ENTERPRISE') {
      await openWorkspacePage(page, 'Đánh giá thực tập');
      await expect(page.getByRole('heading', { name: 'Đánh giá thực tập', exact: true, level: 1 })).toBeVisible();
      await openWorkspacePage(page, 'Tài khoản của tôi');
    } else await openWorkspacePage(page, 'Tài khoản của tôi');
    await signOutFromAccountMenu(page);
    await expect(page.locator('.workspace')).toHaveCount(0);
  });
