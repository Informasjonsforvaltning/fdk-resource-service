package no.fdk.resourceservice.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.fdk.resourceservice.model.ResourceType
import org.springframework.stereotype.Service

/**
 * Metrics for successful resource store and delete operations.
 */
@Service
class ResourceStoreMetricsService(
    private val meterRegistry: MeterRegistry,
) {
    fun recordStored(resourceType: ResourceType) {
        Counter
            .builder("resources_stored_total")
            .description("Total number of resources stored successfully")
            .tag("type", resourceType.name.lowercase())
            .tag("action", "reasoned")
            .register(meterRegistry)
            .increment()
    }

    fun recordDeleted(resourceType: ResourceType) {
        Counter
            .builder("resources_deleted_total")
            .description("Total number of resources marked as deleted")
            .tag("type", resourceType.name.lowercase())
            .register(meterRegistry)
            .increment()
    }
}
