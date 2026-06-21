-- Create QoE metrics table
CREATE TABLE IF NOT EXISTS qoe_metrics (
    id BIGSERIAL PRIMARY KEY,
    platform VARCHAR(50) NOT NULL,
    video_id VARCHAR(255) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    timestamp TIMESTAMP NOT NULL,
    device_type VARCHAR(100),
    os VARCHAR(100),
    browser VARCHAR(100),
    screen_resolution VARCHAR(50),
    playback_state VARCHAR(50) NOT NULL,
    playback_position DOUBLE PRECISION NOT NULL,
    duration DOUBLE PRECISION NOT NULL,
    total_buffering_time DOUBLE PRECISION,
    startup_time BIGINT,
    current_bitrate BIGINT,
    current_resolution VARCHAR(50),
    bitrate_switches INTEGER,
    error_count INTEGER,
    frames_dropped INTEGER,
    frames_rendered INTEGER,
    network_speed BIGINT,
    playback_quality VARCHAR(50),
    buffering_events TEXT,
    errors TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_platform_video ON qoe_metrics(platform, video_id);
CREATE INDEX idx_session ON qoe_metrics(session_id);
CREATE INDEX idx_timestamp ON qoe_metrics(timestamp);

-- Create videos table
CREATE TABLE IF NOT EXISTS videos (
    id BIGSERIAL PRIMARY KEY,
    video_id VARCHAR(255) NOT NULL UNIQUE,
    title VARCHAR(500) NOT NULL,
    description TEXT,
    thumbnail_url VARCHAR(500),
    hls_manifest_url VARCHAR(500),
    dash_manifest_url VARCHAR(500),
    duration BIGINT,
    resolution VARCHAR(50),
    bitrate BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create validation results table
CREATE TABLE IF NOT EXISTS validation_results (
    id BIGSERIAL PRIMARY KEY,
    video_id VARCHAR(255) NOT NULL,
    platform VARCHAR(50) NOT NULL,
    session_id VARCHAR(255) NOT NULL,
    passed BOOLEAN NOT NULL,
    quality_score DOUBLE PRECISION,
    failure_reason TEXT,
    validation_details TEXT,
    validated_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_validation_video_platform ON validation_results(video_id, platform);
CREATE INDEX idx_validation_session ON validation_results(session_id);
