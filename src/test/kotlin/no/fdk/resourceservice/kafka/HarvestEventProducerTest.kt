package no.fdk.resourceservice.kafka

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.fdk.harvest.HarvestEvent
import no.fdk.resourceservice.model.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.core.KafkaTemplate

class HarvestEventProducerTest {
    private val kafkaTemplate = mockk<KafkaTemplate<String, HarvestEvent>>()
    private val harvestTopic = "harvest-events"
    private lateinit var producer: HarvestEventProducer

    @BeforeEach
    fun setUp() {
        producer = HarvestEventProducer(kafkaTemplate, harvestTopic)
        every { kafkaTemplate.send(any(), any<String>(), any()) } returns mockk(relaxed = true)
    }

    @Test
    fun `produceResourceFinishedEvent does not send when harvestRunId is null`() {
        producer.produceResourceFinishedEvent(
            harvestRunId = null,
            resourceType = ResourceType.CONCEPT,
            fdkId = "fdk-1",
            resourceUri = "https://example.com/1",
            startTime = 1000L,
            endTime = 2000L,
        )
        verify(exactly = 0) { kafkaTemplate.send(any(), any(), any<HarvestEvent>()) }
    }

    @Test
    fun `produceResourceFinishedEvent sends event with correct payload`() {
        val eventSlot = slot<HarvestEvent>()
        every { kafkaTemplate.send(harvestTopic, "run-1", capture(eventSlot)) } returns mockk(relaxed = true)

        producer.produceResourceFinishedEvent(
            harvestRunId = "run-1",
            resourceType = ResourceType.DATASET,
            fdkId = "fdk-2",
            resourceUri = "https://example.com/2",
            startTime = 1000L,
            endTime = 2000L,
        )

        verify(exactly = 1) { kafkaTemplate.send(harvestTopic, "run-1", any<HarvestEvent>()) }
        val event = eventSlot.captured
        assertNotNull(event)
        assertEquals("run-1", event.runId)
        assertEquals("fdk-2", event.fdkId)
        assertEquals("https://example.com/2", event.resourceUri)
        assertEquals(
            no.fdk.harvest.DataType
                .valueOf("dataset"),
            event.dataType,
        )
        assertEquals(no.fdk.harvest.HarvestPhase.RESOURCE_PROCESSING, event.phase)
        assertNotNull(event.startTime)
        assertNotNull(event.endTime)
    }

    @Test
    fun `produceResourceRemovedEvent does not send when harvestRunId is null`() {
        producer.produceResourceRemovedEvent(
            harvestRunId = null,
            resourceType = ResourceType.SERVICE,
            fdkId = "fdk-3",
            resourceUri = null,
            startTime = 1000L,
            endTime = 2000L,
        )
        verify(exactly = 0) { kafkaTemplate.send(any(), any(), any<HarvestEvent>()) }
    }

    @Test
    fun `produceResourceRemovedEvent sends event`() {
        val eventSlot = slot<HarvestEvent>()
        every { kafkaTemplate.send(harvestTopic, "run-2", capture(eventSlot)) } returns mockk(relaxed = true)

        producer.produceResourceRemovedEvent(
            harvestRunId = "run-2",
            resourceType = ResourceType.EVENT,
            fdkId = "fdk-4",
            resourceUri = null,
            startTime = 1000L,
            endTime = 3000L,
        )

        verify(exactly = 1) { kafkaTemplate.send(harvestTopic, "run-2", any<HarvestEvent>()) }
        assertEquals("run-2", eventSlot.captured.runId)
        assertEquals("fdk-4", eventSlot.captured.fdkId)
        assertEquals(
            no.fdk.harvest.DataType
                .valueOf("event"),
            eventSlot.captured.dataType,
        )
    }

    @Test
    fun `produceResourceFailedEvent does not send when harvestRunId is null`() {
        producer.produceResourceFailedEvent(
            harvestRunId = null,
            resourceType = ResourceType.INFORMATION_MODEL,
            fdkId = "fdk-5",
            resourceUri = null,
            startTime = 1000L,
            endTime = 2000L,
            errorMessage = "error",
        )
        verify(exactly = 0) { kafkaTemplate.send(any(), any(), any<HarvestEvent>()) }
    }

    @Test
    fun `produceResourceFailedEvent sends event with error message`() {
        val eventSlot = slot<HarvestEvent>()
        every { kafkaTemplate.send(harvestTopic, "run-3", capture(eventSlot)) } returns mockk(relaxed = true)

        producer.produceResourceFailedEvent(
            harvestRunId = "run-3",
            resourceType = ResourceType.DATA_SERVICE,
            fdkId = "fdk-6",
            resourceUri = "https://example.com/6",
            startTime = 1000L,
            endTime = 2000L,
            errorMessage = "Storage failed",
        )

        verify(exactly = 1) { kafkaTemplate.send(harvestTopic, "run-3", any<HarvestEvent>()) }
        val event = eventSlot.captured
        assertEquals("run-3", event.runId)
        assertEquals("Storage failed", event.errorMessage)
        assertEquals(
            no.fdk.harvest.DataType
                .valueOf("dataservice"),
            event.dataType,
        )
    }

    @Test
    fun `produceResourceFinishedEvent catches exception and does not rethrow`() {
        every { kafkaTemplate.send(any(), any(), any<HarvestEvent>()) } throws RuntimeException("Kafka down")

        producer.produceResourceFinishedEvent(
            harvestRunId = "run-4",
            resourceType = ResourceType.CONCEPT,
            fdkId = "fdk-7",
            resourceUri = null,
            startTime = 1000L,
            endTime = 2000L,
        )

        verify(exactly = 1) { kafkaTemplate.send(any(), any(), any<HarvestEvent>()) }
        // No exception propagates - producer swallows to avoid breaking resource processing
    }

    @Test
    fun `all resource types map to correct DataType`() {
        val runId = "run-id"
        listOf(
            ResourceType.CONCEPT to "concept",
            ResourceType.DATASET to "dataset",
            ResourceType.INFORMATION_MODEL to "informationmodel",
            ResourceType.DATA_SERVICE to "dataservice",
            ResourceType.SERVICE to "publicService",
            ResourceType.EVENT to "event",
        ).forEach { (resourceType, expectedDataTypeName) ->
            val eventSlot = slot<HarvestEvent>()
            every { kafkaTemplate.send(harvestTopic, runId, capture(eventSlot)) } returns mockk(relaxed = true)
            producer.produceResourceFinishedEvent(
                harvestRunId = runId,
                resourceType = resourceType,
                fdkId = "id",
                resourceUri = null,
                startTime = 0L,
                endTime = 1L,
            )
            assertEquals(
                no.fdk.harvest.DataType
                    .valueOf(expectedDataTypeName),
                eventSlot.captured.dataType,
                "ResourceType $resourceType should map to DataType $expectedDataTypeName",
            )
        }
    }
}
