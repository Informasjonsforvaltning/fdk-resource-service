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

func TestUnableToGetDeletedInformationModel(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("GET", "/information-models/222", nil)
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusNotFound, w.Code)
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
	assert.True(t, slices.Contains(ids, "123"))
	assert.True(t, slices.Contains(ids, "111"))
	assert.False(t, slices.Contains(ids, "222"))
}

func TestFilterInformationModelsIncludeOne(t *testing.T) {
	app := router.SetupRouter()
	body, _ := json.Marshal(model.Filters{IDs: []string{"111"}})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/information-models", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestInformationModel
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, len(actualResponse), 1)

	var ids []string
	for _, informationModel := range actualResponse {
		ids = append(ids, informationModel.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
}

func TestFilterInformationModelsIncludeTwo(t *testing.T) {
	app := router.SetupRouter()
	body, _ := json.Marshal(model.Filters{IDs: []string{"111", "222", "123"}})

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("POST", "/information-models", bytes.NewBuffer(body))
	app.ServeHTTP(w, req)
	assert.Equal(t, http.StatusOK, w.Code)

	var actualResponse []TestInformationModel
	err := json.Unmarshal(w.Body.Bytes(), &actualResponse)

	assert.Nil(t, err)
	assert.Equal(t, len(actualResponse), 2)

	var ids []string
	for _, informationModel := range actualResponse {
		ids = append(ids, informationModel.ID)
	}
	assert.True(t, slices.Contains(ids, "111"))
	assert.True(t, slices.Contains(ids, "123"))
}

func TestCreateInformationModel(t *testing.T) {
	informationModelService := service.InitInformationModelService()

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

	informationModel0Bytes, _ := json.Marshal(informationModel0)
	err0 := informationModelService.StoreInformationModel(context.TODO(), informationModel0Bytes, 100)
	assert.Nil(t, err0)

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

	informationModel1Bytes, _ := json.Marshal(informationModel1)
	err1 := informationModelService.StoreInformationModel(context.TODO(), informationModel1Bytes, 101)
	assert.Nil(t, err1)

	app := router.SetupRouter()

	wGet0 := httptest.NewRecorder()
	reqGet0, _ := http.NewRequest("GET", "/information-models/000", nil)
	app.ServeHTTP(wGet0, reqGet0)
	assert.Equal(t, http.StatusOK, wGet0.Code)

	var created TestInformationModel
	err0 = json.Unmarshal(wGet0.Body.Bytes(), &created)
	assert.Nil(t, err0)
	assert.Equal(t, informationModel0, created)

	wGet1 := httptest.NewRecorder()
	reqGet1, _ := http.NewRequest("GET", "/information-models/111", nil)
	app.ServeHTTP(wGet1, reqGet1)
	assert.Equal(t, http.StatusOK, wGet1.Code)

	var updated TestInformationModel
	err1 = json.Unmarshal(wGet1.Body.Bytes(), &updated)
	assert.Nil(t, err1)
	assert.Equal(t, informationModel1, updated)
}

func TestUpdateInformationModelSkippedWhenIncomingTimestampIsLower(t *testing.T) {
	informationModelService := service.InitInformationModelService()

	informationModel := TestInformationModel{
		ID:         "111",
		Type:       "informationModels",
		Uri:        "https://information-models.digdir.no/654",
		Identifier: "654",
		Title: map[string]string{
			"en": "skipped",
		},
	}

	informationModelBytes, _ := json.Marshal(informationModel)
	err := informationModelService.StoreInformationModel(context.TODO(), informationModelBytes, 5)
	assert.Nil(t, err)

	app := router.SetupRouter()

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/information-models/111", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusOK, wGet.Code)

	var notUpdated TestInformationModel
	err = json.Unmarshal(wGet.Body.Bytes(), &notUpdated)
	assert.Nil(t, err)
	assert.NotEqual(t, informationModel, notUpdated)
}

func TestDeleteInformationModelUnauthorized(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/information-models/444", nil)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusUnauthorized, w.Code)
}

func TestDeleteInformationModelForbiddenWithWrongAuth(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/information-models/444", nil)
	orgAdminAuth := OrgAdminAuth("987654321")
	jwt := CreateMockJwt(time.Now().Add(time.Hour).Unix(), &orgAdminAuth, &TestValues.Audience)
	req.Header.Set("Authorization", *jwt)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDeleteInformationModelForbiddenWithWrongAudience(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/information-models/444", nil)
	wrongAudience := []string{"invalid-audience"}
	jwt := CreateMockJwt(time.Now().Add(time.Hour).Unix(), &TestValues.SysAdminAuth, &wrongAudience)
	req.Header.Set("Authorization", *jwt)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusForbidden, w.Code)
}

func TestDeleteInformationModelNotFound(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/information-models/not-found", nil)
	jwt := CreateMockJwt(time.Now().Add(time.Hour).Unix(), &TestValues.SysAdminAuth, &TestValues.Audience)
	req.Header.Set("Authorization", *jwt)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNotFound, w.Code)
}

func TestDeleteInformationModel(t *testing.T) {
	app := router.SetupRouter()

	w := httptest.NewRecorder()
	req, _ := http.NewRequest("DELETE", "/information-models/444", nil)
	jwt := CreateMockJwt(time.Now().Add(time.Hour).Unix(), &TestValues.SysAdminAuth, &TestValues.Audience)
	req.Header.Set("Authorization", *jwt)
	app.ServeHTTP(w, req)

	assert.Equal(t, http.StatusNoContent, w.Code)

	wGet := httptest.NewRecorder()
	reqGet, _ := http.NewRequest("GET", "/information-models/444", nil)
	app.ServeHTTP(wGet, reqGet)
	assert.Equal(t, http.StatusNotFound, wGet.Code)
}
