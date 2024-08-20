package handlers

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"github.com/sirupsen/logrus"
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
		serv, status := serviceService.GetService(c.Request.Context(), validate.SanitizeID(id))
		if status == http.StatusOK {
			c.JSON(status, serv)
		} else {
			c.Status(status)
		}
	}
}

func DeleteService() func(c *gin.Context) {
	serviceService := service.InitServiceService()
	return func(c *gin.Context) {
		id := c.Param("id")
		logrus.Infof("Deleting service with id %s", id)

		status := serviceService.DeleteService(c.Request.Context(), validate.SanitizeID(id))
		c.Status(status)
	}
}
