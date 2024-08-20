package handlers

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"github.com/sirupsen/logrus"
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetInformationModels() func(c *gin.Context) {
	informationModelService := service.InitInformationModelService()
	return func(c *gin.Context) {
		informationModels, status := informationModelService.GetInformationModels(c.Request.Context(), nil)
		if status == http.StatusOK {
			c.JSON(status, informationModels)
		} else {
			c.Status(status)
		}
	}
}

func FilterInformationModels() func(c *gin.Context) {
	informationModelService := service.InitInformationModelService()
	return func(c *gin.Context) {
		var filters model.Filters
		err := c.BindJSON(&filters)
		if err != nil {
			c.Status(http.StatusBadRequest)
		} else {
			informationModels, status := informationModelService.GetInformationModels(c.Request.Context(), &filters)
			if status == http.StatusOK {
				c.JSON(status, informationModels)
			} else {
				c.Status(status)
			}
		}
	}
}

func GetInformationModel() func(c *gin.Context) {
	informationModelService := service.InitInformationModelService()
	return func(c *gin.Context) {
		id := c.Param("id")
		informationModel, status := informationModelService.GetInformationModel(c.Request.Context(), validate.SanitizeID(id))
		if status == http.StatusOK {
			c.JSON(status, informationModel)
		} else {
			c.Status(status)
		}
	}
}

func DeleteInformationModel() func(c *gin.Context) {
	informationModelService := service.InitInformationModelService()
	return func(c *gin.Context) {
		id := c.Param("id")
		logrus.Infof("Deleting information model with id %s", id)

		status := informationModelService.DeleteInformationModel(c.Request.Context(), validate.SanitizeID(id))
		c.Status(status)
	}
}
