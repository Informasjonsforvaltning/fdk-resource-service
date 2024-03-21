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

type InformationModelService struct {
	InformationModelRepository repository.ResourceRepository
}

func InitInformationModelService() *InformationModelService {
	service := InformationModelService{
		InformationModelRepository: repository.InitInformationModelRepository(),
	}
	return &service
}

func (service InformationModelService) GetInformationModels(ctx context.Context, includeRemoved string) ([]map[string]interface{}, int) {
	query := bson.D{}
	if strings.ToLower(includeRemoved) != "true" {
		query = bson.D{{Key: "removed", Value: false}}
	}
	informationModels, err := service.InformationModelRepository.GetResources(ctx, query)
	if err != nil {
		logrus.Error("Get information models failed ")
		logger.LogAndPrintError(err)
		return []map[string]interface{}{}, http.StatusInternalServerError
	} else if informationModels == nil {
		return []map[string]interface{}{}, http.StatusOK
	}

	return mappers.ToDTO(informationModels), http.StatusOK
}

func (service InformationModelService) GetInformationModel(ctx context.Context, id string) (map[string]interface{}, int) {
	dbo, err := service.InformationModelRepository.GetResource(ctx, id)
	if err == mongo.ErrNoDocuments {
		return map[string]interface{}{}, http.StatusNotFound
	} else if err != nil {
		logrus.Errorf("Get information model with id %s failed, ", id)
		logger.LogAndPrintError(err)
		return map[string]interface{}{}, http.StatusInternalServerError
	} else {
		return dbo.Resource, http.StatusOK
	}
}

func (service InformationModelService) StoreInformationModel(ctx context.Context, bytes []byte, timestamp int64) error {
	var informationModel map[string]interface{}
	err := json.Unmarshal(bytes, &informationModel)
	if err != nil {
		return err
	}

	updated := model.DBO{
		ID:        informationModel["id"].(string),
		Resource:  informationModel,
		Timestamp: timestamp,
	}

	return service.InformationModelRepository.StoreResource(ctx, updated)
}
