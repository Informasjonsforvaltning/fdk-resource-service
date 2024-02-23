package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockDataServiceHarvested struct{}

func (m MockDataServiceHarvested) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:      modelAvro.DataServiceEventTypeDATA_SERVICE_HARVESTED,
		Timestamp: 123,
		FdkId:     "111",
	}, nil
}

type MockDataServiceReasoned struct{}

func (m MockDataServiceReasoned) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:      modelAvro.DataServiceEventTypeDATA_SERVICE_REASONED,
		Timestamp: 123,
		FdkId:     "111",
	}, nil
}

type MockDataServiceError struct{}

func (m MockDataServiceError) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{}, errors.New("deserialize error")
}

type MockDataServiceOldRemoved struct{}

func (m MockDataServiceOldRemoved) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:      modelAvro.DataServiceEventTypeDATA_SERVICE_REMOVED,
		Timestamp: 5,
		FdkId:     "111",
	}, nil
}

type MockDataServiceRemoved struct{}

func (m MockDataServiceRemoved) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:      modelAvro.DataServiceEventTypeDATA_SERVICE_REMOVED,
		Timestamp: 123,
		FdkId:     "123",
	}, nil
}

type MockDataServiceRemovedNotFound struct{}

func (m MockDataServiceRemovedNotFound) DataServiceEvent() (modelAvro.DataServiceEvent, error) {
	return modelAvro.DataServiceEvent{
		Type:      modelAvro.DataServiceEventTypeDATA_SERVICE_REMOVED,
		Timestamp: 123,
		FdkId:     "invalid",
	}, nil
}
