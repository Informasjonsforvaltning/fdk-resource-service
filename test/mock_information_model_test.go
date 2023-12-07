package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockInformationModelHarvested struct{}

func (m MockInformationModelHarvested) InfoModelEvent() (modelAvro.InformationModelEvent, error) {
	return modelAvro.InformationModelEvent{
		Type:  modelAvro.InformationModelEventTypeINFORMATION_MODEL_HARVESTED,
		FdkId: "111",
	}, nil
}

type MockInformationModelReasoned struct{}

func (m MockInformationModelReasoned) InfoModelEvent() (modelAvro.InformationModelEvent, error) {
	return modelAvro.InformationModelEvent{
		Type:  modelAvro.InformationModelEventTypeINFORMATION_MODEL_REASONED,
		FdkId: "111",
	}, nil
}

type MockInformationModelError struct{}

func (m MockInformationModelError) InfoModelEvent() (modelAvro.InformationModelEvent, error) {
	return modelAvro.InformationModelEvent{}, errors.New("deserialize error")
}

type MockInformationModelRemoved struct{}

func (m MockInformationModelRemoved) InfoModelEvent() (modelAvro.InformationModelEvent, error) {
	return modelAvro.InformationModelEvent{
		Type:  modelAvro.InformationModelEventTypeINFORMATION_MODEL_REMOVED,
		FdkId: "123",
	}, nil
}

type MockInformationModelRemovedNotFound struct{}

func (m MockInformationModelRemovedNotFound) InfoModelEvent() (modelAvro.InformationModelEvent, error) {
	return modelAvro.InformationModelEvent{
		Type:  modelAvro.InformationModelEventTypeINFORMATION_MODEL_REMOVED,
		FdkId: "invalid",
	}, nil
}
