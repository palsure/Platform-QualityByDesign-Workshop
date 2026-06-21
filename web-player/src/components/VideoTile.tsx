import type { Video } from '../data/catalog';
import { mediaBackgroundStyle } from '../data/mediaBackground';

interface VideoTileProps {
  video: Video;
  onClick: (video: Video) => void;
}

export function VideoTile({ video, onClick }: VideoTileProps) {
  return (
    <button
      className="video-tile"
      onClick={() => onClick(video)}
      type="button"
      aria-label={`Play ${video.title}`}
      data-testid={`tile-${video.id}`}
    >
      {/* Thumbnail */}
      <div className="tile-thumb" style={mediaBackgroundStyle(video, 'poster')}>
        <div className="tile-thumb-overlay" />
        {video.contentType === 'live' && (
          <div className={`tile-live-badge tile-live-badge--${video.liveStatus ?? 'live'}`}>
            {video.liveStatus === 'upcoming' ? 'Upcoming' : 'Live'}
          </div>
        )}
        <div className="tile-play-icon">
          <svg viewBox="0 0 24 24" fill="currentColor" width="32" height="32">
            <path d="M8 5v14l11-7z" />
          </svg>
        </div>
        <div className="tile-genre-badge">{video.genre}</div>
        <div className="tile-title-overlay">{video.title}</div>
      </div>

      {/* Card info */}
      <div className="tile-info">
        <div className="tile-meta-row">
          <span className="tile-match" style={{ color: video.accentColor }}>
            {video.matchScore}% Match
          </span>
          <span className="tile-rating">{video.rating}</span>
          <span className="tile-year">{video.year}</span>
          <span className="tile-duration">{video.seriesLabel ?? video.durationLabel}</span>
        </div>
        <div className="tile-sub-genres">
          {video.subGenres.map(g => (
            <span key={g} className="tile-sub-genre-pill">{g}</span>
          ))}
        </div>
      </div>
    </button>
  );
}
