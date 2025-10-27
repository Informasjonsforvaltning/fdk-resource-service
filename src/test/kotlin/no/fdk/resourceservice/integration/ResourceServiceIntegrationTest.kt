package no.fdk.resourceservice.integration

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.ResourceService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertNotNull

class ResourceServiceIntegrationTest : BaseIntegrationTest() {

    @Autowired
    private lateinit var resourceService: ResourceService

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
}
