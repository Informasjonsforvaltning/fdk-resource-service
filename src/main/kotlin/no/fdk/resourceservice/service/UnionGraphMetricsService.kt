package no.fdk.resourceservice.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Service for tracking Prometheus metrics related to union graph processing.
 *
 * This service provides metrics for:
 * - Union graph order status counts
 * - Union graph processing duration
 * - Union graph processing progress (resources processed vs total)
 * - Union graph creation and completion rates
 * - Union graph processing errors
 * - Webhook call metrics
 */
@Service
class UnionGraphMetricsService(
    private val meterRegistry: MeterRegistry,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    // Track processing progress per order
    private val processingProgress = ConcurrentHashMap<String, ProcessingProgress>()

    // Counters
    private val orderCreatedCounter: Counter =
        Counter
            .builder("union_graph_orders_created_total")
            .description("Total number of union graph orders created")
            .register(meterRegistry)

    private val orderCompletedCounter: Counter =
        Counter
            .builder("union_graph_orders_completed_total")
            .description("Total number of union graph orders completed successfully")
            .register(meterRegistry)

    private val orderFailedCounter: Counter =
        Counter
            .builder("union_graph_orders_failed_total")
            .description("Total number of union graph orders that failed")
            .tag("reason", "unknown")
            .register(meterRegistry)

    private val orderResetCounter: Counter =
        Counter
            .builder("union_graph_orders_reset_total")
            .description("Total number of union graph orders reset to pending")
            .register(meterRegistry)

    private val orderDeletedCounter: Counter =
        Counter
            .builder("union_graph_orders_deleted_total")
            .description("Total number of union graph orders deleted")
            .register(meterRegistry)

    private val resourcesProcessedCounter: Counter =
        Counter
            .builder("union_graph_resources_processed_total")
            .description("Total number of resources processed during union graph building")
            .register(meterRegistry)

    private val dataServicesExpandedCounter: Counter =
        Counter
            .builder("union_graph_data_services_expanded_total")
            .description("Total number of DataService graphs expanded from dataset distributions")
            .register(meterRegistry)

    // Timers
    private val processingDurationTimer: Timer =
        Timer
            .builder("union_graph_processing_duration_seconds")
            .description("Duration of union graph processing in seconds")
            .register(meterRegistry)

    private val webhookCallTimer: Timer =
        Timer
            .builder("union_graph_webhook_call_duration_seconds")
            .description("Duration of webhook calls for union graph status updates")
            .register(meterRegistry)

    // Gauges for order status counts (will be updated dynamically)
    private val pendingOrdersGauge: Gauge =
        Gauge
            .builder(
                "union_graph_orders_pending",
                java.util.function.Supplier {
                    getOrderCountByStatus(UnionGraphOrder.GraphStatus.PENDING)
                },
            ).description("Current number of union graph orders in PENDING status")
            .register(meterRegistry)

    private val processingOrdersGauge: Gauge =
        Gauge
            .builder(
                "union_graph_orders_processing",
                java.util.function.Supplier {
                    getOrderCountByStatus(UnionGraphOrder.GraphStatus.PROCESSING)
                },
            ).description("Current number of union graph orders in PROCESSING status")
            .register(meterRegistry)

    private val completedOrdersGauge: Gauge =
        Gauge
            .builder(
                "union_graph_orders_completed",
                java.util.function.Supplier {
                    getOrderCountByStatus(UnionGraphOrder.GraphStatus.COMPLETED)
                },
            ).description("Current number of union graph orders in COMPLETED status")
            .register(meterRegistry)

    private val failedOrdersGauge: Gauge =
        Gauge
            .builder(
                "union_graph_orders_failed",
                java.util.function.Supplier {
                    getOrderCountByStatus(UnionGraphOrder.GraphStatus.FAILED)
                },
            ).description("Current number of union graph orders in FAILED status")
            .register(meterRegistry)

    // Progress gauges are created dynamically per order, so no static gauges needed here

    // Store reference to order repository for gauge callbacks
    private var orderRepository: UnionGraphOrderRepository? = null

    /**
     * Initialize the metrics service with the order repository.
     * This is needed for the gauge callbacks to query order counts.
     */
    fun initialize(orderRepository: UnionGraphOrderRepository) {
        this.orderRepository = orderRepository
    }

    /**
     * Record that a union graph order was created.
     */
    fun recordOrderCreated() {
        orderCreatedCounter.increment()
    }

    /**
     * Record that a union graph order was completed successfully.
     */
    fun recordOrderCompleted() {
        orderCompletedCounter.increment()
    }

    /**
     * Record that a union graph order failed.
     *
     * @param reason The reason for failure (e.g., "build_failed", "conversion_failed")
     */
    fun recordOrderFailed(reason: String = "unknown") {
        Counter
            .builder("union_graph_orders_failed_total")
            .description("Total number of union graph orders that failed")
            .tag("reason", reason)
            .register(meterRegistry)
            .increment()
    }

    /**
     * Record that a union graph order was reset to pending.
     */
    fun recordOrderReset() {
        orderResetCounter.increment()
    }

    /**
     * Record that a union graph order was deleted.
     */
    fun recordOrderDeleted() {
        orderDeletedCounter.increment()
    }

    /**
     * Record the duration of union graph processing.
     *
     * @param durationSeconds The processing duration in seconds
     */
    fun recordProcessingDuration(durationSeconds: Double) {
        processingDurationTimer.record((durationSeconds * 1000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
    }

    /**
     * Record the duration of a webhook call.
     *
     * @param durationSeconds The webhook call duration in seconds
     * @param success Whether the webhook call was successful
     */
    fun recordWebhookCall(
        durationSeconds: Double,
        success: Boolean,
    ) {
        webhookCallTimer.record((durationSeconds * 1000).toLong(), java.util.concurrent.TimeUnit.MILLISECONDS)
        Counter
            .builder("union_graph_webhook_calls_total")
            .description("Total number of webhook calls for union graph status updates")
            .tag("success", success.toString())
            .register(meterRegistry)
            .increment()
    }

    /**
     * Start tracking processing progress for an order.
     *
     * @param orderId The order ID
     * @param totalResources The total number of resources to process
     */
    fun startProcessingProgress(
        orderId: String,
        totalResources: Long,
    ) {
        processingProgress[orderId] = ProcessingProgress(totalResources, AtomicLong(0))
        updateProgressGauges(orderId)
    }

    /**
     * Update processing progress for an order.
     *
     * @param orderId The order ID
     * @param processedCount The number of resources processed so far
     */
    fun updateProcessingProgress(
        orderId: String,
        processedCount: Long,
    ) {
        processingProgress[orderId]?.processedCount?.set(processedCount)
        updateProgressGauges(orderId)
    }

    /**
     * Stop tracking processing progress for an order (when processing completes or fails).
     *
     * @param orderId The order ID
     */
    fun stopProcessingProgress(orderId: String) {
        processingProgress.remove(orderId)
        // Gauges will automatically return 0 when the order is not in the map
    }

    /**
     * Record that resources were processed during union graph building.
     *
     * @param count The number of resources processed
     */
    fun recordResourcesProcessed(count: Long) {
        resourcesProcessedCounter.increment(count.toDouble())
    }

    /**
     * Record that DataService graphs were expanded from dataset distributions.
     *
     * @param count The number of DataService graphs expanded
     */
    fun recordDataServicesExpanded(count: Int) {
        dataServicesExpandedCounter.increment(count.toDouble())
    }

    /**
     * Get the current count of orders by status.
     * This is used by the gauge callbacks.
     */
    private fun getOrderCountByStatus(status: UnionGraphOrder.GraphStatus): Double =
        orderRepository?.countByStatus(status)?.toDouble() ?: 0.0

    /**
     * Update progress gauges for a specific order.
     */
    private fun updateProgressGauges(orderId: String) {
        val progress = processingProgress[orderId] ?: return

        val processed = progress.processedCount.get().toDouble()
        val total = progress.totalResources.toDouble()
        val ratio = if (total > 0) processed / total else 0.0

        // Use a supplier-based approach for dynamic gauges
        // Remove existing gauges first if they exist
        try {
            meterRegistry.remove(
                meterRegistry
                    .find("union_graph_processing_progress_ratio")
                    .tag("order_id", orderId)
                    .gauge(),
            )
            meterRegistry.remove(
                meterRegistry
                    .find("union_graph_processing_resources_processed")
                    .tag("order_id", orderId)
                    .gauge(),
            )
            meterRegistry.remove(
                meterRegistry
                    .find("union_graph_processing_resources_total")
                    .tag("order_id", orderId)
                    .gauge(),
            )
        } catch (e: Exception) {
            // Ignore if gauges don't exist
        }

        // Register new gauges with current values using suppliers
        // Note: We need to capture orderId in a final variable for the lambda
        val finalOrderId = orderId
        Gauge
            .builder(
                "union_graph_processing_progress_ratio",
                java.util.function.Supplier {
                    processingProgress[finalOrderId]?.let {
                        val p = it.processedCount.get().toDouble()
                        val t = it.totalResources.toDouble()
                        if (t > 0) p / t else 0.0
                    } ?: 0.0
                },
            ).description("Processing progress ratio (0.0 to 1.0) for currently processing union graphs")
            .tag("order_id", finalOrderId)
            .register(meterRegistry)

        Gauge
            .builder(
                "union_graph_processing_resources_processed",
                java.util.function.Supplier {
                    processingProgress[finalOrderId]?.processedCount?.get()?.toDouble() ?: 0.0
                },
            ).description("Number of resources processed for currently processing union graphs")
            .tag("order_id", finalOrderId)
            .register(meterRegistry)

        Gauge
            .builder(
                "union_graph_processing_resources_total",
                java.util.function.Supplier {
                    processingProgress[finalOrderId]?.totalResources?.toDouble() ?: 0.0
                },
            ).description("Total number of resources to process for currently processing union graphs")
            .tag("order_id", finalOrderId)
            .register(meterRegistry)
    }

    /**
     * Internal data class to track processing progress.
     */
    private data class ProcessingProgress(
        val totalResources: Long,
        val processedCount: AtomicLong,
    )
}
