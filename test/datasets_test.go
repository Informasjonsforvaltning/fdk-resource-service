package test

import (
	"bytes"
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

func TestUnauthorizedCreate(t *testing.T) {
	app := router.SetupRouter()

	dataset := TestDataset{
		ID: "999",
	}
	var toBeStored []TestDataset
	toBeStored = append(toBeStored, dataset)
	body, _ := json.Marshal(toBeStored)

	wWrongKey := httptest.NewRecorder()
	reqWrongKey, _ := http.NewRequest("POST", "/datasets", bytes.NewBuffer(body))
	reqWrongKey.Header.Set("X-API-Key", "wrong")
	app.ServeHTTP(wWrongKey, reqWrongKey)

	assert.Equal(t, http.StatusUnauthorized, wWrongKey.Code)

	wMissingKey := httptest.NewRecorder()
	reqMissingKey, _ := http.NewRequest("POST", "/datasets", bytes.NewBuffer(body))
	app.ServeHTTP(wMissingKey, reqMissingKey)

	assert.Equal(t, http.StatusUnauthorized, wMissingKey.Code)
}

func TestCreateResource(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

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

	var toBeStored []TestDataset
	toBeStored = append(toBeStored, dataset0)
	toBeStored = append(toBeStored, dataset1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/datasets", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/datasets/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestDataset
	err0 := json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, dataset0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/datasets/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestDataset
	err1 := json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, dataset1, updated)
}

func TestAbortCompleteUpdateWhenOneFails(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	dataset0 := TestDataset{
		ID:   "123",
		Type: "is-aborted",
	}

	dataset1 := TestDataset{
		ID:   "",
		Type: "invalid-dataset",
	}

	var toBeStored []TestDataset
	toBeStored = append(toBeStored, dataset0)
	toBeStored = append(toBeStored, dataset1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/datasets", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/datasets/123", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusOK, wGet.Code)

	var dataset TestDataset
	errGet := json.Unmarshal(wGet.Body.Bytes(), &dataset)
	assert.Nil(t, errGet)
	assert.Equal(t, "datasets", dataset.Type)
}
