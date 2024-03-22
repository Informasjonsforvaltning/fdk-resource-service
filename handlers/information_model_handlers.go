package handlers

import (
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetInformationModels() func(c *gin.Context) {
	informationModelService := service.InitInformationModelService()
	return func(c *gin.Context) {
		informationModels, status := informationModelService.GetInformationModels(c.Request.Context())
		if status == http.StatusOK {
			c.JSON(status, informationModels)
		} else {
			c.Status(status)
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
