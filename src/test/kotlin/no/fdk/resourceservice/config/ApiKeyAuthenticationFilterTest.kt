package no.fdk.resourceservice.config

import io.mockk.every
import io.mockk.verify
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.security.core.context.SecurityContextHolder
import java.lang.reflect.Method

class ApiKeyAuthenticationFilterTest {
    private val validApiKey = "test-api-key-123"
    private val filter = ApiKeyAuthenticationFilter(validApiKey)

    @BeforeEach
    fun clearContext() {
        SecurityContextHolder.clearContext()
    }

    private fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val method: Method =
            ApiKeyAuthenticationFilter::class.java.getDeclaredMethod(
                "doFilterInternal",
                HttpServletRequest::class.java,
                HttpServletResponse::class.java,
                FilterChain::class.java,
            )
        method.isAccessible = true
        method.invoke(filter, request, response, filterChain)
    }

    @Test
    fun `doFilterInternal continues chain when API key header is empty or blank`() {
        val request = io.mockk.mockk<HttpServletRequest>(relaxed = true)
        val response = io.mockk.mockk<HttpServletResponse>(relaxed = true)
        val filterChain = io.mockk.mockk<FilterChain>(relaxed = true)

        every { request.getHeader("X-API-Key") } returns "   "

        doFilterInternal(request, response, filterChain)

        verify(exactly = 1) { filterChain.doFilter(request, response) }
    }

    @Test
    fun `doFilterInternal continues chain when API key header is null`() {
        val request = io.mockk.mockk<HttpServletRequest>(relaxed = true)
        val response = io.mockk.mockk<HttpServletResponse>(relaxed = true)
        val filterChain = io.mockk.mockk<FilterChain>(relaxed = true)

        every { request.getHeader("X-API-Key") } returns null

        doFilterInternal(request, response, filterChain)

        verify(exactly = 1) { filterChain.doFilter(request, response) }
    }

    @Test
    fun `doFilterInternal sets authentication when valid API key has surrounding whitespace`() {
        val request = io.mockk.mockk<HttpServletRequest>(relaxed = true)
        val response = io.mockk.mockk<HttpServletResponse>(relaxed = true)
        val filterChain = io.mockk.mockk<FilterChain>(relaxed = true)

        every { request.getHeader("X-API-Key") } returns "  $validApiKey  "

        doFilterInternal(request, response, filterChain)

        assertNotNull(SecurityContextHolder.getContext().authentication)
        verify(exactly = 1) { filterChain.doFilter(request, response) }
    }

    @Test
    fun `doFilterInternal returns 401 when invalid API key is sent`() {
        val request = io.mockk.mockk<HttpServletRequest>(relaxed = true)
        val response = io.mockk.mockk<HttpServletResponse>(relaxed = true)
        val filterChain = io.mockk.mockk<FilterChain>(relaxed = true)
        val writer = io.mockk.mockk<java.io.PrintWriter>(relaxed = true)

        every { request.getHeader("X-API-Key") } returns "wrong-key"
        every { response.writer } returns writer

        doFilterInternal(request, response, filterChain)

        verify(exactly = 1) { response.setStatus(401) }
        verify(exactly = 0) { filterChain.doFilter(request, response) }
    }

    @Test
    fun `doFilterInternal sets authentication and continues when valid API key is sent`() {
        val request = io.mockk.mockk<HttpServletRequest>(relaxed = true)
        val response = io.mockk.mockk<HttpServletResponse>(relaxed = true)
        val filterChain = io.mockk.mockk<FilterChain>(relaxed = true)

        every { request.getHeader("X-API-Key") } returns validApiKey

        doFilterInternal(request, response, filterChain)

        val auth = SecurityContextHolder.getContext().authentication
        assertNotNull(auth)
        assertEquals("api-user", auth?.principal)
        verify(exactly = 1) { filterChain.doFilter(request, response) }
    }
}
