/**
 * Shared UI navigation helpers for E2E tests.
 */

import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';
import { allure } from 'allure-playwright';

const DEFAULT_TILE_TEST_ID = 'tile-cosmic-journey';

export async function selectVideoAndPlay(
  page: Page,
  tileTestId: string = DEFAULT_TILE_TEST_ID,
): Promise<void> {
  await page.goto('/');
  await page.waitForLoadState('domcontentloaded');

  await allure.step(`Select tile: ${tileTestId}`, async () => {
    const tile = page.getByTestId(tileTestId);
    await tile.scrollIntoViewIfNeeded();
    await tile.click();
  });

  await allure.step('Click Play on detail page', async () => {
    const playBtn = page.getByTestId('detail-play-btn');
    await expect(playBtn).toBeVisible({ timeout: 10_000 });
    await playBtn.click();
  });
}

export async function waitForVideoPlaying(page: Page, timeoutMs = 60_000) {
  const video = page.getByTestId('stream-video');
  await expect(video).toBeVisible({ timeout: 10_000 });
  await expect.poll(async () => {
    return video.evaluate((el: HTMLVideoElement) => !el.paused && el.readyState >= 2);
  }, { timeout: timeoutMs }).toBe(true);
}
