import { defineConfig } from '@playwright/test';
export default defineConfig({
  testDir: './tests', fullyParallel: false, workers: 1, retries: 0, timeout: 45000,
  reporter: [['list'], ['junit', { outputFile: process.env.E2E_REPORT_FILE || 'test-results/e2e.xml' }]],
  use: { baseURL: 'http://127.0.0.1:5178', headless: true, trace: 'off', screenshot: 'only-on-failure' },
  webServer: { command: 'npm run dev -- --port 5178 --strictPort', url: 'http://127.0.0.1:5178', reuseExistingServer: false, timeout: 30000 }
});
