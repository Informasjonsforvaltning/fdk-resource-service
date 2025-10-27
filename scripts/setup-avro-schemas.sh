#!/bin/bash

# Script to copy Avro schemas to the schema-registry container

echo "Copying Avro schemas to schema-registry container..."

# Copy schema files to the container
docker cp avro/DatasetEvent.avsc fdk-resource-service-schema-registry-1:/tmp/
docker cp avro/ConceptEvent.avsc fdk-resource-service-schema-registry-1:/tmp/
docker cp avro/DataServiceEvent.avsc fdk-resource-service-schema-registry-1:/tmp/
docker cp avro/EventEvent.avsc fdk-resource-service-schema-registry-1:/tmp/
docker cp avro/InformationModelEvent.avsc fdk-resource-service-schema-registry-1:/tmp/
docker cp avro/ServiceEvent.avsc fdk-resource-service-schema-registry-1:/tmp/
docker cp avro/RdfParseEvent.avsc fdk-resource-service-schema-registry-1:/tmp/

echo "Avro schemas copied successfully!"


