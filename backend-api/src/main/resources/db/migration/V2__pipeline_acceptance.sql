-- Build acceptance runs: per-platform test counts + optional 80% quality gate outcome
CREATE TABLE IF NOT EXISTS pipeline_runs (
    id BIGSERIAL PRIMARY KEY,
    run_id VARCHAR(64) NOT NULL UNIQUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finalized_at TIMESTAMP,
    overall_pass_rate DOUBLE PRECISION,
    status VARCHAR(32) NOT NULL,
    github_run_id VARCHAR(64),
    gate_threshold DOUBLE PRECISION NOT NULL DEFAULT 0.8
);

CREATE TABLE IF NOT EXISTS pipeline_platform_results (
    id BIGSERIAL PRIMARY KEY,
    pipeline_run_id BIGINT NOT NULL REFERENCES pipeline_runs(id) ON DELETE CASCADE,
    platform VARCHAR(32) NOT NULL,
    passed INT NOT NULL,
    failed INT NOT NULL,
    skipped INT NOT NULL,
    total INT NOT NULL,
    CONSTRAINT uq_pipeline_platform UNIQUE (pipeline_run_id, platform)
);

CREATE INDEX IF NOT EXISTS idx_pipeline_platform_run ON pipeline_platform_results(pipeline_run_id);
