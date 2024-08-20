package security

import (
	"context"
	"github.com/golang-jwt/jwt/v5"
	"net/http"
	"strings"
	"time"

	"github.com/Nerzal/gocloak/v13"
	"github.com/gin-gonic/gin"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/env"
)

func respondWithError(c *gin.Context, code int, message interface{}) {
	c.AbortWithStatusJSON(code, gin.H{"error": message})
}

func validateTokenAndParseAuthorities(token string) (string, int) {
	client := gocloak.NewClient(env.KeycloakHost())

	ctx := context.Background()
	_, claims, err := client.DecodeAccessToken(ctx, token, "fdk")

	authorities := ""
	errStatus := http.StatusOK

	if err != nil {
		errStatus = http.StatusUnauthorized
	} else if claims == nil {
		errStatus = http.StatusForbidden
	} else {
		var v = jwt.NewValidator(
			jwt.WithLeeway(5*time.Second),
			jwt.WithAudience(env.SecurityValues.TokenAudience),
		)
		validError := v.Validate(claims)
		if validError != nil {
			errStatus = http.StatusForbidden
		}

		authClaim := (*claims)["authorities"]
		if authClaim != nil {
			authorities = authClaim.(string)
		}
	}

	return authorities, errStatus
}

func hasSystemAdminRole(authorities string) bool {
	sysAdminAuth := env.SecurityValues.SysAdminAuth
	return strings.Contains(authorities, sysAdminAuth)
}

func AuthenticateSysAdmin() gin.HandlerFunc {
	return func(c *gin.Context) {
		authorities, status := validateTokenAndParseAuthorities(c.GetHeader("Authorization"))

		if status != http.StatusOK {
			respondWithError(c, status, http.StatusText(status))
		} else if !hasSystemAdminRole(authorities) {
			respondWithError(c, http.StatusForbidden, http.StatusText(http.StatusForbidden))
		}

		c.Next()
	}
}
