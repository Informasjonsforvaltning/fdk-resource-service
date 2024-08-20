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
	if errors.Is(err, mongo.ErrNoDocuments) {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get data service with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else if dbo.Deleted == true {
		return map[string]interface{}{}, http.StatusNotFound
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
		Deleted:   false,
	}

	dbo, err := service.DataServiceRepository.GetResource(ctx, updated.ID)
	if err == nil && dbo.Timestamp > updated.Timestamp {
		return nil // do not update if current timestamp is higher
	} else if err == nil || errors.Is(err, mongo.ErrNoDocuments) {
		return service.DataServiceRepository.StoreResource(ctx, updated)
	} else {
		return err
	}
}

func (service DataServiceService) DeleteDataService(ctx context.Context, id string) int {
	dataService := map[string]interface{}{}

	deleted := model.DBO{
		ID:        id,
		Resource:  dataService,
		Timestamp: time.Now().UnixMilli(),
		Deleted:   true,
	}

	_, err := service.DataServiceRepository.GetResource(ctx, id)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Failed to get data service with id %s for deletion", id)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}

	err = service.DataServiceRepository.StoreResource(ctx, deleted)

	if err != nil {
		logrus.Errorf("Failed to data service dataset with id %s", id)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	} else {
		return http.StatusNoContent
	}
}
