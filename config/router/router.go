package router

import (
	"github.com/gin-gonic/gin"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/env"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/security"
	"github.com/Informasjonsforvaltning/fdk-resource-service/handlers"
)

func InitializeRoutes(e *gin.Engine) {
	e.SetTrustedProxies(nil)
	e.GET(env.PathValues.Ping, handlers.PingHandler())
	e.GET(env.PathValues.Ready, handlers.ReadyHandler())

	e.POST(env.PathValues.Concepts, security.ValidateAPIKey(), handlers.StoreConcepts())
	e.GET(env.PathValues.Concepts, handlers.GetConcepts())
	e.GET(env.PathValues.Concept, handlers.GetConcept())

	e.POST(env.PathValues.DataServices, security.ValidateAPIKey(), handlers.StoreDataServices())
	e.GET(env.PathValues.DataServices, handlers.GetDataServices())
	e.GET(env.PathValues.DataService, handlers.GetDataService())

	e.POST(env.PathValues.Datasets, security.ValidateAPIKey(), handlers.StoreDatasets())
	e.GET(env.PathValues.Datasets, handlers.GetDatasets())
	e.GET(env.PathValues.Dataset, handlers.GetDataset())

	e.POST(env.PathValues.Events, security.ValidateAPIKey(), handlers.StoreEvents())
	e.GET(env.PathValues.Events, handlers.GetEvents())
	e.GET(env.PathValues.Event, handlers.GetEvent())

	e.POST(env.PathValues.InformationModels, security.ValidateAPIKey(), handlers.StoreInformationModels())
	e.GET(env.PathValues.InformationModels, handlers.GetInformationModels())
	e.GET(env.PathValues.InformationModel, handlers.GetInformationModel())

	e.POST(env.PathValues.Services, security.ValidateAPIKey(), handlers.StoreServices())
	e.GET(env.PathValues.Services, handlers.GetServices())
	e.GET(env.PathValues.Service, handlers.GetService())
}

func Cors() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		if c.Request.Method == "OPTIONS" {
			c.AbortWithStatus(204)
			return
		}
		c.Next()
	}
}

func SetupRouter() *gin.Engine {
	router := gin.New()
	router.Use(gin.Recovery())
	router.Use(Cors())

	InitializeRoutes(router)

	return router
}
