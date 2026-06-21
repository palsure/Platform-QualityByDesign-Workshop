-- Widen platform columns to accommodate longer keys (e.g. androidphone, desktop_windows)
ALTER TABLE qoe_metrics
    ALTER COLUMN platform TYPE VARCHAR(64);

ALTER TABLE validation_results
    ALTER COLUMN platform TYPE VARCHAR(64);

ALTER TABLE pipeline_platform_results
    ALTER COLUMN platform TYPE VARCHAR(64);
