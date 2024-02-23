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

type DataServiceInputType interface {
	DataServiceEvent() (modelAvro.DataServiceEvent, error)
}

type DataServiceInput struct {
	message      *kafka.Message
	deserializer *avro.SpecificDeserializer
}

func (input DataServiceInput) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	event := modelAvro.DataServiceEvent{}
	err := input.deserializer.DeserializeInto(*input.message.TopicPartition.Topic, input.message.Value, &event)
	return event, err
}

func ConsumeDataServiceMessage(input DataServiceInputType) error {
	event, err := input.DataServiceEvent()
	if err == nil {
		switch event.Type {
		case modelAvro.DataServiceEventTypeDATA_SERVICE_REMOVED:
			dataServiceService := service.InitDataServiceService()
			err = dataServiceService.RemoveDataService(context.TODO(), event.FdkId, event.Timestamp)
		default:
			// Ignoring other data service messages
		}
	}

	if err != nil {
		logrus.Errorf("Error when consuming data service message")
		logger.LogAndPrintError(err)
	}
	return err
}
