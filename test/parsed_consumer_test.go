package test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
)

func TestParsedConceptMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockConceptParsed{})
	dbo, _ := conceptRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed concept nb", nbTitle)
}

func TestParsedDataServiceMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockDataServiceParsed{})
	dbo, _ := dataServiceRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed data service nb", nbTitle)
}

func TestDatasetMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockDatasetParsed{})
	dbo, _ := datasetRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed dataset nb", nbTitle)
}

func TestParsedInfoModelMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockInfoModelParsed{})
	dbo, _ := informationModelRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed infomodel nb", nbTitle)
}

func TestParsedEventMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockEventParsed{})
	dbo, _ := eventRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed event nb", nbTitle)
}

func TestParsedServiceMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockServiceParsed{})
	dbo, _ := serviceRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed service nb", nbTitle)
}
