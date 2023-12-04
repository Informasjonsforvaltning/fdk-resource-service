package test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
)

var repo = repository.InitRepository()

func TestDatasetErrorReturnsError(t *testing.T) {
	err := kafka.ConsumeDatasetMessage(MockDatasetError{})
	assert.NotNil(t, err)
}

func TestDatasetHarvestedIsIgnored(t *testing.T) {
	kafka.ConsumeDatasetMessage(MockDatasetHarvested{})
	dbo, _ := repo.GetDataset(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestDatasetReasonedIsIgnored(t *testing.T) {
	kafka.ConsumeDatasetMessage(MockDatasetReasoned{})
	dbo, _ := repo.GetDataset(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestDatasetRemovedTagsDatasetAsRemoved(t *testing.T) {
	kafka.ConsumeDatasetMessage(MockDatasetRemoved{})
	dbo, _ := repo.GetDataset(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestDatasetRemoveNotFoundReturnsError(t *testing.T) {
	err := kafka.ConsumeDatasetMessage(MockDatasetRemovedNotFound{})
	assert.NotNil(t, err)
}
