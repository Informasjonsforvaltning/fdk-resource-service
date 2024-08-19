package service

import (
	"context"
	"encoding/json"
	"errors"
	"github.com/Informasjonsforvaltning/fdk-resource-service/model"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/mappers"
	"github.com/Informasjonsforvaltning/fdk-resource-service/utils/validate"
	"net/http"

	"github.com/sirupsen/logrus"
	"go.mongodb.org/mongo-driver/bson"
	"go.mongodb.org/mongo-driver/mongo"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
)

type EventService struct {
	EventRepository repository.ResourceRepository
}

func InitEventService() *EventService {
	service := EventService{
		EventRepository: repository.InitEventRepository(),
	}
	return &service
}

func (service EventService) GetEvents(ctx context.Context, filters *model.Filters) ([]map[string]interface{}, int) {
	query := bson.D{}
	if filters != nil {
		var ids []string
		for _, id := range filters.IDs {
			ids = append(ids, validate.SanitizeID(id))
		}
		query = bson.D{{Key: "_id", Value: bson.D{{Key: "$in", Value: ids}}}}
	}
	events, err := service.EventRepository.GetResources(ctx, query)
	if err != nil {
		logrus.Error("Get events failed ")
		logger.LogAndPrintError(err)
		return []map[string]interface{}{}, http.StatusInternalServerError
	} else if events == nil {
		return []map[string]interface{}{}, http.StatusOK
	}

	return mappers.ToDTO(events), http.StatusOK
}

func (service EventService) GetEvent(ctx context.Context, id string) (map[string]interface{}, int) {
	dbo, err := service.EventRepository.GetResource(ctx, id)
	if errors.Is(err, mongo.ErrNoDocuments) {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get event with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else {
		return dbo.Resource, http.StatusOK
	}
}

func (service EventService) StoreEvent(ctx context.Context, bytes []byte, timestamp int64) error {
	var event map[string]interface{}
	err := json.Unmarshal(bytes, &event)
	if err != nil {
		return err
	}

	updated := model.DBO{
		ID:        event["id"].(string),
		Resource:  event,
		Timestamp: timestamp,
	}

	dbo, err := service.EventRepository.GetResource(ctx, updated.ID)
	if err == nil && dbo.Timestamp > updated.Timestamp {
		return nil // do not update if current timestamp is higher
	} else if err == nil || errors.Is(err, mongo.ErrNoDocuments) {
		return service.EventRepository.StoreResource(ctx, updated)
	} else {
		return err
	}
}
