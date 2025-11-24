package no.fdk.resourceservice.integration

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import no.fdk.resourceservice.service.ResourceService
import no.fdk.resourceservice.service.UnionGraphService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.MockMvc
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath
import org.springframework.transaction.support.TransactionTemplate
import java.io.File

/**
 * Integration tests for OAI-PMH endpoint.
 *
 * These tests verify the full OAI-PMH flow with real union graph snapshots.
 */
@SpringBootTest
@AutoConfigureMockMvc
class OaiPmhIntegrationTest : BaseIntegrationTest() {
    @Autowired
    private lateinit var mockMvc: MockMvc

    @Autowired
    private lateinit var unionGraphOrderRepository: UnionGraphOrderRepository

    @Autowired
    private lateinit var resourceRepository: ResourceRepository

    @Autowired
    private lateinit var unionGraphService: UnionGraphService

    @Autowired
    private lateinit var resourceService: ResourceService

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    @BeforeEach
    fun setUp() {
        // Clean up test data
        unionGraphOrderRepository.deleteAll()
        resourceRepository.deleteAll()

        // Clean up test data
        val storageDir = File("/tmp/test-hdt-integration")
        if (storageDir.exists()) {
            storageDir.deleteRecursively()
        }
    }

    @Test
    fun `ListRecords should return all resources with metadata`() {
        // Given
        val orderId = createAndProcessUnionGraph()

        // Get all resources that were created
        val allResources =
            transactionTemplate.execute {
                resourceRepository.findByResourceTypeAndDeletedFalseWithGraphDataPaginated("CONCEPT", 0, 1000)
            }!!

        // When & Then - Paginate through all resources using resumption tokens
        var resourceOffset = 0
        var resumptionToken: String? = null
        val processedResourceIds = mutableSetOf<String>()

        while (resourceOffset < allResources.size) {
            val requestBuilder =
                get("/v1/union-graphs/$orderId/oai-pmh")
                    .param("verb", "ListRecords")
                    .apply {
                        if (resumptionToken != null) {
                            param("resumptionToken", resumptionToken)
                        } else {
                            param("metadataPrefix", "rdfxml")
                        }
                    }

            val resultActions = mockMvc.perform(requestBuilder)

            val result =
                resultActions
                    .andExpect(status().isOk)
                    .andExpect(content().contentType(MediaType.APPLICATION_XML))
                    .andReturn()

            val responseContent = result.response.contentAsString

            // Extract all resource identifiers from the response
            val identifierPattern = Regex("""<identifier>([^<]+)</identifier>""")
            val identifiers = identifierPattern.findAll(responseContent).map { it.groupValues[1] }.toList()

            // Process all resources found in this page
            for (identifier in identifiers) {
                // Parse the new URI format: {baseURL}/records/{resourceId}
                // Extract resourceId from the path
                try {
                    val uri = java.net.URI(identifier)
                    val path = uri.path
                    // Expected format: /v1/union-graphs/{orderId}/oai-pmh/records/{resourceId}
                    val pathParts = path.split("/").filter { it.isNotEmpty() }
                    if (pathParts.size >= 6 &&
                        pathParts[0] == "v1" &&
                        pathParts[1] == "union-graphs" &&
                        pathParts[2] == orderId &&
                        pathParts[3] == "oai-pmh" &&
                        pathParts[4] == "records"
                    ) {
                        val encodedResourceId = pathParts[5]
                        val resourceId = java.net.URLDecoder.decode(encodedResourceId, "UTF-8")
                        processedResourceIds.add(resourceId)
                    }
                } catch (e: Exception) {
                    // Skip invalid identifiers
                }
            }

            // Extract resumption token if present
            val tokenMatch = Regex("""<resumptionToken[^>]*>([^<]+)</resumptionToken>""").find(responseContent)
            resumptionToken = tokenMatch?.groupValues?.get(1)

            // Check if there are more resources
            if (resumptionToken != null) {
                // Parse offset from resumption token (format: orderId:metadataPrefix:offset)
                val tokenParts = resumptionToken.split(":")
                if (tokenParts.size >= 3) {
                    resourceOffset = tokenParts[2].toIntOrNull() ?: resourceOffset
                } else {
                    resourceOffset += identifiers.size
                }
            } else {
                // Last page - no more resources
                break
            }
        }

        // Verify we processed all resources
        assertEquals(
            allResources.size,
            processedResourceIds.size,
            "Should have processed all resources. Processed: ${processedResourceIds.size}, Total: ${allResources.size}",
        )
    }

    @Test
    fun `should return 404 when union graph not found`() {
        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/non-existent/oai-pmh")
                    .param("verb", "ListRecords")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isNotFound)
            .andExpect(xpath("/OAI-PMH/error/@code").string("idDoesNotExist"))
    }

    @Test
    fun `should return 404 when union graph not completed`() {
        // Given
        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.save(
                    UnionGraphOrder(
                        id = "test-order-pending",
                        name = "Test Order Pending",
                        status = UnionGraphOrder.GraphStatus.PENDING,
                    ),
                )
            }!!

        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/${order.id}/oai-pmh")
                    .param("verb", "ListRecords")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isNotFound)
            .andExpect(xpath("/OAI-PMH/error/@code").string("idDoesNotExist"))
    }

    /**
     * Helper method to create a union graph with some test resources and process it.
     * Returns the order ID.
     */
    private fun createAndProcessUnionGraph(): String {
        val orderId: String =
            transactionTemplate.execute {
                // Create test resources
                val timestamp = System.currentTimeMillis()
                for (i in 1..25) { // Create 25 resources to test pagination
                    val resourceId = "test-concept-oai-$i"
                    val turtleData =
                        """
                        @prefix ex: <http://example.org/> .
                        @prefix dct: <http://purl.org/dc/terms/> .
                        
                        <https://example.com/concept$i> a ex:Concept ;
                            dct:title "Test Concept $i" ;
                            dct:description "Description for concept $i" .
                        """.trimIndent()
                    resourceService.storeResourceGraphData(
                        resourceId,
                        ResourceType.CONCEPT,
                        turtleData,
                        "TURTLE",
                        timestamp + i,
                    )
                }

                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-oai",
                            name = "Test Order OAI",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                order.id
            }!!

        // Ensure transaction is committed and order is visible
        transactionTemplate.execute {
            unionGraphOrderRepository.flush()
        }

        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // Process the order (runs outside transaction context)
        unionGraphService.processOrder(order, "test-instance")

        // Process batches incrementally until completion
        var shouldContinue = true
        var maxIterations = 1000 // Safety limit
        var iterations = 0
        while (shouldContinue && iterations < maxIterations) {
            shouldContinue = unionGraphService.processNextBatch(orderId)
            iterations++
            if (!shouldContinue) {
                break
            }
            // Small delay to avoid tight loop
            Thread.sleep(10)
        }

        if (iterations >= maxIterations) {
            throw AssertionError("Reached max iterations ($maxIterations) while processing order $orderId")
        }

        // Verify it's completed
        val completedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        assertEquals(UnionGraphOrder.GraphStatus.COMPLETED, completedOrder.status)

        return orderId
    }
}
