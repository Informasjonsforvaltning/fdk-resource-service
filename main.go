package main

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/router"
	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
)

func main() {
	logger.ConfigureLogger()

	go kafka.ConsumeKafkaEvents()

	app := router.SetupRouter()
	app.Run(":8080")
}
