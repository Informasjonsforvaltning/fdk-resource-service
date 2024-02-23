package test

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"

	"github.com/Informasjonsforvaltning/fdk-resource-service/kafka"
)

func TestEventErrorReturnsError(t *testing.T) {
	err := kafka.ConsumeEventMessage(MockEventError{})
	assert.NotNil(t, err)
}

func TestEventHarvestedIsIgnored(t *testing.T) {
	kafka.ConsumeEventMessage(MockEventHarvested{})
	dbo, _ := eventRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestEventReasonedIsIgnored(t *testing.T) {
	kafka.ConsumeEventMessage(MockEventReasoned{})
	dbo, _ := eventRepository.GetResource(context.TODO(), "111")
	assert.NotEqual(t, true, dbo.Removed)
}

func TestEventRemovedTagsEventAsRemoved(t *testing.T) {
	kafka.ConsumeEventMessage(MockEventRemoved{})
	dbo, _ := eventRepository.GetResource(context.TODO(), "123")
	assert.Equal(t, true, dbo.Removed)
}

func TestEventRemoveNotFoundReturnsError(t *testing.T) {
	err := kafka.ConsumeEventMessage(MockEventRemovedNotFound{})
	assert.NotNil(t, err)
}
