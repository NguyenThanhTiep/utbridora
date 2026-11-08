import { test, expect } from '@playwright/test';

test('T1-004 - Bo trong email', async ({ page }) => {
  await page.goto('/');

  const email = page.getByLabel('Email', { exact: true });
  const password = page.getByLabel('Mật khẩu', { exact: true });

  await email.fill('');
  await password.fill('mat-khau-thu-nghiem');

  let loginRequests = 0;
  page.on('request', request => {
    if (
      request.method() === 'POST' &&
      new URL(request.url()).pathname === '/api/v1/auth/login'
    ) {
      loginRequests++;
    }
  });

  await page.getByRole('button', {
    name: 'Đăng nhập',
    exact: true
  }).click();

  await expect(email).toBeFocused();
  expect(
    await email.evaluate(
      element => (element as HTMLInputElement).validity.valueMissing
    )
  ).toBe(true);

  await expect(page.getByRole('heading', {
    name: 'Đăng nhập',
    exact: true
  })).toBeVisible();

  expect(loginRequests).toBe(0);
});

test('T1-005 - Bo trong mat khau', async ({ page }) => {
  await page.goto('/');

  const email = page.getByLabel('Email', { exact: true });
  const password = page.getByLabel('Mật khẩu', { exact: true });

  await email.fill('qa@example.test');
  await password.fill('');

  let loginRequests = 0;
  page.on('request', request => {
    if (
      request.method() === 'POST' &&
      new URL(request.url()).pathname === '/api/v1/auth/login'
    ) {
      loginRequests++;
    }
  });

  await page.getByRole('button', {
    name: 'Đăng nhập',
    exact: true
  }).click();

  await expect(password).toBeFocused();
  expect(
    await password.evaluate(
      element => (element as HTMLInputElement).validity.valueMissing
    )
  ).toBe(true);

  await expect(page.getByRole('heading', {
    name: 'Đăng nhập',
    exact: true
  })).toBeVisible();

  expect(loginRequests).toBe(0);
});

test('T1-008 - Giu phien sau khi tai lai trang', async ({ page }) => {
  const email = process.env.E2E_ADMIN_EMAIL;
  const password = process.env.E2E_PASSWORD;

  if (!email || !password) {
    throw new Error('Thieu E2E_ADMIN_EMAIL hoac E2E_PASSWORD');
  }

  await page.goto('/');
  await page.getByLabel('Email', { exact: true }).fill(email);
  await page.getByLabel('Mật khẩu', { exact: true }).fill(password);
  await page.getByRole('button', {
    name: 'Đăng nhập',
    exact: true
  }).click();

  const accountHeading = page.getByRole('heading', {
    name: 'Tài khoản của tôi',
    exact: true
  });

  await expect(accountHeading).toBeVisible();

  const before = await page.request.get('/api/v1/auth/me');
  expect(before.status()).toBe(200);
  const originalUser = await before.json();

  await page.reload();
  await expect(accountHeading).toBeVisible();

  const after = await page.request.get('/api/v1/auth/me');
  expect(after.status()).toBe(200);
  const reloadedUser = await after.json();

  expect(reloadedUser.userId).toBe(originalUser.userId);
});