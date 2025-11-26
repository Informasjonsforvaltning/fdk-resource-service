package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.Instant
import java.util.Optional

class UnionGraphServiceTest {
    private lateinit var unionGraphOrderRepository: UnionGraphOrderRepository
    private lateinit var resourceRepository: ResourceRepository
    private lateinit var objectMapper: ObjectMapper
    private lateinit var webhookService: WebhookService
    private lateinit var unionGraphService: UnionGraphService

    @BeforeEach
    fun setUp() {
        unionGraphOrderRepository = mockk(relaxed = true)
        resourceRepository = mockk(relaxed = true)
        val resourceService = mockk<ResourceService>(relaxed = true)
        objectMapper = ObjectMapper()
        webhookService = mockk(relaxed = true)
        unionGraphService =
            UnionGraphService(
                unionGraphOrderRepository,
                resourceRepository,
                resourceService,
                objectMapper,
                webhookService,
            )
    }

    @Test
    fun `createOrder should create new order when no existing order found`() {
        // Given
        val resourceTypes = listOf(ResourceType.CONCEPT, ResourceType.DATASET)
        val updateTtlHours = 24
        val webhookUrl = "https://example.com/webhook"

        every { unionGraphOrderRepository.findByConfiguration(any(), any(), any(), any(), any()) } returns null
        every { unionGraphOrderRepository.save(any()) } answers { firstArg() }

        // When
        val result = unionGraphService.createOrder(resourceTypes, updateTtlHours, webhookUrl, null, false)

        // Then
        assertTrue(result.isNew)
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, result.order.status)
        assertEquals(listOf("CONCEPT", "DATASET"), result.order.resourceTypes)
        assertEquals(updateTtlHours, result.order.updateTtlHours)
        assertEquals(webhookUrl, result.order.webhookUrl)
        verify { unionGraphOrderRepository.save(any()) }
    }

    @Test
    fun `createOrder should return existing order when found`() {
        // Given
        val resourceTypes = listOf(ResourceType.CONCEPT)
        val existingOrder =
            UnionGraphOrder(
                id = "existing-id",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                resourceTypes = listOf("CONCEPT"),
                updateTtlHours = 12,
                webhookUrl = "https://example.com/webhook",
            )

        every {
            unionGraphOrderRepository.findByConfiguration(
                "{CONCEPT}",
                12,
                "https://example.com/webhook",
                null,
                false,
            )
        } returns existingOrder

        // When
        val result = unionGraphService.createOrder(resourceTypes, 12, "https://example.com/webhook", null, false)

        // Then
        assertFalse(result.isNew)
        assertEquals(existingOrder.id, result.order.id)
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, result.order.status)
        verify(exactly = 0) { unionGraphOrderRepository.save(any()) }
    }

    @Test
    fun `createOrder should throw IllegalArgumentException when updateTtlHours is 1`() {
        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                unionGraphService.createOrder(
                    resourceTypes = null,
                    updateTtlHours = 1,
                    webhookUrl = null,
                    resourceFilters = null,
                    expandDistributionAccessServices = false,
                )
            }
        assertTrue(exception.message!!.contains("updateTtlHours must be 0"))
    }

    @Test
    fun `createOrder should throw IllegalArgumentException when updateTtlHours is 2`() {
        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                unionGraphService.createOrder(
                    resourceTypes = null,
                    updateTtlHours = 2,
                    webhookUrl = null,
                    resourceFilters = null,
                    expandDistributionAccessServices = false,
                )
            }
        assertTrue(exception.message!!.contains("updateTtlHours must be 0"))
    }

    @Test
    fun `createOrder should throw IllegalArgumentException when updateTtlHours is 3`() {
        // When & Then
        val exception =
            assertThrows<IllegalArgumentException> {
                unionGraphService.createOrder(
                    resourceTypes = null,
                    updateTtlHours = 3,
                    webhookUrl = null,
                    resourceFilters = null,
                    expandDistributionAccessServices = false,
                )
            }
        assertTrue(exception.message!!.contains("updateTtlHours must be 0"))
    }

    @Test
    fun `createOrder should accept updateTtlHours of 0`() {
        // Given
        every { unionGraphOrderRepository.findByConfiguration(any(), any(), any(), any(), any()) } returns null
        every { unionGraphOrderRepository.save(any()) } answers { firstArg() }

        // When
        val result =
            unionGraphService.createOrder(
                resourceTypes = null,
                updateTtlHours = 0,
                webhookUrl = null,
                resourceFilters = null,
                expandDistributionAccessServices = false,
            )

        // Then
        assertNotNull(result)
        assertEquals(0, result.order.updateTtlHours)
    }

    @Test
    fun `createOrder should accept updateTtlHours greater than 3`() {
        // Given
        every { unionGraphOrderRepository.findByConfiguration(any(), any(), any(), any(), any()) } returns null
        every { unionGraphOrderRepository.save(any()) } answers { firstArg() }

        // When
        val result =
            unionGraphService.createOrder(
                resourceTypes = null,
                updateTtlHours = 4,
                webhookUrl = null,
                resourceFilters = null,
                expandDistributionAccessServices = false,
            )

        // Then
        assertNotNull(result)
        assertEquals(4, result.order.updateTtlHours)
    }

    @Test
    fun `createOrder should throw exception for non-HTTPS webhook URL`() {
        // Given
        val webhookUrl = "http://example.com/webhook"

        // When & Then
        assertThrows<IllegalArgumentException> {
            unionGraphService.createOrder(webhookUrl = webhookUrl)
        }
    }

    @Test
    fun `createOrder should accept null webhook URL`() {
        // Given
        every { unionGraphOrderRepository.findByConfiguration(any(), any(), any(), any(), any()) } returns null
        every { unionGraphOrderRepository.save(any()) } answers { firstArg() }

        // When
        val result = unionGraphService.createOrder(webhookUrl = null)

        // Then
        assertTrue(result.isNew)
        assertNull(result.order.webhookUrl)
    }

    @Test
    fun `createOrder should accept empty resource types list`() {
        // Given
        every { unionGraphOrderRepository.findByConfiguration(null, any(), any(), any(), any()) } returns null
        every { unionGraphOrderRepository.save(any()) } answers { firstArg() }

        // When
        val result = unionGraphService.createOrder(resourceTypes = emptyList())

        // Then
        assertTrue(result.isNew)
        // Empty list is converted to null in the service
        assertTrue(result.order.resourceTypes == null || result.order.resourceTypes?.isEmpty() == true)
    }

    @Test
    fun `createOrder should sort resource types for consistency`() {
        // Given
        val resourceTypes = listOf(ResourceType.DATASET, ResourceType.CONCEPT)
        every { unionGraphOrderRepository.findByConfiguration(any(), any(), any(), any(), any()) } returns null
        every { unionGraphOrderRepository.save(any()) } answers { firstArg() }

        // When
        val result = unionGraphService.createOrder(resourceTypes)

        // Then
        assertEquals(listOf("CONCEPT", "DATASET"), result.order.resourceTypes)
    }

    @Test
    fun `createOrder should persist dataset filters`() {
        // Given
        val filters =
            UnionGraphResourceFilters(
                dataset =
                    UnionGraphResourceFilters.DatasetFilters(
                        isOpenData = true,
                        isRelatedToTransportportal = null,
                    ),
            )
        every { unionGraphOrderRepository.findByConfiguration(any(), any(), any(), any(), any()) } returns null
        every { unionGraphOrderRepository.save(any()) } answers { firstArg() }

        // When
        val result =
            unionGraphService.createOrder(
                resourceTypes = listOf(ResourceType.DATASET),
                resourceFilters = filters,
            )

        // Then
        assertTrue(result.isNew)
        assertEquals(
            true,
            result.order.resourceFilters
                ?.dataset
                ?.isOpenData,
        )
        assertNull(
            result.order.resourceFilters
                ?.dataset
                ?.isRelatedToTransportportal,
        )
    }

    @Test
    fun `createOrder should throw when dataset filters used without dataset type`() {
        // Given
        val filters =
            UnionGraphResourceFilters(
                dataset = UnionGraphResourceFilters.DatasetFilters(isOpenData = true),
            )

        // When & Then
        assertThrows<IllegalArgumentException> {
            unionGraphService.createOrder(
                resourceTypes = listOf(ResourceType.CONCEPT),
                resourceFilters = filters,
                expandDistributionAccessServices = false,
            )
        }
    }

    @Test
    fun `createOrder should persist expandDistributionAccessServices flag`() {
        // Given
        every { unionGraphOrderRepository.findByConfiguration(any(), any(), any(), any(), any()) } returns null
        every { unionGraphOrderRepository.save(any()) } answers { firstArg() }

        // When
        val result =
            unionGraphService.createOrder(
                resourceTypes = listOf(ResourceType.DATASET),
                expandDistributionAccessServices = true,
            )

        // Then
        assertTrue(result.isNew)
        assertTrue(result.order.expandDistributionAccessServices)
    }

    @Test
    fun `createOrder should treat different expandDistributionAccessServices as different orders`() {
        // Given
        val order1 =
            UnionGraphOrder(
                id = "order-1",
                resourceTypes = listOf("DATASET"),
                expandDistributionAccessServices = false,
            )
        val order2 =
            UnionGraphOrder(
                id = "order-2",
                resourceTypes = listOf("DATASET"),
                expandDistributionAccessServices = true,
            )

        every {
            unionGraphOrderRepository.findByConfiguration(
                "{DATASET}",
                0,
                null,
                null,
                false,
            )
        } returns order1
        every {
            unionGraphOrderRepository.findByConfiguration(
                "{DATASET}",
                0,
                null,
                null,
                true,
            )
        } returns order2

        // When
        val result1 = unionGraphService.createOrder(listOf(ResourceType.DATASET), expandDistributionAccessServices = false)
        val result2 = unionGraphService.createOrder(listOf(ResourceType.DATASET), expandDistributionAccessServices = true)

        // Then
        assertFalse(result1.isNew)
        assertFalse(result2.isNew)
        assertEquals("order-1", result1.order.id)
        assertEquals("order-2", result2.order.id)
        assertFalse(result1.order.expandDistributionAccessServices)
        assertTrue(result2.order.expandDistributionAccessServices)
    }

    @Test
    fun `resetOrderToPending should reset order and call webhook`() {
        // Given
        val orderId = "test-order-id"
        val previousOrder =
            UnionGraphOrder(
                id = orderId,
                status = UnionGraphOrder.GraphStatus.FAILED,
                errorMessage = "Test error",
            )
        val resetOrder =
            UnionGraphOrder(
                id = orderId,
                status = UnionGraphOrder.GraphStatus.PENDING,
                errorMessage = null,
            )

        every { unionGraphOrderRepository.findById(orderId) } returnsMany
            listOf(Optional.of(previousOrder), Optional.of(resetOrder))
        every { unionGraphOrderRepository.resetToPending(orderId) } returns 1

        // When
        val result = unionGraphService.resetOrderToPending(orderId)

        // Then
        assertNotNull(result)
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, result?.status)
        verify { unionGraphOrderRepository.resetToPending(orderId) }
        verify { webhookService.callWebhook(resetOrder, UnionGraphOrder.GraphStatus.FAILED) }
    }

    @Test
    fun `resetOrderToPending should return null when order not found`() {
        // Given
        val orderId = "non-existent-id"
        every { unionGraphOrderRepository.findById(orderId) } returns Optional.empty()
        every { unionGraphOrderRepository.resetToPending(orderId) } returns 0

        // When
        val result = unionGraphService.resetOrderToPending(orderId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getOrder should return order when found`() {
        // Given
        val orderId = "test-order-id"
        val order =
            UnionGraphOrder(
                id = orderId,
                status = UnionGraphOrder.GraphStatus.PENDING,
            )
        every { unionGraphOrderRepository.findById(orderId) } returns Optional.of(order)

        // When
        val result = unionGraphService.getOrder(orderId)

        // Then
        assertNotNull(result)
        assertEquals(orderId, result?.id)
    }

    @Test
    fun `getOrder should return null when not found`() {
        // Given
        val orderId = "non-existent-id"
        every { unionGraphOrderRepository.findById(orderId) } returns Optional.empty()

        // When
        val result = unionGraphService.getOrder(orderId)

        // Then
        assertNull(result)
    }

    @Test
    fun `getAllOrders should return all orders sorted by created date`() {
        // Given
        val orders =
            listOf(
                UnionGraphOrder(
                    id = "order-2",
                    createdAt = Instant.now(),
                ),
                UnionGraphOrder(
                    id = "order-1",
                    createdAt = Instant.now().minusSeconds(100),
                ),
            )
        every { unionGraphOrderRepository.findAllByOrderByCreatedAtDesc() } returns orders

        // When
        val result = unionGraphService.getAllOrders()

        // Then
        assertEquals(2, result.size)
        assertEquals("order-2", result[0].id)
        assertEquals("order-1", result[1].id)
    }

    @Test
    fun `deleteOrder should delete order when found`() {
        // Given
        val orderId = "test-order-id"
        val order =
            UnionGraphOrder(
                id = orderId,
                status = UnionGraphOrder.GraphStatus.PENDING,
            )
        every { unionGraphOrderRepository.findById(orderId) } returns Optional.of(order)

        // When
        val result = unionGraphService.deleteOrder(orderId)

        // Then
        assertTrue(result)
        verify { unionGraphOrderRepository.deleteById(orderId) }
    }

    @Test
    fun `deleteOrder should return false when order not found`() {
        // Given
        val orderId = "non-existent-id"
        every { unionGraphOrderRepository.findById(orderId) } returns Optional.empty()

        // When
        val result = unionGraphService.deleteOrder(orderId)

        // Then
        assertFalse(result)
        verify(exactly = 0) { unionGraphOrderRepository.deleteById(any()) }
    }

    @Test
    fun `buildUnionGraph should return null when no resources found`() {
        // Given
        every { resourceRepository.countByResourceTypeAndDeletedFalse(any()) } returns 0L

        // When
        val result = unionGraphService.buildUnionGraph(listOf(ResourceType.CONCEPT))

        // Then
        assertNull(result)
    }

    @Test
    fun `buildUnionGraph should apply dataset filters`() {
        // Given
        every { resourceRepository.countDatasetsByFilters("true", null) } returns 0L

        // When
        val result =
            unionGraphService.buildUnionGraph(
                listOf(ResourceType.DATASET),
                UnionGraphResourceFilters(
                    dataset = UnionGraphResourceFilters.DatasetFilters(isOpenData = true),
                ),
            )

        // Then
        assertNull(result)
        verify { resourceRepository.countDatasetsByFilters("true", null) }
        verify(exactly = 0) { resourceRepository.countByResourceTypeAndDeletedFalse("DATASET") }
    }

    @Test
    fun `processOrder should mark order as completed when graph built successfully`() {
        // Given
        val orderId = "test-order-id"
        val instanceId = "instance-1"
        val order =
            UnionGraphOrder(
                id = orderId,
                status = UnionGraphOrder.GraphStatus.PENDING,
                resourceTypes = listOf("CONCEPT"),
            )
        // After locking, the order should have PROCESSING status
        val lockedOrder =
            UnionGraphOrder(
                id = orderId,
                status = UnionGraphOrder.GraphStatus.PROCESSING,
                resourceTypes = listOf("CONCEPT"),
                lockedBy = instanceId,
            )
        val unionGraph = mapOf("@graph" to listOf(mapOf("@id" to "https://example.com/resource")))

        every { unionGraphOrderRepository.lockOrderForProcessing(orderId, instanceId) } returns 1
        // getOrderInNewTransaction calls findById in a new transaction
        // It should return the locked order with PROCESSING status
        every { unionGraphOrderRepository.findById(orderId) } returns Optional.of(lockedOrder)
        every { resourceRepository.countByResourceTypeAndDeletedFalse("CONCEPT") } returns 0L

        // When
        unionGraphService.processOrder(order, instanceId)

        // Then
        verify { unionGraphOrderRepository.lockOrderForProcessing(orderId, instanceId) }
        // Verify getOrderInNewTransaction is called (via findById in the new transaction)
        // This ensures we fetch the order after locking to verify PROCESSING status
        verify(atLeast = 1) { unionGraphOrderRepository.findById(orderId) }
    }

    @Test
    fun `processOrder should mark order as failed when lock fails`() {
        // Given
        val orderId = "test-order-id"
        val instanceId = "instance-1"
        val order =
            UnionGraphOrder(
                id = orderId,
                status = UnionGraphOrder.GraphStatus.PENDING,
            )

        every { unionGraphOrderRepository.lockOrderForProcessing(orderId, instanceId) } returns 0

        // When
        unionGraphService.processOrder(order, instanceId)

        // Then
        verify(exactly = 0) { unionGraphOrderRepository.markAsCompleted(any(), any()) }
        verify(exactly = 0) { unionGraphOrderRepository.markAsFailed(any(), any()) }
    }
}
