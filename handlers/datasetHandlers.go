package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
)

func GetDatasets() func(c *gin.Context) {
	datasetService := service.InitDatasetService()
	return func(c *gin.Context) {
		datasets, status := datasetService.GetDatasets(c.Request.Context())
		if status == http.StatusOK {
			c.JSON(status, datasets)
		} else {
			c.Status(status)
		}
	}
}

func GetDataset() func(c *gin.Context) {
	datasetService := service.InitDatasetService()
	return func(c *gin.Context) {
		id := c.Param("id")
		dataset, status := datasetService.GetDataset(c.Request.Context(), id)
		if status == http.StatusOK {
			c.JSON(status, dataset)
		} else {
			c.Status(status)
		}
	}
}

func StoreDatasets() func(c *gin.Context) {
	datasetService := service.InitDatasetService()
	return func(c *gin.Context) {
		bytes, err := c.GetRawData()

		if err != nil {
			logrus.Errorf("Unable to get bytes from request.")
			logger.LogAndPrintError(err)

			c.JSON(http.StatusBadRequest, err.Error())
		} else {
			status := datasetService.StoreDatasets(c.Request.Context(), bytes)
			c.Status(status)
		}
	}
}
