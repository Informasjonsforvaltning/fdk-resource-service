package no.fdk.resourceservice.config

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Duration
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig as Resilience4jConfig

@Configuration
class CircuitBreakerConfig {
    @Bean
    fun circuitBreakerRegistry(): CircuitBreakerRegistry {
        val defaultConfig =
            Resilience4jConfig
                .custom()
                .slidingWindowType(Resilience4jConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(50)
                .minimumNumberOfCalls(5)
                .permittedNumberOfCallsInHalfOpenState(2)
                .automaticTransitionFromOpenToHalfOpenEnabled(true)
                .waitDurationInOpenState(Duration.ofSeconds(30))
                .failureRateThreshold(60f)
                .slowCallRateThreshold(60f)
                .slowCallDurationThreshold(Duration.ofSeconds(5))
                .build()

        return CircuitBreakerRegistry.of(defaultConfig)
    }
}
