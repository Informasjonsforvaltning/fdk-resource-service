-- Add processing_state column for incremental batch processing
-- This allows resuming processing after each batch, reducing memory consumption
ALTER TABLE union_graphs
    ADD COLUMN IF NOT EXISTS processing_state JSONB;

COMMENT ON COLUMN union_graphs.processing_state IS
    'Processing state for incremental batch processing. Tracks current resource type, offset, and progress.';



