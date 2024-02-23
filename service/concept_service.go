package service

import (
	"context"
	"encoding/json"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/mappers"
	"net/http"
	"strings"

	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
)

type ConceptService struct {
	ConceptRepository repository.ResourceRepository
}

func InitConceptService() *ConceptService {
	service := ConceptService{
		ConceptRepository: repository.InitConceptRepository(),
	}
	return &service
}

func (service ConceptService) GetConcepts(ctx context.Context, includeRemoved string) ([]map[string]interface{}, int) {
	query := bson.D{}
	if strings.ToLower(includeRemoved) != "true" {
		query = bson.D{{Key: "removed", Value: false}}
	}
	concepts, err := service.ConceptRepository.GetResources(ctx, query)
	if err != nil {
		logrus.Error("Get concepts failed ")
		logger.LogAndPrintError(err)
		return []map[string]interface{}{}, http.StatusInternalServerError
	} else if concepts == nil {
		return []map[string]interface{}{}, http.StatusOK
	}

	return mappers.ToDTO(concepts), http.StatusOK
}

func (service ConceptService) GetConcept(ctx context.Context, id string) (map[string]interface{}, int) {
	dbo, err := service.ConceptRepository.GetResource(ctx, id)
	if err == mongo.ErrNoDocuments {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get concept with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else {
		return dbo.Resource, http.StatusOK
	}
}

func (service ConceptService) StoreConcept(ctx context.Context, bytes []byte, timestamp int64) error {
	var concept map[string]interface{}
	err := json.Unmarshal(bytes, &concept)
	if err != nil {
		return err
	}

	id := concept["id"].(string)
	dbo, _ := service.ConceptRepository.GetResource(ctx, id)
	updated := model.DBO{
		ID:       id,
		Resource: concept,
	}
	if dbo.Timestamp < timestamp {
		updated.Removed = false
		updated.Timestamp = timestamp
	} else {
		updated.Removed = dbo.Removed
		updated.Timestamp = dbo.Timestamp
	}

	return service.ConceptRepository.StoreResource(ctx, updated)
}

func (service ConceptService) RemoveConcept(ctx context.Context, id string, timestamp int64) error {
	logrus.Infof("Tagging concept %s as removed", id)
	concept, err := service.ConceptRepository.GetResource(ctx, id)
	if err == nil && concept.Timestamp < timestamp {
		concept.Removed = true
		concept.Timestamp = timestamp
		err = service.ConceptRepository.StoreResource(ctx, concept)
	}

	return err
}
