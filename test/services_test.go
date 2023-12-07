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

func TestUnauthorizedCreateOfServices(t *testing.T) {
	app := router.SetupRouter()

	service := TestService{
		ID: "999",
	}
	var toBeStored []TestService
	toBeStored = append(toBeStored, service)
	body, _ := json.Marshal(toBeStored)

	wWrongKey := httptest.NewRecorder()
	reqWrongKey, _ := http.NewRequest("POST", "/services", bytes.NewBuffer(body))
	reqWrongKey.Header.Set("X-API-Key", "wrong")
	app.ServeHTTP(wWrongKey, reqWrongKey)

	assert.Equal(t, http.StatusUnauthorized, wWrongKey.Code)

	wMissingKey := httptest.NewRecorder()
	reqMissingKey, _ := http.NewRequest("POST", "/services", bytes.NewBuffer(body))
	app.ServeHTTP(wMissingKey, reqMissingKey)

	assert.Equal(t, http.StatusUnauthorized, wMissingKey.Code)
}

func TestCreateService(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	service0 := TestService{
		ID:         "000",
		Type:       "services",
		Uri:        "https://services.digdir.no/987",
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

	service1 := TestService{
		ID:         "111",
		Type:       "services",
		Uri:        "https://services.digdir.no/654",
		Identifier: "654",
		Title: map[string]string{
			"nb": "updated service nb",
			"nn": "updated service nn",
			"en": "updated service en",
		},
		Description: map[string]string{
			"nb": "updated service desc nb",
			"nn": "updated service desc nn",
			"en": "updated service desc en",
		},
	}

	var toBeStored []TestService
	toBeStored = append(toBeStored, service0)
	toBeStored = append(toBeStored, service1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/services", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/services/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestService
	err0 := json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, service0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/services/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestService
	err1 := json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, service1, updated)
}

func TestAbortServiceUpdateWhenOneFails(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	service0 := TestService{
		ID:   "123",
		Type: "is-aborted",
	}

	service1 := TestService{
		ID:   "",
		Type: "invalid-service",
	}

	var toBeStored []TestService
	toBeStored = append(toBeStored, service0)
	toBeStored = append(toBeStored, service1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/services", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/services/123", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusOK, wGet.Code)

	var service TestService
	errGet := json.Unmarshal(wGet.Body.Bytes(), &service)
	assert.Nil(t, errGet)
	assert.Equal(t, "services", service.Type)
}
