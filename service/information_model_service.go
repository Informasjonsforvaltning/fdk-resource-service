package service

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/mappers"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/pointer"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"net/http"
	"time"

	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
)

type InformationModelService struct {
	InformationModelRepository repository.ResourceRepository
}

func InitInformationModelService() *InformationModelService {
	service := InformationModelService{
		InformationModelRepository: repository.InitInformationModelRepository(),
	}
	return &service
}

func (service InformationModelService) GetInformationModels(ctx context.Context, filters *model.Filters) ([]map[string]interface{}, int) {
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
	informationModels, err := service.InformationModelRepository.GetResources(ctx, query)
	if err != nil {
		logrus.Error("Get information models failed ")
		logger.LogAndPrintError(err)
		return []map[string]interface{}{}, http.StatusInternalServerError
	} else if informationModels == nil {
		return []map[string]interface{}{}, http.StatusOK
	}

	return mappers.ToDTO(informationModels), http.StatusOK
}

func (service InformationModelService) GetInformationModel(ctx context.Context, id string) (map[string]interface{}, int) {
	dbo, err := service.InformationModelRepository.GetResource(ctx, id)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get information model with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else if dbo.Deleted == true {
		return map[string]interface{}{}, http.StatusNotFound
	} else {
		return dbo.Resource, http.StatusOK
	}
}

func (service InformationModelService) StoreInformationModel(ctx context.Context, bytes []byte, timestamp int64) error {
	var informationModel map[string]interface{}
	err := json.Unmarshal(bytes, &informationModel)
	if err != nil {
		return err
	}

	updated := model.DBO{
		ID:        informationModel["id"].(string),
		Resource:  informationModel,
		Timestamp: timestamp,
		Deleted:   false,
	}

	dbo, err := service.InformationModelRepository.GetResource(ctx, updated.ID)
	if err == nil && dbo.Timestamp > updated.Timestamp {
		return nil // do not update if current timestamp is higher
	} else if err == nil || errors.Is(err, mongo.ErrNoDocuments) {
		return service.InformationModelRepository.StoreResource(ctx, updated)
	} else {
		return err
	}
}

func (service InformationModelService) DeleteInformationModel(ctx context.Context, id string) int {
	informationModel := map[string]interface{}{}

	deleted := model.DBO{
		ID:        id,
		Resource:  informationModel,
		Timestamp: time.Now().UnixMilli(),
		Deleted:   true,
	}

	_, err := service.InformationModelRepository.GetResource(ctx, id)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Failed to get information model with id %s for deletion", id)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}

	err = service.InformationModelRepository.StoreResource(ctx, deleted)

	if err != nil {
		logrus.Errorf("Failed to delete information model with id %s", id)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	} else {
		return http.StatusNoContent
	}
}
