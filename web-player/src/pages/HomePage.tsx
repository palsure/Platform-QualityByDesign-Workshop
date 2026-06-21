import { useState, useEffect } from 'react';
import { CATALOG, HERO_VIDEO, type Video } from '../data/catalog';
import { mediaBackgroundStyle } from '../data/mediaBackground';
import { Carousel } from '../components/Carousel';

interface HomePageProps {
  onSelect: (video: Video) => void;
  /** Navigate to the detail page AND start playing fullscreen immediately. */
  onPlay:   (video: Video) => void;
}

export function HomePage({ onSelect, onPlay }: HomePageProps) {
  const [heroIdx, setHeroIdx] = useState(0);
  const heroVideos = CATALOG[0].videos;
  const hero = heroVideos[heroIdx];

  // Auto-rotate hero every 8 s
  useEffect(() => {
    const t = setInterval(() => {
      setHeroIdx(i => (i + 1) % heroVideos.length);
    }, 8000);
    return () => clearInterval(t);
  }, [heroVideos.length]);

  // All carousels except the "featured" row (it's the hero)
  const rows = CATALOG.slice(1);

  return (
    <div className="home-page">
      {/* ── Hero banner ──────────────────────────────────────────── */}
      <section
        className="hero"
        style={mediaBackgroundStyle(hero, 'banner')}
        aria-label={`Featured: ${hero.title}`}
      >
        <div className="hero-gradient-overlay" />
        <div className="hero-content">
          <div className="hero-badges">
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
            <span>{hero.durationLabel}</span>
          </div>
          <div className="hero-actions">
            <button
              className="btn-play"
              type="button"
              onClick={() => onPlay(hero)}
            >
              <svg viewBox="0 0 24 24" fill="currentColor" width="20" height="20">
                <path d="M8 5v14l11-7z" />
              </svg>
              Play
            </button>
            <button
              className="btn-info"
              type="button"
              onClick={() => onSelect(hero)}
            >
              <svg viewBox="0 0 24 24" fill="currentColor" width="18" height="18">
                <path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 15h-2v-6h2v6zm0-8h-2V7h2v2z" />
              </svg>
              More Info
            </button>
          </div>
        </div>

        {/* Hero dots */}
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
      </section>

      {/* ── Carousels ────────────────────────────────────────────── */}
      <div className="carousels">
        {/* Quick-access hero row */}
        <Carousel
          id={CATALOG[0].id}
          label={CATALOG[0].label}
          videos={CATALOG[0].videos}
          onSelect={onSelect}
        />
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

// Silence unused import warning
void HERO_VIDEO;
