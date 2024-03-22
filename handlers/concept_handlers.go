package handlers

import (
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetConcepts() func(c *gin.Context) {
	conceptService := service.InitConceptService()
	return func(c *gin.Context) {
		concepts, status := conceptService.GetConcepts(c.Request.Context())
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
