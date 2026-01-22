package no.fdk.resourceservice.integration

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import no.fdk.resourceservice.service.ResourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

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
        val resourceData =
            mapOf(
                "uri" to "https://example.com/test-resource",
                "title" to "Test Resource",
                "description" to "A test resource",
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
        val resourceData =
            mapOf(
                "uri" to "https://example.com/test-resource-2",
                "title" to "Test Resource 2",
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
    fun `getResourceJsonListById should return list of resources when all were found`() {
        // Given
        val resource1Id = "test-1"
        val resource1Data =
            mapOf(
                "uri" to "https://example.com/1",
                "title" to "Resource 1",
            )
        val resource2Id = "test-2"
        val resource2Data =
            mapOf(
                "uri" to "https://example.com/2",
                "title" to "Resource 2",
            )
        val timestamp = System.currentTimeMillis()

        // When
        resourceService.storeResourceJson(resource1Id, ResourceType.CONCEPT, resource1Data, timestamp)
        resourceService.storeResourceJson(resource2Id, ResourceType.CONCEPT, resource2Data, timestamp)
        val results = resourceService.getResourceJsonListById(listOf(resource1Id, resource2Id), ResourceType.CONCEPT)

        // Then
        assertEquals(2, results.size)
        assertTrue(results.any { it["uri"] == "https://example.com/1" })
        assertTrue(results.any { it["uri"] == "https://example.com/2" })
    }

    @Test
    fun `getResourceJsonListById should return empty list when no resources found`() {
        // When
        val results = resourceService.getResourceJsonListById(listOf("non-existent-1", "non-existent-2"), ResourceType.CONCEPT)

        // Then
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getResourceJsonListById should return partial results when some resources found`() {
        // Given
        val existingId = "test-existing"
        val existingData =
            mapOf(
                "uri" to "https://example.com/existing",
                "title" to "Existing Resource",
            )
        val timestamp = System.currentTimeMillis()

        // When
        resourceService.storeResourceJson(existingId, ResourceType.CONCEPT, existingData, timestamp)
        val results = resourceService.getResourceJsonListById(listOf(existingId, "non-existent"), ResourceType.CONCEPT)

        // Then
        assertEquals(1, results.size)
        assertEquals("https://example.com/existing", results[0]["uri"])
    }

    @Test
    fun `getResourceJsonListById should return empty list when ids list is empty`() {
        // When
        val results = resourceService.getResourceJsonListById(emptyList(), ResourceType.CONCEPT)

        // Then
        assertTrue(results.isEmpty())
    }

    @Test
    fun `getResourceJsonListById should filter by resource type`() {
        // Given
        val conceptId = "test-concept"
        val conceptData =
            mapOf(
                "uri" to "https://example.com/concept",
                "title" to "Concept Resource",
            )
        val datasetId = "test-dataset"
        val datasetData =
            mapOf(
                "uri" to "https://example.com/dataset",
                "title" to "Dataset Resource",
            )
        val timestamp = System.currentTimeMillis()

        // When
        resourceService.storeResourceJson(conceptId, ResourceType.CONCEPT, conceptData, timestamp)
        val conceptResults = resourceService.getResourceJsonListById(listOf(conceptId, datasetId), ResourceType.CONCEPT)

        resourceService.storeResourceJson(datasetId, ResourceType.DATASET, datasetData, timestamp)
        val datasetResults = resourceService.getResourceJsonListById(listOf(conceptId, datasetId), ResourceType.DATASET)

        // Then
        assertEquals(1, conceptResults.size)
        assertEquals("https://example.com/concept", conceptResults[0]["uri"])

        assertEquals(1, datasetResults.size)
        assertEquals("https://example.com/dataset", datasetResults[0]["uri"])
    }

    @Test
    fun `getResourceJsonListById should handle duplicate ids`() {
        // Given
        val resourceId = "test-duplicate"
        val resourceData =
            mapOf(
                "uri" to "https://example.com/duplicate",
                "title" to "Duplicate Resource",
            )
        val timestamp = System.currentTimeMillis()

        // When
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData, timestamp)
        val results = resourceService.getResourceJsonListById(listOf(resourceId, resourceId), ResourceType.CONCEPT)

        // Then - should not return duplicates
        assertEquals(1, results.size)
        assertEquals("https://example.com/duplicate", results[0]["uri"])
    }

    @Test
    fun `getResourceGraphDataByUri should return complete graph data`() {
        // Given
        val resourceId = "test-dataset-3"
        val datasetUri = "https://example.com/dataset-3"
        // First store resourceJson with URI (simulating RdfParseEvent)
        val resourceJson =
            mapOf(
                "uri" to datasetUri,
                "title" to "Test Dataset 3",
            )
        // Use Turtle format
        val turtleData =
            """
            @prefix dcat: <http://www.w3.org/ns/dcat#> .
            @prefix dct: <http://purl.org/dc/terms/> .
            @prefix foaf: <http://xmlns.com/foaf/0.1/> .
            
            <$datasetUri> a dcat:Dataset ;
                dct:title "Test Dataset 3" ;
                dct:publisher <https://example.com/publisher> .
            
            <https://example.com/publisher> a foaf:Organization ;
                foaf:name "Test Publisher" .
            """.trimIndent()

        val timestamp = System.currentTimeMillis()
        val timestamp2 = timestamp + 1

        // When - store resourceJson first to set URI, then store graph data
        resourceService.storeResourceJson(resourceId, ResourceType.DATASET, resourceJson, timestamp)
        resourceService.storeResourceGraphData(resourceId, ResourceType.DATASET, turtleData, "TURTLE", timestamp2)
        val retrieved = resourceService.getResourceEntityByUri(datasetUri)

        // Then
        assertNotNull(retrieved)
        assertNotNull(retrieved!!.resourceGraphData)
        assertTrue(retrieved.resourceGraphData!!.contains(datasetUri), "Retrieved graph data should contain the dataset URI")
        assertTrue(retrieved.resourceGraphData.contains("Test Dataset 3"), "Retrieved graph data should contain the title")
        assertEquals("TURTLE", retrieved.resourceGraphFormat, "Graph format should be TURTLE")
    }

    @Test
    fun `getResourceGraphDataByUri should return null when URI not found`() {
        // Given
        val resourceId = "test-dataset-4"
        val datasetUri = "https://example.com/dataset-4"
        // First store resourceJson with URI (simulating RdfParseEvent)
        val resourceJson =
            mapOf(
                "uri" to datasetUri,
                "title" to "Test Dataset 4",
            )
        val turtleData = """<$datasetUri> a <http://www.w3.org/ns/dcat#Dataset> ."""
        val timestamp = System.currentTimeMillis()
        val timestamp2 = timestamp + 1

        // When - store resourceJson first to set URI, then store graph data, then query for non-existent URI
        resourceService.storeResourceJson(resourceId, ResourceType.DATASET, resourceJson, timestamp)
        resourceService.storeResourceGraphData(resourceId, ResourceType.DATASET, turtleData, "TURTLE", timestamp2)
        val retrieved = resourceService.getResourceEntityByUri("https://example.com/non-existent")

        // Then
        assertNull(retrieved)
    }

    @Test
    fun `getResourceGraphDataByUri should work across resource types`() {
        // Given
        val datasetId = "test-dataset-cross-1"
        val datasetUri = "https://example.com/dataset-cross-1"
        // First store resourceJson with URI (simulating RdfParseEvent)
        val resourceJson =
            mapOf(
                "uri" to datasetUri,
                "title" to "Test Dataset Cross 1",
            )
        val turtleData = """<$datasetUri> a <http://www.w3.org/ns/dcat#Dataset> ."""
        val timestamp = System.currentTimeMillis()
        val timestamp2 = timestamp + 1

        // When - store resourceJson first to set URI, then store graph data
        resourceService.storeResourceJson(datasetId, ResourceType.DATASET, resourceJson, timestamp)
        resourceService.storeResourceGraphData(datasetId, ResourceType.DATASET, turtleData, "TURTLE", timestamp2)
        val retrieved = resourceService.getResourceEntityByUri(datasetUri)

        // Then
        assertNotNull(retrieved)
        assertNotNull(retrieved!!.resourceGraphData)
        assertTrue(retrieved.resourceGraphData!!.contains(datasetUri), "Retrieved graph data should contain the dataset URI")
        assertEquals("TURTLE", retrieved.resourceGraphFormat, "Graph format should be TURTLE")
    }
}
