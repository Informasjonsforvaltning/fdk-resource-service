# Agents Documentation

This document describes the main processing agents (components) in the FDK Resource Service that handle different aspects of resource management, event processing, and graph operations.

## Overview

The FDK Resource Service is built on a microservices architecture with multiple specialized agents that work together to:
- Consume and process Kafka events for various resource types
- Store and retrieve RDF resources in multiple formats
- Build and manage union graphs that combine multiple resource graphs
- Provide REST APIs for accessing resources

## Core Agents

### 1. KafkaConsumer

**Location**: `src/main/kotlin/no/fdk/resourceservice/kafka/KafkaConsumer.kt`

**Purpose**: Consumes events from Kafka topics and routes them to appropriate handlers.

**Responsibilities**:
- Listens to multiple Kafka topics for different resource types:
  - Concepts (`app.kafka.topics.concept`)
  - Datasets (`app.kafka.topics.dataset`)
  - Data Services (`app.kafka.topics.data-service`)
  - Information Models (`app.kafka.topics.information-model`)
  - Services (`app.kafka.topics.service`)
  - Events (`app.kafka.topics.event`)
  - RDF Parse events (`app.kafka.topics.rdf-parse`)
- Extracts and validates event data from Avro GenericRecords
- Handles event deserialization and type conversion
- Routes events to `CircuitBreakerService` for processing
- Manages Kafka acknowledgment (ack/nack) based on processing success
- Runs with concurrency of 4 threads per topic for parallel processing

**Key Methods**:
- `handleConceptEvent()` - Processes concept events
- `handleDatasetEvent()` - Processes dataset events
- `handleDataServiceEvent()` - Processes data service events
- `handleInformationModelEvent()` - Processes information model events
- `handleServiceEvent()` - Processes service events
- `handleEventEvent()` - Processes event events
- `handleRdfParseEvent()` - Processes RDF parse events

**Error Handling**:
- Invalid or malformed events are acknowledged and skipped
- Processing failures trigger nack (negative acknowledgment) for retry
- Logs errors for monitoring and debugging

---

### 2. CircuitBreakerService

**Location**: `src/main/kotlin/no/fdk/resourceservice/service/CircuitBreakerService.kt`

**Purpose**: Processes Kafka events with circuit breaker pattern for resilience and fault tolerance.

**Responsibilities**:
- Processes resource events with circuit breaker protection
- Handles two types of events:
  - **REASONED events**: Stores resource graph data (Turtle format)
  - **REMOVED events**: Marks resources as deleted
- Validates timestamps to prevent processing outdated events
- Converts and stores RDF data in the database
- Records metrics for monitoring (timers, counters)
- Manages transactions for data consistency

**Key Methods**:
- `handleRdfParseEvent()` - Processes parsed RDF data (JSON format)
- `handleConceptEvent()` - Processes concept events
- `handleDatasetEvent()` - Processes dataset events
- `handleDataServiceEvent()` - Processes data service events
- `handleInformationModelEvent()` - Processes information model events
- `handleServiceEvent()` - Processes service events
- `handleEventEvent()` - Processes event events
- `processResourceEvent()` - Common processing logic for resource events

**Circuit Breaker Configuration**:
- Separate circuit breakers for each resource type consumer
- Prevents cascading failures when downstream services are unavailable
- Automatic recovery when services become available again

**Metrics**:
- `store_resource_json` - Timer for JSON storage operations
- `store_resource_jsonld` - Timer for JSON-LD storage operations
- `store_resource_json_error` - Counter for JSON storage errors
- `store_resource_jsonld_error` - Counter for JSON-LD storage errors
- `store_resource_graph_data_error` - Counter for graph data storage errors

---

### 3. ResourceService

**Location**: `src/main/kotlin/no/fdk/resourceservice/service/ResourceService.kt`

**Purpose**: Core service for managing resource data storage and retrieval.

**Responsibilities**:
- Stores resources with both JSON and RDF graph representations
- Retrieves resources by ID, URI, or type
- Manages resource timestamps to prevent overwriting newer data
- Handles resource deletion (soft delete via `deleted` flag)
- Provides efficient URI lookup with fallback mechanisms
- Supports filtering and querying by various criteria

**Key Methods**:
- `storeResourceJson()` - Stores parsed JSON representation of resources
- `storeResourceGraphData()` - Stores original RDF graph data (Turtle format)
- `getResourceJson()` - Retrieves resource as JSON
- `getResourceJsonByUri()` - Retrieves resource by URI
- `getResourceEntityByUri()` - Retrieves resource entity with URI lookup fallbacks
- `getResourceGraphDataByUri()` - Retrieves RDF graph data by URI
- `shouldUpdateResource()` - Checks if resource should be updated based on timestamp
- `markResourceAsDeleted()` - Marks resource as deleted
- `getResourceJsonListSince()` - Gets resources updated since a timestamp

**Data Model**:
- Resources stored in PostgreSQL with JSONB for flexible JSON storage
- Separate columns for:
  - `resource_json` - Parsed JSON representation
  - `resource_graph_data` - Original RDF graph data (Turtle)
  - `resource_graph_format` - Format of graph data (TURTLE, JSON_LD, etc.)
  - `uri` - Resource URI for efficient lookup
  - `timestamp` - Processing timestamp
  - `deleted` - Soft delete flag

---

### 4. RdfService

**Location**: `src/main/kotlin/no/fdk/resourceservice/service/RdfService.kt`

**Purpose**: Handles RDF format conversion, content negotiation, and RDF processing.

**Responsibilities**:
- Converts between RDF formats (Turtle, JSON-LD, RDF/XML, N-Triples, N-Quads)
- Handles content negotiation for HTTP requests
- Manages RDF namespace prefixes for different resource types
- Optimizes memory usage for large graphs (union graphs)
- Provides format-specific styling (PRETTY, STANDARD)

**Key Methods**:
- `convertFromFormat()` - Converts from any RDF format to any target format
- `convertFromTurtle()` - Converts Turtle to target format
- `convertFromJsonLd()` - Converts JSON-LD to target format
- `convertFromModel()` - Converts Jena Model to target format
- `convertTurtleToJsonLdMap()` - Converts Turtle to JSON-LD Map
- `getBestFormat()` - Determines best format from Accept header
- `getContentType()` - Returns MediaType for RDF format

**Supported Formats**:
- **JSON-LD** (`application/ld+json`) - Default format
- **Turtle** (`text/turtle`)
- **RDF/XML** (`application/rdf+xml`)
- **N-Triples** (`application/n-triples`)
- **N-Quads** (`application/n-quads`)

**Resource-Specific Prefixes**:
- Dataset: DCAT-AP-NO vocabularies
- Data Service: DCAT-AP-NO vocabularies
- Concept: SKOS-AP-NO-Begrep vocabularies
- Information Model: ModellDCAT-AP-NO vocabularies
- Service: CPSV-AP-NO vocabularies
- Event: CPSV-AP-NO vocabularies

---

### 5. UnionGraphService

**Location**: `src/main/kotlin/no/fdk/resourceservice/service/UnionGraphService.kt`

**Purpose**: Manages union graph creation, processing, and lifecycle.

**Responsibilities**:
- Creates union graph orders from multiple resource graphs
- Builds union graphs by combining resource graphs
- Handles incremental batch processing to prevent memory issues
- Manages union graph state (PENDING, PROCESSING, COMPLETED, FAILED)
- Supports resource filtering and expansion (e.g., expand distribution access services)
- Handles TTL-based automatic updates
- Manages resource snapshots for union graphs

**Key Methods**:
- `createOrder()` - Creates a new union graph order
- `processOrder()` - Processes a union graph order (full processing)
- `processNextBatch()` - Processes next batch incrementally
- `getUnionGraph()` - Retrieves union graph data
- `getUnionGraphStatus()` - Gets union graph processing status
- `resetToPending()` - Resets union graph to pending state
- `deleteUnionGraph()` - Deletes union graph

**Union Graph Features**:
- **Resource Type Filtering**: Include/exclude specific resource types
- **Resource Filters**: Per-resource-type filters (e.g., `isOpenData`, `isRelatedToTransportportal`)
- **Resource ID/URI Filtering**: Include specific resources by ID or URI
- **Distribution Expansion**: Automatically include DataService graphs referenced by datasets
- **TTL Management**: Automatic updates based on time-to-live
- **Webhook Support**: Notifications on status changes

**Processing States**:
- `PENDING` - Order created, waiting for processing
- `PROCESSING` - Currently being built
- `COMPLETED` - Successfully built and ready
- `FAILED` - Processing failed

---

### 6. UnionGraphProcessor

**Location**: `src/main/kotlin/no/fdk/resourceservice/service/UnionGraphProcessor.kt`

**Purpose**: Background processor that schedules and manages union graph processing.

**Responsibilities**:
- Polls for pending union graph orders
- Processes orders asynchronously using thread pool
- Handles distributed locking to prevent duplicate processing
- Manages incremental batch processing for large graphs
- Cleans up stale locks from crashed instances
- Handles TTL expiration and automatic updates

**Scheduled Tasks**:
- `processPendingOrders()` - Runs every 5 seconds, processes pending orders
- `processIncrementalBatches()` - Runs every 10 seconds, processes one batch at a time
- `processExpiredOrders()` - Runs every hour, resets expired orders to PENDING
- `cleanupStaleLocks()` - Runs every 10 minutes, releases stale locks

**Key Methods**:
- `processPendingOrders()` - Finds and processes pending orders
- `processOrderAsync()` - Processes order asynchronously
- `processIncrementalBatches()` - Processes batches incrementally
- `processExpiredOrders()` - Handles TTL expiration
- `cleanupStaleLocks()` - Cleans up stale locks

**Distributed Processing**:
- Uses database-level locking (`FOR UPDATE SKIP LOCKED`)
- Instance ID tracking for distributed environments
- Lock timeout of 48 hours for crashed instances
- Prevents multiple instances from processing the same order

---

### 7. WebhookService

**Location**: `src/main/kotlin/no/fdk/resourceservice/service/WebhookService.kt`

**Purpose**: Handles webhook notifications for union graph status changes.

**Responsibilities**:
- Calls webhook URLs asynchronously when union graph status changes
- Sends status change notifications with order details
- Handles connection errors gracefully
- Logs webhook call results

**Key Methods**:
- `callWebhook()` - Calls webhook URL asynchronously with status change payload

**Webhook Payload**:
```json
{
  "id": "order-id",
  "status": "COMPLETED",
  "previousStatus": "PROCESSING",
  "resourceTypes": ["DATASET", "CONCEPT"],
  "updateTtlHours": 24,
  "errorMessage": null,
  "createdAt": "2024-01-01T00:00:00Z",
  "updatedAt": "2024-01-01T01:00:00Z",
  "processedAt": "2024-01-01T01:00:00Z"
}
```

**Requirements**:
- Webhook URLs must use HTTPS
- Calls are asynchronous and non-blocking
- Failures are logged but don't affect order processing

---

## Agent Interaction Flow

### Event Processing Flow

```
Kafka Topic
    ↓
KafkaConsumer (extracts event)
    ↓
CircuitBreakerService (processes with circuit breaker)
    ↓
ResourceService (stores in database)
    ↓
PostgreSQL Database
```

### Union Graph Processing Flow

```
REST API Request
    ↓
UnionGraphController
    ↓
UnionGraphService (creates order)
    ↓
UnionGraphProcessor (scheduled task picks up order)
    ↓
UnionGraphService (processes order)
    ↓
ResourceService (retrieves resources)
    ↓
RdfService (converts formats)
    ↓
UnionGraphService (combines graphs)
    ↓
WebhookService (notifies on completion)
```

### Resource Retrieval Flow

```
REST API Request
    ↓
Controller (ConceptController, DatasetController, etc.)
    ↓
ResourceService (retrieves from database)
    ↓
RdfService (converts format if needed)
    ↓
HTTP Response (with content negotiation)
```

---

## Configuration

### Kafka Configuration
- Topics configured via `app.kafka.topics.*` properties
- Concurrency: 4 threads per topic
- Schema Registry for Avro serialization

### Circuit Breaker Configuration
- Separate circuit breakers per resource type
- Configurable failure thresholds and recovery settings
- See `CircuitBreakerConfig.kt` for details

### Union Graph Configuration
- Thread pool size: `UNION_GRAPH_MAX_POOL_SIZE`
- Batch size: Configurable per order
- Lock timeout: 48 hours
- See `UnionGraphConfig.kt` for details

---

## Monitoring and Metrics

### Metrics Exposed
- **Timers**: Processing duration for storage operations
- **Counters**: Error counts by type and resource type
- **Gauges**: Union graph order counts by status

### Health Checks
- Spring Boot Actuator endpoints
- Database connectivity checks
- Kafka consumer health

---

## Error Handling

### Event Processing Errors
- Invalid events: Acknowledged and skipped
- Processing failures: Nacked for retry
- Circuit breaker: Opens on repeated failures

### Union Graph Errors
- Processing failures: Order marked as FAILED
- Stale locks: Automatically cleaned up
- Memory issues: Handled via incremental batch processing

---

## Best Practices

1. **Timestamp Validation**: Always check timestamps before processing to avoid overwriting newer data
2. **Transaction Management**: Use appropriate transaction boundaries for data consistency
3. **Memory Management**: Use incremental processing for large union graphs
4. **Error Logging**: Log errors with sufficient context for debugging
5. **Metrics Collection**: Record metrics for all critical operations
6. **Circuit Breaker**: Use circuit breakers for external dependencies
7. **Distributed Locking**: Use database-level locking for distributed processing

---

## Future Enhancements

Potential improvements for agents:
- Enhanced retry mechanisms with exponential backoff
- More granular circuit breaker configurations
- Additional metrics and monitoring
- Support for more RDF formats
- Enhanced webhook retry logic
- Streaming union graph processing for very large graphs



