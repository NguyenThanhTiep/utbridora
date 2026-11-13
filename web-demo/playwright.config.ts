import { defineConfig } from '@playwright/test';
const port = process.env.E2E_PORT || '5178';
export default defineConfig({
  testDir: './tests', fullyParallel: false, workers: 1, retries: 0, timeout: 45000, expect: { timeout: 15000 },
  // Each backend harness retains its failures/screenshots when the following harness starts.
  outputDir: 'test-results/' + (process.env.E2E_REPORT_FILE || 'default').split(/[\\/]/).pop()!.replace(/\.xml$/, ''),
  reporter: [['list'], ['junit', { outputFile: process.env.E2E_REPORT_FILE || 'test-results/e2e.xml' }]],
  use: { baseURL: `http://127.0.0.1:${port}`, headless: true, trace: 'off', screenshot: 'only-on-failure' },
  webServer: { command: `npm run dev -- --port ${port} --strictPort`, url: `http://127.0.0.1:${port}`, reuseExistingServer: false, timeout: 30000 }
});
