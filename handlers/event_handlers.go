package handlers

import (
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetEvents() func(c *gin.Context) {
	eventService := service.InitEventService()
	return func(c *gin.Context) {
		events, status := eventService.GetEvents(c.Request.Context(), c.Query("includeRemoved"))
		if status == http.StatusOK {
			c.JSON(status, events)
		} else {
			c.Status(status)
		}
	}
}

func GetEvent() func(c *gin.Context) {
	eventService := service.InitEventService()
	return func(c *gin.Context) {
		id := c.Param("id")
		event, status := eventService.GetEvent(c.Request.Context(), id)
		if status == http.StatusOK {
			c.JSON(status, event)
		} else {
			c.Status(status)
		}
	}
}
