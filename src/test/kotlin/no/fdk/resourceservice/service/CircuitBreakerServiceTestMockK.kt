package no.fdk.resourceservice.service

import io.mockk.*
import no.fdk.concept.ConceptEvent
import no.fdk.concept.ConceptEventType
import no.fdk.dataset.DatasetEvent
import no.fdk.dataset.DatasetEventType
import no.fdk.resourceservice.model.ResourceType
import org.junit.jupiter.api.BeforeEach
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test

class CircuitBreakerServiceTestMockK {

    private val resourceService = mockk<ResourceService>()
    private val rdfService = mockk<RdfService>()
    private lateinit var circuitBreakerService: CircuitBreakerService

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        circuitBreakerService = CircuitBreakerService(resourceService, rdfService)
        
        // Mock ResourceService methods
        every { resourceService.storeResourceJsonLd(any(), any(), any(), any()) } just Runs
        every { resourceService.storeResourceJson(any(), any(), any(), any()) } just Runs
        every { resourceService.markResourceAsDeleted(any(), any(), any()) } just Runs
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
    fun `should handle RdfParseEvent successfully`() {
        // Given
        val event = no.fdk.rdf.parse.RdfParseEvent().apply {
            setFdkId("test-id")
            setResourceType(no.fdk.rdf.parse.RdfParseResourceType.CONCEPT)
            setData("test-graph")
            setTimestamp(System.currentTimeMillis())
        }

        // When
        circuitBreakerService.handleRdfParseEvent(event)

        // Then
        verify { resourceService.storeResourceJson("test-id", ResourceType.CONCEPT, any(), any()) }
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
