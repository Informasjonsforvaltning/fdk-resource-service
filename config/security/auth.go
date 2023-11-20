package security

import (
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/env"
	"github.com/gin-gonic/gin"
	"net/http"
)

func ValidateAPIKey() gin.HandlerFunc {
	return func(c *gin.Context) {
		APIKey := c.Request.Header.Get("X-API-Key")
		if APIKey != env.ApiKey() {
			c.JSON(http.StatusUnauthorized, gin.H{"status": 401, "message": "Authentication failed"})
		}
		return
	}
}
