# FDK Resource Service

This application provides an API for the management of resources (concepts, datasets, data services, information models, services, and events) in the FDK (Felles Datakatalog) ecosystem. Resources are stored as RDF data and can be accessed in multiple serialization formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads).

The service consumes Kafka events for real-time resource updates and provides REST endpoints for querying and accessing resources. It also supports building union graphs that combine multiple resource graphs into a single queryable graph.

For a broader understanding of the system's context, refer to the [architecture documentation](https://github.com/Informasjonsforvaltning/architecture-documentation) wiki.

## Getting Started

These instructions will give you a copy of the project up and running on your local machine for development and testing purposes.

### Prerequisites

Ensure you have the following installed:

- Java 21
- Maven
- Docker

### Running locally

Clone the repository

```sh
git clone https://github.com/Informasjonsforvaltning/fdk-resource-service.git
cd fdk-resource-service
```

Start PostgreSQL, Kafka, and Schema Registry (either through your IDE using the dev profile, or via CLI):

```sh
docker compose up -d
mvn spring-boot:run -Dspring-boot.run.profiles=dev
```

### API Documentation (OpenAPI)

Once the application is running locally, the API documentation can be accessed at http://localhost:8080/swagger-ui/index.html

### Running tests

```sh
mvn verify
```

## Features

- **REST API** for managing resources (concepts, datasets, data services, information models, services, events)
- **Kafka event consumption** for real-time resource updates
- **PostgreSQL database** with JSONB support for flexible resource storage
- **RDF graph support** with multiple serialization formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)
- **Union graphs** for combining multiple resource graphs into a single queryable graph
- **Content negotiation** for RDF graph endpoints
- **JWT-based authentication** for secured endpoints
- **Health checks and monitoring** via Spring Boot Actuator

## Technology Stack

- **Language**: Kotlin
- **Framework**: Spring Boot 3.5.6
- **Database**: PostgreSQL with JSONB
- **Message Queue**: Apache Kafka with Avro serialization
- **Schema Registry**: Confluent Schema Registry
- **Authentication**: OAuth2 JWT
- **Build Tool**: Maven

## API Endpoints

### Concepts

- `GET /v1/concepts` - Get all concepts
- `GET /v1/concepts/{id}` - Get specific concept
- `GET /v1/concepts/{id}/graph` - Get concept RDF graph (supports content negotiation)
- `GET /v1/concepts/by-uri?uri={uri}` - Get concept by URI
- `GET /v1/concepts/by-uri/graph?uri={uri}` - Get concept RDF graph by URI (supports content negotiation)
- `POST /v1/concepts/filter` - Filter concepts
- `DELETE /v1/concepts/{id}` - Delete concept

### Datasets

- `GET /v1/datasets` - Get all datasets
- `GET /v1/datasets/{id}` - Get specific dataset
- `GET /v1/datasets/{id}/graph` - Get dataset RDF graph (supports content negotiation)
- `GET /v1/datasets/by-uri?uri={uri}` - Get dataset by URI
- `GET /v1/datasets/by-uri/graph?uri={uri}` - Get dataset RDF graph by URI (supports content negotiation)
- `POST /v1/datasets/filter` - Filter datasets
- `DELETE /v1/datasets/{id}` - Delete dataset

### Data Services

- `GET /v1/data-services` - Get all data services
- `GET /v1/data-services/{id}` - Get specific data service
- `GET /v1/data-services/{id}/graph` - Get data service RDF graph (supports content negotiation)
- `GET /v1/data-services/by-uri?uri={uri}` - Get data service by URI
- `GET /v1/data-services/by-uri/graph?uri={uri}` - Get data service RDF graph by URI (supports content negotiation)
- `DELETE /v1/data-services/{id}` - Delete data service

### Events

- `GET /v1/events` - Get all events
- `GET /v1/events/{id}` - Get specific event
- `GET /v1/events/{id}/graph` - Get event RDF graph (supports content negotiation)
- `GET /v1/events/by-uri?uri={uri}` - Get event by URI
- `GET /v1/events/by-uri/graph?uri={uri}` - Get event RDF graph by URI (supports content negotiation)
- `DELETE /v1/events/{id}` - Delete event

### Services

- `GET /v1/services` - Get all services
- `GET /v1/services/{id}` - Get specific service
- `GET /v1/services/{id}/graph` - Get service RDF graph (supports content negotiation)
- `GET /v1/services/by-uri?uri={uri}` - Get service by URI
- `GET /v1/services/by-uri/graph?uri={uri}` - Get service RDF graph by URI (supports content negotiation)
- `DELETE /v1/services/{id}` - Delete service

### Information Models

- `GET /v1/information-models` - Get all information models
- `GET /v1/information-models/{id}` - Get specific information model
- `GET /v1/information-models/{id}/graph` - Get information model RDF graph (supports content negotiation)
- `GET /v1/information-models/by-uri?uri={uri}` - Get information model by URI
- `GET /v1/information-models/by-uri/graph?uri={uri}` - Get information model RDF graph by URI (supports content negotiation)
- `DELETE /v1/information-models/{id}` - Delete information model

### Union Graphs

- `POST /v1/union-graphs` - Create a union graph order
- `GET /v1/union-graphs` - List all union graphs
- `GET /v1/union-graphs/{id}` - Get union graph details
- `GET /v1/union-graphs/{id}/status` - Get union graph status
- `GET /v1/union-graphs/{id}/graph` - Get union graph (supports content negotiation)
- `POST /v1/union-graphs/{id}/reset` - Reset union graph to pending
- `DELETE /v1/union-graphs/{id}` - Delete union graph

### Health

- `GET /v1/health` - Health check endpoint

## RDF Graph Endpoints

The `/graph` endpoints provide access to the RDF graph representation of resources with support for multiple serialization formats through content negotiation.

### Supported RDF Formats

- **JSON-LD** (`application/ld+json`) - Default format
- **Turtle** (`text/turtle`)
- **RDF/XML** (`application/rdf+xml`)
- **N-Triples** (`application/n-triples`)
- **N-Quads** (`application/n-quads`)

### Content Negotiation

Use the `Accept` header to specify the desired RDF format:

```bash
# Get as JSON-LD (default)
curl -H "Accept: application/ld+json" http://localhost:8080/v1/datasets/123/graph

# Get as Turtle
curl -H "Accept: text/turtle" http://localhost:8080/v1/datasets/123/graph

# Get as RDF/XML
curl -H "Accept: application/rdf+xml" http://localhost:8080/v1/datasets/123/graph
```

If no `Accept` header is provided, the endpoint defaults to JSON-LD format.

## Configuration

The application uses environment variables for configuration:

- `POSTGRES_HOST` - PostgreSQL host (default: localhost)
- `POSTGRES_PORT` - PostgreSQL port (default: 5432)
- `POSTGRES_DB` - Database name (default: fdk_resource)
- `POSTGRES_USERNAME` - Database username (default: postgres)
- `POSTGRES_PASSWORD` - Database password (default: postgres)
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka bootstrap servers (default: kafka:9092)
- `SCHEMA_REGISTRY_URL` - Schema registry URL (default: http://schema-registry:8081)
- `JWT_ISSUER_URI` - JWT issuer URI
- `JWT_JWK_SET_URI` - JWT JWK set URI

## Development

### Project Structure

```
src/main/kotlin/no/fdk/resourceservice/
├── controller/          # REST controllers
├── model/              # Data models and entities
├── repository/         # Data access layer
├── service/           # Business logic
├── kafka/             # Kafka consumers
└── config/            # Configuration classes
```

### Database Migrations

Database migrations are handled by Flyway and located in `src/main/resources/db/migration/`.

### Testing

The project includes both unit tests and integration tests using Testcontainers:

```bash
# Run all tests
mvn verify

# Run only unit tests
mvn test

# Run only integration tests
mvn verify -Dtest=*IntegrationTest
```

See [TESTING.md](TESTING.md) for more details on testing.

## Monitoring

The application includes:

- Health checks at `/actuator/health`
- Actuator endpoints for monitoring (`/actuator/metrics`, `/actuator/prometheus`)
- Structured logging with SLF4J
- Metrics collection via Micrometer

## Security

- OAuth2 JWT authentication for secured endpoints
- Input validation and sanitization
- SQL injection prevention through parameterized queries
- CORS configuration for cross-origin requests

## License

See [LICENSE](LICENSE) file for details.
