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

type TestDataService struct {
	ID          string            `json:"id"`
	Type        string            `json:"type"`
	Uri         string            `json:"uri"`
	Identifier  string            `json:"identifier"`
	Title       map[string]string `json:"title"`
	Description map[string]string `json:"description"`
}

func TestGetDataService(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/data-services/123", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	expectedResponse := TestDataService{
		ID:         "123",
		Type:       "dataServices",
		Uri:        "https://data-services.digdir.no/321",
		Identifier: "321",
		Title: map[string]string{
			"nb": "data service nb",
			"nn": "data service nn",
			"en": "data service en",
		},
		Description: map[string]string{
			"nb": "data service desc nb",
			"nn": "data service desc nn",
			"en": "data service desc en",
		},
	}

	var actualResponse TestDataService
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, expectedResponse, actualResponse)
}

func TestGetDataServices(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/data-services", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestDataService
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 0)

	var ids []string
	for _, dataService := range actualResponse {
		ids = append(ids, dataService.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.False(t, slices.Contains(ids, "222"))
}

func TestGetDataServicesIncludeRemoved(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/data-services?includeRemoved=true", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestDataService
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 1)

	var ids []string
	for _, dataService := range actualResponse {
		ids = append(ids, dataService.ID)
	}
	assert.True(t, slices.Contains(ids, "222"))
}
