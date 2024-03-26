package handlers

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
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
		informationModel, status := informationModelService.GetInformationModel(c.Request.Context(), id)
		if status == http.StatusOK {
			c.JSON(status, informationModel)
		} else {
			c.Status(status)
		}
	}
}
