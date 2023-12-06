package test

import (
	"errors"
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockConceptHarvested struct{}

func (m MockConceptHarvested) ConceptEvent() (modelAvro.ConceptEvent, error) {
	return modelAvro.ConceptEvent{
		Type:  modelAvro.ConceptEventTypeCONCEPT_HARVESTED,
		FdkId: "111",
	}, nil
}

type MockConceptReasoned struct{}

func (m MockConceptReasoned) ConceptEvent() (modelAvro.ConceptEvent, error) {
	return modelAvro.ConceptEvent{
		Type:  modelAvro.ConceptEventTypeCONCEPT_REASONED,
		FdkId: "111",
	}, nil
}

type MockConceptError struct{}

func (m MockConceptError) ConceptEvent() (modelAvro.ConceptEvent, error) {
	return modelAvro.ConceptEvent{}, errors.New("deserialize error")
}

type MockConceptRemoved struct{}

func (m MockConceptRemoved) ConceptEvent() (modelAvro.ConceptEvent, error) {
	return modelAvro.ConceptEvent{
		Type:  modelAvro.ConceptEventTypeCONCEPT_REMOVED,
		FdkId: "123",
	}, nil
}

type MockConceptRemovedNotFound struct{}

func (m MockConceptRemovedNotFound) ConceptEvent() (modelAvro.ConceptEvent, error) {
	return modelAvro.ConceptEvent{
		Type:  modelAvro.ConceptEventTypeCONCEPT_REMOVED,
		FdkId: "invalid",
	}, nil
}
