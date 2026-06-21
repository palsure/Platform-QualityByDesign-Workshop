/**
 * playwright.config.ts
 *
 * Supports three run modes via environment variables:
 *
 *   LOCAL (default)
 *     npx playwright test
 *     Spins up the Vite preview server and runs against Desktop Chrome.
 *
 *   LAB — BrowserStack Automate (CDP proxy)
 *     PLAYWRIGHT_RUN_MODE=lab \
 *     PLAYWRIGHT_LAB_PROVIDER=browserstack \
 *     BROWSERSTACK_USERNAME=... \
 *     BROWSERSTACK_ACCESS_KEY=... \
 *     PLAYWRIGHT_BASE_URL=https://staging.example.com \
 *     npx playwright test
 *
 *     BrowserStack routes Playwright traffic through its CDP proxy.
 *     See: https://www.browserstack.com/docs/automate/playwright
 *
 *   LAB — Selenium Grid / Playwright Grid
 *     PLAYWRIGHT_RUN_MODE=lab \
 *     PLAYWRIGHT_LAB_PROVIDER=local_grid \
 *     PLAYWRIGHT_BASE_URL=http://your-app:3000 \
 *     GRID_URL=ws://selenium-grid:4444 \
 *     npx playwright test
 *
 * Selecting a project subset:
 *   PLAYWRIGHT_PROJECT=chromium npx playwright test        # local Chrome only
 *   PLAYWRIGHT_PROJECT=chromium-throttle npx playwright test
 *   PLAYWRIGHT_PROJECT=bs-chrome-win11 npx playwright test # BrowserStack preset
 *   PLAYWRIGHT_PROJECT=bs-iphone15 npx playwright test     # BrowserStack iOS Safari
 *   PLAYWRIGHT_PROJECT=bs-galaxy-s23 npx playwright test   # BrowserStack Android Chrome
 *
 * Relevant env vars:
 *   PLAYWRIGHT_RUN_MODE         local (default) | lab
 *   PLAYWRIGHT_LAB_PROVIDER     browserstack | local_grid
 *   PLAYWRIGHT_BASE_URL         override the target app URL (skips webServer)
 *   BROWSERSTACK_USERNAME       BrowserStack credentials
 *   BROWSERSTACK_ACCESS_KEY
 *   BROWSERSTACK_BUILD_NAME     build label in BS dashboard (defaults to GITHUB_RUN_NUMBER)
 *   GRID_URL                    WebSocket URL for Playwright Grid / BrowserStack proxy
 *   CI                          set by GitHub Actions; changes retries, workers, reporters
 */

import { defineConfig, devices } from '@playwright/test';

// ── Resolve run context ───────────────────────────────────────────────────────

const RUN_MODE     = (process.env.PLAYWRIGHT_RUN_MODE     ?? 'local').toLowerCase();
const LAB_PROVIDER = (process.env.PLAYWRIGHT_LAB_PROVIDER ?? 'browserstack').toLowerCase();
const IS_LAB       = RUN_MODE === 'lab';

const BASE_URL           = process.env.PLAYWRIGHT_BASE_URL || 'http://127.0.0.1:4173';
// In CI, never launch a webServer — the runner must provide the target URL (Firebase or local serve).
// Locally, webServer is started automatically when PLAYWRIGHT_BASE_URL is not set.
const USE_EXTERNAL_SERVER = !!process.env.PLAYWRIGHT_BASE_URL || !!process.env.CI;

// ── Test stage — controls allure output folder and worker count ───────────────
// Set PLAYWRIGHT_STAGE=bat | smoke | regression (default: e2e)
const STAGE = (process.env.PLAYWRIGHT_STAGE ?? 'e2e').toLowerCase();

const STAGE_WORKERS: Record<string, number | undefined> = {
  bat:   4,        // fast sanity tests, run fully parallel
  smoke: 4,        // run smoke fully in parallel too — matches BAT for max throughput
  e2e:   undefined,// auto (all tests together)
};

const STAGE_TIMEOUT: Record<string, number> = {
  bat:   90_000,   // 90 s — allows waitForFirstFrame (60 s) + assertions + CI network latency
  smoke: 90_000,   // 90 s — smoke includes stall and seek scenarios
  e2e:   90_000,
};

// In CI, respect stage-specific parallelism; fall back to 1 only when no stage value is set.
const stageWorkers  = IS_LAB ? 1 : (STAGE_WORKERS[STAGE] ?? (process.env.CI ? 1 : undefined));
const stageTimeout  = STAGE_TIMEOUT[STAGE] ?? 90_000;
const allureFolder  = `allure-results/${STAGE}`;

// BrowserStack configuration
const BS_USERNAME   = process.env.BROWSERSTACK_USERNAME   ?? '';
const BS_ACCESS_KEY = process.env.BROWSERSTACK_ACCESS_KEY ?? '';
const BS_BUILD_NAME = process.env.BROWSERSTACK_BUILD_NAME
    ?? process.env.GITHUB_RUN_NUMBER
    ?? 'local';

// BrowserStack CDP proxy endpoint for Playwright
// Format: wss://<username>:<key>@cdp.browserstack.com/playwright
const BS_CDP_URL = `wss://${BS_USERNAME}:${BS_ACCESS_KEY}@cdp.browserstack.com/playwright`;

// Selenium / Playwright Grid WebSocket URL
const GRID_URL = process.env.GRID_URL ?? 'ws://localhost:4444';

/**
 * Resolve the remote browser endpoint for lab runs.
 * Returns undefined for local runs (Playwright manages the browser itself).
 */
function resolveConnectUrl(): string | undefined {
  if (!IS_LAB) return undefined;
  switch (LAB_PROVIDER) {
    case 'browserstack': return BS_CDP_URL;
    case 'local_grid':   return GRID_URL;
    default:             return undefined;
  }
}

/**
 * Build BrowserStack-specific capabilities object that is merged into
 * `use.connectOptions.headers` for BrowserStack Playwright sessions.
 * Returns undefined for non-BS runs.
 */
function bsCaps(extra: Record<string, unknown> = {}): Record<string, string> | undefined {
  if (!IS_LAB || LAB_PROVIDER !== 'browserstack') return undefined;
  const caps = {
    'browser':           'chrome',
    'browser_version':   'latest',
    'os':                'Windows',
    'os_version':        '11',
    'name':              'QoE Web Player E2E',
    'build':             `DevOpsDays-QoE-${BS_BUILD_NAME}`,
    'project':           'DevOpsDays QoE',
    'browserstack.networkLogs':  'true',
    'browserstack.console':      'warnings',
    ...Object.fromEntries(
      Object.entries(extra).map(([k, v]) => [k, String(v)])
    ),
  };
  return { 'x-bstack-playwright': JSON.stringify(caps) };
}

// ── Shared base use options ───────────────────────────────────────────────────

const sharedUse = {
  baseURL:    BASE_URL,
  video:      'on'      as const,
  screenshot: 'on'      as const,
  trace:      'on-first-retry' as const,
};

// ── Config ────────────────────────────────────────────────────────────────────

export default defineConfig({
  testDir: 'e2e',
  fullyParallel: !IS_LAB,
  forbidOnly: !!process.env.CI,
  // BAT keeps 1 retry in CI for resilience; Smoke has 0 retries so Allure
  // counts match Playwright JSON stats (retries create duplicate Allure entries).
  retries: process.env.CI ? (STAGE === 'bat' ? 1 : 0) : 0,
  workers:  stageWorkers,
  timeout:  stageTimeout,

  // ── Reporters ─────────────────────────────────────────────────────────────
  reporter: [
    ['list'],
    ['html', { outputFolder: `playwright-report/${STAGE}`, open: 'never' }],
    ['allure-playwright', {
      detail:     true,
      resultsDir: allureFolder,
      suiteTitle: true,
      environmentInfo: {
        Framework:   'Playwright',
        Language:    'TypeScript',
        App:         'QoE Web Player',
        Stage:       STAGE.toUpperCase(),
        BaseURL:     BASE_URL,
        RunMode:     RUN_MODE,
        LabProvider: IS_LAB ? LAB_PROVIDER : 'local',
      },
    }],
    ...(process.env.CI ? [['json',   { outputFile: `playwright-results-${STAGE}.json` }] as const] : []),
    ...(process.env.CI ? [['github'] as const] : []),
  ],

  // ── Projects ──────────────────────────────────────────────────────────────
  projects: IS_LAB
    ? labProjects()
    : localProjects(),

  // ── Web server (local only) ───────────────────────────────────────────────
  ...(USE_EXTERNAL_SERVER || IS_LAB
    ? {}
    : {
        webServer: {
          command:             'npm run preview -- --host 127.0.0.1 --port 4173',
          url:                 'http://127.0.0.1:4173',
          reuseExistingServer: !process.env.CI,
          timeout:             120_000,
        },
      }),
});

// ── Project definitions ───────────────────────────────────────────────────────

/** Projects for local runs — no remote connection needed. */
function localProjects() {
  return [
    {
      name: 'chromium',
      use: {
        ...sharedUse,
        ...devices['Desktop Chrome'],
      },
    },
    {
      // Network throttle tests run serially to avoid bandwidth contention.
      // Activate with: PLAYWRIGHT_PROJECT=chromium-throttle npx playwright test
      name: 'chromium-throttle',
      use: {
        ...sharedUse,
        ...devices['Desktop Chrome'],
      },
      testMatch: '**/network-throttle.spec.ts',
      fullyParallel: false,
    },
    {
      // Firefox local smoke — activate with PLAYWRIGHT_PROJECT=firefox
      name: 'firefox',
      use: {
        ...sharedUse,
        ...devices['Desktop Firefox'],
      },
    },
    {
      // Mobile viewport emulation (local Chrome) for quick mobile-layout checks
      name: 'mobile-chrome',
      use: {
        ...sharedUse,
        ...devices['Pixel 5'],
      },
    },
  ];
}

/**
 * Projects for lab runs.
 *
 * BrowserStack projects use `connectOptions` to route through the CDP proxy.
 * Each project sets a different `x-bstack-playwright` header so BrowserStack
 * knows which OS/browser/device to provision.
 *
 * For local_grid, we connect to the Playwright Grid WebSocket URL and use
 * standard device descriptors — the grid handles browser provisioning.
 */
function labProjects() {
  const connectUrl = resolveConnectUrl();

  if (LAB_PROVIDER === 'browserstack') {
    return [
      // ── Desktop browsers ────────────────────────────────────────────────────
      {
        name: 'bs-chrome-win11',
        use: {
          ...sharedUse,
          ...devices['Desktop Chrome'],
          connectOptions: {
            wsEndpoint: connectUrl!,
            headers:    bsCaps({ os: 'Windows', os_version: '11', browser: 'chrome' }),
          },
        },
      },
      {
        name: 'bs-firefox-win11',
        use: {
          ...sharedUse,
          ...devices['Desktop Firefox'],
          connectOptions: {
            wsEndpoint: connectUrl!,
            headers:    bsCaps({ os: 'Windows', os_version: '11', browser: 'firefox' }),
          },
        },
        // Skip throttle tests — Firefox CDP support is limited in BrowserStack
        testIgnore: '**/network-throttle.spec.ts',
      },
      {
        name: 'bs-chrome-macos',
        use: {
          ...sharedUse,
          ...devices['Desktop Chrome'],
          connectOptions: {
            wsEndpoint: connectUrl!,
            headers:    bsCaps({ os: 'OS X', os_version: 'Ventura', browser: 'chrome' }),
          },
        },
      },
      // ── Mobile browsers ─────────────────────────────────────────────────────
      {
        // Safari on real iPhone 15 via BrowserStack
        name: 'bs-iphone15',
        use: {
          ...sharedUse,
          ...devices['iPhone 15'],
          connectOptions: {
            wsEndpoint: connectUrl!,
            headers:    bsCaps({
              'device':          'iPhone 15',
              'os_version':      '17',
              'browser':         'safari',
              'real_mobile':     'true',
            }),
          },
        },
        // Throttle tests are desktop-only (CDP emulation not available on real devices)
        testIgnore: '**/network-throttle.spec.ts',
      },
      {
        // Chrome on real Samsung Galaxy S23 via BrowserStack
        name: 'bs-galaxy-s23',
        use: {
          ...sharedUse,
          ...devices['Galaxy S9+'],
          connectOptions: {
            wsEndpoint: connectUrl!,
            headers:    bsCaps({
              'device':          'Samsung Galaxy S23',
              'os_version':      '13.0',
              'browser':         'chrome',
              'real_mobile':     'true',
            }),
          },
        },
        testIgnore: '**/network-throttle.spec.ts',
      },
    ];
  }

  // ── Local Selenium / Playwright Grid ─────────────────────────────────────
  return [
    {
      name: 'grid-chromium',
      use: {
        ...sharedUse,
        ...devices['Desktop Chrome'],
        connectOptions: { wsEndpoint: connectUrl! },
      },
    },
    {
      name: 'grid-firefox',
      use: {
        ...sharedUse,
        ...devices['Desktop Firefox'],
        connectOptions: { wsEndpoint: connectUrl! },
      },
    },
  ];
}
