import { useRef } from 'react';
import type { Video } from '../data/catalog';
import { VideoTile } from './VideoTile';

interface CarouselProps {
  id: string;
  label: string;
  videos: Video[];
  onSelect: (video: Video) => void;
}

export function Carousel({ id, label, videos, onSelect }: CarouselProps) {
  const trackRef = useRef<HTMLDivElement>(null);

  const scroll = (dir: 'left' | 'right') => {
    const track = trackRef.current;
    if (!track) return;
    const amount = track.clientWidth * 0.75;
    track.scrollBy({ left: dir === 'right' ? amount : -amount, behavior: 'smooth' });
  };

  return (
    <section className="carousel" aria-labelledby={`carousel-title-${id}`}>
      <h2 className="carousel-title" id={`carousel-title-${id}`}>{label}</h2>
      <div className="carousel-viewport">
        <button
          className="carousel-arrow carousel-arrow-left"
          onClick={() => scroll('left')}
          type="button"
          aria-label="Scroll left"
        >
          ‹
        </button>

        <div className="carousel-track" ref={trackRef}>
          {videos.map(video => (
            <div className="carousel-item" key={video.id}>
              <VideoTile video={video} onClick={onSelect} />
            </div>
          ))}
        </div>

        <button
          className="carousel-arrow carousel-arrow-right"
          onClick={() => scroll('right')}
          type="button"
          aria-label="Scroll right"
        >
          ›
        </button>
      </div>
    </section>
  );
}
