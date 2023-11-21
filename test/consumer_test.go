package test

import (
	"context"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/service"
)

var repo = repository.InitRepository()

func TestDatasetErrorReturnsError(t *testing.T) {
	err := service.ConsumeDatasetMessage(MockDatasetError{})
	assert.NotNil(t, err)
}

func TestDatasetHarvestedIsIgnored(t *testing.T) {
	service.ConsumeDatasetMessage(MockDatasetHarvested{})
	dbo, _ := repo.GetDataset(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestDatasetReasonedIsIgnored(t *testing.T) {
	service.ConsumeDatasetMessage(MockDatasetReasoned{})
	dbo, _ := repo.GetDataset(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestDatasetRemovedTagsDatasetAsRemoved(t *testing.T) {
	service.ConsumeDatasetMessage(MockDatasetRemoved{})
	dbo, _ := repo.GetDataset(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestDatasetRemoveNotFoundReturnsError(t *testing.T) {
	err := service.ConsumeDatasetMessage(MockDatasetRemovedNotFound{})
	assert.NotNil(t, err)
}
