package no.fdk.resourceservice.kafka

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.kafka.listener.KafkaMessageListenerContainer

class KafkaListenerManagerTest {
    private lateinit var circuitBreakerRegistry: CircuitBreakerRegistry
    private lateinit var circuitBreaker: CircuitBreaker
    private lateinit var listenerContainer: KafkaMessageListenerContainer<String, Any>
    private lateinit var kafkaListenerManager: KafkaListenerManager

    @BeforeEach
    fun setUp() {
        circuitBreakerRegistry = mockk<CircuitBreakerRegistry>()
        circuitBreaker = mockk<CircuitBreaker>()
        listenerContainer = mockk<KafkaMessageListenerContainer<String, Any>>()

        val containers = listOf(listenerContainer)

        // Mock all circuit breakers that KafkaListenerManager tries to access
        val circuitBreakerNames =
            listOf(
                "rdfParseConsumer",
                "conceptConsumer",
                "datasetConsumer",
                "dataServiceConsumer",
                "informationModelConsumer",
                "serviceConsumer",
                "eventConsumer",
            )

        circuitBreakerNames.forEach { name ->
            every { circuitBreakerRegistry.circuitBreaker(name) } returns circuitBreaker
        }

        every { circuitBreaker.state } returns CircuitBreaker.State.CLOSED
        every { circuitBreaker.metrics } returns mockk<CircuitBreaker.Metrics>()
        every { circuitBreaker.metrics.failureRate } returns 0.0f
        every { circuitBreaker.metrics.numberOfBufferedCalls } returns 10
        every { circuitBreaker.metrics.numberOfFailedCalls } returns 0
        every { circuitBreaker.metrics.numberOfSuccessfulCalls } returns 10

        // Mock listener container
        every { listenerContainer.listenerId } returns "concept-events-listener"
        every { listenerContainer.isRunning } returns true

        kafkaListenerManager = KafkaListenerManager(circuitBreakerRegistry, containers)
    }

    @Test
    fun `should get circuit breaker status`() {
        // When
        val status = kafkaListenerManager.getCircuitBreakerStatus()

        // Then
        assertNotNull(status)
        assertNotNull(status["conceptConsumer"])
        assertEquals("CLOSED", status["conceptConsumer"]?.state)
    }

    @Test
    fun `should pause all listeners`() {
        // When
        kafkaListenerManager.pauseAllListeners()

        // Then
        verify { listenerContainer.pause() }
    }

    @Test
    fun `should resume all listeners`() {
        // Given
        every { listenerContainer.isRunning } returns false

        // When
        kafkaListenerManager.resumeAllListeners()

        // Then
        verify { listenerContainer.resume() }
    }
}
