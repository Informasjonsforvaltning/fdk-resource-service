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
	assert.False(t, dbo.Removed)
}

func TestOldParsedConceptMessageUpdatesResourceButKeepsRemovedStatus(t *testing.T) {
	kafka.ConsumeParseMessage(MockOldConceptParsed{})
	dbo, _ := conceptRepository.GetResource(context.TODO(), "444")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed concept nb", nbTitle)
	assert.True(t, dbo.Removed)
}

func TestParsedDataServiceMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockDataServiceParsed{})
	dbo, _ := dataServiceRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed data service nb", nbTitle)
	assert.False(t, dbo.Removed)
}

func TestOldParsedDataServiceMessageUpdatesResourceButKeepsRemovedStatus(t *testing.T) {
	kafka.ConsumeParseMessage(MockOldDataServiceParsed{})
	dbo, _ := dataServiceRepository.GetResource(context.TODO(), "444")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed data service nb", nbTitle)
	assert.True(t, dbo.Removed)
}

func TestDatasetMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockDatasetParsed{})
	dbo, _ := datasetRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed dataset nb", nbTitle)
	assert.False(t, dbo.Removed)
}

func TestOldDatasetMessageUpdatesResourceButKeepsRemovedStatus(t *testing.T) {
	kafka.ConsumeParseMessage(MockOldDatasetParsed{})
	dbo, _ := datasetRepository.GetResource(context.TODO(), "444")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed dataset nb", nbTitle)
	assert.True(t, dbo.Removed)
}

func TestParsedInfoModelMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockInfoModelParsed{})
	dbo, _ := informationModelRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed infomodel nb", nbTitle)
	assert.False(t, dbo.Removed)
}

func TestOldParsedInfoModelMessageUpdatesResourceButKeepsRemovedStatus(t *testing.T) {
	kafka.ConsumeParseMessage(MockOldInfoModelParsed{})
	dbo, _ := informationModelRepository.GetResource(context.TODO(), "444")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed infomodel nb", nbTitle)
	assert.True(t, dbo.Removed)
}

func TestParsedEventMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockEventParsed{})
	dbo, _ := eventRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed event nb", nbTitle)
	assert.False(t, dbo.Removed)
}

func TestOldParsedEventMessageUpdatesResourceButKeepsRemovedStatus(t *testing.T) {
	kafka.ConsumeParseMessage(MockOldEventParsed{})
	dbo, _ := eventRepository.GetResource(context.TODO(), "444")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed event nb", nbTitle)
	assert.True(t, dbo.Removed)
}

func TestParsedServiceMessageUpdatesResource(t *testing.T) {
	kafka.ConsumeParseMessage(MockServiceParsed{})
	dbo, _ := serviceRepository.GetResource(context.TODO(), "333")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed service nb", nbTitle)
	assert.False(t, dbo.Removed)
}

func TestOldParsedServiceMessageUpdatesResourceButKeepsRemovedStatus(t *testing.T) {
	kafka.ConsumeParseMessage(MockOldServiceParsed{})
	dbo, _ := serviceRepository.GetResource(context.TODO(), "444")
	nbTitle := dbo.Resource["title"].(map[string]interface{})["nb"]
	assert.Equal(t, "parsed service nb", nbTitle)
	assert.True(t, dbo.Removed)
}
