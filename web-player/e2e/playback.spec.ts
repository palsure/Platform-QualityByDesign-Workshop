import { test, expect } from './fixtures';
import { selectVideoAndPlay, waitForVideoPlaying } from './video-helpers';

test.describe('Playback @BAT', () => {
  test('play button opens video element on detail page', async ({ page }) => {
    await selectVideoAndPlay(page);
    await expect(page.getByTestId('stream-video')).toBeVisible();
  });
});

test.describe('Playback @Smoke', () => {
  test('video starts playing after pressing Play', async ({ page }) => {
    await selectVideoAndPlay(page);
    await waitForVideoPlaying(page);
  });

  test('playback pauses and resumes in fullscreen player', async ({ page }) => {
    await selectVideoAndPlay(page);
    await waitForVideoPlaying(page);

    const video = page.getByTestId('stream-video');
    await video.evaluate((el: HTMLVideoElement) => el.pause());
    await expect.poll(async () => video.evaluate((el: HTMLVideoElement) => el.paused)).toBe(true);

    await video.evaluate((el: HTMLVideoElement) => void el.play());
    await waitForVideoPlaying(page);
  });
});

test.describe('Playback @Regression', () => {
  test('e2e autoplay query opens player directly', async ({ page }) => {
    await page.goto('/?e2e_autoplay=1');
    await expect(page.getByTestId('stream-video')).toBeVisible({ timeout: 15_000 });
    await waitForVideoPlaying(page);
  });

  test('closing player overlay returns to detail page', async ({ page }) => {
    await selectVideoAndPlay(page);
    await waitForVideoPlaying(page);
    await page.getByRole('button', { name: 'Close player' }).click();
    await expect(page.getByTestId('detail-play-btn')).toBeVisible();
    await expect(page.getByTestId('stream-video')).toBeHidden();
  });
});
