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

type TestInformationModel struct {
	ID          string            `json:"id"`
	Type        string            `json:"type"`
	Uri         string            `json:"uri"`
	Identifier  string            `json:"identifier"`
	Title       map[string]string `json:"title"`
	Description map[string]string `json:"description"`
}

func TestGetInformationModel(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/information-models/123", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	expectedResponse := TestInformationModel{
		ID:         "123",
		Type:       "informationModels",
		Uri:        "https://information-models.digdir.no/321",
		Identifier: "321",
		Title: map[string]string{
			"nb": "information model nb",
			"nn": "information model nn",
			"en": "information model en",
		},
		Description: map[string]string{
			"nb": "information model desc nb",
			"nn": "information model desc nn",
			"en": "information model desc en",
		},
	}

	var actualResponse TestInformationModel
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, expectedResponse, actualResponse)
}

func TestGetInformationModels(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/information-models", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestInformationModel
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 0)

	var ids []string
	for _, informationModel := range actualResponse {
		ids = append(ids, informationModel.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.False(t, slices.Contains(ids, "222"))
}

func TestGetInformationModelsIncludeRemoved(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/information-models?includeRemoved=true", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestInformationModel
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 1)

	var ids []string
	for _, informationModel := range actualResponse {
		ids = append(ids, informationModel.ID)
	}
	assert.True(t, slices.Contains(ids, "222"))
}

func TestUnauthorizedCreateOfInformationModels(t *testing.T) {
	app := router.SetupRouter()

	informationModel := TestInformationModel{
		ID: "999",
	}
	var toBeStored []TestInformationModel
	toBeStored = append(toBeStored, informationModel)
	body, _ := json.Marshal(toBeStored)

	wWrongKey := httptest.NewRecorder()
	reqWrongKey, _ := http.NewRequest("POST", "/information-models", bytes.NewBuffer(body))
	reqWrongKey.Header.Set("X-API-Key", "wrong")
	app.ServeHTTP(wWrongKey, reqWrongKey)

	assert.Equal(t, http.StatusUnauthorized, wWrongKey.Code)

	wMissingKey := httptest.NewRecorder()
	reqMissingKey, _ := http.NewRequest("POST", "/information-models", bytes.NewBuffer(body))
	app.ServeHTTP(wMissingKey, reqMissingKey)

	assert.Equal(t, http.StatusUnauthorized, wMissingKey.Code)
}

func TestCreateInformationModel(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	informationModel0 := TestInformationModel{
		ID:         "000",
		Type:       "informationModels",
		Uri:        "https://information-models.digdir.no/987",
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

	informationModel1 := TestInformationModel{
		ID:         "111",
		Type:       "informationModels",
		Uri:        "https://information-models.digdir.no/654",
		Identifier: "654",
		Title: map[string]string{
			"nb": "updated information model nb",
			"nn": "updated information model nn",
			"en": "updated information model en",
		},
		Description: map[string]string{
			"nb": "updated information model desc nb",
			"nn": "updated information model desc nn",
			"en": "updated information model desc en",
		},
	}

	var toBeStored []TestInformationModel
	toBeStored = append(toBeStored, informationModel0)
	toBeStored = append(toBeStored, informationModel1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/information-models", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/information-models/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestInformationModel
	err0 := json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, informationModel0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/information-models/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestInformationModel
	err1 := json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, informationModel1, updated)
}

func TestAbortInformationModelUpdateWhenOneFails(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	informationModel0 := TestInformationModel{
		ID:   "123",
		Type: "is-aborted",
	}

	informationModel1 := TestInformationModel{
		ID:   "",
		Type: "invalid-information-model",
	}

	var toBeStored []TestInformationModel
	toBeStored = append(toBeStored, informationModel0)
	toBeStored = append(toBeStored, informationModel1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/information-models", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/information-models/123", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusOK, wGet.Code)

	var informationModel TestInformationModel
	errGet := json.Unmarshal(wGet.Body.Bytes(), &informationModel)
	assert.Nil(t, errGet)
	assert.Equal(t, "informationModels", informationModel.Type)
}
