package no.fdk.resourceservice.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
@EnableWebSecurity
class SecurityConfig(
    @Value("\${app.union-graphs.api-key:}")
    private val unionGraphsApiKey: String,
) {
    @Bean
    fun filterChain(http: HttpSecurity): SecurityFilterChain {
        // Add API key filter if API key is configured
        if (unionGraphsApiKey.isNotBlank()) {
            http.addFilterBefore(
                ApiKeyAuthenticationFilter(unionGraphsApiKey),
                UsernamePasswordAuthenticationFilter::class.java,
            )
        }

        return http
            .csrf { it.disable() } // Disable CSRF for stateless REST API
            .cors { it.configurationSource(corsConfigurationSource()) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { authz ->
                // Actuator endpoints are public
                authz
                    .requestMatchers("/actuator/**")
                    .permitAll()
                    // Union graph endpoints require API key (except public endpoints)
                    .requestMatchers("GET", "/v1/union-graphs/available")
                    .permitAll() // Available graphs list is public
                    .requestMatchers("GET", "/v1/union-graphs/{id}/info")
                    .permitAll() // Graph info endpoint is public
                    .requestMatchers("GET", "/v1/union-graphs/{id}/graph")
                    .permitAll() // Graph endpoint is public
                    .requestMatchers("/v1/union-graphs", "/v1/union-graphs/**")
                    .hasRole("API_USER")
                    .anyRequest()
                    .permitAll()
            }.build()
    }

    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val configuration = CorsConfiguration()
        configuration.allowedOriginPatterns = listOf("*")
        configuration.allowedMethods = listOf("GET", "POST", "DELETE", "OPTIONS")
        configuration.allowedHeaders =
            listOf(
                "Accept",
                "Content-Type",
                "Origin",
                "X-API-Key",
            )
        configuration.exposedHeaders =
            listOf(
                "Content-Type",
                "Cache-Control",
            )
        configuration.allowCredentials = true
        configuration.maxAge = 3600L // 1 hour

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", configuration)
        return source
    }
}
