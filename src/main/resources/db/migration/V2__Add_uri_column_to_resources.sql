-- Add uri column to resources table for better querying by URI
-- This allows querying by URI without parsing JSONB, and supports both single node and @graph formats

ALTER TABLE resources ADD COLUMN uri VARCHAR(1000);

-- Create index for URI lookups
CREATE INDEX idx_resources_uri ON resources(uri);
CREATE INDEX idx_resources_uri_deleted ON resources(uri, deleted);
CREATE INDEX idx_resources_resource_type_uri ON resources(resource_type, uri);

-- Update existing records by extracting URI from resource_json->>'uri' or resource_json_ld
-- Note: This is a best-effort update. Some records may not have a URI if it's not in the expected format.
UPDATE resources 
SET uri = resource_json->>'uri' 
WHERE resource_json->>'uri' IS NOT NULL;

-- For records without URI in resource_json, try to extract from resource_json_ld
-- This handles the @id field in JSON-LD
UPDATE resources 
SET uri = resource_json_ld->>'@id'
WHERE uri IS NULL AND resource_json_ld->>'@id' IS NOT NULL;

