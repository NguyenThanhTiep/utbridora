import { test, expect, type Page } from '@playwright/test';

// Browser behavior tests: API responses are isolated fixtures, not real integration evidence.
const org = '11111111-1111-4111-8111-111111111111';
const school = '22222222-2222-4222-8222-222222222222';
const term = '33333333-3333-4333-8333-333333333333';
async function session(page: Page, grants = true) {
  await page.route('**/api/v1/enterprise/demand-references/schools?**', route => route.fulfill({ json: {
    content: [{ schoolOrgId: school, name: 'Trường kiểm thử' }], page: 0, size: 100, totalElements: 1, totalPages: 1,
  } }));
  await page.route('**/api/v1/enterprise/demand-references/terms?**', route => route.fulfill({ json: {
    content: [{ termId: term, termCode: 'HK1-2027', startDate: '2027-01-01', endDate: '2027-06-30' }], page: 0, size: 100, totalElements: 1, totalPages: 1,
  } }));
  await page.route('**/api/v1/enterprise/demand-references/academic-catalog?**', route => route.fulfill({ json: {
    schoolOrgId: school, termId: term, academicCatalogVersion: 'v1',
  } }));
  await page.route('**/api/v1/taxonomy/skills?**', route => route.fulfill({ json: {
    taxonomyVersion: 'v1', skills: { content: [], page: 0, size: 10, totalElements: 0, totalPages: 0 },
  } }));
  // New week-2 panels stay isolated in this UX-only harness; real workflows have a separate live test.
  await page.route('**/api/v1/enterprise/verifications?**', route => route.fulfill({ json: { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 } }));
  await page.route('**/api/v1/enterprise/cooperations?**', route => route.fulfill({ json: { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 } }));
  await page.route('**/api/v1/auth/**', route => {
    const path = new URL(route.request().url()).pathname;
    const json = path.endsWith('/permissions') ? (grants ? [{ role: 'ENTERPRISE', organizationId: org, permissions: ['cooperation:enterprise:create'] }] : [])
      : path.endsWith('/csrf') ? { headerName: 'X-CSRF-TOKEN', token: 'test-csrf' }
      : { userId: 'test-user', email: 'enterprise@example.test', displayName: 'Enterprise tester', memberships: [{ role: 'ENTERPRISE', organizationId: org }] };
    return route.fulfill({ json });
  });
}
async function open(page: Page) {
  await page.goto('/');
  await page.getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
}
async function fill(page: Page, selectTaxonomy = true) {
  await page.getByRole('button', { name: 'Tạo nhu cầu', exact: true }).click();
  await page.getByLabel('Trường nhận nhu cầu', { exact: true }).selectOption(school);
  await page.getByLabel('Kỳ thực tập', { exact: true }).selectOption(term);
  await page.getByLabel('Tên nhu cầu', { exact: true }).fill('Java internship');
  await page.getByLabel('Số lượng vị trí').fill('5');
  await page.getByLabel('Hạn tuyển').fill('2027-01-01T09:00');
  await page.getByLabel('Mô tả công việc').fill('Build APIs');
  await page.getByLabel('Ngày bắt đầu thực tập').fill('2027-02-01');
  await page.getByLabel('Ngày kết thúc thực tập').fill('2027-05-01');
  await page.getByLabel('Phiên bản danh mục học vụ').selectOption('v1');
  if (selectTaxonomy) {
    const taxonomy = page.getByLabel('Phiên bản danh mục kỹ năng');
    await expect(taxonomy).toBeEnabled();
    await taxonomy.selectOption({ index: 1 });
  }
}
const empty = { content: [], page: 0, size: 25, totalElements: 0, totalPages: 0 };

test('demand references are choices and changing school clears the previous term and academic version', async ({ page }) => {
  await session(page);
  const otherSchool = '55555555-5555-4555-8555-555555555555';
  await page.route('**/api/v1/enterprise/demands**', route => route.fulfill({ json: empty }));
  await page.route('**/api/v1/enterprise/demand-references/schools?**', route => route.fulfill({ json: {
    ...empty, content: [{ schoolOrgId: school, name: 'Trường A' }, { schoolOrgId: otherSchool, name: 'Trường B' }], totalElements: 2, totalPages: 1,
  } }));
  await page.route('**/api/v1/enterprise/demand-references/terms?**', route => route.fulfill({ json: {
    ...empty, ...(new URL(route.request().url()).searchParams.get('schoolOrgId') === school ? {
      content: [{ termId: term, termCode: 'HK1-2027', startDate: '2027-01-01', endDate: '2027-06-30' }], totalElements: 1, totalPages: 1,
    } : {}),
  } }));
  await open(page);await fill(page);
  for (const label of ['Trường nhận nhu cầu', 'Kỳ thực tập', 'Phiên bản danh mục học vụ', 'Phiên bản danh mục kỹ năng']) {
    await expect(page.getByLabel(label, { exact: true })).toHaveJSProperty('tagName', 'SELECT');
  }
  await expect(page.getByLabel('Trường nhận nhu cầu')).toHaveValue(school);
  await page.getByLabel('Trường nhận nhu cầu').selectOption({ label: 'Trường B' });
  await expect(page.getByLabel('Kỳ thực tập', { exact: true })).toHaveValue('');
  await expect(page.getByLabel('Phiên bản danh mục học vụ')).toHaveValue('');
  await expect(page.getByLabel('Phiên bản danh mục học vụ')).toBeDisabled();
  await expect(page.getByText('Trường này chưa có kỳ thực tập đang hoạt động.')).toBeVisible();
  await expect(page.getByLabel('Tên nhu cầu', { exact: true })).toHaveValue('Java internship');
});

test('T4 lookup selects canonical skills and version without duplicates across groups', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.fulfill({ json: empty }));
  await page.route('**/api/v1/taxonomy/skills?**', route => route.fulfill({ json: {
    taxonomyVersion: 'skills-v7', skills: { ...empty, totalElements: 1, totalPages: 1,
      content: [{ skillCode: 'JAVA', name: 'Java', status: 'ACTIVE', categoryCode: 'IT', parentSkillCode: null, version: 1 }] }
  } }));
  await open(page); await fill(page);
  await page.getByRole('button', { name: 'Tra cứu kỹ năng', exact: true }).click();
  await page.getByRole('button', { name: 'Chọn JAVA bắt buộc', exact: true }).click();
  await expect(page.getByLabel('Bắt buộc: mã kỹ năng 1', { exact: true })).toHaveValue('JAVA');
  await expect(page.getByLabel('Phiên bản danh mục kỹ năng', { exact: true })).toHaveValue('skills-v7');
  await expect(page.getByRole('button', { name: 'Chọn JAVA ưu tiên', exact: true })).toBeDisabled();
  await page.getByRole('button', { name: 'Xóa kỹ năng bắt buộc 1', exact: true }).click();
  await page.getByRole('button', { name: 'Chọn JAVA ưu tiên', exact: true }).click();
  await expect(page.getByLabel('Ưu tiên: mã kỹ năng 1', { exact: true })).toHaveValue('JAVA');
  await expect(page.getByLabel('Tên nhu cầu', { exact: true })).toHaveValue('Java internship');
});

test('T4 lookup failure preserves draft and can be retried', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.fulfill({ json: empty }));
  let fail = true;
  await page.route('**/api/v1/taxonomy/skills?**', route => fail
    ? route.fulfill({ status: 503, json: { errorCode: 'DEPENDENCY_UNAVAILABLE' } })
    : route.fulfill({ json: { taxonomyVersion: 'skills-v2', skills: empty } }));
  await open(page); await fill(page, false);
  await page.getByRole('button', { name: 'Tra cứu kỹ năng', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Thử tải kỹ năng lại' })).toBeVisible();
  await expect(page.getByLabel('Tên nhu cầu', { exact: true })).toHaveValue('Java internship');
  await expect(page.getByLabel('Phiên bản danh mục kỹ năng', { exact: true })).toHaveValue('');
  fail = false;
  await page.getByRole('button', { name: 'Thử tải kỹ năng lại' }).click();
  await expect(page.getByText('Danh mục kỹ năng chưa có dữ liệu. Cần bổ sung kỹ năng trước khi chọn.')).toBeVisible();
  await page.getByRole('button', { name: 'Dùng phiên bản kỹ năng này' }).click();
  await expect(page.getByLabel('Phiên bản danh mục kỹ năng', { exact: true })).toHaveValue('skills-v2');
});

test('expired session removes editor without persisting draft in browser storage', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.request().method() === 'GET'
    ? route.fulfill({ json: empty }) : route.fulfill({ status: 401, json: { errorCode: 'SESSION_EXPIRED' } }));
  await open(page); await fill(page);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đăng nhập', exact: true })).toBeVisible();
  await expect(page.getByLabel('Tên nhu cầu')).toHaveCount(0);
  expect(await page.evaluate(() => [localStorage.length, sessionStorage.length])).toEqual([0, 0]);
});

test('confirmed discard closes editor and a new form starts empty', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.fulfill({ json: empty }));
  await open(page); await fill(page);
  page.once('dialog', dialog => dialog.accept());
  await page.getByRole('button', { name: 'Đóng bản nhập', exact: true }).click();
  await expect(page.getByLabel('Tên nhu cầu')).toHaveCount(0);
  await page.getByRole('button', { name: 'Tạo nhu cầu', exact: true }).click();
  await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('');
});

for (const code of ['CSRF_INVALID', 'ACCESS_DENIED']) {
  test(`structured ${code} preserves form and displays request ID from body`, async ({ page }) => {
    await session(page);
    await page.route('**/api/v1/enterprise/demands**', route => route.request().method() === 'GET'
      ? route.fulfill({ json: empty }) : route.fulfill({ status: 403, json: { errorCode: code, requestId: 'support-test-123' } }));
    await open(page); await fill(page);
    await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
    await expect(page.getByText(code === 'CSRF_INVALID' ? /Mã bảo vệ đã hết hạn/ : /Bạn không có quyền thực hiện thao tác này/)).toBeVisible();
    await expect(page.getByText(/Mã hỗ trợ: support-test-123/)).toBeVisible();
    await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('Java internship');
    await expect(page.getByLabel('Tên nhu cầu')).toBeEnabled();
  });
}

test('organization cannot switch during save and successful switch clears previous scope', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [org, school].map(organizationId =>
    ({ role: 'ENTERPRISE', organizationId, permissions: ['cooperation:enterprise:create'] })) }));
  let release!: () => void;
  const pending = new Promise<void>(resolve => { release = resolve; });
  let saving = false;
  let saved: any = null;
  await page.route('**/api/v1/enterprise/demands**', async route => {
    const scope = route.request().headers()['x-organization-id'];
    if (route.request().method() === 'GET') return route.fulfill({ json: scope === org && saved
      ? { ...empty, content: [saved], totalElements: 1, totalPages: 1 } : empty });
    saving = true;
    const data = route.request().postDataJSON();
    await pending;
    saved = { demandId: term, enterpriseOrgId: org, state: 'DRAFT', version: 1, criteriaVersion: 1,
      validationState: 'VALID', data, cooperationId: null, softDeletedAt: null };
    await route.fulfill({ status: 201, json: saved });
  });
  await open(page); await fill(page);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect.poll(() => saving).toBe(true);
  try {
    await page.getByLabel('Doanh nghiệp đang làm việc').selectOption(school);
    await expect(page.getByLabel('Doanh nghiệp đang làm việc')).toHaveValue(org);
  } finally { release(); }
  await expect(page.getByRole('heading', { name: 'Java internship', exact: true })).toBeVisible();
  await page.getByLabel('Doanh nghiệp đang làm việc').selectOption(school);
  await expect(page.getByText('Chưa có nhu cầu', { exact: true })).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Java internship', exact: true })).toHaveCount(0);
});

test('verified enterprise sees workspace; missing effective grant hides it', async ({ page }) => {
  await session(page, false); await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Nhu cầu thực tập', exact: true })).toHaveCount(0);
});

test('contract rejection displays explanation and allows preparing form', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.fulfill({ status: 503, json: { errorCode: 'CONTRACT_PENDING_SIGNOFF' } }));
  await open(page);
  await expect(page.getByText(/Chức năng nhu cầu chưa được bật trong môi trường này/)).toBeVisible();
  await fill(page); await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('Java internship');
});

test('deployment disabled is a known rejection, so form stays editable', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.fulfill({ status: 503, json: { errorCode: 'FEATURE_DISABLED' } }));
  await open(page); await fill(page);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByLabel('Tên nhu cầu')).toBeEnabled();
  await expect(page.getByRole('button', { name: 'Thử lưu lại', exact: true })).toHaveCount(0);
  await expect(page.getByText(/Chức năng nhu cầu chưa được bật trong môi trường này/).first()).toBeVisible();
});

test('create then edit preserves scope and sends expected version', async ({ page }) => {
  await session(page);
  let saved: any = null;
  await page.route('**/api/v1/enterprise/demands**', async route => {
    const request = route.request();
    expect(request.headers()['x-organization-id']).toBe(org);
    if (request.method() === 'GET') return route.fulfill({ json: saved ? { ...empty, content: [saved], totalElements: 1, totalPages: 1 } : empty });
    expect(request.headers()['idempotency-key']).toMatch(/^[0-9a-f-]{36}$/);
    expect(request.headers()['x-csrf-token']).toBe('test-csrf');
    const body = request.postDataJSON();
    if (request.method() === 'PATCH') expect(body.expectedVersion).toBe(1);
    const data = request.method() === 'PATCH' ? body.data : body;
    expect(data.schoolOrgId).toBe(school); expect(data.termId).toBe(term);
    expect(data).not.toHaveProperty('enterpriseOrgId');
    saved = { demandId: '44444444-4444-4444-8444-444444444444', enterpriseOrgId: org, state: 'DRAFT', version: saved ? 2 : 1,
      criteriaVersion: 1, validationState: 'VALID', data, cooperationId: null, softDeletedAt: null };
    return route.fulfill({ status: request.method() === 'POST' ? 201 : 200, json: saved });
  });
  await open(page); await fill(page);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Java internship', exact: true })).toBeVisible();
  await page.getByRole('button', { name: /Sửa/ }).click();
  await expect(page.getByLabel('Trường nhận nhu cầu', { exact: true })).toBeDisabled();
  await page.getByLabel('Tên nhu cầu').fill('Updated internship');
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Updated internship', exact: true })).toBeVisible();
});

test('uncertain save retries the same key and payload', async ({ page }) => {
  await session(page); const attempts: { key: string; body: string | null }[] = [];
  await page.route('**/api/v1/enterprise/demands**', route => {
    if (route.request().method() === 'GET') return route.fulfill({ json: empty });
    attempts.push({ key: route.request().headers()['idempotency-key'], body: route.request().postData() });
    return route.fulfill({ status: 500, json: { errorCode: 'AUDIT_WRITE_FAILED' } });
  });
  await open(page); await fill(page);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByLabel('Tên nhu cầu')).toBeDisabled();
  await page.getByRole('button', { name: 'Thử lưu lại', exact: true }).click();
  await expect.poll(() => attempts.length).toBe(2);
  expect(attempts[1]).toEqual(attempts[0]);
  await page.getByRole('button', { name: 'Tài khoản của tôi', exact: true }).click();
  await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('Java internship');
  await expect(page.getByText('Đang lưu hoặc chưa xác nhận được kết quả. Hãy hoàn tất lần lưu trước khi rời bản nhập.')).toBeVisible();
});

test('validation failure preserves entered data and shows field errors', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.request().method() === 'GET'
    ? route.fulfill({ json: empty })
    : route.fulfill({ status: 422, json: { errorCode: 'INVALID_CRITERIA', fieldErrors: [{ field: 'quantity', code: 'INVALID_VALUE', message: 'Test field validation' }] } }));
  await open(page); await fill(page);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByText('quantity: Test field validation', { exact: true })).toBeVisible();
  await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('Java internship');
  await expect(page.getByLabel('Tên nhu cầu')).toBeEnabled();
  await expect(page.getByLabel('Số lượng vị trí')).toHaveAttribute('aria-invalid', 'true');
  await expect(page.getByLabel('Số lượng vị trí')).toBeFocused();
});

test('unsaved editor can cancel close, navigation and organization switch', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [org, school].map(organizationId =>
    ({ role: 'ENTERPRISE', organizationId, permissions: ['cooperation:enterprise:create'] })) }));
  await page.route('**/api/v1/enterprise/demands**', route => route.fulfill({ json: empty }));
  await open(page); await fill(page);
  page.on('dialog', dialog => dialog.dismiss());
  await page.getByRole('button', { name: 'Đóng bản nhập', exact: true }).click();
  await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('Java internship');
  await page.getByRole('button', { name: 'Tài khoản của tôi', exact: true }).click();
  await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('Java internship');
  await page.getByLabel('Doanh nghiệp đang làm việc').selectOption(school);
  await expect(page.getByLabel('Doanh nghiệp đang làm việc')).toHaveValue(org);
  await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('Java internship');
});

test('school workspace loads API-backed panels and has no approval action for an empty queue', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/school/verifications?**', route => route.fulfill({ json: empty }));
  await page.route('**/api/v1/school/cooperations?**', route => route.fulfill({ json: empty }));
  await page.route('**/api/v1/terms?**', route => route.fulfill({ json: empty }));
  await page.route('**/api/v1/auth/me/permissions', route => route.fulfill({ json: [{ role: 'SCHOOL_STAFF', organizationId: school, permissions: [] }] }));
  await page.goto('/');
  await expect(page.getByRole('heading', { name: 'Tổng quan Nhà trường', exact: true })).toBeVisible();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Xác minh doanh nghiệp', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Xác minh doanh nghiệp', exact: true })).toBeVisible();
  await expect(page.getByText('Chưa có hồ sơ xác minh trong phạm vi này.', { exact: true })).toBeVisible();
  await page.getByRole('navigation', { name: 'Điều hướng chính' }).getByRole('button', { name: 'Duyệt hợp tác', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Đề nghị hợp tác', exact: true })).toBeVisible();
  await expect(page.getByRole('button', { name: 'Lưu kết quả xét duyệt', exact: true })).toHaveCount(0);
});

test('version conflict explains reload without discarding input', async ({ page }) => {
  await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.request().method() === 'GET'
    ? route.fulfill({ json: empty })
    : route.fulfill({ status: 409, json: { errorCode: 'VERSION_CONFLICT' } }));
  await open(page); await fill(page);
  await page.getByRole('button', { name: 'Lưu bản nháp', exact: true }).click();
  await expect(page.getByText(/Nhu cầu đã thay đổi/)).toBeVisible();
  await expect(page.getByLabel('Tên nhu cầu')).toHaveValue('Java internship');
});

test('mobile demand form fits viewport', async ({ page }) => {
  await page.setViewportSize({ width: 390, height: 844 }); await session(page);
  await page.route('**/api/v1/enterprise/demands**', route => route.fulfill({ json: empty }));
  await page.goto('/');
  // Navigate with a desktop-width sidebar, then verify the actual mobile editor layout.
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.getByRole('button', { name: 'Nhu cầu thực tập', exact: true }).click();
  await page.setViewportSize({ width: 390, height: 844 }); await fill(page);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});
