package handlers

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"github.com/sirupsen/logrus"
	"net/http"

	"github.com/gin-gonic/gin"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
)

func GetDataServices() func(c *gin.Context) {
	dataServiceService := service.InitDataServiceService()
	return func(c *gin.Context) {
		dataServices, status := dataServiceService.GetDataServices(c.Request.Context(), nil)
		if status == http.StatusOK {
			c.JSON(status, dataServices)
		} else {
			c.Status(status)
		}
	}
}

func FilterDataServices() func(c *gin.Context) {
	dataServiceService := service.InitDataServiceService()
	return func(c *gin.Context) {
		var filters model.Filters
		err := c.BindJSON(&filters)
		if err != nil {
			c.Status(http.StatusBadRequest)
		} else {
			dataServices, status := dataServiceService.GetDataServices(c.Request.Context(), &filters)
			if status == http.StatusOK {
				c.JSON(status, dataServices)
			} else {
				c.Status(status)
			}
		}
	}
}

func GetDataService() func(c *gin.Context) {
	dataServiceService := service.InitDataServiceService()
	return func(c *gin.Context) {
		id := c.Param("id")
		dataService, status := dataServiceService.GetDataService(c.Request.Context(), validate.SanitizeID(id))
		if status == http.StatusOK {
			c.JSON(status, dataService)
		} else {
			c.Status(status)
		}
	}
}

func DeleteDataService() func(c *gin.Context) {
	dataServiceService := service.InitDataServiceService()
	return func(c *gin.Context) {
		id := c.Param("id")
		logrus.Infof("Deleting data service with id %s", id)

		status := dataServiceService.DeleteDataService(c.Request.Context(), validate.SanitizeID(id))
		c.Status(status)
	}
}
