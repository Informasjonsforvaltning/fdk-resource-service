ALTER TABLE resources
    ADD COLUMN catalog_graph_data TEXT,
    ADD COLUMN catalog_graph_format VARCHAR(50);

COMMENT ON COLUMN resources.catalog_graph_data IS
    'RDF graph data containing catalog metadata (dcat:Catalog, dcat:CatalogRecord, skos:Collection) from Kafka catalogGraph field';
COMMENT ON COLUMN resources.catalog_graph_format IS
    'Format of catalog_graph_data (typically TURTLE)';
