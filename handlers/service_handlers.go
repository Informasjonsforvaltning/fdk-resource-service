package handlers

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetServices() func(c *gin.Context) {
	serviceService := service.InitServiceService()
	return func(c *gin.Context) {
		services, status := serviceService.GetServices(c.Request.Context(), nil)
		if status == http.StatusOK {
			c.JSON(status, services)
		} else {
			c.Status(status)
		}
	}
}

func FilterServices() func(c *gin.Context) {
	serviceService := service.InitServiceService()
	return func(c *gin.Context) {
		var filters model.Filters
		err := c.BindJSON(&filters)
		if err != nil {
			c.Status(http.StatusBadRequest)
		} else {
			services, status := serviceService.GetServices(c.Request.Context(), &filters)
			if status == http.StatusOK {
				c.JSON(status, services)
			} else {
				c.Status(status)
			}
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
