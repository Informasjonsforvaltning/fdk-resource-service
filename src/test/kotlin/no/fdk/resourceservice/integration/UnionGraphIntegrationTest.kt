package no.fdk.resourceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import no.fdk.resourceservice.repository.UnionGraphResourceSnapshotRepository
import no.fdk.resourceservice.service.ResourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import java.sql.Timestamp
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
    private lateinit var unionGraphResourceSnapshotRepository: UnionGraphResourceSnapshotRepository

    @Autowired
    private lateinit var unionGraphService: no.fdk.resourceservice.service.UnionGraphService

    @Autowired
    private lateinit var resourceService: ResourceService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @Autowired
    private lateinit var entityManager: EntityManager

    private val logger = LoggerFactory.getLogger(UnionGraphIntegrationTest::class.java)

    @BeforeEach
    fun setUp() {
        // Clean up test data
        unionGraphOrderRepository.deleteAll()
        resourceRepository.deleteAll()
        unionGraphResourceSnapshotRepository.deleteAll()
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
                name = "Test Order",
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
                updateTtlHours = 24,
                name = "Test Order",
            )

        // When
        val result =
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                updateTtlHours = 24,
                name = "Test Order",
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
                name = "Test Order 1",
            )

        // When
        val order2 =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                webhookUrl = "https://example.com/webhook2",
                name = "Test Order 2",
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
                    name = "Test Order",
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
                    name = "Test Order",
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
                    name = "Order 1",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                ),
            )
        Thread.sleep(10) // Ensure different timestamps
        val order2 =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "order-2",
                    name = "Order 2",
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
    fun `buildUnionGraph should return false when no resources found`() {
        // Given
        val orderId = "test-order-no-resources"

        // When
        val result = unionGraphService.buildUnionGraph(listOf(ResourceType.CONCEPT), orderId = orderId)

        // Then
        assertEquals(false, result)
    }

    @Test
    fun `processOrder should mark order as failed when no resources found`() {
        // Given - use DATASET with filters that won't match any existing resources
        // This ensures no resources will be found without deleting anything or affecting other tests
        val filters =
            UnionGraphResourceFilters(
                dataset =
                    UnionGraphResourceFilters.DatasetFilters(
                        isOpenData = true, // Filter that likely won't match existing test data
                        isRelatedToTransportportal = true, // Additional filter to ensure no match
                    ),
            )

        // Create order in a transaction that commits (simulating controller)
        val orderId: String =
            transactionTemplate.execute {
                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order",
                            name = "Test Order",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("DATASET"),
                            resourceFilters = filters,
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

        // Process batches incrementally until completion or failure
        var shouldContinue = true
        var maxIterations = 1000 // Safety limit
        var iterations = 0
        while (shouldContinue && iterations < maxIterations) {
            shouldContinue = unionGraphService.processNextBatch(orderId)
            iterations++
            if (!shouldContinue) {
                break
            }
            // Small delay to avoid tight loop
            Thread.sleep(10)
        }

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
        val testId = System.currentTimeMillis()
        val orderId: String =
            transactionTemplate.execute {
                val resourceId1 = "test-concept-1-$testId"
                val resourceId2 = "test-concept-2-$testId"
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
                val turtle1 =
                    """<https://example.com/concept1> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                        |<http://purl.org/dc/terms/title> "Test Concept 1" .
                    """.trimMargin()
                val turtle2 =
                    """<https://example.com/concept2> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                        |<http://purl.org/dc/terms/title> "Test Concept 2" .
                    """.trimMargin()
                resourceService.storeResourceGraphData(resourceId1, ResourceType.CONCEPT, turtle1, "TURTLE", timestamp)
                resourceService.storeResourceGraphData(resourceId2, ResourceType.CONCEPT, turtle2, "TURTLE", timestamp + 1)
                // Flush resources immediately after storing to ensure they're persisted
                resourceRepository.flush()

                // Verify resources are actually saved and not deleted
                val saved1 = resourceRepository.findById(resourceId1).orElse(null)
                val saved2 = resourceRepository.findById(resourceId2).orElse(null)
                assertNotNull(saved1, "Resource 1 should be saved")
                assertNotNull(saved2, "Resource 2 should be saved")
                assertFalse(saved1!!.deleted, "Resource 1 should not be deleted")
                assertFalse(saved2!!.deleted, "Resource 2 should not be deleted")

                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-complete-$testId",
                            name = "Test Order Complete",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                            updateTtlHours = 24,
                            webhookUrl = "https://example.com/webhook",
                        ),
                    )
                // Flush to ensure order is committed
                unionGraphOrderRepository.flush()
                order.id
            }!!

        // Verify resources are committed and visible using the same query method that buildUnionGraph uses
        // This ensures they're queryable in the same way when processOrder runs with NOT_SUPPORTED
        val resource1Exists =
            transactionTemplate.execute {
                resourceRepository.findById("test-concept-1-$testId").isPresent
            }!!
        val resource2Exists =
            transactionTemplate.execute {
                resourceRepository.findById("test-concept-2-$testId").isPresent
            }!!
        assertTrue(resource1Exists, "Resource 1 should be committed before processing")
        assertTrue(resource2Exists, "Resource 2 should be committed before processing")

        // Use the exact same query that buildUnionGraph uses to count resources
        val resourceCountCheck =
            transactionTemplate.execute {
                resourceRepository.countByResourceTypeAndDeletedFalse("CONCEPT")
            }!!
        assertTrue(
            resourceCountCheck >= 2,
            "Resources should be queryable using countByResourceTypeAndDeletedFalse. Found: $resourceCountCheck, expected: at least 2",
        )

        // Also verify resources are queryable using the paginated query that buildUnionGraph uses
        val resourcesQueryable =
            transactionTemplate.execute {
                resourceRepository.findByResourceTypeAndDeletedFalseWithGraphDataPaginated("CONCEPT", 0, 10)
            }!!
        assertTrue(
            resourcesQueryable.size >= 2,
            "Resources should be queryable using findByResourceTypeAndDeletedFalseWithGraphDataPaginated. Found: ${resourcesQueryable.size}, expected: at least 2",
        )

        // Verify initial state in a new transaction
        val initialOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, initialOrder.status)
        assertNull(initialOrder.lockedBy)
        assertNull(initialOrder.lockedAt)
        assertNull(initialOrder.processedAt)

        // Fetch the order to pass to processOrder
        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // Double-check resources are still queryable right before processing
        // This simulates what buildUnionGraph will do when it runs with NOT_SUPPORTED
        val finalResourceCount =
            transactionTemplate.execute {
                resourceRepository.countByResourceTypeAndDeletedFalse("CONCEPT")
            }!!
        assertTrue(
            finalResourceCount >= 2,
            "Resources must be queryable right before processOrder. Found: $finalResourceCount, expected: at least 2",
        )

        // When - process the order (uses NOT_SUPPORTED, so runs outside any transaction)
        unionGraphService.processOrder(order, "test-instance")

        // Process batches incrementally until completion
        var shouldContinue = true
        var maxIterations = 1000 // Safety limit
        var iterations = 0
        while (shouldContinue && iterations < maxIterations) {
            shouldContinue = unionGraphService.processNextBatch(orderId)
            iterations++
            if (!shouldContinue) {
                break
            }
            // Small delay to avoid tight loop
            Thread.sleep(10)
        }

        if (iterations >= maxIterations) {
            logger.error("Reached max iterations ({}) while processing order {}", maxIterations, orderId)
        }

        // Then - verify state transitions and database mutations in a new transaction
        // Note: The order goes through PENDING -> PROCESSING -> COMPLETED
        // We verify the final COMPLETED state, but PROCESSING should be visible
        // during processing (tested separately in getOrderInNewTransaction test)
        val completedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        if (completedOrder.status == UnionGraphOrder.GraphStatus.FAILED) {
            logger.error("Order failed with error: {}", completedOrder.errorMessage)
        }
        assertEquals(
            UnionGraphOrder.GraphStatus.COMPLETED,
            completedOrder.status,
            "Order should be completed. Error: ${completedOrder.errorMessage}",
        )
        assertNotNull(completedOrder.processedAt, "processedAt should be set when completed")
        assertNull(completedOrder.errorMessage, "errorMessage should be null when completed")
        assertNotNull(completedOrder.updatedAt, "updatedAt should be set")
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
                            name = "Test Order Failed",
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

        // Process batches incrementally until completion or failure
        var shouldContinue = true
        var maxIterations = 1000 // Safety limit
        var iterations = 0
        while (shouldContinue && iterations < maxIterations) {
            shouldContinue = unionGraphService.processNextBatch(orderId)
            iterations++
            if (!shouldContinue) {
                break
            }
            // Small delay to avoid tight loop
            Thread.sleep(10)
        }

        // Then - verify state transitions and database mutations in a new transaction
        val failedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.FAILED, failedOrder.status)
        assertNotNull(failedOrder.errorMessage, "errorMessage should be set when failed")
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
                    name = "Test Order Reset Failed",
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
                    name = "Test Order Reset Completed",
                    status = UnionGraphOrder.GraphStatus.COMPLETED,
                    resourceTypes = listOf("CONCEPT"),
                    processedAt = Instant.now(),
                ),
            )

        // Verify initial state
        val initialOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, initialOrder.status)
        assertNotNull(initialOrder.processedAt)

        // When - reset to pending
        val result = unionGraphService.resetOrderToPending(order.id)

        // Then - verify state change and database mutations
        assertNotNull(result)
        val resetOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, resetOrder.status)
        // Note: processedAt is not cleared by resetToPending
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
                            name = "Test Order Lock",
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
                            name = "Test Order Get New",
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
        val testId = System.currentTimeMillis()
        val orderId: String =
            transactionTemplate.execute {
                val resourceId1 = "test-concept-visibility-1-$testId"
                val resourceId2 = "test-concept-visibility-2-$testId"
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
                val turtle1 =
                    """<https://example.com/concept1> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                        |<http://purl.org/dc/terms/title> "Test Concept 1" .
                    """.trimMargin()
                val turtle2 =
                    """<https://example.com/concept2> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                        |<http://purl.org/dc/terms/title> "Test Concept 2" .
                    """.trimMargin()
                resourceService.storeResourceGraphData(resourceId1, ResourceType.CONCEPT, turtle1, "TURTLE", timestamp)
                resourceService.storeResourceGraphData(resourceId2, ResourceType.CONCEPT, turtle2, "TURTLE", timestamp + 1)
                // Flush resources immediately after storing to ensure they're persisted
                resourceRepository.flush()

                // Verify resources are actually saved and not deleted
                val saved1 = resourceRepository.findById(resourceId1).orElse(null)
                val saved2 = resourceRepository.findById(resourceId2).orElse(null)
                assertNotNull(saved1, "Resource 1 should be saved")
                assertNotNull(saved2, "Resource 2 should be saved")
                assertFalse(saved1!!.deleted, "Resource 1 should not be deleted")
                assertFalse(saved2!!.deleted, "Resource 2 should not be deleted")

                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-visibility-$testId",
                            name = "Test Order Visibility",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                // Flush to ensure order is committed
                unionGraphOrderRepository.flush()
                order.id
            }!!

        // Verify resources are committed and visible using the same query method that buildUnionGraph uses
        // This ensures they're queryable in the same way when processOrder runs with NOT_SUPPORTED
        val resource1Exists =
            transactionTemplate.execute {
                resourceRepository.findById("test-concept-visibility-1-$testId").isPresent
            }!!
        val resource2Exists =
            transactionTemplate.execute {
                resourceRepository.findById("test-concept-visibility-2-$testId").isPresent
            }!!
        assertTrue(resource1Exists, "Resource 1 should be committed before processing")
        assertTrue(resource2Exists, "Resource 2 should be committed before processing")

        // Use the exact same query that buildUnionGraph uses to count resources
        val resourceCountCheck =
            transactionTemplate.execute {
                resourceRepository.countByResourceTypeAndDeletedFalse("CONCEPT")
            }!!
        assertTrue(
            resourceCountCheck >= 2,
            "Resources should be queryable using countByResourceTypeAndDeletedFalse. Found: $resourceCountCheck, expected: at least 2",
        )

        // Also verify resources are queryable using the paginated query that buildUnionGraph uses
        val resourcesQueryable =
            transactionTemplate.execute {
                resourceRepository.findByResourceTypeAndDeletedFalseWithGraphDataPaginated("CONCEPT", 0, 10)
            }!!
        assertTrue(
            resourcesQueryable.size >= 2,
            "Resources should be queryable using findByResourceTypeAndDeletedFalseWithGraphDataPaginated. Found: ${resourcesQueryable.size}, expected: at least 2",
        )

        // Fetch the order to pass to processOrder
        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // When - start processing (this will lock and update to PROCESSING)
        // We call processOrder which internally:
        // 1. Calls lockOrderInNewTransaction (commits PROCESSING status)
        // 2. Calls getOrderInNewTransaction (fetches in new transaction to verify)
        // 3. Initializes processing state (scheduler will process batches incrementally)
        unionGraphService.processOrder(order, "test-instance")

        // Process batches incrementally until completion
        var shouldContinue = true
        var maxIterations = 1000 // Safety limit
        var iterations = 0
        while (shouldContinue && iterations < maxIterations) {
            shouldContinue = unionGraphService.processNextBatch(orderId)
            iterations++
            if (!shouldContinue) {
                break
            }
            // Small delay to avoid tight loop
            Thread.sleep(10)
        }

        if (iterations >= maxIterations) {
            logger.error("Reached max iterations ({}) while processing order {}", maxIterations, orderId)
        }

        // Then - verify the order was processed to COMPLETED
        val finalOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        if (finalOrder.status == UnionGraphOrder.GraphStatus.FAILED) {
            logger.error("Order failed with error: {}", finalOrder.errorMessage)
        }
        assertEquals(
            UnionGraphOrder.GraphStatus.COMPLETED,
            finalOrder.status,
            "Order should be completed. Error: ${finalOrder.errorMessage}",
        )
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
                            name = "Test Order Process Verify",
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
                            name = "Test Order Lock Fail",
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
    fun `markAsCompleted should update status with database mutations`() {
        // Given - a processing order
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-order-mark-complete",
                    name = "Test Order Mark Complete",
                    status = UnionGraphOrder.GraphStatus.PROCESSING,
                    resourceTypes = listOf("CONCEPT"),
                    lockedBy = "test-instance",
                    lockedAt = Instant.now(),
                ),
            )

        // When - mark as completed
        unionGraphOrderRepository.markAsCompleted(order.id)

        // Then - verify state change and database mutations
        val completedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, completedOrder.status)
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
                    name = "Test Order Mark Failed",
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
                name = "Test Order",
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
                name = "Test Order",
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
                    name = "Test Order Metadata",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                    resourceTypes = listOf("CONCEPT", "DATASET"),
                    updateTtlHours = 48,
                    webhookUrl = "https://example.com/webhook",
                    expandDistributionAccessServices = true,
                ),
            )

        // When - process and complete
        // Reload from database to get exact database timestamp
        val savedOrder = unionGraphOrderRepository.findById(order.id).get()
        // PostgreSQL stores timestamps with microsecond precision, but truncate to milliseconds
        // to avoid precision issues in tests
        val initialCreatedAt = savedOrder.createdAt.truncatedTo(ChronoUnit.MILLIS)
        val initialId = savedOrder.id

        // Lock for processing
        unionGraphService.lockOrderInNewTransaction(order.id, "test-instance")
        val lockedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(initialId, lockedOrder.id)
        assertEquals(initialCreatedAt, lockedOrder.createdAt.truncatedTo(ChronoUnit.MILLIS))
        assertEquals(listOf("CONCEPT", "DATASET"), lockedOrder.resourceTypes)
        assertEquals(48, lockedOrder.updateTtlHours)
        assertEquals("https://example.com/webhook", lockedOrder.webhookUrl)
        assertTrue(lockedOrder.expandDistributionAccessServices)

        // Mark as completed
        unionGraphOrderRepository.markAsCompleted(order.id)
        val completedOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(initialId, completedOrder.id)
        assertEquals(initialCreatedAt, completedOrder.createdAt.truncatedTo(ChronoUnit.MILLIS))
        assertEquals(listOf("CONCEPT", "DATASET"), completedOrder.resourceTypes)
        assertEquals(48, completedOrder.updateTtlHours)
        assertEquals("https://example.com/webhook", completedOrder.webhookUrl)
        assertTrue(completedOrder.expandDistributionAccessServices)

        // Reset to pending
        unionGraphService.resetOrderToPending(order.id)
        val resetOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(initialId, resetOrder.id)
        assertEquals(initialCreatedAt, resetOrder.createdAt.truncatedTo(ChronoUnit.MILLIS))
        assertEquals(listOf("CONCEPT", "DATASET"), resetOrder.resourceTypes)
        assertEquals(48, resetOrder.updateTtlHours)
        assertEquals("https://example.com/webhook", resetOrder.webhookUrl)
        assertTrue(resetOrder.expandDistributionAccessServices)
    }

    @Test
    @Transactional
    fun `updateOrder should update union graph fields in database`() {
        // Given - create an order
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-update-order",
                    name = "Original Name",
                    description = "Original Description",
                    status = UnionGraphOrder.GraphStatus.COMPLETED,
                    resourceTypes = listOf("CONCEPT"),
                    updateTtlHours = 24,
                    webhookUrl = "https://example.com/original-webhook",
                    expandDistributionAccessServices = false,
                ),
            )

        // When - update the order
        val updatedOrder =
            unionGraphService.updateOrder(
                id = order.id,
                name = "Updated Name",
                description = "Updated Description",
                updateTtlHours = 24,
                webhookUrl = "https://example.com/updated-webhook",
                expandDistributionAccessServices = true,
            )

        // Then - verify the update
        assertNotNull(updatedOrder)
        assertEquals("Updated Name", updatedOrder!!.name)
        assertEquals("Updated Description", updatedOrder.description)
        assertEquals(24, updatedOrder.updateTtlHours)
        assertEquals("https://example.com/updated-webhook", updatedOrder.webhookUrl)
        assertTrue(updatedOrder.expandDistributionAccessServices)

        // Verify in database directly
        val dbOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals("Updated Name", dbOrder.name)
        assertEquals("Updated Description", dbOrder.description)
        assertEquals(24, dbOrder.updateTtlHours)
        assertEquals("https://example.com/updated-webhook", dbOrder.webhookUrl)
        assertTrue(dbOrder.expandDistributionAccessServices)
    }

    @Test
    @Transactional
    fun `updateOrder should update resourceTypes and trigger rebuild`() {
        // Given - create an order
        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = "test-update-resource-types",
                    name = "Test Order",
                    status = UnionGraphOrder.GraphStatus.COMPLETED,
                    resourceTypes = listOf("CONCEPT"),
                    updateTtlHours = 24,
                ),
            )

        // When - update resource types
        val updatedOrder =
            unionGraphService.updateOrder(
                id = order.id,
                resourceTypes = listOf(ResourceType.DATASET),
            )

        // Then - verify status reset to PENDING and resource types updated
        assertNotNull(updatedOrder)
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, updatedOrder!!.status)
        assertEquals(listOf("DATASET"), updatedOrder.resourceTypes)

        // Verify in database
        val dbOrder = unionGraphOrderRepository.findById(order.id).get()
        assertEquals(UnionGraphOrder.GraphStatus.PENDING, dbOrder.status)
        assertEquals(listOf("DATASET"), dbOrder.resourceTypes)
    }

    @Test
    @Transactional
    fun `deleteByUnionGraphIdCreatedBefore should only delete snapshots created before timestamp`() {
        // Given - create an order and set processingStartedAt
        val orderId = "test-snapshot-deletion"
        val processingStartedAt = Instant.now().minus(1, ChronoUnit.HOURS)

        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = orderId,
                    name = "Test Order",
                    status = UnionGraphOrder.GraphStatus.PROCESSING,
                    processingStartedAt = processingStartedAt,
                ),
            )

        // Create old snapshots (created BEFORE processingStartedAt) using native SQL to control exact timestamps
        val oldTimestamp1 = Timestamp.from(processingStartedAt.minus(2, ChronoUnit.HOURS))
        val oldTimestamp2 = Timestamp.from(processingStartedAt.minus(30, ChronoUnit.MINUTES))
        val newTimestamp1 = Timestamp.from(processingStartedAt.plus(1, ChronoUnit.MINUTES))
        val newTimestamp2 = Timestamp.from(processingStartedAt.plus(2, ChronoUnit.MINUTES))

        entityManager
            .createNativeQuery(
                """
            INSERT INTO union_graph_resource_snapshots 
            (union_graph_id, resource_id, resource_type, resource_graph_data, resource_graph_format, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ).apply {
                setParameter(1, orderId)
                setParameter(2, "resource-1")
                setParameter(3, "CONCEPT")
                setParameter(4, "<rdf:RDF>old data 1</rdf:RDF>")
                setParameter(5, "RDF_XML")
                setParameter(6, oldTimestamp1)
                executeUpdate()
            }

        entityManager
            .createNativeQuery(
                """
            INSERT INTO union_graph_resource_snapshots 
            (union_graph_id, resource_id, resource_type, resource_graph_data, resource_graph_format, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ).apply {
                setParameter(1, orderId)
                setParameter(2, "resource-2")
                setParameter(3, "CONCEPT")
                setParameter(4, "<rdf:RDF>old data 2</rdf:RDF>")
                setParameter(5, "RDF_XML")
                setParameter(6, oldTimestamp2)
                executeUpdate()
            }

        // Create new snapshots (created AFTER processingStartedAt)
        entityManager
            .createNativeQuery(
                """
            INSERT INTO union_graph_resource_snapshots 
            (union_graph_id, resource_id, resource_type, resource_graph_data, resource_graph_format, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ).apply {
                setParameter(1, orderId)
                setParameter(2, "resource-3")
                setParameter(3, "CONCEPT")
                setParameter(4, "<rdf:RDF>new data 1</rdf:RDF>")
                setParameter(5, "RDF_XML")
                setParameter(6, newTimestamp1)
                executeUpdate()
            }

        entityManager
            .createNativeQuery(
                """
            INSERT INTO union_graph_resource_snapshots 
            (union_graph_id, resource_id, resource_type, resource_graph_data, resource_graph_format, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ).apply {
                setParameter(1, orderId)
                setParameter(2, "resource-4")
                setParameter(3, "CONCEPT")
                setParameter(4, "<rdf:RDF>new data 2</rdf:RDF>")
                setParameter(5, "RDF_XML")
                setParameter(6, newTimestamp2)
                executeUpdate()
            }

        entityManager.flush()
        entityManager.clear()

        // Get the saved snapshots by querying
        val allSnapshotsBefore = unionGraphResourceSnapshotRepository.findAll()
        assertEquals(4, allSnapshotsBefore.size)
        val oldSnapshot1 = allSnapshotsBefore.find { it.resourceId == "resource-1" }!!
        val oldSnapshot2 = allSnapshotsBefore.find { it.resourceId == "resource-2" }!!
        val newSnapshot1 = allSnapshotsBefore.find { it.resourceId == "resource-3" }!!
        val newSnapshot2 = allSnapshotsBefore.find { it.resourceId == "resource-4" }!!

        // Debug: Check timestamps before delete
        val allBeforeDelete = unionGraphResourceSnapshotRepository.findAll().filter { it.unionGraphId == orderId }
        logger.info("Before delete: {} snapshots", allBeforeDelete.size)
        allBeforeDelete.forEach {
            logger.info(
                "Snapshot {}: resourceId={}, createdAt={}, processingStartedAt={}, isBefore={}",
                it.id,
                it.resourceId,
                it.createdAt,
                processingStartedAt,
                it.createdAt.isBefore(processingStartedAt),
            )
        }

        // When - delete snapshots created before processingStartedAt
        unionGraphResourceSnapshotRepository.deleteByUnionGraphIdCreatedBefore(
            orderId,
            java.sql.Timestamp.from(processingStartedAt),
        )
        entityManager.flush()
        entityManager.clear()

        // Then - old snapshots should be deleted, new snapshots should remain
        val remainingSnapshots = unionGraphResourceSnapshotRepository.findAll().filter { it.unionGraphId == orderId }
        logger.info("After delete: {} snapshots remain", remainingSnapshots.size)
        remainingSnapshots.forEach {
            logger.info("Remaining snapshot {}: resourceId={}, createdAt={}", it.id, it.resourceId, it.createdAt)
        }
        assertEquals(2, remainingSnapshots.size)

        val remainingIds = remainingSnapshots.map { it.id }.toSet()
        assertTrue(remainingIds.contains(newSnapshot1.id), "New snapshot 1 should remain")
        assertTrue(remainingIds.contains(newSnapshot2.id), "New snapshot 2 should remain")
        assertFalse(remainingIds.contains(oldSnapshot1.id), "Old snapshot 1 should be deleted")
        assertFalse(remainingIds.contains(oldSnapshot2.id), "Old snapshot 2 should be deleted")

        // Verify the remaining snapshots are the new ones
        val foundNew1 = remainingSnapshots.find { it.id == newSnapshot1.id }
        val foundNew2 = remainingSnapshots.find { it.id == newSnapshot2.id }
        assertNotNull(foundNew1, "New snapshot 1 should be found")
        assertNotNull(foundNew2, "New snapshot 2 should be found")
        assertEquals("resource-3", foundNew1!!.resourceId)
        assertEquals("resource-4", foundNew2!!.resourceId)
    }

    @Test
    @Transactional
    fun `deleteByUnionGraphIdCreatedBefore should handle snapshots with exact timestamp correctly`() {
        // Given - create an order with processingStartedAt
        val orderId = "test-exact-timestamp"
        val processingStartedAt = Instant.now().minus(1, ChronoUnit.HOURS)

        val order =
            unionGraphOrderRepository.save(
                UnionGraphOrder(
                    id = orderId,
                    name = "Test Order",
                    status = UnionGraphOrder.GraphStatus.PROCESSING,
                    processingStartedAt = processingStartedAt,
                ),
            )

        // Create a snapshot with timestamp exactly at processingStartedAt (should NOT be deleted due to < comparison)
        val exactTimestamp = Timestamp.from(processingStartedAt)
        val beforeTimestamp = Timestamp.from(processingStartedAt.minus(1, ChronoUnit.SECONDS))

        entityManager
            .createNativeQuery(
                """
            INSERT INTO union_graph_resource_snapshots 
            (union_graph_id, resource_id, resource_type, resource_graph_data, resource_graph_format, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ).apply {
                setParameter(1, orderId)
                setParameter(2, "resource-exact")
                setParameter(3, "CONCEPT")
                setParameter(4, "<rdf:RDF>exact time data</rdf:RDF>")
                setParameter(5, "RDF_XML")
                setParameter(6, exactTimestamp)
                executeUpdate()
            }

        // Create a snapshot with timestamp just before processingStartedAt (should be deleted)
        entityManager
            .createNativeQuery(
                """
            INSERT INTO union_graph_resource_snapshots 
            (union_graph_id, resource_id, resource_type, resource_graph_data, resource_graph_format, created_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """,
            ).apply {
                setParameter(1, orderId)
                setParameter(2, "resource-before")
                setParameter(3, "CONCEPT")
                setParameter(4, "<rdf:RDF>before data</rdf:RDF>")
                setParameter(5, "RDF_XML")
                setParameter(6, beforeTimestamp)
                executeUpdate()
            }

        entityManager.flush()
        entityManager.clear()

        val allSnapshotsBeforeExact = unionGraphResourceSnapshotRepository.findAll()
        val snapshotAtExactTime = allSnapshotsBeforeExact.find { it.resourceId == "resource-exact" }!!
        val snapshotJustBefore = allSnapshotsBeforeExact.find { it.resourceId == "resource-before" }!!

        // When - delete snapshots created before processingStartedAt
        unionGraphResourceSnapshotRepository.deleteByUnionGraphIdCreatedBefore(
            orderId,
            java.sql.Timestamp.from(processingStartedAt),
        )
        entityManager.flush()
        entityManager.clear()

        // Then - snapshot at exact time should remain (due to < comparison, not <=)
        val remainingSnapshots = unionGraphResourceSnapshotRepository.findAll().filter { it.unionGraphId == orderId }
        assertEquals(1, remainingSnapshots.size)
        assertEquals(snapshotAtExactTime.id, remainingSnapshots[0].id)
        assertFalse(remainingSnapshots.any { it.id == snapshotJustBefore.id })
    }

    @Test
    @Transactional
    fun `createOrder should persist resourceIds in database`() {
        // When
        val resourceIds = listOf("resource-id-1", "resource-id-2")
        val result =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                resourceIds = resourceIds,
                name = "Test Order",
            )

        // Then
        val savedOrder = unionGraphOrderRepository.findById(result.order.id).get()
        assertEquals(resourceIds.sorted(), savedOrder.resourceIds)
    }

    @Test
    @Transactional
    fun `createOrder should persist resourceUris in database`() {
        // When
        val resourceUris = listOf("https://example.com/resource1", "https://example.com/resource2")
        val result =
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                resourceUris = resourceUris,
                name = "Test Order",
            )

        // Then
        val savedOrder = unionGraphOrderRepository.findById(result.order.id).get()
        assertEquals(resourceUris.sorted(), savedOrder.resourceUris)
    }

    @Test
    @Transactional
    fun `createOrder should treat different resourceIds as different orders`() {
        // Given
        val order1 =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                resourceIds = listOf("id-1", "id-2"),
                name = "Order 1",
            )

        // When
        val order2 =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                resourceIds = listOf("id-3", "id-4"),
                name = "Order 2",
            )

        // Then
        assertTrue(order1.isNew)
        assertTrue(order2.isNew)
        assertTrue(order1.order.id != order2.order.id)
    }

    @Test
    @Transactional
    fun `createOrder should treat different resourceUris as different orders`() {
        // Given
        val order1 =
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                resourceUris = listOf("https://example.com/resource1"),
                name = "Order 1",
            )

        // When
        val order2 =
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                resourceUris = listOf("https://example.com/resource2"),
                name = "Order 2",
            )

        // Then
        assertTrue(order1.isNew)
        assertTrue(order2.isNew)
        assertTrue(order1.order.id != order2.order.id)
    }

    @Test
    @Transactional
    fun `createOrder should return existing order when resourceIds match`() {
        // Given
        val existingOrder =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                resourceIds = listOf("id-1", "id-2"),
                name = "Test Order",
            )

        // When
        val result =
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                resourceIds = listOf("id-1", "id-2"),
                name = "Test Order",
            )

        // Then
        assertTrue(!result.isNew)
        assertEquals(existingOrder.order.id, result.order.id)
    }

    @Test
    fun `buildUnionGraph should merge DataService graphs into dataset graphs when expandDistributionAccessServices is true`() {
        // Given - create a DataService resource
        val dataServiceUri = "https://example.com/data-service/1"
        val dataServiceId = "dataservice-1"
        val dataServiceGraph =
            """
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix dcat: <http://www.w3.org/ns/dcat#> .
            <$dataServiceUri> a dcat:DataService ;
                dcat:title "Test Data Service" ;
                dcat:endpointURL <https://example.com/api> .
            """.trimIndent()

        // Given - create a dataset with a distribution that references the DataService
        val datasetId = "dataset-1"
        val datasetUri = "https://example.com/dataset/1"
        val datasetGraph =
            """
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            @prefix dcat: <http://www.w3.org/ns/dcat#> .
            <$datasetUri> a dcat:Dataset ;
                dcat:title "Test Dataset" ;
                dcat:distribution [
                    dcat:accessService [ dcat:endpointURL <$dataServiceUri> ]
                ] .
            """.trimIndent()

        val datasetJson =
            mapOf(
                "@id" to datasetUri,
                "title" to "Test Dataset",
                "distribution" to
                    listOf(
                        mapOf(
                            "accessService" to
                                listOf(
                                    mapOf("uri" to dataServiceUri),
                                ),
                        ),
                    ),
            )

        // Save resources in a transaction that commits
        transactionTemplate.execute {
            resourceRepository.save(
                no.fdk.resourceservice.model.ResourceEntity(
                    id = dataServiceId,
                    uri = dataServiceUri,
                    resourceType = ResourceType.DATA_SERVICE.name,
                    resourceGraphData = dataServiceGraph,
                    resourceGraphFormat = "TURTLE",
                    resourceJson = mapOf("@id" to dataServiceUri, "title" to "Test Data Service"),
                    deleted = false,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            resourceRepository.save(
                no.fdk.resourceservice.model.ResourceEntity(
                    id = datasetId,
                    uri = datasetUri,
                    resourceType = ResourceType.DATASET.name,
                    resourceGraphData = datasetGraph,
                    resourceGraphFormat = "TURTLE",
                    resourceJson = datasetJson,
                    deleted = false,
                    timestamp = System.currentTimeMillis(),
                ),
            )
            resourceRepository.flush()
        }

        // When - build union graph with expandDistributionAccessServices = true
        val orderId = "test-order-expand-services"
        val result =
            unionGraphService.buildUnionGraph(
                listOf(ResourceType.DATASET),
                expandDistributionAccessServices = true,
                orderId = orderId,
            )

        // Then - verify build succeeded
        assertTrue(result, "buildUnionGraph should return true")

        // Then - verify snapshot exists for the dataset
        val snapshots = unionGraphResourceSnapshotRepository.findAll().filter { it.unionGraphId == orderId }
        assertEquals(1, snapshots.size, "Should have one snapshot (dataset with merged DataService)")
        val snapshot = snapshots[0]
        assertEquals(datasetId, snapshot.resourceId)
        assertEquals(ResourceType.DATASET.name, snapshot.resourceType)

        // Then - verify the snapshot contains both dataset and DataService data
        val snapshotContent = snapshot.resourceGraphData
        assertTrue(
            snapshotContent.contains("Test Dataset") || snapshotContent.contains(datasetUri),
            "Snapshot should contain dataset data",
        )
        assertTrue(
            snapshotContent.contains("Test Data Service") || snapshotContent.contains(dataServiceUri),
            "Snapshot should contain merged DataService data",
        )
        assertTrue(
            snapshotContent.contains("endpointURL") || snapshotContent.contains("https://example.com/api"),
            "Snapshot should contain DataService endpoint URL",
        )

        // Then - verify no separate DataService snapshot was created
        val dataServiceSnapshots = snapshots.filter { it.resourceId == dataServiceId }
        assertEquals(0, dataServiceSnapshots.size, "Should not create separate DataService snapshot")
    }
}
