package service

import (
	"context"
	"encoding/json"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/mappers"
	"net/http"

	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
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

func (service DataServiceService) GetDataServices(ctx context.Context, filters *model.Filters) ([]map[string]interface{}, int) {
	query := bson.D{}
	if filters != nil {
		query = bson.D{{Key: "_id", Value: bson.D{{Key: "$in", Value: filters.IDs}}}}
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

func (service DataServiceService) StoreDataService(ctx context.Context, bytes []byte, timestamp int64) error {
	var dataService map[string]interface{}
	err := json.Unmarshal(bytes, &dataService)
	if err != nil {
		return err
	}

	updated := model.DBO{
		ID:        dataService["id"].(string),
		Resource:  dataService,
		Timestamp: timestamp,
	}

	return service.DataServiceRepository.StoreResource(ctx, updated)
}
