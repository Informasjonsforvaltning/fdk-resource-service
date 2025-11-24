package no.fdk.resourceservice.config

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Filter for API key authentication.
 * Validates the API key from the X-API-Key header and sets authentication context.
 */
class ApiKeyAuthenticationFilter(
    private val apiKey: String,
) : OncePerRequestFilter() {
    companion object {
        private const val API_KEY_HEADER = "X-API-Key"
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val apiKeyHeader = request.getHeader(API_KEY_HEADER)

        if (apiKeyHeader != null && apiKeyHeader == apiKey) {
            // Valid API key - set authentication
            val authorities = listOf(SimpleGrantedAuthority("ROLE_API_USER"))
            val authentication =
                UsernamePasswordAuthenticationToken(
                    "api-user",
                    null,
                    authorities,
                ).apply {
                    details = WebAuthenticationDetailsSource().buildDetails(request)
                }

            SecurityContextHolder.getContext().authentication = authentication
        } else if (apiKeyHeader != null) {
            // Invalid API key
            response.status = HttpServletResponse.SC_UNAUTHORIZED
            response.contentType = "application/json"
            response.writer.write("""{"error":"Invalid API key"}""")
            return
        }
        // No API key header - let it through (will be rejected by security config if required)
        filterChain.doFilter(request, response)
    }
}
