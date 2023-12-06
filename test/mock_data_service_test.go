package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockDataServiceHarvested struct{}

func (m MockDataServiceHarvested) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:  modelAvro.DataServiceEventTypeDATA_SERVICE_HARVESTED,
		FdkId: "111",
	}, nil
}

type MockDataServiceReasoned struct{}

func (m MockDataServiceReasoned) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:  modelAvro.DataServiceEventTypeDATA_SERVICE_REASONED,
		FdkId: "111",
	}, nil
}

type MockDataServiceError struct{}

func (m MockDataServiceError) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{}, errors.New("deserialize error")
}

type MockDataServiceRemoved struct{}

func (m MockDataServiceRemoved) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:  modelAvro.DataServiceEventTypeDATA_SERVICE_REMOVED,
		FdkId: "123",
	}, nil
}

type MockDataServiceRemovedNotFound struct{}

func (m MockDataServiceRemovedNotFound) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:  modelAvro.DataServiceEventTypeDATA_SERVICE_REMOVED,
		FdkId: "invalid",
	}, nil
}
