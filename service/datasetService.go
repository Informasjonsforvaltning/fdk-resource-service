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
)

type DatasetService struct {
	DatasetRepository repository.DatasetRepository
}

func InitDatasetService() *DatasetService {
	service := DatasetService{
		DatasetRepository: repository.InitRepository(),
	}
	return &service
}

func (service DatasetService) GetDatasets(ctx context.Context, includeRemoved string) ([]model.Dataset, int) {
	query := bson.D{}
	if strings.ToLower(includeRemoved) != "true" {
		query = bson.D{{Key: "removed", Value: false}}
	}
	datasets, err := service.DatasetRepository.GetDatasets(ctx, query)
	if err != nil {
		logrus.Error("Get datasets failed ")
		logger.LogAndPrintError(err)
		return []model.Dataset{}, http.StatusInternalServerError
	} else if datasets == nil {
		return []model.Dataset{}, http.StatusOK
	}

	return dboToDTO(datasets), http.StatusOK
}

func (service DatasetService) GetDataset(ctx context.Context, id string) (model.Dataset, int) {
	dbo, err := service.DatasetRepository.GetDataset(ctx, id)
	if err == mongo.ErrNoDocuments {
		return model.Dataset{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get dataset with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return model.Dataset{}, http.StatusInternalServerError
	} else {
		return dbo.Dataset, http.StatusOK
	}
}

func (service DatasetService) StoreDatasets(ctx context.Context, bytes []byte) int {
	var datasets []model.Dataset
	err := json.Unmarshal(bytes, &datasets)
	if err != nil {
		logrus.Error("Unable to unmarshal datasets")
		logger.LogAndPrintError(err)
		return http.StatusBadRequest
	}

	err = service.DatasetRepository.StoreDatasets(ctx, dtoToDBO(datasets))
	if err != nil {
		logrus.Error("Could not store datasets")
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}
	return http.StatusOK
}

func (service DatasetService) RemoveDataset(ctx context.Context, id string) error {
	logrus.Infof("Tagging dataset %s as removed", id)
	dataset, err := service.DatasetRepository.GetDataset(ctx, id)
	if err == nil {
		dataset.Removed = true
		err = service.DatasetRepository.StoreDatasets(ctx, []model.DatasetDBO{dataset})
	}

	return err
}

func dboToDTO(dboDatasets []model.DatasetDBO) []model.Dataset {
	var dtoDatasets []model.Dataset
	for _, dbo := range dboDatasets {
		dtoDatasets = append(dtoDatasets, dbo.Dataset)
	}
	return dtoDatasets
}

func dtoToDBO(dtoDatasets []model.Dataset) []model.DatasetDBO {
	var dboDatasets []model.DatasetDBO
	for _, dto := range dtoDatasets {
		var dbo = model.DatasetDBO{
			ID:      dto.ID,
			Dataset: dto,
			Removed: false,
		}
		dboDatasets = append(dboDatasets, dbo)
	}
	return dboDatasets
}
