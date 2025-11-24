-- Create union_graph_resource_snapshots table for storing snapshots of resource graphs
-- when a union graph is built. This ensures consistency - resources served via OAI-PMH
-- will match the version that was used when building the union graph.
CREATE TABLE union_graph_resource_snapshots (
    id BIGSERIAL PRIMARY KEY,
    union_graph_id VARCHAR(255) NOT NULL,
    resource_id VARCHAR(255) NOT NULL,
    resource_type VARCHAR(50) NOT NULL,
    resource_graph_data TEXT NOT NULL,
    resource_graph_format VARCHAR(50),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_union_graph_resource_snapshots_union_graph_id 
        FOREIGN KEY (union_graph_id) REFERENCES union_graphs(id) ON DELETE CASCADE
);

-- Create indexes for efficient lookups
CREATE INDEX idx_union_graph_resource_snapshots_union_graph_id 
    ON union_graph_resource_snapshots(union_graph_id);
CREATE INDEX idx_union_graph_resource_snapshots_resource_id 
    ON union_graph_resource_snapshots(resource_id);
CREATE INDEX idx_union_graph_resource_snapshots_union_graph_resource 
    ON union_graph_resource_snapshots(union_graph_id, resource_id);
CREATE INDEX idx_union_graph_resource_snapshots_union_graph_type 
    ON union_graph_resource_snapshots(union_graph_id, resource_type);

COMMENT ON TABLE union_graph_resource_snapshots IS
    'Stores snapshots of resource graph data at the time a union graph was built. '
    'This ensures OAI-PMH endpoints return consistent data that matches the union graph.';
COMMENT ON COLUMN union_graph_resource_snapshots.union_graph_id IS
    'Reference to the union graph this snapshot belongs to';
COMMENT ON COLUMN union_graph_resource_snapshots.resource_id IS
    'ID of the resource that was snapshotted';
COMMENT ON COLUMN union_graph_resource_snapshots.resource_type IS
    'Type of the resource (DATASET, CONCEPT, etc.)';
COMMENT ON COLUMN union_graph_resource_snapshots.resource_graph_data IS
    'Snapshot of the resource graph data (typically Turtle text)';
COMMENT ON COLUMN union_graph_resource_snapshots.resource_graph_format IS
    'Format of the snapshot data (TURTLE, JSON_LD, etc.)';

