#!/bin/bash

# Script to create Kafka topics for the FDK Resource Service
# Usage: ./scripts/setup-kafka-topics.sh

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}

echo "Setting up Kafka topics for FDK Resource Service..."

# Function to create topic if it doesn't exist
create_topic() {
    local topic_name=$1
    local partitions=${2:-3}
    local replication_factor=${3:-1}
    
    echo "Creating topic: $topic_name"
    
    docker exec fdk-resource-service-kafka-1 kafka-topics \
        --bootstrap-server localhost:9092 \
        --create \
        --topic "$topic_name" \
        --partitions "$partitions" \
        --replication-factor "$replication_factor" \
        --if-not-exists
}

# Create all required topics (matching legacy design)
create_topic "rdf-parse-events" 3 1
create_topic "concept-events" 3 1
create_topic "dataset-events" 3 1
create_topic "data-service-events" 3 1
create_topic "information-model-events" 3 1
create_topic "service-events" 3 1
create_topic "event-events" 3 1

echo "Kafka topics created successfully!"

# List all topics
echo "Listing all topics:"
docker exec fdk-resource-service-kafka-1 kafka-topics --bootstrap-server localhost:9092 --list
