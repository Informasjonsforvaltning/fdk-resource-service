package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.UnionGraphOrder
import org.slf4j.LoggerFactory
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.web.client.ResourceAccessException
import org.springframework.web.client.RestClientException
import org.springframework.web.client.RestTemplate
import java.util.concurrent.CompletableFuture

/**
 * Service for calling webhooks when union graph order status changes.
 */
@Service
class WebhookService(
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(WebhookService::class.java)

    /**
     * Calls the webhook URL asynchronously when order status changes.
     *
     * @param order The order with updated status
     * @param previousStatus The previous status (null if this is the initial status)
     */
    @Async
    fun callWebhook(
        order: UnionGraphOrder,
        previousStatus: UnionGraphOrder.GraphStatus?,
    ): CompletableFuture<Void> {
        if (order.webhookUrl.isNullOrBlank()) {
            return CompletableFuture.completedFuture(null)
        }

        return try {
            logger.info(
                "Calling webhook for order {}: {} -> {}",
                order.id,
                previousStatus ?: "null",
                order.status,
            )

            val payload =
                mapOf(
                    "id" to order.id,
                    "status" to order.status.name,
                    "previousStatus" to (previousStatus?.name),
                    "resourceTypes" to order.resourceTypes,
                    "updateTtlHours" to order.updateTtlHours,
                    "errorMessage" to order.errorMessage,
                    "createdAt" to order.createdAt.toString(),
                    "updatedAt" to order.updatedAt.toString(),
                    "processedAt" to (order.processedAt?.toString()),
                )

            val headers = HttpHeaders()
            headers.contentType = MediaType.APPLICATION_JSON

            val request =
                HttpEntity(
                    objectMapper.writeValueAsString(payload),
                    headers,
                )

            val response = restTemplate.postForEntity(order.webhookUrl, request, String::class.java)
            logger.info(
                "Successfully called webhook for order {}: HTTP {}",
                order.id,
                response.statusCode.value(),
            )
            CompletableFuture.completedFuture(null)
        } catch (e: ResourceAccessException) {
            logger.error(
                "Failed to call webhook for order {} (connection error): {}",
                order.id,
                e.message,
            )
            CompletableFuture.completedFuture(null)
        } catch (e: RestClientException) {
            logger.error("Failed to call webhook for order {}: {}", order.id, e.message)
            CompletableFuture.completedFuture(null)
        } catch (e: Exception) {
            logger.error("Unexpected error calling webhook for order {}: {}", order.id, e.message, e)
            CompletableFuture.completedFuture(null)
        }
    }
}
