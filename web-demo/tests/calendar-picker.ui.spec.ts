import { expect, test, type Page } from '@playwright/test';
import { cohort, grants, group, ids, me, mentor, mockContextLabels } from './fixtures/internship';
import { openWorkspacePage } from './helpers/workspace';

const enterprise = '11111111-1111-4111-8111-111111111111';
const school = '22222222-2222-4222-8222-222222222222';
const term = '44444444-4444-4444-8444-444444444444';
const pageOf = (content: unknown[]) => ({
  content,
  page: 0,
  size: 100,
  totalElements: content.length,
  totalPages: content.length ? 1 : 0,
});

async function setupEnterpriseDemand(page: Page) {
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/auth/me') {
      return route.fulfill({
        json: {
          userId: 'ent-user',
          displayName: 'Enterprise Calendar Tester',
          email: 'ent@example.test',
          memberships: [{ role: 'ENTERPRISE', organizationId: enterprise }],
        },
      });
    }
    if (path === '/auth/me/permissions') {
      return route.fulfill({
        json: [{ role: 'ENTERPRISE', organizationId: enterprise, permissions: ['cooperation:enterprise:create'] }],
      });
    }
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'cal-csrf' } });
    if (path === '/cooperation/organizations') {
      return route.fulfill({ json: [{ id: enterprise, name: 'Công ty Công nghệ', type: 'ENTERPRISE' }] });
    }
    if (path === '/enterprise/demand-references/schools') {
      return route.fulfill({ json: pageOf([{ schoolOrgId: school, name: 'Trường Đại học A' }]) });
    }
    if (path === '/enterprise/demand-references/terms') {
      return route.fulfill({
        json: pageOf([{ termId: term, termCode: 'HK1-2027', startDate: '2027-01-01', endDate: '2027-06-30' }]),
      });
    }
    if (path === '/enterprise/demand-references/academic-catalogs') {
      return route.fulfill({ json: [{ version: 'v1', majors: [{ majorCode: 'SE', majorName: 'Kỹ thuật phần mềm' }] }] });
    }
    if (path.startsWith('/taxonomy/skills/releases')) {
      return route.fulfill({ json: [{ version: 'tv1', active: true, items: [{ skillCode: 'JAVA', name: 'Java' }] }] });
    }
    return route.fulfill({ json: pageOf([]) });
  });
  await page.goto('/');
  await openWorkspacePage(page, 'Nhu cầu thực tập');
  await page.getByRole('button', { name: 'Tạo nhu cầu', exact: true }).click();
  await expect(page.getByLabel('Ngày bắt đầu thực tập', { exact: true })).toBeVisible();
}

test('demand range fields open Lucian 030 range calendar with hover ribbon, animated month step and atomic apply', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await setupEnterpriseDemand(page);

  const startInput = page.getByLabel('Ngày bắt đầu thực tập', { exact: true });
  const endInput = page.getByLabel('Ngày kết thúc thực tập', { exact: true });

  await startInput.click();
  const dialog = page.getByRole('dialog', { name: 'Ngày bắt đầu thực tập' });
  await expect(dialog).toBeVisible();
  await expect(dialog.locator('.u030-pill')).toHaveCount(2);

  // Dialog remains cleanly bounded inside viewport height & width.
  const box = (await dialog.boundingBox())!;
  expect(box.y).toBeGreaterThanOrEqual(8);
  expect(box.y + box.height).toBeLessThanOrEqual(892);

  await dialog.getByRole('button', { name: 'Tháng sau', exact: true }).click();
  const applyBtn = dialog.getByRole('button', { name: 'Áp dụng', exact: true });
  await expect(applyBtn).toHaveAttribute('aria-disabled', 'true');

  const day5 = dialog.locator('.u030-d:not(:disabled)', { hasText: /^5$/ }).first();
  const day14 = dialog.locator('.u030-d:not(:disabled)', { hasText: /^14$/ }).first();
  await day5.click();
  await day14.hover();
  await expect(dialog.locator('.u030-cell.in')).toHaveCount(10);
  await day14.click();

  await expect(dialog.locator('.u030-sum')).toContainText('10 ngày');
  await expect(applyBtn).toHaveAttribute('aria-disabled', 'false');
  await applyBtn.click();
  await expect(dialog).toHaveCount(0);

  const startVal = await startInput.inputValue();
  const endVal = await endInput.inputValue();
  expect(startVal).toMatch(/^\d{4}-\d{2}-05$/);
  expect(endVal).toMatch(/^\d{4}-\d{2}-14$/);

  // Direct Playwright .fill() remains completely compatible.
  await startInput.fill('2027-02-01');
  await endInput.fill('2027-05-01');
  await expect(startInput.locator('..').locator('.vn-date-display')).toHaveText('01/02/2027');
  await expect(endInput.locator('..').locator('.vn-date-display')).toHaveText('01/05/2027');
});

test('datetime-local Lucian 030 popover commits date and time cleanly at 390px mobile without horizontal overflow', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 });
  await setupEnterpriseDemand(page);

  const deadline = page.getByLabel('Hạn tuyển', { exact: true });
  await deadline.click();
  const dialog = page.getByRole('dialog', { name: 'Hạn tuyển' });
  await expect(dialog).toBeVisible();

  const box = (await dialog.boundingBox())!;
  expect(box.x).toBeGreaterThanOrEqual(0);
  expect(box.x + box.width).toBeLessThanOrEqual(390);
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(true);

  await dialog.locator('.u030-d:not(:disabled)', { hasText: /^15$/ }).first().click();
  await dialog.getByLabel('Giờ', { exact: true }).selectOption('14');
  await dialog.getByLabel('Phút', { exact: true }).selectOption('30');
  await dialog.getByRole('button', { name: 'Áp dụng', exact: true }).click();
  await expect(dialog).toHaveCount(0);

  expect(await deadline.inputValue()).toMatch(/^\d{4}-\d{2}-15T14:30(?::00)?$/);
  await expect(deadline.locator('..').locator('.vn-date-display')).toContainText('14:30');
});

test('student availability range calendar supports 320px viewport, keyboard arrows and Escape focus return', async ({ page }) => {
  await page.setViewportSize({ width: 320, height: 800 });
  const organizationId = '00000000-0000-4000-8000-000000000011';
  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/auth/me') {
      return route.fulfill({
        json: {
          userId: 'stu-cal',
          displayName: 'Nguyễn Minh An',
          email: 'student@example.test',
          memberships: [{ role: 'STUDENT', organizationId }],
        },
      });
    }
    if (path === '/auth/me/permissions') {
      return route.fulfill({
        json: [{ role: 'STUDENT', organizationId, permissions: ['student_profile:self:read', 'student_profile:self:write'] }],
      });
    }
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'stu-csrf' } });
    if (path === '/terms') {
      return route.fulfill({
        json: pageOf([{ termId: term, termCode: 'HK1-DEMO', schoolOrgId: organizationId, startDate: '2026-09-01', endDate: '2027-01-15', status: 'ACTIVE', version: 1 }]),
      });
    }
    if (path === '/students/me/profile') {
      return route.fulfill({
        json: {
          studentUserId: 'stu-cal',
          schoolOrgId: organizationId,
          profileVersion: 1,
          profile: {
            termId: term,
            majorCode: 'SE',
            skillsDeclared: true,
            skills: [],
            customSkills: [],
            availabilityDeclared: false,
            availablePeriods: [],
            orientationCodes: [],
            subjectCodes: [],
            projects: [],
            certificates: [],
            cvFileId: null,
            phone: '0901234567',
          },
          completeness: { policyVersion: 'demo-v1', complete: false, checks: [] },
        },
      });
    }
    if (path === '/academic-catalog/majors' || path.startsWith('/students/me/academic-catalog')) {
      return route.fulfill({ json: { schoolOrgId: organizationId, version: 'v1', majors: [{ majorCode: 'SE', majorName: 'Kỹ thuật phần mềm' }], orientations: [], subjects: [] } });
    }
    return route.fulfill({ json: pageOf([]) });
  });

  await page.goto('/');
  await openWorkspacePage(page, 'Thông tin chung');
  await page.getByLabel('Tôi khai báo thời gian có thể tham gia thực tập', { exact: true }).check();
  await page.getByRole('button', { name: 'Thêm khoảng thời gian', exact: true }).click();

  const fromInput = page.getByLabel('Từ ngày', { exact: true });
  await fromInput.click();
  const dialog = page.getByRole('dialog', { name: 'Từ ngày' });
  await expect(dialog).toBeVisible();
  const box = (await dialog.boundingBox())!;
  expect(box.x + box.width).toBeLessThanOrEqual(320);

  await page.keyboard.press('ArrowRight');
  await page.keyboard.press('Escape');
  await expect(dialog).toHaveCount(0);
  await expect(fromInput).toBeFocused();
});

test('type="month" Lucian popover selects month across years and reduced motion disables slide durations', async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 900 });
  await page.emulateMedia({ reducedMotion: 'reduce' });

  await page.route('**/api/v1/**', route => {
    const path = new URL(route.request().url()).pathname.replace('/api/v1', '');
    if (path === '/auth/me') return route.fulfill({ json: me });
    if (path.endsWith('/permissions')) {
      return route.fulfill({ json: [{ ...grants[0], permissions: [...grants[0].permissions, 't3:task:create'] }] });
    }
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 't3-csrf' } });
    if (path === '/t3/context-assignments/me') return route.fulfill({ json: [mentor] });
    if (path === '/t3/cohorts') return route.fulfill({ json: pageOf([cohort]) });
    if (path.endsWith('/groups')) return route.fulfill({ json: pageOf([group]) });
    if (path === '/t3/calendar') return route.fulfill({ json: [] });
    return route.fulfill({ json: pageOf([]) });
  });
  await mockContextLabels(page);

  await page.goto('/');
  await openWorkspacePage(page, 'Không gian thực tập');
  await page.getByLabel('Phân công đang làm việc', { exact: true }).selectOption(ids.mentor);
  await page.getByRole('button', { name: 'Xem nhóm ' + group.code, exact: true }).click();
  await page.getByRole('button', { name: 'Lịch công việc', exact: true }).click();

  const monthInput = page.getByLabel('Tháng', { exact: true });
  await monthInput.click();
  const dialog = page.getByRole('dialog', { name: 'Tháng' });
  await expect(dialog).toBeVisible();
  const animDuration = await dialog.evaluate(node => parseFloat(getComputedStyle(node).animationDuration));
  expect(animDuration).toBeLessThanOrEqual(0.01);

  await dialog.getByRole('button', { name: 'Năm sau', exact: true }).click();
  await dialog.getByRole('button', { name: 'Tháng 3', exact: true }).click();
  await expect(dialog).toHaveCount(0);
  await expect(monthInput.locator('..').locator('.vn-date-display')).toHaveText(/^Tháng 3\/\d{4}$/);
});
