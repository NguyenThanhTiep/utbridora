import { test, expect, type Page } from '@playwright/test';

// T2-02: S01/S02 theo BC11 (Contract Pack v1.0). Backend hồ sơ (T2-01) và học kỳ (T5) chưa có,
// nên test có nhãn “UI mock” dùng dữ liệu giả theo contract; chúng không thay thế tích hợp backend thật.
const fixture = (name: string) => {
  const value = process.env[name];
  if (!value) throw new Error('Missing test fixture: ' + name);
  return value;
};
async function signIn(page: Page, email: string) {
  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(fixture('E2E_PASSWORD'));
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
}
async function navigate(page: Page, title: string) {
  const menu = page.getByRole('button', { name: 'Mở menu điều hướng', exact: true });
  if (await menu.isVisible() && await menu.getAttribute('aria-expanded') !== 'true') await menu.click();
  await page.getByRole('button', { name: title, exact: true }).click();
  await expect(page.getByRole('heading', { name: title, exact: true }).first()).toBeVisible();
}
async function expectNoPageOverflow(page: Page) {
  await expect.poll(() => page.evaluate(() => document.documentElement.scrollWidth - window.innerWidth)).toBeLessThanOrEqual(1);
}

// Dữ liệu tổng hợp theo TermReference (BC21) và SelfProfileView (BC11); không phải dữ liệu thật.
const TERM_ID = '00000000-0000-4000-8000-000000000021';
const SCHOOL_ID = '00000000-0000-4000-8000-000000000011';
const term = (termId: string, termCode: string, status: string) =>
  ({ termId, termCode, schoolOrgId: SCHOOL_ID, startDate: '2026-09-01', endDate: '2027-01-15', status, version: 1 });
const termsPage = (content: unknown[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: content.length ? 1 : 0 });
const profileView = (profileVersion: number, phone: string | null) => ({
  studentUserId: '00000000-0000-4000-8000-000000000002', schoolOrgId: SCHOOL_ID, profileVersion,
  profile: {
    termId: TERM_ID, majorCode: 'SE', skillsDeclared: true,
    skills: [{ skillCode: 'JAVA', level: 3, yearsPractice: 1.5, source: 'SELF_DECLARED', note: null }],
    customSkills: [{ customSkillId: '00000000-0000-4000-8000-000000000031', name: 'Thiết kế giao diện', categoryCode: 'DESIGN', level: 2, yearsPractice: 1, note: 'Tự học' }],
    availabilityDeclared: true, availablePeriods: [{ startDate: '2026-12-01', endDate: '2027-02-28' }],
    orientationCodes: ['BACKEND'], subjectCodes: ['OOP'],
    projects: [{ projectId: '00000000-0000-4000-8000-000000000041', title: 'Ứng dụng thử nghiệm', description: 'Đồ án mẫu', role: 'Backend',
      skillCodes: ['JAVA'], url: 'https://example.test/project', evidenceFileIds: ['00000000-0000-4000-8000-000000000051'] }],
    certificates: [{ certificateId: '00000000-0000-4000-8000-000000000061', title: 'Chứng chỉ thử nghiệm', issuer: 'Đơn vị thử nghiệm', issuedOn: '2026-06-01', expiresOn: null, fileId: null }],
    cvFileId: '00000000-0000-4000-8000-000000000071', phone
  },
  completeness: { policyVersion: 'demo-v1', complete: true, checks: ['MAJOR_VALID', 'STUDENT_TERM_PRESENT', 'SKILLS_DECLARED', 'SKILLS_VALID', 'AVAILABILITY_DECLARED', 'AVAILABILITY_VALID']
    .map(code => ({ code, passed: true, reasonCode: 'OK' })) }
});
const envelope = (errorCode: string, message: string, fieldErrors: { field: string; code: string; message: string }[] = []) =>
  ({ errorCode, error: errorCode, message, requestId: '00000000-0000-4000-8000-000000000099', fieldErrors, retryAfterSeconds: null });
async function openProfile(page: Page) {
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await navigate(page, 'Thông tin chung');
}

test('Sinh viên thấy nhóm Sinh viên, mở Tổng quan và Thông tin chung', async ({ page }) => {
  const profileRequests: string[] = [];
  page.on('request', request => { if (new URL(request.url()).pathname.startsWith('/api/v1/students/')) profileRequests.push(request.url()); });
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await expect(nav.getByText('SINH VIÊN', { exact: true })).toBeVisible();
  await expect(nav.getByText('QUẢN TRỊ HỆ THỐNG', { exact: true })).toHaveCount(0);
  await navigate(page, 'Tổng quan');
  await expect(page.getByText('KHÔNG GIAN SINH VIÊN', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Mở thông tin chung', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Thông tin chung', exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Chưa xác định học kỳ', exact: true })).toBeVisible();
  await expect(page.locator('main')).toContainText(fixture('E2E_STUDENT_EMAIL'));
  expect(profileRequests).toEqual([]);
});

test('Quản trị viên không thấy nhóm Sinh viên', async ({ page }) => {
  await signIn(page, fixture('E2E_ADMIN_EMAIL'));
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await expect(nav.getByRole('button', { name: 'Tài khoản & phân quyền', exact: true })).toBeVisible();
  await expect(nav.getByText('SINH VIÊN', { exact: true })).toHaveCount(0);
  await expect(nav.getByRole('button', { name: 'Tổng quan', exact: true })).toHaveCount(0);
  await expect(nav.getByRole('button', { name: 'Thông tin chung', exact: true })).toHaveCount(0);
});

test('Hai màn sinh viên vừa màn hình 320px và 390px', async ({ page }) => {
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    for (const title of ['Tổng quan', 'Thông tin chung']) {
      await navigate(page, title);
      await expectNoPageOverflow(page);
    }
  }
});

test('UX UI mock: không có grant STUDENT hợp lệ thì không hiện nhóm Sinh viên', async ({ page }) => {
  // Giả lập trường chưa VERIFIED: backend không trả grant STUDENT nào.
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [] }));
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  const nav = page.getByRole('navigation', { name: 'Điều hướng chính' });
  await expect(nav.getByRole('button', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
  await expect(nav.getByText('SINH VIÊN', { exact: true })).toHaveCount(0);
  await expect(nav.getByRole('button', { name: 'Tổng quan', exact: true })).toHaveCount(0);
});

test('UX UI mock: S02 lưu đủ 14 trường, giữ kỹ năng, dự án, chứng chỉ và CV', async ({ page }) => {
  const puts: { body: Record<string, unknown>; headers: Record<string, string> }[] = [];
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/profile**', route => {
    const request = route.request();
    if (request.method() !== 'PUT') return route.fulfill({ json: profileView(3, null) });
    puts.push({ body: request.postDataJSON(), headers: request.headers() });
    return route.fulfill({ json: profileView(4, '0901234567') });
  });
  await openProfile(page);
  await expect(page.getByText('Học kỳ HK1-DEMO', { exact: false })).toBeVisible();
  const save = page.getByRole('button', { name: 'Lưu thông tin chung', exact: true });
  await expect(save).toBeDisabled();
  await page.getByLabel('Số điện thoại', { exact: true }).fill('0901234567');
  await save.click();
  await expect(page.getByText('Đã lưu thông tin chung.', { exact: true })).toBeVisible();
  expect(puts).toHaveLength(1);
  const { body, headers } = puts[0];
  const original = profileView(3, null).profile;
  expect(Object.keys(body).sort()).toEqual(['availabilityDeclared', 'availablePeriods', 'certificates', 'customSkills', 'cvFileId', 'expectedProfileVersion',
    'majorCode', 'orientationCodes', 'phone', 'projects', 'skills', 'skillsDeclared', 'subjectCodes', 'termId']);
  expect(body).toMatchObject({ expectedProfileVersion: 3, termId: TERM_ID, phone: '0901234567', majorCode: 'SE', skillsDeclared: true, availabilityDeclared: true });
  // Kỹ năng ghi theo CanonicalSkillInput: không gửi `source`.
  expect(body.skills).toEqual([{ skillCode: 'JAVA', level: 3, yearsPractice: 1.5, note: null }]);
  for (const key of ['customSkills', 'availablePeriods', 'orientationCodes', 'subjectCodes', 'projects', 'certificates', 'cvFileId'] as const) expect(body[key]).toEqual(original[key]);
  expect(headers['idempotency-key']).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
  const csrf: { headerName: string } = await (await page.request.get('/api/v1/auth/csrf')).json();
  expect(headers[csrf.headerName.toLowerCase()]).toBeTruthy();
  await expect(page.getByLabel('Số điện thoại', { exact: true })).toHaveValue('0901234567');
  await page.setViewportSize({ width: 320, height: 844 });
  await expectNoPageOverflow(page);
});

test('UX UI mock: 409 giữ bản nháp và cho tải lại dữ liệu mới nhất', async ({ page }) => {
  let latest = profileView(3, null);
  let puts = 0;
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/profile**', route => {
    if (route.request().method() !== 'PUT') return route.fulfill({ json: latest });
    puts += 1;
    // Lần 2: body kiểu cũ, errorCode sai định dạng Reason nên phải dùng `error` và không được coi là PROFILE_STALE.
    return route.fulfill({ status: 409, json: puts === 1
      ? envelope('PROFILE_STALE', 'Hồ sơ đã thay đổi, cần đánh giá lại trước khi đề xuất.')
      : { errorCode: 'profile_stale', error: 'CONFLICT', message: 'Thông điệp gốc từ server.' } });
  });
  await openProfile(page);
  const phone = page.getByLabel('Số điện thoại', { exact: true });
  const save = page.getByRole('button', { name: 'Lưu thông tin chung', exact: true });
  await phone.fill('0901234567');
  await save.click();
  await expect(page.getByText('Hồ sơ đã thay đổi ở nơi khác', { exact: false })).toBeVisible();
  await expect(phone).toHaveValue('0901234567');
  await expect(save).toBeDisabled();
  latest = profileView(4, '0911111111');
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Tải lại dữ liệu mới nhất', exact: true }).click();
  await expect(phone).toHaveValue('0911111111');
  await expect(page.getByText('Hồ sơ đã thay đổi ở nơi khác', { exact: false })).toHaveCount(0);
  await phone.fill('0922222222');
  await expect(save).toBeEnabled();
  await save.click();
  await expect(page.getByText('Chưa thể lưu do xung đột với yêu cầu hoặc dữ liệu hiện tại', { exact: false })).toBeVisible();
  await expect(page.getByText('Hồ sơ đã thay đổi ở nơi khác', { exact: false })).toHaveCount(0);
  await expect(page.getByText('Thông điệp gốc từ server', { exact: false })).toHaveCount(0);
  await expect(phone).toHaveValue('0922222222');
  await expect(save).toBeDisabled();
});

test('UX UI mock: lỗi nhập và fieldErrors 422 hiện dưới ô tương ứng', async ({ page }) => {
  let puts = 0;
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/profile**', route => {
    if (route.request().method() !== 'PUT') return route.fulfill({ json: profileView(3, null) });
    puts += 1;
    return route.fulfill({ status: 422, headers: { 'X-Request-ID': '00000000-0000-4000-8000-000000000099' },
      json: envelope('BUSINESS_VALIDATION', 'Dữ liệu chưa hợp lệ.', [
        { field: 'phone', code: 'BUSINESS_VALIDATION', message: 'Thông điệp gốc từ server cho số điện thoại.' },
        { field: 'unknownField', code: 'BUSINESS_VALIDATION', message: 'Thông điệp gốc từ server cho trường khác.' }]) });
  });
  await openProfile(page);
  const phone = page.getByLabel('Số điện thoại', { exact: true });
  const save = page.getByRole('button', { name: 'Lưu thông tin chung', exact: true });
  await phone.fill('123');
  await save.click();
  await expect(page.getByText('Số điện thoại cần từ 8 đến 20 ký tự.', { exact: true })).toBeVisible();
  await expect(phone).toHaveAttribute('aria-invalid', 'true');
  await phone.fill('0901234567');
  await expect(page.getByText('Số điện thoại cần từ 8 đến 20 ký tự.', { exact: true })).toHaveCount(0);
  const period = page.getByRole('group', { name: 'Khoảng thời gian 1', exact: true });
  await period.getByLabel('Đến ngày', { exact: true }).fill('2026-11-01');
  await save.click();
  await expect(period.getByText('Ngày bắt đầu phải trước hoặc trùng ngày kết thúc.', { exact: true })).toBeVisible();
  expect(puts).toBe(0);
  await period.getByLabel('Đến ngày', { exact: true }).fill('2027-03-31');
  await save.click();
  await expect(page.getByText('Một số thông tin chưa hợp lệ', { exact: false })).toBeVisible();
  expect(puts).toBe(1);
  await expect(page.getByText('Số điện thoại chưa được hệ thống chấp nhận.', { exact: true })).toBeVisible();
  await expect(phone).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByText('Một số dữ liệu khác trong hồ sơ chưa hợp lệ (mã yêu cầu: 00000000-0000-4000-8000-000000000099).', { exact: true })).toBeVisible();
  // Không hiển thị nguyên văn message của server.
  await expect(page.getByText('Thông điệp gốc từ server', { exact: false })).toHaveCount(0);
  // Sửa lại dữ liệu thì xóa lỗi cũ của server.
  await phone.fill('0912345678');
  await expect(page.getByText('Số điện thoại chưa được hệ thống chấp nhận.', { exact: true })).toHaveCount(0);
});

test('UX UI mock: 400 SCHEMA_VALIDATION có fieldErrors hiện dưới ô, 400 không có fieldErrors dùng thông báo core', async ({ page }) => {
  let puts = 0;
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/profile**', route => {
    if (route.request().method() !== 'PUT') return route.fulfill({ json: profileView(3, null) });
    puts += 1;
    return route.fulfill({ status: 400, json: puts === 1
      ? envelope('SCHEMA_VALIDATION', 'Thông điệp gốc từ server.', [{ field: 'phone', code: 'SCHEMA_VALIDATION', message: 'Thông điệp gốc từ server cho số điện thoại.' }])
      : envelope('INVALID_REQUEST', 'Thông điệp gốc từ server.') });
  });
  await openProfile(page);
  const phone = page.getByLabel('Số điện thoại', { exact: true });
  const save = page.getByRole('button', { name: 'Lưu thông tin chung', exact: true });
  await phone.fill('0901234567');
  await save.click();
  await expect(page.getByText('Số điện thoại chưa được hệ thống chấp nhận.', { exact: true })).toBeVisible();
  await expect(phone).toHaveAttribute('aria-invalid', 'true');
  await phone.fill('0912345678');
  await save.click();
  await expect(page.getByText('Thông tin chưa hợp lệ. Kiểm tra dữ liệu', { exact: false })).toBeVisible();
  await expect(page.getByText('Số điện thoại chưa được hệ thống chấp nhận.', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Thông điệp gốc từ server', { exact: false })).toHaveCount(0);
  expect(puts).toBe(2);
});

test('UX UI mock: lưu gặp 403, 429, HTML, JSON null rồi 401', async ({ page }) => {
  const keys: string[] = [];
  const responses = [
    { status: 403, contentType: 'application/json', body: JSON.stringify(envelope('ACCESS_DENIED', 'Thông điệp gốc từ server.')) },
    { status: 429, contentType: 'application/json', body: JSON.stringify(envelope('RESEND_RATE_LIMITED', 'Thông điệp gốc từ server.')) },
    { status: 500, contentType: 'text/html', body: '<html><body>Thông điệp gốc từ server.</body></html>' },
    { status: 500, contentType: 'application/json', body: 'null' },
    { status: 401, contentType: 'application/json', body: JSON.stringify({ error: 'SESSION_EXPIRED', message: 'Thông điệp gốc từ server.' }) }];
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/profile**', route => {
    if (route.request().method() !== 'PUT') return route.fulfill({ json: profileView(3, null) });
    keys.push(route.request().headers()['idempotency-key']);
    return route.fulfill(responses[keys.length - 1]);
  });
  await openProfile(page);
  await page.getByLabel('Số điện thoại', { exact: true }).fill('0901234567');
  const save = page.getByRole('button', { name: 'Lưu thông tin chung', exact: true });
  for (const expected of ['Bạn không có quyền thực hiện thao tác', 'Có quá nhiều yêu cầu', 'Không thể xử lý yêu cầu', 'Không thể xử lý yêu cầu']) {
    await save.click();
    await expect(page.getByText(expected, { exact: false })).toBeVisible();
    await expect(page.getByText('Thông điệp gốc từ server', { exact: false })).toHaveCount(0);
    await expect(save).toBeEnabled();
  }
  await save.click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await expect(page.getByText('Phiên đã hết hạn hoặc bị thu hồi', { exact: false })).toBeVisible();
  // Cùng nội dung bản nháp thì thử lại bằng cùng Idempotency-Key (C.0 v1.0 dòng 92-94).
  expect(keys).toHaveLength(5);
  expect(new Set(keys).size).toBe(1);
});

test('UX UI mock: nhiều học kỳ đang mở thì cho chọn, chưa có hồ sơ thì báo rõ', async ({ page }) => {
  const requested: string[] = [];
  const otherTerm = '00000000-0000-4000-8000-000000000022';
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([
    term(TERM_ID, 'HK1-DEMO', 'ACTIVE'), term(otherTerm, 'HK2-DEMO', 'ACTIVE'), term('00000000-0000-4000-8000-000000000023', 'HK0-DEMO', 'CLOSED')]) }));
  await page.route('**/api/v1/students/me/profile**', route => {
    requested.push(new URL(route.request().url()).searchParams.get('termId') || '');
    return route.fulfill({ status: 404, json: { error: 'RESOURCE_NOT_FOUND', message: 'Không tìm thấy.' } });
  });
  await openProfile(page);
  await expect(page.getByRole('heading', { name: 'Chọn học kỳ', exact: true })).toBeVisible();
  const select = page.getByRole('combobox', { name: 'Học kỳ', exact: true });
  await expect(select.locator('option')).toHaveText(['Chọn học kỳ', 'HK1-DEMO', 'HK2-DEMO']);
  expect(requested).toEqual([]);
  await select.selectOption(otherTerm);
  await expect(page.getByRole('heading', { name: 'Bạn chưa có hồ sơ sinh viên', exact: true })).toBeVisible();
  expect(requested).toEqual([otherTerm]);
});

test('UX UI mock: chưa có hồ sơ thì tạo bằng hồ sơ rỗng đủ 14 trường, version 0', async ({ page }) => {
  const puts: { body: Record<string, unknown>; headers: Record<string, string> }[] = [];
  const created = profileView(1, null);
  created.profile = { ...created.profile, majorCode: null, skillsDeclared: false, skills: [], customSkills: [], availabilityDeclared: false,
    availablePeriods: [], orientationCodes: [], subjectCodes: [], projects: [], certificates: [], cvFileId: null };
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/profile**', route => {
    const request = route.request();
    if (request.method() !== 'PUT') return route.fulfill({ status: 404, json: envelope('RESOURCE_NOT_FOUND', 'Không tìm thấy.') });
    puts.push({ body: request.postDataJSON(), headers: request.headers() });
    return route.fulfill({ json: created });
  });
  await openProfile(page);
  await expect(page.getByRole('heading', { name: 'Bạn chưa có hồ sơ sinh viên', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Tạo hồ sơ', exact: true }).click();
  await expect(page.getByText('Đã tạo hồ sơ.', { exact: false })).toBeVisible();
  await expect(page.getByLabel('Số điện thoại', { exact: true })).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Lưu thông tin chung', exact: true })).toBeDisabled();
  expect(puts).toHaveLength(1);
  expect(puts[0].body).toEqual({ expectedProfileVersion: 0, termId: TERM_ID, majorCode: null, skillsDeclared: false, skills: [], customSkills: [],
    availabilityDeclared: false, availablePeriods: [], orientationCodes: [], subjectCodes: [], projects: [], certificates: [], cvFileId: null, phone: null });
  expect(puts[0].headers['idempotency-key']).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});
