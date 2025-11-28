-- Change graph_json_ld column to graph_data (TEXT) and add format configuration fields
ALTER TABLE union_graphs 
    ADD COLUMN graph_data TEXT,
    ADD COLUMN graph_format VARCHAR(50) DEFAULT 'JSON_LD',
    ADD COLUMN graph_style VARCHAR(50) DEFAULT 'PRETTY',
    ADD COLUMN graph_expand_uris BOOLEAN DEFAULT true;

-- Migrate existing data: convert JSONB to JSON string
UPDATE union_graphs 
SET graph_data = graph_json_ld::text
WHERE graph_json_ld IS NOT NULL;

-- Drop the GIN index on graph_json_ld
DROP INDEX IF EXISTS idx_union_graphs_graph_json_ld_gin;

-- Drop the old column
ALTER TABLE union_graphs DROP COLUMN graph_json_ld;

