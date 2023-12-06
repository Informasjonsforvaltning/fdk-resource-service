package service

import (
	"context"
	"encoding/json"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/mappers"
	"net/http"
	"strings"

	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
)

type DataServiceService struct {
	DataServiceRepository repository.ResourceRepository
}

func InitDataServiceService() *DataServiceService {
	service := DataServiceService{
		DataServiceRepository: repository.InitDataServiceRepository(),
	}
	return &service
}

func (service DataServiceService) GetDataServices(ctx context.Context, includeRemoved string) ([]map[string]interface{}, int) {
	query := bson.D{}
	if strings.ToLower(includeRemoved) != "true" {
		query = bson.D{{Key: "removed", Value: false}}
	}
	dataServices, err := service.DataServiceRepository.GetResources(ctx, query)
	if err != nil {
		logrus.Error("Get data services failed ")
		logger.LogAndPrintError(err)
		return []map[string]interface{}{}, http.StatusInternalServerError
	} else if dataServices == nil {
		return []map[string]interface{}{}, http.StatusOK
	}

	return mappers.ToDTO(dataServices), http.StatusOK
}

func (service DataServiceService) GetDataService(ctx context.Context, id string) (map[string]interface{}, int) {
	dbo, err := service.DataServiceRepository.GetResource(ctx, id)
	if err == mongo.ErrNoDocuments {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get data service with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else {
		return dbo.Resource, http.StatusOK
	}
}

func (service DataServiceService) StoreDataServices(ctx context.Context, bytes []byte) int {
	var dataServices []map[string]interface{}
	err := json.Unmarshal(bytes, &dataServices)
	if err != nil {
		logrus.Error("Unable to unmarshal data services")
		logger.LogAndPrintError(err)
		return http.StatusBadRequest
	}

	err = service.DataServiceRepository.StoreResources(ctx, mappers.ToDBO(dataServices))
	if err != nil {
		logrus.Error("Could not store data services")
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}
	return http.StatusOK
}

func (service DataServiceService) RemoveDataService(ctx context.Context, id string) error {
	logrus.Infof("Tagging data service %s as removed", id)
	dataService, err := service.DataServiceRepository.GetResource(ctx, id)
	if err == nil {
		dataService.Removed = true
		err = service.DataServiceRepository.StoreResources(ctx, []model.DBO{dataService})
	}

	return err
}
