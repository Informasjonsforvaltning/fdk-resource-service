-- Add columns to store original graph data and format for each resource
ALTER TABLE resources
    ADD COLUMN resource_graph_data TEXT,
    ADD COLUMN resource_graph_format VARCHAR(50);

COMMENT ON COLUMN resources.resource_graph_data IS
    'Original RDF graph data representation (typically Turtle text)';
COMMENT ON COLUMN resources.resource_graph_format IS
    'Format of the original RDF data (TURTLE, JSON_LD, RDF_XML, N_TRIPLES, N_QUADS)';

-- Create index on resource_graph_format for filtering
CREATE INDEX idx_resources_resource_graph_format ON resources(resource_graph_format);
