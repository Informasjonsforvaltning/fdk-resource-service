package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockDatasetHarvested struct{}

func (m MockDatasetHarvested) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:      modelAvro.DatasetEventTypeDATASET_HARVESTED,
		Timestamp: 123,
		FdkId:     "111",
	}, nil
}

type MockDatasetReasoned struct{}

func (m MockDatasetReasoned) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:      modelAvro.DatasetEventTypeDATASET_REASONED,
		Timestamp: 123,
		FdkId:     "111",
	}, nil
}

type MockDatasetError struct{}

func (m MockDatasetError) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{}, errors.New("deserialize error")
}

type MockDatasetOldRemoved struct{}

func (m MockDatasetOldRemoved) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:      modelAvro.DatasetEventTypeDATASET_REMOVED,
		Timestamp: 5,
		FdkId:     "111",
	}, nil
}

type MockDatasetRemoved struct{}

func (m MockDatasetRemoved) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:      modelAvro.DatasetEventTypeDATASET_REMOVED,
		Timestamp: 123,
		FdkId:     "123",
	}, nil
}

type MockDatasetRemovedNotFound struct{}

func (m MockDatasetRemovedNotFound) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:      modelAvro.DatasetEventTypeDATASET_REMOVED,
		Timestamp: 123,
		FdkId:     "invalid",
	}, nil
}
