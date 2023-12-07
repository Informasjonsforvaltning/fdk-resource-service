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

func (s ServiceService) StoreServices(ctx context.Context, bytes []byte) int {
	var services []map[string]interface{}
	err := json.Unmarshal(bytes, &services)
	if err != nil {
		logrus.Error("Unable to unmarshal services")
		logger.LogAndPrintError(err)
		return http.StatusBadRequest
	}

	err = s.ServiceRepository.StoreResources(ctx, mappers.ToDBO(services))
	if err != nil {
		logrus.Error("Could not store services")
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}
	return http.StatusOK
}

func (s ServiceService) RemoveService(ctx context.Context, id string) error {
	logrus.Infof("Tagging service %s as removed", id)
	service, err := s.ServiceRepository.GetResource(ctx, id)
	if err == nil {
		service.Removed = true
		err = s.ServiceRepository.StoreResources(ctx, []model.DBO{service})
	}

	return err
}
