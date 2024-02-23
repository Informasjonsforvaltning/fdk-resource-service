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

func TestCreateService(t *testing.T) {
	serviceService := service.InitServiceService()

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

	service0Bytes, _ := json.Marshal(service0)
	err0 := serviceService.StoreService(context.TODO(), service0Bytes)
	assert.Nil(t, err0)

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

	service1Bytes, _ := json.Marshal(service1)
	err1 := serviceService.StoreService(context.TODO(), service1Bytes)
	assert.Nil(t, err1)

	app := router.SetupRouter()

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/services/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestService
	err0 = json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, service0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/services/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestService
	err1 = json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, service1, updated)
}
