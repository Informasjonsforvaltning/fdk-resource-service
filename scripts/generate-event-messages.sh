#!/bin/bash

# Script to generate event messages for the FDK Resource Service
# This script generates proper event messages that match the legacy format

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
COUNT=${1:-3}

echo "Generating $COUNT event messages..."

# Function to generate a random UUID
generate_uuid() {
    if command -v uuidgen >/dev/null 2>&1; then
        uuidgen | tr '[:upper:]' '[:lower:]'
    else
        cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "test-$(date +%s)-$RANDOM"
    fi
}

# Function to get a random event type
get_random_event_type() {
    local types=("HARVESTED" "REASONED" "REMOVED")
    echo ${types[$RANDOM % ${#types[@]}]}
}

# Function to generate RDF graph data
generate_rdf_graph() {
    local id="$1"
    local resource_type="$2"
    echo "{\\\"@id\\\":\\\"https://example.com/$resource_type/$id\\\",\\\"@type\\\":\\\"$resource_type\\\",\\\"title\\\":\\\"Sample $resource_type $id\\\",\\\"description\\\":\\\"A sample $resource_type for testing\\\"}"
}

# Generate dataset events
echo "Generating dataset events..."
for i in $(seq 1 $COUNT); do
    ID=$(generate_uuid)
    EVENT_TYPE=$(get_random_event_type)
    TIMESTAMP=$(date +%s)000  # milliseconds
    GRAPH=$(generate_rdf_graph "$ID" "Dataset")
    
    # Create the dataset event JSON
    DATASET_EVENT="{\"type\":\"DATASET_$EVENT_TYPE\",\"fdkId\":\"$ID\",\"graph\":\"$GRAPH\",\"timestamp\":$TIMESTAMP}"
    
    echo "Sending dataset event $i/$COUNT: DATASET_$EVENT_TYPE with ID $ID"
    
    # Send to Kafka using the Avro console producer from schema-registry container
    echo "$DATASET_EVENT" | docker exec -i fdk-resource-service-schema-registry-1 kafka-avro-console-producer \
        --bootstrap-server kafka:9092 \
        --topic dataset-events \
        --property schema.registry.url=http://localhost:8081 \
        --property value.schema.file=/tmp/DatasetEvent.avsc
    
    sleep 1
done

# Generate concept events
echo "Generating concept events..."
for i in $(seq 1 2); do
    ID=$(generate_uuid)
    EVENT_TYPE=$(get_random_event_type)
    TIMESTAMP=$(date +%s)000  # milliseconds
    GRAPH=$(generate_rdf_graph "$ID" "Concept")
    
    # Create the concept event JSON
    CONCEPT_EVENT="{\"type\":\"CONCEPT_$EVENT_TYPE\",\"fdkId\":\"$ID\",\"graph\":\"$GRAPH\",\"timestamp\":$TIMESTAMP}"
    
    echo "Sending concept event $i/2: CONCEPT_$EVENT_TYPE with ID $ID"
    
    # Send to Kafka using the Avro console producer from schema-registry container
    echo "$CONCEPT_EVENT" | docker exec -i fdk-resource-service-schema-registry-1 kafka-avro-console-producer \
        --bootstrap-server kafka:9092 \
        --topic concept-events \
        --property schema.registry.url=http://localhost:8081 \
        --property value.schema.file=/tmp/ConceptEvent.avsc
    
    sleep 1
done

# Generate service events
echo "Generating service events..."
for i in $(seq 1 2); do
    ID=$(generate_uuid)
    EVENT_TYPE=$(get_random_event_type)
    TIMESTAMP=$(date +%s)000  # milliseconds
    GRAPH=$(generate_rdf_graph "$ID" "Service")
    
    # Create the service event JSON
    SERVICE_EVENT="{\"type\":\"SERVICE_$EVENT_TYPE\",\"fdkId\":\"$ID\",\"graph\":\"$GRAPH\",\"timestamp\":$TIMESTAMP}"
    
    echo "Sending service event $i/2: SERVICE_$EVENT_TYPE with ID $ID"
    
    # Send to Kafka using the Avro console producer from schema-registry container
    echo "$SERVICE_EVENT" | docker exec -i fdk-resource-service-schema-registry-1 kafka-avro-console-producer \
        --bootstrap-server kafka:9092 \
        --topic service-events \
        --property schema.registry.url=http://localhost:8081 \
        --property value.schema.file=/tmp/ServiceEvent.avsc
    
    sleep 1
done

echo "Generated $COUNT dataset events, 2 concept events, and 2 service events"
