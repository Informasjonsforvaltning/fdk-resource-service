package router

import (
	"github.com/gin-gonic/gin"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/env"
	"github.com/Informasjonsforvaltning/fdk-resource-service/handlers"
)

func InitializeRoutes(e *gin.Engine) {
	e.SetTrustedProxies(nil)
	e.GET(env.PathValues.Ping, handlers.PingHandler())
	e.GET(env.PathValues.Ready, handlers.ReadyHandler())

	e.GET(env.PathValues.Concepts, handlers.GetConcepts())
	e.POST(env.PathValues.Concepts, handlers.FilterConcepts())
	e.GET(env.PathValues.Concept, handlers.GetConcept())

	e.GET(env.PathValues.DataServices, handlers.GetDataServices())
	e.POST(env.PathValues.DataServices, handlers.FilterDataServices())
	e.GET(env.PathValues.DataService, handlers.GetDataService())

	e.GET(env.PathValues.Datasets, handlers.GetDatasets())
	e.POST(env.PathValues.Datasets, handlers.FilterDatasets())
	e.GET(env.PathValues.Dataset, handlers.GetDataset())

	e.GET(env.PathValues.Events, handlers.GetEvents())
	e.POST(env.PathValues.Events, handlers.FilterEvents())
	e.GET(env.PathValues.Event, handlers.GetEvent())

	e.GET(env.PathValues.InformationModels, handlers.GetInformationModels())
	e.POST(env.PathValues.InformationModels, handlers.FilterInformationModels())
	e.GET(env.PathValues.InformationModel, handlers.GetInformationModel())

	e.GET(env.PathValues.Services, handlers.GetServices())
	e.POST(env.PathValues.Services, handlers.FilterServices())
	e.GET(env.PathValues.Service, handlers.GetService())
}

func Cors() gin.HandlerFunc {
	return func(c *gin.Context) {
		c.Writer.Header().Set("Access-Control-Allow-Origin", "*")
		c.Writer.Header().Set("Access-Control-Allow-Methods", "GET, POST")
		c.Writer.Header().Set("Access-Control-Allow-Headers", "Accept, Content-Type")
		c.Writer.Header().Set("Access-Control-Max-Age", "3600")
		c.Writer.Header().Set("Access-Control-Allow-Credentials", "false")
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
