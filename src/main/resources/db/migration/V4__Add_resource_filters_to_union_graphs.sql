ALTER TABLE union_graphs
    ADD COLUMN resource_filters JSONB;

COMMENT ON COLUMN union_graphs.resource_filters IS
    'JSON payload describing per-resource-type filters (e.g., dataset filters)';

CREATE INDEX idx_union_graphs_resource_filters ON union_graphs USING GIN (resource_filters);



