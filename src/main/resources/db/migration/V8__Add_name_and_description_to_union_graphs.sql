-- Add name and description columns to union_graphs table
ALTER TABLE union_graphs
    ADD COLUMN name VARCHAR(255) NOT NULL DEFAULT '',
    ADD COLUMN description TEXT;

-- Remove the default after adding the column
ALTER TABLE union_graphs
    ALTER COLUMN name DROP DEFAULT;

COMMENT ON COLUMN union_graphs.name IS
    'Human-readable name for the union graph (required)';
COMMENT ON COLUMN union_graphs.description IS
    'Optional human-readable description of the union graph';

