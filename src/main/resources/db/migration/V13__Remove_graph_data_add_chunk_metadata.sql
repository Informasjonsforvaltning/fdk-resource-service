-- Remove graph_data column and add chunk metadata columns for OAI-PMH support
ALTER TABLE union_graphs
    DROP COLUMN IF EXISTS graph_data,
    ADD COLUMN IF NOT EXISTS chunks_base_path TEXT,
    ADD COLUMN IF NOT EXISTS chunk_count INTEGER NOT NULL DEFAULT 0;

COMMENT ON COLUMN union_graphs.chunks_base_path IS
    'Base path where chunks are stored. Chunks are stored as {basePath}/chunk-{index}.ttl.gz (gzip-compressed Turtle)';
COMMENT ON COLUMN union_graphs.chunk_count IS
    'Total number of chunks created for this union graph (0-based indexing)';
