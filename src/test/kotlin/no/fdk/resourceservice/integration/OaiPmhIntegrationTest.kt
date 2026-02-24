package no.fdk.resourceservice.integration

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import no.fdk.resourceservice.repository.UnionGraphResourceSnapshotRepository
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
import java.sql.Timestamp

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

    @Autowired
    private lateinit var unionGraphResourceSnapshotRepository: UnionGraphResourceSnapshotRepository

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

        // Expected count = snapshots for this order (same beforeTimestamp as OAI-PMH controller uses for completed orders)
        val beforeTimestamp = Timestamp.valueOf("2099-12-31 23:59:59")
        val expectedCount =
            transactionTemplate.execute {
                unionGraphResourceSnapshotRepository.countByUnionGraphId(orderId, beforeTimestamp)
            }!!

        // When - Paginate through all pages using resumption tokens until none returned
        var resumptionToken: String? = null
        val processedResourceIds = mutableSetOf<String>()

        do {
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

            val result =
                mockMvc
                    .perform(requestBuilder)
                    .andExpect(status().isOk)
                    .andExpect(content().contentType(MediaType.APPLICATION_XML))
                    .andReturn()

            val responseContent = result.response.contentAsString

            val identifierPattern = Regex("""<identifier>([^<]+)</identifier>""")
            val identifiers = identifierPattern.findAll(responseContent).map { it.groupValues[1] }.toList()

            for (identifier in identifiers) {
                try {
                    val uri = java.net.URI(identifier)
                    val path = uri.path
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

            val tokenMatch = Regex("""<resumptionToken[^>]*>([^<]+)</resumptionToken>""").find(responseContent)
            resumptionToken = tokenMatch?.groupValues?.get(1)
        } while (resumptionToken != null)

        // Then - we must get exactly as many records as the snapshot table has for this order
        assertEquals(
            expectedCount.toInt(),
            processedResourceIds.size,
            "Should have processed all resources from ListRecords. Processed: ${processedResourceIds.size}, expected (snapshot count): $expectedCount",
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
    fun `should return 404 when union graph is FAILED`() {
        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.save(
                    UnionGraphOrder(
                        id = "test-order-failed",
                        name = "Test Order Failed",
                        status = UnionGraphOrder.GraphStatus.FAILED,
                    ),
                )
            }!!

        mockMvc
            .perform(
                get("/v1/union-graphs/${order.id}/oai-pmh")
                    .param("verb", "ListRecords")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isNotFound)
            .andExpect(xpath("/OAI-PMH/error/@code").string("idDoesNotExist"))
    }

    /**
     * Exercises the filtered snapshot repository queries (from/until/set) against real PostgreSQL.
     * Without explicit CAST of nullable params in the native SQL, PostgreSQL fails with
     * "could not determine data type of parameter $4". These tests would fail before the CAST fix.
     */
    @Test
    fun `ListIdentifiers with from and until params runs filtered repository query`() {
        val orderId = createAndProcessUnionGraph()
        mockMvc
            .perform(
                get("/v1/union-graphs/$orderId/oai-pmh")
                    .param("verb", "ListIdentifiers")
                    .param("metadataPrefix", "rdfxml")
                    .param("from", "2020-01-01T00:00:00Z")
                    .param("until", "2030-01-01T00:00:00Z"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(xpath("/OAI-PMH/ListIdentifiers").exists())
    }

    @Test
    fun `ListIdentifiers with only from param runs filtered repository query with null until and set`() {
        val orderId = createAndProcessUnionGraph()
        mockMvc
            .perform(
                get("/v1/union-graphs/$orderId/oai-pmh")
                    .param("verb", "ListIdentifiers")
                    .param("metadataPrefix", "rdfxml")
                    .param("from", "2020-01-01T00:00:00Z"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(xpath("/OAI-PMH/ListIdentifiers").exists())
    }

    @Test
    fun `ListIdentifiers with only set param runs filtered repository query with null from and until`() {
        val orderId = createAndProcessUnionGraph()
        mockMvc
            .perform(
                get("/v1/union-graphs/$orderId/oai-pmh")
                    .param("verb", "ListIdentifiers")
                    .param("metadataPrefix", "rdfxml")
                    .param("set", "org:986252932"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(xpath("/OAI-PMH/ListIdentifiers").exists())
    }

    @Test
    fun `ListRecords with from and until params runs filtered repository query`() {
        val orderId = createAndProcessUnionGraph()
        mockMvc
            .perform(
                get("/v1/union-graphs/$orderId/oai-pmh")
                    .param("verb", "ListRecords")
                    .param("metadataPrefix", "rdfxml")
                    .param("from", "2020-01-01T00:00:00Z")
                    .param("until", "2030-01-01T00:00:00Z"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(xpath("/OAI-PMH/ListRecords").exists())
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
