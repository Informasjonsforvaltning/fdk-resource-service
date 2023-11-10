# fdk-resource-service
Service for accessing FDK data

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
