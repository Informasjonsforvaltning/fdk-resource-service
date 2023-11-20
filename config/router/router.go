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

	e.POST(env.PathValues.Datasets, security.ValidateAPIKey(), handlers.StoreDatasets())
	e.GET(env.PathValues.Datasets, handlers.GetDatasets())
	e.GET(env.PathValues.Dataset, handlers.GetDataset())
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
