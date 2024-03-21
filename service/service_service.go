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

type ServiceService struct {
	ServiceRepository repository.ResourceRepository
}

func InitServiceService() *ServiceService {
	serviceService := ServiceService{
		ServiceRepository: repository.InitServiceRepository(),
	}
	return &serviceService
}

func (s ServiceService) GetServices(ctx context.Context, includeRemoved string) ([]map[string]interface{}, int) {
	query := bson.D{}
	if strings.ToLower(includeRemoved) != "true" {
		query = bson.D{{Key: "removed", Value: false}}
	}
	services, err := s.ServiceRepository.GetResources(ctx, query)
	if err != nil {
		logrus.Error("Get services failed ")
		logger.LogAndPrintError(err)
		return []map[string]interface{}{}, http.StatusInternalServerError
	} else if services == nil {
		return []map[string]interface{}{}, http.StatusOK
	}

	return mappers.ToDTO(services), http.StatusOK
}

func (s ServiceService) GetService(ctx context.Context, id string) (map[string]interface{}, int) {
	dbo, err := s.ServiceRepository.GetResource(ctx, id)
	if err == mongo.ErrNoDocuments {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get service with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else {
		return dbo.Resource, http.StatusOK
	}
}

func (s ServiceService) StoreService(ctx context.Context, bytes []byte, timestamp int64) error {
	var service map[string]interface{}
	err := json.Unmarshal(bytes, &service)
	if err != nil {
		return err
	}

	updated := model.DBO{
		ID:        service["id"].(string),
		Resource:  service,
		Timestamp: timestamp,
	}

	return s.ServiceRepository.StoreResource(ctx, updated)
}
