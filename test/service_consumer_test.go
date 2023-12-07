package test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
	"github.com/Informasjonsforvaltning/fdk-resource-service/repository"
)

var serviceRepository = repository.InitServiceRepository()

func TestServiceErrorReturnsError(t *testing.T) {
	err := kafka.ConsumeServiceMessage(MockServiceError{})
	assert.NotNil(t, err)
}

func TestServiceHarvestedIsIgnored(t *testing.T) {
	kafka.ConsumeServiceMessage(MockServiceHarvested{})
	dbo, _ := serviceRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestServiceReasonedIsIgnored(t *testing.T) {
	kafka.ConsumeServiceMessage(MockServiceReasoned{})
	dbo, _ := serviceRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestServiceRemovedTagsServiceAsRemoved(t *testing.T) {
	kafka.ConsumeServiceMessage(MockServiceRemoved{})
	dbo, _ := serviceRepository.GetResource(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestServiceRemoveNotFoundReturnsError(t *testing.T) {
	err := kafka.ConsumeServiceMessage(MockServiceRemovedNotFound{})
	assert.NotNil(t, err)
}
