package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockServiceHarvested struct{}

func (m MockServiceHarvested) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:  modelAvro.ServiceEventTypeSERVICE_HARVESTED,
		FdkId: "111",
	}, nil
}

type MockServiceReasoned struct{}

func (m MockServiceReasoned) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:  modelAvro.ServiceEventTypeSERVICE_REASONED,
		FdkId: "111",
	}, nil
}

type MockServiceError struct{}

func (m MockServiceError) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{}, errors.New("deserialize error")
}

type MockServiceRemoved struct{}

func (m MockServiceRemoved) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:  modelAvro.ServiceEventTypeSERVICE_REMOVED,
		FdkId: "123",
	}, nil
}

type MockServiceRemovedNotFound struct{}

func (m MockServiceRemovedNotFound) ServiceEvent() (modelAvro.ServiceEvent, error) {
	return modelAvro.ServiceEvent{
		Type:  modelAvro.ServiceEventTypeSERVICE_REMOVED,
		FdkId: "invalid",
	}, nil
}
