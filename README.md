# FDK Resource Service

This is a Kotlin/Spring Boot implementation of the FDK Resource Service, migrated from the original Go implementation.

## Features

- REST API for managing resources (concepts, datasets, data services, etc.)
- Kafka event consumption for real-time updates
- PostgreSQL database with JSONB support for flexible resource storage
- JWT-based authentication
- Health checks and monitoring

## Technology Stack

- **Language**: Kotlin
- **Framework**: Spring Boot 3.3.5
- **Database**: PostgreSQL with JSONB
- **Message Queue**: Apache Kafka with Avro serialization
- **Authentication**: OAuth2 JWT
- **Build Tool**: Maven

## Database Design Decision: JPA vs Plain SQL

For this application, we use **JPA with custom SQL queries** for the following reasons:

### Why JPA + Custom SQL:

1. **JSONB Support**: PostgreSQL's JSONB is well-supported by Hibernate with `@JdbcTypeCode(SqlTypes.JSON)`
2. **Type Safety**: JPA provides compile-time type safety and reduces boilerplate
3. **Transaction Management**: Spring's `@Transactional` provides declarative transaction management
4. **Custom Queries**: We can use `@Query` annotations for complex PostgreSQL-specific queries
5. **Performance**: Custom SQL queries allow us to optimize for PostgreSQL's JSONB capabilities

### When to Use Plain SQL:

- Complex analytical queries
- Performance-critical operations requiring raw SQL optimization
- Database-specific features not supported by JPA

## Getting Started

### Prerequisites

- Java 21
- Maven 3.8+
- Docker and Docker Compose

### Running Locally

1. **Start infrastructure services:**

   ```bash
   docker-compose up -d postgres schema-registry kafka
   ```

2. **Run the application:**

   ```bash
   mvn spring-boot:run -Dspring-boot.run.profiles=dev
   ```

3. **Access the API:**
   - Health check: http://localhost:8080/v1/health
   - Concepts: http://localhost:8080/v1/concepts
   - Datasets: http://localhost:8080/v1/datasets
   - Data Services: http://localhost:8080/v1/data-services
   - **Swagger UI**: http://localhost:8080/swagger-ui.html
   - **OpenAPI JSON**: http://localhost:8080/api-docs

### Running with Docker

```bash
docker-compose up --build
```

## API Documentation

The API is fully documented with OpenAPI 3.0 (Swagger) specifications:

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **OpenAPI JSON**: http://localhost:8080/api-docs

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
curl -H "Accept: application/ld+json" /v1/datasets/123/graph

# Get as Turtle
curl -H "Accept: text/turtle" /v1/datasets/123/graph

# Get as RDF/XML
curl -H "Accept: application/rdf+xml" /v1/datasets/123/graph

# Get as N-Triples
curl -H "Accept: application/n-triples" /v1/datasets/123/graph

# Get as N-Quads
curl -H "Accept: application/n-quads" /v1/datasets/123/graph
```

If no `Accept` header is provided, the endpoint defaults to JSON-LD format.

## Configuration

The application uses environment variables for configuration:

- `DATABASE_URL` - PostgreSQL connection string
- `DATABASE_USERNAME` - Database username
- `DATABASE_PASSWORD` - Database password
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka bootstrap servers
- `SCHEMA_REGISTRY_URL` - Schema registry URL
- `JWT_ISSUER_URI` - JWT issuer URI
- `JWT_JWK_SET_URI` - JWT JWK set URI

## Development

### Project Structure

```
src/main/kotlin/no/fdk/fdk_resource_service/
├── controller/          # REST controllers
├── model/              # Data models and DTOs
├── repository/         # Data access layer
├── service/           # Business logic
├── kafka/             # Kafka consumers
└── config/            # Configuration classes
```

### Database Migrations

Database migrations are handled by Flyway and located in `src/main/resources/db/migration/`.

### Testing

```bash
mvn test
```

## Migration from Go

The original Go code has been moved to the `legacy/` folder. The new Kotlin implementation provides:

1. **Same API endpoints** as the original Go service
2. **Enhanced type safety** with Kotlin's type system
3. **Better error handling** with Spring's exception handling
4. **Improved maintainability** with Spring Boot's conventions
5. **Better testing support** with Spring Boot Test

### Legacy Endpoint Support

Temporary redirects (HTTP 307) are provided for legacy endpoints to ensure backward compatibility during migration:

- Legacy endpoints (e.g., `/concepts`, `/datasets`) redirect to v1 endpoints (e.g., `/v1/concepts`, `/v1/datasets`)
- Legacy health endpoints (`/ping`, `/ready`) redirect to `/health`
- All redirects are logged for monitoring purposes

**Note**: These redirects should be removed after clients have migrated to v1 endpoints.

## Monitoring

The application includes:

- Health checks at `/health`
- Actuator endpoints for monitoring
- Structured logging with SLF4J
- Metrics collection

## Security

- OAuth2 JWT authentication
- Input validation and sanitization
- SQL injection prevention through parameterized queries
- CORS configuration for cross-origin requests
