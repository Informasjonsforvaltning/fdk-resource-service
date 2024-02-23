package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockEventHarvested struct{}

func (m MockEventHarvested) EventEvent() (modelAvro.EventEvent, error) {
	return modelAvro.EventEvent{
		Type:      modelAvro.EventEventTypeEVENT_HARVESTED,
		Timestamp: 123,
		FdkId:     "111",
	}, nil
}

type MockEventReasoned struct{}

func (m MockEventReasoned) EventEvent() (modelAvro.EventEvent, error) {
	return modelAvro.EventEvent{
		Type:      modelAvro.EventEventTypeEVENT_REASONED,
		Timestamp: 123,
		FdkId:     "111",
	}, nil
}

type MockEventError struct{}

func (m MockEventError) EventEvent() (modelAvro.EventEvent, error) {
	return modelAvro.EventEvent{}, errors.New("deserialize error")
}

type MockEventOldRemoved struct{}

func (m MockEventOldRemoved) EventEvent() (modelAvro.EventEvent, error) {
	return modelAvro.EventEvent{
		Type:      modelAvro.EventEventTypeEVENT_REMOVED,
		Timestamp: 5,
		FdkId:     "111",
	}, nil
}

type MockEventRemoved struct{}

func (m MockEventRemoved) EventEvent() (modelAvro.EventEvent, error) {
	return modelAvro.EventEvent{
		Type:      modelAvro.EventEventTypeEVENT_REMOVED,
		Timestamp: 123,
		FdkId:     "123",
	}, nil
}

type MockEventRemovedNotFound struct{}

func (m MockEventRemovedNotFound) EventEvent() (modelAvro.EventEvent, error) {
	return modelAvro.EventEvent{
		Type:      modelAvro.EventEventTypeEVENT_REMOVED,
		Timestamp: 123,
		FdkId:     "invalid",
	}, nil
}
