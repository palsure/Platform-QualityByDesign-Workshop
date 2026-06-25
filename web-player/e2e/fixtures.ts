/**
 * Playwright test fixtures for navigation and playback E2E specs.
 *
 * Extends the base test with a lightweight labContext fixture that records
 * run mode / CI build metadata in Allure reports.
 */
import { test as base, expect } from '@playwright/test';
import { allure } from 'allure-playwright';

const RUN_MODE     = (process.env['PLAYWRIGHT_RUN_MODE']     ?? 'local').toLowerCase();
const LAB_PROVIDER = (process.env['PLAYWRIGHT_LAB_PROVIDER'] ?? '').toLowerCase();
const IS_LAB       = RUN_MODE === 'lab';

export type LabContext = {
  runMode:     string;
  labProvider: string;
  isLab:       boolean;
  buildName:   string;
};

type Fixtures = {
  labContext: LabContext;
};

export const test = base.extend<Fixtures>({
  labContext: [async ({}, use, testInfo) => {
    const buildName = process.env['BROWSERSTACK_BUILD_NAME']
        ?? process.env['GITHUB_RUN_NUMBER']
        ?? 'local';

    const ctx: LabContext = {
      runMode:     RUN_MODE,
      labProvider: IS_LAB ? (LAB_PROVIDER || 'unknown') : 'local',
      isLab:       IS_LAB,
      buildName,
    };

    await allure.parameter('run:mode',     ctx.runMode);
    await allure.parameter('run:provider', ctx.labProvider);
    await allure.parameter('run:build',    ctx.buildName);
    await allure.label('run_mode', ctx.runMode);

    if (IS_LAB) {
      testInfo.annotations.push({
        type:        'Lab Run',
        description: `${ctx.labProvider}  build=${ctx.buildName}`,
      });
      await allure.label('lab_provider', ctx.labProvider);
    }

    await use(ctx);
  }, { scope: 'test', auto: true }],
});

export { expect };
