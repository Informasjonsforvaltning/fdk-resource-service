package no.fdk.resourceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import no.fdk.resourceservice.service.ResourceService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate

/**
 * Performance tests for union graph building.
 *
 * These tests measure the time taken for different parts of the union graph build process
 * to identify performance bottlenecks.
 */
@TestPropertySource(
    properties = [
        "app.union-graphs.delete-enabled=true",
        "app.union-graphs.reset-enabled=true",
    ],
)
class UnionGraphPerformanceTest : BaseIntegrationTest() {
    @Autowired
    private lateinit var unionGraphOrderRepository: UnionGraphOrderRepository

    @Autowired
    private lateinit var resourceRepository: ResourceRepository

    @Autowired
    private lateinit var unionGraphService: no.fdk.resourceservice.service.UnionGraphService

    @Autowired
    private lateinit var resourceService: ResourceService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val logger = LoggerFactory.getLogger(UnionGraphPerformanceTest::class.java)

    @BeforeEach
    fun setUp() {
        // Clean up test data
        unionGraphOrderRepository.deleteAll()
        resourceRepository.deleteAll()
    }

    /**
     * Measures performance of building a union graph with a small number of resources.
     * This test helps identify baseline performance and bottlenecks.
     */
    @Test
    fun `measure performance for small union graph`() {
        val resourceCount = 10
        val testId = System.currentTimeMillis()
        val orderId: String =
            transactionTemplate.execute {
                // Create test resources with unique IDs to avoid conflicts
                val timestamp = System.currentTimeMillis()
                for (i in 1..resourceCount) {
                    val resourceId = "test-concept-perf-$testId-$i"
                    val jsonLd =
                        mapOf(
                            "@id" to "https://example.com/concept$i",
                            "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
                            "http://purl.org/dc/terms/title" to listOf(mapOf("@value" to "Test Concept $i")),
                            "http://purl.org/dc/terms/description" to listOf(mapOf("@value" to "Description for concept $i")),
                        )
                    val turtleData =
                        """<https://example.com/concept$i> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                            |<http://purl.org/dc/terms/title> "Test Concept $i" ;
                            |<http://purl.org/dc/terms/description> "Description for concept $i" .
                        """.trimMargin()
                    resourceService.storeResourceGraphData(resourceId, ResourceType.CONCEPT, turtleData, "TURTLE", timestamp + i)
                }

                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-performance-$testId",
                            name = "Performance Test Order",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                // Flush to ensure resources and order are committed
                resourceRepository.flush()
                unionGraphOrderRepository.flush()
                order.id
            }!!

        // Verify resources are committed and visible in a new transaction
        val resourceCountCheck =
            transactionTemplate.execute {
                resourceRepository.countByResourceTypeAndDeletedFalse("CONCEPT")
            }!!
        assertTrue(
            resourceCountCheck >= resourceCount,
            "Resources should be committed before processing. Found: $resourceCountCheck, expected: at least $resourceCount",
        )

        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // Measure total processing time
        val startTime = System.nanoTime()
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
            logger.error("Reached max iterations ({}) while processing order {}", maxIterations, orderId)
        }

        val totalTime = (System.nanoTime() - startTime) / 1_000_000.0 // Convert to milliseconds

        // Verify completion
        val completedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        if (completedOrder.status == UnionGraphOrder.GraphStatus.FAILED) {
            logger.error("Order failed with error: {}", completedOrder.errorMessage)
        }
        assertEquals(
            UnionGraphOrder.GraphStatus.COMPLETED,
            completedOrder.status,
            "Order should be completed. Error: ${completedOrder.errorMessage}",
        )

        logger.info("=== Performance Results for {} resources ===", resourceCount)
        logger.info("Total processing time: {:.2f} ms ({:.2f} seconds)", totalTime, totalTime / 1000.0)
        logger.info("Average time per resource: {:.2f} ms", totalTime / resourceCount)
        logger.info("Resources per second: {:.2f}", resourceCount / (totalTime / 1000.0))
    }

    /**
     * Measures performance of building a union graph with a medium number of resources.
     * This helps identify if performance degrades with scale.
     */
    @Test
    fun `measure performance for medium union graph`() {
        val resourceCount = 100
        val testId = System.currentTimeMillis()
        val orderId: String =
            transactionTemplate.execute {
                // Create test resources with unique IDs to avoid conflicts
                val timestamp = System.currentTimeMillis()
                for (i in 1..resourceCount) {
                    val resourceId = "test-concept-perf-$testId-$i"
                    val jsonLd =
                        mapOf(
                            "@id" to "https://example.com/concept$i",
                            "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
                            "http://purl.org/dc/terms/title" to listOf(mapOf("@value" to "Test Concept $i")),
                            "http://purl.org/dc/terms/description" to listOf(mapOf("@value" to "Description for concept $i")),
                        )
                    val turtleData =
                        """<https://example.com/concept$i> a <http://www.w3.org/2004/02/skos/core#Concept> ;
                            |<http://purl.org/dc/terms/title> "Test Concept $i" ;
                            |<http://purl.org/dc/terms/description> "Description for concept $i" .
                        """.trimMargin()
                    resourceService.storeResourceGraphData(resourceId, ResourceType.CONCEPT, turtleData, "TURTLE", timestamp + i)
                }

                val order =
                    unionGraphOrderRepository.save(
                        UnionGraphOrder(
                            id = "test-order-performance-medium-$testId",
                            name = "Performance Test Order Medium",
                            status = UnionGraphOrder.GraphStatus.PENDING,
                            resourceTypes = listOf("CONCEPT"),
                        ),
                    )
                // Flush to ensure resources and order are committed
                resourceRepository.flush()
                unionGraphOrderRepository.flush()
                order.id
            }!!

        // Verify resources are committed and visible in a new transaction
        val resourceCountCheck =
            transactionTemplate.execute {
                resourceRepository.countByResourceTypeAndDeletedFalse("CONCEPT")
            }!!
        assertTrue(
            resourceCountCheck >= resourceCount,
            "Resources should be committed before processing. Found: $resourceCountCheck, expected: at least $resourceCount",
        )

        val order =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!

        // Measure total processing time
        val startTime = System.nanoTime()
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
            logger.error("Reached max iterations ({}) while processing order {}", maxIterations, orderId)
        }

        val totalTime = (System.nanoTime() - startTime) / 1_000_000.0 // Convert to milliseconds

        // Verify completion
        val completedOrder =
            transactionTemplate.execute {
                unionGraphOrderRepository.findById(orderId).get()
            }!!
        if (completedOrder.status == UnionGraphOrder.GraphStatus.FAILED) {
            logger.error("Order failed with error: {}", completedOrder.errorMessage)
        }
        assertEquals(
            UnionGraphOrder.GraphStatus.COMPLETED,
            completedOrder.status,
            "Order should be completed. Error: ${completedOrder.errorMessage}",
        )

        logger.info("=== Performance Results for {} resources ===", resourceCount)
        logger.info(
            "Total processing time: {} ms ({} seconds)",
            String.format("%.2f", totalTime),
            String.format("%.2f", totalTime / 1000.0),
        )
        logger.info("Average time per resource: {} ms", String.format("%.2f", totalTime / resourceCount))
        logger.info("Resources per second: {}", String.format("%.2f", resourceCount / (totalTime / 1000.0)))
    }

    /**
     * Measures performance of buildUnionGraph method directly to isolate timing.
     * This helps identify if the bottleneck is in buildUnionGraph or processOrder overhead.
     */
    @Test
    fun `measure buildUnionGraph performance directly`() {
        val resourceCount = 50
        transactionTemplate.execute {
            // Create test resources
            val timestamp = System.currentTimeMillis()
            for (i in 1..resourceCount) {
                val resourceId = "test-concept-direct-$i"
                // Create a large resource with many properties to simulate real-world data
                val turtleData = buildLargeConceptTurtle(i)
                resourceService.storeResourceGraphData(resourceId, ResourceType.CONCEPT, turtleData, "TURTLE", timestamp + i)
            }
        }

        // Measure buildUnionGraph directly
        val orderId = "perf-test-direct"
        val startTime = System.nanoTime()
        val result =
            transactionTemplate.execute {
                unionGraphService.buildUnionGraph(listOf(ResourceType.CONCEPT), orderId = orderId)
            }
        val buildTime = (System.nanoTime() - startTime) / 1_000_000.0

        assertTrue(result == true, "buildUnionGraph should return true on success")

        logger.info("=== buildUnionGraph Performance for {} resources ===", resourceCount)
        logger.info("Build time: {} ms ({} seconds)", String.format("%.2f", buildTime), String.format("%.2f", buildTime / 1000.0))
        logger.info("Average time per resource: {} ms", String.format("%.2f", buildTime / resourceCount))
        logger.info("Resources per second: {}", String.format("%.2f", resourceCount / (buildTime / 1000.0)))
    }

    /**
     * Measures performance with a large number of resources (500).
     * Tests scalability and identifies performance degradation at scale.
     */
    @Test
    fun `measure buildUnionGraph performance for 500 resources`() {
        val resourceCount = 500
        transactionTemplate.execute {
            // Create test resources
            val timestamp = System.currentTimeMillis()
            for (i in 1..resourceCount) {
                val resourceId = "test-concept-large-$i"
                // Create a large resource with many properties to simulate real-world data
                val turtleData = buildLargeConceptTurtle(i)
                resourceService.storeResourceGraphData(resourceId, ResourceType.CONCEPT, turtleData, "TURTLE", timestamp + i)
            }
        }

        // Measure buildUnionGraph directly
        val orderId = "perf-test-500"
        val startTime = System.nanoTime()
        val result =
            transactionTemplate.execute {
                unionGraphService.buildUnionGraph(listOf(ResourceType.CONCEPT), orderId = orderId)
            }
        val buildTime = (System.nanoTime() - startTime) / 1_000_000.0

        assertTrue(result == true, "buildUnionGraph should return true on success")

        logger.info("=== buildUnionGraph Performance for {} resources ===", resourceCount)
        logger.info("Build time: {} ms ({} seconds)", String.format("%.2f", buildTime), String.format("%.2f", buildTime / 1000.0))
        logger.info("Average time per resource: {} ms", String.format("%.2f", buildTime / resourceCount))
        logger.info("Resources per second: {}", String.format("%.2f", resourceCount / (buildTime / 1000.0)))
    }

    /**
     * Measures performance with a very large number of resources (1000).
     * Tests scalability at production-like scale.
     */
    @Test
    fun `measure buildUnionGraph performance for 1000 resources`() {
        val resourceCount = 1000
        transactionTemplate.execute {
            // Create test resources
            val timestamp = System.currentTimeMillis()
            for (i in 1..resourceCount) {
                val resourceId = "test-concept-xlarge-$i"
                // Create a large resource with many properties to simulate real-world data
                val turtleData = buildLargeConceptTurtle(i)
                resourceService.storeResourceGraphData(resourceId, ResourceType.CONCEPT, turtleData, "TURTLE", timestamp + i)
            }
        }

        // Measure buildUnionGraph directly
        val orderId = "perf-test-1000"
        val startTime = System.nanoTime()
        val result =
            transactionTemplate.execute {
                unionGraphService.buildUnionGraph(listOf(ResourceType.CONCEPT), orderId = orderId)
            }
        val buildTime = (System.nanoTime() - startTime) / 1_000_000.0

        assertTrue(result == true, "buildUnionGraph should return true on success")

        logger.info("=== buildUnionGraph Performance for {} resources ===", resourceCount)
        logger.info("Build time: {} ms ({} seconds)", String.format("%.2f", buildTime), String.format("%.2f", buildTime / 1000.0))
        logger.info("Average time per resource: {} ms", String.format("%.2f", buildTime / resourceCount))
        logger.info("Resources per second: {}", String.format("%.2f", resourceCount / (buildTime / 1000.0)))
    }

    /**
     * Measures performance with an extremely large number of resources (2000).
     * Tests scalability at very high scale to identify performance limits.
     */
    @Test
    fun `measure buildUnionGraph performance for 2000 resources`() {
        val resourceCount = 2000
        transactionTemplate.execute {
            // Create test resources
            val timestamp = System.currentTimeMillis()
            for (i in 1..resourceCount) {
                val resourceId = "test-concept-xxlarge-$i"
                // Create a large resource with many properties to simulate real-world data
                val turtleData = buildLargeConceptTurtle(i)
                resourceService.storeResourceGraphData(resourceId, ResourceType.CONCEPT, turtleData, "TURTLE", timestamp + i)
            }
        }

        // Measure buildUnionGraph directly
        val orderId = "perf-test-direct"
        val startTime = System.nanoTime()
        val result =
            transactionTemplate.execute {
                unionGraphService.buildUnionGraph(listOf(ResourceType.CONCEPT), orderId = orderId)
            }
        val buildTime = (System.nanoTime() - startTime) / 1_000_000.0

        assertTrue(result == true, "buildUnionGraph should return true on success")

        logger.info("=== buildUnionGraph Performance for {} resources ===", resourceCount)
        logger.info("Build time: {} ms ({} seconds)", String.format("%.2f", buildTime), String.format("%.2f", buildTime / 1000.0))
        logger.info("Average time per resource: {} ms", String.format("%.2f", buildTime / resourceCount))
        logger.info("Resources per second: {}", String.format("%.2f", resourceCount / (buildTime / 1000.0)))
    }

    /**
     * Measures performance with multiple resource types to test mixed workloads.
     */
    @Test
    fun `measure buildUnionGraph performance for multiple resource types`() {
        val resourcesPerType = 250
        val resourceTypes = listOf(ResourceType.CONCEPT, ResourceType.DATASET, ResourceType.DATA_SERVICE)
        val totalResourceCount = resourcesPerType * resourceTypes.size

        transactionTemplate.execute {
            // Create test resources for each type
            val timestamp = System.currentTimeMillis()
            for (type in resourceTypes) {
                for (i in 1..resourcesPerType) {
                    val resourceId = "test-${type.name.lowercase()}-mixed-$i"
                    val jsonLd =
                        mapOf(
                            "@id" to "https://example.com/${type.name.lowercase()}$i",
                            "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
                            "http://purl.org/dc/terms/title" to listOf(mapOf("@value" to "Test ${type.name} $i")),
                            "http://purl.org/dc/terms/description" to
                                listOf(mapOf("@value" to "Description for ${type.name.lowercase()} $i")),
                        )
                    val turtleData = buildLargeConceptTurtle(i)
                    resourceService.storeResourceGraphData(resourceId, type, turtleData, "TURTLE", timestamp + i)
                }
            }
        }

        // Measure buildUnionGraph directly
        val orderId = "perf-test-multi"
        val startTime = System.nanoTime()
        val result =
            transactionTemplate.execute {
                unionGraphService.buildUnionGraph(resourceTypes, orderId = orderId)
            }
        val buildTime = (System.nanoTime() - startTime) / 1_000_000.0

        assertTrue(result == true, "buildUnionGraph should return true on success")

        logger.info(
            "=== buildUnionGraph Performance for {} resources ({} types, {} per type) ===",
            totalResourceCount,
            resourceTypes.size,
            resourcesPerType,
        )
        logger.info("Build time: {} ms ({} seconds)", String.format("%.2f", buildTime), String.format("%.2f", buildTime / 1000.0))
        logger.info("Average time per resource: {} ms", String.format("%.2f", buildTime / totalResourceCount))
        logger.info("Resources per second: {}", String.format("%.2f", totalResourceCount / (buildTime / 1000.0)))
    }

    /**
     * Builds a large concept JSON-LD with many properties to simulate real-world resources.
     * This creates resources with 50-100+ triples per resource.
     */
    private fun buildLargeConceptTurtle(index: Int): String {
        val baseUri = "https://example.com/concept$index"
        return """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            @prefix dct: <http://purl.org/dc/terms/> .
            
            <$baseUri> a skos:Concept ;
                skos:prefLabel "Preferred Label $index"@nb, "Preferred Label $index"@en, "Preferred Label $index"@nn ;
                skos:altLabel "Alternative Label $index-1"@nb, "Alternative Label $index-2"@en, "Alternative Label $index-3"@nb, "Alternative Label $index-4"@en, "Alternative Label $index-5"@nb ;
                skos:hiddenLabel "Hidden Label $index-1"@nb, "Hidden Label $index-2"@nb, "Hidden Label $index-3"@nb ;
                dct:description "This is a comprehensive description for concept $index. It contains detailed information about the concept, its purpose, usage, and relationships. This description is intentionally long to simulate real-world data with substantial content. This description is intentionally long to simulate real-world data with substantial content. This description is intentionally long to simulate real-world data with substantial content."@nb,
                    "English description for concept $index with detailed information about its characteristics and usage patterns. Extended content to increase the size of the resource. Extended content to increase the size of the resource."@en ;
                dct:abstract "Abstract description for concept $index providing a high-level overview of the concept's meaning and application."@nb ;
                dct:subject <https://example.com/subject/$index-1>, <https://example.com/subject/$index-2>, <https://example.com/subject/$index-3>, <https://example.com/subject/$index-4>, <https://example.com/subject/$index-5> ;
                dct:classification <https://example.com/classification/$index-1>, <https://example.com/classification/$index-2>, <https://example.com/classification/$index-3> ;
                skos:broader <https://example.com/concept/broader-$index-1>, <https://example.com/concept/broader-$index-2>, <https://example.com/concept/broader-$index-3> ;
                skos:narrower <https://example.com/concept/narrower-$index-1>, <https://example.com/concept/narrower-$index-2>, <https://example.com/concept/narrower-$index-3>, <https://example.com/concept/narrower-$index-4> ;
                skos:related <https://example.com/concept/related-$index-1>, <https://example.com/concept/related-$index-2>, <https://example.com/concept/related-$index-3>, <https://example.com/concept/related-$index-4>, <https://example.com/concept/related-$index-5> ;
                dct:publisher <https://example.com/organization/publisher-$index> ;
                dct:creator <https://example.com/organization/creator-$index-1>, <https://example.com/organization/creator-$index-2> .
            """.trimIndent()
    }

    private fun buildLargeConceptJsonLd(index: Int): Map<String, Any> {
        val baseUri = "https://example.com/concept$index"
        return mapOf(
            "@id" to baseUri,
            "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
            // Multiple labels in different languages
            "http://www.w3.org/2004/02/skos/core#prefLabel" to
                listOf(
                    mapOf("@value" to "Preferred Label $index", "@language" to "nb"),
                    mapOf("@value" to "Preferred Label $index", "@language" to "en"),
                    mapOf("@value" to "Preferred Label $index", "@language" to "nn"),
                ),
            "http://www.w3.org/2004/02/skos/core#altLabel" to
                (1..5).map { i ->
                    mapOf("@value" to "Alternative Label $index-$i", "@language" to if (i % 2 == 0) "nb" else "en")
                },
            "http://www.w3.org/2004/02/skos/core#hiddenLabel" to
                (1..3).map { i ->
                    mapOf("@value" to "Hidden Label $index-$i", "@language" to "nb")
                },
            // Multiple descriptions
            "http://purl.org/dc/terms/description" to
                listOf(
                    mapOf(
                        "@value" to
                            (
                                "This is a comprehensive description for concept $index. " +
                                    "It contains detailed information about the concept, its purpose, usage, and relationships. " +
                                    "This description is intentionally long to simulate real-world data with substantial content. ".repeat(
                                        3,
                                    )
                            ),
                        "@language" to "nb",
                    ),
                    mapOf(
                        "@value" to
                            (
                                "English description for concept $index with detailed information " +
                                    "about its characteristics and usage patterns. " +
                                    "Extended content to increase the size of the resource. ".repeat(2)
                            ),
                        "@language" to "en",
                    ),
                ),
            "http://purl.org/dc/terms/abstract" to
                listOf(
                    mapOf(
                        "@value" to
                            (
                                "Abstract description for concept $index providing a high-level overview of " +
                                    "the concept's meaning and application."
                            ),
                        "@language" to "nb",
                    ),
                ),
            // Subject and classification
            "http://purl.org/dc/terms/subject" to
                (1..5).map { i ->
                    mapOf("@id" to "https://example.com/subject/$index-$i")
                },
            "http://purl.org/dc/terms/classification" to
                (1..3).map { i ->
                    mapOf("@id" to "https://example.com/classification/$index-$i")
                },
            // Broader and narrower relationships
            "http://www.w3.org/2004/02/skos/core#broader" to
                (1..3).map { i ->
                    mapOf("@id" to "https://example.com/concept/broader-$index-$i")
                },
            "http://www.w3.org/2004/02/skos/core#narrower" to
                (1..4).map { i ->
                    mapOf("@id" to "https://example.com/concept/narrower-$index-$i")
                },
            "http://www.w3.org/2004/02/skos/core#related" to
                (1..5).map { i ->
                    mapOf("@id" to "https://example.com/concept/related-$index-$i")
                },
            // Contact information
            "http://purl.org/dc/terms/publisher" to
                listOf(
                    mapOf("@id" to "https://example.com/organization/publisher-$index"),
                ),
            "http://purl.org/dc/terms/creator" to
                (1..2).map { i ->
                    mapOf("@id" to "https://example.com/person/creator-$index-$i")
                },
            "http://purl.org/dc/terms/contributor" to
                (1..3).map { i ->
                    mapOf("@id" to "https://example.com/person/contributor-$index-$i")
                },
            // Dates
            "http://purl.org/dc/terms/created" to
                listOf(
                    mapOf(
                        "@value" to "2024-01-${String.format("%02d", (index % 28) + 1)}",
                        "@type" to "http://www.w3.org/2001/XMLSchema#date",
                    ),
                ),
            "http://purl.org/dc/terms/modified" to
                listOf(
                    mapOf(
                        "@value" to "2024-12-${String.format("%02d", (index % 28) + 1)}",
                        "@type" to "http://www.w3.org/2001/XMLSchema#date",
                    ),
                ),
            "http://purl.org/dc/terms/issued" to
                listOf(
                    mapOf(
                        "@value" to "2024-06-${String.format("%02d", (index % 28) + 1)}",
                        "@type" to "http://www.w3.org/2001/XMLSchema#date",
                    ),
                ),
            // Additional metadata
            "http://purl.org/dc/terms/identifier" to
                listOf(
                    mapOf("@value" to "CONCEPT-$index"),
                    mapOf("@value" to "CON-${String.format("%05d", index)}"),
                ),
            "http://purl.org/dc/terms/language" to
                listOf(
                    mapOf("@id" to "http://publications.europa.eu/resource/authority/language/NOR"),
                    mapOf("@id" to "http://publications.europa.eu/resource/authority/language/ENG"),
                ),
            "http://purl.org/dc/terms/coverage" to
                (1..2).map { i ->
                    mapOf("@value" to "Geographic coverage $index-$i", "@language" to "nb")
                },
            "http://purl.org/dc/terms/spatial" to
                (1..2).map { i ->
                    mapOf("@id" to "https://example.com/place/spatial-$index-$i")
                },
            "http://purl.org/dc/terms/temporal" to
                (1..2).map { i ->
                    mapOf("@id" to "https://example.com/period/temporal-$index-$i")
                },
            // SKOS-specific properties
            "https://data.norge.no/vocabulary/skosno#bruksområde" to
                (1..3).map { i ->
                    mapOf("@value" to "Bruksområde $index-$i", "@language" to "nb")
                },
            "https://data.norge.no/vocabulary/skosno#definisjon" to
                listOf(
                    mapOf(
                        "@value" to "Formell definisjon av begrep $index med detaljert beskrivelse av begrepets betydning og anvendelse.",
                        "@language" to "nb",
                    ),
                ),
            "https://data.norge.no/vocabulary/skosno#eksempel" to
                (1..2).map { i ->
                    mapOf("@value" to "Eksempel $index-$i: Dette er et eksempel på bruk av begrepet.", "@language" to "nb")
                },
            // Version and status
            "http://purl.org/dc/terms/version" to listOf(mapOf("@value" to "1.${index % 10}")),
            "http://www.w3.org/ns/adms#status" to
                listOf(mapOf("@id" to "http://publications.europa.eu/resource/authority/dataset-status/COMPLETED")),
        )
    }
}
