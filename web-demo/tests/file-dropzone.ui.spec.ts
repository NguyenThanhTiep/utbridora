import { expect, test, type Page, type Locator } from '@playwright/test';

async function drop(page: Page, zone: Locator, files: { name: string; content?: string; type?: string; size?: number }[]) {
  const transfer = await page.evaluateHandle(items => {
    const data = new DataTransfer();
    for (const item of items) data.items.add(new File([item.size ? new Uint8Array(item.size) : item.content ?? '%PDF-1.4 test'], item.name, { type: item.type || 'application/pdf' }));
    return data;
  }, files);
  await zone.dispatchEvent('dragenter', { dataTransfer: transfer });
  await zone.dispatchEvent('drop', { dataTransfer: transfer });
  await transfer.dispose();
}

test('drop forwards real files once and rejects multiple, unsupported, empty and oversized files', async ({ page }) => {
  await page.goto('/tests/fixtures/file-dropzone.html');
  const zone = page.getByRole('button', { name: 'Mở hộp chọn tệp', exact: true });
  await drop(page, zone, [{ name: 'minh-chung.pdf' }]);
  await expect(page.getByLabel('Số lần chọn')).toHaveText('1');
  await expect(page.locator('.u032-row')).toContainText('minh-chung.pdf');
  await expect(page.locator('.u032-row')).toContainText('Đã chọn, chưa tải lên');
  for (const files of [[{ name: 'one.pdf' }, { name: 'two.pdf' }], [{ name: 'wrong.zip' }],
    [{ name: 'empty.pdf', content: '' }], [{ name: 'large.pdf', size: 5*1024*1024 + 1 }]]) {
    await drop(page, zone, files);
    await expect(page.getByRole('alert')).toBeVisible();
    await expect(page.getByLabel('Số lần chọn')).toHaveText('1');
    await expect(page.locator('.u032-row')).toContainText('minh-chung.pdf');
  }
  await drop(page, zone, [{ name: 'sinh-vien.csv', type: 'text/csv', content: 'studentCode,email\n001,test@example.test' }]);
  await expect(page.getByLabel('Số lần chọn')).toHaveText('2');
  await expect(page.getByRole('alert')).toHaveCount(0);
  expect(await page.getByLabel('Tệp thử nghiệm', { exact: true }).evaluate(e => (e as HTMLInputElement).files?.[0].name)).toBe('sinh-vien.csv');
});

test('keyboard chooser preserves input label, reselecting the same file and clearing controlled selection', async ({ page }) => {
  await page.goto('/tests/fixtures/file-dropzone.html');
  const zone = page.getByRole('button', { name: 'Mở hộp chọn tệp', exact: true });
  for (const key of ['Enter', 'Space']) {
    await zone.focus();
    const chooser = page.waitForEvent('filechooser');
    await page.keyboard.press(key);
    await (await chooser).setFiles({ name: 'minh-chung.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') });
  }
  await expect(page.getByLabel('Số lần chọn')).toHaveText('2');
  await page.getByRole('button', { name: 'Xóa tệp đã chọn' }).click();
  await expect(page.locator('.u032-row')).toHaveCount(0);
});

test('an inherited disabled fieldset blocks drag/drop and the keyboard chooser', async ({ page }) => {
  await page.goto('/tests/fixtures/file-dropzone.html');
  await page.getByRole('button', { name: 'Khóa chọn tệp' }).click();
  const zone = page.getByRole('button', { name: 'Mở hộp chọn tệp', exact: true });
  await expect(zone).toBeDisabled();
  await drop(page, zone, [{ name: 'blocked.pdf' }]);
  await expect(page.getByLabel('Số lần chọn')).toHaveText('0');
  await expect(page.locator('.u032')).not.toHaveClass(/is-over/);
});

for (const colorScheme of ['light', 'dark'] as const) test(`upload remains readable on the light portal with OS ${colorScheme} preference`, async ({ page }, testInfo) => {
  await page.emulateMedia({ colorScheme, reducedMotion: 'reduce' });
  await page.setViewportSize({ width: 390, height: 844 });
  await page.goto('/tests/fixtures/file-dropzone.html');
  await expect(page.locator('.u032-zone')).toHaveCSS('background-color', 'rgb(255, 255, 255)');
  const contrast = await page.locator('.u032').evaluate(root => {
    const luminance = (value: string) => {
      const channels = value.match(/[\d.]+/g)!.slice(0, 3).map(Number).map(v => {
        const c = v / 255; return c <= .04045 ? c / 12.92 : ((c + .055) / 1.055) ** 2.4;
      });
      return .2126 * channels[0] + .7152 * channels[1] + .0722 * channels[2];
    };
    return ['.u032-label', '.u032-zone b', '.u032-zone small'].map(selector =>
      1.05 / (luminance(getComputedStyle(root.querySelector(selector)!).color) + .05));
  });
  for (const ratio of contrast) expect(ratio).toBeGreaterThanOrEqual(4.5);
  await page.getByRole('button', { name: 'Khóa chọn tệp' }).click();
  await expect(page.locator('.u032-zone')).toBeDisabled();
  await expect(page.locator('.u032-zone')).toHaveCSS('opacity', '1');
  await page.screenshot({ path: testInfo.outputPath(`readable-os-${colorScheme}.png`) });
});

for (const workspace of ['enterprise', 'internship', 'student']) for (const width of [1440, 390, 320]) {
  test(`${workspace} upload stays within ${width}px, light/dark and reduced motion keep truthful upload states`, async ({ page }, testInfo) => {
    await page.setViewportSize({ width, height: 900 });
    await page.goto('/tests/fixtures/file-dropzone.html?workspace=' + workspace);
    await page.getByLabel('Tệp thử nghiệm', { exact: true }).setInputFiles({ name: 'giay-dang-ky-doanh-nghiep-'.repeat(8) + '.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.4') });
    await page.getByLabel('Trạng thái thử nghiệm').selectOption('uploading');
    await expect(page.getByRole('progressbar')).not.toHaveAttribute('aria-valuenow');
    const backgrounds = [];
    for (const theme of ['light', 'dark']) {
      await page.evaluate(value => document.documentElement.dataset.theme = value, theme);
      await expect(page.locator('.u032-zone')).toHaveCSS('background-color', theme === 'light' ? 'rgb(255, 255, 255)' : 'rgb(19, 43, 50)');
      backgrounds.push(await page.locator('.u032-zone').evaluate(e => getComputedStyle(e).backgroundColor));
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
      const box = await page.locator('.u032-zone').boundingBox(); expect(box!.height).toBe(196);
      await page.screenshot({ path: testInfo.outputPath(`${workspace}-${width}-${theme}.png`) });
    }
    expect(backgrounds[0]).not.toBe(backgrounds[1]);
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.getByLabel('Trạng thái thử nghiệm').selectOption('success');
    await expect(page.locator('.u032-row')).toContainText('Đã hoàn tất');
    expect(await page.locator('.u032-st path').evaluate(e => getComputedStyle(e).strokeDashoffset)).toBe('0px');
    await page.getByLabel('Trạng thái thử nghiệm').selectOption('error');
    await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
    await expect(page.getByRole('progressbar')).toBeVisible();
    expect(await page.locator('.u032-spin').evaluate(e => getComputedStyle(e).animationName)).toBe('none');
  });
}

test('student CV drop uses the existing multipart binding and cannot be used before CLEAN', async ({ page }, testInfo) => {
  const student = '00000000-0000-4000-8000-000000000001', school = '00000000-0000-4000-8000-000000000011';
  const term = '00000000-0000-4000-8000-000000000021', fileId = '00000000-0000-4000-8000-000000000051';
  const uploads: string[] = [];
  let clean = false;
  await page.route('**/api/v1/**', route => {
    const request = route.request(), path = new URL(request.url()).pathname.replace('/api/v1', '');
    if (path === '/auth/me') return route.fulfill({ json: { userId: student, email: 'student@example.test', displayName: 'Sinh viên kiểm thử', memberships: [{ role: 'STUDENT', organizationId: school }] } });
    if (path === '/auth/me/permissions') return route.fulfill({ json: [{ role: 'STUDENT', organizationId: school, permissions: ['student_profile:self:read', 'student_profile:self:write'] }] });
    if (path === '/auth/csrf') return route.fulfill({ json: { headerName: 'X-CSRF-TOKEN', token: 'fixture-token' } });
    if (path === '/terms') return route.fulfill({ json: { content: [{ termId: term, schoolOrgId: school, termCode: 'HK1', status: 'ACTIVE' }], totalPages: 1 } });
    if (path === '/students/me/profile') return route.fulfill({ json: { studentUserId: student, schoolOrgId: school, profileVersion: 1, completeness: { complete: false, checks: [] }, profile: {
      termId: term, phone: null, majorCode: null, skillsDeclared: false, skills: [], customSkills: [], availabilityDeclared: false,
      availablePeriods: [], orientationCodes: [], subjectCodes: [], projects: [], certificates: [], cvFileId: null,
    } } });
    if (path === '/files' || path === '/files/' + fileId + '/metadata') {
      if (request.method() === 'POST') {
        uploads.push(request.postData()!);
        expect(request.headers()['x-csrf-token']).toBe('fixture-token');
        expect(request.headers()['idempotency-key']).toBeTruthy();
      }
      return route.fulfill({ status: request.method() === 'POST' ? 202 : 200, json: { fileId, resource: { resourceType: 'STUDENT_PROFILE', resourceId: student },
        purpose: 'CV', originalName: 'cv-sinh-vien.pdf', contentType: 'application/pdf', sizeBytes: 16, sha256: 'a'.repeat(64), scanStatus: clean ? 'CLEAN' : 'PENDING', createdAt: '2026-11-28T01:00:00Z', deletedAt: null } });
    }
    return route.fulfill({ json: { content: [], totalPages: 0 } });
  });
  await page.setViewportSize({ width: 320, height: 900 });
  await page.goto('/ho-so/nang-luc?tab=cv');
  await drop(page, page.locator('.student-upload .u032-zone'), [{ name: 'cv-sinh-vien.pdf' }]);
  await page.getByRole('button', { name: 'Tải CV lên', exact: true }).click();
  await expect.poll(() => uploads.length).toBe(1);
  for (const value of ['STUDENT_PROFILE', 'CV', student]) expect(uploads[0]).toContain(value);
  await expect(page.getByRole('button', { name: 'Dùng làm CV', exact: true })).toBeDisabled();
  clean = true;
  await page.getByRole('button', { name: 'Kiểm tra trạng thái tệp', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Dùng làm CV', exact: true })).toBeEnabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: testInfo.outputPath('student-cv-drop-320.png'), fullPage: true });
});
