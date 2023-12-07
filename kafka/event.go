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

type EventInputType interface {
	EventEvent() (modelAvro.EventEvent, error)
}

type EventInput struct {
	message      *kafka.Message
	deserializer *avro.SpecificDeserializer
}

func (input EventInput) EventEvent() (modelAvro.EventEvent, error) {
	event := modelAvro.EventEvent{}
	err := input.deserializer.DeserializeInto(*input.message.TopicPartition.Topic, input.message.Value, &event)
	return event, err
}

func ConsumeEventMessage(input EventInputType) error {
	event, err := input.EventEvent()
	if err == nil {
		switch event.Type {
		case modelAvro.EventEventTypeEVENT_REMOVED:
			eventService := service.InitEventService()
			err = eventService.RemoveEvent(context.TODO(), event.FdkId)
		default:
			// Ignoring other event messages
		}
	}

	if err != nil {
		logrus.Errorf("Error when consuming event message")
		logger.LogAndPrintError(err)
	}
	return err
}
