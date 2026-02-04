-- Add include_catalog column to union_graphs table
-- When false, dcat:Catalog and dcat:CatalogRecord resources will be filtered out from RDF/XML snapshots
ALTER TABLE union_graphs
    ADD COLUMN IF NOT EXISTS include_catalog BOOLEAN NOT NULL DEFAULT true;

COMMENT ON COLUMN union_graphs.include_catalog IS
    'If true (default), Catalog and CatalogRecord resources are included in union graph snapshots. If false, Catalog and CatalogRecord resources are removed from snapshots, but references to their URIs are preserved.';

