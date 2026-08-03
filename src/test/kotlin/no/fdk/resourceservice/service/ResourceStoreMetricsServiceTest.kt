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
    fun `recordStored increments counter with type and kind tags`() {
        metricsService.recordStored(ResourceType.DATASET, ResourceStoreMetricsService.StoreKind.JSON)
        metricsService.recordStored(ResourceType.DATASET, ResourceStoreMetricsService.StoreKind.GRAPH)
        metricsService.recordStored(ResourceType.CONCEPT, ResourceStoreMetricsService.StoreKind.JSON)

        assertEquals(
            1.0,
            meterRegistry
                .find("resources_stored_total")
                .tag("type", "dataset")
                .tag("kind", "json")
                .counter()
                ?.count(),
        )
        assertEquals(
            1.0,
            meterRegistry
                .find("resources_stored_total")
                .tag("type", "dataset")
                .tag("kind", "graph")
                .counter()
                ?.count(),
        )
        assertEquals(
            1.0,
            meterRegistry
                .find("resources_stored_total")
                .tag("type", "concept")
                .tag("kind", "json")
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
