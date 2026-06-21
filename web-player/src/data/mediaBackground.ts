import type { CSSProperties } from 'react';

export function mediaBackgroundStyle(
  video: { gradient: string; posterUrl?: string; bannerUrl?: string },
  variant: 'poster' | 'banner',
): CSSProperties {
  const url = variant === 'banner' ? video.bannerUrl : video.posterUrl;
  if (url) {
    return {
      backgroundImage: `url("${url}")`,
      backgroundSize: 'cover',
      backgroundPosition: 'center',
      backgroundColor: '#111',
    };
  }
  return { background: video.gradient };
}

export { getCatalogImages, CATALOG_IMAGES } from './catalog-images';
