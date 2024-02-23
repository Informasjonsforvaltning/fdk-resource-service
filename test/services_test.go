package test

import (
	"encoding/json"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/router"
	"github.com/stretchr/testify/assert"
	"net/http"
	"net/http/httptest"
	"slices"
	"testing"
)

type TestService struct {
	ID          string            `json:"id"`
	Type        string            `json:"type"`
	Uri         string            `json:"uri"`
	Identifier  string            `json:"identifier"`
	Title       map[string]string `json:"title"`
	Description map[string]string `json:"description"`
}

func TestGetService(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/services/123", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	expectedResponse := TestService{
		ID:         "123",
		Type:       "services",
		Uri:        "https://services.digdir.no/321",
		Identifier: "321",
		Title: map[string]string{
			"nb": "service nb",
			"nn": "service nn",
			"en": "service en",
		},
		Description: map[string]string{
			"nb": "service desc nb",
			"nn": "service desc nn",
			"en": "service desc en",
		},
	}

	var actualResponse TestService
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, expectedResponse, actualResponse)
}

func TestGetServices(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/services", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestService
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 0)

	var ids []string
	for _, service := range actualResponse {
		ids = append(ids, service.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.False(t, slices.Contains(ids, "222"))
}

func TestGetServicesIncludeRemoved(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/services?includeRemoved=true", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestService
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 1)

	var ids []string
	for _, service := range actualResponse {
		ids = append(ids, service.ID)
	}
	assert.True(t, slices.Contains(ids, "222"))
}
