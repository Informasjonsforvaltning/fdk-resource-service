package service

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/mappers"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/pointer"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"
	"net/http"
	"time"

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

func (service ConceptService) GetConcepts(ctx context.Context, filters *model.Filters) ([]map[string]interface{}, int) {
	query := bson.D{{Key: "deleted", Value: bson.D{{Key: "$in", Value: []*bool{nil, pointer.Of(false)}}}}}
	if filters != nil {
		var ids []string
		for _, id := range filters.IDs {
			ids = append(ids, validate.SanitizeID(id))
		}
		query = bson.D{
			{Key: "_id", Value: bson.D{{Key: "$in", Value: ids}}},
			{Key: "deleted", Value: bson.D{{Key: "$in", Value: []*bool{nil, pointer.Of(false)}}}},
		}
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
	if errors.Is(err, mongo.ErrNoDocuments) {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get concept with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else if dbo.Deleted == true {
		return map[string]interface{}{}, http.StatusNotFound
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

	updated := model.DBO{
		ID:        concept["id"].(string),
		Resource:  concept,
		Timestamp: timestamp,
		Deleted:   false,
	}

	dbo, err := service.ConceptRepository.GetResource(ctx, updated.ID)
	if err == nil && dbo.Timestamp > updated.Timestamp {
		return nil // do not update if current timestamp is higher
	} else if err == nil || errors.Is(err, mongo.ErrNoDocuments) {
		return service.ConceptRepository.StoreResource(ctx, updated)
	} else {
		return err
	}
}

func (service ConceptService) DeleteConcept(ctx context.Context, id string) int {
	sanitizedID, err := validate.SanitizeAndValidateID(id)
	if err != nil {
		logrus.Warnf("Invalid concept id for deletion: %s", id)
		return http.StatusBadRequest
	}
	concept := map[string]interface{}{}

	deleted := model.DBO{
		ID:        sanitizedID,
		Resource:  concept,
		Timestamp: time.Now().UnixMilli(),
		Deleted:   true,
	}

	_, err = service.ConceptRepository.GetResource(ctx, sanitizedID)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Failed to get concept with id %s for deletion", sanitizedID)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}

	err = service.ConceptRepository.StoreResource(ctx, deleted)

	if err != nil {
		logrus.Errorf("Failed to concept dataset with id %s", sanitizedID)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	} else {
		return http.StatusNoContent
	}
}
