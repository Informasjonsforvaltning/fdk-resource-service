package test

import (
	"bytes"
	"encoding/json"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/router"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/stretchr/testify/assert"
	"net/http"
	"net/http/httptest"
	"testing"
)

func TestGetDataset(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/datasets/123", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	expectedResponse := model.Dataset{
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

	var actualResponse model.Dataset
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, expectedResponse, actualResponse)
}

func TestGetUpdates(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/datasets", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []model.Dataset
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 1)
}

func TestUnauthorizedCreate(t *testing.T) {
	app := router.SetupRouter()

	dataset := model.Dataset{
		ID: "999",
	}
	var toBeStored []model.Dataset
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

	dataset0 := model.Dataset{
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

	dataset1 := model.Dataset{
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

	var toBeStored []model.Dataset
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

	var created model.Dataset
	err0 := json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, dataset0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/datasets/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated model.Dataset
	err1 := json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, dataset1, updated)
}

func TestAbortCompleteUpdateWhenOneFails(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	dataset0 := model.Dataset{
		ID:   "123",
		Type: "is-aborted",
	}

	dataset1 := model.Dataset{
		ID:   "",
		Type: "invalid-dataset",
	}

	var toBeStored []model.Dataset
	toBeStored = append(toBeStored, dataset0)
	toBeStored = append(toBeStored, dataset1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/datasets", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/datasets/123", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusOK, wGet.Code)

	var dataset model.Dataset
	errGet := json.Unmarshal(wGet.Body.Bytes(), &dataset)
	assert.Nil(t, errGet)
	assert.Equal(t, "datasets", dataset.Type)
}
