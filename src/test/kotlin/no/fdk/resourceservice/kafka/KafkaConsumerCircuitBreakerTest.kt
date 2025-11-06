package no.fdk.resourceservice.kafka

import no.fdk.concept.ConceptEvent
import no.fdk.dataservice.DataServiceEvent
import no.fdk.dataset.DatasetEvent
import no.fdk.event.EventEvent
import no.fdk.informationmodel.InformationModelEvent
import no.fdk.rdf.parse.RdfParseEvent
import no.fdk.rdf.parse.RdfParseResourceType
import no.fdk.resourceservice.service.CircuitBreakerService
import no.fdk.service.ServiceEvent
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.junit.jupiter.MockitoExtension
import org.springframework.kafka.support.Acknowledgment

@ExtendWith(MockitoExtension::class)
class KafkaConsumerCircuitBreakerTest {
    @Mock
    private lateinit var circuitBreakerService: CircuitBreakerService

    @Mock
    private lateinit var acknowledgment: Acknowledgment

    private lateinit var kafkaConsumer: KafkaConsumer

    @BeforeEach
    fun setUp() {
        kafkaConsumer = KafkaConsumer(circuitBreakerService)
    }

    @Test
    fun `should delegate to CircuitBreakerService for handleRdfParseEvent`() {
        // Given
        val event =
            RdfParseEvent().apply {
                fdkId = "test-id"
                resourceType = RdfParseResourceType.CONCEPT
                data = "test-graph"
                timestamp = System.currentTimeMillis()
            }
        val record = ConsumerRecord<String, Any>("test-topic", 0, 0L, "test-key", event)

        // When
        kafkaConsumer.handleRdfParseEvent(record, acknowledgment, "test-topic", 0, 0L)

        // Then
        verify(circuitBreakerService, times(1)).handleRdfParseEvent(event)
    }

    @Test
    fun `should delegate to CircuitBreakerService for handleConceptEvent`() {
        // Given
        val event =
            ConceptEvent().apply {
                fdkId = "test-concept-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }
        val record = ConsumerRecord<String, Any>("test-topic", 0, 0L, "test-key", event)

        // When
        kafkaConsumer.handleConceptEvent(record, acknowledgment, "test-topic", 0, 0L)

        // Then
        verify(circuitBreakerService, times(1)).handleConceptEvent(event)
    }

    @Test
    fun `should delegate to CircuitBreakerService for handleDatasetEvent`() {
        // Given
        val event =
            DatasetEvent().apply {
                fdkId = "test-dataset-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }
        val record = ConsumerRecord<String, Any>("test-topic", 0, 0L, "test-key", event)

        // When
        kafkaConsumer.handleDatasetEvent(record, acknowledgment, "test-topic", 0, 0L)

        // Then
        verify(circuitBreakerService, times(1)).handleDatasetEvent(event)
    }

    @Test
    fun `should delegate to CircuitBreakerService for handleDataServiceEvent`() {
        // Given
        val event =
            DataServiceEvent().apply {
                fdkId = "test-dataservice-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }
        val record = ConsumerRecord<String, Any>("test-topic", 0, 0L, "test-key", event)

        // When
        kafkaConsumer.handleDataServiceEvent(record, acknowledgment, "test-topic", 0, 0L)

        // Then
        verify(circuitBreakerService, times(1)).handleDataServiceEvent(event)
    }

    @Test
    fun `should delegate to CircuitBreakerService for handleInformationModelEvent`() {
        // Given
        val event =
            InformationModelEvent().apply {
                fdkId = "test-informationmodel-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }
        val record = ConsumerRecord<String, Any>("test-topic", 0, 0L, "test-key", event)

        // When
        kafkaConsumer.handleInformationModelEvent(record, acknowledgment, "test-topic", 0, 0L)

        // Then
        verify(circuitBreakerService, times(1)).handleInformationModelEvent(event)
    }

    @Test
    fun `should delegate to CircuitBreakerService for handleServiceEvent`() {
        // Given
        val event =
            ServiceEvent().apply {
                fdkId = "test-service-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }
        val record = ConsumerRecord<String, Any>("test-topic", 0, 0L, "test-key", event)

        // When
        kafkaConsumer.handleServiceEvent(record, acknowledgment, "test-topic", 0, 0L)

        // Then
        verify(circuitBreakerService, times(1)).handleServiceEvent(event)
    }

    @Test
    fun `should delegate to CircuitBreakerService for handleEventEvent`() {
        // Given
        val event =
            EventEvent().apply {
                fdkId = "test-event-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }
        val record = ConsumerRecord<String, Any>("test-topic", 0, 0L, "test-key", event)

        // When
        kafkaConsumer.handleEventEvent(record, acknowledgment, "test-topic", 0, 0L)

        // Then
        verify(circuitBreakerService, times(1)).handleEventEvent(event)
    }
}
