FROM golang:1.21-alpine as build-env

ENV APP_NAME fdk-resource-service
ENV CMD_PATH main.go

COPY . $GOPATH/src/$APP_NAME
WORKDIR $GOPATH/src/$APP_NAME

RUN CGO_ENABLED=0 go build -v -o /$APP_NAME $GOPATH/src/$APP_NAME/$CMD_PATH

FROM alpine

ENV APP_NAME fdk-resource-service
ENV GIN_MODE release

COPY --from=build-env /$APP_NAME .

EXPOSE 8080

CMD ./$APP_NAME
