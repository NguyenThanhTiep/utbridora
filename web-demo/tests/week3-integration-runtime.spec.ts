import { expect, test, type Page } from '@playwright/test';

test('school publication, student preference and matching use real T5 and T2 providers', async ({ browser }) => {
  test.skip(process.env.E2E_WEEK3 !== 'true', 'Run from EnterpriseProposalIT on its private database');
  test.setTimeout(210000);
  const coop = process.env.E2E_COOPERATION!, term = '00000000-0000-4000-8000-000000000021';
  const schoolContext = await browser.newContext(), studentContext = await browser.newContext();
  const school = await schoolContext.newPage(), student = await studentContext.newPage();
  school.setDefaultTimeout(15000); student.setDefaultTimeout(15000);
  async function login(page: Page, email: string) {
    await page.goto('/'); await page.getByLabel('Email', { exact: true }).fill(email);
    await page.getByLabel('Mật khẩu', { exact: true }).fill(process.env.E2E_PASSWORD!);
    await page.getByRole('button', { name: 'Đăng nhập', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Tài khoản của tôi', exact: true })).toBeVisible();
  }
  function localTime(date: Date) { return new Date(date.getTime() - date.getTimezoneOffset() * 60000).toISOString().slice(0, 16); }
  let original: { profileVersion: number; profile: Record<string, unknown> } | null = null;
  async function saveSkills(skills: unknown[]) {
    const current = await (await student.request.get(`/api/v1/students/me/profile?termId=${term}`)).json();
    const csrf = await (await student.request.get('/api/v1/auth/csrf')).json();
    const response = await student.request.put('/api/v1/students/me/profile', {
      headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': crypto.randomUUID() },
      data: { ...current.profile, expectedProfileVersion: current.profileVersion, skills },
    });
    expect(response.ok(), await response.text()).toBeTruthy();
  }
  try {
    await login(school, 'school@example.test'); await login(student, 'student@example.test');
    await school.getByRole('button', { name: 'Hợp tác doanh nghiệp', exact: true }).click();
    await school.getByLabel('Nhà trường', { exact: true }).selectOption('00000000-0000-4000-8000-000000000011');
    const cooperationPanel = school.locator('section.panel').filter({ has: school.getByRole('heading', { name: 'Đề nghị hợp tác', exact: true }) });
    let positionInList = -1;
    for (let page = 0; page < 100; page++) {
      const rows = await (await school.request.get(`/api/v1/school/cooperations?page=${page}&size=25`, {
        headers: { 'X-Organization-Id': '00000000-0000-4000-8000-000000000011' },
      })).json();
      const index = rows.content.findIndex((c: { cooperationId: string }) => c.cooperationId === coop);
      if (index >= 0) { positionInList = page * 25 + index; break; }
      expect(page + 1).toBeLessThan(rows.totalPages);
      const loaded = school.waitForResponse(r => r.url().includes('/school/cooperations?') && new URL(r.url()).searchParams.get('page') === String(page + 1));
      await cooperationPanel.getByRole('button', { name: 'Sau', exact: true }).click(); await loaded;
    }
    expect(positionInList).toBeGreaterThanOrEqual(0);
    await cooperationPanel.getByRole('button', { name: `Xem nội dung đề nghị ${positionInList + 1}`, exact: true }).click();
    await school.getByLabel('Thời điểm công bố', { exact: true }).fill(localTime(new Date(Date.now() - 3600000)));
    await school.getByLabel('Thời điểm đóng', { exact: true }).fill(localTime(new Date(Date.now() + 86400000)));
    await school.getByRole('button', { name: 'Lưu thời gian công bố', exact: true }).click();
    await expect(school.getByText('Đã lưu thời gian công bố cơ hội.', { exact: true })).toBeVisible();
    const opportunity = await (await student.request.get(`/api/v1/opportunities/${coop}`)).json();
    expect(opportunity.opportunityId).toBe(coop);
    await student.getByRole('button', { name: 'Cơ hội thực tập', exact: true }).click();
    const card = student.locator('li.student-list-item').filter({ has: student.getByRole('heading', { name: opportunity.title, exact: true }) });
    // Scope the card by the real opportunity order when several synthetic demands have the same title.
    const all = await (await student.request.get(`/api/v1/opportunities?termId=${term}&page=0&size=25`)).json();
    const position = all.content.findIndex((c: { opportunityId: string }) => c.opportunityId === coop);
    expect(position).toBeGreaterThanOrEqual(0);
    await expect(card.first()).toBeVisible();
    await student.locator('li.student-list-item').nth(position).getByRole('button', { name: 'Xem chi tiết', exact: true }).click();
    await student.getByLabel('Lời nhắn cho nhà trường (không bắt buộc)', { exact: true }).fill('Real provider week 3 browser');
    const submitted = student.waitForResponse(r => r.url().endsWith('/students/me/preferences') && r.request().method() === 'POST');
    await student.getByRole('button', { name: 'Gửi nguyện vọng', exact: true }).click();
    expect((await submitted).status()).toBe(201);
    await student.getByRole('button', { name: 'Lời mời & nguyện vọng', exact: true }).click();
    await student.getByRole('tab', { name: 'Nguyện vọng', exact: true }).click();
    await expect(student.getByText('Lời nhắn: Real provider week 3 browser', { exact: true })).toBeVisible();
    await student.reload();
    await student.getByRole('button', { name: 'Lời mời & nguyện vọng', exact: true }).click();
    await student.getByRole('tab', { name: 'Nguyện vọng', exact: true }).click();
    const preference = student.locator('li.student-list-item').filter({ hasText: 'Real provider week 3 browser' });
    student.once('dialog', dialog => void dialog.accept());
    await preference.getByRole('button', { name: 'Rút nguyện vọng', exact: true }).click();
    await expect(preference.getByText('Đã rút', { exact: true })).toBeVisible();
    const matching = school.locator('.matching-workspace');
    await matching.getByLabel('Nhu cầu đã duyệt', { exact: true }).selectOption(coop);
    const evaluated = school.waitForResponse(r => r.url().endsWith('/matching/evaluate') && r.request().method() === 'POST');
    await matching.getByRole('button', { name: 'Chạy Matching', exact: true }).click();
    const firstResponse = await evaluated; expect(firstResponse.ok()).toBeTruthy(); const first = await firstResponse.json();
    await expect(matching.getByRole('button', { name: new RegExp(`^Đáp ứng\\s+${first.summary.matched}$`) })).toBeVisible();
    original = await (await student.request.get(`/api/v1/students/me/profile?termId=${term}`)).json();
    await saveSkills([]);
    const rerun = school.waitForResponse(r => r.url().endsWith('/matching/evaluate') && r.request().method() === 'POST');
    await matching.getByRole('button', { name: 'Chạy lại Matching', exact: true }).click();
    const nextResponse = await rerun; expect(nextResponse.ok()).toBeTruthy(); const next = await nextResponse.json();
    expect(next.summary.matched).toBe(first.summary.matched - 1);
    await expect(matching.getByRole('button', { name: new RegExp(`^Đáp ứng\\s+${next.summary.matched}$`) })).toBeVisible();
    await matching.getByRole('button', { name: new RegExp(`^Không đáp ứng\\s+${next.summary.notMatched}$`) }).click();
    await expect(matching.locator('.matching-candidate')).not.toHaveCount(0);
    await school.screenshot({ path: '../target/week3-matching-school.png', fullPage: true });
    await student.setViewportSize({ width: 390, height: 844 });
    await student.screenshot({ path: '../target/week3-preferences-mobile.png', fullPage: true });
    expect(await student.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBeTruthy();
  } finally {
    if (original) {
      const skills = original.profile.skills as { skillCode: string; level: number; yearsPractice: number; note: string | null }[];
      await saveSkills(skills.map(({ skillCode, level, yearsPractice, note }) => ({ skillCode, level, yearsPractice, note })));
    }
    await Promise.all([schoolContext.close(), studentContext.close()]);
  }
});
