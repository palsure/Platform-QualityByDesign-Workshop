import { useEffect, useRef, useState } from 'react';
import type { Video } from '../data/catalog';
import { mediaBackgroundStyle } from '../data/mediaBackground';
import { VideoPlayer } from '../components/VideoPlayer';

interface DetailPageProps {
  video: Video;
  onBack: () => void;
  autoPlay?: boolean;
}

export function DetailPage({ video, onBack, autoPlay = false }: DetailPageProps) {
  const [playing, setPlaying] = useState(autoPlay);
  const overlayRef = useRef<HTMLDivElement>(null);

  // ── Fullscreen helpers ──────────────────────────────────────────────────────

  const openFullscreen = () => {
    const overlay = overlayRef.current;
    if (!overlay) return;

    // Make the overlay visible synchronously BEFORE calling requestFullscreen.
    // requestFullscreen() will throw/reject if the target element is hidden (display: none).
    overlay.style.display = 'flex';

    // Call requestFullscreen() here — still inside the user-gesture call stack.
    overlay.requestFullscreen?.().catch(() => {
      // Browser denied fullscreen (e.g. headless / iframe restriction).
      // The overlay remains as a full-page modal so the video still plays.
    });

    setPlaying(true);
  };

  const closePlayer = () => {
    // Always hide the overlay and stop playback immediately —
    // don't wait for the async fullscreen exit animation.
    setPlaying(false);
    if (overlayRef.current) overlayRef.current.style.display = 'none';
    // Exit OS-level fullscreen if active (fire-and-forget)
    if (document.fullscreenElement) {
      document.exitFullscreen().catch(() => {});
    }
  };

  // When the user presses ESC or the browser exits fullscreen for any reason,
  // hide the overlay and stop the player.
  useEffect(() => {
    const onFsChange = () => {
      if (!document.fullscreenElement && overlayRef.current) {
        overlayRef.current.style.display = 'none';
        setPlaying(false);
      }
    };
    document.addEventListener('fullscreenchange', onFsChange);
    return () => document.removeEventListener('fullscreenchange', onFsChange);
  }, []);

  // ── Render ──────────────────────────────────────────────────────────────────

  return (
    <div className="detail-page">

      {/* ── Hero backdrop ──────────────────────────────────────────────────── */}
      <div className="detail-hero" style={mediaBackgroundStyle(video, 'banner')}>
        <div className="detail-hero-overlay" />

        <button className="detail-back-btn" onClick={onBack} type="button" aria-label="Back">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.5"
               width="22" height="22">
            <polyline points="15 18 9 12 15 6" />
          </svg>
          Back
        </button>

        <div className="detail-hero-content">
          <div className="detail-badges">
            {video.contentType === 'live' && (
              <span className={`hero-live-badge hero-live-badge--${video.liveStatus ?? 'live'}`}>
                {video.liveStatus === 'upcoming' ? 'Upcoming' : 'Live'}
              </span>
            )}
            <span className="detail-genre-badge">{video.genre}</span>
            {video.subGenres.map(g => (
              <span key={g} className="detail-sub-badge">{g}</span>
            ))}
          </div>

          <h1 className="detail-title">{video.title}</h1>
          <p className="detail-description">{video.longDescription}</p>

          <div className="detail-meta">
            <span className="detail-match" style={{ color: video.accentColor }}>
              {video.matchScore}% Match
            </span>
            <span className="detail-rating-chip">{video.rating}</span>
            <span>{video.year}</span>
            <span>{video.seriesLabel ?? video.durationLabel}</span>
          </div>

          <div className="detail-actions">
            <button
              className="btn-play btn-play-lg"
              type="button"
              onClick={openFullscreen}
              data-testid="detail-play-btn"
            >
              <svg viewBox="0 0 24 24" fill="currentColor" width="22" height="22">
                <path d="M8 5v14l11-7z" />
              </svg>
              Play
            </button>
            <button className="btn-watchlist" type="button">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                   width="20" height="20">
                <line x1="12" y1="5" x2="12" y2="19" />
                <line x1="5" y1="12" x2="19" y2="12" />
              </svg>
              Watchlist
            </button>
          </div>
        </div>
      </div>

      {/* ── Info panels ─────────────────────────────────────────────────────── */}
      <div className="detail-panels">
        <div className="detail-panel">
          <h3>About <em>{video.title}</em></h3>
          <dl className="detail-facts">
            <div><dt>Genre</dt>    <dd>{[video.genre, ...video.subGenres].join(', ')}</dd></div>
            <div><dt>Year</dt>     <dd>{video.year}</dd></div>
            <div><dt>Rating</dt>   <dd>{video.rating}</dd></div>
            <div><dt>Duration</dt> <dd>{video.seriesLabel ?? video.durationLabel}</dd></div>
          </dl>
        </div>
      </div>

      {/* ── Fullscreen player overlay ────────────────────────────────────────
           Always in the DOM so requestFullscreen() can target it.
           Visibility is toggled via overlay.style.display (not React state)
           so the DOM mutation is synchronous inside the user-gesture handler. */}
      <div
        ref={overlayRef}
        className="player-overlay"
        style={{ display: autoPlay ? 'flex' : 'none' }}
      >
        {/* VideoPlayer FIRST so the close button (rendered after) sits
            above it in the DOM stacking order — critical because <video>
            renders on a GPU compositing layer and ignores z-index otherwise */}
        {playing && (
          <VideoPlayer
            key={video.id}
            videoUrl={video.hlsUrl}
            videoId={video.id}
            autoPlayForE2E={true}
          />
        )}

        <button
          className="player-overlay-close"
          type="button"
          onClick={closePlayer}
          aria-label="Close player"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor"
               strokeWidth="2.5" width="22" height="22">
            <line x1="18" y1="6"  x2="6"  y2="18" />
            <line x1="6"  y1="6"  x2="18" y2="18" />
          </svg>
        </button>
      </div>
    </div>
  );
}
