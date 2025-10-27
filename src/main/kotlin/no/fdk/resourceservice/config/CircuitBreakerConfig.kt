package no.fdk.resourceservice.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Configuration class for Resilience4j circuit breaker components.
 * 
 * This configuration ensures that the circuit breaker registry
 * is properly configured and available for dependency injection.
 * 
 * Note: Retry mechanism is not needed as Kafka already handles retries at the broker level.
 */
@Configuration
class CircuitBreakerConfig {

    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        return CircuitBreakerRegistry.ofDefaults()
    }
}
