package test

import (
	"context"
	"encoding/json"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/router"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
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
	assert.True(t, slices.Contains(ids, "222"))
}

func TestCreateResource(t *testing.T) {
	datasetService := service.InitDatasetService()

	dataset0 := TestDataset{
		ID:         "000",
		Type:       "datasets",
		Uri:        "https://datasets.digdir.no/987",
		Identifier: "987",
		Title: map[string]string{
			"nb": "nb",
			"nn": "nn",
			"en": "en",
		},
		Description: map[string]string{
			"nb": "desc nb",
			"nn": "desc nn",
			"en": "desc en",
		},
	}

	dataset0Bytes, _ := json.Marshal(dataset0)
	err0 := datasetService.StoreDataset(context.TODO(), dataset0Bytes, 100)
	assert.Nil(t, err0)

	dataset1 := TestDataset{
		ID:         "111",
		Type:       "datasets",
		Uri:        "https://datasets.digdir.no/654",
		Identifier: "654",
		Title: map[string]string{
			"nb": "updated dataset nb",
			"nn": "updated dataset nn",
			"en": "updated dataset en",
		},
		Description: map[string]string{
			"nb": "updated dataset desc nb",
			"nn": "updated dataset desc nn",
			"en": "updated dataset desc en",
		},
	}

	dataset1Bytes, _ := json.Marshal(dataset1)
	err1 := datasetService.StoreDataset(context.TODO(), dataset1Bytes, 101)
	assert.Nil(t, err1)

	app := router.SetupRouter()

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/datasets/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestDataset
	err0 = json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, dataset0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/datasets/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestDataset
	err1 = json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, dataset1, updated)
}
