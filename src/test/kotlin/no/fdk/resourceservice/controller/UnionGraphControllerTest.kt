package no.fdk.resourceservice.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.fdk.resourceservice.config.UnionGraphFeatureConfig
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.service.UnionGraphService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.http.MediaType
import org.springframework.test.context.TestPropertySource
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import java.time.Instant

@WebMvcTest(UnionGraphController::class)
@TestPropertySource(
    properties = [
        "app.union-graphs.delete-enabled=true",
        "app.union-graphs.reset-enabled=true",
    ],
)
class UnionGraphControllerTest : BaseControllerTest() {
    @MockkBean
    private lateinit var unionGraphService: UnionGraphService

    @Autowired
    private lateinit var unionGraphFeatureConfig: UnionGraphFeatureConfig

    @TestConfiguration
    class TestConfig {
        @Bean
        fun unionGraphFeatureConfig(): UnionGraphFeatureConfig =
            UnionGraphFeatureConfig(
                deleteEnabled = true,
                resetEnabled = true,
            )
    }

    @Test
    fun `createOrder should return 201 when new order created`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-1",
                status = UnionGraphOrder.GraphStatus.PENDING,
                resourceTypes = listOf("CONCEPT"),
                updateTtlHours = 24,
                webhookUrl = "https://example.com/webhook",
            )

        every {
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                24,
                "https://example.com/webhook",
                null,
                false,
                UnionGraphOrder.GraphFormat.JSON_LD,
                UnionGraphOrder.GraphStyle.PRETTY,
                true,
            )
        } returns UnionGraphService.CreateOrderResult(order, isNew = true)

        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["CONCEPT"],
                            "updateTtlHours": 24,
                            "webhookUrl": "https://example.com/webhook"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(header().string("Location", "/v1/union-graphs/test-order-1"))
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("test-order-1"))
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.resourceTypes[0]").value("CONCEPT"))
            .andExpect(jsonPath("$.updateTtlHours").value(24))
            .andExpect(jsonPath("$.webhookUrl").value("https://example.com/webhook"))
    }

    @Test
    fun `createOrder should return 400 when updateTtlHours is 1`() {
        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["CONCEPT"],
                            "updateTtlHours": 1
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `createOrder should return 400 when updateTtlHours is 2`() {
        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["CONCEPT"],
                            "updateTtlHours": 2
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `createOrder should return 400 when updateTtlHours is 3`() {
        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["CONCEPT"],
                            "updateTtlHours": 3
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `createOrder should accept updateTtlHours of 0`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-0",
                status = UnionGraphOrder.GraphStatus.PENDING,
                resourceTypes = listOf("CONCEPT"),
                updateTtlHours = 0,
            )

        every {
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                0,
                null,
                null,
                false,
                UnionGraphOrder.GraphFormat.JSON_LD,
                UnionGraphOrder.GraphStyle.PRETTY,
                true,
            )
        } returns UnionGraphService.CreateOrderResult(order, isNew = true)

        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["CONCEPT"],
                            "updateTtlHours": 0
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `createOrder should accept updateTtlHours greater than 3`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-4",
                status = UnionGraphOrder.GraphStatus.PENDING,
                resourceTypes = listOf("CONCEPT"),
                updateTtlHours = 4,
            )

        every {
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                4,
                null,
                null,
                false,
            )
        } returns UnionGraphService.CreateOrderResult(order, isNew = true)

        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["CONCEPT"],
                            "updateTtlHours": 4
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
    }

    @Test
    fun `createOrder should return 409 when order already exists`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "existing-order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                resourceTypes = listOf("DATASET"),
            )

        every {
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                0,
                null,
                null,
                false,
            )
        } returns UnionGraphService.CreateOrderResult(order, isNew = false)

        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["DATASET"]
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isConflict)
            .andExpect(jsonPath("$.id").value("existing-order"))
            .andExpect(jsonPath("$.status").value("COMPLETED"))
    }

    @Test
    fun `createOrder should return 400 for non-HTTPS webhook URL`() {
        // Given
        every {
            unionGraphService.createOrder(
                null,
                0,
                "http://example.com/webhook",
                null,
                false,
                UnionGraphOrder.GraphFormat.JSON_LD,
                UnionGraphOrder.GraphStyle.PRETTY,
                true,
            )
        } throws IllegalArgumentException("Webhook URL must use HTTPS protocol")

        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "webhookUrl": "http://example.com/webhook"
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `createOrder should accept empty request body`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-2",
                status = UnionGraphOrder.GraphStatus.PENDING,
            )

        every {
            unionGraphService.createOrder(
                null,
                0,
                null,
                null,
                false,
                UnionGraphOrder.GraphFormat.JSON_LD,
                UnionGraphOrder.GraphStyle.PRETTY,
                true,
            )
        } returns UnionGraphService.CreateOrderResult(order, isNew = true)

        // When & Then
        mockMvc
            .perform(post("/v1/union-graphs"))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value("test-order-2"))
    }

    @Test
    fun `createOrder should accept dataset filters`() {
        // Given
        val filters =
            UnionGraphResourceFilters(
                dataset =
                    UnionGraphResourceFilters.DatasetFilters(
                        isOpenData = true,
                        isRelatedToTransportportal = false,
                    ),
            )
        val order =
            UnionGraphOrder(
                id = "dataset-order",
                status = UnionGraphOrder.GraphStatus.PENDING,
                resourceTypes = listOf("DATASET"),
                resourceFilters = filters,
            )

        every {
            unionGraphService.createOrder(
                listOf(ResourceType.DATASET),
                0,
                null,
                filters,
                false,
                UnionGraphOrder.GraphFormat.JSON_LD,
                UnionGraphOrder.GraphStyle.PRETTY,
                true,
            )
        } returns UnionGraphService.CreateOrderResult(order, isNew = true)

        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["DATASET"],
                            "resourceFilters": {
                                "dataset": {
                                    "isOpenData": true,
                                    "isRelatedToTransportportal": false
                                }
                            }
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isCreated)
            .andExpect(jsonPath("$.resourceFilters.dataset.isOpenData").value(true))
            .andExpect(jsonPath("$.resourceFilters.dataset.isRelatedToTransportportal").value(false))
    }

    @Test
    fun `createOrder should return 400 when dataset filters lack dataset type`() {
        // Given
        val filters =
            UnionGraphResourceFilters(
                dataset = UnionGraphResourceFilters.DatasetFilters(isOpenData = true),
            )

        every {
            unionGraphService.createOrder(
                listOf(ResourceType.CONCEPT),
                0,
                null,
                filters,
                false,
                UnionGraphOrder.GraphFormat.JSON_LD,
                UnionGraphOrder.GraphStyle.PRETTY,
                true,
            )
        } throws IllegalArgumentException("Dataset filters require the DATASET resource type")

        // When & Then
        mockMvc
            .perform(
                post("/v1/union-graphs")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                            "resourceTypes": ["CONCEPT"],
                            "resourceFilters": {
                                "dataset": {
                                    "isOpenData": true
                                }
                            }
                        }
                        """.trimIndent(),
                    ),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `getAllOrders should return list of orders`() {
        // Given
        val orders =
            listOf(
                UnionGraphOrder(
                    id = "order-1",
                    status = UnionGraphOrder.GraphStatus.COMPLETED,
                    resourceTypes = listOf("CONCEPT"),
                    updateTtlHours = 24,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
                UnionGraphOrder(
                    id = "order-2",
                    status = UnionGraphOrder.GraphStatus.PENDING,
                    resourceTypes = null,
                    updateTtlHours = 0,
                    createdAt = Instant.now(),
                    updatedAt = Instant.now(),
                ),
            )

        every { unionGraphService.getAllOrders() } returns orders

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").value("order-1"))
            .andExpect(jsonPath("$[0].status").value("COMPLETED"))
            .andExpect(jsonPath("$[1].id").value("order-2"))
            .andExpect(jsonPath("$[1].status").value("PENDING"))
    }

    @Test
    fun `getStatus should return order status`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-3",
                status = UnionGraphOrder.GraphStatus.PROCESSING,
                resourceTypes = listOf("DATASET"),
                updateTtlHours = 12,
                webhookUrl = "https://example.com/webhook",
                errorMessage = null,
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
                updatedAt = Instant.parse("2024-01-02T00:00:00Z"),
                processedAt = null,
            )

        every { unionGraphService.getOrder("test-order-3") } returns order

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/test-order-3/status"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("test-order-3"))
            .andExpect(jsonPath("$.status").value("PROCESSING"))
            .andExpect(jsonPath("$.resourceTypes[0]").value("DATASET"))
            .andExpect(jsonPath("$.updateTtlHours").value(12))
            .andExpect(jsonPath("$.webhookUrl").value("https://example.com/webhook"))
    }

    @Test
    fun `getStatus should return 404 when order not found`() {
        // Given
        every { unionGraphService.getOrder("non-existent") } returns null

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/non-existent/status"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `resetOrder should reset order when enabled`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-4",
                status = UnionGraphOrder.GraphStatus.PENDING,
            )

        every { unionGraphService.resetOrderToPending("test-order-4") } returns order

        // When & Then
        mockMvc
            .perform(post("/v1/union-graphs/test-order-4/reset"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value("test-order-4"))
            .andExpect(jsonPath("$.status").value("PENDING"))
    }

    @Test
    fun `resetOrder should return 404 when order not found`() {
        // Given
        every { unionGraphService.resetOrderToPending("non-existent") } returns null

        // When & Then
        mockMvc
            .perform(post("/v1/union-graphs/non-existent/reset"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getGraph should return graph in stored format`() {
        // Given
        val graphData = """{"@graph":[{"@id":"https://example.com/resource"}]}"""
        val order =
            UnionGraphOrder(
                id = "test-order-5",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                graphData = graphData,
                graphFormat = UnionGraphOrder.GraphFormat.JSON_LD,
                graphStyle = UnionGraphOrder.GraphStyle.PRETTY,
                graphExpandUris = true,
            )

        every { unionGraphService.getOrder("test-order-5") } returns order

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/test-order-5/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "ld+json")))
            .andExpect(content().string(graphData))
    }

    @Test
    fun `getGraph should return 404 when order not found`() {
        // Given
        every { unionGraphService.getOrder("non-existent") } returns null

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/non-existent/graph"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getGraph should return 400 when order not completed`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-6",
                status = UnionGraphOrder.GraphStatus.PENDING,
            )

        every { unionGraphService.getOrder("test-order-6") } returns order

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/test-order-6/graph"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `getGraph should return graph in stored Turtle format`() {
        // Given
        val graphData = """<https://example.com/resource> a <http://example.org/Resource> ."""
        val order =
            UnionGraphOrder(
                id = "test-order-7",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                graphData = graphData,
                graphFormat = UnionGraphOrder.GraphFormat.TURTLE,
                graphStyle = UnionGraphOrder.GraphStyle.PRETTY,
                graphExpandUris = false,
            )

        every { unionGraphService.getOrder("test-order-7") } returns order

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/test-order-7/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("text/turtle")))
            .andExpect(content().string(graphData))
    }

    @Test
    fun `deleteOrder should delete order when enabled`() {
        // Given
        every { unionGraphService.deleteOrder("test-order-8") } returns true

        // When & Then
        mockMvc
            .perform(delete("/v1/union-graphs/test-order-8"))
            .andExpect(status().isNoContent)
    }

    @Test
    fun `deleteOrder should return 404 when order not found`() {
        // Given
        every { unionGraphService.deleteOrder("non-existent") } returns false

        // When & Then
        mockMvc
            .perform(delete("/v1/union-graphs/non-existent"))
            .andExpect(status().isNotFound)
    }
}
