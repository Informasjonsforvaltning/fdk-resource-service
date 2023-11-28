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

type DatasetInputType interface {
	DatasetEvent() (modelAvro.DatasetEvent, error)
}

type DatasetInput struct {
	message      *kafka.Message
	deserializer *avro.SpecificDeserializer
}

func (input DatasetInput) DatasetEvent() (modelAvro.DatasetEvent, error) {
	event := modelAvro.DatasetEvent{}
	err := input.deserializer.DeserializeInto(*input.message.TopicPartition.Topic, input.message.Value, &event)
	return event, err
}

func ConsumeDatasetMessage(input DatasetInputType) error {
	event, err := input.DatasetEvent()
	if err == nil {
		switch event.Type {
		case modelAvro.DatasetEventTypeDATASET_REMOVED:
			datasetService := service.InitDatasetService()
			err = datasetService.RemoveDataset(context.TODO(), event.FdkId)
		default:
			// Ignoring other dataset events
		}
	}

	if err != nil {
		logrus.Errorf("Error when consuming dataset event")
		logger.LogAndPrintError(err)
	}
	return err
}
