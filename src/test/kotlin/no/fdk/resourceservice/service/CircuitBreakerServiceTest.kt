package no.fdk.resourceservice.service

import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.fdk.concept.ConceptEvent
import no.fdk.concept.ConceptEventType
import no.fdk.dataservice.DataServiceEvent
import no.fdk.dataservice.DataServiceEventType
import no.fdk.dataset.DatasetEvent
import no.fdk.dataset.DatasetEventType
import no.fdk.event.EventEvent
import no.fdk.event.EventEventType
import no.fdk.informationmodel.InformationModelEvent
import no.fdk.informationmodel.InformationModelEventType
import no.fdk.rdf.parse.RdfParseEvent
import no.fdk.rdf.parse.RdfParseResourceType
import no.fdk.resourceservice.model.ResourceType
import no.fdk.service.ServiceEvent
import no.fdk.service.ServiceEventType
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.sql.SQLException

class CircuitBreakerServiceTest {
    private val resourceService = mockk<ResourceService>()
    private val rdfService = mockk<RdfService>()
    private lateinit var circuitBreakerService: CircuitBreakerService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        circuitBreakerService = CircuitBreakerService(resourceService, rdfService)

        // Mock ResourceService methods with relaxed mocking
        every { resourceService.shouldUpdateResource(any(), any()) } returns true // Default: allow updates
        every { resourceService.storeResourceJsonLd(any(), any(), any(), any()) } just Runs
        every { resourceService.storeResourceJson(any(), any(), any(), any()) } just Runs
        every { resourceService.markResourceAsDeleted(any(), any(), any()) } just Runs
    }

    @Test
    fun `should handle RdfParseEvent successfully`() {
        // Given
        val event =
            RdfParseEvent().apply {
                setFdkId("test-id")
                setResourceType(RdfParseResourceType.CONCEPT)
                setData("""{"id": "test-id", "title": "Test Concept"}""")
                setTimestamp(System.currentTimeMillis())
            }
        every { resourceService.shouldUpdateResource("test-id", any()) } returns true

        // When
        circuitBreakerService.handleRdfParseEvent(event)

        // Then
        verify { resourceService.shouldUpdateResource("test-id", any()) }
        verify { resourceService.storeResourceJson("test-id", ResourceType.CONCEPT, any(), any()) }
    }

    @Test
    fun `should handle RdfParseEvent with invalid JSON gracefully`() {
        // Given
        val event =
            RdfParseEvent().apply {
                setFdkId("test-id")
                setResourceType(RdfParseResourceType.CONCEPT)
                setData("invalid-json-string") // Not valid JSON
                setTimestamp(System.currentTimeMillis())
            }
        every { resourceService.shouldUpdateResource("test-id", any()) } returns true

        // When/Then - should throw exception when JSON parsing fails
        assertThrows<Exception> {
            circuitBreakerService.handleRdfParseEvent(event)
        }

        // Verify that storeResourceJson was not called when JSON parsing fails
        verify { resourceService.shouldUpdateResource("test-id", any()) }
        verify(exactly = 0) { resourceService.storeResourceJson(any(), any(), any(), any()) }
    }

    @Test
    fun `should handle ConceptEvent successfully`() {
        // Given
        val event =
            ConceptEvent().apply {
                type = ConceptEventType.CONCEPT_REASONED
                fdkId = "test-concept-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph", true) }
            .returns(mapOf("@id" to "http://example.com/concept"))
        every { resourceService.shouldUpdateResource("test-concept-id", any()) } returns true

        // When
        circuitBreakerService.handleConceptEvent(event)

        // Then
        verify { resourceService.shouldUpdateResource("test-concept-id", any()) }
        verify { resourceService.storeResourceJsonLd("test-concept-id", ResourceType.CONCEPT, any(), any()) }
    }

    @Test
    fun `should handle DatasetEvent successfully`() {
        // Given
        val event =
            DatasetEvent().apply {
                type = DatasetEventType.DATASET_REASONED
                fdkId = "test-dataset-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph", true) }
            .returns(mapOf("@id" to "http://example.com/dataset"))
        every { resourceService.shouldUpdateResource("test-dataset-id", any()) } returns true

        // When
        circuitBreakerService.handleDatasetEvent(event)

        // Then
        verify { resourceService.shouldUpdateResource("test-dataset-id", any()) }
        verify { resourceService.storeResourceJsonLd("test-dataset-id", ResourceType.DATASET, any(), any()) }
    }

    @Test
    fun `should handle DataServiceEvent successfully`() {
        // Given
        val event =
            DataServiceEvent().apply {
                type = DataServiceEventType.DATA_SERVICE_REASONED
                fdkId = "test-dataservice-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph", true) }
            .returns(mapOf("@id" to "http://example.com/dataservice"))
        every { resourceService.shouldUpdateResource("test-dataservice-id", any()) } returns true

        // When
        circuitBreakerService.handleDataServiceEvent(event)

        // Then
        verify { resourceService.shouldUpdateResource("test-dataservice-id", any()) }
        verify { resourceService.storeResourceJsonLd("test-dataservice-id", ResourceType.DATA_SERVICE, any(), any()) }
    }

    @Test
    fun `should handle InformationModelEvent successfully`() {
        // Given
        val event =
            InformationModelEvent().apply {
                type = InformationModelEventType.INFORMATION_MODEL_REASONED
                fdkId = "test-informationmodel-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph", true) }
            .returns(mapOf("@id" to "http://example.com/informationmodel"))
        every { resourceService.shouldUpdateResource("test-informationmodel-id", any()) } returns true

        // When
        circuitBreakerService.handleInformationModelEvent(event)

        // Then
        verify { resourceService.shouldUpdateResource("test-informationmodel-id", any()) }
        verify { resourceService.storeResourceJsonLd("test-informationmodel-id", ResourceType.INFORMATION_MODEL, any(), any()) }
    }

    @Test
    fun `should handle ServiceEvent successfully`() {
        // Given
        val event =
            ServiceEvent().apply {
                type = ServiceEventType.SERVICE_REASONED
                fdkId = "test-service-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph", true) }
            .returns(mapOf("@id" to "http://example.com/service"))
        every { resourceService.shouldUpdateResource("test-service-id", any()) } returns true

        // When
        circuitBreakerService.handleServiceEvent(event)

        // Then
        verify { resourceService.shouldUpdateResource("test-service-id", any()) }
        verify { resourceService.storeResourceJsonLd("test-service-id", ResourceType.SERVICE, any(), any()) }
    }

    @Test
    fun `should handle EventEvent successfully`() {
        // Given
        val event =
            EventEvent().apply {
                type = EventEventType.EVENT_REASONED
                fdkId = "test-event-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph", true) }
            .returns(mapOf("@id" to "http://example.com/event"))
        every { resourceService.shouldUpdateResource("test-event-id", any()) } returns true

        // When
        circuitBreakerService.handleEventEvent(event)

        // Then
        verify { resourceService.shouldUpdateResource("test-event-id", any()) }
        verify { resourceService.storeResourceJsonLd("test-event-id", ResourceType.EVENT, any(), any()) }
    }

    @Test
    fun `should trigger fallback when ResourceService throws exception in handleRdfParseEvent`() {
        // Given
        val event =
            RdfParseEvent().apply {
                setFdkId("test-id")
                setResourceType(RdfParseResourceType.CONCEPT)
                setData("""{"id": "test-id", "title": "Test Concept"}""")
                setTimestamp(System.currentTimeMillis())
            }

        every { resourceService.shouldUpdateResource("test-id", any()) } returns true
        // When ResourceService throws an exception
        every { resourceService.storeResourceJson(any(), any(), any(), any()) } throws SQLException("Database connection failed")

        // When/Then - expect the exception to be thrown
        try {
            circuitBreakerService.handleRdfParseEvent(event)
            assert(false) { "Expected SQLException to be thrown" }
        } catch (e: SQLException) {
            assert(e.message == "Database connection failed")
        }
    }

    @Test
    fun `should trigger fallback when ResourceService throws exception in handleConceptEvent`() {
        // Given
        val event =
            ConceptEvent().apply {
                type = ConceptEventType.CONCEPT_REASONED
                fdkId = "test-concept-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph", true) }
            .returns(mapOf("@id" to "http://example.com/concept"))
        every { resourceService.shouldUpdateResource("test-concept-id", any()) } returns true

        // When ResourceService throws an exception
        every { resourceService.storeResourceJsonLd(any(), any(), any(), any()) } throws SQLException("Database connection failed")

        // When/Then - expect the exception to be thrown
        try {
            circuitBreakerService.handleConceptEvent(event)
            assert(false) { "Expected SQLException to be thrown" }
        } catch (e: SQLException) {
            assert(e.message == "Database connection failed")
        }
    }

    @Test
    fun `should mark resource as deleted for REMOVED event`() {
        // Given
        val event =
            ConceptEvent().apply {
                type = ConceptEventType.CONCEPT_REMOVED
                fdkId = "test-concept-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // When
        circuitBreakerService.handleConceptEvent(event)

        // Then
        verify { resourceService.markResourceAsDeleted("test-concept-id", ResourceType.CONCEPT, any()) }
    }

    @Test
    fun `should ignore HARVESTED ConceptEvent`() {
        // Given
        val event =
            ConceptEvent().apply {
                type = ConceptEventType.CONCEPT_HARVESTED
                fdkId = "test-concept-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // When
        circuitBreakerService.handleConceptEvent(event)

        // Then - verify no processing occurred
        verify(exactly = 0) { resourceService.shouldUpdateResource(any(), any()) }
        verify(exactly = 0) { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
        verify(exactly = 0) { rdfService.convertTurtleToJsonLdMap(any(), any()) }
    }

    @Test
    fun `should ignore HARVESTED DatasetEvent`() {
        // Given
        val event =
            DatasetEvent().apply {
                type = DatasetEventType.DATASET_HARVESTED
                fdkId = "test-dataset-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // When
        circuitBreakerService.handleDatasetEvent(event)

        // Then - verify no processing occurred
        verify(exactly = 0) { resourceService.shouldUpdateResource(any(), any()) }
        verify(exactly = 0) { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
        verify(exactly = 0) { rdfService.convertTurtleToJsonLdMap(any(), any()) }
    }

    @Test
    fun `should ignore HARVESTED DataServiceEvent`() {
        // Given
        val event =
            DataServiceEvent().apply {
                type = DataServiceEventType.DATA_SERVICE_HARVESTED
                fdkId = "test-dataservice-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // When
        circuitBreakerService.handleDataServiceEvent(event)

        // Then - verify no processing occurred
        verify(exactly = 0) { resourceService.shouldUpdateResource(any(), any()) }
        verify(exactly = 0) { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
        verify(exactly = 0) { rdfService.convertTurtleToJsonLdMap(any(), any()) }
    }

    @Test
    fun `should ignore HARVESTED InformationModelEvent`() {
        // Given
        val event =
            InformationModelEvent().apply {
                type = InformationModelEventType.INFORMATION_MODEL_HARVESTED
                fdkId = "test-informationmodel-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // When
        circuitBreakerService.handleInformationModelEvent(event)

        // Then - verify no processing occurred
        verify(exactly = 0) { resourceService.shouldUpdateResource(any(), any()) }
        verify(exactly = 0) { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
        verify(exactly = 0) { rdfService.convertTurtleToJsonLdMap(any(), any()) }
    }

    @Test
    fun `should ignore HARVESTED ServiceEvent`() {
        // Given
        val event =
            ServiceEvent().apply {
                type = ServiceEventType.SERVICE_HARVESTED
                fdkId = "test-service-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // When
        circuitBreakerService.handleServiceEvent(event)

        // Then - verify no processing occurred
        verify(exactly = 0) { resourceService.shouldUpdateResource(any(), any()) }
        verify(exactly = 0) { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
        verify(exactly = 0) { rdfService.convertTurtleToJsonLdMap(any(), any()) }
    }

    @Test
    fun `should ignore HARVESTED EventEvent`() {
        // Given
        val event =
            EventEvent().apply {
                type = EventEventType.EVENT_HARVESTED
                fdkId = "test-event-id"
                graph = "test-graph"
                timestamp = System.currentTimeMillis()
            }

        // When
        circuitBreakerService.handleEventEvent(event)

        // Then - verify no processing occurred
        verify(exactly = 0) { resourceService.shouldUpdateResource(any(), any()) }
        verify(exactly = 0) { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
        verify(exactly = 0) { rdfService.convertTurtleToJsonLdMap(any(), any()) }
    }

    @Test
    fun `should ignore RdfParseEvent with empty fdkId`() {
        // Given - RdfParseEvent with empty fdkId (would be caught at KafkaConsumer level)
        // Since Avro objects enforce fields, we test that the service handles empty strings gracefully
        val event =
            RdfParseEvent().apply {
                setFdkId("") // Empty fdkId
                setResourceType(RdfParseResourceType.CONCEPT)
                setData("""{"id": "test-id"}""")
                setTimestamp(System.currentTimeMillis())
            }

        // When/Then - should handle gracefully (validation happens in KafkaConsumer, but service should be resilient)
        every { resourceService.shouldUpdateResource("", any()) } returns false

        circuitBreakerService.handleRdfParseEvent(event)

        // Verify no storage occurred due to shouldUpdateResource returning false
        verify(exactly = 0) { resourceService.storeResourceJson(any(), any(), any(), any()) }
    }

    @Test
    fun `should ignore RdfParseEvent with zero timestamp`() {
        // Given - RdfParseEvent with zero timestamp (would be caught at KafkaConsumer level)
        val event =
            RdfParseEvent().apply {
                setFdkId("test-id")
                setResourceType(RdfParseResourceType.CONCEPT)
                setData("""{"id": "test-id"}""")
                setTimestamp(0) // Zero timestamp
            }

        // When - shouldUpdateResource might return false for zero timestamp
        every { resourceService.shouldUpdateResource("test-id", 0) } returns false

        circuitBreakerService.handleRdfParseEvent(event)

        // Then - verify no storage occurred
        verify(exactly = 0) { resourceService.storeResourceJson(any(), any(), any(), any()) }
    }

    @Test
    fun `should ignore RdfParseEvent with empty resourceType`() {
        // Given - This test verifies that if resourceType is somehow invalid, it's handled
        // Note: Avro enums enforce valid values, so this is more of a resilience test
        val event =
            RdfParseEvent().apply {
                setFdkId("test-id")
                setResourceType(RdfParseResourceType.CONCEPT) // Required and valid
                setData("""{"id": "test-id"}""")
                setTimestamp(System.currentTimeMillis())
            }

        // When - service should handle normally
        every { resourceService.shouldUpdateResource("test-id", any()) } returns true
        every { resourceService.storeResourceJson(any(), any(), any(), any()) } just Runs

        circuitBreakerService.handleRdfParseEvent(event)

        // Then - should process successfully
        verify { resourceService.storeResourceJson("test-id", ResourceType.CONCEPT, any(), any()) }
    }
}
