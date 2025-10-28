package no.fdk.resourceservice.service

import io.mockk.*
import no.fdk.concept.ConceptEvent
import no.fdk.concept.ConceptEventType
import no.fdk.dataset.DatasetEvent
import no.fdk.dataset.DatasetEventType
import no.fdk.dataservice.DataServiceEvent
import no.fdk.dataservice.DataServiceEventType
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
import no.fdk.resourceservice.service.RdfService
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
        every { resourceService.storeResourceJsonLd(any(), any(), any(), any()) } just Runs
        every { resourceService.storeResourceJson(any(), any(), any(), any()) } just Runs
        every { resourceService.markResourceAsDeleted(any(), any(), any()) } just Runs
    }

    @Test
    fun `should handle RdfParseEvent successfully`() {
        // Given
        val event = RdfParseEvent().apply {
            setFdkId("test-id")
            setResourceType(RdfParseResourceType.CONCEPT)
            setData("""{"id": "test-id", "title": "Test Concept"}""")
            setTimestamp(System.currentTimeMillis())
        }

        // When
        circuitBreakerService.handleRdfParseEvent(event)

        // Then
        verify { resourceService.storeResourceJson("test-id", ResourceType.CONCEPT, any(), any()) }
    }

    @Test
    fun `should handle ConceptEvent successfully`() {
        // Given
        val event = ConceptEvent().apply {
            type = ConceptEventType.CONCEPT_HARVESTED
            fdkId = "test-concept-id"
            graph = "test-graph"
            timestamp = System.currentTimeMillis()
        }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph") }
            .returns(mapOf("@id" to "http://example.com/concept"))

        // When
        circuitBreakerService.handleConceptEvent(event)

        // Then
        verify { resourceService.storeResourceJsonLd("test-concept-id", ResourceType.CONCEPT, any(), any()) }
    }

    @Test
    fun `should handle DatasetEvent successfully`() {
        // Given
        val event = DatasetEvent().apply {
            type = DatasetEventType.DATASET_HARVESTED
            fdkId = "test-dataset-id"
            graph = "test-graph"
            timestamp = System.currentTimeMillis()
        }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph") }
            .returns(mapOf("@id" to "http://example.com/dataset"))

        // When
        circuitBreakerService.handleDatasetEvent(event)

        // Then
        verify { resourceService.storeResourceJsonLd("test-dataset-id", ResourceType.DATASET, any(), any()) }
    }

    @Test
    fun `should handle DataServiceEvent successfully`() {
        // Given
        val event = DataServiceEvent().apply {
            type = DataServiceEventType.DATA_SERVICE_HARVESTED
            fdkId = "test-dataservice-id"
            graph = "test-graph"
            timestamp = System.currentTimeMillis()
        }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph") }
            .returns(mapOf("@id" to "http://example.com/dataservice"))

        // When
        circuitBreakerService.handleDataServiceEvent(event)

        // Then
        verify { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
    }

    @Test
    fun `should handle InformationModelEvent successfully`() {
        // Given
        val event = InformationModelEvent().apply {
            type = InformationModelEventType.INFORMATION_MODEL_HARVESTED
            fdkId = "test-informationmodel-id"
            graph = "test-graph"
            timestamp = System.currentTimeMillis()
        }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph") }
            .returns(mapOf("@id" to "http://example.com/informationmodel"))

        // When
        circuitBreakerService.handleInformationModelEvent(event)

        // Then
        verify { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
    }

    @Test
    fun `should handle ServiceEvent successfully`() {
        // Given
        val event = ServiceEvent().apply {
            type = ServiceEventType.SERVICE_HARVESTED
            fdkId = "test-service-id"
            graph = "test-graph"
            timestamp = System.currentTimeMillis()
        }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph") }
            .returns(mapOf("@id" to "http://example.com/service"))

        // When
        circuitBreakerService.handleServiceEvent(event)

        // Then
        verify { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
    }

    @Test
    fun `should handle EventEvent successfully`() {
        // Given
        val event = EventEvent().apply {
            type = EventEventType.EVENT_HARVESTED
            fdkId = "test-event-id"
            graph = "test-graph"
            timestamp = System.currentTimeMillis()
        }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph") }
            .returns(mapOf("@id" to "http://example.com/event"))

        // When
        circuitBreakerService.handleEventEvent(event)

        // Then
        verify { resourceService.storeResourceJsonLd(any(), any(), any(), any()) }
    }

    @Test
    fun `should trigger fallback when ResourceService throws exception in handleRdfParseEvent`() {
        // Given
        val event = RdfParseEvent().apply {
            setFdkId("test-id")
            setResourceType(RdfParseResourceType.CONCEPT)
            setData("""{"id": "test-id", "title": "Test Concept"}""")
            setTimestamp(System.currentTimeMillis())
        }

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
        val event = ConceptEvent().apply {
            type = ConceptEventType.CONCEPT_HARVESTED
            fdkId = "test-concept-id"
            graph = "test-graph"
            timestamp = System.currentTimeMillis()
        }

        // Mock the RDF processing service
        every { rdfService.convertTurtleToJsonLdMap("test-graph") }
            .returns(mapOf("@id" to "http://example.com/concept"))

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
        val event = ConceptEvent().apply {
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
}


