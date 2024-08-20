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
	"time"
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

func TestUnableToGetDeletedEvent(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/events/222", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
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
	assert.True(t, slices.Contains(ids, "123"))
	assert.True(t, slices.Contains(ids, "111"))
	assert.False(t, slices.Contains(ids, "222"))
}

func TestFilterEventsIncludeOne(t *testing.T) {
	app := router.SetupRouter()
	body, _ := json.Marshal(model.Filters{IDs: []string{"111"}})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/events", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestEvent
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, len(actualResponse), 1)

	var ids []string
	for _, event := range actualResponse {
		ids = append(ids, event.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
}

func TestFilterEventsIncludeTwo(t *testing.T) {
	app := router.SetupRouter()
	body, _ := json.Marshal(model.Filters{IDs: []string{"111", "222", "123"}})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/events", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestEvent
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, len(actualResponse), 2)

	var ids []string
	for _, event := range actualResponse {
		ids = append(ids, event.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.True(t, slices.Contains(ids, "123"))
}

func TestCreateEvent(t *testing.T) {
	eventService := service.InitEventService()

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

	event0Bytes, _ := json.Marshal(event0)
	err0 := eventService.StoreEvent(context.TODO(), event0Bytes, 100)
	assert.Nil(t, err0)

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

	event1Bytes, _ := json.Marshal(event1)
	err1 := eventService.StoreEvent(context.TODO(), event1Bytes, 101)
	assert.Nil(t, err1)

	app := router.SetupRouter()

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/events/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestEvent
	err0 = json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, event0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/events/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestEvent
	err1 = json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, event1, updated)
}

func TestUpdateEventSkippedWhenIncomingTimestampIsLower(t *testing.T) {
	eventService := service.InitEventService()

	event := TestEvent{
		ID:         "111",
		Type:       "events",
		Uri:        "https://events.digdir.no/654",
		Identifier: "654",
		Title: map[string]string{
			"en": "skipped",
		},
	}

	eventBytes, _ := json.Marshal(event)
	err := eventService.StoreEvent(context.TODO(), eventBytes, 5)
	assert.Nil(t, err)

	app := router.SetupRouter()

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/events/111", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusOK, wGet.Code)

	var notUpdated TestEvent
	err = json.Unmarshal(wGet.Body.Bytes(), &notUpdated)
	assert.Nil(t, err)
	assert.NotEqual(t, event, notUpdated)
}

func TestDeleteEventUnauthorized(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/events/444", nil)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestDeleteEventForbiddenWithWrongAuth(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/events/444", nil)
	orgAdminAuth := OrgAdminAuth("987654321")
	jwt := CreateMockJwt(time.Now().Add(time.Hour).Unix(), &orgAdminAuth, &TestValues.Audience)
	req.Header.Set("Authorization", *jwt)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDeleteEventForbiddenWithWrongAudience(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/events/444", nil)
	wrongAudience := []string{"invalid-audience"}
	jwt := CreateMockJwt(time.Now().Add(time.Hour).Unix(), &TestValues.SysAdminAuth, &wrongAudience)
	req.Header.Set("Authorization", *jwt)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDeleteEventNotFound(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/events/not-found", nil)
	jwt := CreateMockJwt(time.Now().Add(time.Hour).Unix(), &TestValues.SysAdminAuth, &TestValues.Audience)
	req.Header.Set("Authorization", *jwt)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDeleteEvent(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/events/444", nil)
	jwt := CreateMockJwt(time.Now().Add(time.Hour).Unix(), &TestValues.SysAdminAuth, &TestValues.Audience)
	req.Header.Set("Authorization", *jwt)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNoContent, w.Code)

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/events/444", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusNotFound, wGet.Code)
}
