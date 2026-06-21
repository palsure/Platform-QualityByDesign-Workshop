/** Curated poster (16:9) and banner (wide) images per catalog title. */
const unsplash = (id: string, w: number, h: number) =>
  `https://images.unsplash.com/${id}?auto=format&fit=crop&w=${w}&h=${h}&q=80`;

const local = (name: string, variant: 'poster' | 'banner') =>
  `/images/catalog/${name}-${variant}.jpg`;

export const CATALOG_IMAGES: Record<string, { poster: string; banner: string }> = {
  'cosmic-journey': {
    poster: local('cosmic-journey', 'poster'),
    banner: local('cosmic-journey', 'banner'),
  },
  'neon-noir': {
    poster: unsplash('photo-1514565131-fce0801e5785', 480, 270),
    banner: unsplash('photo-1514565131-fce0801e5785', 1920, 1080),
  },
  'lost-city': {
    poster: unsplash('photo-1506905925346-21bda4d32df4', 480, 270),
    banner: unsplash('photo-1506905925346-21bda4d32df4', 1920, 1080),
  },
  'echo-chamber': {
    poster: unsplash('photo-1519681393784-d120267933ba', 480, 270),
    banner: unsplash('photo-1519681393784-d120267933ba', 1920, 1080),
  },
  'iron-tides': {
    poster: unsplash('photo-1544551763-46a013bb70d5', 480, 270),
    banner: unsplash('photo-1544551763-46a013bb70d5', 1920, 1080),
  },
  'garden-of-lies': {
    poster: unsplash('photo-1441974231531-c6227db76b6e', 480, 270),
    banner: unsplash('photo-1441974231531-c6227db76b6e', 1920, 1080),
  },
  'fracture': {
    poster: unsplash('photo-1451187580459-43490279c0fa', 480, 270),
    banner: unsplash('photo-1451187580459-43490279c0fa', 1920, 1080),
  },
  'blue-meridian': {
    poster: unsplash('photo-1559827260-dc66d52bef19', 480, 270),
    banner: unsplash('photo-1559827260-dc66d52bef19', 1920, 1080),
  },
  'amber-coast': {
    poster: unsplash('photo-1507525428034-b723cf961d3e', 480, 270),
    banner: unsplash('photo-1507525428034-b723cf961d3e', 1920, 1080),
  },
  'vertex': {
    poster: unsplash('photo-1620712943543-bcc4688e7485', 480, 270),
    banner: unsplash('photo-1620712943543-bcc4688e7485', 1920, 1080),
  },
  'wolf-run': {
    poster: local('wolf-run', 'poster'),
    banner: local('wolf-run', 'banner'),
  },
  'red-thread': {
    poster: unsplash('photo-1481627834876-b7833e8f5570', 480, 270),
    banner: unsplash('photo-1481627834876-b7833e8f5570', 1920, 1080),
  },
  'neon-district': {
    poster: unsplash('photo-1514565131-fce0801e5785', 480, 270),
    banner: unsplash('photo-1514565131-fce0801e5785', 1920, 1080),
  },
  'coastal-signal': {
    poster: unsplash('photo-1506905925346-21bda4d32df4', 480, 270),
    banner: unsplash('photo-1506905925346-21bda4d32df4', 1920, 1080),
  },
  'deep-archive': {
    poster: unsplash('photo-1481627834876-b7833e8f5570', 480, 270),
    banner: unsplash('photo-1481627834876-b7833e8f5570', 1920, 1080),
  },
  'live-championship': {
    poster: unsplash('photo-1574629810360-7efbbe195018', 480, 270),
    banner: unsplash('photo-1574629810360-7efbbe195018', 1920, 1080),
  },
  'live-evening-news': {
    poster: unsplash('photo-1504711434969-e33886168f5c', 480, 270),
    banner: unsplash('photo-1504711434969-e33886168f5c', 1920, 1080),
  },
  'live-jazz-hall': {
    poster: unsplash('photo-1415201364774-f6f0ff0a028', 480, 270),
    banner: unsplash('photo-1415201364774-f6f0ff0a028', 1920, 1080),
  },
  'live-wildlife-cam': {
    poster: local('wolf-run', 'poster'),
    banner: local('wolf-run', 'banner'),
  },
};

export function getCatalogImages(id: string): { posterUrl: string; bannerUrl: string } {
  const img = CATALOG_IMAGES[id];
  if (!img) return { posterUrl: '', bannerUrl: '' };
  return { posterUrl: img.poster, bannerUrl: img.banner };
}
