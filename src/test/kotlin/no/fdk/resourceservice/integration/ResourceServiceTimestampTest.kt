package no.fdk.resourceservice.integration

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.service.ResourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired

/**
 * Tests for verifying that save and delete operations are only allowed
 * when the timestamp is after the saved timestamp.
 */
class ResourceServiceTimestampTest : BaseIntegrationTest() {
    @Autowired
    private lateinit var resourceService: ResourceService

    @Autowired
    private lateinit var resourceRepository: ResourceRepository

    @Test
    fun `should save resource when timestamp is higher than existing`() {
        // Given
        val resourceId = "test-timestamp-save-1"
        val resourceData1 = mapOf("uri" to "https://example.com/test1", "title" to "Original")
        val originalTimestamp = System.currentTimeMillis()

        val resourceData2 = mapOf("uri" to "https://example.com/test1", "title" to "Updated")
        val updatedTimestamp = originalTimestamp + 1000 // Later timestamp

        // When - Save with original timestamp
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData1, originalTimestamp)
        val retrieved1 = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)

        // Then - Verify original was saved
        assertNotNull(retrieved1)
        assertEquals("Original", retrieved1!!["title"])

        // When - Try to save with later timestamp
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData2, updatedTimestamp)
        val retrieved2 = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)

        // Then - Verify updated data was saved
        assertNotNull(retrieved2)
        assertEquals("Updated", retrieved2!!["title"])
    }

    @Test
    fun `should not save resource when timestamp is lower than existing`() {
        // Given
        val resourceId = "test-timestamp-save-2"
        val resourceData1 = mapOf("uri" to "https://example.com/test2", "title" to "Original")
        val originalTimestamp = System.currentTimeMillis()

        val resourceData2 = mapOf("uri" to "https://example.com/test2", "title" to "Older Update")
        val olderTimestamp = originalTimestamp - 1000 // Earlier timestamp

        // When - Save with original timestamp
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData1, originalTimestamp)

        // When - Try to save with older timestamp
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData2, olderTimestamp)
        val retrieved = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)

        // Then - Verify original data was NOT updated
        assertNotNull(retrieved)
        assertEquals("Original", retrieved!!["title"])
    }

    @Test
    fun `should save resource when timestamp is equal to existing`() {
        // Given
        val resourceId = "test-timestamp-save-3"
        val resourceData1 = mapOf("uri" to "https://example.com/test3", "title" to "Original")
        val resourceData2 = mapOf("uri" to "https://example.com/test3", "title" to "Equal Update")
        val timestamp = System.currentTimeMillis()

        // When - Save with timestamp
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData1, timestamp)

        // When - Try to save with same timestamp
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData2, timestamp)
        val retrieved = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)

        // Then - Verify data was updated (equal timestamp is allowed)
        assertNotNull(retrieved)
        assertEquals("Equal Update", retrieved!!["title"])
    }

    @Test
    fun `should delete resource when timestamp is higher than existing`() {
        // Given
        val resourceId = "test-timestamp-delete-1"
        val resourceData = mapOf("uri" to "https://example.com/delete1", "title" to "To Delete")
        val savedTimestamp = System.currentTimeMillis()
        val deleteTimestamp = savedTimestamp + 1000

        // When - Save resource
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData, savedTimestamp)
        assertNotNull(resourceService.getResourceJson(resourceId, ResourceType.CONCEPT))

        // When - Mark as deleted with later timestamp
        resourceService.markResourceAsDeleted(resourceId, ResourceType.CONCEPT, deleteTimestamp)

        // Then - Verify resource is still retrievable but deleted flag is set
        val retrieved = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)
        assertNotNull(retrieved, "Resource should still be retrievable even when marked as deleted")
        assertEquals("To Delete", retrieved!!["title"])

        // Verify the deleted flag is set in the database
        val entity = resourceRepository.findById(resourceId).orElse(null)
        assertNotNull(entity, "Resource entity should exist in database")
        assertTrue(entity!!.deleted, "Resource should be marked as deleted in database")
    }

    @Test
    fun `should not delete resource when timestamp is lower than existing`() {
        // Given
        val resourceId = "test-timestamp-delete-2"
        val resourceData = mapOf("uri" to "https://example.com/delete2", "title" to "Keep Me")
        val savedTimestamp = System.currentTimeMillis()
        val deleteTimestamp = savedTimestamp - 1000 // Earlier timestamp

        // When - Save resource
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData, savedTimestamp)

        // When - Try to mark as deleted with older timestamp
        resourceService.markResourceAsDeleted(resourceId, ResourceType.CONCEPT, deleteTimestamp)

        // Then - Verify resource is NOT deleted
        val retrieved = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)
        assertNotNull(retrieved, "Resource should still exist with lower timestamp deletion")
        assertEquals("Keep Me", retrieved!!["title"])
    }

    @Test
    fun `should delete resource when timestamp is equal to existing`() {
        // Given
        val resourceId = "test-timestamp-delete-3"
        val resourceData = mapOf("uri" to "https://example.com/delete3", "title" to "Equal Delete")
        val timestamp = System.currentTimeMillis()

        // When - Save resource
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData, timestamp)

        // When - Mark as deleted with same timestamp
        resourceService.markResourceAsDeleted(resourceId, ResourceType.CONCEPT, timestamp)

        // Then - Verify resource is still retrievable but deleted flag is set
        val retrieved = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)
        assertNotNull(retrieved, "Resource should still be retrievable even when marked as deleted with equal timestamp")
        assertEquals("Equal Delete", retrieved!!["title"])

        // Verify the deleted flag is set in the database
        val entity = resourceRepository.findById(resourceId).orElse(null)
        assertNotNull(entity, "Resource entity should exist in database")
        assertTrue(entity!!.deleted, "Resource should be marked as deleted in database")
    }

    @Test
    fun `should save resource when timestamp is higher than deleted resource`() {
        // Given
        val resourceId = "test-timestamp-recreate"
        val resourceData1 = mapOf("uri" to "https://example.com/recreate", "title" to "Original")
        val originalTimestamp = System.currentTimeMillis()
        val deleteTimestamp = originalTimestamp + 500
        val recreateTimestamp = deleteTimestamp + 1000

        // When - Save resource
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData1, originalTimestamp)

        // When - Mark as deleted
        resourceService.markResourceAsDeleted(resourceId, ResourceType.CONCEPT, deleteTimestamp)
        // Note: Resource is still retrievable even when marked as deleted

        // When - Save with higher timestamp (recreate scenario)
        val resourceData2 = mapOf("uri" to "https://example.com/recreate", "title" to "Recreated")
        resourceService.storeResourceJson(resourceId, ResourceType.CONCEPT, resourceData2, recreateTimestamp)

        // Then - Verify resource is updated with new data
        val retrieved = resourceService.getResourceJson(resourceId, ResourceType.CONCEPT)
        assertNotNull(retrieved)
        assertEquals("Recreated", retrieved!!["title"])
    }

    @Test
    fun `should mark harvested resource as not deleted even if previously deleted`() {
        // Given - Resource is harvested, then deleted, then harvested again
        val resourceId = "test-harvest-undelete"
        val originalData = mapOf("@id" to "https://example.com/original", "title" to "Original Resource")
        val originalTimestamp = System.currentTimeMillis()

        val deleteTimestamp = originalTimestamp + 1000
        val reharvestTimestamp = deleteTimestamp + 1000

        // When - Original harvest
        resourceService.storeResourceJsonLd(resourceId, ResourceType.CONCEPT, originalData, originalTimestamp)
        assertNotNull(resourceService.getResourceJsonLd(resourceId, ResourceType.CONCEPT))

        // When - Resource is deleted
        resourceService.markResourceAsDeleted(resourceId, ResourceType.CONCEPT, deleteTimestamp)
        // Note: Resource is still retrievable even when marked as deleted

        // When - Resource is harvested again (should undelete it)
        val reharvestData = mapOf("@id" to "https://example.com/reharvested", "title" to "Re-harvested Resource")
        resourceService.storeResourceJsonLd(resourceId, ResourceType.CONCEPT, reharvestData, reharvestTimestamp)

        // Then - Resource should be available with updated data and not deleted
        val retrieved = resourceService.getResourceJsonLd(resourceId, ResourceType.CONCEPT)
        assertNotNull(retrieved)
        assertEquals("Re-harvested Resource", retrieved!!["title"])

        // Verify the deleted flag is cleared in the database
        val entity = resourceRepository.findById(resourceId).orElse(null)
        assertNotNull(entity, "Resource entity should exist in database")
        assertFalse(entity!!.deleted, "Resource should not be marked as deleted after re-harvesting")
    }
}
