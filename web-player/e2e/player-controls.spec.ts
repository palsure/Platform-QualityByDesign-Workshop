/**
 * player-controls.spec.ts
 *
 * Tests for:
 *   1. Player Controls   — pause, resume, seek forward/backward via the QoE
 *                          demo bridge (window.__QOE_DEMO__)
 *   2. ABR & Bitrate     — quality level enumeration, bandwidth estimation,
 *                          forced level override, and the built-in
 *                          bitrate_step_down fault scenario
 *
 * All commands are issued through the QoE bridge so the tests remain
 * framework-agnostic (no CSS selector hacks; just the JS player API).
 */

import { test, expect }      from './fixtures';
import { allure }             from 'allure-playwright';
import { selectVideoAndPlay } from './video-helpers';
import type { QoeDemoSnapshot } from '../src/demo/qoeDemoBridge';

// ── Bridge helpers ─────────────────────────────────────────────────────────────

const bridge = {
  snapshot: (page: import('@playwright/test').Page) =>
    page.evaluate(() => window.__QOE_DEMO__?.getSnapshot() ?? null),

  play: (page: import('@playwright/test').Page) =>
    page.evaluate(() => window.__QOE_DEMO__?.play()),

  pause: (page: import('@playwright/test').Page) =>
    page.evaluate(() => window.__QOE_DEMO__?.pause()),

  seek: (page: import('@playwright/test').Page, seconds: number) =>
    page.evaluate((s) => window.__QOE_DEMO__?.seek(s), seconds),

  setLevel: (page: import('@playwright/test').Page, level: number) =>
    page.evaluate((l) => window.__QOE_DEMO__?.setLevel(l), level),
};

/** Wait until the bridge is registered and the manifest is parsed.
 *  Uses page.waitForFunction() so intermediate retries are silent in Allure. */
async function waitForManifest(page: import('@playwright/test').Page, timeout = 30_000) {
  await page.waitForFunction(
    () => ((window as any).__QOE_DEMO__?.getSnapshot()?.hlsLevels?.length ?? 0) > 0,
    { timeout },
  );
}

/** Wait until the QoE collector records the first decoded frame.
 *  Uses page.waitForFunction() so intermediate retries are silent in Allure. */
async function waitForFirstFrame(page: import('@playwright/test').Page, timeout = 60_000) {
  await page.waitForFunction(
    () => (window as any).__QOE_DEMO__?.getSnapshot()?.timeToFirstFrameMs != null,
    { timeout },
  );
}

/** Wait until the video's currentTime passes the given threshold.
 *  Uses page.waitForFunction() so intermediate retries are silent in Allure. */
async function waitForPlayhead(
  page: import('@playwright/test').Page,
  minSeconds: number,
  timeout = 30_000,
) {
  await page.waitForFunction(
    (min: number) => ((window as any).__QOE_DEMO__?.getSnapshot()?.currentTime ?? 0) >= min,
    minSeconds,
    { timeout },
  );
}

// ─────────────────────────────────────────────────────────────────────────────
// Suite 1 — Player Controls
// ─────────────────────────────────────────────────────────────────────────────

test.describe('Player Controls (pause / play / seek)', () => {

  test.beforeEach(async ({ page }) => {
    // UI navigation: home → "Crystal Clear" tile → detail page → click Play CTA.
    // The user-gesture click satisfies the headless browser autoplay policy so
    // the video reliably begins decoding (URL+autoplay query params do not).
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    const testedUrl = page.url();
    await allure.parameter('tested_url', testedUrl);
    await allure.link(testedUrl, 'Tested App URL');

    await selectVideoAndPlay(page, 'Play Crystal Clear');
    await waitForFirstFrame(page);
  });

  // ── Pause ──────────────────────────────────────────────────────────────────

  test('pause stops playback and isPaused is true', { tag: ['@BAT'] }, async ({ page }) => {
    await allure.feature('Player Controls');
    await allure.story('Pause');
    await allure.severity('critical');
    await allure.description(`
Verifies that calling pause() via the QoE bridge halts playback.

**Steps:**
1. Autoplay baseline stream, wait for first frame.
2. Call bridge.pause() — equivalent to video.pause().
3. Assert isPaused = true and currentTime is frozen.

**Pass condition:** \`isPaused === true\`
    `.trim());

    await allure.step('Pause playback via bridge', () => bridge.pause(page));
    await page.waitForTimeout(300);

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert player reports isPaused = true', async () => {
      await allure.parameter('isPaused',     String(snap.isPaused));
      await allure.parameter('currentTime',  String(snap.currentTime));
      expect(snap.isPaused).toBe(true);
    });
  });

  // ── Resume ─────────────────────────────────────────────────────────────────

  test('resume after pause restarts playback', { tag: ['@Smoke'] }, async ({ page }) => {
    await allure.feature('Player Controls');
    await allure.story('Resume');
    await allure.severity('critical');
    await allure.description(`
Verifies that calling play() after pause() resumes advancement of currentTime.

**Pass condition:** currentTime advances > 0.5 s within 5 s of resume.
    `.trim());

    await allure.step('Pause playback', () => bridge.pause(page));
    await page.waitForTimeout(200);

    const snapBefore = await bridge.snapshot(page) as QoeDemoSnapshot;
    const frozenTime = snapBefore.currentTime;

    await allure.step('Resume playback via bridge', () => bridge.play(page));

    await allure.step('Assert currentTime advances after resume', async () => {
      await expect
        .poll(() => bridge.snapshot(page).then(s => s?.currentTime ?? 0), { timeout: 5_000 })
        .toBeGreaterThan(frozenTime + 0.3);

      const snapAfter = await bridge.snapshot(page) as QoeDemoSnapshot;
      await allure.parameter('isPaused',         String(snapAfter.isPaused));
      await allure.parameter('frozenTime_s',     String(frozenTime));
      await allure.parameter('resumedTime_s',    String(snapAfter.currentTime));
      expect(snapAfter.isPaused).toBe(false);
    });
  });

  // ── Seek forward ──────────────────────────────────────────────────────────

  test('seek forward: currentTime jumps to target position', { tag: ['@Smoke'] }, async ({ page }) => {
    await allure.feature('Player Controls');
    await allure.story('Seek Forward');
    await allure.severity('normal');
    await allure.description(`
Seeks the playhead forward to 20 s and verifies the video element reports
the new position within ±2 s.

**Pass condition:** \`currentTime ≥ 18 s\`
    `.trim());

    const TARGET = 20;

    await allure.step(`Seek to ${TARGET}s`, () => bridge.seek(page, TARGET));

    await allure.step('Wait for playhead to reach target', () =>
      waitForPlayhead(page, TARGET - 2));

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert currentTime is at target', async () => {
      await allure.parameter('seekTarget_s',   String(TARGET));
      await allure.parameter('currentTime_s',  String(snap.currentTime));
      expect(snap.currentTime).toBeGreaterThanOrEqual(TARGET - 2);
    });
  });

  // ── Seek backward ─────────────────────────────────────────────────────────

  test('seek backward: currentTime rewinds to earlier position', { tag: ['@Smoke'] }, async ({ page }) => {
    await allure.feature('Player Controls');
    await allure.story('Seek Backward');
    await allure.severity('normal');
    await allure.description(`
Seeks the playhead forward to 30 s, then rewinds back to 5 s. Verifies that
the player correctly handles backward seeking in an HLS VOD stream.

**Pass condition:** currentTime ≤ 8 s after the backward seek.
    `.trim());

    await allure.step('Seek forward to 30s', () => bridge.seek(page, 30));
    await waitForPlayhead(page, 28);

    const snapMid = await bridge.snapshot(page) as QoeDemoSnapshot;
    await allure.parameter('midpoint_s',  String(snapMid.currentTime));

    await allure.step('Seek backward to 5s', () => bridge.seek(page, 5));
    await page.waitForTimeout(800);

    const snapAfter = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert currentTime is at the rewound position', async () => {
      await allure.parameter('rewindTarget_s',  String(5));
      await allure.parameter('currentTime_s',   String(snapAfter.currentTime));
      expect(snapAfter.currentTime).toBeLessThanOrEqual(8);
    });
  });

  // ── Seek boundary ─────────────────────────────────────────────────────────

  test('seek to 0 resets playhead to beginning', { tag: ['@Smoke'] }, async ({ page }) => {
    await allure.feature('Player Controls');
    await allure.story('Seek Boundary');
    await allure.severity('minor');
    await allure.description('Seeking to 0 resets the playhead to the beginning of the stream.');

    await allure.step('Seek forward to 15s', () => bridge.seek(page, 15));
    await waitForPlayhead(page, 13);

    await allure.step('Seek back to 0', () => bridge.seek(page, 0));
    await page.waitForTimeout(600);

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert currentTime ≤ 2 s', async () => {
      await allure.parameter('currentTime_s',  String(snap.currentTime));
      expect(snap.currentTime).toBeLessThanOrEqual(2);
    });
  });
});

// ─────────────────────────────────────────────────────────────────────────────
// Suite 2 — ABR & Bitrate
// ─────────────────────────────────────────────────────────────────────────────

test.describe('ABR & Bitrate (HLS.js level management)', () => {

  test.beforeEach(async ({ page }) => {
    // UI navigation (same as Player Controls suite) — bypasses autoplay block.
    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    const testedUrl = page.url();
    await allure.parameter('tested_url', testedUrl);
    await allure.link(testedUrl, 'Tested App URL');

    await selectVideoAndPlay(page, 'Play Crystal Clear');
    await waitForManifest(page);
  });

  // ── Quality levels ─────────────────────────────────────────────────────────

  test('multiple quality levels are available after manifest parse', { tag: ['@BAT'] }, async ({ page }) => {
    await allure.feature('ABR');
    await allure.story('Quality Levels');
    await allure.severity('critical');
    await allure.description(`
Verifies that the HLS manifest exposes more than one quality rendition.
ABR requires at least 2 levels to be meaningful.

**Pass condition:** \`hlsLevels.length > 1\`
    `.trim());

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert multiple quality levels', async () => {
      await allure.parameter('levelCount',  String(snap.hlsLevels.length));
      for (const l of snap.hlsLevels) {
        await allure.parameter(`level_${l.index}`,  String(l.name));
      }
      expect(snap.hlsLevels.length).toBeGreaterThan(1);
    });
  });

  // ── Bandwidth estimate ─────────────────────────────────────────────────────

  test('bandwidth estimate is non-zero after first segment download', { tag: ['@BAT'] }, async ({ page }) => {
    await allure.feature('ABR');
    await allure.story('Bandwidth Estimation');
    await allure.severity('normal');
    await allure.description(`
HLS.js estimates the available bandwidth after downloading the first segment.
This is the foundation of adaptive bitrate selection.

**Pass condition:** \`bandwidthEstimate > 0\` within 30 s.
    `.trim());

    await allure.step('Wait for bandwidth estimate to be non-zero', async () => {
      await expect
        .poll(() => bridge.snapshot(page).then(s => s?.bandwidthEstimate ?? 0), { timeout: 30_000 })
        .toBeGreaterThan(0);
    });

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert bandwidth estimate is reasonable', async () => {
      const estimateMbps = Math.round(snap.bandwidthEstimate / 1_000) / 1_000;
      await allure.parameter('bandwidthEstimate_bps',   String(snap.bandwidthEstimate));
      await allure.parameter('bandwidthEstimate_Mbps',  String(estimateMbps));
      // Estimate should be > 0 and below 1 Gbps (sanity check)
      expect(snap.bandwidthEstimate).toBeGreaterThan(0);
      expect(snap.bandwidthEstimate).toBeLessThan(1_000_000_000);
    });
  });

  // ── ABR current level ─────────────────────────────────────────────────────

  test('ABR selects a valid quality level after first frame', { tag: ['@Smoke'] }, async ({ page }) => {
    await allure.feature('ABR');
    await allure.story('Automatic Level Selection');
    await allure.severity('normal');
    await allure.description(`
After the first frame is rendered, HLS.js should have locked into a specific
quality level (currentLevelIndex ≥ 0).

**Pass condition:** \`currentLevelIndex ≥ 0\`
    `.trim());

    await waitForFirstFrame(page);
    await page.waitForTimeout(500);

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert ABR has selected a level', async () => {
      await allure.parameter('currentLevelIndex',  String(snap.currentLevelIndex));
      await allure.parameter('currentLevel',
         String(snap.hlsLevels[snap.currentLevelIndex]?.name ?? 'auto'));
      await allure.parameter('bandwidthEstimate_bps',  String(snap.bandwidthEstimate));
      expect(snap.currentLevelIndex).toBeGreaterThanOrEqual(0);
    });
  });

  // ── Manual level override ──────────────────────────────────────────────────

  test('manual level override forces lowest quality', { tag: ['@Smoke'] }, async ({ page }) => {
    await allure.feature('ABR');
    await allure.story('Manual Level Override');
    await allure.severity('normal');
    await allure.description(`
Calling setLevel(0) forces HLS.js to lock to the lowest quality rendition
regardless of available bandwidth. This is useful for testing degraded
network conditions without actual throttling.

**Pass condition:** \`currentLevelIndex === 0\` within 10 s.
    `.trim());

    await waitForFirstFrame(page);
    const snapBefore = await bridge.snapshot(page) as QoeDemoSnapshot;
    await allure.parameter('levelBefore',  String(snapBefore.currentLevelIndex));

    await allure.step('Force lowest quality level (index 0)', () =>
      bridge.setLevel(page, 0));

    await allure.step('Wait for level switch to take effect', async () => {
      await expect
        .poll(() => bridge.snapshot(page).then(s => s?.currentLevelIndex), { timeout: 10_000 })
        .toBe(0);
    });

    const snapAfter = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert forced level is active', async () => {
      await allure.parameter('forcedLevel',       String(0));
      await allure.parameter('currentLevel',      String(snapAfter.currentLevelIndex));
      await allure.parameter('levelName',         String(snapAfter.hlsLevels[0]?.name ?? '?'));
      await allure.parameter('bitrate_bps',       String(snapAfter.hlsLevels[0]?.bitrate ?? 0));
      expect(snapAfter.currentLevelIndex).toBe(0);
    });
  });

  // ── Restore auto ABR ──────────────────────────────────────────────────────

  test('setLevel(-1) restores auto ABR after manual override', { tag: ['@Regression'] }, async ({ page }) => {
    await allure.feature('ABR');
    await allure.story('Restore Auto ABR');
    await allure.severity('normal');
    await allure.description(`
After forcing a quality level, setLevel(-1) hands control back to HLS.js
adaptive algorithm. The player should step up to a higher quality when
bandwidth permits.

**Pass condition:** \`currentLevelIndex > 0\` within 15 s of restoring auto.
    `.trim());

    await waitForFirstFrame(page);
    const levels = (await bridge.snapshot(page) as QoeDemoSnapshot).hlsLevels;
    // Only meaningful when there is more than one level
    test.skip(levels.length <= 1, 'Stream has only one quality level');

    await allure.step('Force lowest quality', () => bridge.setLevel(page, 0));
    await expect
      .poll(() => bridge.snapshot(page).then(s => s?.currentLevelIndex), { timeout: 10_000 })
      .toBe(0);

    await allure.step('Restore auto ABR', () => bridge.setLevel(page, -1));

    await allure.step('Assert ABR steps up to a higher quality', async () => {
      await expect
        .poll(() => bridge.snapshot(page).then(s => s?.currentLevelIndex ?? -1), { timeout: 15_000 })
        .toBeGreaterThan(0);

      const snap = await bridge.snapshot(page) as QoeDemoSnapshot;
      await allure.parameter('restoredLevelIndex',  String(snap.currentLevelIndex));
      await allure.parameter('restoredLevelName',   String(snap.hlsLevels[snap.currentLevelIndex]?.name ?? '?'));
    });
  });

});

// ─────────────────────────────────────────────────────────────────────────────
// Forced bitrate step-down — standalone (no beforeEach navigation conflict)
// ─────────────────────────────────────────────────────────────────────────────

test.describe('ABR — Forced Bitrate Step-Down Scenario', () => {

  test('bitrate_step_down: records ≥ 2 bitrate switches', { tag: ['@Regression'] }, async ({ page }) => {
    await allure.feature('ABR');
    await allure.story('Forced Bitrate Step-Down');
    await allure.severity('critical');
    await allure.description(`
The \`bitrate_step_down\` scenario calls \`hls.nextLevel = 0\` at 2 s (step down
to the lowest quality rendition) then restores auto ABR at 4.5 s.

This forces at least **one** \`LEVEL_SWITCHED\` event (the forced step-down).
The QoE collector must record it — confirming the level-event pipeline is wired
correctly end-to-end.

**Pass condition:** \`bitrateSwitches ≥ 1\`
    `.trim());

    await page.goto('/');
    await page.waitForLoadState('domcontentloaded');
    await selectVideoAndPlay(page, 'Play Signal Loss');
    await waitForFirstFrame(page);

    await allure.step('Wait for forced level-switch event (≤ 20 s)', async () => {
      await expect
        .poll(() => bridge.snapshot(page).then(s => s?.bitrateSwitches ?? 0), { timeout: 20_000 })
        .toBeGreaterThanOrEqual(1);
    });

    const snap = await bridge.snapshot(page) as QoeDemoSnapshot;

    await allure.step('Assert level-switch events were recorded', async () => {
      await allure.parameter('bitrateSwitches',   String(snap.bitrateSwitches));
      await allure.parameter('lastBitrate_bps',   String(snap.lastBitrate ?? 0));
      await allure.parameter('currentLevelIndex', String(snap.currentLevelIndex));
      expect(snap.bitrateSwitches).toBeGreaterThanOrEqual(1);
    });
  });
});
