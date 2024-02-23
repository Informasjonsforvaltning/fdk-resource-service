package handlers

import (
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetDatasets() func(c *gin.Context) {
	datasetService := service.InitDatasetService()
	return func(c *gin.Context) {
		datasets, status := datasetService.GetDatasets(c.Request.Context(), c.Query("includeRemoved"))
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
