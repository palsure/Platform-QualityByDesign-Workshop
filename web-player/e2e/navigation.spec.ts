import { test, expect } from './fixtures';
import { selectVideoAndPlay } from './video-helpers';

test.describe('Navigation @BAT', () => {
  test('home page loads with navbar and featured content', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('navigation')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Home' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Play Cosmic Journey' })).toBeVisible();
  });

  test('navbar switches between Home, Movies, Shows, and Live', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('button', { name: 'Movies' }).click();
    await expect(page.getByRole('heading', { name: 'Movies' })).toBeVisible();

    await page.getByRole('button', { name: 'Shows' }).click();
    await expect(page.getByRole('heading', { name: 'TV Shows' })).toBeVisible();

    await page.getByRole('button', { name: 'Live' }).click();
    await expect(page.getByRole('heading', { name: 'Live' })).toBeVisible();

    await page.getByRole('button', { name: 'Home' }).click();
    await expect(page.getByRole('button', { name: 'Play Cosmic Journey' })).toBeVisible();
  });

  test('tile opens detail page and back returns to browse tab', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Play Cosmic Journey' }).click();
    await expect(page.getByRole('heading', { name: 'Cosmic Journey' })).toBeVisible();
    await page.getByRole('button', { name: 'Back' }).click();
    await expect(page.getByRole('button', { name: 'Play Cosmic Journey' })).toBeVisible();
  });
});

test.describe('Navigation @Smoke', () => {
  test('Movies tab shows browse rows with playable tiles', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Movies' }).click();
    await expect(page.getByRole('heading', { name: 'Movies' })).toBeVisible();
    const firstTile = page.getByRole('button', { name: /^Play / }).first();
    await firstTile.scrollIntoViewIfNeeded();
    await expect(firstTile).toBeVisible();
  });

  test('logo returns to home from Movies tab', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Movies' }).click();
    await page.getByRole('button', { name: 'Go home' }).click();
    await expect(page.getByRole('button', { name: 'Play Cosmic Journey' })).toBeVisible();
  });
});

test.describe('Navigation @Regression', () => {
  test('detail page shows metadata panels', async ({ page }) => {
    await page.goto('/');
    await page.getByRole('button', { name: 'Play Cosmic Journey' }).click();
    await expect(page.getByText('About')).toBeVisible();
    await expect(page.getByText('Genre')).toBeVisible();
    await expect(page.getByText('Duration')).toBeVisible();
  });
});
