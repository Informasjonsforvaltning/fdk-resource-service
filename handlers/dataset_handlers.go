package handlers

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetDatasets() func(c *gin.Context) {
	datasetService := service.InitDatasetService()
	return func(c *gin.Context) {
		datasets, status := datasetService.GetDatasets(c.Request.Context(), nil)
		if status == http.StatusOK {
			c.JSON(status, datasets)
		} else {
			c.Status(status)
		}
	}
}

func FilterDatasets() func(c *gin.Context) {
	datasetService := service.InitDatasetService()
	return func(c *gin.Context) {
		var filters model.Filters
		err := c.BindJSON(&filters)
		if err != nil {
			c.Status(http.StatusBadRequest)
		} else {
			datasets, status := datasetService.GetDatasets(c.Request.Context(), &filters)
			if status == http.StatusOK {
				c.JSON(status, datasets)
			} else {
				c.Status(status)
			}
		}
	}
}

func GetDataset() func(c *gin.Context) {
	datasetService := service.InitDatasetService()
	return func(c *gin.Context) {
		id := c.Param("id")
		dataset, status := datasetService.GetDataset(c.Request.Context(), validate.SanitizeID(id))
		if status == http.StatusOK {
			c.JSON(status, dataset)
		} else {
			c.Status(status)
		}
	}
}
