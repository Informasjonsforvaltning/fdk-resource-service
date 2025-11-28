ALTER TABLE union_graphs
    ADD COLUMN expand_distribution_access_services BOOLEAN NOT NULL DEFAULT false;

COMMENT ON COLUMN union_graphs.expand_distribution_access_services IS
    'If true, when building union graphs, datasets with distributions that reference DataService URIs will have those DataService graphs automatically included in the union graph';


