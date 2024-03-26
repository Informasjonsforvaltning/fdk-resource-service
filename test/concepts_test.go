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

type TestConcept struct {
	ID          string            `json:"id"`
	Type        string            `json:"type"`
	Uri         string            `json:"uri"`
	Identifier  string            `json:"identifier"`
	Title       map[string]string `json:"title"`
	Description map[string]string `json:"description"`
}

func TestGetConcept(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/concepts/123", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	expectedResponse := TestConcept{
		ID:         "123",
		Type:       "concepts",
		Uri:        "https://concepts.digdir.no/321",
		Identifier: "321",
		Title: map[string]string{
			"nb": "concept nb",
			"nn": "concept nn",
			"en": "concept en",
		},
		Description: map[string]string{
			"nb": "concept desc nb",
			"nn": "concept desc nn",
			"en": "concept desc en",
		},
	}

	var actualResponse TestConcept
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, expectedResponse, actualResponse)
}

func TestGetConcepts(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/concepts", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestConcept
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 0)

	var ids []string
	for _, concept := range actualResponse {
		ids = append(ids, concept.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.True(t, slices.Contains(ids, "222"))
}

func TestFilterConceptsIncludeOne(t *testing.T) {
	app := router.SetupRouter()
	body, _ := json.Marshal(model.Filters{IDs: []string{"111"}})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/concepts", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestConcept
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, len(actualResponse), 1)

	var ids []string
	for _, concept := range actualResponse {
		ids = append(ids, concept.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
}

func TestFilterConceptsIncludeTwo(t *testing.T) {
	app := router.SetupRouter()
	body, _ := json.Marshal(model.Filters{IDs: []string{"111", "222"}})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/concepts", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestConcept
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, len(actualResponse), 2)

	var ids []string
	for _, concept := range actualResponse {
		ids = append(ids, concept.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.True(t, slices.Contains(ids, "222"))
}

func TestCreateConcept(t *testing.T) {
	conceptService := service.InitConceptService()

	concept0 := TestConcept{
		ID:         "000",
		Type:       "concepts",
		Uri:        "https://concepts.digdir.no/987",
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

	concept0Bytes, _ := json.Marshal(concept0)
	err0 := conceptService.StoreConcept(context.TODO(), concept0Bytes, 100)
	assert.Nil(t, err0)

	concept1 := TestConcept{
		ID:         "111",
		Type:       "concepts",
		Uri:        "https://concepts.digdir.no/654",
		Identifier: "654",
		Title: map[string]string{
			"nb": "updated concept nb",
			"nn": "updated concept nn",
			"en": "updated concept en",
		},
		Description: map[string]string{
			"nb": "updated concept desc nb",
			"nn": "updated concept desc nn",
			"en": "updated concept desc en",
		},
	}

	concept1Bytes, _ := json.Marshal(concept1)
	err1 := conceptService.StoreConcept(context.TODO(), concept1Bytes, 101)
	assert.Nil(t, err1)

	app := router.SetupRouter()

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/concepts/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestConcept
	err0 = json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, concept0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/concepts/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestConcept
	err1 = json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, concept1, updated)
}
