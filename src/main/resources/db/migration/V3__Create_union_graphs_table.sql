-- Create union_graphs table for storing union graph orders and their results
CREATE TABLE union_graphs (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    resource_types TEXT[],
    update_ttl_hours INTEGER NOT NULL DEFAULT 0,
    graph_json_ld JSONB,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP,
    processing_started_at TIMESTAMP,
    locked_by VARCHAR(255),
    locked_at TIMESTAMP,
    webhook_url TEXT
);

-- Create indexes for better query performance
CREATE INDEX idx_union_graphs_status ON union_graphs(status);
CREATE INDEX idx_union_graphs_created_at ON union_graphs(created_at);
CREATE INDEX idx_union_graphs_locked_by ON union_graphs(locked_by);
CREATE INDEX idx_union_graphs_status_locked ON union_graphs(status, locked_by) WHERE locked_by IS NULL;

-- Create GIN index for JSONB column
CREATE INDEX idx_union_graphs_graph_json_ld_gin ON union_graphs USING GIN(graph_json_ld);

-- Create index for efficient lookups by update_ttl_hours
CREATE INDEX idx_union_graphs_update_ttl_hours ON union_graphs(update_ttl_hours) WHERE update_ttl_hours > 0;

