#!/bin/bash

# Script to consume and display Kafka messages
# Usage: ./scripts/consume-messages.sh [topic] [count]

KAFKA_BOOTSTRAP_SERVERS=${KAFKA_BOOTSTRAP_SERVERS:-localhost:9092}
TOPIC=${1:-rdf-parse-events}
COUNT=${2:-10}

echo "Consuming messages from topic: $TOPIC (max $COUNT messages)"
echo "Press Ctrl+C to stop"
echo "----------------------------------------"

docker exec -it fdk-resource-service-schema-registry-1 kafka-avro-console-consumer \
    --bootstrap-server kafka:9092 \
    --topic "$TOPIC" \
    --from-beginning \
    --max-messages "$COUNT" \
    --property "print.key=true" \
    --property "print.value=true" \
    --property "key.separator=: " \
    --property "print.timestamp=true" \
    --property "schema.registry.url=http://localhost:8081"
