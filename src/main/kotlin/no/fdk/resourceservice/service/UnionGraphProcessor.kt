package no.fdk.resourceservice.service

import no.fdk.resourceservice.config.UnionGraphConfig
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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
     */
    @Scheduled(fixedDelay = 5000)
    @Transactional
    fun processPendingOrders() {
        try {
            // Lock timeout: release locks older than 60 minutes (in case of crashed instances)
            val lockTimeout = Instant.now().minus(60, ChronoUnit.MINUTES)

            // Calculate how many orders we can process based on thread pool capacity
            // Fetch up to maxPoolSize orders per cycle to match thread pool capacity
            // This ensures we keep the thread pool busy without overwhelming memory
            val maxOrdersPerCycle = UnionGraphConfig.UNION_GRAPH_MAX_POOL_SIZE

            // Find pending orders (with pessimistic locking)
            val orders = unionGraphOrderRepository.findNextPendingOrders(lockTimeout, maxOrdersPerCycle)

            if (orders.isEmpty()) {
                // No tasks available - this is normal, scheduler will retry in 5 seconds
                logger.debug("No pending orders found")
                return
            }

            logger.info("Found {} pending order(s) to process", orders.size)

            // Process each order asynchronously
            // The thread pool will handle queuing if all threads are busy
            for (order in orders) {
                processOrderAsync(order)
            }
        } catch (e: Exception) {
            logger.error("Error in processPendingOrders scheduler", e)
        }
    }

    /**
     * Processes a union graph order asynchronously.
     *
     * @param order The order to process.
     */
    @Async("unionGraphTaskExecutor")
    fun processOrderAsync(order: UnionGraphOrder) {
        try {
            logger.info("Processing order {} asynchronously", order.id)
            unionGraphService.processOrder(order, instanceId)
        } catch (e: Exception) {
            logger.error("Error processing order {} asynchronously", order.id, e)
            try {
                unionGraphOrderRepository.markAsFailed(
                    order.id,
                    "Error during async processing: ${e.message}",
                )
            } catch (updateException: Exception) {
                logger.error("Failed to mark order {} as failed", order.id, updateException)
            }
        }
    }

    /**
     * Scheduled task that checks for completed orders that need to be updated based on TTL.
     * Runs every hour.
     *
     * Finds completed orders where the TTL has expired (processed_at + update_ttl_hours < now)
     * and resets them to PENDING so they get rebuilt.
     */
    @Scheduled(fixedDelay = 3600000) // 1 hour
    @Transactional
    fun processExpiredOrders() {
        try {
            logger.debug("Checking for expired orders that need updating")

            // Find all completed orders with TTL > 0
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
     */
    @Scheduled(fixedDelay = 600000) // 10 minutes
    @Transactional
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
