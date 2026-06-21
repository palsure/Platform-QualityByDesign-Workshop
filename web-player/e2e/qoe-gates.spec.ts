import { test, expect }       from './fixtures';
import { allure }              from 'allure-playwright';
import { selectVideoAndPlay }  from './video-helpers';

// ── Shared helpers ────────────────────────────────────────────────────────────

async function snapshot(page: import('@playwright/test').Page) {
  return page.evaluate(() => window.__QOE_DEMO__?.getSnapshot() ?? null);
}

/** Wait until the QoE probe bridge has recorded a first frame.
 *  Uses page.waitForFunction() so intermediate retries are silent in Allure. */
async function waitForFirstFrame(page: import('@playwright/test').Page, timeout = 60_000) {
  await page.waitForFunction(
    () => (window as any).__QOE_DEMO__?.getSnapshot()?.timeToFirstFrameMs != null,
    { timeout },
  );
}

/**
 * Snapshot the request log and assert no critical network issues exist.
 * Called at the end of each test after the scenario has played out.
 *
 * HLS manifest failures are hard-blocked — they directly prevent video playback.
 * QoE API failures are recorded as Allure parameters but do NOT fail the test:
 * these E2E tests run against a static file server (no backend), so 404s on
 * /api/v1/metrics are expected and do not affect visual playback behaviour.
 */
async function assertNoNetworkIssues(
  entries: import('./fixtures').NetworkFixtures['networkCapture']['entries'],
) {
  const failed         = entries.filter(e => e.failed);
  const manifestErrors = failed.filter(e => e.category === 'hls-manifest');
  const apiErrors      = failed.filter(e => e.category === 'qoe-api');
  const apiTotal       = entries.filter(e => e.category === 'qoe-api').length;

  // HLS manifest errors block playback — always assert
  expect.soft(manifestErrors, `HLS manifest errors: ${manifestErrors.map(e => e.url).join(', ')}`).toHaveLength(0);

  // QoE API availability is tested by the API pipeline; record here for visibility only
  if (apiTotal > 0) {
    await allure.parameter(
      'qoe_api_failures',
      `${apiErrors.length}/${apiTotal} (backend not co-deployed — informational only)`,
    );
  }
}

// ── Test suite ────────────────────────────────────────────────────────────────

test.describe('Playback quality gates', () => {

  test.beforeEach(async ({ page }) => {
    const testedUrl = page.url();
    await allure.parameter('tested_url', testedUrl);
    await allure.link(testedUrl, 'Tested App URL');
  });

  test('baseline: first frame within generous budget', { tag: ['@BAT'] }, async ({ page, networkCapture }) => {
    await allure.feature('Time to First Frame');
    await allure.story('Baseline (reference stream)');
    await allure.severity('critical');
    await allure.description(`
**Scenario:** Baseline — no faults injected.

The player loads the HLS manifest immediately and begins buffering segments.
This test asserts that the first decoded video frame is delivered within a
generous 60-second budget (cloud CI networks serving external HLS streams
can be slow; the budget is intentionally wide to distinguish real regressions
from transient network latency).

**Pass condition:** \`timeToFirstFrameMs < 60 000\`
    `.trim());
    await allure.label('layer', 'e2e');
    await allure.label('testType', 'automated');
    await allure.tag('qoe', 'ttff', 'baseline');
    await allure.link('https://www.w3.org/TR/media-source/', 'MSE spec', 'reference');

    await selectVideoAndPlay(page, undefined, 'baseline');

    await allure.step('Wait for first frame', () => waitForFirstFrame(page));

    const snap = await snapshot(page);
    const ttff = snap!.timeToFirstFrameMs!;

    await allure.step('Assert time-to-first-frame < 60 s', async () => {
      await allure.parameter('timeToFirstFrameMs',  String(ttff));
      await allure.parameter('threshold_ms',  String(60_000));
      expect(ttff).toBeLessThan(60_000);
    });

    await allure.step('Assert no critical network issues', async () => {
      await assertNoNetworkIssues(networkCapture.entries);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────

  test('startup_delay: time-to-first-frame reflects injected delay', { tag: ['@Smoke'] }, async ({ page, networkCapture }) => {
    await allure.feature('Startup Latency');
    await allure.story('Startup delay (late manifest attach)');
    await allure.severity('normal');
    await allure.description(`
**Scenario:** Startup delay — HLS manifest attach is intentionally delayed by 2 800 ms.

Validates that the QoE probe correctly measures the injected startup penalty.
47 % of viewers abandon a stream that takes > 3 s to start; early detection
in CI prevents regressions landing in production.

**Pass condition:** \`timeToFirstFrameMs > 2 000\`
    `.trim());
    await allure.label('layer', 'e2e');
    await allure.label('testType', 'automated');
    await allure.tag('qoe', 'ttff', 'startup-delay');

    await selectVideoAndPlay(page, undefined, 'startup_delay');

    await allure.step('Wait for first frame (with injected delay)', () => waitForFirstFrame(page));

    const snap = await snapshot(page);
    const ttff = snap!.timeToFirstFrameMs!;

    await allure.step('Assert startup delay was measured (ttff > 2 000 ms)', async () => {
      await allure.parameter('timeToFirstFrameMs',  String(ttff));
      await allure.parameter('injected_delay_ms',  String(2_800));
      expect(ttff).toBeGreaterThan(2_000);
    });

    await allure.step('Assert no critical network issues', async () => {
      await assertNoNetworkIssues(networkCapture.entries);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────

  test('black_screen_pulse: blackout overlay appears for CV-style probes', { tag: ['@BAT'] }, async ({ page, networkCapture }) => {
    await allure.feature('Visual Fault Detection');
    await allure.story('Black screen pulse (decoder freeze simulation)');
    await allure.severity('critical');
    await allure.description(`
**Scenario:** Visual fault — a black overlay covers the video at 3 500 ms
for 1 600 ms, simulating a GPU decoder stall or frame corruption event.

This fault is *invisible to server-side logs* — only a client-side visual
probe (or computer-vision CI check) can catch it. The test asserts that the
\`data-testid="visual-blackout-overlay"\` element appears and then clears.

**Pass conditions:**
- Overlay becomes visible within 15 s
- Overlay clears within 15 s of appearing
    `.trim());
    await allure.label('layer', 'e2e');
    await allure.label('testType', 'automated');
    await allure.tag('qoe', 'visual-fault', 'black-screen');

    await selectVideoAndPlay(page, undefined, 'black_screen_pulse');

    const overlay = page.getByTestId('visual-blackout-overlay');

    await allure.step('Assert blackout overlay becomes visible', async () => {
      await expect(overlay).toBeVisible({ timeout: 15_000 });
    });

    await allure.step('Assert blackout overlay clears automatically', async () => {
      await expect(overlay).toBeHidden({ timeout: 15_000 });
    });

    await allure.step('Assert no critical network issues', async () => {
      await assertNoNetworkIssues(networkCapture.entries);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────

  test('forced_mid_play_rebuffer: records at least one buffering span', { tag: ['@Smoke'] }, async ({ page, networkCapture }) => {
    await allure.feature('Rebuffering Detection');
    await allure.story('Mid-play stall (HLS stop/start)');
    await allure.severity('critical');
    await allure.description(`
**Scenario:** Mid-play rebuffering — HLS segment loading is halted at 4 500 ms
for 2 200 ms, then resumed.

Rebuffering mid-play is the primary driver of viewer churn. The QoE collector
must detect the stall via the \`waiting\` media event, measure its duration,
and accumulate it in \`totalBufferingTime\`.

**Pass condition:** \`bufferingEventsCount > 0\` OR \`totalBufferingTime > 0.05 s\`
    `.trim());
    await allure.label('layer', 'e2e');
    await allure.label('testType', 'automated');
    await allure.tag('qoe', 'rebuffering', 'stall');

    await selectVideoAndPlay(page, undefined, 'forced_mid_play_rebuffer');

    await allure.step('Wait for first frame', () => waitForFirstFrame(page));

    await allure.step('Assert at least one buffering event was recorded', async () => {
      await expect
        .poll(async () => {
          const s = await snapshot(page);
          return (s?.bufferingEventsCount ?? 0) > 0 || (s?.totalBufferingTime ?? 0) > 0.05;
        }, { timeout: 60_000 })
        .toBe(true);

      const snap = await snapshot(page);
      await allure.parameter('bufferingEventsCount',   String(snap?.bufferingEventsCount ?? 0));
      await allure.parameter('totalBufferingTime_s',   String(snap?.totalBufferingTime   ?? 0));
    });

    await allure.step('Assert HLS segment continuity (no prolonged network error)', async () => {
      const segErrors     = networkCapture.entries.filter(e => e.category === 'hls-segment' && e.failed);
      const totalSegs     = networkCapture.entries.filter(e => e.category === 'hls-segment').length;
      const segErrorRate  = totalSegs > 0 ? Math.round((segErrors.length / totalSegs) * 100) : 0;
      await allure.parameter('seg_total',       String(totalSegs));
      await allure.parameter('seg_errors',      String(segErrors.length));
      await allure.parameter('seg_error_rate', `${segErrorRate}%`);
      // Segment errors should be below 50% — scenario injects a *stall*, not a hard failure
      expect(segErrorRate).toBeLessThan(50);
    });
  });

});
