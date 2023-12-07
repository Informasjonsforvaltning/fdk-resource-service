package kafka

import (
	"context"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde/avro"
	"github.com/sirupsen/logrus"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
)

type InfoModelInputType interface {
	InfoModelEvent() (modelAvro.InformationModelEvent, error)
}

type InfoModelInput struct {
	message      *kafka.Message
	deserializer *avro.SpecificDeserializer
}

func (input InfoModelInput) InfoModelEvent() (modelAvro.InformationModelEvent, error) {
	event := modelAvro.InformationModelEvent{}
	err := input.deserializer.DeserializeInto(*input.message.TopicPartition.Topic, input.message.Value, &event)
	return event, err
}

func ConsumeInfoModelMessage(input InfoModelInputType) error {
	event, err := input.InfoModelEvent()
	if err == nil {
		switch event.Type {
		case modelAvro.InformationModelEventTypeINFORMATION_MODEL_REMOVED:
			informationModelService := service.InitInformationModelService()
			err = informationModelService.RemoveInformationModel(context.TODO(), event.FdkId)
		default:
			// Ignoring other information model events
		}
	}

	if err != nil {
		logrus.Errorf("Error when consuming dataset event")
		logger.LogAndPrintError(err)
	}
	return err
}
