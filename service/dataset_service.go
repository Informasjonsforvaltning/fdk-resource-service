package service

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/pointer"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"net/http"
	"time"

	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/mappers"
)

type DatasetService struct {
	DatasetRepository repository.ResourceRepository
}

func InitDatasetService() *DatasetService {
	service := DatasetService{
		DatasetRepository: repository.InitDatasetRepository(),
	}
	return &service
}

func (service DatasetService) GetDatasets(ctx context.Context, filters *model.Filters) ([]map[string]interface{}, int) {
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
	datasets, err := service.DatasetRepository.GetResources(ctx, query)
	if err != nil {
		logrus.Error("Get datasets failed ")
		logger.LogAndPrintError(err)
		return []map[string]interface{}{}, http.StatusInternalServerError
	} else if datasets == nil {
		return []map[string]interface{}{}, http.StatusOK
	}

	return mappers.ToDTO(datasets), http.StatusOK
}

func (service DatasetService) GetDataset(ctx context.Context, id string) (map[string]interface{}, int) {
	dbo, err := service.DatasetRepository.GetResource(ctx, id)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get dataset with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else if dbo.Deleted == true {
		return map[string]interface{}{}, http.StatusNotFound
	} else {
		return dbo.Resource, http.StatusOK
	}
}

func (service DatasetService) StoreDataset(ctx context.Context, bytes []byte, timestamp int64) error {
	var dataset map[string]interface{}
	err := json.Unmarshal(bytes, &dataset)
	if err != nil {
		return err
	}

	updated := model.DBO{
		ID:        dataset["id"].(string),
		Resource:  dataset,
		Timestamp: timestamp,
		Deleted:   false,
	}

	dbo, err := service.DatasetRepository.GetResource(ctx, updated.ID)
	if err == nil && dbo.Timestamp > updated.Timestamp {
		return nil // do not update if current timestamp is higher
	} else if err == nil || errors.Is(err, mongo.ErrNoDocuments) {
		return service.DatasetRepository.StoreResource(ctx, updated)
	} else {
		return err
	}
}

func (service DatasetService) DeleteDataset(ctx context.Context, id string) int {
	dataset := map[string]interface{}{}

	deleted := model.DBO{
		ID:        id,
		Resource:  dataset,
		Timestamp: time.Now().UnixMilli(),
		Deleted:   true,
	}

	_, err := service.DatasetRepository.GetResource(ctx, id)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Failed to get dataset with id %s for deletion", id)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}

	err = service.DatasetRepository.StoreResource(ctx, deleted)

	if err != nil {
		logrus.Errorf("Failed to delete dataset with id %s", id)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	} else {
		return http.StatusNoContent
	}
}
