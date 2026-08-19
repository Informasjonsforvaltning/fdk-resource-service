package no.fdk.resourceservice.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import no.fdk.resourceservice.model.ResourceType
import org.springframework.stereotype.Service

/**
 * Metrics for Kafka consumer processing outcomes (ack / nack / skip).
 */
@Service
class KafkaConsumerMetricsService(private val meterRegistry: MeterRegistry) {
    enum class Outcome(val value: String) {
        ACKED("acked"),
        NACKED("nacked"),
        SKIPPED_INVALID("skipped_invalid"),
    }

    /**
     * Record a consumer outcome for a Kafka topic.
     */
    fun recordOutcome(topic: String, outcome: Outcome) {
        Counter
            .builder("kafka_consumer_events_total")
            .description("Total Kafka consumer events by processing outcome")
            .tag("topic", topic)
            .tag("outcome", outcome.value)
            .register(meterRegistry)
            .increment()
    }

    /**
     * Record a skip due to an older event timestamp.
     */
    fun recordSkippedStaleTimestamp(resourceType: ResourceType) {
        Counter
            .builder("resource_events_skipped_total")
            .description("Resource events skipped without processing")
            .tag("type", resourceType.name.lowercase())
            .tag("reason", "stale_timestamp")
            .register(meterRegistry)
            .increment()
    }
}
