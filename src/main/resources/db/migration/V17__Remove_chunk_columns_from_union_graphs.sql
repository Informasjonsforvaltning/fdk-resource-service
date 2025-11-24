-- Remove chunk-related columns from union_graphs table
-- Chunks are no longer needed since OAI-PMH now uses resource snapshots directly from the database
ALTER TABLE union_graphs
    DROP COLUMN IF EXISTS chunks_base_path,
    DROP COLUMN IF EXISTS chunk_count;

