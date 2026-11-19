import { expect, test, type Page } from '@playwright/test';

// Real MySQL, quarantine scanner, owner APIs and Chromium. No route interception.
test.skip(!process.env.T3OPS_E2E, 'Requires the isolated T3 operations backend harness');
test.use({ actionTimeout: 20000 });
const env = (name: string) => process.env['T3OPS_' + name]!;
async function login(page: Page, email: string) {
  await page.goto('/');await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(env('PASSWORD'));
  await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
  await expect(page.getByRole('button', { name: 'Quản lý thực tập', exact: true })).toBeVisible();
  console.log('T3 runtime: authenticated role navigation available');
}
async function openCase(page: Page) {
  await page.getByLabel('Mã Case được cấp', { exact: true }).fill(env('CASE'));
  await page.getByRole('button', { name: 'Mở hồ sơ vận hành', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Quan tâm tuyển dụng', exact: true })).toBeVisible();
}
test('HR imports 40 placements into four groups; school coordinates, evaluates quality and closes the case', async ({ page, browser }) => {
  test.setTimeout(180000);await login(page, env('HR_EMAIL'));
  await page.getByRole('button', { name: 'Không gian thực tập', exact: true }).click();
  await page.getByLabel('Phân công đang làm việc', { exact: true }).selectOption(env('ASSIGNMENT'));
  await page.getByText('Tạo đợt thực tập', { exact: true }).click();
  await page.getByLabel('Mã đợt', { exact: true }).fill('SYNTHETIC-T3-UI');
  await page.getByLabel('Tên đợt', { exact: true }).fill('Synthetic T3 runtime UI');
  await page.getByRole('button', { name: 'Tạo đợt trong kỳ đã chọn', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Tiếp nhận Placement từ danh sách', exact: true })).toBeVisible();
  console.log('T3 runtime: cohort created');
  const csv = 'placementId\n' + env('PLACEMENTS').split(',').join('\n') + '\ninvalid-id\n';
  await page.getByLabel('Tệp danh sách Placement', { exact: true }).setInputFiles({ name: 'synthetic-placements.csv', mimeType: 'text/csv', buffer: Buffer.from(csv) });
  await page.getByRole('button', { name: 'Xem trước danh sách tiếp nhận', exact: true }).click();
  await expect(page.getByText('40 dòng hợp lệ / 41 dòng.', { exact: false })).toBeVisible({ timeout: 45000 });
  await page.getByLabel('Chọn tất cả dòng hợp lệ', { exact: true }).check();
  await page.setViewportSize({ width: 390, height: 844 });await page.getByRole('button', { name: 'Trang sau', exact: true }).click();
  await expect(page.getByLabel('Chọn dòng 42', { exact: true })).toBeDisabled();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth + 1)).toBeTruthy();
  await page.setViewportSize({ width: 1280, height: 900 });
  await page.getByRole('button', { name: 'Xác nhận tiếp nhận 40 Placement', exact: true }).click();
  await expect(page.getByText('Đã thêm 40; đã có 0; không hợp lệ 0.', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Tải phiên bản đợt mới', exact: true }).click();
  await page.getByLabel('Số sinh viên mỗi nhóm', { exact: true }).fill('10');
  await page.getByRole('button', { name: 'Xem trước chia nhóm', exact: true }).click();
  await expect(page.getByText('Nhóm 4: 10 hồ sơ', { exact: true })).toBeVisible();
  await page.getByRole('button', { name: 'Xác nhận tạo 4 nhóm', exact: true }).click();
  await expect(page.getByText('4 nhóm trong phạm vi đã chọn', { exact: true })).toBeVisible();
  console.log('T3 runtime: scanned intake and grouping committed');
  await page.getByRole('button', { name: 'Quản lý thực tập', exact: true }).click();
  await page.getByRole('combobox', { name: 'Phân công quản lý thực tập', exact: true }).selectOption(env('ASSIGNMENT'));await openCase(page);
  await page.getByLabel('Doanh nghiệp quan tâm tuyển dụng', { exact: true }).check();
  await page.getByLabel('Ghi chú quan tâm tuyển dụng', { exact: true }).fill('Synthetic retained hiring interest');
  await page.getByRole('button', { name: 'Lưu quan tâm tuyển dụng', exact: true }).click();
  await expect(page.getByText('Đã lưu ý định tuyển dụng.', { exact: true })).toBeVisible();
  console.log('T3 runtime: hiring interest saved');
  const schoolContext = await browser.newContext({ baseURL: 'http://127.0.0.1:' + (process.env.E2E_PORT || '5178') });
  const school = await schoolContext.newPage();
  try {
    await login(school, env('STAFF_EMAIL'));await school.getByRole('button', { name: 'Quản lý thực tập', exact: true }).click();
    await school.getByRole('combobox', { name: 'Kỳ quản lý thực tập', exact: true }).selectOption(env('TERM'));
    const kpi = school.locator('.t3-kpis > div').filter({ has: school.getByText('Hồ sơ thực tập', { exact: true }) });
    await expect(kpi.locator('dd')).toHaveText('40');
    await school.getByLabel('Mã doanh nghiệp được đánh giá', { exact: true }).fill(env('ENTERPRISE'));
    await school.getByRole('combobox', { name: 'Rubric chất lượng', exact: true }).selectOption(env('TEMPLATE') + ':1');
    await school.getByRole('combobox', { name: 'Synthetic mentoring support', exact: true }).selectOption('5');
    await school.getByRole('button', { name: 'Lưu đánh giá doanh nghiệp', exact: true }).click();
    await expect(school.getByText('Đã lưu đánh giá doanh nghiệp, mã', { exact: false })).toBeVisible();
    await openCase(school);await expect(school.getByText('Synthetic retained hiring interest', { exact: true })).toBeVisible();
    await school.getByLabel('Nội dung vấn đề', { exact: true }).fill('Synthetic coordination resolution');
    await school.getByLabel('Mã người phụ trách', { exact: true }).fill(env('HR_ID'));
    await school.getByRole('button', { name: 'Tạo yêu cầu phối hợp', exact: true }).click();
    await school.getByRole('button', { name: 'Bắt đầu xử lý', exact: true }).click();
    await school.getByRole('button', { name: 'Đánh dấu đã giải quyết', exact: true }).click();
    await expect(school.getByRole('heading', { name: 'COMMUNICATION · RESOLVED', exact: true })).toBeVisible();
    await school.getByRole('combobox', { name: 'Hình thức kết thúc', exact: true }).selectOption('EARLY_TERMINATION');
    await school.getByLabel('Căn cứ quyết định đóng Case', { exact: true }).fill('SYNTHETIC_UI_CLOSURE');
    await school.getByLabel('Mã lý do đóng Case', { exact: true }).fill('EARLY_TERMINATION');
    await school.getByRole('button', { name: 'Xác nhận đóng Case', exact: true }).click();
    await expect(school.getByText('Trạng thái: TERMINATED', { exact: false })).toBeVisible();
    console.log('T3 runtime: school quality, coordination and closure committed');
    await page.bringToFront();await page.getByRole('button', { name: 'Tải lại hồ sơ vận hành', exact: true }).click();
    await expect(page.getByText('Case đã đóng; nội dung này chỉ được xem.', { exact: true })).toBeVisible();
    await expect(page.getByText('Synthetic retained hiring interest', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Lưu quan tâm tuyển dụng', exact: true })).toHaveCount(0);
  } finally {await schoolContext.close();}
});
