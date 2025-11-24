package no.fdk.resourceservice.integration

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.service.ResourceService
import org.apache.jena.query.Dataset
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.system.Txn
import org.apache.jena.tdb2.TDB2Factory
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.test.context.TestPropertySource
import org.springframework.transaction.support.TransactionTemplate
import java.io.ByteArrayInputStream
import java.nio.file.Files

/**
 * Performance comparison test for different write methods to union graph.
 * Compares:
 * 1. Direct write to unionModel (current approach - parse individually, add in sub-batches)
 * 2. Merged JSON string (combine all JSON-LD into one string, parse once)
 */
@TestPropertySource(
    properties = [
        "app.union-graphs.delete-enabled=true",
        "app.union-graphs.reset-enabled=true",
    ],
)
class UnionGraphWriteMethodComparisonTest : BaseIntegrationTest() {
    @Autowired
    private lateinit var resourceRepository: ResourceRepository

    @Autowired
    private lateinit var resourceService: ResourceService

    @Autowired
    private lateinit var objectMapper: ObjectMapper

    @Autowired
    private lateinit var transactionTemplate: TransactionTemplate

    private val logger = LoggerFactory.getLogger(UnionGraphWriteMethodComparisonTest::class.java)

    @BeforeEach
    fun setUp() {
        resourceRepository.deleteAll()
    }

    /**
     * Compares direct write vs merged JSON string approaches.
     */
    @Test
    fun `compare direct write vs merged JSON string`() {
        val resourceCount = 500
        val batchSize = 250

        // Create test resources
        transactionTemplate.execute {
            val timestamp = System.currentTimeMillis()
            for (i in 1..resourceCount) {
                val resourceId = "test-concept-comparison-$i"
                val turtleData = buildLargeConceptTurtle(i)
                resourceService.storeResourceGraphData(resourceId, ResourceType.CONCEPT, turtleData, "TURTLE", timestamp + i)
            }
        }

        // Get all resources
        val resources =
            transactionTemplate.execute {
                resourceRepository.findByResourceTypeAndDeletedFalseWithGraphDataPaginated(ResourceType.CONCEPT.name, 0, resourceCount)
            }!!

        logger.info("=== Comparing Write Methods for {} resources ===", resourceCount)

        // Test 1: Direct write to unionModel (current approach)
        val directWriteTime = testDirectWriteApproach(resources, batchSize)
        logger.info("Direct write approach: {} ms", String.format("%.2f", directWriteTime))

        // Test 2: Merged JSON string approach
        val mergedJsonTime = testMergedJsonApproach(resources, batchSize)
        logger.info("Merged JSON string approach: {} ms", String.format("%.2f", mergedJsonTime))

        val speedup = if (mergedJsonTime > 0) directWriteTime / mergedJsonTime else 0.0
        logger.info("Speedup: {}x ({})", String.format("%.2f", speedup), if (speedup > 1.0) "merged JSON faster" else "direct write faster")
    }

    /**
     * Test direct write approach: parse each resource individually, accumulate in temp model, add in sub-batches.
     */
    private fun testDirectWriteApproach(
        resources: List<*>,
        batchSize: Int,
    ): Double {
        val tempDir = Files.createTempDirectory("jena-tdb2-direct-").toFile()
        var dataset: Dataset? = null

        try {
            dataset = TDB2Factory.connectDataset(tempDir.absolutePath)
            val unionModel = dataset!!.defaultModel

            val startTime = System.nanoTime()
            val subBatchSize = 50

            resources.chunked(batchSize).forEach { batch ->
                batch.chunked(subBatchSize).forEach { subBatch ->
                    val tempModel = ModelFactory.createDefaultModel()
                    try {
                        for (resource in subBatch) {
                            val resourceEntity = resource as no.fdk.resourceservice.model.ResourceEntity
                            val graphData = resourceEntity.resourceGraphData
                            val graphFormat = resourceEntity.resourceGraphFormat ?: "TURTLE"
                            if (graphData != null) {
                                val lang =
                                    when (graphFormat.uppercase()) {
                                        "TURTLE" -> Lang.TURTLE
                                        "JSON-LD", "JSONLD" -> Lang.JSONLD
                                        "RDF/XML", "RDFXML" -> Lang.RDFXML
                                        "N-TRIPLES", "NTRIPLES" -> Lang.NTRIPLES
                                        else -> Lang.TURTLE
                                    }
                                ByteArrayInputStream(graphData.toByteArray()).use { inputStream ->
                                    RDFDataMgr.read(tempModel, inputStream, lang)
                                }
                            }
                        }

                        if (!tempModel.isEmpty) {
                            Txn.executeWrite(dataset) {
                                unionModel.add(tempModel.listStatements())
                            }
                        }
                    } finally {
                        tempModel.close()
                    }
                }
            }

            val size = Txn.calculateRead(dataset) { unionModel.size() }
            val elapsed = (System.nanoTime() - startTime) / 1_000_000.0

            logger.info(
                "  Direct write: {} statements in {} ms ({} ms/resource)",
                size,
                String.format("%.2f", elapsed),
                String.format(
                    "%.2f",
                    elapsed / resources.size,
                ),
            )
            return elapsed
        } finally {
            dataset?.close()
            tempDir.deleteRecursively()
        }
    }

    /**
     * Test merged JSON string approach: combine all JSON-LD into one string, parse once per batch.
     */
    private fun testMergedJsonApproach(
        resources: List<*>,
        batchSize: Int,
    ): Double {
        val tempDir = Files.createTempDirectory("jena-tdb2-merged-").toFile()
        var dataset: Dataset? = null

        try {
            dataset = TDB2Factory.connectDataset(tempDir.absolutePath)
            val unionModel = dataset!!.defaultModel

            val startTime = System.nanoTime()

            resources.chunked(batchSize).forEach { batch ->
                // Build merged Turtle string
                val turtleWriter = java.io.StringWriter()

                for (resource in batch) {
                    val resourceEntity = resource as no.fdk.resourceservice.model.ResourceEntity
                    val graphData = resourceEntity.resourceGraphData
                    val graphFormat = resourceEntity.resourceGraphFormat ?: "TURTLE"
                    if (graphData != null) {
                        // Concatenate Turtle strings
                        turtleWriter.append(graphData).append("\n")
                    }
                }

                val combinedTurtleString = turtleWriter.toString()

                // Parse the merged Turtle string once
                Txn.executeWrite(dataset) {
                    ByteArrayInputStream(combinedTurtleString.toByteArray()).use { inputStream ->
                        RDFDataMgr.read(unionModel, inputStream, Lang.TURTLE)
                    }
                }
            }

            val size = Txn.calculateRead(dataset) { unionModel.size() }
            val elapsed = (System.nanoTime() - startTime) / 1_000_000.0

            logger.info(
                "  Merged JSON: {} statements in {} ms ({} ms/resource)",
                size,
                String.format("%.2f", elapsed),
                String.format(
                    "%.2f",
                    elapsed / resources.size,
                ),
            )
            return elapsed
        } finally {
            dataset?.close()
            tempDir.deleteRecursively()
        }
    }

    /**
     * Builds a large concept JSON-LD with many properties (same as performance test).
     */
    private fun buildLargeConceptTurtle(index: Int): String {
        val baseUri = "https://example.com/concept$index"
        return """
            @prefix skos: <http://www.w3.org/2004/02/skos/core#> .
            @prefix dct: <http://purl.org/dc/terms/> .
            
            <$baseUri> a skos:Concept ;
                skos:prefLabel "Preferred Label $index"@nb, "Preferred Label $index"@en ;
                skos:altLabel "Alternative Label $index-1"@nb, "Alternative Label $index-2"@en, "Alternative Label $index-3"@nb, "Alternative Label $index-4"@en, "Alternative Label $index-5"@nb ;
                dct:description "This is a comprehensive description for concept $index. It contains detailed information about the concept, its purpose, usage, and relationships. This description is intentionally long to simulate real-world data with substantial content. This description is intentionally long to simulate real-world data with substantial content. This description is intentionally long to simulate real-world data with substantial content."@nb ;
                dct:subject <https://example.com/subject/$index-1>, <https://example.com/subject/$index-2>, <https://example.com/subject/$index-3>, <https://example.com/subject/$index-4>, <https://example.com/subject/$index-5> ;
                skos:broader <https://example.com/concept/broader-$index-1>, <https://example.com/concept/broader-$index-2>, <https://example.com/concept/broader-$index-3> ;
                skos:narrower <https://example.com/concept/narrower-$index-1>, <https://example.com/concept/narrower-$index-2>, <https://example.com/concept/narrower-$index-3>, <https://example.com/concept/narrower-$index-4> ;
                dct:publisher <https://example.com/organization/publisher-$index> ;
                dct:created "2024-01-${String.format("%02d", (index % 28) + 1)}"^^<http://www.w3.org/2001/XMLSchema#date> .
            """.trimIndent()
    }

    private fun buildLargeConceptJsonLd(index: Int): Map<String, Any> {
        val baseUri = "https://example.com/concept$index"
        return mapOf(
            "@id" to baseUri,
            "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
            "http://www.w3.org/2004/02/skos/core#prefLabel" to
                listOf(
                    mapOf("@value" to "Preferred Label $index", "@language" to "nb"),
                    mapOf("@value" to "Preferred Label $index", "@language" to "en"),
                ),
            "http://www.w3.org/2004/02/skos/core#altLabel" to
                (1..5).map { i ->
                    mapOf("@value" to "Alternative Label $index-$i", "@language" to if (i % 2 == 0) "nb" else "en")
                },
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
                ),
            "http://purl.org/dc/terms/subject" to
                (1..5).map { i ->
                    mapOf("@id" to "https://example.com/subject/$index-$i")
                },
            "http://www.w3.org/2004/02/skos/core#broader" to
                (1..3).map { i ->
                    mapOf("@id" to "https://example.com/concept/broader-$index-$i")
                },
            "http://www.w3.org/2004/02/skos/core#narrower" to
                (1..4).map { i ->
                    mapOf("@id" to "https://example.com/concept/narrower-$index-$i")
                },
            "http://purl.org/dc/terms/publisher" to
                listOf(
                    mapOf("@id" to "https://example.com/organization/publisher-$index"),
                ),
            "http://purl.org/dc/terms/created" to
                listOf(
                    mapOf(
                        "@value" to "2024-01-${String.format("%02d", (index % 28) + 1)}",
                        "@type" to "http://www.w3.org/2001/XMLSchema#date",
                    ),
                ),
        )
    }
}
