import { useState, useEffect } from 'react';
import type { CatalogRow, Video } from '../data/catalog';
import { mediaBackgroundStyle } from '../data/mediaBackground';
import { Carousel } from '../components/Carousel';

interface BrowsePageProps {
  title: string;
  subtitle: string;
  heroVideos: Video[];
  rows: CatalogRow[];
  onSelect: (video: Video) => void;
  onPlay: (video: Video) => void;
}

export function BrowsePage({
  title,
  subtitle,
  heroVideos,
  rows,
  onSelect,
  onPlay,
}: BrowsePageProps) {
  const [heroIdx, setHeroIdx] = useState(0);
  const hero = heroVideos[heroIdx] ?? heroVideos[0];

  useEffect(() => {
    if (heroVideos.length <= 1) return;
    const t = setInterval(() => {
      setHeroIdx(i => (i + 1) % heroVideos.length);
    }, 8000);
    return () => clearInterval(t);
  }, [heroVideos.length]);

  if (!hero) {
    return (
      <div className="browse-page">
        <div className="browse-empty">
          <h1>{title}</h1>
          <p>No titles available yet.</p>
        </div>
      </div>
    );
  }

  return (
    <div className="browse-page">
      <section
        className="hero"
        style={mediaBackgroundStyle(hero, 'banner')}
        aria-label={`Featured: ${hero.title}`}
      >
        <div className="hero-gradient-overlay" />
        <div className="hero-content">
          <p className="browse-eyebrow">{title}</p>
          <div className="hero-badges">
            {hero.contentType === 'live' && (
              <span className={`hero-live-badge hero-live-badge--${hero.liveStatus ?? 'live'}`}>
                {hero.liveStatus === 'upcoming' ? 'Upcoming' : 'Live'}
              </span>
            )}
            <span className="hero-genre-badge">{hero.genre}</span>
            {hero.subGenres.map(g => (
              <span key={g} className="hero-sub-badge">{g}</span>
            ))}
          </div>
          <h1 className="hero-title">{hero.title}</h1>
          <p className="hero-description">{hero.description}</p>
          <div className="hero-meta">
            <span className="hero-match" style={{ color: hero.accentColor }}>
              {hero.matchScore}% Match
            </span>
            <span className="hero-rating">{hero.rating}</span>
            <span>{hero.year}</span>
            <span>{hero.seriesLabel ?? hero.durationLabel}</span>
          </div>
          <div className="hero-actions">
            <button className="btn-play" type="button" onClick={() => onPlay(hero)}>
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                <path d="M8 5v14l11-7z" />
              </svg>
              {hero.contentType === 'live' && hero.liveStatus !== 'upcoming' ? 'Watch Live' : 'Play'}
            </button>
            <button className="btn-info" type="button" onClick={() => onSelect(hero)}>
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" />
              </svg>
              More Info
            </button>
          </div>
        </div>

        {heroVideos.length > 1 && (
          <div className="hero-dots">
            {heroVideos.map((v, i) => (
              <button
                key={v.id}
                className={`hero-dot${i === heroIdx ? ' hero-dot-active' : ''}`}
                onClick={() => setHeroIdx(i)}
                type="button"
                aria-label={`Feature ${v.title}`}
              />
            ))}
          </div>
        )}
      </section>

      <div className="browse-intro">
        <h2>{title}</h2>
        <p>{subtitle}</p>
      </div>

      <div className="carousels">
        {rows.map(row => (
          <Carousel
            key={row.id}
            id={row.id}
            label={row.label}
            videos={row.videos}
            onSelect={onSelect}
          />
        ))}
      </div>
    </div>
  );
}
