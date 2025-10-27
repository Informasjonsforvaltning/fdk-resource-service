#!/bin/bash

# Simple script to generate test messages for the FDK Resource Service
# This script generates JSON messages that can be consumed by the application

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
COUNT=${1:-3}

echo "Generating $COUNT test messages..."

# Function to generate a random UUID
generate_uuid() {
    if command -v uuidgen >/dev/null 2>&1; then
        uuidgen | tr '[:upper:]' '[:lower:]'
    else
        cat /proc/sys/kernel/random/uuid 2>/dev/null || echo "test-$(date +%s)-$RANDOM"
    fi
}

# Function to get a random resource type
get_random_resource_type() {
    local types=("concept" "dataset" "dataService" "informationModel" "service" "event")
    echo ${types[$RANDOM % ${#types[@]}]}
}

# Generate RDF parse events
echo "Generating RDF parse events..."
for i in $(seq 1 $COUNT); do
    ID=$(generate_uuid)
    RESOURCE_TYPE=$(get_random_resource_type)
    TIMESTAMP=$(date +%s)000  # milliseconds
    
    # Create a simple resource JSON
    RESOURCE_JSON="{\"id\":\"$ID\",\"title\":\"Sample $RESOURCE_TYPE $ID\",\"description\":\"A sample $RESOURCE_TYPE for testing\",\"publisher\":\"Test Publisher\",\"issued\":\"$(date -Iseconds)\"}"
    
    # Create the RDF parse event JSON
    RDF_EVENT="{\"id\":\"$ID\",\"resourceType\":\"$RESOURCE_TYPE\",\"resource\":$RESOURCE_JSON,\"timestamp\":$TIMESTAMP}"
    
    echo "Sending RDF parse event $i/$COUNT: $RESOURCE_TYPE with ID $ID"
    
    # Send to Kafka using the Avro console producer
    echo "$RDF_EVENT" | docker exec -i fdk-resource-service-schema-registry-1 kafka-avro-console-producer \
        --bootstrap-server kafka:9092 \
        --topic rdf-parse-events \
        --property schema.registry.url=http://localhost:8081
    
    sleep 1
done

# Generate remove events
echo "Generating remove events..."
for i in $(seq 1 2); do
    ID=$(generate_uuid)
    TIMESTAMP=$(date +%s)000  # milliseconds
    
    # Create the remove event JSON
    REMOVE_EVENT="{\"id\":\"$ID\",\"timestamp\":$TIMESTAMP}"
    
    echo "Sending remove event $i/2: $ID"
    
    # Send to Kafka using the Avro console producer
    echo "$REMOVE_EVENT" | docker exec -i fdk-resource-service-schema-registry-1 kafka-avro-console-producer \
        --bootstrap-server kafka:9092 \
        --topic dataset-remove-events \
        --property schema.registry.url=http://localhost:8081
    
    sleep 1
done

echo "Generated $COUNT RDF parse events and 2 remove events"
