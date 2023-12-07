package handlers

import (
	"net/http"

	"github.com/gin-gonic/gin"
	"github.com/sirupsen/logrus"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
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

func StoreEvents() func(c *gin.Context) {
	eventService := service.InitEventService()
	return func(c *gin.Context) {
		bytes, err := c.GetRawData()

		if err != nil {
			logrus.Errorf("Unable to get bytes from request.")
			logger.LogAndPrintError(err)

			c.JSON(http.StatusBadRequest, err.Error())
		} else {
			status := eventService.StoreEvents(c.Request.Context(), bytes)
			c.Status(status)
		}
	}
}
