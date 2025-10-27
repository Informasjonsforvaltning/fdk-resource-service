# Testing Guide

This project uses Testcontainers for integration testing, providing automatic container lifecycle management.

## Test Types

### Unit Tests

- **Location**: `src/test/kotlin/no/fdk/resourceservice/controller/`
- **Purpose**: Test controller endpoints with mocked services
- **Database**: No database required (uses mocks)
- **Run with**: `mvn test`

### Integration Tests

- **Location**: `src/test/kotlin/no/fdk/resourceservice/integration/`
- **Purpose**: Test full application with real PostgreSQL, Kafka, and Schema Registry
- **Services**: Uses Testcontainers for automatic container management
- **Run with**: `mvn verify`

## Running Tests

```bash
# Run unit tests only
mvn test

# Run all tests (unit + integration)
mvn verify
```

## Testcontainers Configuration

The integration tests use Testcontainers to automatically:

1. **Start PostgreSQL**: Reused container across test runs for faster execution
2. **Start Kafka**: Reused container with automatic topic creation
3. **Start Schema Registry**: Reused container with automatic Avro schema registration
4. **Configure Spring**: Dynamic property injection for database and Kafka URLs

### Container Reuse

- **Reuse Enabled**: Containers are reused between test runs for faster execution
- **Configuration**: `testcontainers.properties` enables global container reuse
- **Lifecycle**: Containers start once and persist across multiple test classes
- **Cleanup**: Containers are automatically cleaned up when all tests complete

### Automatic Setup

- **Topics**: All required Kafka topics are created automatically
- **Schemas**: Avro schemas from `avro/` directory are registered with Schema Registry
- **Database**: PostgreSQL with proper schema via Flyway migrations
- **Networking**: Containers communicate via Testcontainers network

### Configuration

- **File**: `src/test/kotlin/no/fdk/resourceservice/config/TestContainersConfig.kt`
- **Properties**: `src/test/resources/testcontainers.properties`
- **Services**: PostgreSQL 15, Kafka 7.4.0, Schema Registry 7.4.0
- **Topics**: `rdf-parse-events`, `concept-events`, `dataset-events`, `data-service-events`, `information-model-events`, `service-events`, `event-events`

### Performance Benefits

- **Faster Tests**: Container reuse reduces startup time from ~30s to ~5s per test class
- **Resource Efficiency**: Single set of containers instead of fresh containers per test
- **Parallel Execution**: Multiple test classes can run against the same containers

### Disabling Container Reuse

To disable container reuse (for debugging or isolation), set in `testcontainers.properties`:
```properties
testcontainers.reuse.enable=false
```

## Test Structure

```
src/test/kotlin/
├── controller/                    # Unit tests (mocked services)
│   ├── ConceptControllerTest.kt
│   ├── DatasetControllerTest.kt
│   ├── DataServiceControllerTest.kt
│   ├── EventControllerTest.kt
│   ├── HealthControllerTest.kt
│   ├── InformationModelControllerTest.kt
│   └── ServiceControllerTest.kt
├── integration/                   # Integration tests (real services)
│   └── ResourceServiceIntegrationTest.kt
└── config/                       # Test configurations
    ├── TestContainersConfig.kt    # Testcontainers setup
    └── TestApplicationConfig.kt   # Application test config
```

## Benefits of Testcontainers

- **Automatic Lifecycle**: Containers start/stop automatically
- **Isolation**: Each test run gets fresh containers
- **CI/CD Ready**: Works in GitHub Actions, Jenkins, etc.
- **No Manual Setup**: No need to manage Docker Compose manually
- **Port Management**: Automatic port allocation prevents conflicts
- **Schema Management**: Automatic topic and schema registration
