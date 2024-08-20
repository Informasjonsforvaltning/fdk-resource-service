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

type ServiceService struct {
	ServiceRepository repository.ResourceRepository
}

func InitServiceService() *ServiceService {
	serviceService := ServiceService{
		ServiceRepository: repository.InitServiceRepository(),
	}
	return &serviceService
}

func (s ServiceService) GetServices(ctx context.Context, filters *model.Filters) ([]map[string]interface{}, int) {
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
	if errors.Is(err, mongo.ErrNoDocuments) {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get service with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else if dbo.Deleted == true {
		return map[string]interface{}{}, http.StatusNotFound
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
		Deleted:   false,
	}

	dbo, err := s.ServiceRepository.GetResource(ctx, updated.ID)
	if err == nil && dbo.Timestamp > updated.Timestamp {
		return nil // do not update if current timestamp is higher
	} else if err == nil || errors.Is(err, mongo.ErrNoDocuments) {
		return s.ServiceRepository.StoreResource(ctx, updated)
	} else {
		return err
	}
}

func (s ServiceService) DeleteService(ctx context.Context, id string) int {
	service := map[string]interface{}{}

	deleted := model.DBO{
		ID:        id,
		Resource:  service,
		Timestamp: time.Now().UnixMilli(),
		Deleted:   true,
	}

	_, err := s.ServiceRepository.GetResource(ctx, id)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Failed to get service with id %s for deletion", id)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	}

	err = s.ServiceRepository.StoreResource(ctx, deleted)

	if err != nil {
		logrus.Errorf("Failed to delete service with id %s", id)
		logger.LogAndPrintError(err)
		return http.StatusInternalServerError
	} else {
		return http.StatusNoContent
	}
}
