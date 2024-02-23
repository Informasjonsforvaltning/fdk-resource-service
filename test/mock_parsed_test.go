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

type MockOldConceptParsed struct{}

func (m MockOldConceptParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeCONCEPT,
		Data:         "{\"id\":\"444\",\"title\":{\"nb\":\"parsed concept nb\"}}",
		Timestamp:    5,
		FdkId:        "444",
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

type MockOldDataServiceParsed struct{}

func (m MockOldDataServiceParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeDATASERVICE,
		Data:         "{\"id\":\"444\",\"title\":{\"nb\":\"parsed data service nb\"}}",
		Timestamp:    5,
		FdkId:        "444",
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

type MockOldDatasetParsed struct{}

func (m MockOldDatasetParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeDATASET,
		Data:         "{\"id\":\"444\",\"title\":{\"nb\":\"parsed dataset nb\"}}",
		Timestamp:    5,
		FdkId:        "444",
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

type MockOldInfoModelParsed struct{}

func (m MockOldInfoModelParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeINFORMATIONMODEL,
		Data:         "{\"id\":\"444\",\"title\":{\"nb\":\"parsed infomodel nb\"}}",
		Timestamp:    5,
		FdkId:        "444",
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

type MockOldEventParsed struct{}

func (m MockOldEventParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeEVENT,
		Data:         "{\"id\":\"444\",\"title\":{\"nb\":\"parsed event nb\"}}",
		Timestamp:    5,
		FdkId:        "444",
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

type MockOldServiceParsed struct{}

func (m MockOldServiceParsed) ParseEvent() (modelAvro.RdfParseEvent, error) {
	return modelAvro.RdfParseEvent{
		ResourceType: modelAvro.RdfParseResourceTypeSERVICE,
		Data:         "{\"id\":\"444\",\"title\":{\"nb\":\"parsed service nb\"}}",
		Timestamp:    5,
		FdkId:        "444",
	}, nil
}
