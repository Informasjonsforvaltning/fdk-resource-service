package test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
)

func TestDataServiceErrorReturnsError(t *testing.T) {
	err := kafka.ConsumeDataServiceMessage(MockDataServiceError{})
	assert.NotNil(t, err)
}

func TestDataServiceHarvestedIsIgnored(t *testing.T) {
	kafka.ConsumeDataServiceMessage(MockDataServiceHarvested{})
	dbo, _ := dataServiceRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestDataServiceReasonedIsIgnored(t *testing.T) {
	kafka.ConsumeDataServiceMessage(MockDataServiceReasoned{})
	dbo, _ := dataServiceRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestDataServiceRemovedTagsDataServiceAsRemoved(t *testing.T) {
	kafka.ConsumeDataServiceMessage(MockDataServiceRemoved{})
	dbo, _ := dataServiceRepository.GetResource(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestDataServiceRemoveNotFoundReturnsError(t *testing.T) {
	err := kafka.ConsumeDataServiceMessage(MockDataServiceRemovedNotFound{})
	assert.NotNil(t, err)
}
