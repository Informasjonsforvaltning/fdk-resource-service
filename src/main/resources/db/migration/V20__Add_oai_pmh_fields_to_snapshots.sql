-- Add OAI-PMH filtering fields to union_graph_resource_snapshots.
-- resource_modified_at: from harvest.modified at build time; used for from/until and datestamp.
-- publisher_orgnr: from publisher.id at build time; used for set filter (org:orgnr) and setSpec.
ALTER TABLE union_graph_resource_snapshots
    ADD COLUMN resource_modified_at TIMESTAMP WITH TIME ZONE NULL,
    ADD COLUMN publisher_orgnr VARCHAR(50) NULL;

COMMENT ON COLUMN union_graph_resource_snapshots.resource_modified_at IS
    'Resource modified date (from harvest.modified) at snapshot time; used for OAI-PMH from/until and datestamp';
COMMENT ON COLUMN union_graph_resource_snapshots.publisher_orgnr IS
    'Publisher organization number (from publisher.id) at snapshot time; used for OAI-PMH set org:orgnr';

-- Indexes for OAI-PMH filtered queries
CREATE INDEX idx_union_graph_resource_snapshots_union_graph_modified
    ON union_graph_resource_snapshots(union_graph_id, resource_modified_at);
CREATE INDEX idx_union_graph_resource_snapshots_union_graph_orgnr
    ON union_graph_resource_snapshots(union_graph_id, publisher_orgnr);
CREATE INDEX idx_union_graph_resource_snapshots_union_graph_modified_orgnr
    ON union_graph_resource_snapshots(union_graph_id, resource_modified_at, publisher_orgnr);
