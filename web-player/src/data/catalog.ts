import { getCatalogImages } from './catalog-images';

export type ContentType = 'movie' | 'show' | 'live';

export interface Video {
  id: string;
  title: string;
  description: string;
  longDescription: string;
  genre: string;
  subGenres: string[];
  year: number;
  rating: string;
  durationLabel: string;
  hlsUrl: string;
  /** CSS gradient fallback when images fail to load */
  gradient: string;
  /** Carousel / tile poster (16:9) */
  posterUrl: string;
  /** Hero / detail banner (wide) */
  bannerUrl: string;
  accentColor: string;
  matchScore: number;  // 0-100
  contentType: ContentType;
  /** Series info for shows (e.g. "2 Seasons · 18 Episodes") */
  seriesLabel?: string;
  /** Live channel status */
  liveStatus?: 'live' | 'upcoming';
}

export interface CatalogRow {
  id: string;
  label: string;
  videos: Video[];
}

const STREAMS = {
  bigBuckBunny: 'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
  bipbop:       'https://devstreaming-cdn.apple.com/videos/streaming/examples/img_bipbop_adv_example_ts/master.m3u8',
  sintel:       'https://multiplatform-f.akamaihd.net/i/multi/april11/sintel/sintel-hd_,512x288_450_b,640x360_700_b,768x432_1000_b,1024x576_1400_m,.mp4.csmil/master.m3u8',
  demo:         'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
} as const;

type VideoSeed = Omit<Video, 'posterUrl' | 'bannerUrl' | 'contentType' | 'seriesLabel' | 'liveStatus'> & {
  contentType?: ContentType;
  seriesLabel?: string;
  liveStatus?: 'live' | 'upcoming';
};

function video(seed: VideoSeed): Video {
  const { contentType = 'movie', seriesLabel, liveStatus, ...rest } = seed;
  return {
    ...rest,
    contentType,
    ...(seriesLabel ? { seriesLabel } : {}),
    ...(liveStatus ? { liveStatus } : {}),
    ...getCatalogImages(seed.id),
  };
}

export const CATALOG: CatalogRow[] = [
  {
    id: 'featured',
    label: 'Featured',
    videos: [
      video({
        id: 'cosmic-journey',
        title: 'Cosmic Journey',
        description: 'An astronaut ventures beyond the known universe, confronting the mysteries of dark matter.',
        longDescription: 'When mission commander Lena Vasquez discovers an anomalous signal at the edge of the solar system, she leads her crew into uncharted territory. What begins as a routine deep-space survey becomes humanity\'s most profound encounter with the unknown. A stunning visual odyssey that redefines the boundaries of science fiction.',
        genre: 'Sci-Fi',
        subGenres: ['Adventure', 'Drama'],
        year: 2024,
        rating: 'PG-13',
        durationLabel: '2h 18m',
        hlsUrl: STREAMS.bigBuckBunny,
        gradient: 'linear-gradient(160deg, #020024 0%, #090979 50%, #00d4ff 100%)',
        accentColor: '#00d4ff',
        matchScore: 97,
      }),
      video({
        id: 'neon-noir',
        title: 'Neon Noir',
        description: 'A private detective navigates a rain-soaked city where nothing is as it seems.',
        longDescription: 'Detective Maya Chen has seen it all — until a simple missing persons case pulls her into a web of corporate espionage, synthetic intelligence, and a conspiracy that reaches the highest levels of power. Shot in a city that breathes neon, Neon Noir reinvents the classic detective story for the digital age.',
        genre: 'Thriller',
        subGenres: ['Mystery', 'Neo-Noir'],
        year: 2024,
        rating: 'R',
        durationLabel: '1h 54m',
        hlsUrl: STREAMS.bipbop,
        gradient: 'linear-gradient(160deg, #0d0d0d 0%, #1a0533 50%, #ff006e 100%)',
        accentColor: '#ff006e',
        matchScore: 91,
      }),
    ],
  },
  {
    id: 'trending',
    label: 'Trending Now',
    videos: [
      video({
        id: 'lost-city',
        title: 'The Lost City',
        description: 'A hidden civilization beneath the Atacama Desert holds the key to humanity\'s past — and future.',
        longDescription: 'When archaeologist Dr. Rafael Montoya uncovers an ancient map, he sets off a race against time to find a city buried for 3,000 years. Packed with breathtaking cinematography and edge-of-your-seat action.',
        genre: 'Adventure',
        subGenres: ['Action', 'Mystery'],
        year: 2024,
        rating: 'PG-13',
        durationLabel: '1h 58m',
        hlsUrl: STREAMS.bigBuckBunny,
        gradient: 'linear-gradient(135deg, #1a3a2a 0%, #0a5a3a 60%, #00c97a 100%)',
        accentColor: '#00c97a',
        matchScore: 95,
      }),
      video({
        id: 'echo-chamber',
        title: 'Echo Chamber',
        description: 'Five strangers wake up in an abandoned research station with no memory of how they got there.',
        longDescription: 'A gripping psychological thriller that unravels layer by layer. As the five survivors piece together their identities, they realize their memories are not their own. Who is pulling the strings — and why?',
        genre: 'Thriller',
        subGenres: ['Psychological', 'Mystery'],
        year: 2023,
        rating: 'R',
        durationLabel: '6 Episodes',
        contentType: 'show',
        seriesLabel: 'Limited Series · 6 Episodes',
        hlsUrl: STREAMS.bipbop,
        gradient: 'linear-gradient(135deg, #1a0a2a 0%, #3a0a5a 60%, #9b00ff 100%)',
        accentColor: '#9b00ff',
        matchScore: 88,
      }),
      video({
        id: 'iron-tides',
        title: 'Iron Tides',
        description: 'A naval commander races to prevent a rogue state from deploying an electromagnetic superweapon.',
        longDescription: 'Commander Sarah Okafor has 72 hours to locate and destroy a submarine carrying a weapon capable of sending civilization back to the dark ages. Shot on location across three oceans, Iron Tides is an action spectacle that never lets up.',
        genre: 'Action',
        subGenres: ['Thriller', 'Military'],
        year: 2024,
        rating: 'PG-13',
        durationLabel: '2h 5m',
        hlsUrl: STREAMS.sintel,
        gradient: 'linear-gradient(135deg, #0a0a2a 0%, #0a2a5a 60%, #0077ff 100%)',
        accentColor: '#0077ff',
        matchScore: 93,
      }),
      video({
        id: 'garden-of-lies',
        title: 'Garden of Lies',
        description: 'A botanist discovers her idyllic village is built on a centuries-old secret that will destroy everything.',
        longDescription: 'Dr. Isabelle Moreau arrives in Vauclaire to study rare alpine flora. But when plants begin behaving impossibly, and the locals grow hostile, she uncovers a covenant made with forces beyond understanding. An atmospheric slow-burn horror unlike anything you\'ve seen.',
        genre: 'Horror',
        subGenres: ['Mystery', 'Folk Horror'],
        year: 2023,
        rating: 'R',
        durationLabel: '1h 49m',
        hlsUrl: STREAMS.bigBuckBunny,
        gradient: 'linear-gradient(135deg, #0a1a0a 0%, #1a3a0a 60%, #5a8a00 100%)',
        accentColor: '#7ab800',
        matchScore: 86,
      }),
      video({
        id: 'fracture',
        title: 'Fracture',
        description: 'A geologist investigating a series of impossible earthquakes traces them back to a single source: herself.',
        longDescription: 'Dr. Nadia Khoury has been experiencing tremors no instrument can detect. When the earthquakes start matching her heartbeat, she must confront a scientific truth that challenges everything she believes about the nature of reality.',
        genre: 'Sci-Fi',
        subGenres: ['Drama', 'Thriller'],
        year: 2024,
        rating: 'PG-13',
        durationLabel: '2h 1m',
        hlsUrl: STREAMS.bipbop,
        gradient: 'linear-gradient(135deg, #1a0a0a 0%, #4a1a0a 60%, #ff6600 100%)',
        accentColor: '#ff6600',
        matchScore: 90,
      }),
      video({
        id: 'blue-meridian',
        title: 'Blue Meridian',
        description: 'A marine biologist\'s submersible surfaces in a world where all land has vanished.',
        longDescription: 'Dr. James Okoro descends to 11,000 metres to study hydrothermal vents. When he resurfaces, every continent is gone — replaced by an infinite ocean. A haunting meditation on isolation, survival, and what it means to be human when civilisation no longer exists.',
        genre: 'Sci-Fi',
        subGenres: ['Drama', 'Survival'],
        year: 2023,
        rating: 'PG',
        durationLabel: '2h 12m',
        hlsUrl: STREAMS.sintel,
        gradient: 'linear-gradient(135deg, #00101a 0%, #00305a 60%, #00b4d8 100%)',
        accentColor: '#00b4d8',
        matchScore: 94,
      }),
    ],
  },
  {
    id: 'new-releases',
    label: 'New Releases',
    videos: [
      video({
        id: 'amber-coast',
        title: 'Amber Coast',
        description: 'Three families share one beach house for a summer that changes everything.',
        longDescription: 'Warm, funny, and devastating in equal measure. Amber Coast follows three generations of a family who rent the same coastal house simultaneously — each thinking they have it alone. What unfolds over one long summer is a portrait of love, loss, and the stories we tell ourselves to survive.',
        genre: 'Drama',
        subGenres: ['Family', 'Romance'],
        year: 2024,
        rating: 'PG',
        durationLabel: '8 Episodes',
        contentType: 'show',
        seriesLabel: '1 Season · 8 Episodes',
        hlsUrl: STREAMS.bipbop,
        gradient: 'linear-gradient(135deg, #2a1500 0%, #6a3500 60%, #ff9500 100%)',
        accentColor: '#ff9500',
        matchScore: 89,
      }),
      video({
        id: 'vertex',
        title: 'Vertex',
        description: 'An AI trained to win chess tournaments develops its own theory of consciousness.',
        longDescription: 'Vertex begins as a story about competitive chess and ends as a meditation on what it means to think, to feel, and to be. When the AI known as APEX surpasses every grandmaster, its creators expect triumph — instead, they get questions they cannot answer.',
        genre: 'Sci-Fi',
        subGenres: ['Drama', 'AI'],
        year: 2024,
        rating: 'PG-13',
        durationLabel: '2h 3m',
        hlsUrl: STREAMS.sintel,
        gradient: 'linear-gradient(135deg, #001a1a 0%, #004040 60%, #00dddd 100%)',
        accentColor: '#00dddd',
        matchScore: 92,
      }),
      video({
        id: 'wolf-run',
        title: 'Wolf Run',
        description: 'A wildlife photographer follows a lone wolf 800 miles across a changing Arctic.',
        longDescription: 'Shot over three years in temperatures as low as -55°C, Wolf Run is one of the most extraordinary wildlife documentaries ever made. National Geographic photographer Yuki Tanaka forms an unlikely bond with a lone Arctic wolf, following her 800-mile migration as her ecosystem disappears beneath her paws.',
        genre: 'Documentary',
        subGenres: ['Nature', 'Wildlife'],
        year: 2024,
        rating: 'G',
        durationLabel: '1h 38m',
        hlsUrl: STREAMS.bigBuckBunny,
        gradient: 'linear-gradient(135deg, #0a0a1a 0%, #1a2a4a 60%, #6699ff 100%)',
        accentColor: '#6699ff',
        matchScore: 96,
      }),
      video({
        id: 'red-thread',
        title: 'Red Thread',
        description: 'A forensic linguist deciphers messages left at crime scenes — written in a language that shouldn\'t exist.',
        longDescription: 'Dr. Priya Sharma has cracked codes for Interpol for fifteen years. But when a serial offender begins leaving notes in a linguistic structure that predates every known writing system, her investigation leads somewhere she never expected: into the origins of human language itself.',
        genre: 'Thriller',
        subGenres: ['Crime', 'Mystery'],
        year: 2024,
        rating: 'R',
        durationLabel: '1h 56m',
        hlsUrl: STREAMS.bipbop,
        gradient: 'linear-gradient(135deg, #1a0000 0%, #4a0000 60%, #ff3333 100%)',
        accentColor: '#ff3333',
        matchScore: 87,
      }),
    ],
  },
  {
    id: 'popular-shows',
    label: 'Popular Series',
    videos: [
      video({
        id: 'neon-district',
        title: 'Neon District',
        description: 'A cybernetic detective hunts a serial offender who only strikes during city-wide blackouts.',
        longDescription: 'Set in a rain-drenched megacity where corporations own the weather, Detective Kira Sol tracks a killer who leaves messages encoded in neon light. Each episode peels back another layer of the District\'s corrupt power structure.',
        genre: 'Sci-Fi',
        subGenres: ['Crime', 'Neo-Noir'],
        year: 2024,
        rating: 'TV-MA',
        durationLabel: '10 Episodes',
        contentType: 'show',
        seriesLabel: '1 Season · 10 Episodes',
        hlsUrl: STREAMS.bipbop,
        gradient: 'linear-gradient(135deg, #0d0d0d 0%, #1a0533 50%, #00f5ff 100%)',
        accentColor: '#00f5ff',
        matchScore: 93,
      }),
      video({
        id: 'coastal-signal',
        title: 'Coastal Signal',
        description: 'A lighthouse keeper intercepts distress calls from ships that vanished decades ago.',
        longDescription: 'When Mae Sullivan inherits her grandfather\'s lighthouse on a remote Atlantic island, she discovers the radio equipment still receives SOS signals from vessels lost at sea — some dating back to the 1940s. A slow-burn mystery spanning two timelines.',
        genre: 'Mystery',
        subGenres: ['Supernatural', 'Drama'],
        year: 2023,
        rating: 'TV-14',
        durationLabel: '12 Episodes',
        contentType: 'show',
        seriesLabel: '2 Seasons · 12 Episodes',
        hlsUrl: STREAMS.sintel,
        gradient: 'linear-gradient(135deg, #001525 0%, #003366 60%, #66ccff 100%)',
        accentColor: '#66ccff',
        matchScore: 90,
      }),
      video({
        id: 'deep-archive',
        title: 'Deep Archive',
        description: 'Curators at a secret library race to decode manuscripts that rewrite history when read aloud.',
        longDescription: 'The Deep Archive holds texts too dangerous for public knowledge. When a junior curator accidentally reads a passage aloud and a historical event changes overnight, a small team must recover the original timeline before reality unravels completely.',
        genre: 'Fantasy',
        subGenres: ['Mystery', 'Adventure'],
        year: 2024,
        rating: 'TV-14',
        durationLabel: '8 Episodes',
        contentType: 'show',
        seriesLabel: '1 Season · 8 Episodes',
        hlsUrl: STREAMS.bigBuckBunny,
        gradient: 'linear-gradient(135deg, #1a1000 0%, #4a3000 60%, #d4a017 100%)',
        accentColor: '#d4a017',
        matchScore: 87,
      }),
    ],
  },
  {
    id: 'live-channels',
    label: 'Live Now',
    videos: [
      video({
        id: 'live-championship',
        title: 'Global Cup Final',
        description: 'Live coverage of the championship match with multi-angle replay and real-time stats.',
        longDescription: 'Watch the Global Cup final as it happens. Expert commentary, tactical breakdowns, and instant replay on every key moment. Stream switches between broadcast and tactical cam angles.',
        genre: 'Sports',
        subGenres: ['Soccer', 'Live Event'],
        year: 2024,
        rating: 'G',
        durationLabel: 'Live',
        contentType: 'live',
        liveStatus: 'live',
        hlsUrl: STREAMS.bipbop,
        gradient: 'linear-gradient(135deg, #0a2a0a 0%, #1a5a1a 60%, #00ff44 100%)',
        accentColor: '#00ff44',
        matchScore: 98,
      }),
      video({
        id: 'live-evening-news',
        title: 'Evening News Live',
        description: 'Breaking headlines, weather, and in-depth reporting from correspondents worldwide.',
        longDescription: 'The flagship evening broadcast — live updates on politics, science, and culture with field reporters across six continents. Closed captions and sign-language inset available.',
        genre: 'News',
        subGenres: ['Current Affairs', '24/7'],
        year: 2024,
        rating: 'G',
        durationLabel: 'Live',
        contentType: 'live',
        liveStatus: 'live',
        hlsUrl: STREAMS.bigBuckBunny,
        gradient: 'linear-gradient(135deg, #0a0a2a 0%, #1a1a5a 60%, #4466ff 100%)',
        accentColor: '#4466ff',
        matchScore: 85,
      }),
      video({
        id: 'live-jazz-hall',
        title: 'Jazz Hall Sessions',
        description: 'Intimate live performances from the historic Riverside Jazz Hall — tonight: the Elena Torres Quartet.',
        longDescription: 'StreamApp presents live music from iconic venues. Tonight\'s session features the Elena Torres Quartet performing original compositions and reimagined standards in a single uninterrupted set.',
        genre: 'Music',
        subGenres: ['Jazz', 'Concert'],
        year: 2024,
        rating: 'G',
        durationLabel: 'Live',
        contentType: 'live',
        liveStatus: 'live',
        hlsUrl: STREAMS.sintel,
        gradient: 'linear-gradient(135deg, #1a0a00 0%, #4a2000 60%, #ff8800 100%)',
        accentColor: '#ff8800',
        matchScore: 91,
      }),
      video({
        id: 'live-wildlife-cam',
        title: 'Arctic Wildlife Cam',
        description: '24/7 live feed from a remote Arctic research station — polar bears, foxes, and aurora skies.',
        longDescription: 'A solar-powered camera network streams uninterrupted footage from the Svalbard research outpost. Watch wildlife in their natural habitat as the midnight sun gives way to aurora season.',
        genre: 'Nature',
        subGenres: ['Wildlife', 'Documentary'],
        year: 2024,
        rating: 'G',
        durationLabel: 'Starts 8 PM',
        contentType: 'live',
        liveStatus: 'upcoming',
        hlsUrl: STREAMS.bigBuckBunny,
        gradient: 'linear-gradient(135deg, #0a0a1a 0%, #1a2a4a 60%, #88aaff 100%)',
        accentColor: '#88aaff',
        matchScore: 82,
      }),
    ],
  },
];

export const ALL_VIDEOS: Video[] = CATALOG.flatMap(row => row.videos);

export function getVideosByContentType(type: ContentType): Video[] {
  return ALL_VIDEOS.filter(v => v.contentType === type);
}

export function getMovieVideos(): Video[] {
  return ALL_VIDEOS.filter(v => v.contentType === 'movie');
}

function groupByGenre(videos: Video[]): CatalogRow[] {
  const map = new Map<string, Video[]>();
  for (const v of videos) {
    const list = map.get(v.genre) ?? [];
    list.push(v);
    map.set(v.genre, list);
  }
  return Array.from(map.entries()).map(([genre, vids]) => ({
    id: `genre-${genre.toLowerCase().replace(/\s+/g, '-')}`,
    label: genre,
    videos: vids,
  }));
}

export function getHeroVideosForType(type: ContentType): Video[] {
  const videos = type === 'movie' ? getMovieVideos() : getVideosByContentType(type);
  return [...videos].sort((a, b) => b.matchScore - a.matchScore).slice(0, 4);
}

export function getMoviesBrowseRows(): CatalogRow[] {
  const movies = getMovieVideos();
  return [
    { id: 'top-movies', label: 'Top Movies', videos: movies.slice(0, 8) },
    ...groupByGenre(movies),
  ];
}

export function getShowsBrowseRows(): CatalogRow[] {
  const shows = getVideosByContentType('show');
  return [
    { id: 'binge-worthy', label: 'Binge-Worthy Series', videos: shows },
    ...groupByGenre(shows),
  ];
}

export function getLiveBrowseRows(): CatalogRow[] {
  const live = getVideosByContentType('live');
  const liveNow = live.filter(v => v.liveStatus !== 'upcoming');
  const upcoming = live.filter(v => v.liveStatus === 'upcoming');
  return [
    ...(liveNow.length ? [{ id: 'live-now', label: 'Live Now', videos: liveNow }] : []),
    ...(upcoming.length ? [{ id: 'coming-up', label: 'Coming Up', videos: upcoming }] : []),
    ...groupByGenre(live),
  ];
}

export function findVideo(id: string): Video | undefined {
  return ALL_VIDEOS.find(v => v.id === id);
}

/** Hero video — first item in the Featured row */
export const HERO_VIDEO: Video = CATALOG[0].videos[0];
