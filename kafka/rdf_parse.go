package kafka

import (
	"context"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde/avro"
	"github.com/sirupsen/logrus"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type ParseInputType interface {
	ParseEvent() (modelAvro.RdfParseEvent, error)
}

type ParseInput struct {
	message      *kafka.Message
	deserializer *avro.SpecificDeserializer
}

func (input ParseInput) ParseEvent() (modelAvro.RdfParseEvent, error) {
	event := modelAvro.RdfParseEvent{}
	err := input.deserializer.DeserializeInto(*input.message.TopicPartition.Topic, input.message.Value, &event)
	return event, err
}

func ConsumeParseMessage(input ParseInputType) error {
	event, err := input.ParseEvent()
	if err == nil {
		switch event.ResourceType {
		case modelAvro.RdfParseResourceTypeCONCEPT:
			conceptService := service.InitConceptService()
			err = conceptService.StoreConcept(context.TODO(), []byte(event.Data), event.Timestamp)
		case modelAvro.RdfParseResourceTypeDATASERVICE:
			dataServiceService := service.InitDataServiceService()
			err = dataServiceService.StoreDataService(context.TODO(), []byte(event.Data), event.Timestamp)
		case modelAvro.RdfParseResourceTypeDATASET:
			datasetService := service.InitDatasetService()
			err = datasetService.StoreDataset(context.TODO(), []byte(event.Data), event.Timestamp)
		case modelAvro.RdfParseResourceTypeEVENT:
			eventService := service.InitEventService()
			err = eventService.StoreEvent(context.TODO(), []byte(event.Data), event.Timestamp)
		case modelAvro.RdfParseResourceTypeINFORMATIONMODEL:
			infoModelService := service.InitInformationModelService()
			err = infoModelService.StoreInformationModel(context.TODO(), []byte(event.Data), event.Timestamp)
		case modelAvro.RdfParseResourceTypeSERVICE:
			serviceService := service.InitServiceService()
			err = serviceService.StoreService(context.TODO(), []byte(event.Data), event.Timestamp)
		default:
			// Ignoring other parse messages
		}
	}

	if err != nil {
		logrus.Errorf("Error when consuming parse message")
		logger.LogAndPrintError(err)
	}
	return err
}
