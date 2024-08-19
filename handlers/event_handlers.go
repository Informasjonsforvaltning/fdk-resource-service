package handlers

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"net/http"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/gin-gonic/gin"
)

func GetEvents() func(c *gin.Context) {
	eventService := service.InitEventService()
	return func(c *gin.Context) {
		events, status := eventService.GetEvents(c.Request.Context(), nil)
		if status == http.StatusOK {
			c.JSON(status, events)
		} else {
			c.Status(status)
		}
	}
}

func FilterEvents() func(c *gin.Context) {
	eventService := service.InitEventService()
	return func(c *gin.Context) {
		var filters model.Filters
		err := c.BindJSON(&filters)
		if err != nil {
			c.Status(http.StatusBadRequest)
		} else {
			events, status := eventService.GetEvents(c.Request.Context(), &filters)
			if status == http.StatusOK {
				c.JSON(status, events)
			} else {
				c.Status(status)
			}
		}
	}
}

func GetEvent() func(c *gin.Context) {
	eventService := service.InitEventService()
	return func(c *gin.Context) {
		id := c.Param("id")
		event, status := eventService.GetEvent(c.Request.Context(), validate.SanitizeID(id))
		if status == http.StatusOK {
			c.JSON(status, event)
		} else {
			c.Status(status)
		}
	}
}
