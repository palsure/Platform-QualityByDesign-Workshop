/**
 * Shared UI navigation helpers for E2E tests.
 *
 * Scenario faults are injected via `?scenario=` on the URL (see App.tsx).
 * Playback still starts from a user gesture — click Play on the detail page.
 */

import type { Page } from '@playwright/test';
import { expect }    from '@playwright/test';
import { allure }    from 'allure-playwright';

const FEATURED_TILE = 'Play Cosmic Journey';

/**
 * Navigate home, open a featured title, and press Play.
 * Optional `scenario` query param activates fault injection for CI tests.
 */
export async function selectVideoAndPlay(
  page: Page,
  tileLabel: string = FEATURED_TILE,
  scenario?: string,
): Promise<void> {
  const url = scenario ? `/?scenario=${scenario}` : '/';
  await page.goto(url);
  await page.waitForLoadState('domcontentloaded');

  const videoTitle = tileLabel.replace(/^Play\s+/, '');

  await allure.step(`Select tile: "${videoTitle}"`, async () => {
    const tile = page.getByRole('button', { name: tileLabel });
    await tile.scrollIntoViewIfNeeded();
    await tile.click();
  });

  await allure.step('Click Play on detail page', async () => {
    const playBtn = page.getByTestId('detail-play-btn');
    await expect(playBtn).toBeVisible({ timeout: 10_000 });
    await allure.parameter('player_url', page.url());
    await playBtn.click();
  });
}

export async function openScenario(page: Page, scenario: string) {
  await selectVideoAndPlay(page, FEATURED_TILE, scenario);
}
