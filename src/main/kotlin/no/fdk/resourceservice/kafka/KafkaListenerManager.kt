package no.fdk.resourceservice.kafka

import io.github.resilience4j.circuitbreaker.CircuitBreaker
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import jakarta.annotation.PostConstruct
import org.slf4j.LoggerFactory
import org.springframework.kafka.listener.KafkaMessageListenerContainer
import org.springframework.stereotype.Service

/**
 * Service that manages Kafka listener containers based on circuit breaker state.
 *
 * This service monitors circuit breaker states and pauses/resumes Kafka listeners
 * to prevent message processing when downstream services are failing.
 *
 * Key responsibilities:
 * - Monitor circuit breaker states for all consumer types
 * - Pause listeners when circuit breakers are open
 * - Resume listeners when circuit breakers are closed
 * - Provide health status for monitoring
 */
@Service
class KafkaListenerManager(
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val kafkaListenerContainers: List<KafkaMessageListenerContainer<*, *>>,
) {
    private val logger = LoggerFactory.getLogger(KafkaListenerManager::class.java)

    // Map of circuit breaker names to their corresponding listener containers
    private val circuitBreakerToContainers =
        mapOf(
            "rdfParseConsumer" to listOf("rdf-parse-events"),
            "conceptConsumer" to listOf("concept-events"),
            "datasetConsumer" to listOf("dataset-events"),
            "dataServiceConsumer" to listOf("data-service-events"),
            "informationModelConsumer" to listOf("information-model-events"),
            "serviceConsumer" to listOf("service-events"),
            "eventConsumer" to listOf("event-events"),
        )

    @PostConstruct
    fun initializeCircuitBreakerMonitoring() {
        logger.info("🔧 Initializing Kafka listener management with circuit breaker monitoring")

        // Register event listeners for all circuit breakers
        circuitBreakerToContainers.keys.forEach { circuitBreakerName ->
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName)
            if (circuitBreaker != null) {
                circuitBreaker.eventPublisher.onStateTransition { event ->
                    handleCircuitBreakerStateChange(circuitBreakerName, event.stateTransition)
                }
                logger.info("✅ Registered circuit breaker monitoring for: $circuitBreakerName")
            } else {
                logger.warn("⚠️ Circuit breaker not found: $circuitBreakerName")
            }
        }
    }

    private fun handleCircuitBreakerStateChange(
        circuitBreakerName: String,
        stateTransition: CircuitBreaker.StateTransition,
    ) {
        val fromState = stateTransition.fromState
        val toState = stateTransition.toState

        logger.info("🔄 Circuit breaker state change: $circuitBreakerName from $fromState to $toState")

        when (toState) {
            CircuitBreaker.State.OPEN -> {
                pauseListenersForCircuitBreaker(circuitBreakerName)
            }

            CircuitBreaker.State.CLOSED -> {
                resumeListenersForCircuitBreaker(circuitBreakerName)
            }

            CircuitBreaker.State.HALF_OPEN -> {
                // Keep listeners paused in half-open state to test with limited traffic
                logger.info("🔶 Circuit breaker $circuitBreakerName is HALF_OPEN - listeners remain paused for testing")
            }

            else -> {}
        }
    }

    private fun pauseListenersForCircuitBreaker(circuitBreakerName: String) {
        val topics = circuitBreakerToContainers[circuitBreakerName] ?: return

        kafkaListenerContainers.forEach { container ->
            if (topics.any { topic -> container.listenerId.contains(topic) }) {
                if (!container.isRunning) {
                    logger.debug("📴 Listener container ${container.listenerId} is already stopped")
                    return@forEach
                }

                try {
                    container.pause()
                    logger.warn(
                        "⏸️ PAUSED listener container ${container.listenerId} due to circuit breaker $circuitBreakerName being OPEN",
                    )
                } catch (e: Exception) {
                    logger.error("❌ Failed to pause listener container ${container.listenerId}", e)
                }
            }
        }
    }

    private fun resumeListenersForCircuitBreaker(circuitBreakerName: String) {
        val topics = circuitBreakerToContainers[circuitBreakerName] ?: return

        kafkaListenerContainers.forEach { container ->
            if (topics.any { topic -> container.listenerId.contains(topic) }) {
                if (container.isRunning) {
                    logger.debug("▶️ Listener container ${container.listenerId} is already running")
                    return@forEach
                }

                try {
                    container.resume()
                    logger.info("▶️ RESUMED listener container ${container.listenerId} - circuit breaker $circuitBreakerName is CLOSED")
                } catch (e: Exception) {
                    logger.error("❌ Failed to resume listener container ${container.listenerId}", e)
                }
            }
        }
    }

    /**
     * Get the current status of all circuit breakers and their corresponding listeners
     */
    fun getCircuitBreakerStatus(): Map<String, CircuitBreakerStatus> =
        circuitBreakerToContainers.keys.associateWith { circuitBreakerName ->
            val circuitBreaker = circuitBreakerRegistry.circuitBreaker(circuitBreakerName)
            val topics = circuitBreakerToContainers[circuitBreakerName] ?: emptyList()

            val listenerStatus =
                kafkaListenerContainers
                    .filter { container -> topics.any { topic -> container.listenerId.contains(topic) } }
                    .associate { container ->
                        container.listenerId to
                            ListenerStatus(
                                isRunning = container.isRunning,
                                isPaused = !container.isRunning,
                                topics = topics,
                            )
                    }

            CircuitBreakerStatus(
                name = circuitBreakerName,
                state = circuitBreaker.state.name,
                failureRate = circuitBreaker.metrics.failureRate.toDouble(),
                numberOfBufferedCalls = circuitBreaker.metrics.numberOfBufferedCalls,
                numberOfFailedCalls = circuitBreaker.metrics.numberOfFailedCalls,
                numberOfSuccessfulCalls = circuitBreaker.metrics.numberOfSuccessfulCalls,
                listeners = listenerStatus,
            )
        }

    /**
     * Manually pause all listeners (for maintenance or emergency situations)
     */
    fun pauseAllListeners() {
        logger.warn("🛑 Manually pausing ALL Kafka listeners")
        kafkaListenerContainers.forEach { container ->
            try {
                if (container.isRunning) {
                    container.pause()
                    logger.info("⏸️ Paused listener container ${container.listenerId}")
                }
            } catch (e: Exception) {
                logger.error("❌ Failed to pause listener container ${container.listenerId}", e)
            }
        }
    }

    /**
     * Manually resume all listeners
     */
    fun resumeAllListeners() {
        logger.info("▶️ Manually resuming ALL Kafka listeners")
        kafkaListenerContainers.forEach { container ->
            try {
                if (!container.isRunning) {
                    container.resume()
                    logger.info("▶️ Resumed listener container ${container.listenerId}")
                }
            } catch (e: Exception) {
                logger.error("❌ Failed to resume listener container ${container.listenerId}", e)
            }
        }
    }
}

data class CircuitBreakerStatus(
    val name: String,
    val state: String,
    val failureRate: Double,
    val numberOfBufferedCalls: Int,
    val numberOfFailedCalls: Int,
    val numberOfSuccessfulCalls: Int,
    val listeners: Map<String, ListenerStatus>,
)

data class ListenerStatus(
    val isRunning: Boolean,
    val isPaused: Boolean,
    val topics: List<String>,
)
