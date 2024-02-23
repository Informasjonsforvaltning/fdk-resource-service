package test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
)

func TestInformationModelErrorReturnsError(t *testing.T) {
	err := kafka.ConsumeInfoModelMessage(MockInformationModelError{})
	assert.NotNil(t, err)
}

func TestInformationModelHarvestedIsIgnored(t *testing.T) {
	kafka.ConsumeInfoModelMessage(MockInformationModelHarvested{})
	dbo, _ := informationModelRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestInformationModelReasonedIsIgnored(t *testing.T) {
	kafka.ConsumeInfoModelMessage(MockInformationModelReasoned{})
	dbo, _ := informationModelRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestInformationModelRemovedTagsInformationModelAsRemoved(t *testing.T) {
	kafka.ConsumeInfoModelMessage(MockInformationModelRemoved{})
	dbo, _ := informationModelRepository.GetResource(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestInformationModelRemoveNotFoundReturnsError(t *testing.T) {
	err := kafka.ConsumeInfoModelMessage(MockInformationModelRemovedNotFound{})
	assert.NotNil(t, err)
}
