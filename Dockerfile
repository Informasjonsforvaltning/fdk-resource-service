FROM golang:1.25-bookworm AS build-env

ARG APP_NAME=fdk-resource-service
ARG CMD_PATH=main.go

COPY . $GOPATH/src/$APP_NAME
WORKDIR $GOPATH/src/$APP_NAME

RUN CGO_ENABLED=1 go build -v -o /$APP_NAME $GOPATH/src/$APP_NAME/$CMD_PATH

FROM debian:bookworm-slim

# Install the ca-certificates package
RUN apt-get update && apt-get install -y ca-certificates && rm -rf /var/lib/apt/lists/*

ENV APP_NAME=fdk-resource-service
ENV GIN_MODE=release

COPY --from=build-env /$APP_NAME /$APP_NAME

EXPOSE 8080

CMD ["/fdk-resource-service"]
