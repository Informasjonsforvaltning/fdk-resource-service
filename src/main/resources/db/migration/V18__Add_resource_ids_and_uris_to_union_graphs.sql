-- Add resource_ids and resource_uris columns to union_graphs table
-- These columns allow filtering union graphs to include only specific resources by ID (fdkId) or URI
ALTER TABLE union_graphs
    ADD COLUMN IF NOT EXISTS resource_ids TEXT[],
    ADD COLUMN IF NOT EXISTS resource_uris TEXT[];

COMMENT ON COLUMN union_graphs.resource_ids IS
    'Optional list of resource IDs (fdkId) to filter by. If provided, only resources with matching IDs will be included in the union graph.';
COMMENT ON COLUMN union_graphs.resource_uris IS
    'Optional list of resource URIs to filter by. If provided, only resources with matching URIs will be included in the union graph.';

-- Create indexes for better query performance when filtering by IDs or URIs
CREATE INDEX IF NOT EXISTS idx_union_graphs_resource_ids ON union_graphs USING GIN(resource_ids);
CREATE INDEX IF NOT EXISTS idx_union_graphs_resource_uris ON union_graphs USING GIN(resource_uris);


