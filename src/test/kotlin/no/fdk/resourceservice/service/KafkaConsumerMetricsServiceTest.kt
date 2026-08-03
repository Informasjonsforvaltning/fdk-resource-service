package no.fdk.resourceservice.service

import io.micrometer.core.instrument.simple.SimpleMeterRegistry
import no.fdk.resourceservice.model.ResourceType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class KafkaConsumerMetricsServiceTest {
    private lateinit var meterRegistry: SimpleMeterRegistry
    private lateinit var metricsService: KafkaConsumerMetricsService

    @BeforeEach
    fun setUp() {
        meterRegistry = SimpleMeterRegistry()
        metricsService = KafkaConsumerMetricsService(meterRegistry)
    }

    @Test
    fun `recordOutcome increments counter with topic and outcome tags`() {
        metricsService.recordOutcome("dataset-events", KafkaConsumerMetricsService.Outcome.ACKED)
        metricsService.recordOutcome("dataset-events", KafkaConsumerMetricsService.Outcome.ACKED)
        metricsService.recordOutcome("dataset-events", KafkaConsumerMetricsService.Outcome.NACKED)

        assertEquals(
            2.0,
            meterRegistry
                .find("kafka_consumer_events_total")
                .tag("topic", "dataset-events")
                .tag("outcome", "acked")
                .counter()
                ?.count(),
        )
        assertEquals(
            1.0,
            meterRegistry
                .find("kafka_consumer_events_total")
                .tag("topic", "dataset-events")
                .tag("outcome", "nacked")
                .counter()
                ?.count(),
        )
    }

    @Test
    fun `recordSkippedStaleTimestamp uses type and reason tags`() {
        metricsService.recordSkippedStaleTimestamp(ResourceType.DATASET)

        assertEquals(
            1.0,
            meterRegistry
                .find("resource_events_skipped_total")
                .tag("type", "dataset")
                .tag("reason", "stale_timestamp")
                .counter()
                ?.count(),
        )
    }
}
