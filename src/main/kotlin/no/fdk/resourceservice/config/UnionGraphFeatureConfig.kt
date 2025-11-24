package no.fdk.resourceservice.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * Configuration properties for union graph feature flags.
 */
@Configuration
@ConfigurationProperties(prefix = "app.union-graphs")
data class UnionGraphFeatureConfig(
    /**
     * Whether the delete endpoint is enabled.
     * When false, DELETE requests will return 403 Forbidden.
     */
    var deleteEnabled: Boolean = false,
    /**
     * Whether the reset endpoint is enabled.
     * When false, POST /reset requests will return 403 Forbidden.
     */
    var resetEnabled: Boolean = false,
)
