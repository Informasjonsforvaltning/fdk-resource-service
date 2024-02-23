package test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
)

func TestConceptErrorReturnsError(t *testing.T) {
	err := kafka.ConsumeConceptMessage(MockConceptError{})
	assert.NotNil(t, err)
}

func TestConceptHarvestedIsIgnored(t *testing.T) {
	kafka.ConsumeConceptMessage(MockConceptHarvested{})
	dbo, _ := conceptRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestConceptReasonedIsIgnored(t *testing.T) {
	kafka.ConsumeConceptMessage(MockConceptReasoned{})
	dbo, _ := conceptRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestConceptRemovedIsIgnoredWhenMessageIsOld(t *testing.T) {
	kafka.ConsumeConceptMessage(MockConceptRemoved{})
	dbo, _ := conceptRepository.GetResource(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestConceptRemovedTagsConceptAsRemoved(t *testing.T) {
	kafka.ConsumeConceptMessage(MockConceptRemoved{})
	dbo, _ := conceptRepository.GetResource(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestConceptRemoveNotFoundReturnsError(t *testing.T) {
	err := kafka.ConsumeConceptMessage(MockConceptRemovedNotFound{})
	assert.NotNil(t, err)
}
