/**
 * video-helpers.ts
 *
 * Shared UI navigation helpers for E2E tests.
 *
 * Why UI navigation (and not `?scenario=...&e2e_autoplay=1`)?
 *   Headless Chrome blocks `video.play()` without a user gesture (autoplay
 *   policy). Direct URL navigation + `e2e_autoplay=1` works locally but
 *   fails on CI/Firebase deployments — the player loads but never produces
 *   a first frame, causing `waitForFirstFrame` to time out (black screen).
 *
 *   Clicking the Play CTA on the detail page satisfies the user-gesture
 *   requirement and reliably starts playback in every environment.
 */

import type { Page } from '@playwright/test';
import { expect }    from '@playwright/test';
import { allure }    from 'allure-playwright';

/**
 * Simulate a real user selecting a video tile from the home-page carousel,
 * navigating to the detail page, and pressing the Play CTA.
 *
 * @param page       Playwright page (must already be on `/`)
 * @param tileLabel  The aria-label of the VideoTile button, e.g. "Play Crystal Clear"
 */
export async function selectVideoAndPlay(
  page: Page,
  tileLabel: string,
): Promise<void> {
  const videoTitle = tileLabel.replace(/^Play\s+/, '');

  await allure.step(`Select tile from carousel: "${videoTitle}"`, async () => {
    const tile = page.getByRole('button', { name: tileLabel });
    await tile.scrollIntoViewIfNeeded();
    await tile.click();
  });

  await allure.step('Click Play CTA on detail page', async () => {
    const playBtn = page.getByTestId('detail-play-btn');
    await expect(playBtn).toBeVisible({ timeout: 10_000 });

    // The app uses in-memory routing, so the URL stays at the root;
    // we log it here so it is visible alongside the step in the Allure timeline.
    const url = page.url();
    await allure.parameter('player_url', url);

    await playBtn.click();
  });
}

/**
 * Tile-label lookup keyed by demo scenario ID.
 * Mirrors the catalog in `src/data/catalog.ts`.
 */
export const SCENARIO_TILE: Record<string, string> = {
  baseline:                 'Play Crystal Clear',
  startup_delay:            'Play The Delay',
  black_screen_pulse:       'Play Blackout',
  forced_mid_play_rebuffer: 'Play The Stall',
  bitrate_step_down:        'Play Signal Loss',
};

/**
 * Convenience helper: navigate to home, then play the tile for the given scenario.
 * Used by tests that want the simplest possible "open the player and start it" flow.
 */
export async function openScenario(page: Page, scenario: keyof typeof SCENARIO_TILE) {
  const tile = SCENARIO_TILE[scenario];
  if (!tile) throw new Error(`No tile mapping for scenario "${scenario}"`);

  await page.goto('/');
  await page.waitForLoadState('domcontentloaded');
  await selectVideoAndPlay(page, tile);
}
