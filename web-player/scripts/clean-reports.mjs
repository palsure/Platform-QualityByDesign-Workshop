/**
 * Remove prior Allure / Playwright / Vitest report outputs for a stage.
 * Usage: node scripts/clean-reports.mjs bat|smoke|e2e|unit|all
 */
import { rmSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');

function rm(rel) {
  try {
    rmSync(join(root, rel), { recursive: true, force: true });
  } catch {
    /* missing paths are fine */
  }
}

function cleanPlaywrightStage(stage) {
  for (const rel of [
    `allure-results/${stage}`,
    `playwright-report/${stage}`,
    `allure-report/${stage}`,
    `playwright-results-${stage}.json`,
  ]) {
    rm(rel);
  }
}

function cleanUnit() {
  for (const rel of ['allure-results/unit', 'allure-report/unit', 'test-results']) {
    rm(rel);
  }
}

const stage = (process.argv[2] ?? 'all').toLowerCase();

switch (stage) {
  case 'bat':
    cleanPlaywrightStage('bat');
    break;
  case 'smoke':
    cleanPlaywrightStage('smoke');
    break;
  case 'e2e':
    cleanPlaywrightStage('e2e');
    break;
  case 'unit':
    cleanUnit();
    break;
  case 'all':
    cleanPlaywrightStage('bat');
    cleanPlaywrightStage('smoke');
    cleanPlaywrightStage('e2e');
    cleanUnit();
    break;
  default:
    process.stderr.write(
      'Usage: node scripts/clean-reports.mjs [bat|smoke|e2e|unit|all]\n',
    );
    process.exit(1);
}
