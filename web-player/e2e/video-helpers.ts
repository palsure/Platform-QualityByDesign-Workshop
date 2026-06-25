/**
 * Shared UI navigation helpers for E2E tests.
 */

import type { Page } from '@playwright/test';
import { expect } from '@playwright/test';
import { allure } from 'allure-playwright';

const FEATURED_TILE = 'Play Cosmic Journey';

export async function selectVideoAndPlay(
  page: Page,
  tileLabel: string = FEATURED_TILE,
): Promise<void> {
  await page.goto('/');
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
