package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import no.fdk.resourceservice.model.UnionGraphOrder
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.time.Instant

class WebhookServiceTest {
    private lateinit var restTemplate: RestTemplate
    private lateinit var objectMapper: ObjectMapper
    private lateinit var webhookService: WebhookService

    @BeforeEach
    fun setUp() {
        restTemplate = mockk(relaxed = true)
        objectMapper = ObjectMapper()
        webhookService = WebhookService(restTemplate, objectMapper)
    }

    @Test
    fun `callWebhook should not call webhook when URL is null`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order",
                webhookUrl = null,
            )

        // When
        val result = webhookService.callWebhook(order, null)

        // Then
        assertNotNull(result)
        verify(exactly = 0) { restTemplate.postForEntity(any<String>(), any(), String::class.java) }
    }

    @Test
    fun `callWebhook should not call webhook when URL is blank`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order",
                webhookUrl = "",
            )

        // When
        val result = webhookService.callWebhook(order, null)

        // Then
        assertNotNull(result)
        verify(exactly = 0) { restTemplate.postForEntity(any<String>(), any(), String::class.java) }
    }

    @Test
    fun `callWebhook should call webhook with correct payload`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                resourceTypes = listOf("CONCEPT"),
                updateTtlHours = 24,
                webhookUrl = "https://example.com/webhook",
                errorMessage = null,
                createdAt = Instant.now(),
                updatedAt = Instant.now(),
                processedAt = Instant.now(),
            )
        val previousStatus = UnionGraphOrder.GraphStatus.PENDING

        val responseEntity =
            org.springframework.http.ResponseEntity
                .ok("OK")
        every {
            restTemplate.postForEntity(
                "https://example.com/webhook",
                any(),
                String::class.java,
            )
        } returns responseEntity

        // When
        val result = webhookService.callWebhook(order, previousStatus)

        // Then
        assertNotNull(result)
        val payloadSlot = slot<org.springframework.http.HttpEntity<*>>()
        verify {
            restTemplate.postForEntity(
                "https://example.com/webhook",
                capture(payloadSlot),
                String::class.java,
            )
        }

        val payload = payloadSlot.captured.body as String
        assertNotNull(payload)
        assert(payload.contains("test-order"))
        assert(payload.contains("COMPLETED"))
        assert(payload.contains("PENDING"))
    }

    @Test
    fun `callWebhook should handle connection errors gracefully`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order",
                webhookUrl = "https://example.com/webhook",
            )

        every {
            restTemplate.postForEntity(any<String>(), any(), String::class.java)
        } throws ResourceAccessException("Connection refused")

        // When
        val result = webhookService.callWebhook(order, null)

        // Then
        assertNotNull(result)
        // Should not throw exception
    }

    @Test
    fun `callWebhook should handle RestClientException gracefully`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order",
                webhookUrl = "https://example.com/webhook",
            )

        val exception = mockk<RestClientException>(relaxed = true)
        every { exception.message } returns "Rest client error"
        every {
            restTemplate.postForEntity(any<String>(), any(), String::class.java)
        } throws exception

        // When
        val result = webhookService.callWebhook(order, null)

        // Then
        assertNotNull(result)
        // Should not throw exception
    }

    @Test
    fun `callWebhook should handle generic exceptions gracefully`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order",
                webhookUrl = "https://example.com/webhook",
            )

        every {
            restTemplate.postForEntity(any<String>(), any(), String::class.java)
        } throws RuntimeException("Unexpected error")

        // When
        val result = webhookService.callWebhook(order, null)

        // Then
        assertNotNull(result)
        // Should not throw exception
    }

    @Test
    fun `callWebhook should include all order fields in payload`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-123",
                status = UnionGraphOrder.GraphStatus.FAILED,
                resourceTypes = listOf("DATASET", "CONCEPT"),
                updateTtlHours = 48,
                webhookUrl = "https://example.com/webhook",
                errorMessage = "Test error message",
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2024-01-02T00:00:00Z"),
                processedAt = Instant.parse("2024-01-02T01:00:00Z"),
            )

        val responseEntity =
            org.springframework.http.ResponseEntity
                .ok("OK")
        every {
            restTemplate.postForEntity(any<String>(), any(), String::class.java)
        } returns responseEntity

        // When
        webhookService.callWebhook(order, UnionGraphOrder.GraphStatus.PROCESSING)

        // Then
        val payloadSlot = slot<org.springframework.http.HttpEntity<*>>()
        verify {
            restTemplate.postForEntity(any<String>(), capture(payloadSlot), String::class.java)
        }

        val payload = payloadSlot.captured.body as String
        assert(payload.contains("test-order-123"))
        assert(payload.contains("FAILED"))
        assert(payload.contains("PROCESSING"))
        assert(payload.contains("DATASET"))
        assert(payload.contains("CONCEPT"))
        assert(payload.contains("48"))
        assert(payload.contains("Test error message"))
    }
}
