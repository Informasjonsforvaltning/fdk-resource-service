package handlers

import (
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetServices() func(c *gin.Context) {
	serviceService := service.InitServiceService()
	return func(c *gin.Context) {
		services, status := serviceService.GetServices(c.Request.Context(), c.Query("includeRemoved"))
		if status == http.StatusOK {
			c.JSON(status, services)
		} else {
			c.Status(status)
		}
	}
}

func GetService() func(c *gin.Context) {
	serviceService := service.InitServiceService()
	return func(c *gin.Context) {
		id := c.Param("id")
		service, status := serviceService.GetService(c.Request.Context(), id)
		if status == http.StatusOK {
			c.JSON(status, service)
		} else {
			c.Status(status)
		}
	}
}
