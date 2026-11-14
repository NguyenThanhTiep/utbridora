import { test, expect, type Page } from '@playwright/test';
import type { ConsentGrant, EvaluationDto, InternshipInvitation, OpportunityView, PreferenceView, SelfProfileView, StudentResultDto } from '../src/modules/student/types';
test.beforeEach(async ({ page }) => {
  const empty = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 };
  await page.route('**/api/v1/taxonomy/categories?**', route => route.fulfill({ json: empty }));
  await page.route('**/api/v1/taxonomy/skills?**', route => route.fulfill({ json: { taxonomyVersion: 'skills-v1', skills: empty } }));
});

// T2-02: các ca “UI mock” dùng dữ liệu tổng hợp theo BC11, không thay thế tích hợp thật.
// Luồng browser qua backend nằm riêng trong student-runtime.spec.ts.
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
const profileView = (profileVersion: number, phone: string | null): SelfProfileView => ({
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
  completeness: { policyVersion: 'demo-v1', complete: true, checks: (['MAJOR_VALID', 'STUDENT_TERM_PRESENT', 'SKILLS_DECLARED', 'SKILLS_VALID', 'AVAILABILITY_DECLARED', 'AVAILABILITY_VALID'] as const)
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

test('Các màn sinh viên vừa màn hình 320px và 390px', async ({ page }) => {
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  for (const width of [320, 390]) {
    await page.setViewportSize({ width, height: 844 });
    for (const title of ['Tổng quan', 'Thông tin chung', 'Năng lực', 'Cơ hội thực tập', 'Nhiệm vụ & sản phẩm', 'Báo cáo & đánh giá']) {
      await navigate(page, title);
      await expectNoPageOverflow(page);
    }
    await page.getByRole('tab', { name: 'Kết quả đánh giá', exact: true }).click();
    await expectNoPageOverflow(page);
    await navigate(page, 'Lời mời & nguyện vọng');
    await expectNoPageOverflow(page);
    await page.getByRole('tab', { name: 'Nguyện vọng', exact: true }).click();
    await expectNoPageOverflow(page);
    await page.getByRole('tab', { name: 'Lời mời', exact: true }).click();
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
  await expect(page.getByRole('heading', { name: 'Chưa thể đọc hồ sơ trong học kỳ này', exact: true })).toBeVisible();
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
  await expect(page.getByRole('heading', { name: 'Chưa thể đọc hồ sơ trong học kỳ này', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Tạo hồ sơ', exact: true }).click();
  await expect(page.getByText('Đã tạo hồ sơ.', { exact: false })).toBeVisible();
  await expect(page.getByLabel('Số điện thoại', { exact: true })).toHaveValue('');
  await expect(page.getByRole('button', { name: 'Lưu thông tin chung', exact: true })).toBeDisabled();
  expect(puts).toHaveLength(1);
  expect(puts[0].body).toEqual({ expectedProfileVersion: 0, termId: TERM_ID, majorCode: null, skillsDeclared: false, skills: [], customSkills: [],
    availabilityDeclared: false, availablePeriods: [], orientationCodes: [], subjectCodes: [], projects: [], certificates: [], cvFileId: null, phone: null });
  expect(puts[0].headers['idempotency-key']).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
});

// T2-08: lời mời/consent tổng hợp theo BC23/BC24, chỉ dùng trong test; không thay thế API T5 hay tích hợp thật.
const ENTERPRISE_ID = '00000000-0000-4000-8000-000000000012';
const FUTURE = '2099-01-01T00:00:00Z', PAST = '2020-01-01T00:00:00Z';
const UUID_V4 = /^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/;
const invitation = (n: number, title: string, state: InternshipInvitation['state'], expiresAt: string): InternshipInvitation => {
  const suffix = String(n).padStart(2, '0'), item = '00000000-0000-4000-8000-0000000002' + suffix;
  return { invitationId: '00000000-0000-4000-8000-0000000001' + suffix, proposalItemId: item, placementCandidateId: item,
    studentUserId: '00000000-0000-4000-8000-000000000002', schoolOrgId: SCHOOL_ID, enterpriseOrgId: ENTERPRISE_ID, termId: TERM_ID,
    opportunityId: '00000000-0000-4000-8000-000000000081', title, enterpriseName: 'Doanh nghiệp thử nghiệm', state, expiresAt,
    decidedAt: null, decisionReasonCode: null, version: 1, placementId: null };
};
async function mockInvitations(page: Page, current: () => InternshipInvitation[], consents: ConsentGrant[] = []) {
  await page.route('**/api/v1/invitations/me?**', route => route.fulfill({ json: termsPage(current()) }));
  await page.route('**/api/v1/students/me/consents?**', route => route.fulfill({ json: termsPage(consents) }));
  await page.route('**/api/v1/school/terms/*/profile-sharing-policy', route => route.fulfill({ json:
    { schoolOrgId: SCHOOL_ID, termId: TERM_ID, version: 3, allowedFields: ['skills', 'phone'], updatedAt: null, updatedByUserId: null } }));
}
const invitationPanel = (page: Page, title: string) => page.locator('section.panel').filter({ has: page.getByRole('heading', { name: title, exact: true }) });

test('UX UI mock: lời mời hiện trạng thái tiếng Việt, quá hạn thì khóa phản hồi, nhận lời không tự cấp quyền chia sẻ', async ({ page }) => {
  let invitations = [invitation(1, 'Thực tập Java thử nghiệm', 'PENDING', FUTURE), invitation(2, 'Lời mời đã quá hạn', 'PENDING', PAST),
    invitation(3, 'Lời mời đã từ chối', 'DECLINED', FUTURE)];
  const decisions: { body: Record<string, unknown>; key: string | undefined }[] = [], consentWrites: string[] = [];
  page.on('request', request => {
    if (request.method() === 'POST' && new URL(request.url()).pathname.startsWith('/api/v1/students/me/consents')) consentWrites.push(request.url());
  });
  await mockInvitations(page, () => invitations);
  await page.route('**/api/v1/invitations/*/decision', route => {
    const request = route.request();
    decisions.push({ body: request.postDataJSON(), key: request.headers()['idempotency-key'] });
    invitations = invitations.map((item, index): InternshipInvitation => index === 0 ? { ...item, state: 'ACCEPTED', version: 2 } : item);
    return route.fulfill({ json: invitations[0] });
  });
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await navigate(page, 'Lời mời & nguyện vọng');
  const open = invitationPanel(page, 'Thực tập Java thử nghiệm'), late = invitationPanel(page, 'Lời mời đã quá hạn');
  const declined = invitationPanel(page, 'Lời mời đã từ chối');
  await expect(open.getByText(/ · Chờ phản hồi$/)).toBeVisible();
  await expect(declined.getByText(/ · Đã từ chối$/)).toBeVisible();
  await expect(late.getByText('Đã quá hạn phản hồi.', { exact: true })).toBeVisible();
  await expect(late.getByRole('button', { name: 'Nhận lời mời', exact: true })).toHaveCount(0);
  await expect(declined.getByRole('button', { name: 'Đồng ý chia sẻ các mục đã chọn', exact: true })).toHaveCount(0);
  await open.getByRole('button', { name: 'Nhận lời mời', exact: true }).click();
  await expect(open.getByText(/ · Đã nhận lời$/)).toBeVisible();
  await expect(open.getByRole('button', { name: 'Rút nhận lời', exact: true })).toBeVisible();
  expect(decisions).toHaveLength(1);
  expect(decisions[0].body).toEqual({ decision: 'ACCEPT', expectedVersion: 1, reasonCode: null, reasonText: null });
  expect(decisions[0].key).toMatch(UUID_V4);
  expect(consentWrites).toEqual([]);
  await expect(page.getByText('Đang có hiệu lực', { exact: true })).toHaveCount(0);
});

test('UX UI mock: từ chối cần xác nhận; 409 INVITATION_EXPIRED báo rõ và tải lại', async ({ page }) => {
  let listCalls = 0;
  const bodies: Record<string, unknown>[] = [];
  await mockInvitations(page, () => { listCalls++; return [invitation(1, 'Thực tập Java thử nghiệm', 'PENDING', FUTURE)]; });
  await page.route('**/api/v1/invitations/*/decision', route => {
    bodies.push(route.request().postDataJSON());
    return route.fulfill({ status: 409, json: envelope('INVITATION_EXPIRED', 'Nội dung máy chủ không hiển thị') });
  });
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await navigate(page, 'Lời mời & nguyện vọng');
  const panel = invitationPanel(page, 'Thực tập Java thử nghiệm');
  page.once('dialog', dialog => void dialog.dismiss());
  await panel.getByRole('button', { name: 'Từ chối', exact: true }).click();
  expect(bodies).toEqual([]);
  const before = listCalls;
  page.once('dialog', dialog => void dialog.accept());
  await panel.getByRole('button', { name: 'Từ chối', exact: true }).click();
  await expect(page.getByText('Lời mời đã hết hạn phản hồi.', { exact: true })).toBeVisible();
  await expect.poll(() => listCalls).toBeGreaterThan(before);
  expect(bodies).toEqual([{ decision: 'DECLINE', expectedVersion: 1, reasonCode: 'STUDENT_DECLINED', reasonText: null }]);
  await expect(page.getByText('Nội dung máy chủ không hiển thị')).toHaveCount(0);
});

test('UX UI mock: tải lỗi thì báo và cho tải lại; quyền chia sẻ hiện đúng nhãn; xem chi tiết lời mời', async ({ page }) => {
  let fail = true;
  const accepted: InternshipInvitation = { ...invitation(1, 'Thực tập Java thử nghiệm', 'ACCEPTED', FUTURE), version: 2, decidedAt: '2026-11-03T08:00:00Z' };
  const consent: ConsentGrant = { consentId: '00000000-0000-4000-8000-000000000091', consentVersion: 1, lineageVersion: 1,
    studentUserId: '00000000-0000-4000-8000-000000000002', schoolOrgId: SCHOOL_ID, termId: TERM_ID, recipientOrgId: ENTERPRISE_ID,
    invitationId: accepted.invitationId, purpose: 'INTERNSHIP_SELECTION', fieldWhitelist: ['skills', 'gpa'], grantedAt: '2026-11-03T08:00:00Z',
    expiresAt: FUTURE, revokedAt: null, policyVersion: 'demo-consent-v1', replacedByConsentId: null };
  await mockInvitations(page, () => [accepted], [consent]);
  await page.route('**/api/v1/invitations/me?**', route => fail ? route.fulfill({ status: 500, json: envelope('INTERNAL_ERROR', 'Lỗi') }) : route.fallback());
  await page.route('**/api/v1/invitations/' + accepted.invitationId, route => route.fulfill({ json: accepted }));
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await navigate(page, 'Lời mời & nguyện vọng');
  await expect(page.getByText('Không tải được lời mời hoặc quyền chia sẻ.', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Chưa có lời mời thực tập', exact: true })).toHaveCount(0);
  fail = false;
  await page.getByRole('button', { name: 'Tải lại lời mời', exact: true }).click();
  const panel = invitationPanel(page, 'Thực tập Java thử nghiệm');
  await expect(panel.getByText(/ · Đã nhận lời$/)).toBeVisible();
  await expect(page.getByText('Không tải được lời mời hoặc quyền chia sẻ.', { exact: true })).toHaveCount(0);
  await expect(page.getByText('Doanh nghiệp thử nghiệm: Kỹ năng, Điểm trung bình', { exact: false })).toBeVisible();
  await expect(page.getByText('Đang có hiệu lực', { exact: true })).toBeVisible();
  await panel.getByRole('button', { name: 'Xem chi tiết', exact: true }).click();
  await expect(panel.getByText('Chưa có Placement', { exact: true })).toBeVisible();
});

// T2-07/T2-08: cơ hội và nguyện vọng tổng hợp theo BC22, chỉ dùng trong test cô lập.
// Không thay thế API T5 và không phải bằng chứng đã tích hợp T5.
const OPPORTUNITY_ID = '00000000-0000-4000-8000-000000000081';
const opportunity = (id: string, title: string, closedAt: string): OpportunityView => ({
  opportunityId: id, cooperationId: id, demandId: '00000000-0000-4000-8000-000000000072', schoolOrgId: SCHOOL_ID, enterpriseOrgId: ENTERPRISE_ID,
  termId: TERM_ID, enterpriseName: 'Doanh nghiệp thử nghiệm', title, majorCodes: ['SE'], dateRange: { startDate: '2027-01-05', endDate: '2027-03-31' },
  benefits: 'Hỗ trợ chi phí đi lại', location: 'Địa điểm thử nghiệm', publishedAt: '2026-11-01T00:00:00Z', closedAt, version: 1
});
const preference = (id: string, opportunityId: string, intentVersion: number, version: number, status: PreferenceView['status']): PreferenceView => ({
  preferenceId: id, studentUserId: '00000000-0000-4000-8000-000000000002', schoolOrgId: SCHOOL_ID, termId: TERM_ID, opportunityId, intentVersion, version,
  status, message: null, createdAt: '2026-11-0' + intentVersion + 'T08:00:00Z', withdrawnAt: status === 'WITHDRAWN' ? '2026-11-05T08:00:00Z' : null,
  reviewedAt: status === 'REVIEWED' ? '2026-11-06T08:00:00Z' : null, reviewedByUserId: null
});
const preferenceItem = (page: Page, title: string) => page.locator('li.student-list-item').filter({ has: page.getByRole('heading', { name: title, exact: true }) });

test('UX UI mock: cơ hội lọc theo kỳ và ngành, xem chi tiết, gửi nguyện vọng lần đầu với version 0 rồi mở tab Nguyện vọng', async ({ page }) => {
  const listQueries: URLSearchParams[] = [], posts: { body: Record<string, unknown>; key: string | undefined }[] = [];
  let preferences: PreferenceView[] = [];
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/catalogs?**', route => route.fulfill({ json:
    { catalogVersion: 'school-demo-v1', entries: termsPage([{ code: 'SE', name: 'Kỹ thuật phần mềm', majorCodes: [], status: 'ACTIVE' }]) } }));
  await page.route('**/api/v1/opportunities?**', route => {
    listQueries.push(new URL(route.request().url()).searchParams);
    return route.fulfill({ json: termsPage([opportunity(OPPORTUNITY_ID, 'Thực tập Java thử nghiệm', FUTURE)]) });
  });
  await page.route('**/api/v1/opportunities/' + OPPORTUNITY_ID, route => route.fulfill({ json: opportunity(OPPORTUNITY_ID, 'Thực tập Java thử nghiệm', FUTURE) }));
  await page.route('**/api/v1/students/me/preferences**', route => {
    const request = route.request();
    if (request.method() !== 'POST') return route.fulfill({ json: termsPage(preferences) });
    posts.push({ body: request.postDataJSON(), key: request.headers()['idempotency-key'] });
    preferences = [{ ...preference('00000000-0000-4000-8000-000000000091', OPPORTUNITY_ID, 1, 1, 'SUBMITTED'), message: 'Em mong muốn vị trí này' }];
    return route.fulfill({ status: 201, json: preferences[0] });
  });
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await navigate(page, 'Cơ hội thực tập');
  await expect(page.getByRole('heading', { name: 'Thực tập Java thử nghiệm', exact: true })).toBeVisible();
  expect(Object.fromEntries(listQueries[0])).toEqual({ termId: TERM_ID, page: '0', size: '20' });
  await page.getByLabel('Vị trí hoặc doanh nghiệp', { exact: true }).fill('Java');
  await page.getByLabel('Ngành', { exact: true }).selectOption('SE');
  await page.getByRole('button', { name: 'Tìm cơ hội', exact: true }).click();
  await expect.poll(() => listQueries.length).toBe(2);
  expect(Object.fromEntries(listQueries[1])).toEqual({ termId: TERM_ID, q: 'Java', majorCode: 'SE', page: '0', size: '20' });
  await page.getByRole('button', { name: 'Xem chi tiết', exact: true }).click();
  await expect(page.getByText('Hỗ trợ chi phí đi lại', { exact: true })).toBeVisible();
  await expect(page.getByText('Kỹ thuật phần mềm', { exact: true })).toBeVisible();
  await page.getByLabel('Lời nhắn cho nhà trường (không bắt buộc)', { exact: true }).fill('Em mong muốn vị trí này');
  await page.getByRole('button', { name: 'Gửi nguyện vọng', exact: true }).click();
  await expect(page.getByText('Đã gửi nguyện vọng.', { exact: false })).toBeVisible();
  await expect(page.getByText(/Nguyện vọng gần nhất \(lần 1\): Đã gửi, chờ nhà trường xét/)).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gửi nguyện vọng', exact: true })).toHaveCount(0);
  expect(posts).toHaveLength(1);
  expect(posts[0].body).toEqual({ action: 'SUBMIT', opportunityId: OPPORTUNITY_ID, message: 'Em mong muốn vị trí này', expectedIntentVersion: 0 });
  expect(posts[0].key).toMatch(UUID_V4);
  await page.getByRole('button', { name: 'Xem hoặc rút nguyện vọng', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Lời mời & nguyện vọng', exact: true })).toBeVisible();
  await expect(page.getByRole('tab', { name: 'Nguyện vọng', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(preferenceItem(page, 'Thực tập Java thử nghiệm').getByText('Đã gửi, chờ nhà trường xét', { exact: true })).toBeVisible();
});

test('UX UI mock: dịch vụ cơ hội chưa sẵn sàng báo rõ; cơ hội vừa đóng thì không cho gửi nguyện vọng', async ({ page }) => {
  let unavailable = true;
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/catalogs?**', route => route.fulfill({ status: 404, json: envelope('RESOURCE_NOT_FOUND', 'Không có') }));
  await page.route('**/api/v1/opportunities?**', route => unavailable
    ? route.fulfill({ status: 503, json: envelope('DEPENDENCY_UNAVAILABLE', 'Nội dung máy chủ không hiển thị') })
    : route.fulfill({ json: termsPage([opportunity(OPPORTUNITY_ID, 'Thực tập Java thử nghiệm', FUTURE)]) }));
  await page.route('**/api/v1/opportunities/' + OPPORTUNITY_ID, route => route.fulfill({ json: opportunity(OPPORTUNITY_ID, 'Thực tập Java thử nghiệm', PAST) }));
  await page.route('**/api/v1/students/me/preferences**', route => route.fulfill({ json: termsPage([]) }));
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await navigate(page, 'Cơ hội thực tập');
  await expect(page.getByRole('heading', { name: 'Chưa tải được cơ hội thực tập', exact: true })).toBeVisible();
  await expect(page.getByText('Dịch vụ cơ hội thực tập chưa sẵn sàng. Vui lòng thử lại sau.', { exact: true })).toBeVisible();
  await expect(page.getByText('Nội dung máy chủ không hiển thị')).toHaveCount(0);
  unavailable = false;
  await page.getByRole('button', { name: 'Thử lại', exact: true }).click();
  await page.getByRole('button', { name: 'Xem chi tiết', exact: true }).click();
  await expect(page.getByText('Cơ hội đã hết hạn nhận nguyện vọng.', { exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Gửi nguyện vọng', exact: true })).toHaveCount(0);
});

test('UX UI mock: nguyện vọng hiện trạng thái và lịch sử, chỉ rút khi chưa xét; 409 PREFERENCE_REVIEWED báo rõ và tải lại', async ({ page }) => {
  const other = '00000000-0000-4000-8000-000000000082';
  let listCalls = 0;
  const bodies: Record<string, unknown>[] = [];
  await page.route('**/api/v1/students/me/preferences**', route => {
    const request = route.request();
    if (request.method() === 'POST') {
      bodies.push(request.postDataJSON());
      return route.fulfill({ status: 409, json: envelope('PREFERENCE_REVIEWED', 'Nội dung máy chủ không hiển thị') });
    }
    listCalls++;
    return route.fulfill({ json: termsPage([preference('00000000-0000-4000-8000-000000000091', OPPORTUNITY_ID, 1, 2, 'WITHDRAWN'),
      preference('00000000-0000-4000-8000-000000000092', OPPORTUNITY_ID, 2, 3, 'SUBMITTED'), preference('00000000-0000-4000-8000-000000000093', other, 1, 2, 'REVIEWED')]) });
  });
  await page.route('**/api/v1/opportunities/' + OPPORTUNITY_ID, route => route.fulfill({ json: opportunity(OPPORTUNITY_ID, 'Thực tập Java thử nghiệm', FUTURE) }));
  await page.route('**/api/v1/opportunities/' + other, route => route.fulfill({ status: 404, json: envelope('RESOURCE_NOT_FOUND', 'Không có') }));
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await navigate(page, 'Lời mời & nguyện vọng');
  // Mặc định mở tab Lời mời; mũi tên phải chuyển sang tab Nguyện vọng và giữ focus trên tab.
  await page.getByRole('tab', { name: 'Lời mời', exact: true }).press('ArrowRight');
  await expect(page.getByRole('tab', { name: 'Nguyện vọng', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('tab', { name: 'Nguyện vọng', exact: true })).toBeFocused();
  const submitted = preferenceItem(page, 'Thực tập Java thử nghiệm'), reviewed = preferenceItem(page, 'Cơ hội đã đóng hoặc không còn hiển thị');
  await expect(submitted.getByText('Đã gửi, chờ nhà trường xét', { exact: true })).toBeVisible();
  await expect(reviewed.getByText('Nhà trường đã xét', { exact: true })).toBeVisible();
  await expect(reviewed.getByRole('button', { name: 'Rút nguyện vọng', exact: true })).toHaveCount(0);
  await submitted.getByRole('button', { name: 'Xem chi tiết', exact: true }).click();
  await expect(submitted.getByText('Lần 1: Đã rút', { exact: false })).toBeVisible();
  const before = listCalls;
  page.once('dialog', dialog => void dialog.accept());
  await submitted.getByRole('button', { name: 'Rút nguyện vọng', exact: true }).click();
  await expect(page.getByText('Nguyện vọng đã được nhà trường xét nên không thể rút. Muốn đổi ý, hãy gửi nguyện vọng mới.', { exact: true })).toBeVisible();
  await expect.poll(() => listCalls).toBeGreaterThan(before);
  expect(bodies).toEqual([{ action: 'WITHDRAW', preferenceId: '00000000-0000-4000-8000-000000000092', expectedVersion: 3, reasonCode: 'STUDENT_WITHDREW' }]);
  await expect(page.getByText('Nội dung máy chủ không hiển thị')).toHaveCount(0);
});

// T2-10: nhiệm vụ, hồ sơ thực tập và kết quả tổng hợp theo CP-WS1/CP-WS2/CP-WS4, chỉ dùng trong test cô lập.
// Không thay thế API T3 và không phải bằng chứng đã tích hợp T3; GET /t3/me/results còn chờ T3-11.
const CASE_ID = '00000000-0000-4000-8000-000000000301', PLACEMENT_ID = '00000000-0000-4000-8000-000000000302';
const currentUserId = async (page: Page) => ((await (await page.request.get('/api/v1/auth/me')).json()) as { userId: string }).userId;
const internshipTask = (studentUserId: string) => ({
  taskId: '00000000-0000-4000-8000-000000000303', title: 'Nhiệm vụ thử nghiệm', description: 'Mô tả thử nghiệm',
  groupId: '00000000-0000-4000-8000-000000000304', dueAt: FUTURE, priority: 'NORMAL', lateAllowed: true, version: 1,
  assignments: [{ assignmentId: '00000000-0000-4000-8000-000000000305', caseId: CASE_ID, studentUserId, state: 'NEW', version: 1, currentSubmissionId: null }]
});
// Ví dụ CP-WS4 (v1.0 dòng 624–655): trọng số 40/60, điểm 5/3, total100=76.00 do server trả.
const evaluation = (state: EvaluationDto['state']): EvaluationDto => ({
  evaluationId: '00000000-0000-4000-8000-000000000306', caseId: CASE_ID,
  template: { templateId: '00000000-0000-4000-8000-000000000307', templateVersion: 1, criteria: [
    { criterionCode: 'QUALITY', label: 'Chất lượng công việc', weight: 40 }, { criterionCode: 'PROCESS', label: 'Quy trình thực hiện', weight: 60 }] },
  scores: [{ criterionCode: 'QUALITY', score: 5, comment: 'Hoàn thành tốt', evidenceFileIds: [] }, { criterionCode: 'PROCESS', score: 3, comment: null, evidenceFileIds: [] }],
  total100: 76, state, revisionNo: 1, version: 3, publishedAt: state === 'PUBLISHED' ? '2026-12-20T08:00:00Z' : null, decisionRef: null
});
async function mockInternship(page: Page, studentUserId: string, tasks = true) {
  await page.route('**/api/v1/t3/tasks?**', route => route.fulfill({ json: termsPage(tasks ? [internshipTask(studentUserId)] : []) }));
  await page.route('**/api/v1/t3/calendar?**', route => route.fulfill({ json: termsPage([]) }));
  await page.route('**/api/v1/t3/cases/' + CASE_ID, route => route.fulfill({ json: { caseId: CASE_ID, placementId: PLACEMENT_ID, studentUserId,
    schoolOrgId: SCHOOL_ID, enterpriseOrgId: ENTERPRISE_ID, termId: TERM_ID, state: 'IN_PROGRESS', version: 1 } }));
  await page.route('**/api/v1/invitations/me?**', route => route.fulfill({ json: termsPage([{ ...invitation(1, 'Thực tập Java thử nghiệm', 'ACCEPTED', FUTURE), placementId: PLACEMENT_ID }]) }));
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
}
async function openResults(page: Page) {
  await navigate(page, 'Báo cáo & đánh giá');
  await page.getByRole('tab', { name: 'Kết quả đánh giá', exact: true }).click();
  return page.getByRole('region', { name: 'Kết quả đánh giá', exact: true });
}

test('UX UI mock: S07 thay Công việc của tôi, hiện vị trí đã chốt, kỳ thực tập và nhiệm vụ; báo cáo tuần chuyển sang S08', async ({ page }) => {
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await mockInternship(page, await currentUserId(page));
  await expect(page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Công việc của tôi', exact: true })).toHaveCount(0);
  await navigate(page, 'Nhiệm vụ & sản phẩm');
  const mine = page.getByRole('region', { name: 'Thực tập của tôi', exact: true });
  await expect(mine.getByRole('heading', { name: 'Thực tập Java thử nghiệm', exact: true })).toBeVisible();
  await expect(mine.getByText('Doanh nghiệp thử nghiệm', { exact: true })).toBeVisible();
  await expect(mine.getByText('Đang thực tập', { exact: true })).toBeVisible();
  await expect(mine.getByText(/^Kỳ HK1-DEMO:/)).toBeVisible();
  await expect(page.locator('article').getByRole('button', { name: 'Nhiệm vụ thử nghiệm', exact: true })).toBeVisible();
  await expect(page.locator('article').getByRole('button', { name: 'Bắt đầu làm', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Mở báo cáo tuần', exact: true })).toHaveCount(0);
  await navigate(page, 'Báo cáo & đánh giá');
  await expect(page.getByRole('tab', { name: 'Báo cáo tuần', exact: true })).toHaveAttribute('aria-selected', 'true');
  await expect(page.getByRole('combobox', { name: 'Hồ sơ cho báo cáo', exact: true }).locator('option', { hasText: 'Hồ sơ …000301' })).toHaveCount(1);
});

test('UX UI mock: chưa có nhiệm vụ thì không đoán caseId và không gọi API kết quả', async ({ page }) => {
  const resultCalls: string[] = [];
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await mockInternship(page, await currentUserId(page), false);
  await page.route('**/api/v1/invitations/me?**', route => route.fulfill({ json: termsPage([]) }));
  await page.route('**/api/v1/t3/me/results?**', route => { resultCalls.push(route.request().url()); return route.fulfill({ status: 500, json: envelope('INTERNAL_ERROR', 'Lỗi') }); });
  await navigate(page, 'Nhiệm vụ & sản phẩm');
  await expect(page.getByRole('heading', { name: 'Chưa có thông tin thực tập để hiển thị', exact: true })).toBeVisible();
  await navigate(page, 'Báo cáo & đánh giá');
  await page.getByRole('tab', { name: 'Kết quả đánh giá', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Chưa có hồ sơ thực tập', exact: true })).toBeVisible();
  expect(resultCalls).toEqual([]);
});

test('UX UI mock: kết quả HIDDEN, bản chưa công bố hoặc bị gửi kèm sai đều chỉ báo chưa công bố và không lộ số điểm', async ({ page }) => {
  let result: StudentResultDto = { caseId: CASE_ID, visibility: 'HIDDEN', evaluation: null, reasonCode: 'NOT_AVAILABLE' };
  const queries: (string | null)[] = [];
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await mockInternship(page, await currentUserId(page));
  await page.route('**/api/v1/t3/me/results?**', route => { queries.push(new URL(route.request().url()).searchParams.get('caseId')); return route.fulfill({ json: result }); });
  const region = await openResults(page);
  const hidden = region.getByRole('heading', { name: 'Chưa có kết quả được công bố', exact: true });
  await expect(hidden).toBeVisible();
  await expect(region).not.toContainText(/\d/);
  expect(queries).toEqual([CASE_ID]);
  // Server gửi kèm bản đã chấm khi HIDDEN (vi phạm CP-WS4) hoặc VISIBLE nhưng chưa PUBLISHED: giao diện vẫn không hiển thị.
  const leaks: StudentResultDto[] = [{ ...result, evaluation: evaluation('PUBLISHED') },
    { caseId: CASE_ID, visibility: 'VISIBLE', reasonCode: 'VISIBLE', evaluation: evaluation('SUBMITTED') }];
  for (const leaked of leaks) {
    result = leaked;
    const response = page.waitForResponse('**/api/v1/t3/me/results?**');
    await page.getByRole('button', { name: 'Tải lại kết quả', exact: true }).click();
    await response;
    await expect(hidden).toBeVisible();
    await expect(region).not.toContainText(/\d/);
  }
});

test('UX UI mock: kết quả VISIBLE hiện đúng total100 và điểm theo tiêu chí do server trả, vừa màn 320px', async ({ page }) => {
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await mockInternship(page, await currentUserId(page));
  await page.route('**/api/v1/t3/me/results?**', route => route.fulfill({ json: { caseId: CASE_ID, visibility: 'VISIBLE', reasonCode: 'VISIBLE', evaluation: evaluation('PUBLISHED') } }));
  const region = await openResults(page);
  await expect(region.getByText('76,00', { exact: true })).toBeVisible();
  const quality = region.getByRole('row').filter({ has: page.getByRole('rowheader', { name: 'Chất lượng công việc', exact: true }) });
  await expect(quality).toContainText('40,00%');
  await expect(quality).toContainText('5,00');
  await expect(quality).toContainText('Hoàn thành tốt');
  const process = region.getByRole('row').filter({ has: page.getByRole('rowheader', { name: 'Quy trình thực hiện', exact: true }) });
  await expect(process).toContainText('60,00%');
  await expect(process).toContainText('3,00');
  await page.setViewportSize({ width: 320, height: 844 });
  await expectNoPageOverflow(page);
});

test('UX UI mock: API kết quả trả 404 thì báo chưa sẵn sàng, không hiện nội dung server hay số điểm', async ({ page }) => {
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await mockInternship(page, await currentUserId(page));
  await page.route('**/api/v1/t3/me/results?**', route => route.fulfill({ status: 404, json: envelope('RESOURCE_NOT_FOUND', 'Nội dung máy chủ không hiển thị') }));
  const region = await openResults(page);
  await expect(region.getByText('Chưa xem được kết quả: chức năng chưa sẵn sàng hoặc hồ sơ không còn truy cập được. Vui lòng thử lại sau.', { exact: true })).toBeVisible();
  await expect(region).not.toContainText(/\d/);
  await expect(page.getByText('Nội dung máy chủ không hiển thị')).toHaveCount(0);
});

// T2-09/S04: hồ sơ và tệp tổng hợp theo BC11/CP-FILE, chỉ dùng trong test cô lập.
// Không thay thế dịch vụ tệp T1 và không phải bằng chứng đã có bộ quét T1-07.
const CV_ID = '00000000-0000-4000-8000-000000000071';
const fileDto = (fileId: string, resourceId: string, purpose: 'CV' | 'EVIDENCE', scanStatus: 'PENDING' | 'CLEAN', originalName: string) => ({
  fileId, resource: { resourceType: 'STUDENT_PROFILE', resourceId }, purpose, originalName, contentType: 'application/pdf', sizeBytes: 1024,
  sha256: 'a'.repeat(64), scanStatus, createdAt: '2026-11-01T00:00:00Z', deletedAt: null
});
async function mockCapabilityProfile(page: Page, userId: string, onPut: (body: Record<string, unknown>, key: string | undefined) => { status: number; json: unknown }) {
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: termsPage([term(TERM_ID, 'HK1-DEMO', 'ACTIVE')]) }));
  await page.route('**/api/v1/students/me/profile**', route => {
    const request = route.request();
    if (request.method() !== 'PUT') return route.fulfill({ json: profileView(3, '0901234567') });
    return route.fulfill(onPut(request.postDataJSON(), request.headers()['idempotency-key']));
  });
  await page.route('**/api/v1/files/*/metadata', route => {
    const id = new URL(route.request().url()).pathname.split('/')[4];
    return route.fulfill({ json: fileDto(id, userId, id === CV_ID ? 'CV' : 'EVIDENCE', 'CLEAN', id === CV_ID ? 'cv.pdf' : 'minh-chung.pdf') });
  });
}

test('UX UI mock: S04 thêm dự án gửi PUT hồ sơ đầy đủ, giữ kỹ năng, chứng chỉ và CV', async ({ page }) => {
  const puts: { body: Record<string, unknown>; key: string | undefined }[] = [];
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  await mockCapabilityProfile(page, await currentUserId(page), (body, key) => {
    puts.push({ body, key });
    const saved = profileView(4, '0901234567');
    saved.profile.projects = [...saved.profile.projects, { ...(body.projects as SelfProfileView['profile']['projects'])[1], projectId: '00000000-0000-4000-8000-000000000042' }];
    return { status: 200, json: saved };
  });
  await navigate(page, 'Năng lực');
  await expect(page.getByRole('heading', { name: 'Ứng dụng thử nghiệm', exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Thêm dự án', exact: true }).click();
  await page.getByLabel('Tên dự án', { exact: true }).fill('Hệ thống quản lý thực tập');
  await page.getByLabel('Vai trò của bạn', { exact: true }).fill('Frontend');
  await page.getByLabel('Đường dẫn dự án (https)', { exact: true }).fill('http://example.test/intern');
  await page.getByRole('button', { name: 'Lưu dự án', exact: true }).click();
  await expect(page.getByText('Đường dẫn phải là URL https:// hợp lệ, không chứa tài khoản hoặc mật khẩu.', { exact: true })).toBeVisible();
  expect(puts).toEqual([]);
  await page.getByLabel('Đường dẫn dự án (https)', { exact: true }).fill('https://example.test/intern');
  await page.getByRole('checkbox', { name: 'JAVA', exact: true }).check();
  await page.getByRole('button', { name: 'Lưu dự án', exact: true }).click();
  await expect(page.getByText('Đã thêm dự án.', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Hệ thống quản lý thực tập', exact: true })).toBeVisible();
  expect(puts).toHaveLength(1);
  expect(puts[0].key).toMatch(UUID_V4);
  expect(puts[0].body.expectedProfileVersion).toBe(3);
  expect(puts[0].body.projects).toEqual([
    { projectId: '00000000-0000-4000-8000-000000000041', title: 'Ứng dụng thử nghiệm', description: 'Đồ án mẫu', role: 'Backend', skillCodes: ['JAVA'],
      url: 'https://example.test/project', evidenceFileIds: ['00000000-0000-4000-8000-000000000051'] },
    { projectId: null, title: 'Hệ thống quản lý thực tập', description: '', role: 'Frontend', skillCodes: ['JAVA'], url: 'https://example.test/intern', evidenceFileIds: [] }]);
  expect(puts[0].body.skills).toEqual([{ skillCode: 'JAVA', level: 3, yearsPractice: 1.5, note: null }]);
  expect(puts[0].body.certificates).toHaveLength(1);
  expect(puts[0].body.cvFileId).toBe(CV_ID);
});

test('UX UI mock: S04 tải CV lên chờ quét, chỉ dùng làm CV khi đã an toàn; FILE_NOT_CLEAN báo rõ và thử lại cùng key', async ({ page }) => {
  const NEW_CV = '00000000-0000-4000-8000-000000000072';
  const uploads: string[] = [], puts: { body: Record<string, unknown>; key: string | undefined }[] = [];
  let clean = false;
  await signIn(page, fixture('E2E_STUDENT_EMAIL'));
  const userId = await currentUserId(page);
  await mockCapabilityProfile(page, userId, (body, key) => {
    puts.push({ body, key });
    if (puts.length === 1) return { status: 409, json: envelope('FILE_NOT_CLEAN', 'Nội dung máy chủ không hiển thị') };
    const saved = profileView(4, '0901234567'); saved.profile.cvFileId = NEW_CV;
    return { status: 200, json: saved };
  });
  await page.route('**/api/v1/files/' + NEW_CV + '/metadata', route => route.fulfill({ json: fileDto(NEW_CV, userId, 'CV', clean ? 'CLEAN' : 'PENDING', 'cv-moi.pdf') }));
  await page.route('**/api/v1/files', route => {
    uploads.push(route.request().postDataBuffer()?.toString('utf8') ?? '');
    return route.fulfill({ status: 202, json: fileDto(NEW_CV, userId, 'CV', 'PENDING', 'cv-moi.pdf') });
  });
  await navigate(page, 'Năng lực');
  await page.getByRole('tab', { name: 'CV', exact: true }).click();
  await page.getByLabel('Chọn tệp CV', { exact: true }).setInputFiles({ name: 'cv-moi.pdf', mimeType: 'application/pdf', buffer: Buffer.from('%PDF-1.7\n%%EOF') });
  await page.getByRole('button', { name: 'Tải CV lên', exact: true }).click();
  await expect.poll(() => uploads.length).toBe(1);
  for (const part of ['name="resourceType"', 'STUDENT_PROFILE', 'name="purpose"', 'name="resourceId"', userId]) expect(uploads[0]).toContain(part);
  const use = page.getByRole('button', { name: 'Dùng làm CV', exact: true });
  await expect(page.getByText('Đang chờ quét', { exact: true })).toBeVisible();
  await expect(use).toBeDisabled();
  clean = true;
  await page.getByRole('button', { name: 'Kiểm tra trạng thái tệp', exact: true }).click();
  await expect(use).toBeEnabled();
  await use.click();
  await expect(page.getByText('Tệp chưa được xác nhận an toàn. Chờ trạng thái "Đã xác nhận an toàn" rồi gắn lại.', { exact: true })).toBeVisible();
  await expect(page.getByText('Nội dung máy chủ không hiển thị')).toHaveCount(0);
  await use.click();
  await expect(page.getByText('Đã gắn CV vào hồ sơ.', { exact: true })).toBeVisible();
  expect(puts.map(item => item.body.cvFileId)).toEqual([NEW_CV, NEW_CV]);
  expect(puts[1].key).toBe(puts[0].key);
});
