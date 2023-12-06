package test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
)

var datasetRepository = repository.InitDatasetRepository()

func TestDatasetErrorReturnsError(t *testing.T) {
	err := kafka.ConsumeDatasetMessage(MockDatasetError{})
	assert.NotNil(t, err)
}

func TestDatasetHarvestedIsIgnored(t *testing.T) {
	kafka.ConsumeDatasetMessage(MockDatasetHarvested{})
	dbo, _ := datasetRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestDatasetReasonedIsIgnored(t *testing.T) {
	kafka.ConsumeDatasetMessage(MockDatasetReasoned{})
	dbo, _ := datasetRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestDatasetRemovedTagsDatasetAsRemoved(t *testing.T) {
	kafka.ConsumeDatasetMessage(MockDatasetRemoved{})
	dbo, _ := datasetRepository.GetResource(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestDatasetRemoveNotFoundReturnsError(t *testing.T) {
	err := kafka.ConsumeDatasetMessage(MockDatasetRemovedNotFound{})
	assert.NotNil(t, err)
}
