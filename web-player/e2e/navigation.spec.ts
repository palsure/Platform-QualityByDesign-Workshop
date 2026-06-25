import { test, expect } from './fixtures';

const COSMIC_JOURNEY_TILE = 'tile-cosmic-journey';

test.describe('Navigation @BAT', () => {
  test('home page loads with navbar and featured content', async ({ page }) => {
    await page.goto('/');
    await expect(page.getByRole('navigation')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Home', exact: true })).toBeVisible();
    await expect(page.getByTestId(COSMIC_JOURNEY_TILE)).toBeVisible();
  });

  test('navbar switches between Home, Movies, Shows, and Live', async ({ page }) => {
    await page.goto('/');

    await page.getByRole('button', { name: 'Movies' }).click();
    await expect(page.getByRole('heading', { name: 'Movies', exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Shows' }).click();
    await expect(page.getByRole('heading', { name: 'TV Shows', exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Live' }).click();
    await expect(page.getByRole('heading', { name: 'Live', exact: true })).toBeVisible();

    await page.getByRole('button', { name: 'Home', exact: true }).click();
    await expect(page.getByTestId(COSMIC_JOURNEY_TILE)).toBeVisible();
  });

  test('tile opens detail page and back returns to browse tab', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId(COSMIC_JOURNEY_TILE).click();
    await expect(page.getByTestId('detail-title')).toHaveText('Cosmic Journey');
    await page.getByRole('button', { name: 'Back' }).click();
    await expect(page.getByTestId(COSMIC_JOURNEY_TILE)).toBeVisible();
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
    await expect(page.getByTestId(COSMIC_JOURNEY_TILE)).toBeVisible();
  });
});

test.describe('Navigation @Regression', () => {
  test('detail page shows metadata panels', async ({ page }) => {
    await page.goto('/');
    await page.getByTestId(COSMIC_JOURNEY_TILE).click();
    await expect(page.getByText('About')).toBeVisible();
    await expect(page.getByText('Genre')).toBeVisible();
    await expect(page.getByText('Duration')).toBeVisible();
  });
});
