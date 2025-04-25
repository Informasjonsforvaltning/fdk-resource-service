# FDK Resource Service

This application provides an API for retrieving all resources (datasets, concepts etc.).

For a broader understanding of the system’s context, refer to
the [architecture documentation](https://github.com/Informasjonsforvaltning/architecture-documentation) wiki. For more
specific context on this application, see the **Portal** subsystem section.

## Getting Started

These instructions will give you a copy of the project up and running on your local machine for development and testing
purposes.

### Prerequisites

Ensure you have the following installed:

- Go
- Docker

Clone the repository.

```sh
git clone https://github.com/Informasjonsforvaltning/fdk-resource-service.git
cd fdk-resource-service
```

#### Start MongoDB database, Kafka cluster and setup topics/schemas

Topics and schemas are set up automatically when starting the Kafka cluster. Docker compose uses the scripts
```create-topics.sh``` and ```create-schemas.sh``` to set up topics and schemas.

```
docker-compose up -d
```

If you have problems starting kafka, check if all health checks are ok. Make sure number at the end (after 'grep')
matches desired topics.

#### Install required dependencies

```shell
go get
```

#### Start application

```sh
go run main.go
```

#### Produce messages

Check if schema id is correct in the script. This should be 1 if there is only one schema in your registry.

```
sh ./kafka/produce-messages.sh
```

### API Documentation (OpenAPI)

The API documentation is available at ```openapi.yaml```.

### Running tests

```shell
go test ./test
```

To generate a test coverage report, use the following command:

```shell
go test -v -race -coverpkg=./... -coverprofile=coverage.txt -covermode=atomic ./test
```
