package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
)

func GetInformationModels() func(c *gin.Context) {
	informationModelService := service.InitInformationModelService()
	return func(c *gin.Context) {
		informationModels, status := informationModelService.GetInformationModels(c.Request.Context(), c.Query("includeRemoved"))
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

func StoreInformationModels() func(c *gin.Context) {
	informationModelService := service.InitInformationModelService()
	return func(c *gin.Context) {
		bytes, err := c.GetRawData()

		if err != nil {
			logrus.Errorf("Unable to get bytes from request.")
			logger.LogAndPrintError(err)

			c.JSON(http.StatusBadRequest, err.Error())
		} else {
			status := informationModelService.StoreInformationModels(c.Request.Context(), bytes)
			c.Status(status)
		}
	}
}
