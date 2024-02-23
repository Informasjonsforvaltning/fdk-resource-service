package service

import (
	"context"
	"encoding/json"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"net/http"
	"strings"

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

func (service DatasetService) GetDatasets(ctx context.Context, includeRemoved string) ([]map[string]interface{}, int) {
	query := bson.D{}
	if strings.ToLower(includeRemoved) != "true" {
		query = bson.D{{Key: "removed", Value: false}}
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
	if err == mongo.ErrNoDocuments {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get dataset with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
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

	id := dataset["id"].(string)
	dbo, _ := service.DatasetRepository.GetResource(ctx, id)
	updated := model.DBO{
		ID:       id,
		Resource: dataset,
	}
	if dbo.Timestamp < timestamp {
		updated.Removed = false
		updated.Timestamp = timestamp
	} else {
		updated.Removed = dbo.Removed
		updated.Timestamp = dbo.Timestamp
	}

	return service.DatasetRepository.StoreResource(ctx, updated)
}

func (service DatasetService) RemoveDataset(ctx context.Context, id string, timestamp int64) error {
	logrus.Infof("Tagging dataset %s as removed", id)
	dataset, err := service.DatasetRepository.GetResource(ctx, id)
	if err == nil && dataset.Timestamp < timestamp {
		dataset.Removed = true
		dataset.Timestamp = timestamp
		err = service.DatasetRepository.StoreResource(ctx, dataset)
	}

	return err
}
