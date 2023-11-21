package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockDatasetHarvested struct{}

func (m MockDatasetHarvested) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:  modelAvro.DatasetEventTypeDATASET_HARVESTED,
		FdkId: "111",
	}, nil
}

type MockDatasetReasoned struct{}

func (m MockDatasetReasoned) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:  modelAvro.DatasetEventTypeDATASET_REASONED,
		FdkId: "111",
	}, nil
}

type MockDatasetError struct{}

func (m MockDatasetError) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{}, errors.New("deserialize error")
}

type MockDatasetRemoved struct{}

func (m MockDatasetRemoved) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:  modelAvro.DatasetEventTypeDATASET_REMOVED,
		FdkId: "123",
	}, nil
}

type MockDatasetRemovedNotFound struct{}

func (m MockDatasetRemovedNotFound) DatasetEvent() (modelAvro.DatasetEvent, error) {
	return modelAvro.DatasetEvent{
		Type:  modelAvro.DatasetEventTypeDATASET_REMOVED,
		FdkId: "invalid",
	}, nil
}
