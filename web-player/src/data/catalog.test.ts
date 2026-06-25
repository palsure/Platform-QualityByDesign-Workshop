import * as allure from 'allure-js-commons';
import { describe, expect, it } from 'vitest';
import { ALL_VIDEOS, CATALOG, HERO_VIDEO, findVideo } from './catalog';

// ── CATALOG structure ───────────────────────────────────────────────────────

describe('CATALOG', () => {
  it('contains at least one row', async () => {
    await allure.feature('Video Catalog');
    await allure.story('CATALOG structure');
    await allure.description('The catalog must expose at least one content row.');
    await allure.step('row count > 0', () => expect(CATALOG.length).toBeGreaterThan(0));
  });

  it('every row has a non-empty id and label', async () => {
    await allure.feature('Video Catalog');
    await allure.story('CATALOG structure');
    await allure.description('Every catalog row must have a non-empty id (used as a React key) and a display label.');
    for (const row of CATALOG) {
      await allure.step(`row "${row.id}" has id and label`, () => {
        expect(row.id).toBeTruthy();
        expect(row.label).toBeTruthy();
      });
    }
  });

  it('every row contains at least one video', async () => {
    await allure.feature('Video Catalog');
    await allure.story('CATALOG structure');
    await allure.description('Rendering an empty row would produce a blank carousel — every row must have ≥ 1 video.');
    for (const row of CATALOG) {
      await allure.step(`row "${row.id}" has videos`, () =>
        expect(row.videos.length).toBeGreaterThan(0),
      );
    }
  });

  it('contains a "featured" row', async () => {
    await allure.feature('Video Catalog');
    await allure.story('CATALOG structure');
    await allure.description('The "featured" row drives the hero banner on the home page.');
    await allure.step('find featured row', () =>
      expect(CATALOG.find(r => r.id === 'featured')).toBeDefined(),
    );
  });

  it('contains a "new-releases" row', async () => {
    await allure.feature('Video Catalog');
    await allure.story('CATALOG structure');
    await allure.description('The new-releases row provides browse content for the home page.');
    await allure.step('find new-releases row', () =>
      expect(CATALOG.find(r => r.id === 'new-releases')).toBeDefined(),
    );
  });
});

// ── ALL_VIDEOS ──────────────────────────────────────────────────────────────

describe('ALL_VIDEOS', () => {
  it('is the flat union of all CATALOG rows', async () => {
    await allure.feature('Video Catalog');
    await allure.story('ALL_VIDEOS');
    await allure.description('ALL_VIDEOS must equal the flat-mapped union of every catalog row — no extras, no omissions.');
    await allure.step('deep-equal CATALOG.flatMap', () =>
      expect(ALL_VIDEOS).toEqual(CATALOG.flatMap(r => r.videos)),
    );
  });

  it('contains more than 5 videos', async () => {
    await allure.feature('Video Catalog');
    await allure.story('ALL_VIDEOS');
    await allure.description('The catalog must have a meaningful number of videos — a count ≤ 5 would indicate missing content.');
    await allure.step('length > 5', () => expect(ALL_VIDEOS.length).toBeGreaterThan(5));
  });

  it('all video IDs are unique', async () => {
    await allure.feature('Video Catalog');
    await allure.story('ALL_VIDEOS');
    await allure.description('Duplicate video IDs would cause React key collisions and routing bugs.');
    await allure.step('Set size === array length', () => {
      const ids = ALL_VIDEOS.map(v => v.id);
      expect(new Set(ids).size).toBe(ids.length);
    });
  });

  it('all videos have non-empty id, title, and description', async () => {
    await allure.feature('Video Catalog');
    await allure.story('ALL_VIDEOS');
    await allure.description('Every video must have the three fields required for display in a VideoTile component.');
    for (const video of ALL_VIDEOS) {
      await allure.step(`"${video.id}" has id, title, description`, () => {
        expect(video.id).toBeTruthy();
        expect(video.title).toBeTruthy();
        expect(video.description).toBeTruthy();
      });
    }
  });

  it('all videos have a contentType', async () => {
    await allure.feature('Video Catalog');
    await allure.story('ALL_VIDEOS');
    for (const video of ALL_VIDEOS) {
      await allure.step(`"${video.id}" has contentType`, () =>
        expect(['movie', 'show', 'live']).toContain(video.contentType),
      );
    }
  });

  it('movies, shows, and live tabs each have content', async () => {
    await allure.feature('Video Catalog');
    await allure.story('Browse tabs');
    const movies = ALL_VIDEOS.filter(v => v.contentType === 'movie');
    const shows = ALL_VIDEOS.filter(v => v.contentType === 'show');
    const live = ALL_VIDEOS.filter(v => v.contentType === 'live');
    await allure.step('movies tab', () => expect(movies.length).toBeGreaterThan(0));
    await allure.step('shows tab', () => expect(shows.length).toBeGreaterThan(0));
    await allure.step('live tab', () => expect(live.length).toBeGreaterThan(0));
  });

  it('all hlsUrl values are valid http(s) URLs', async () => {
    await allure.feature('Video Catalog');
    await allure.story('ALL_VIDEOS');
    await allure.description('HLS stream URLs must be fully-qualified http(s) addresses ending in .m3u8 so HLS.js can load them.');
    for (const video of ALL_VIDEOS) {
      await allure.step(`"${video.id}" hlsUrl is valid`, () =>
        expect(video.hlsUrl).toMatch(/^https?:\/\/.+\.m3u8$/),
      );
    }
  });

  it('all matchScore values are in the range 0–100', async () => {
    await allure.feature('Video Catalog');
    await allure.story('ALL_VIDEOS');
    await allure.description('matchScore drives the recommendation percentage badge (0–100); values outside that range would cause display bugs.');
    for (const video of ALL_VIDEOS) {
      await allure.step(`"${video.id}" matchScore in [0, 100]`, () => {
        expect(video.matchScore).toBeGreaterThanOrEqual(0);
        expect(video.matchScore).toBeLessThanOrEqual(100);
      });
    }
  });

  it('all year values are plausible (≥ 2000)', async () => {
    await allure.feature('Video Catalog');
    await allure.story('ALL_VIDEOS');
    await allure.description('Year values are displayed in the UI; obviously wrong years indicate a data entry error.');
    for (const video of ALL_VIDEOS) {
      await allure.step(`"${video.id}" year ≥ 2000`, () =>
        expect(video.year).toBeGreaterThanOrEqual(2000),
      );
    }
  });
});

// ── findVideo ───────────────────────────────────────────────────────────────

describe('findVideo', () => {
  it('returns undefined for an unknown id', async () => {
    await allure.feature('Video Catalog');
    await allure.story('findVideo');
    await allure.description('findVideo must return undefined when no video matches the given id.');
    await allure.step('"does-not-exist" → undefined', () =>
      expect(findVideo('does-not-exist')).toBeUndefined(),
    );
  });

  it('returns undefined for an empty string', async () => {
    await allure.feature('Video Catalog');
    await allure.story('findVideo');
    await allure.description('An empty string is not a valid video id and must return undefined.');
    await allure.step('"" → undefined', () => expect(findVideo('')).toBeUndefined());
  });

  it('finds every video present in ALL_VIDEOS by its id', async () => {
    await allure.feature('Video Catalog');
    await allure.story('findVideo');
    await allure.description('findVideo must successfully locate every video that exists in ALL_VIDEOS.');
    for (const video of ALL_VIDEOS) {
      await allure.step(`find "${video.id}"`, () => expect(findVideo(video.id)).toBe(video));
    }
  });

  it('returns the exact same object reference (no clone)', async () => {
    await allure.feature('Video Catalog');
    await allure.story('findVideo');
    await allure.description('findVideo should return the same reference stored in ALL_VIDEOS, not a copy, to enable identity comparisons.');
    await allure.step('reference equality', () => {
      const video = ALL_VIDEOS[0];
      expect(findVideo(video.id)).toBe(video);
    });
  });
});

// ── HERO_VIDEO ──────────────────────────────────────────────────────────────

describe('HERO_VIDEO', () => {
  it('is the first video in the "featured" row', async () => {
    await allure.feature('Video Catalog');
    await allure.story('HERO_VIDEO');
    await allure.description('HERO_VIDEO powers the full-width hero banner; it must be the leading video in the "featured" row.');
    await allure.step('identity check vs featured.videos[0]', () => {
      const featured = CATALOG.find(r => r.id === 'featured')!;
      expect(HERO_VIDEO).toBe(featured.videos[0]);
    });
  });

  it('is findable by its id', async () => {
    await allure.feature('Video Catalog');
    await allure.story('HERO_VIDEO');
    await allure.description('HERO_VIDEO must be reachable via findVideo so detail pages work when navigating from the hero banner.');
    await allure.step('findVideo(HERO_VIDEO.id) === HERO_VIDEO', () =>
      expect(findVideo(HERO_VIDEO.id)).toBe(HERO_VIDEO),
    );
  });
});
