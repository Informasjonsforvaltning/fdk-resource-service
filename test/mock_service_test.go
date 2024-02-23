package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockServiceHarvested struct{}

func (m MockServiceHarvested) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:      modelAvro.ServiceEventTypeSERVICE_HARVESTED,
		Timestamp: 123,
		FdkId:     "111",
	}, nil
}

type MockServiceReasoned struct{}

func (m MockServiceReasoned) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:      modelAvro.ServiceEventTypeSERVICE_REASONED,
		Timestamp: 123,
		FdkId:     "111",
	}, nil
}

type MockServiceError struct{}

func (m MockServiceError) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{}, errors.New("deserialize error")
}

type MockServiceOldRemoved struct{}

func (m MockServiceOldRemoved) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:      modelAvro.ServiceEventTypeSERVICE_REMOVED,
		Timestamp: 5,
		FdkId:     "111",
	}, nil
}

type MockServiceRemoved struct{}

func (m MockServiceRemoved) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:      modelAvro.ServiceEventTypeSERVICE_REMOVED,
		Timestamp: 123,
		FdkId:     "123",
	}, nil
}

type MockServiceRemovedNotFound struct{}

func (m MockServiceRemovedNotFound) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:      modelAvro.ServiceEventTypeSERVICE_REMOVED,
		Timestamp: 123,
		FdkId:     "invalid",
	}, nil
}
