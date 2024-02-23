package test

import (
	modelAvro "github.com/Informasjonsforvaltning/fdk-resource-service/model/avro"
)

type MockConceptParsed struct{}

func (m MockConceptParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeCONCEPT,
		Data:         "{\"id\":\"333\",\"title\":{\"nb\":\"parsed concept nb\"}}",
		Timestamp:    500,
		FdkId:        "333",
	}, nil
}

type MockDataServiceParsed struct{}

func (m MockDataServiceParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeDATASERVICE,
		Data:         "{\"id\":\"333\",\"title\":{\"nb\":\"parsed data service nb\"}}",
		Timestamp:    500,
		FdkId:        "333",
	}, nil
}

type MockDatasetParsed struct{}

func (m MockDatasetParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeDATASET,
		Data:         "{\"id\":\"333\",\"title\":{\"nb\":\"parsed dataset nb\"}}",
		Timestamp:    500,
		FdkId:        "333",
	}, nil
}

type MockInfoModelParsed struct{}

func (m MockInfoModelParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeINFORMATIONMODEL,
		Data:         "{\"id\":\"333\",\"title\":{\"nb\":\"parsed infomodel nb\"}}",
		Timestamp:    500,
		FdkId:        "333",
	}, nil
}

type MockEventParsed struct{}

func (m MockEventParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeEVENT,
		Data:         "{\"id\":\"333\",\"title\":{\"nb\":\"parsed event nb\"}}",
		Timestamp:    500,
		FdkId:        "333",
	}, nil
}

type MockServiceParsed struct{}

func (m MockServiceParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeSERVICE,
		Data:         "{\"id\":\"333\",\"title\":{\"nb\":\"parsed service nb\"}}",
		Timestamp:    500,
		FdkId:        "333",
	}, nil
}
