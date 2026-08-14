import { defineConfig, devices } from '@playwright/test';

/**
 * Operate's process-instance header hides the "Called Instances" link below
 * the Carbon xlg breakpoint (~1312px). Use a large desktop viewport so the
 * full header is rendered consistently across browsers.
 */
const desktopViewport = { width: 1920, height: 1080 };

/**
 * E2E test configuration for Cockpit Plugin
 * See https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: './tests',
  /* Run tests in files in parallel */
  fullyParallel: false,
  /* Fail the build on CI if you accidentally left test.only in the source code. */
  forbidOnly: !!process.env.CI,
  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,
  /* Retries that eventually pass are still defects and should fail CI */
  failOnFlakyTests: !!process.env.CI,
  /* Opt out of parallel tests on CI. */
  workers: 1,
  /* Reporter to use. See https://playwright.dev/docs/test-reporters */
  reporter: process.env.CI
    ? [
        ['html'],
        ['github'],
        ['list'],
        ['json', { outputFile: 'test-results/playwright-report.json' }],
      ]
    : 'html',
  /* Shared settings for all the projects below. See https://playwright.dev/docs/api/class-testoptions. */
  use: {
    /* Base URL to use in actions like `await page.goto('/')`. */
    baseURL: 'http://localhost:8090',
    /* Collect traces for every retry attempt to preserve flaky diagnostics */
    trace: 'on-all-retries',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'], viewport: desktopViewport },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'], viewport: desktopViewport },
    },
    {
      name: 'edge',
      use: {
        ...devices['Desktop Edge'],
        channel: 'msedge',
        viewport: desktopViewport,
      },
    },
  ],

  /* Run your local dev server before starting the tests */
  webServer: {
    command: 'bash start-services.sh',
    url: 'http://localhost:8090/camunda',
    reuseExistingServer: !process.env.CI,
    timeout: 360 * 1000, // 6 minutes for Camunda to start and migration to complete
    stdout: 'pipe',
    stderr: 'pipe',
  },

  /* Global setup to ensure migration is complete before tests start */
  globalSetup: require.resolve('./global-setup.ts'),

  /* Global teardown to stop Docker Compose after tests complete */
  globalTeardown: require.resolve('./global-teardown.ts'),
});
