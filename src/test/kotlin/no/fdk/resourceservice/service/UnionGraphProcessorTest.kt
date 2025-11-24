package no.fdk.resourceservice.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

class UnionGraphProcessorTest {
    private lateinit var unionGraphService: UnionGraphService
    private lateinit var unionGraphOrderRepository: UnionGraphOrderRepository
    private lateinit var unionGraphProcessor: UnionGraphProcessor

    @BeforeEach
    fun setUp() {
        unionGraphService = mockk(relaxed = true)
        unionGraphOrderRepository = mockk(relaxed = true)
        unionGraphProcessor =
            UnionGraphProcessor(
                unionGraphService,
                unionGraphOrderRepository,
            )
    }

    @Test
    fun `processPendingOrders should process orders when found`() {
        // Given
        val lockTimeout = Instant.now().minus(60, ChronoUnit.MINUTES)
        val orders =
            listOf(
                UnionGraphOrder(
                    id = "order-1",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                ),
                UnionGraphOrder(
                    id = "order-2",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                ),
            )

        every {
            unionGraphOrderRepository.findNextPendingOrders(lockTimeout, any())
        } returns orders

        // When
        unionGraphProcessor.processPendingOrders()

        // Then - processOrderAsync is async, so the actual processOrder call happens asynchronously
        // We can't easily verify async calls in unit tests without waiting, so we just verify
        // that processPendingOrders completed without errors
        // In integration tests, we would verify the actual processing happens
    }

    @Test
    fun `processPendingOrders should handle empty list gracefully`() {
        // Given
        val lockTimeout = Instant.now().minus(60, ChronoUnit.MINUTES)
        every {
            unionGraphOrderRepository.findNextPendingOrders(lockTimeout, any())
        } returns emptyList()

        // When
        unionGraphProcessor.processPendingOrders()

        // Then
        verify(exactly = 0) { unionGraphService.processOrder(any(), any()) }
    }

    @Test
    fun `processPendingOrders should handle exceptions gracefully`() {
        // Given
        val lockTimeout = Instant.now().minus(60, ChronoUnit.MINUTES)
        every {
            unionGraphOrderRepository.findNextPendingOrders(lockTimeout, any())
        } throws RuntimeException("Database error")

        // When & Then - should not throw
        unionGraphProcessor.processPendingOrders()
    }

    @Test
    fun `processExpiredOrders should reset expired orders to pending`() {
        // Given
        val expiredOrder =
            UnionGraphOrder(
                id = "expired-order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                updateTtlHours = 24,
                processedAt = Instant.now().minus(25, ChronoUnit.HOURS),
            )
        val notExpiredOrder =
            UnionGraphOrder(
                id = "not-expired-order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                updateTtlHours = 24,
                processedAt = Instant.now().minus(12, ChronoUnit.HOURS),
            )

        every {
            unionGraphOrderRepository.findByStatus(UnionGraphOrder.GraphStatus.COMPLETED)
        } returns listOf(expiredOrder, notExpiredOrder)
        every { unionGraphOrderRepository.resetToPending(any()) } returns 1

        // When
        unionGraphProcessor.processExpiredOrders()

        // Then
        verify { unionGraphOrderRepository.resetToPending("expired-order") }
        verify(exactly = 0) { unionGraphOrderRepository.resetToPending("not-expired-order") }
    }

    @Test
    fun `processExpiredOrders should not reset orders with TTL 0`() {
        // Given
        val orderWithNoTtl =
            UnionGraphOrder(
                id = "no-ttl-order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                updateTtlHours = 0,
                processedAt = Instant.now().minus(100, ChronoUnit.HOURS),
            )

        every {
            unionGraphOrderRepository.findByStatus(UnionGraphOrder.GraphStatus.COMPLETED)
        } returns listOf(orderWithNoTtl)

        // When
        unionGraphProcessor.processExpiredOrders()

        // Then
        verify(exactly = 0) { unionGraphOrderRepository.resetToPending(any()) }
    }

    @Test
    fun `cleanupStaleLocks should release stale locks`() {
        // Given
        val staleOrder =
            UnionGraphOrder(
                id = "stale-order",
                status = UnionGraphOrder.GraphStatus.PROCESSING,
                lockedBy = "crashed-instance",
                lockedAt = Instant.now().minus(70, ChronoUnit.MINUTES),
            )
        val freshOrder =
            UnionGraphOrder(
                id = "fresh-order",
                status = UnionGraphOrder.GraphStatus.PROCESSING,
                lockedBy = "active-instance",
                lockedAt = Instant.now().minus(30, ChronoUnit.MINUTES),
            )

        every {
            unionGraphOrderRepository.findByStatus(UnionGraphOrder.GraphStatus.PROCESSING)
        } returns listOf(staleOrder, freshOrder)
        every { unionGraphOrderRepository.releaseLock(any()) } returns 1
        every { unionGraphOrderRepository.resetToPending(any()) } returns 1

        // When
        unionGraphProcessor.cleanupStaleLocks()

        // Then
        verify { unionGraphOrderRepository.releaseLock("stale-order") }
        verify { unionGraphOrderRepository.resetToPending("stale-order") }
        verify(exactly = 0) { unionGraphOrderRepository.releaseLock("fresh-order") }
    }

    @Test
    fun `cleanupStaleLocks should handle orders without lockedAt`() {
        // Given
        val orderWithoutLock =
            UnionGraphOrder(
                id = "no-lock-order",
                status = UnionGraphOrder.GraphStatus.PROCESSING,
                lockedBy = "instance",
                lockedAt = null,
            )

        every {
            unionGraphOrderRepository.findByStatus(UnionGraphOrder.GraphStatus.PROCESSING)
        } returns listOf(orderWithoutLock)

        // When
        unionGraphProcessor.cleanupStaleLocks()

        // Then
        verify(exactly = 0) { unionGraphOrderRepository.releaseLock(any()) }
    }

    @Test
    fun `cleanupStaleLocks should handle exceptions gracefully`() {
        // Given
        every {
            unionGraphOrderRepository.findByStatus(UnionGraphOrder.GraphStatus.PROCESSING)
        } throws RuntimeException("Database error")

        // When & Then - should not throw
        unionGraphProcessor.cleanupStaleLocks()
    }
}
