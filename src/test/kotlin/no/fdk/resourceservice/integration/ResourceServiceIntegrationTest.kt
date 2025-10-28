package no.fdk.resourceservice.integration

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.ResourceService
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.Assertions.*
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.core.type.TypeReference

class ResourceServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var resourceService: ResourceService

    @Autowired
    private lateinit var rdfService: RdfService

    @Test
    fun `getResourceJson should return null when not found`() {
        // When
        val result = resourceService.getResourceJson("non-existent-resource", ResourceType.CONCEPT)

        // Then
        assertNull(result)
    }

    @Test
    fun `getResourceJsonByUri should return null when not found by URI`() {
        // When
        val result = resourceService.getResourceJsonByUri("https://example.com/non-existent", ResourceType.CONCEPT)

        // Then
        assertNull(result)
    }

    @Test
    fun `storeResourceJson should save and retrieve resource`() {
        // Given
        val resourceId = "test-resource-1"
        val resourceData = mapOf(
            "uri" to "https://example.com/test-resource",
            "title" to "Test Resource",
            "description" to "A test resource"
        )
        val timestamp = System.currentTimeMillis()

        // When
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData, timestamp)
        val retrieved = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)

        // Then
        assertNotNull(retrieved)
        assert(retrieved!!["uri"] == "https://example.com/test-resource")
        assert(retrieved["title"] == "Test Resource")
    }

    @Test
    fun `getResourceJsonByUri should find resource by URI`() {
        // Given
        val resourceId = "test-resource-2"
        val resourceData = mapOf(
            "uri" to "https://example.com/test-resource-2",
            "title" to "Test Resource 2"
        )
        val timestamp = System.currentTimeMillis()

        // When
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData, timestamp)
        val retrieved = resourceService.getResourceJsonByUri("https://example.com/test-resource-2", ResourceType.CONCEPT)

        // Then
        assertNotNull(retrieved)
        assert(retrieved!!["uri"] == "https://example.com/test-resource-2")
        assert(retrieved["title"] == "Test Resource 2")
    }

    @Test
    fun `getResourceJsonLdByUri should return complete graph from @graph format`() {
        // Given
        val resourceId = "test-dataset-3"
        val datasetUri = "https://example.com/dataset-3"
        // First store resourceJson with URI (simulating RdfParseEvent)
        val resourceJson = mapOf(
            "uri" to datasetUri,
            "title" to "Test Dataset 3"
        )
        // Use Turtle format and convert to JSON-LD to ensure proper formatting
        val turtleData = """
            @prefix dcat: <http://www.w3.org/ns/dcat#> .
            @prefix dct: <http://purl.org/dc/terms/> .
            @prefix foaf: <http://xmlns.com/foaf/0.1/> .
            
            <$datasetUri> a dcat:Dataset ;
                dct:title "Test Dataset 3" ;
                dct:publisher <https://example.com/publisher> .
            
            <https://example.com/publisher> a foaf:Organization ;
                foaf:name "Test Publisher" .
        """.trimIndent()
        
        // Convert Turtle to JSON-LD Map
        val jsonLd = rdfService.convertTurtleToJsonLdMap(turtleData, expandUris = true)
        assertNotNull(jsonLd)
        assertTrue(jsonLd.isNotEmpty(), "JSON-LD conversion should not be empty")
        
        val timestamp = System.currentTimeMillis()
        val timestamp2 = timestamp + 1

        // When - store resourceJson first to set URI, then store JSON-LD
        resourceService.storeResourceJson(resourceId, ResourceType.DATASET, resourceJson, timestamp)
        resourceService.storeResourceJsonLd(resourceId, ResourceType.DATASET, jsonLd, timestamp2)
        val retrieved = resourceService.getResourceJsonLdByUri(datasetUri, ResourceType.DATASET)

        // Then
        assertNotNull(retrieved)
        // Should return the complete @graph format (or single node format depending on conversion)
        // JSON-LD conversion from Turtle with multiple nodes may result in @graph or array format
        assertTrue(
            retrieved!!.containsKey("@graph") || 
            retrieved.containsKey("@id") || 
            (retrieved.values.any { it is List<*> }),
            "Retrieved JSON-LD should have @graph, @id, or be an array format"
        )
        
        // Verify the dataset URI is present in the graph
        // After normalization in ResourceService, @graph should be a proper List
        val hasDatasetUri = when {
            retrieved.containsKey("@graph") -> {
                val graph = retrieved["@graph"] as? List<*>
                graph?.any { node ->
                    node is Map<*, *> && (node as Map<*, *>)["@id"] == datasetUri
                } ?: false
            }
            retrieved.containsKey("@id") -> {
                retrieved["@id"] == datasetUri
            }
            else -> {
                // Check if it's an array format
                retrieved.values.any { value ->
                    value is Map<*, *> && (value as Map<*, *>)["@id"] == datasetUri
                }
            }
        }
        assertTrue(hasDatasetUri, "Retrieved JSON-LD should contain the dataset URI")
        
        // Also verify the structure is correct - after normalization, @graph should be a List
        if (retrieved.containsKey("@graph")) {
            val graph = retrieved["@graph"] as? List<*>
            assertNotNull(graph, "@graph should not be null and should be a List")
            assertTrue(graph!!.isNotEmpty(), "@graph should not be empty")
            assertTrue(
                graph.all { it is Map<*, *> },
                "@graph should contain Map objects"
            )
        }
    }

    @Test
    fun `getResourceJsonLdByUri should return null when URI not found in @graph`() {
        // Given
        val resourceId = "test-dataset-4"
        val datasetUri = "https://example.com/dataset-4"
        // First store resourceJson with URI (simulating RdfParseEvent)
        val resourceJson = mapOf(
            "uri" to datasetUri,
            "title" to "Test Dataset 4"
        )
        val jsonLd = mapOf(
            "@graph" to listOf(
                mapOf(
                    "@id" to datasetUri,
                    "@type" to listOf("http://www.w3.org/ns/dcat#Dataset")
                )
            )
        )
        val timestamp = System.currentTimeMillis()
        val timestamp2 = timestamp + 1

        // When - store resourceJson first to set URI, then store JSON-LD, then query for non-existent URI
        resourceService.storeResourceJson(resourceId, ResourceType.DATASET, resourceJson, timestamp)
        resourceService.storeResourceJsonLd(resourceId, ResourceType.DATASET, jsonLd, timestamp2)
        val retrieved = resourceService.getResourceJsonLdByUri("https://example.com/non-existent", ResourceType.DATASET)

        // Then
        assertNull(retrieved)
    }

    @Test
    fun `getResourceJsonLdByUri should work across resource types`() {
        // Given
        val datasetId = "test-dataset-cross-1"
        val datasetUri = "https://example.com/dataset-cross-1"
        // First store resourceJson with URI (simulating RdfParseEvent)
        val resourceJson = mapOf(
            "uri" to datasetUri,
            "title" to "Test Dataset Cross 1"
        )
        val jsonLd = mapOf(
            "@id" to datasetUri,
            "@type" to listOf("http://www.w3.org/ns/dcat#Dataset")
        )
        val timestamp = System.currentTimeMillis()
        val timestamp2 = timestamp + 1

        // When - store resourceJson first to set URI, then store JSON-LD
        resourceService.storeResourceJson(datasetId, ResourceType.DATASET, resourceJson, timestamp)
        resourceService.storeResourceJsonLd(datasetId, ResourceType.DATASET, jsonLd, timestamp2)
        val retrieved = resourceService.getResourceJsonLdByUri(datasetUri)

        // Then
        assertNotNull(retrieved)
        // Should return the complete stored JSON-LD (single node format in this case)
        assertEquals(datasetUri, retrieved!!["@id"])
        assertTrue(retrieved.containsKey("@type"))
    }
}
