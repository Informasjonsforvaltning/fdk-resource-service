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

type ConceptInputType interface {
	ConceptEvent() (modelAvro.ConceptEvent, error)
}

type ConceptInput struct {
	message      *kafka.Message
	deserializer *avro.SpecificDeserializer
}

func (input ConceptInput) ConceptEvent() (modelAvro.ConceptEvent, error) {
	event := modelAvro.ConceptEvent{}
	err := input.deserializer.DeserializeInto(*input.message.TopicPartition.Topic, input.message.Value, &event)
	return event, err
}

func ConsumeConceptMessage(input ConceptInputType) error {
	event, err := input.ConceptEvent()
	if err == nil {
		switch event.Type {
		case modelAvro.ConceptEventTypeCONCEPT_REMOVED:
			conceptService := service.InitConceptService()
			err = conceptService.RemoveConcept(context.TODO(), event.FdkId)
		default:
			// Ignoring other concept messages
		}
	}

	if err != nil {
		logrus.Errorf("Error when consuming concept message")
		logger.LogAndPrintError(err)
	}
	return err
}
