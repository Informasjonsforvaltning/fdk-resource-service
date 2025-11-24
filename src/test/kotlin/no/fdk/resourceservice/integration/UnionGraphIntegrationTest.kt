package no.fdk.resourceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional

/**
 * Integration tests for union graph functionality.
 *
 * These tests verify the full flow from creating orders to processing them,
 * including database interactions and webhook calls.
 */
@TestPropertySource(
    properties = [
        "app.union-graphs.delete-enabled=true",
        "app.union-graphs.reset-enabled=true",
    ],
)
class UnionGraphIntegrationTest : BaseIntegrationTest() {
    @Autowired
    private lateinit var unionGraphOrderRepository: UnionGraphOrderRepository

    @Autowired
    private lateinit var resourceRepository: ResourceRepository

    @Autowired
    private lateinit var unionGraphService: no.fdk.resourceservice.service.UnionGraphService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        // Clean up test data
        unionGraphOrderRepository.deleteAll()
    }

    @Test
    @Transactional
    fun `createOrder should create new order in database`() {
        // When
        val result =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                updateTtlHours = 24,
                webhookUrl = "https://example.com/webhook",
            )

        // Then
        assertTrue(result.isNew)
        val savedOrder = unionGraphOrderRepository.findById(result.order.id)
        assertTrue(savedOrder.isPresent)
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, savedOrder.get().status)
        assertEquals(listOf("CONCEPT"), savedOrder.get().resourceTypes)
        assertEquals(24, savedOrder.get().updateTtlHours)
        assertEquals("https://example.com/webhook", savedOrder.get().webhookUrl)
    }

    @Test
    @Transactional
    fun `createOrder should return existing order when configuration matches`() {
        // Given
        val existingOrder =
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                updateTtlHours = 12,
            )

        // When
        val result =
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                updateTtlHours = 12,
            )

        // Then
        assertTrue(!result.isNew)
        assertEquals(existingOrder.order.id, result.order.id)
    }

    @Test
    @Transactional
    fun `createOrder should treat different webhook URLs as different orders`() {
        // Given
        val order1 =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                webhookUrl = "https://example.com/webhook1",
            )

        // When
        val order2 =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                webhookUrl = "https://example.com/webhook2",
            )

        // Then
        assertTrue(order1.isNew)
        assertTrue(order2.isNew)
        assertTrue(order1.order.id != order2.order.id)
    }

    @Test
    @Transactional
    fun `resetOrderToPending should reset failed order`() {
        // Given
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order",
                    status = UnionGraphOrder.GraphStatus.FAILED,
                    errorMessage = "Test error",
                ),
            )

        // When
        val result = unionGraphService.resetOrderToPending(order.id)

        // Then
        assertNotNull(result)
        val updatedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, updatedOrder.status)
        assertNull(updatedOrder.errorMessage)
    }

    @Test
    @Transactional
    fun `deleteOrder should remove order from database`() {
        // Given
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                ),
            )

        // When
        val deleted = unionGraphService.deleteOrder(order.id)

        // Then
        assertTrue(deleted)
        assertTrue(unionGraphOrderRepository.findById(order.id).isEmpty)
    }

    @Test
    @Transactional
    fun `getAllOrders should return all orders sorted by created date`() {
        // Given
        val order1 =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "order-1",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                ),
            )
        Thread.sleep(10) // Ensure different timestamps
        val order2 =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "order-2",
                    status = UnionGraphOrder.GraphStatus.COMPLETED,
                ),
            )

        // When
        val orders = unionGraphService.getAllOrders()

        // Then
        assertTrue(orders.size >= 2)
        // Should be sorted by created date descending (newest first)
        val orderIds = orders.map { it.id }
        assertTrue(orderIds.indexOf("order-2") < orderIds.indexOf("order-1"))
    }

    @Test
    @Transactional
    fun `buildUnionGraph should return null when no resources found`() {
        // When
        val result = unionGraphService.buildUnionGraph(listOf(ResourceType.CONCEPT))

        // Then
        assertNull(result)
    }

    @Test
    @Transactional
    fun `processOrder should mark order as failed when no resources found`() {
        // Given - no resources in database
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                    resourceTypes = listOf("CONCEPT"),
                ),
            )

        // When
        unionGraphService.processOrder(order, "test-instance")

        // Then
        val updatedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.FAILED, updatedOrder.status)
        assertNotNull(updatedOrder.errorMessage)
    }
}
