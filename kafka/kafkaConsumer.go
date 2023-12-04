package kafka

import (
	"os"
	"os/signal"
	"syscall"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde"
	"github.com/confluentinc/confluent-kafka-go/v2/schemaregistry/serde/avro"
	"github.com/sirupsen/logrus"

	"github.com/Informasjonsforvaltning/fdk-resource-service/config/env"
	"github.com/Informasjonsforvaltning/fdk-resource-service/config/logger"
)

func consumeMessage(message *kafka.Message, deserializer *avro.SpecificDeserializer) {
	switch *message.TopicPartition.Topic {
	case env.KafkaValues.DatasetTopic:
		ConsumeDatasetMessage(DatasetInput{message: message, deserializer: deserializer})
	case env.KafkaValues.InfoModelTopic:
		ConsumeInfoModelMessage(InfoModelInput{message: message, deserializer: deserializer})
	default:
		// ignoring other topics
	}
}

var ConsumeKafkaEvents = func() {
	run := true
	consumer, err := kafka.NewConsumer(&kafka.ConfigMap{
		"bootstrap.servers":        env.KafkaBrokers(),
		"auto.offset.reset":        "latest",
		"allow.auto.create.topics": false,
		"group.id":                 "fdk_resource_service",
	})

	if err != nil {
		logrus.Errorf("Unable to init kafka consumer")
		logger.LogAndPrintError(err)
		run = false
	}

	client, err := schemaregistry.NewClient(schemaregistry.NewConfig(env.SchemaRegistry()))
	if err != nil {
		logrus.Errorf("Failed to create schema registry client")
		logger.LogAndPrintError(err)
		run = false
	}

	deserializer, err := avro.NewSpecificDeserializer(client, serde.ValueSerde, avro.NewDeserializerConfig())
	if err != nil {
		logrus.Errorf("Failed to create avro deserializer")
		logger.LogAndPrintError(err)
		run = false
	}

	topics := []string{
		env.KafkaValues.DatasetTopic,
		env.KafkaValues.InfoModelTopic,
	}
	err = consumer.SubscribeTopics(topics, nil)
	if err != nil {
		logrus.Errorf("Error when subscribing to kafka topics")
		logger.LogAndPrintError(err)
		run = false
	}

	sigchan := make(chan os.Signal, 1)
	signal.Notify(sigchan, syscall.SIGINT, syscall.SIGTERM)

	for run {
		select {
		case sig := <-sigchan:
			logrus.Errorf("Caught signal %v: terminating", sig)
			run = false
		default:
			event := consumer.Poll(100)
			if event == nil {
				continue
			}

			switch message := event.(type) {
			case *kafka.Message:
				consumeMessage(message, deserializer)
			case kafka.Error:
				logrus.Errorf("Error: %v: %v", message.Code(), message)
			default:
				// Ignoring other messages
			}
		}
	}

	consumer.Close()
}
