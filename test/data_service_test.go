package test

import (
	"bytes"
	"context"
	"encoding/json"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/router"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
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
	assert.True(t, slices.Contains(ids, "222"))
}

func TestFilterDataServicesIncludeOne(t *testing.T) {
	app := router.SetupRouter()
	body, _ := json.Marshal(model.Filters{IDs: []string{"111"}})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/data-services", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestDataService
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, len(actualResponse), 1)

	var ids []string
	for _, dataService := range actualResponse {
		ids = append(ids, dataService.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
}

func TestFilterDataServicesIncludeTwo(t *testing.T) {
	app := router.SetupRouter()
	body, _ := json.Marshal(model.Filters{IDs: []string{"111", "222"}})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/data-services", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestDataService
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, len(actualResponse), 2)

	var ids []string
	for _, dataService := range actualResponse {
		ids = append(ids, dataService.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.True(t, slices.Contains(ids, "222"))
}

func TestCreateDataService(t *testing.T) {
	dataServiceService := service.InitDataServiceService()

	dataService0 := TestDataService{
		ID:         "000",
		Type:       "dataServices",
		Uri:        "https://data-services.digdir.no/987",
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

	dataService0Bytes, _ := json.Marshal(dataService0)
	err0 := dataServiceService.StoreDataService(context.TODO(), dataService0Bytes, 100)
	assert.Nil(t, err0)

	dataService1 := TestDataService{
		ID:         "111",
		Type:       "dataServices",
		Uri:        "https://data-services.digdir.no/654",
		Identifier: "654",
		Title: map[string]string{
			"nb": "updated data service nb",
			"nn": "updated data service nn",
			"en": "updated data service en",
		},
		Description: map[string]string{
			"nb": "updated data service desc nb",
			"nn": "updated data service desc nn",
			"en": "updated data service desc en",
		},
	}

	dataService1Bytes, _ := json.Marshal(dataService1)
	err1 := dataServiceService.StoreDataService(context.TODO(), dataService1Bytes, 101)
	assert.Nil(t, err1)

	app := router.SetupRouter()

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/data-services/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestDataService
	err0 = json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, dataService0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/data-services/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestDataService
	err1 = json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, dataService1, updated)
}
