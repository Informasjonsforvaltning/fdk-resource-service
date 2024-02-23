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

type ServiceInputType interface {
	ServiceEvent() (modelAvro.ServiceEvent, error)
}

type ServiceInput struct {
	message      *kafka.Message
	deserializer *avro.SpecificDeserializer
}

func (input ServiceInput) ServiceEvent() (modelAvro.ServiceEvent, error) {
	event := modelAvro.ServiceEvent{}
	err := input.deserializer.DeserializeInto(*input.message.TopicPartition.Topic, input.message.Value, &event)
	return event, err
}

func ConsumeServiceMessage(input ServiceInputType) error {
	event, err := input.ServiceEvent()
	if err == nil {
		switch event.Type {
		case modelAvro.ServiceEventTypeSERVICE_REMOVED:
			serviceService := service.InitServiceService()
			err = serviceService.RemoveService(context.TODO(), event.FdkId, event.Timestamp)
		default:
			// Ignoring other service messages
		}
	}

	if err != nil {
		logrus.Errorf("Error when consuming service message")
		logger.LogAndPrintError(err)
	}
	return err
}
