package no.fdk.resourceservice.service

import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.verify
import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.repository.ResourceRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional

class ResourceServiceTest {
    private val resourceRepository = mockk<ResourceRepository>(relaxed = true)
    private lateinit var resourceService: ResourceService

    @BeforeEach
    fun setUp() {
        resourceService = ResourceService(resourceRepository)
    }

    @Test
    fun `getResourceJson returns null when entity not found`() {
        every { resourceRepository.findById("missing") } returns Optional.empty()

        val result = resourceService.getResourceJson("missing", ResourceType.CONCEPT)

        assertNull(result)
    }

    @Test
    fun `getResourceJson returns null when resource type mismatch`() {
        val entity = ResourceEntity(id = "1", resourceType = "DATASET", resourceJson = mapOf("uri" to "https://example.com/1"))
        every { resourceRepository.findById("1") } returns Optional.of(entity)

        val result = resourceService.getResourceJson("1", ResourceType.CONCEPT)

        assertNull(result)
    }

    @Test
    fun `getResourceJson returns json when found and type matches`() {
        val json = mapOf("uri" to "https://example.com/1", "title" to "Concept")
        val entity = ResourceEntity(id = "1", resourceType = "CONCEPT", resourceJson = json)
        every { resourceRepository.findById("1") } returns Optional.of(entity)

        val result = resourceService.getResourceJson("1", ResourceType.CONCEPT)

        assertEquals(json, result)
    }

    @Test
    fun `getResourceEntity returns null when not found`() {
        every { resourceRepository.findById("missing") } returns Optional.empty()

        val result = resourceService.getResourceEntity("missing", ResourceType.CONCEPT)

        assertNull(result)
    }

    @Test
    fun `getResourceEntity returns entity when found and type matches`() {
        val entity = ResourceEntity(id = "1", resourceType = "CONCEPT", resourceJson = mapOf("uri" to "https://example.com/1"))
        every { resourceRepository.findById("1") } returns Optional.of(entity)

        val result = resourceService.getResourceEntity("1", ResourceType.CONCEPT)

        assertEquals(entity, result)
    }

    @Test
    fun `shouldUpdateResource returns true when no existing timestamp`() {
        every { resourceRepository.findTimestampById("new-id") } returns null

        val result = resourceService.shouldUpdateResource("new-id", 1000L)

        assertEquals(true, result)
    }

    @Test
    fun `shouldUpdateResource returns true when new timestamp is greater or equal`() {
        every { resourceRepository.findTimestampById("id") } returns 500L

        assertEquals(true, resourceService.shouldUpdateResource("id", 1000L))
        assertEquals(true, resourceService.shouldUpdateResource("id", 500L))
    }

    @Test
    fun `shouldUpdateResource returns false when existing timestamp is newer`() {
        every { resourceRepository.findTimestampById("id") } returns 2000L

        val result = resourceService.shouldUpdateResource("id", 1000L)

        assertEquals(false, result)
    }

    @Test
    fun `getResourceJsonByUri returns json when found by uri column`() {
        val json = mapOf("uri" to "https://example.com/concept/1", "title" to "Concept")
        val entity = ResourceEntity(id = "1", resourceType = "CONCEPT", resourceJson = json, uri = "https://example.com/concept/1")
        every { resourceRepository.findConceptByIdentifier("https://example.com/concept/1") } returns null
        every { resourceRepository.findByUri("https://example.com/concept/1") } returns entity

        val result = resourceService.getResourceJsonByUri("https://example.com/concept/1", ResourceType.CONCEPT)

        assertEquals(json, result)
    }

    @Test
    fun `getResourceJsonByUri returns null when entity type mismatch`() {
        val entity = ResourceEntity(id = "1", resourceType = "DATASET", resourceJson = mapOf(), uri = "https://example.com/1")
        every { resourceRepository.findConceptByIdentifier("https://example.com/1") } returns null
        every { resourceRepository.findByUri("https://example.com/1") } returns entity

        val result = resourceService.getResourceJsonByUri("https://example.com/1", ResourceType.CONCEPT)

        assertNull(result)
    }

    @Test
    fun `getResourceJsonByUri uses findConceptByIdentifier for concept type when uri lookup fails`() {
        val json = mapOf("identifier" to "https://example.com/concept/1", "title" to "Concept")
        val entity = ResourceEntity(id = "1", resourceType = "CONCEPT", resourceJson = json, uri = null, deleted = false)
        every { resourceRepository.findByUri(any()) } returns null
        every { resourceRepository.findConceptByIdentifier("https://example.com/concept/1") } returns entity

        val result = resourceService.getResourceJsonByUri("https://example.com/concept/1", ResourceType.CONCEPT)

        assertEquals(json, result)
    }

    @Test
    fun `getResourceJsonByUri single arg returns json when found via getResourceEntityByUri`() {
        val json = mapOf("uri" to "https://example.com/1", "title" to "Resource")
        val entity = ResourceEntity(id = "1", resourceType = "CONCEPT", resourceJson = json, uri = "https://example.com/1")
        every { resourceRepository.findByUri("https://example.com/1") } returns entity

        val result = resourceService.getResourceJsonByUri("https://example.com/1")

        assertEquals(json, result)
    }

    @Test
    fun `getResourceEntityByUri uses findByUriInJson fallback when findByUri returns null`() {
        val entity =
            ResourceEntity(id = "1", resourceType = "DATASET", resourceJson = mapOf("uri" to "https://example.com/fallback"), uri = null)
        every { resourceRepository.findByUri("https://example.com/fallback") } returns null
        every { resourceRepository.findByUriInJson("https://example.com/fallback") } returns entity
        every { resourceRepository.findConceptByIdentifier(any()) } returns null

        val result = resourceService.getResourceEntityByUri("https://example.com/fallback")

        assertEquals(entity, result)
    }

    @Test
    fun `getResourceJsonList returns list of resource json by type`() {
        val entities =
            listOf(
                ResourceEntity(id = "1", resourceType = "CONCEPT", resourceJson = mapOf("a" to 1)),
                ResourceEntity(id = "2", resourceType = "CONCEPT", resourceJson = mapOf("b" to 2)),
            )
        every { resourceRepository.findByResourceType("CONCEPT") } returns entities

        val result = resourceService.getResourceJsonList(ResourceType.CONCEPT)

        assertEquals(2, result.size)
        assertEquals(mapOf("a" to 1), result[0])
        assertEquals(mapOf("b" to 2), result[1])
    }

    @Test
    fun `markResourceAsDeleted skips when existing timestamp is higher`() {
        val entity = ResourceEntity(id = "1", resourceType = "CONCEPT", timestamp = 2000L)
        every { resourceRepository.findById("1") } returns Optional.of(entity)

        resourceService.markResourceAsDeleted("1", ResourceType.CONCEPT, 1000L)

        verify(exactly = 0) { resourceRepository.markAsDeleted(any(), any()) }
        verify(exactly = 0) { resourceRepository.save(any()) }
    }

    @Test
    fun `markResourceAsDeleted calls repository when existing entity has lower or equal timestamp`() {
        val entity = ResourceEntity(id = "1", resourceType = "CONCEPT", timestamp = 500L)
        every { resourceRepository.findById("1") } returns Optional.of(entity)
        every { resourceRepository.markAsDeleted("1", 1000L) } returns 1

        resourceService.markResourceAsDeleted("1", ResourceType.CONCEPT, 1000L)

        verify(exactly = 1) { resourceRepository.markAsDeleted("1", 1000L) }
    }

    @Test
    fun `markResourceAsDeleted saves new deleted entity when resource does not exist`() {
        every { resourceRepository.findById("missing") } returns Optional.empty()
        every { resourceRepository.save(any()) } returns
            ResourceEntity(id = "missing", resourceType = "CONCEPT", deleted = true, timestamp = 1000L)

        resourceService.markResourceAsDeleted("missing", ResourceType.CONCEPT, 1000L)

        verify(exactly = 1) { resourceRepository.save(any()) }
    }

    @Test
    fun `getResourceJsonListById returns filtered list by type`() {
        val entity1 = ResourceEntity(id = "1", resourceType = "CONCEPT", resourceJson = mapOf("x" to 1))
        val entity2 = ResourceEntity(id = "2", resourceType = "DATASET", resourceJson = mapOf("y" to 2))
        every { resourceRepository.findAllById(listOf("1", "2")) } returns listOf(entity1, entity2)

        val result = resourceService.getResourceJsonListById(listOf("1", "2"), ResourceType.CONCEPT)

        assertEquals(1, result.size)
        assertEquals(mapOf("x" to 1), result[0])
    }

    @Test
    fun `storeResourceJson creates new entity when not exists`() {
        every { resourceRepository.findById("new-id") } returns Optional.empty()
        every { resourceRepository.save(any()) } returns ResourceEntity(id = "new-id", resourceType = "CONCEPT")

        resourceService.storeResourceJson(
            "new-id",
            ResourceType.CONCEPT,
            mapOf("uri" to "https://example.com/new", "title" to "New"),
            1000L,
        )

        verify(exactly = 1) { resourceRepository.save(any()) }
    }

    @Test
    fun `storeResourceJson updates existing entity when timestamp is newer`() {
        val existing = ResourceEntity(id = "id", resourceType = "CONCEPT", timestamp = 500L)
        every { resourceRepository.findById("id") } returns Optional.of(existing)
        every { resourceRepository.updateResourceJson("id", any(), "https://example.com/1", 1000L) } returns 1

        resourceService.storeResourceJson(
            "id",
            ResourceType.CONCEPT,
            mapOf("uri" to "https://example.com/1", "title" to "Updated"),
            1000L,
        )

        verify(exactly = 1) { resourceRepository.updateResourceJson("id", any(), "https://example.com/1", 1000L) }
    }

    @Test
    fun `storeResourceJson skips when existing timestamp is older`() {
        val existing = ResourceEntity(id = "id", resourceType = "CONCEPT", timestamp = 2000L)
        every { resourceRepository.findById("id") } returns Optional.of(existing)

        resourceService.storeResourceJson("id", ResourceType.CONCEPT, mapOf("title" to "Skip"), 1000L)

        verify(exactly = 0) { resourceRepository.updateResourceJson(any(), any(), any(), any()) }
        verify(exactly = 0) { resourceRepository.save(any()) }
    }

    @Test
    fun `storeResourceGraphData creates new entity when not exists`() {
        every { resourceRepository.findById("graph-id") } returns Optional.empty()
        every { resourceRepository.save(any()) } returns ResourceEntity(id = "graph-id", resourceType = "DATASET")
        every { resourceRepository.flush() } just Runs

        resourceService.storeResourceGraphData("graph-id", ResourceType.DATASET, "<a> <b> <c> .", "TURTLE", 2000L)

        verify(exactly = 1) { resourceRepository.save(any()) }
        verify(exactly = 1) { resourceRepository.flush() }
    }

    @Test
    fun `storeResourceGraphData updates existing when timestamp is newer`() {
        val existing = ResourceEntity(id = "g", resourceType = "CONCEPT", timestamp = 1000L)
        every { resourceRepository.findById("g") } returns Optional.of(existing)
        every { resourceRepository.updateResourceGraphData("g", "<graph>", "TURTLE", 2000L) } returns 1
        every { resourceRepository.flush() } just Runs

        resourceService.storeResourceGraphData("g", ResourceType.CONCEPT, "<graph>", "TURTLE", 2000L)

        verify(exactly = 1) { resourceRepository.updateResourceGraphData("g", "<graph>", "TURTLE", 2000L) }
    }

    @Test
    fun `getResourceGraphDataByUri returns graph data when entity found and type matches`() {
        val entity = ResourceEntity(id = "1", resourceType = "CONCEPT", resourceGraphData = "turtle data", uri = "https://example.com/1")
        every { resourceRepository.findByUri("https://example.com/1") } returns entity

        val result = resourceService.getResourceGraphDataByUri("https://example.com/1", ResourceType.CONCEPT)

        assertEquals("turtle data", result)
    }

    @Test
    fun `getResourceGraphDataByUri returns null when type mismatch`() {
        val entity = ResourceEntity(id = "1", resourceType = "DATASET", resourceGraphData = "data", uri = "https://example.com/1")
        every { resourceRepository.findByUri("https://example.com/1") } returns entity

        val result = resourceService.getResourceGraphDataByUri("https://example.com/1", ResourceType.CONCEPT)

        assertNull(result)
    }

    @Test
    fun `getResourceJsonListSince returns list from repository`() {
        val entities =
            listOf(
                ResourceEntity(id = "1", resourceType = "CONCEPT", resourceJson = mapOf("a" to 1)),
                ResourceEntity(id = "2", resourceType = "CONCEPT", resourceJson = mapOf("b" to 2)),
            )
        every { resourceRepository.findResourcesSince("CONCEPT", 1000L) } returns entities

        val result = resourceService.getResourceJsonListSince(ResourceType.CONCEPT, 1000L)

        assertEquals(2, result.size)
        assertEquals(mapOf("a" to 1), result[0])
        assertEquals(mapOf("b" to 2), result[1])
    }
}
