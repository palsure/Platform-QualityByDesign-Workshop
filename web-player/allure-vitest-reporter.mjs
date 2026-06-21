/**
 * allure-vitest-reporter.mjs
 *
 * Bridges the Vitest 1.x reporter lifecycle (onFinished) to the
 * allure-vitest 3.x reporter interface (onTestRunEnd).
 *
 * In Vitest 1.x, `onFinished(files)` receives File[] (suite tasks).
 * In Vitest 2.x, `onTestRunEnd(tests)` receives TestCase[] where each
 * has a `.task` property pointing at the underlying task.
 *
 * The allure reporter traverses task.task → handleTask(), so we wrap
 * each File as { task: file } to satisfy the expected shape.
 *
 * Results directory: ALLURE_RESULTS_DIR env var (default: allure-results).
 */

import AllureVitestReporter from 'allure-vitest/reporter';

const reporter = new AllureVitestReporter({
  resultsDir: process.env.ALLURE_RESULTS_DIR ?? 'allure-results',
});

export default class AllureReporterBridge {
  onInit(ctx) {
    reporter.onInit(ctx);
  }

  /** Vitest 1.x end-of-run hook — translate File[] to the allure shape. */
  onFinished(files = []) {
    // Each Vitest File is itself a Suite task; wrap as { task: File }
    // so AllureVitestReporter.onTestRunEnd's inner loop can call
    // handleTask(test.task) which then recurses into file.tasks.
    reporter.onTestRunEnd(files.map(f => ({ task: f })));
  }
}
