package handlers

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"github.com/sirupsen/logrus"
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetConcepts() func(c *gin.Context) {
	conceptService := service.InitConceptService()
	return func(c *gin.Context) {
		concepts, status := conceptService.GetConcepts(c.Request.Context(), nil)
		if status == http.StatusOK {
			c.JSON(status, concepts)
		} else {
			c.Status(status)
		}
	}
}

func FilterConcepts() func(c *gin.Context) {
	conceptService := service.InitConceptService()
	return func(c *gin.Context) {
		var filters model.Filters
		err := c.BindJSON(&filters)
		if err != nil {
			c.Status(http.StatusBadRequest)
		} else {
			concepts, status := conceptService.GetConcepts(c.Request.Context(), &filters)
			if status == http.StatusOK {
				c.JSON(status, concepts)
			} else {
				c.Status(status)
			}
		}
	}
}

func GetConcept() func(c *gin.Context) {
	conceptService := service.InitConceptService()
	return func(c *gin.Context) {
		id := c.Param("id")
		concept, status := conceptService.GetConcept(c.Request.Context(), validate.SanitizeID(id))
		if status == http.StatusOK {
			c.JSON(status, concept)
		} else {
			c.Status(status)
		}
	}
}

func DeleteConcept() func(c *gin.Context) {
	conceptService := service.InitConceptService()
	return func(c *gin.Context) {
		id := c.Param("id")
		logrus.Infof("Deleting concept with id %s", id)

		status := conceptService.DeleteConcept(c.Request.Context(), validate.SanitizeID(id))
		c.Status(status)
	}
}
