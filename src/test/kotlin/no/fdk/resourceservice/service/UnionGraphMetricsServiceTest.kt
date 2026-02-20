package no.fdk.resourceservice.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import io.mockk.every
import io.mockk.mockk
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class UnionGraphMetricsServiceTest {
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var orderRepository: UnionGraphOrderRepository
    private lateinit var metricsService: UnionGraphMetricsService

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        orderRepository = mockk(relaxed = true)
        metricsService = UnionGraphMetricsService(meterRegistry)
        metricsService.initialize(orderRepository)

        every { orderRepository.countByStatus(any()) } returns 0L
    }

    @Test
    fun `recordOrderCreated increments counter`() {
        metricsService.recordOrderCreated()
        metricsService.recordOrderCreated()

        assertEquals(2.0, meterRegistry.find("union_graph_orders_created_total").counter()?.count())
    }

    @Test
    fun `recordOrderCompleted increments counter`() {
        metricsService.recordOrderCompleted()
        metricsService.recordOrderCompleted()

        assertEquals(2.0, meterRegistry.find("union_graph_orders_completed_total").counter()?.count())
    }

    @Test
    fun `recordOrderFailed with default reason increments counter`() {
        metricsService.recordOrderFailed()
        assertEquals(
            1.0,
            meterRegistry
                .find("union_graph_orders_failed_total")
                .tag("reason", "unknown")
                .counter()
                ?.count(),
        )
    }

    @Test
    fun `recordOrderFailed with custom reason increments counter`() {
        metricsService.recordOrderFailed("build_failed")
        assertEquals(
            1.0,
            meterRegistry
                .find("union_graph_orders_failed_total")
                .tag("reason", "build_failed")
                .counter()
                ?.count(),
        )
    }

    @Test
    fun `recordOrderReset increments counter`() {
        metricsService.recordOrderReset()
        metricsService.recordOrderReset()
        assertEquals(2.0, meterRegistry.find("union_graph_orders_reset_total").counter()?.count())
    }

    @Test
    fun `recordOrderDeleted increments counter`() {
        metricsService.recordOrderDeleted()
        assertEquals(1.0, meterRegistry.find("union_graph_orders_deleted_total").counter()?.count())
    }

    @Test
    fun `recordProcessingDuration records timer`() {
        metricsService.recordProcessingDuration(2.5)
        val timer = meterRegistry.find("union_graph_processing_duration_seconds").timer()
        assertEquals(1, timer?.count())
        assertEquals(2500.0, timer?.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS))
    }

    @Test
    fun `recordWebhookCall records timer and success counter`() {
        metricsService.recordWebhookCall(0.5, true)
        metricsService.recordWebhookCall(0.3, false)

        val webhookTimer = meterRegistry.find("union_graph_webhook_call_duration_seconds").timer()
        assertEquals(2, webhookTimer?.count())

        assertEquals(
            1.0,
            meterRegistry
                .find("union_graph_webhook_calls_total")
                .tag("success", "true")
                .counter()
                ?.count(),
        )
        assertEquals(
            1.0,
            meterRegistry
                .find("union_graph_webhook_calls_total")
                .tag("success", "false")
                .counter()
                ?.count(),
        )
    }

    @Test
    fun `startProcessingProgress and updateProcessingProgress create and update gauges`() {
        metricsService.startProcessingProgress("order-1", 100L)
        metricsService.updateProcessingProgress("order-1", 50L)

        assertEquals(
            0.5,
            meterRegistry
                .find("union_graph_processing_progress_ratio")
                .tag("order_id", "order-1")
                .gauge()
                ?.value(),
        )
        assertEquals(
            50.0,
            meterRegistry
                .find("union_graph_processing_resources_processed")
                .tag("order_id", "order-1")
                .gauge()
                ?.value(),
        )
        assertEquals(
            100.0,
            meterRegistry
                .find("union_graph_processing_resources_total")
                .tag("order_id", "order-1")
                .gauge()
                ?.value(),
        )
    }

    @Test
    fun `stopProcessingProgress removes progress gauges`() {
        metricsService.startProcessingProgress("order-2", 10L)
        metricsService.stopProcessingProgress("order-2")

        assertEquals(
            0,
            meterRegistry
                .find("union_graph_processing_progress_ratio")
                .tag("order_id", "order-2")
                .gauges()
                .size,
        )
    }

    @Test
    fun `recordResourcesProcessed increments counter`() {
        metricsService.recordResourcesProcessed(5L)
        metricsService.recordResourcesProcessed(3L)
        assertEquals(8.0, meterRegistry.find("union_graph_resources_processed_total").counter()?.count())
    }

    @Test
    fun `recordDataServicesExpanded increments counter`() {
        metricsService.recordDataServicesExpanded(2)
        metricsService.recordDataServicesExpanded(1)
        assertEquals(3.0, meterRegistry.find("union_graph_data_services_expanded_total").counter()?.count())
    }

    @Test
    fun `order status gauges use repository when initialized`() {
        every { orderRepository.countByStatus(UnionGraphOrder.GraphStatus.PENDING) } returns 3L
        every { orderRepository.countByStatus(UnionGraphOrder.GraphStatus.PROCESSING) } returns 1L
        every { orderRepository.countByStatus(UnionGraphOrder.GraphStatus.COMPLETED) } returns 10L
        every { orderRepository.countByStatus(UnionGraphOrder.GraphStatus.FAILED) } returns 0L

        val pendingGauge = meterRegistry.find("union_graph_orders_pending").gauge()
        val processingGauge = meterRegistry.find("union_graph_orders_processing").gauge()
        val completedGauge = meterRegistry.find("union_graph_orders_completed").gauge()
        val failedGauge = meterRegistry.find("union_graph_orders_failed").gauge()

        assertEquals(3.0, pendingGauge?.value())
        assertEquals(1.0, processingGauge?.value())
        assertEquals(10.0, completedGauge?.value())
        assertEquals(0.0, failedGauge?.value())
    }
}
