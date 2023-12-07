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

type TestEvent struct {
	ID          string            `json:"id"`
	Type        string            `json:"type"`
	Uri         string            `json:"uri"`
	Identifier  string            `json:"identifier"`
	Title       map[string]string `json:"title"`
	Description map[string]string `json:"description"`
}

func TestGetEvent(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/events/123", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	expectedResponse := TestEvent{
		ID:         "123",
		Type:       "events",
		Uri:        "https://events.digdir.no/321",
		Identifier: "321",
		Title: map[string]string{
			"nb": "event nb",
			"nn": "event nn",
			"en": "event en",
		},
		Description: map[string]string{
			"nb": "event desc nb",
			"nn": "event desc nn",
			"en": "event desc en",
		},
	}

	var actualResponse TestEvent
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, expectedResponse, actualResponse)
}

func TestGetEvents(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/events", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestEvent
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 0)

	var ids []string
	for _, event := range actualResponse {
		ids = append(ids, event.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.False(t, slices.Contains(ids, "222"))
}

func TestGetEventsIncludeRemoved(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/events?includeRemoved=true", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestEvent
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.True(t, len(actualResponse) > 1)

	var ids []string
	for _, event := range actualResponse {
		ids = append(ids, event.ID)
	}
	assert.True(t, slices.Contains(ids, "222"))
}

func TestUnauthorizedCreateOfEvents(t *testing.T) {
	app := router.SetupRouter()

	event := TestEvent{
		ID: "999",
	}
	var toBeStored []TestEvent
	toBeStored = append(toBeStored, event)
	body, _ := json.Marshal(toBeStored)

	wWrongKey := httptest.NewRecorder()
	reqWrongKey, _ := http.NewRequest("POST", "/events", bytes.NewBuffer(body))
	reqWrongKey.Header.Set("X-API-Key", "wrong")
	app.ServeHTTP(wWrongKey, reqWrongKey)

	assert.Equal(t, http.StatusUnauthorized, wWrongKey.Code)

	wMissingKey := httptest.NewRecorder()
	reqMissingKey, _ := http.NewRequest("POST", "/events", bytes.NewBuffer(body))
	app.ServeHTTP(wMissingKey, reqMissingKey)

	assert.Equal(t, http.StatusUnauthorized, wMissingKey.Code)
}

func TestCreateEvent(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	event0 := TestEvent{
		ID:         "000",
		Type:       "events",
		Uri:        "https://events.digdir.no/987",
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

	event1 := TestEvent{
		ID:         "111",
		Type:       "events",
		Uri:        "https://events.digdir.no/654",
		Identifier: "654",
		Title: map[string]string{
			"nb": "updated event nb",
			"nn": "updated event nn",
			"en": "updated event en",
		},
		Description: map[string]string{
			"nb": "updated event desc nb",
			"nn": "updated event desc nn",
			"en": "updated event desc en",
		},
	}

	var toBeStored []TestEvent
	toBeStored = append(toBeStored, event0)
	toBeStored = append(toBeStored, event1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/events", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusOK, w.Code)

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/events/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestEvent
	err0 := json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, event0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/events/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestEvent
	err1 := json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, event1, updated)
}

func TestAbortEventUpdateWhenOneFails(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()

	event0 := TestEvent{
		ID:   "123",
		Type: "is-aborted",
	}

	event1 := TestEvent{
		ID:   "",
		Type: "invalid-event",
	}

	var toBeStored []TestEvent
	toBeStored = append(toBeStored, event0)
	toBeStored = append(toBeStored, event1)

	body, _ := json.Marshal(toBeStored)
	req, _ := http.NewRequest("POST", "/events", bytes.NewBuffer(body))
	req.Header.Set("X-API-Key", "test")
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusInternalServerError, w.Code)

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/events/123", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusOK, wGet.Code)

	var event TestEvent
	errGet := json.Unmarshal(wGet.Body.Bytes(), &event)
	assert.Nil(t, errGet)
	assert.Equal(t, "events", event.Type)
}
