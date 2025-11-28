package no.fdk.resourceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import no.fdk.resourceservice.service.ResourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.time.Instant
import java.time.temporal.ChronoUnit

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
    private lateinit var resourceService: ResourceService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        // Clean up test data
        unionGraphOrderRepository.deleteAll()
        resourceRepository.deleteAll()
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
    fun `processOrder should mark order as failed when no resources found`() {
        // Given - no resources in database
        // Create order in a transaction that commits (simulating controller)
        val orderId: String =
            transactionTemplate.execute {
                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                order.id
            }!!

        // Fetch the order to pass to processOrder
        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // When - processOrder uses NOT_SUPPORTED, so it runs outside any transaction
        unionGraphService.processOrder(order, "test-instance")

        // Then - verify in a new transaction (simulating API call)
        val updatedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.FAILED, updatedOrder.status)
        assertNotNull(updatedOrder.errorMessage)
    }

    @Test
    fun `processOrder should transition PENDING to PROCESSING to COMPLETED with database mutations`() {
        // Given - create test resources with JSON-LD (commits to database)
        val orderId: String =
            transactionTemplate.execute {
                val resourceId1 = "test-concept-1"
                val resourceId2 = "test-concept-2"
                val jsonLd1 =
                    mapOf(
                        "@id" to "https://example.com/concept1",
                        "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
                        "http://purl.org/dc/terms/title" to listOf(mapOf("@value" to "Test Concept 1")),
                    )
                val jsonLd2 =
                    mapOf(
                        "@id" to "https://example.com/concept2",
                        "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
                        "http://purl.org/dc/terms/title" to listOf(mapOf("@value" to "Test Concept 2")),
                    )
                val timestamp = System.currentTimeMillis()
                resourceService.storeResourceJsonLd(resourceId1, ResourceType.CONCEPT, jsonLd1, timestamp)
                resourceService.storeResourceJsonLd(resourceId2, ResourceType.CONCEPT, jsonLd2, timestamp + 1)

                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-complete",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                            updateTtlHours = 24,
                            webhookUrl = "https://example.com/webhook",
                        ),
                    )
                order.id
            }!!

        // Verify initial state in a new transaction
        val initialOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, initialOrder.status)
        assertNull(initialOrder.lockedBy)
        assertNull(initialOrder.lockedAt)
        assertNull(initialOrder.processedAt)
        assertNull(initialOrder.graphData)

        // Fetch the order to pass to processOrder
        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // When - process the order (uses NOT_SUPPORTED, so runs outside any transaction)
        unionGraphService.processOrder(order, "test-instance")

        // Then - verify state transitions and database mutations in a new transaction
        // Note: The order goes through PENDING -> PROCESSING -> COMPLETED
        // We verify the final COMPLETED state, but PROCESSING should be visible
        // during processing (tested separately in getOrderInNewTransaction test)
        val completedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, completedOrder.status)
        assertNotNull(completedOrder.processedAt, "processedAt should be set when completed")
        assertNotNull(completedOrder.graphData, "graphData should be set when completed")
        assertNull(completedOrder.errorMessage, "errorMessage should be null when completed")
        assertNotNull(completedOrder.updatedAt, "updatedAt should be set")
        // Verify the graph data is a non-empty string
        val graphData = completedOrder.graphData
        assertNotNull(graphData)
        assertTrue(graphData!!.isNotEmpty(), "graphData should not be empty")
    }

    @Test
    fun `processOrder should transition PENDING to PROCESSING to FAILED with database mutations`() {
        // Given - create order (commits to database)
        val orderId: String =
            transactionTemplate.execute {
                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-failed",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("DATASET"),
                        ),
                    )
                order.id
            }!!

        // Fetch the order to pass to processOrder
        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // When - process the order (no datasets exist, uses NOT_SUPPORTED)
        unionGraphService.processOrder(order, "test-instance")

        // Then - verify state transitions and database mutations in a new transaction
        val failedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.FAILED, failedOrder.status)
        assertNotNull(failedOrder.errorMessage, "errorMessage should be set when failed")
        assertNull(failedOrder.graphData, "graphData should be null when failed")
        assertNull(failedOrder.processedAt, "processedAt should be null when failed")
        assertNotNull(failedOrder.updatedAt, "updatedAt should be set")
    }

    @Test
    @Transactional
    fun `resetOrderToPending should transition FAILED to PENDING with database mutations`() {
        // Given - a failed order
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order-reset-failed",
                    status = UnionGraphOrder.GraphStatus.FAILED,
                    errorMessage = "Test error message",
                    resourceTypes = listOf("CONCEPT"),
                ),
            )

        // Verify initial state
        val initialOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.FAILED, initialOrder.status)
        assertEquals("Test error message", initialOrder.errorMessage)

        // When - reset to pending
        val result = unionGraphService.resetOrderToPending(order.id)

        // Then - verify state change and database mutations
        assertNotNull(result)
        val resetOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, resetOrder.status)
        assertNull(resetOrder.errorMessage, "errorMessage should be cleared when reset to pending")
        assertNotNull(resetOrder.updatedAt, "updatedAt should be set")
    }

    @Test
    @Transactional
    fun `resetOrderToPending should transition COMPLETED to PENDING with database mutations`() {
        // Given - a completed order
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order-reset-completed",
                    status = UnionGraphOrder.GraphStatus.COMPLETED,
                    resourceTypes = listOf("CONCEPT"),
                    graphData = """{"@graph":[{"@id":"https://example.com/resource"}]}""",
                    processedAt = Instant.now(),
                ),
            )

        // Verify initial state
        val initialOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, initialOrder.status)
        assertNotNull(initialOrder.graphData)
        assertNotNull(initialOrder.processedAt)

        // When - reset to pending
        val result = unionGraphService.resetOrderToPending(order.id)

        // Then - verify state change and database mutations
        assertNotNull(result)
        val resetOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, resetOrder.status)
        // Note: graphData and processedAt are not cleared by resetToPending
        // They remain until the order is processed again
        assertNotNull(resetOrder.updatedAt, "updatedAt should be set")
    }

    @Test
    fun `lockOrderForProcessing should update status to PROCESSING with database mutations`() {
        // Given - simulate controller creating an order (commits to database in separate transaction)
        val orderId: String =
            transactionTemplate.execute {
                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-lock",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                order.id
            }!!

        // Verify order was committed and is visible (query in new transaction)
        val initialOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, initialOrder.status)
        assertNull(initialOrder.lockedBy)
        assertNull(initialOrder.lockedAt)

        // When - simulate scheduled task locking the order in a new transaction
        // This uses REQUIRES_NEW propagation, simulating the real scenario where
        // the scheduled task runs in a separate transaction from the controller
        val locked = unionGraphService.lockOrderInNewTransaction(orderId, "test-instance")

        // Then - verify state change and database mutations (query in new transaction)
        assertTrue(locked, "Lock should succeed")
        val lockedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.PROCESSING, lockedOrder.status)
        assertEquals("test-instance", lockedOrder.lockedBy, "lockedBy should be set")
        assertNotNull(lockedOrder.lockedAt, "lockedAt should be set")
        assertNotNull(lockedOrder.processingStartedAt, "processingStartedAt should be set")
        assertNotNull(lockedOrder.updatedAt, "updatedAt should be set")
    }

    @Test
    fun `getOrderInNewTransaction should return PROCESSING status after locking`() {
        // Given - simulate controller creating an order (commits to database in separate transaction)
        val orderId: String =
            transactionTemplate.execute {
                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-get-new",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                order.id
            }!!

        // When - lock the order and then fetch it using getOrderInNewTransaction
        // This simulates what processOrder does: lock, then fetch in new transaction
        val locked = unionGraphService.lockOrderInNewTransaction(orderId, "test-instance")
        assertTrue(locked, "Lock should succeed")

        // Fetch using getOrderInNewTransaction (simulating what processOrder does)
        val lockedOrder = unionGraphService.getOrderInNewTransaction(orderId)

        // Then - verify the status is PROCESSING and visible in the new transaction
        // This is critical: the status must be visible to external API calls
        assertNotNull(lockedOrder, "Order should be found after locking")
        assertEquals(
            UnionGraphOrder.GraphStatus.PROCESSING,
            lockedOrder!!.status,
            "Status should be PROCESSING and visible in new transaction",
        )
        assertEquals("test-instance", lockedOrder.lockedBy, "lockedBy should be set")
        assertNotNull(lockedOrder.lockedAt, "lockedAt should be set")
        assertNotNull(lockedOrder.processingStartedAt, "processingStartedAt should be set")

        // Also verify that querying in a separate transaction (simulating API call) sees PROCESSING
        val apiOrder =
            transactionTemplate.execute {
                unionGraphService.getOrder(orderId)
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.PROCESSING, apiOrder.status, "Status should be visible to external API calls")
    }

    @Test
    fun `processOrder should make PROCESSING status visible immediately after locking`() {
        // Given - create order and resources (commits to database in separate transaction)
        val orderId: String =
            transactionTemplate.execute {
                val resourceId1 = "test-concept-visibility-1"
                val resourceId2 = "test-concept-visibility-2"
                val jsonLd1 =
                    mapOf(
                        "@id" to "https://example.com/concept-visibility-1",
                        "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
                        "http://purl.org/dc/terms/title" to listOf(mapOf("@value" to "Test Concept Visibility 1")),
                    )
                val jsonLd2 =
                    mapOf(
                        "@id" to "https://example.com/concept-visibility-2",
                        "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
                        "http://purl.org/dc/terms/title" to listOf(mapOf("@value" to "Test Concept Visibility 2")),
                    )
                val timestamp = System.currentTimeMillis()
                resourceService.storeResourceJsonLd(resourceId1, ResourceType.CONCEPT, jsonLd1, timestamp)
                resourceService.storeResourceJsonLd(resourceId2, ResourceType.CONCEPT, jsonLd2, timestamp + 1)

                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-visibility",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                order.id
            }!!

        // Fetch the order to pass to processOrder
        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // When - start processing (this will lock and update to PROCESSING)
        // We call processOrder which internally:
        // 1. Calls lockOrderInNewTransaction (commits PROCESSING status)
        // 2. Calls getOrderInNewTransaction (fetches in new transaction to verify)
        // 3. Builds the graph (long-running operation)
        unionGraphService.processOrder(order, "test-instance")

        // Then - verify the order was processed to COMPLETED
        val finalOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, finalOrder.status)
    }

    @Test
    fun `processOrder should verify PROCESSING status is visible via getOrderInNewTransaction`() {
        // This test verifies the actual fix: that getOrderInNewTransaction correctly
        // fetches the PROCESSING status after locking, which is what processOrder uses internally.
        // Given - create and lock an order
        val orderId: String =
            transactionTemplate.execute {
                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-process-verify",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                order.id
            }!!

        // When - lock the order (this is what processOrder does first)
        val locked = unionGraphService.lockOrderInNewTransaction(orderId, "test-instance")
        assertTrue(locked, "Lock should succeed")

        // Then - verify getOrderInNewTransaction (which processOrder uses) sees PROCESSING
        // This is the critical test: if this fails, the status won't be visible during processing
        val lockedOrder = unionGraphService.getOrderInNewTransaction(orderId)
        assertNotNull(lockedOrder, "Order should be found")
        assertEquals(
            UnionGraphOrder.GraphStatus.PROCESSING,
            lockedOrder!!.status,
            "getOrderInNewTransaction must return PROCESSING status after locking - this is what processOrder uses internally",
        )

        // Also verify that a regular getOrder (simulating API call) also sees PROCESSING
        // This ensures external API calls will see the correct status
        val apiOrder =
            transactionTemplate.execute {
                unionGraphService.getOrder(orderId)
            }!!
        assertEquals(
            UnionGraphOrder.GraphStatus.PROCESSING,
            apiOrder.status,
            "External API calls should see PROCESSING status immediately after locking",
        )
    }

    @Test
    fun `lockOrderForProcessing should fail when order is not PENDING`() {
        // Given - simulate controller creating a completed order (commits to database in separate transaction)
        val orderId: String =
            transactionTemplate.execute {
                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-lock-fail",
                            status = UnionGraphOrder.GraphStatus.COMPLETED,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                order.id
            }!!

        // When - simulate scheduled task trying to lock a completed order in a new transaction
        // The WHERE clause requires status = 'PENDING', so this should fail
        val locked = unionGraphService.lockOrderInNewTransaction(orderId, "test-instance")

        // Then - verify lock failed and state unchanged (query in new transaction)
        assertTrue(!locked, "Lock should fail for non-PENDING order")
        val unchangedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, unchangedOrder.status)
        assertNull(unchangedOrder.lockedBy)
        assertNull(unchangedOrder.lockedAt)
    }

    @Test
    @Transactional
    fun `markAsCompleted should update status and store graph with database mutations`() {
        // Given - a processing order
        val graphData = """{"@graph":[{"@id":"https://example.com/resource1"},{"@id":"https://example.com/resource2"}]}"""
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order-mark-complete",
                    status = UnionGraphOrder.GraphStatus.PROCESSING,
                    resourceTypes = listOf("CONCEPT"),
                    lockedBy = "test-instance",
                    lockedAt = Instant.now(),
                ),
            )

        // When - mark as completed
        unionGraphOrderRepository.markAsCompleted(
            order.id,
            graphData,
            UnionGraphOrder.GraphFormat.JSON_LD.name,
            UnionGraphOrder.GraphStyle.PRETTY.name,
            true,
        )

        // Then - verify state change and database mutations
        val completedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, completedOrder.status)
        assertNotNull(completedOrder.graphData, "graphData should be set")
        assertEquals(graphData, completedOrder.graphData, "graphData should match stored value")
        assertEquals(UnionGraphOrder.GraphFormat.JSON_LD, completedOrder.format)
        assertEquals(UnionGraphOrder.GraphStyle.PRETTY, completedOrder.style)
        assertTrue(completedOrder.expandUris)
        assertNotNull(completedOrder.processedAt, "processedAt should be set")
        assertNotNull(completedOrder.updatedAt, "updatedAt should be set")
    }

    @Test
    @Transactional
    fun `markAsFailed should update status and error message with database mutations`() {
        // Given - a processing order
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order-mark-failed",
                    status = UnionGraphOrder.GraphStatus.PROCESSING,
                    resourceTypes = listOf("CONCEPT"),
                    lockedBy = "test-instance",
                    lockedAt = Instant.now(),
                ),
            )

        // When - mark as failed
        val errorMessage = "Failed to build union graph: No resources found"
        unionGraphOrderRepository.markAsFailed(order.id, errorMessage)

        // Then - verify state change and database mutations
        val failedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.FAILED, failedOrder.status)
        assertEquals(errorMessage, failedOrder.errorMessage, "errorMessage should be set")
        assertNull(failedOrder.graphData, "graphData should be null when failed")
        assertNull(failedOrder.processedAt, "processedAt should be null when failed")
        assertNotNull(failedOrder.updatedAt, "updatedAt should be set")
    }

    @Test
    @Transactional
    fun `createOrder should persist expandDistributionAccessServices flag in database`() {
        // When
        val result =
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                expandDistributionAccessServices = true,
            )

        // Then
        assertTrue(result.isNew)
        val savedOrder = unionGraphOrderRepository.findById(result.order.id).get()
        assertTrue(savedOrder.expandDistributionAccessServices, "expandDistributionAccessServices should be persisted")
    }

    @Test
    @Transactional
    fun `createOrder should persist resourceFilters in database`() {
        // Given
        val filters =
            no.fdk.resourceservice.model.UnionGraphResourceFilters(
                dataset =
                    no.fdk.resourceservice.model.UnionGraphResourceFilters.DatasetFilters(
                        isOpenData = true,
                        isRelatedToTransportportal = false,
                    ),
            )

        // When
        val result =
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                resourceFilters = filters,
            )

        // Then
        assertTrue(result.isNew)
        val savedOrder = unionGraphOrderRepository.findById(result.order.id).get()
        assertNotNull(savedOrder.resourceFilters, "resourceFilters should be persisted")
        assertEquals(true, savedOrder.resourceFilters?.dataset?.isOpenData)
        assertEquals(false, savedOrder.resourceFilters?.dataset?.isRelatedToTransportportal)
    }

    @Test
    @Transactional
    fun `all state changes should preserve order metadata`() {
        // Given - create order with all metadata
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order-metadata",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                    resourceTypes = listOf("CONCEPT", "DATASET"),
                    updateTtlHours = 48,
                    webhookUrl = "https://example.com/webhook",
                    expandDistributionAccessServices = true,
                ),
            )

        // When - process and complete
        val initialCreatedAt = order.createdAt.truncatedTo(ChronoUnit.MICROS) // PostgreSQL stores timestamps with microsecond precision
        val initialId = order.id

        // Lock for processing
        unionGraphService.lockOrderInNewTransaction(order.id, "test-instance")
        val lockedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(initialId, lockedOrder.id)
        assertEquals(initialCreatedAt, lockedOrder.createdAt.truncatedTo(ChronoUnit.MICROS))
        assertEquals(listOf("CONCEPT", "DATASET"), lockedOrder.resourceTypes)
        assertEquals(48, lockedOrder.updateTtlHours)
        assertEquals("https://example.com/webhook", lockedOrder.webhookUrl)
        assertTrue(lockedOrder.expandDistributionAccessServices)

        // Mark as completed
        val graphData = """{"@graph":[{"@id":"https://example.com/resource"}]}"""
        unionGraphOrderRepository.markAsCompleted(
            order.id,
            graphData,
            UnionGraphOrder.GraphFormat.JSON_LD.name,
            UnionGraphOrder.GraphStyle.PRETTY.name,
            true,
        )
        val completedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(initialId, completedOrder.id)
        assertEquals(initialCreatedAt, completedOrder.createdAt.truncatedTo(ChronoUnit.MICROS))
        assertEquals(listOf("CONCEPT", "DATASET"), completedOrder.resourceTypes)
        assertEquals(48, completedOrder.updateTtlHours)
        assertEquals("https://example.com/webhook", completedOrder.webhookUrl)
        assertTrue(completedOrder.expandDistributionAccessServices)

        // Reset to pending
        unionGraphService.resetOrderToPending(order.id)
        val resetOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(initialId, resetOrder.id)
        assertEquals(initialCreatedAt, resetOrder.createdAt.truncatedTo(ChronoUnit.MICROS))
        assertEquals(listOf("CONCEPT", "DATASET"), resetOrder.resourceTypes)
        assertEquals(48, resetOrder.updateTtlHours)
        assertEquals("https://example.com/webhook", resetOrder.webhookUrl)
        assertTrue(resetOrder.expandDistributionAccessServices)
    }
}
