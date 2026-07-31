package no.fdk.resourceservice.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.fdk.resourceservice.model.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class ResourceStoreMetricsServiceTest {
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var metricsService: ResourceStoreMetricsService

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        metricsService = ResourceStoreMetricsService(meterRegistry)
    }

    @Test
    fun `recordStored increments counter with type and action tags`() {
        metricsService.recordStored(ResourceType.DATASET)
        metricsService.recordStored(ResourceType.DATASET)
        metricsService.recordStored(ResourceType.CONCEPT)

        assertEquals(
            2.0,
            meterRegistry
                .find("resources_stored_total")
                .tag("type", "dataset")
                .tag("action", "reasoned")
                .counter()
                ?.count(),
        )
        assertEquals(
            1.0,
            meterRegistry
                .find("resources_stored_total")
                .tag("type", "concept")
                .tag("action", "reasoned")
                .counter()
                ?.count(),
        )
    }

    @Test
    fun `recordDeleted increments counter with type tag`() {
        metricsService.recordDeleted(ResourceType.SERVICE)

        assertEquals(
            1.0,
            meterRegistry
                .find("resources_deleted_total")
                .tag("type", "service")
                .counter()
                ?.count(),
        )
    }
}
