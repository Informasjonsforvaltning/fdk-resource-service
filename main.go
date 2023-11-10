package main

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/router"
)

func main() {
	logger.ConfigureLogger()

	app := router.SetupRouter()
	app.Run(":8080")
}
