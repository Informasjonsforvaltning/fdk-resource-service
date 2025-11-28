package no.fdk.resourceservice.service

import no.fdk.resourceservice.config.UnionGraphConfig
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.net.InetAddress
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * Background processor for union graph orders.
 *
 * This service polls for pending orders and processes them asynchronously.
 * It uses database-level locking to ensure only one instance processes each order
 * in a distributed environment.
 */
@Service
class UnionGraphProcessor(
    private val unionGraphService: UnionGraphService,
    private val unionGraphOrderRepository: UnionGraphOrderRepository,
) {
    private val logger = LoggerFactory.getLogger(UnionGraphProcessor::class.java)

    /**
     * Unique identifier for this instance (used for distributed locking).
     */
    private val instanceId: String =
        try {
            "${InetAddress.getLocalHost().hostName}-${System.getProperty("user.name")}-${Thread.currentThread().threadId()}"
        } catch (_: Exception) {
            "unknown-${System.currentTimeMillis()}"
        }

    init {
        logger.info("UnionGraphProcessor initialized with instance ID: {}", instanceId)
    }

    /**
     * Scheduled task that polls for pending orders and processes them.
     * Runs every 5 seconds.
     *
     * Handles memory-intensive graph operations by:
     * - Processing multiple orders per cycle (up to available thread capacity)
     * - Limiting concurrent processing via thread pool configuration
     * - Gracefully handling when no tasks are available (simply returns)
     *
     * Note: This method is NOT transactional because:
     * 1. The database query uses FOR UPDATE SKIP LOCKED which handles locking at the DB level
     * 2. The async processing runs in its own transaction context
     * 3. Wrapping this in a transaction can cause visibility issues where the async method
     *    doesn't see the committed state, preventing orders from being processed
     */
    @Scheduled(fixedDelay = 5000)
    fun processPendingOrders() {
        try {
            logger.debug("Scheduler running: checking for pending orders")

            // Lock timeout: release locks older than 60 minutes (in case of crashed instances)
            val lockTimeout = Instant.now().minus(60, ChronoUnit.MINUTES)

            // Calculate how many orders we can process based on thread pool capacity
            // Fetch up to maxPoolSize orders per cycle to match thread pool capacity
            // This ensures we keep the thread pool busy without overwhelming memory
            val maxOrdersPerCycle = UnionGraphConfig.UNION_GRAPH_MAX_POOL_SIZE

            // Find pending orders (with pessimistic locking)
            // The query uses FOR UPDATE SKIP LOCKED which locks rows at the database level
            // This happens in a read-only transaction managed by Spring Data JPA
            val orders = unionGraphOrderRepository.findNextPendingOrders(lockTimeout, maxOrdersPerCycle)

            if (orders.isEmpty()) {
                // No tasks available - this is normal, scheduler will retry in 5 seconds
                // Log at debug level to avoid spam, but visible when debug logging is enabled
                logger.debug("No pending orders found (scheduler will retry in 5 seconds)")
                return
            }

            logger.info("Found {} pending order(s) to process", orders.size)

            // Process each order asynchronously
            // The thread pool will handle queuing if all threads are busy
            // Pass only the order ID to avoid detached entity issues
            // The async method runs in its own transaction context, so it can see
            // the committed state and properly lock the order for processing
            for (order in orders) {
                processOrderAsync(order.id)
            }
        } catch (e: Exception) {
            logger.error("Error in processPendingOrders scheduler", e)
        }
    }

    /**
     * Processes a union graph order asynchronously.
     *
     * Fetches the order fresh from the database to avoid detached entity issues
     * when called from a transactional context.
     *
     * @param orderId The ID of the order to process.
     */
    @Async("unionGraphTaskExecutor")
    fun processOrderAsync(orderId: String) {
        try {
            logger.info("Processing order {} asynchronously", orderId)
            // Fetch the order fresh from the database to ensure it's managed
            val order = unionGraphOrderRepository.findById(orderId).orElse(null)
            if (order == null) {
                logger.warn("Order {} not found when trying to process asynchronously", orderId)
                return
            }
            unionGraphService.processOrder(order, instanceId)
        } catch (e: Exception) {
            logger.error("Error processing order {} asynchronously", orderId, e)
            try {
                unionGraphOrderRepository.markAsFailed(
                    orderId,
                    "Error during async processing: ${e.message}",
                )
            } catch (updateException: Exception) {
                logger.error("Failed to mark order {} as failed", orderId, updateException)
            }
        }
    }

    /**
     * Scheduled task that checks for completed orders that need to be updated based on TTL.
     * Runs every hour.
     *
     * Finds completed orders where the TTL has expired (processed_at + update_ttl_hours < now)
     * and resets them to PENDING so they get rebuilt.
     *
     * Note: Only COMPLETED orders with processedAt set are checked. PENDING orders are not
     * affected by TTL expiration.
     *
     * Note: This method is NOT transactional because:
     * 1. Each repository method (resetToPending) is already @Transactional
     * 2. We want each order reset to be independent (if one fails, others should still succeed)
     * 3. Avoids long-running transactions that could cause issues
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    fun processExpiredOrders() {
        try {
            logger.debug("Checking for expired orders that need updating")

            // Find all completed orders with TTL > 0 and processedAt set
            // Only COMPLETED orders can expire - PENDING orders don't have processedAt set
            val completedOrders =
                unionGraphOrderRepository
                    .findByStatus(UnionGraphOrder.GraphStatus.COMPLETED)
                    .filter { it.updateTtlHours > 0 && it.processedAt != null }

            val now = Instant.now()
            var expiredCount = 0

            for (order in completedOrders) {
                // Calculate when the order should be updated
                val nextUpdateTime = order.processedAt!!.plus(order.updateTtlHours.toLong(), ChronoUnit.HOURS)

                // If the TTL has expired, reset to PENDING
                if (!now.isBefore(nextUpdateTime)) {
                    logger.info(
                        "Order {} TTL expired (processed at {}, TTL {} hours, next update was {}), resetting to PENDING",
                        order.id,
                        order.processedAt,
                        order.updateTtlHours,
                        nextUpdateTime,
                    )
                    unionGraphOrderRepository.resetToPending(order.id)
                    expiredCount++
                }
            }

            if (expiredCount > 0) {
                logger.info("Reset {} expired order(s) to PENDING for automatic update", expiredCount)
            } else {
                logger.debug("No expired orders found")
            }
        } catch (e: Exception) {
            logger.error("Error in processExpiredOrders", e)
        }
    }

    /**
     * Cleanup task that releases stale locks (from crashed instances).
     * Runs every 10 minutes.
     *
     * Only resets orders to PENDING if they've been locked for more than 60 minutes
     * (the lock timeout), to avoid interfering with long-running graph building operations.
     *
     * Note: This method is NOT transactional because:
     * 1. Each repository method (releaseLock, resetToPending) is already @Transactional
     * 2. We want each order cleanup to be independent (if one fails, others should still succeed)
     * 3. Avoids long-running transactions that could cause issues
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    fun cleanupStaleLocks() {
        try {
            // Use 60 minutes as the lock timeout (matching processPendingOrders)
            val lockTimeout = Instant.now().minus(60, ChronoUnit.MINUTES)
            logger.debug("Cleaning up stale locks older than {} (60 minutes)", lockTimeout)

            // Find orders that are locked but haven't been updated recently
            // Only reset to PENDING if locked for more than 60 minutes
            val staleOrders =
                unionGraphOrderRepository
                    .findByStatus(UnionGraphOrder.GraphStatus.PROCESSING)
                    .filter { it.lockedAt != null && it.lockedAt.isBefore(lockTimeout) }

            for (order in staleOrders) {
                logger.warn(
                    "Releasing stale lock on order {} (locked by {} at {}, locked for more than 60 minutes)",
                    order.id,
                    order.lockedBy,
                    order.lockedAt,
                )
                unionGraphOrderRepository.releaseLock(order.id)
                // Reset status to PENDING so it can be retried
                unionGraphOrderRepository.resetToPending(order.id)
            }

            if (staleOrders.isNotEmpty()) {
                logger.info("Released {} stale locks (locked for more than 60 minutes)", staleOrders.size)
            }
        } catch (e: Exception) {
            logger.error("Error in cleanupStaleLocks", e)
        }
    }
}
