package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
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

func StoreServices() func(c *gin.Context) {
	serviceService := service.InitServiceService()
	return func(c *gin.Context) {
		bytes, err := c.GetRawData()

		if err != nil {
			logrus.Errorf("Unable to get bytes from request.")
			logger.LogAndPrintError(err)

			c.JSON(http.StatusBadRequest, err.Error())
		} else {
			status := serviceService.StoreServices(c.Request.Context(), bytes)
			c.Status(status)
		}
	}
}
