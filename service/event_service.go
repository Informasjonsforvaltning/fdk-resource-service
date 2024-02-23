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

func (service EventService) GetEvents(ctx context.Context, includeRemoved string) ([]map[string]interface{}, int) {
	query := bson.D{}
	if strings.ToLower(includeRemoved) != "true" {
		query = bson.D{{Key: "removed", Value: false}}
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
	if err == mongo.ErrNoDocuments {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get event with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else {
		return dbo.Resource, http.StatusOK
	}
}

func (service EventService) StoreEvent(ctx context.Context, bytes []byte) error {
	var event map[string]interface{}
	err := json.Unmarshal(bytes, &event)
	if err != nil {
		return err
	}

	return service.EventRepository.StoreResource(ctx, mappers.ToDBO(event))
}

func (service EventService) RemoveEvent(ctx context.Context, id string) error {
	logrus.Infof("Tagging event %s as removed", id)
	event, err := service.EventRepository.GetResource(ctx, id)
	if err == nil {
		event.Removed = true
		err = service.EventRepository.StoreResource(ctx, event)
	}

	return err
}
