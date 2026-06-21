-- Add catalog fields to the videos table
ALTER TABLE videos
    ADD COLUMN IF NOT EXISTS category VARCHAR(100),
    ADD COLUMN IF NOT EXISTS genre    VARCHAR(100),
    ADD COLUMN IF NOT EXISTS active   BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_videos_category ON videos(category);
CREATE INDEX IF NOT EXISTS idx_videos_active   ON videos(active);

-- ── Seed demo videos ─────────────────────────────────────────────────────────
-- Using publicly available HLS / DASH test streams from industry standard sources.

INSERT INTO videos (video_id, title, description, thumbnail_url,
                    hls_manifest_url, dash_manifest_url,
                    duration, resolution, bitrate, category, genre, active,
                    created_at, updated_at)
VALUES
    ('vid-bbb-001',
     'Big Buck Bunny',
     'A large and lovable rabbit deals with bullying from a trio of rodents. Showcase for adaptive bitrate (ABR) with 1080p HLS.',
     'https://upload.wikimedia.org/wikipedia/commons/thumb/c/c5/Big_buck_bunny_poster_big.jpg/800px-Big_buck_bunny_poster_big.jpg',
     'https://test-streams.mux.dev/x36xhzz/x36xhzz.m3u8',
     'https://dash.akamaized.net/akamai/bbb_30fps/bbb_30fps.mpd',
     596, '1920x1080', 5000000, 'movie', 'animation', TRUE,
     NOW(), NOW()),

    ('vid-tears-002',
     'Tears of Steel',
     'A group of warriors and scientists take refuge in an Amsterdam café. Blender open-movie — great for QoE testing with high-motion scenes.',
     'https://upload.wikimedia.org/wikipedia/commons/thumb/3/3c/Tears_of_Steel_poster.jpg/800px-Tears_of_Steel_poster.jpg',
     'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.m3u8',
     'https://demo.unified-streaming.com/k8s/features/stable/video/tears-of-steel/tears-of-steel.ism/.mpd',
     734, '1920x1080', 4500000, 'movie', 'sci-fi', TRUE,
     NOW(), NOW()),

    ('vid-cosmos-003',
     'Cosmos Laundromat (Preview)',
     'On a desolate island, a traveller on the brink of ending it all encounters a peculiar machine.',
     'https://upload.wikimedia.org/wikipedia/commons/thumb/5/5b/Cosmos_Laundromat_poster.jpg/800px-Cosmos_Laundromat_poster.jpg',
     'https://demo.unified-streaming.com/k8s/features/stable/video/cosmos-laundromat/cosmos-laundromat.ism/.m3u8',
     'https://demo.unified-streaming.com/k8s/features/stable/video/cosmos-laundromat/cosmos-laundromat.ism/.mpd',
     839, '2048x858', 6000000, 'movie', 'drama', TRUE,
     NOW(), NOW()),

    ('vid-sintel-004',
     'Sintel',
     'A wanderer searching for her lost companion encounters danger in a frozen landscape.',
     'https://upload.wikimedia.org/wikipedia/commons/thumb/f/f9/Sintel_movie_4sshot.jpg/800px-Sintel_movie_4sshot.jpg',
     'https://bitdash-a.akamaihd.net/content/sintel/hls/playlist.m3u8',
     'https://bitdash-a.akamaihd.net/content/sintel/sintel.mpd',
     888, '1280x544', 3500000, 'movie', 'animation', TRUE,
     NOW(), NOW()),

    ('vid-live-001',
     'Live News Stream (Demo)',
     'Simulated 24/7 live HLS stream for testing low-latency live playback across platforms.',
     NULL,
     'https://cph-p2p-msl.akamaized.net/hls/live/2000341/test/master.m3u8',
     NULL,
     NULL, '1280x720', 2000000, 'live', 'news', TRUE,
     NOW(), NOW()),

    ('vid-sports-001',
     'Sports Highlights Reel',
     'High-action sport clips — ideal for bitrate-switching and startup-time benchmarks.',
     NULL,
     'https://rbmn-live.akamaized.net/hls/live/590964/BoRB-AT/master.m3u8',
     NULL,
     NULL, '1920x1080', 8000000, 'sports', 'highlights', TRUE,
     NOW(), NOW()),

    ('vid-show-001',
     'Elephant Dream',
     'Two strange characters explore an abstract mechanical world. First Blender open movie.',
     'https://upload.wikimedia.org/wikipedia/commons/thumb/e/e8/Elephant_Dream_s5_both.jpg/800px-Elephant_Dream_s5_both.jpg',
     'https://playertest.longtailvideo.com/adaptive/elephants_dream_playlist.m3u8',
     NULL,
     654, '1280x720', 2500000, 'show', 'animation', TRUE,
     NOW(), NOW())

ON CONFLICT (video_id) DO NOTHING;
