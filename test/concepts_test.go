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
	assert.False(t, slices.Contains(ids, "222"))
}

func TestGetConceptsIncludeRemoved(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/concepts?includeRemoved=true", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestConcept
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 1)

	var ids []string
	for _, concept := range actualResponse {
		ids = append(ids, concept.ID)
	}
	assert.True(t, slices.Contains(ids, "222"))
}

func TestUnauthorizedCreateOfConcepts(t *testing.T) {
	app := router.SetupRouter()

	concept := TestConcept{
		ID: "999",
	}
	var toBeStored []TestConcept
	toBeStored = append(toBeStored, concept)
	body, _ := json.Marshal(toBeStored)

	wWrongKey := httptest.NewRecorder()
	reqWrongKey, _ := http.NewRequest("POST", "/concepts", bytes.NewBuffer(body))
	reqWrongKey.Header.Set("X-API-Key", "wrong")
	app.ServeHTTP(wWrongKey, reqWrongKey)

	assert.Equal(t, http.StatusUnauthorized, wWrongKey.Code)

	wMissingKey := httptest.NewRecorder()
	reqMissingKey, _ := http.NewRequest("POST", "/concepts", bytes.NewBuffer(body))
	app.ServeHTTP(wMissingKey, reqMissingKey)

	assert.Equal(t, http.StatusUnauthorized, wMissingKey.Code)
}

func TestCreateConcept(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

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

	var toBeStored []TestConcept
	toBeStored = append(toBeStored, concept0)
	toBeStored = append(toBeStored, concept1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/concepts", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/concepts/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestConcept
	err0 := json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, concept0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/concepts/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestConcept
	err1 := json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, concept1, updated)
}

func TestAbortConceptUpdateWhenOneFails(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	concept0 := TestConcept{
		ID:   "123",
		Type: "is-aborted",
	}

	concept1 := TestConcept{
		ID:   "",
		Type: "invalid-concept",
	}

	var toBeStored []TestConcept
	toBeStored = append(toBeStored, concept0)
	toBeStored = append(toBeStored, concept1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/concepts", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/concepts/123", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusOK, wGet.Code)

	var concept TestConcept
	errGet := json.Unmarshal(wGet.Body.Bytes(), &concept)
	assert.Nil(t, errGet)
	assert.Equal(t, "concepts", concept.Type)
}
