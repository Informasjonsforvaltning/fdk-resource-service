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

type TestDataset struct {
	ID          string            `json:"id"`
	Type        string            `json:"type"`
	Uri         string            `json:"uri"`
	Identifier  string            `json:"identifier"`
	Title       map[string]string `json:"title"`
	Description map[string]string `json:"description"`
}

func TestGetDataset(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/datasets/123", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	expectedResponse := TestDataset{
		ID:         "123",
		Type:       "datasets",
		Uri:        "https://datasets.digdir.no/321",
		Identifier: "321",
		Title: map[string]string{
			"nb": "dataset nb",
			"nn": "dataset nn",
			"en": "dataset en",
		},
		Description: map[string]string{
			"nb": "dataset desc nb",
			"nn": "dataset desc nn",
			"en": "dataset desc en",
		},
	}

	var actualResponse TestDataset
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, expectedResponse, actualResponse)
}

func TestGetDatasets(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/datasets", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestDataset
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 0)

	var ids []string
	for _, dataset := range actualResponse {
		ids = append(ids, dataset.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.False(t, slices.Contains(ids, "222"))
}

func TestGetDatasetsIncludeRemoved(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/datasets?includeRemoved=true", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestDataset
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 1)

	var ids []string
	for _, dataset := range actualResponse {
		ids = append(ids, dataset.ID)
	}
	assert.True(t, slices.Contains(ids, "222"))
}
