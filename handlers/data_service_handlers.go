package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
)

func GetDataServices() func(c *gin.Context) {
	dataServiceService := service.InitDataServiceService()
	return func(c *gin.Context) {
		dataServices, status := dataServiceService.GetDataServices(c.Request.Context(), c.Query("includeRemoved"))
		if status == http.StatusOK {
			c.JSON(status, dataServices)
		} else {
			c.Status(status)
		}
	}
}

func GetDataService() func(c *gin.Context) {
	dataServiceService := service.InitDataServiceService()
	return func(c *gin.Context) {
		id := c.Param("id")
		dataService, status := dataServiceService.GetDataService(c.Request.Context(), id)
		if status == http.StatusOK {
			c.JSON(status, dataService)
		} else {
			c.Status(status)
		}
	}
}
