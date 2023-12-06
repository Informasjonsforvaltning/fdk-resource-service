package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
)

func GetConcepts() func(c *gin.Context) {
	conceptService := service.InitConceptService()
	return func(c *gin.Context) {
		concepts, status := conceptService.GetConcepts(c.Request.Context(), c.Query("includeRemoved"))
		if status == http.StatusOK {
			c.JSON(status, concepts)
		} else {
			c.Status(status)
		}
	}
}

func GetConcept() func(c *gin.Context) {
	conceptService := service.InitConceptService()
	return func(c *gin.Context) {
		id := c.Param("id")
		concept, status := conceptService.GetConcept(c.Request.Context(), id)
		if status == http.StatusOK {
			c.JSON(status, concept)
		} else {
			c.Status(status)
		}
	}
}

func StoreConcepts() func(c *gin.Context) {
	conceptService := service.InitConceptService()
	return func(c *gin.Context) {
		bytes, err := c.GetRawData()

		if err != nil {
			logrus.Errorf("Unable to get bytes from request.")
			logger.LogAndPrintError(err)

			c.JSON(http.StatusBadRequest, err.Error())
		} else {
			status := conceptService.StoreConcepts(c.Request.Context(), bytes)
			c.Status(status)
		}
	}
}
