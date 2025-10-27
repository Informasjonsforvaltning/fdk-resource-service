-- Create resources table with correct column names from the start
-- This baseline migration includes both resource_json and resource_json_ld columns

CREATE TABLE resources (
    id VARCHAR(255) NOT NULL PRIMARY KEY,
    resource_type VARCHAR(50) NOT NULL,
    resource_json JSONB,
    resource_json_ld JSONB,
    timestamp BIGINT NOT NULL DEFAULT 0,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes for better query performance
CREATE INDEX idx_resources_resource_type ON resources(resource_type);
CREATE INDEX idx_resources_deleted ON resources(deleted);
CREATE INDEX idx_resources_timestamp ON resources(timestamp);
CREATE INDEX idx_resources_resource_type_deleted ON resources(resource_type, deleted);

-- Create GIN indexes for JSONB columns for better JSON query performance
CREATE INDEX idx_resources_resource_json_gin ON resources USING GIN(resource_json);
CREATE INDEX idx_resources_resource_json_ld_gin ON resources USING GIN(resource_json_ld);
