# Kafka Scripts for FDK Resource Service

This directory contains scripts for managing Kafka topics and generating test messages for the FDK Resource Service.

## Scripts

### `setup-kafka-topics.sh`

Creates all required Kafka topics for the FDK Resource Service.

**Usage:**

```bash
./scripts/setup-kafka-topics.sh
```

**Topics created:**

- `rdf-parse-events` - For RDF parse events
- `concept-remove-events` - For concept removal events
- `dataset-remove-events` - For dataset removal events
- `data-service-remove-events` - For data service removal events
- `information-model-remove-events` - For information model removal events
- `service-remove-events` - For service removal events
- `event-remove-events` - For event removal events

### `generate-simple-messages.sh`

Generates test messages for the FDK Resource Service using JSON serialization.

**Usage:**

```bash
./scripts/generate-simple-messages.sh [count]
```

**Parameters:**

- `count` (optional): Number of RDF parse events to generate (default: 3)

**Example:**

```bash
./scripts/generate-simple-messages.sh 5
```

### `consume-messages.sh`

Consumes and displays messages from Kafka topics.

**Usage:**

```bash
./scripts/consume-messages.sh [topic] [count]
```

**Parameters:**

- `topic` (optional): Topic name to consume from (default: rdf-parse-events)
- `count` (optional): Maximum number of messages to consume (default: 10)

**Examples:**

```bash
# Consume from default topic
./scripts/consume-messages.sh

# Consume from specific topic
./scripts/consume-messages.sh dataset-remove-events

# Consume limited number of messages
./scripts/consume-messages.sh rdf-parse-events 5
```

### `setup-avro-schemas.sh`

Copies Avro schema files to the schema-registry container for proper message serialization.

**Usage:**

```bash
./scripts/setup-avro-schemas.sh
```

### `test-api.sh`

Tests the FDK Resource Service API endpoints.

**Usage:**

```bash
./scripts/test-api.sh
```

## Setup

1. Start the services:

   ```bash
   docker-compose up -d
   ```

2. Wait for services to be healthy, then create topics:

   ```bash
   ./scripts/setup-kafka-topics.sh
   ```

3. Copy Avro schemas to schema registry:

   ```bash
   ./scripts/setup-avro-schemas.sh
   ```

4. Generate test messages:

   ```bash
   ./scripts/generate-simple-messages.sh 5
   ```

5. Check application logs to see if messages are being processed:
   ```bash
   docker logs fdk-resource-service-fdk-resource-service-1
   ```

## Message Format

### RDF Parse Event

```json
{
  "id": "unique-resource-id",
  "resourceType": "concept|dataset|dataService|informationModel|service|event",
  "resource": {
    "id": "unique-resource-id",
    "title": "Resource Title",
    "description": "Resource Description",
    "publisher": "Publisher Name",
    "issued": "2024-01-01T00:00:00Z"
  },
  "timestamp": 1704067200000
}
```

### Remove Event

```json
{
  "id": "unique-resource-id",
  "timestamp": 1704067200000
}
```

## Troubleshooting

- **Port conflicts**: Make sure ports 5432, 9092, and 8080 are not in use
- **Container not found**: Ensure Docker Compose services are running with `docker-compose ps`
- **Topics not created**: Run `./scripts/setup-kafka-topics.sh` after services are healthy
- **Messages not processed**: Check application logs with `docker logs fdk-resource-service-fdk-resource-service-1`
