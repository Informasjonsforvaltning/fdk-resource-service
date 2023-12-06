package service

import (
	"context"
	"encoding/json"
	"net/http"
	"strings"

	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
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

func (service DatasetService) StoreDatasets(ctx context.Context, bytes []byte) int {
	var datasets []map[string]interface{}
	err := json.Unmarshal(bytes, &datasets)
	if err != nil {
		logrus.Error("Unable to unmarshal datasets")
		logger.LogAndPrintError(err)
		return http.StatusBadRequest
	}

	err = service.DatasetRepository.StoreResources(ctx, mappers.ToDBO(datasets))
	if err != nil {
		logrus.Error("Could not store datasets")
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}
	return http.StatusOK
}

func (service DatasetService) RemoveDataset(ctx context.Context, id string) error {
	logrus.Infof("Tagging dataset %s as removed", id)
	dataset, err := service.DatasetRepository.GetResource(ctx, id)
	if err == nil {
		dataset.Removed = true
		err = service.DatasetRepository.StoreResources(ctx, []model.DBO{dataset})
	}

	return err
}
