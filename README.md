# fdk-resource-service
Service for accessing FDK data

The only mandatory field for any resource is the unique id field, which can be used to GET the resource. See the [openapi specification](https://raw.githubusercontent.com/Informasjonsforvaltning/fdk-resource-service/main/openapi.yaml) for the list of supported resources and associated endpoints.

## Testing the project
```
// Run all tests
go test ./test
// Run tests with coverage report
go test -v -race -coverpkg=./... -coverprofile=coverage.txt -covermode=atomic ./test
```

## Running the project

```
go run main.go
```

This will start an HTTP server on localhost:8080.
