import { defineConfig } from '@playwright/test';
import path from 'node:path';
const evidence = process.env.ADVISORY_EVIDENCE_DIRECTORY || 'test-results/advisory';
export default defineConfig({ testDir: './tests', testMatch: 'advisory.ui.spec.ts', workers: 1, reporter: [['list'], ['junit', { outputFile: path.join(evidence, 'advisory-browser.xml') }]],
  use: { baseURL: 'http://127.0.0.1:15174', viewport: { width: 1200, height: 900 } },
  webServer: process.env.ADVISORY_EXTERNAL_PREVIEW === '1' ? undefined : { command: 'node node_modules/vite/bin/vite.js --host 127.0.0.1 --port 15174 --strictPort', url: 'http://127.0.0.1:15174/tests/fixtures/advisory.html', reuseExistingServer: false, timeout: 30000 },
});
