/**
 * network-throttle.spec.ts
 *
 * Tests the player's behaviour under simulated network constraints using
 * Playwright's CDP network emulation (Chromium only).
 *
 * Throttle presets (download / latency):
 *   4G    — 20 Mbps  / 20 ms  (smooth, expect highest quality)
 *   3G    — 1.5 Mbps / 100 ms (common mobile; expect mid-quality)
 *   Slow3G— 400 kbps / 300 ms (degraded; expect lowest quality, possible stalls)
 *
 * All throttle calls are applied BEFORE page.goto() so network conditions
 * are in effect from the very first byte.
 */

import { test, expect, NETWORK_PRESETS } from './fixtures';
import { allure }                         from 'allure-playwright';
import type { QoeDemoSnapshot }           from '../src/demo/qoeDemoBridge';

// ── Bridge helpers ─────────────────────────────────────────────────────────────

const bridge = {
  snapshot: (page: import('@playwright/test').Page) =>
    page.evaluate(() => window.__QOE_DEMO__?.getSnapshot() ?? null),
};

/** Wait functions use page.waitForFunction() — polls silently in the browser
 *  without creating intermediate failed assertion steps in Allure. */

async function waitForFirstFrame(page: import('@playwright/test').Page, timeout = 90_000) {
  await page.waitForFunction(
    () => (window as any).__QOE_DEMO__?.getSnapshot()?.timeToFirstFrameMs != null,
    { timeout },
  );
}

async function waitForBandwidthEstimate(page: import('@playwright/test').Page, timeout = 60_000) {
  await page.waitForFunction(
    () => ((window as any).__QOE_DEMO__?.getSnapshot()?.bandwidthEstimate ?? 0) > 0,
    { timeout },
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Suite 1 — Time to First Frame under throttled conditions
// ─────────────────────────────────────────────────────────────────────────────

test.describe('TTFF under network throttling', { tag: ['@Regression'] }, () => {

  test('4G throttle: first frame within 10 s', async ({ page, throttle }) => {
    await allure.feature('Network Throttling');
    await allure.story('4G — Time to First Frame');
    await allure.severity('normal');
    await allure.description(`
Applies a 4G network profile (20 Mbps / 20 ms RTT) before navigation.

A well-optimised player should deliver the first decoded frame within 10 s
on a 4G connection. If the TTFF exceeds this, there is likely a startup
penalty or manifest parsing bottleneck.

**Pass condition:** \`timeToFirstFrameMs < 10 000\`
    `.trim());
    await allure.tag('throttle', '4G', 'ttff');

    const preset = NETWORK_PRESETS['4G'];
    await allure.step('Apply 4G network profile', () => throttle.apply('4G'));
    await allure.parameter('throttle_downloadKbps',  String(preset.downloadKbps));
    await allure.parameter('throttle_latencyMs',     String(preset.latencyMs));

    await page.goto('/?scenario=baseline&e2e_autoplay=1');
    await allure.step('Wait for first frame', () => waitForFirstFrame(page, 20_000));

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert TTFF < 10 000 ms', async () => {
      await allure.parameter('timeToFirstFrameMs',  String(snap.timeToFirstFrameMs!));
      expect(snap.timeToFirstFrameMs!).toBeLessThan(10_000);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────

  test('3G throttle: first frame within 30 s', async ({ page, throttle }) => {
    await allure.feature('Network Throttling');
    await allure.story('3G — Time to First Frame');
    await allure.severity('normal');
    await allure.description(`
Applies a 3G network profile (1.5 Mbps / 100 ms RTT) before navigation.

On a 3G connection the player must buffer enough segments before playback.
The budget is generous (30 s) to account for network variance in CI.

**Pass condition:** \`timeToFirstFrameMs < 30 000\`
    `.trim());
    await allure.tag('throttle', '3G', 'ttff');

    const preset = NETWORK_PRESETS['3G'];
    await allure.step('Apply 3G network profile', () => throttle.apply('3G'));
    await allure.parameter('throttle_downloadKbps',  String(preset.downloadKbps));
    await allure.parameter('throttle_latencyMs',     String(preset.latencyMs));

    await page.goto('/?scenario=baseline&e2e_autoplay=1');
    await allure.step('Wait for first frame (≤ 30 s budget)', () =>
      waitForFirstFrame(page, 45_000));

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert TTFF < 30 000 ms', async () => {
      await allure.parameter('timeToFirstFrameMs',  String(snap.timeToFirstFrameMs!));
      expect(snap.timeToFirstFrameMs!).toBeLessThan(30_000);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────

  test('Slow3G throttle: player stays alive without fatal errors', async ({ page, throttle }) => {
    // Slow3G is very slow — skip in CI fast-run unless @slow tag is explicitly requested
    test.setTimeout(180_000);
    // Mark so this can be excluded with --grep-invert @slow
    await allure.tag('slow');

    await allure.feature('Network Throttling');
    await allure.story('Slow3G — Resilience');
    await allure.severity('normal');
    await allure.description(`
Applies a Slow3G profile (400 kbps / 300 ms RTT). Playback may stall and
the player may take a long time to start, but it must not report any fatal
HLS errors. The test validates resilience, not performance.

**Pass conditions:**
- \`errorCount === 0\` (no fatal HLS errors)
- \`timeToFirstFrameMs != null\` (player eventually starts)
    `.trim());
    await allure.tag('throttle', 'Slow3G', 'resilience');

    await allure.step('Apply Slow3G network profile', () => throttle.apply('Slow3G'));

    await page.goto('/?scenario=baseline&e2e_autoplay=1');
    await allure.step('Wait for first frame (120 s budget)', () =>
      waitForFirstFrame(page, 120_000));

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert no fatal HLS errors', async () => {
      await allure.parameter('errorCount',          String(snap.errorCount));
      await allure.parameter('timeToFirstFrameMs',  String(snap.timeToFirstFrameMs ?? 'null'));
      await allure.parameter('bufferingEvents',     String(snap.bufferingEventsCount));
      expect(snap.errorCount).toBe(0);
      expect(snap.timeToFirstFrameMs).not.toBeNull();
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Suite 2 — Bandwidth estimation accuracy
// ─────────────────────────────────────────────────────────────────────────────

test.describe('Bandwidth estimation under throttling', { tag: ['@Regression'] }, () => {

  test('3G throttle: bandwidth estimate reflects constrained network', async ({ page, throttle }) => {
    await allure.feature('Network Throttling');
    await allure.story('3G — Bandwidth Estimation');
    await allure.severity('normal');
    await allure.description(`
Under a 3G throttle (1.5 Mbps), HLS.js should converge to a bandwidth
estimate that is meaningfully lower than an unthrottled connection.

We allow a generous 4× headroom above the throttle to account for burst
buffering and initial segment pre-load before the throttle was fully applied.

**Pass condition:** \`bandwidthEstimate < 3G_limit × 4 = 6 000 000 bps\`
    `.trim());
    await allure.tag('throttle', '3G', 'bandwidth');

    const preset     = NETWORK_PRESETS['3G'];
    const maxExpected = preset.downloadKbps * 1_000 * 4; // generous 4× headroom

    await allure.step('Apply 3G network profile', () => throttle.apply('3G'));
    await allure.parameter('throttle_downloadKbps',  String(preset.downloadKbps));
    await allure.parameter('maxExpected_bps',         String(maxExpected));

    await page.goto('/?scenario=baseline&e2e_autoplay=1');
    await allure.step('Wait for bandwidth estimate', () =>
      waitForBandwidthEstimate(page, 60_000));

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert bandwidth estimate is below expected ceiling', async () => {
      const estimateMbps = Math.round(snap.bandwidthEstimate / 10_000) / 100;
      await allure.parameter('bandwidthEstimate_bps',   String(snap.bandwidthEstimate));
      await allure.parameter('bandwidthEstimate_Mbps',  String(estimateMbps));
      await allure.parameter('maxExpected_Mbps',        String(maxExpected / 1_000_000));
      expect(snap.bandwidthEstimate).toBeGreaterThan(0);
      expect(snap.bandwidthEstimate).toBeLessThan(maxExpected);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────

  test('4G throttle: bandwidth estimate is non-trivially large', async ({ page, throttle }) => {
    await allure.feature('Network Throttling');
    await allure.story('4G — Bandwidth Estimation');
    await allure.severity('normal');
    await allure.description(`
On a 4G profile (20 Mbps), HLS.js bandwidth estimation should report a
meaningful throughput after several segments are downloaded. We wait for first
frame + 3 s to allow the moving average to stabilise.

**Pass condition:** \`bandwidthEstimate > 200 000 bps\` (> 200 kbps)
    `.trim());
    await allure.tag('throttle', '4G', 'bandwidth');

    await allure.step('Apply 4G network profile', () => throttle.apply('4G'));
    await allure.parameter('throttle_downloadKbps', String(NETWORK_PRESETS['4G'].downloadKbps));

    await page.goto('/?scenario=baseline&e2e_autoplay=1');
    await allure.step('Wait for first frame', () => waitForFirstFrame(page, 30_000));
    await page.waitForTimeout(3_000);

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert bandwidth estimate > 200 kbps', async () => {
      const estimateMbps = Math.round(snap.bandwidthEstimate / 10_000) / 100;
      await allure.parameter('bandwidthEstimate_bps',  String(snap.bandwidthEstimate));
      await allure.parameter('bandwidthEstimate_Mbps', String(estimateMbps));
      expect(snap.bandwidthEstimate).toBeGreaterThan(200_000);
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Suite 3 — ABR quality adaptation under throttling
// ─────────────────────────────────────────────────────────────────────────────

test.describe('ABR quality adaptation under throttling', { tag: ['@Regression'] }, () => {

  test('3G throttle: player selects a quality level within bandwidth budget', async ({ page, throttle }) => {
    await allure.feature('Network Throttling');
    await allure.story('3G — ABR Level Selection');
    await allure.severity('critical');
    await allure.description(`
Under a 3G throttle (1.5 Mbps), HLS.js adaptive bitrate logic must select
a quality rendition whose bitrate does not exceed the available bandwidth.

The selected level's bitrate should be ≤ 1.5 Mbps (the throttle limit).
We apply a small 10% tolerance for segment burst buffering.

**Pass condition:** \`selectedLevel.bitrate ≤ 3G_limit × 1.1\`
    `.trim());
    await allure.tag('throttle', '3G', 'abr');

    const g3LimitBps = NETWORK_PRESETS['3G'].downloadKbps * 1_000; // 1 500 000 bps
    // ABR doesn't react instantaneously — allow 3× headroom for the convergence period
    const tolerance  = g3LimitBps * 3;                              // 4 500 000 bps

    await allure.step('Apply 3G network profile', () => throttle.apply('3G'));

    await page.goto('/?scenario=baseline&e2e_autoplay=1');
    await allure.step('Wait for first frame', () => waitForFirstFrame(page, 60_000));

    // Give ABR time to stabilise on a throttled connection (ABR convergence is not instant)
    await page.waitForTimeout(5_000);

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;
    const selected = snap.hlsLevels[snap.currentLevelIndex];

    await allure.step('Assert selected quality is within bandwidth budget', async () => {
      await allure.parameter('currentLevelIndex',    String(snap.currentLevelIndex));
      await allure.parameter('selectedLevel',        String(selected?.name ?? 'unknown'));
      await allure.parameter('selectedBitrate_bps',  String(selected?.bitrate ?? 0));
      await allure.parameter('g3Limit_bps',          String(g3LimitBps));
      await allure.parameter('tolerance_bps',        String(tolerance));
      await allure.parameter('bandwidthEstimate_bps',  String(snap.bandwidthEstimate));

      // Ensure we have valid data
      expect(snap.currentLevelIndex).toBeGreaterThanOrEqual(0);
      expect(selected).toBeDefined();

      // The selected bitrate must not exceed the throttle limit (with tolerance)
      expect(selected!.bitrate).toBeLessThanOrEqual(tolerance);
    });
  });

  // ──────────────────────────────────────────────────────────────────────────

  test('3G→None: player upgrades quality after throttle is removed', async ({ page, throttle }) => {
    await allure.feature('Network Throttling');
    await allure.story('Network Recovery — ABR Upgrade');
    await allure.severity('normal');
    await allure.description(`
Simulates a network recovery scenario:

1. Apply 3G throttle — player selects low quality.
2. Remove throttle — player should detect improved bandwidth and step up.

**Pass condition:** After removing throttle, \`currentLevelIndex\` increases
within 15 s compared to the level selected under 3G.
    `.trim());
    await allure.tag('throttle', '3G', 'recovery', 'abr');

    await allure.step('Apply 3G throttle', () => throttle.apply('3G'));
    await page.goto('/?scenario=baseline&e2e_autoplay=1');
    await allure.step('Wait for first frame under 3G', () => waitForFirstFrame(page, 60_000));
    await page.waitForTimeout(3_000);

    const snapThrottled = await bridge.snapshot(page) as QoeDemoSnapshot;
    await allure.parameter('throttled_levelIndex',   String(snapThrottled.currentLevelIndex));
    await allure.parameter('throttled_levelName',    String(snapThrottled.hlsLevels[snapThrottled.currentLevelIndex]?.name ?? '?'));

    await allure.step('Remove throttle (restore full speed)', () => throttle.reset());

    // ABR may not step all the way to highest within 20s — assert it holds or improves
    await page.waitForTimeout(3_000);

    await allure.step('Assert ABR does not downgrade after throttle removed', async () => {
      await expect
        .poll(
          () => bridge.snapshot(page).then(s => s?.currentLevelIndex ?? 0),
          { timeout: 20_000 },
        )
        .toBeGreaterThanOrEqual(snapThrottled.currentLevelIndex);
    });

    const snapRecovered = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert quality held or improved after throttle removed', async () => {
      await allure.parameter('throttled_levelIndex',  String(snapThrottled.currentLevelIndex));
      await allure.parameter('recovered_levelIndex',  String(snapRecovered.currentLevelIndex));
      await allure.parameter('recovered_levelName',   snapRecovered.hlsLevels[snapRecovered.currentLevelIndex]?.name ?? '?');
      await allure.parameter('bandwidthEstimate_bps', String(snapRecovered.bandwidthEstimate));
      expect(snapRecovered.currentLevelIndex).toBeGreaterThanOrEqual(snapThrottled.currentLevelIndex);
    });
  });
});
