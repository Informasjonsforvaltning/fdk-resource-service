package no.fdk.resourceservice.controller

import com.ninjasquad.springmockk.MockkBean
import io.mockk.every
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceSnapshot
import no.fdk.resourceservice.repository.UnionGraphResourceSnapshotRepository
import no.fdk.resourceservice.service.UnionGraphService
import org.junit.jupiter.api.Test
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.xpath
import java.time.Instant

@WebMvcTest(OaiPmhController::class)
class OaiPmhControllerTest : BaseControllerTest() {
    @MockkBean
    private lateinit var unionGraphService: UnionGraphService

    @MockkBean
    private lateinit var unionGraphResourceSnapshotRepository: UnionGraphResourceSnapshotRepository

    @Test
    fun `ListRecords should return resource records with pagination`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-6",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                resourceTypes = listOf("CONCEPT"),
                processedAt = Instant.parse("2024-01-01T12:00:00Z"),
            )

        val snapshots =
            listOf(
                UnionGraphResourceSnapshot(
                    unionGraphId = "test-order-6",
                    resourceId = "resource-1",
                    resourceType = "CONCEPT",
                    resourceGraphData =
                        "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" " +
                            "xmlns:ex=\"http://example.org/\"><rdf:Description rdf:about=\"http://example.org/resource1\">" +
                            "<rdf:type rdf:resource=\"http://example.org/Concept\"/></rdf:Description></rdf:RDF>",
                    resourceGraphFormat = "RDF_XML",
                ),
            )

        every { unionGraphService.getOrder("test-order-6") } returns order
        every {
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceTypePaginated(
                "test-order-6",
                "CONCEPT",
                0,
                50,
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns
            snapshots
        every {
            unionGraphResourceSnapshotRepository.countByUnionGraphIdAndResourceType(
                "test-order-6",
                "CONCEPT",
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns 1L

        // When & Then - First page should return resource-1 with resumption token if there are more
        val result1 =
            mockMvc
                .perform(
                    get("/v1/union-graphs/test-order-6/oai-pmh")
                        .param("verb", "ListRecords")
                        .param("metadataPrefix", "rdfxml"),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(xpath("/OAI-PMH/ListRecords/record/header/identifier[contains(., '/records/resource-1')]").exists())
                .andExpect(xpath("/OAI-PMH/ListRecords/record/header/datestamp").exists())
                .andExpect(xpath("/OAI-PMH/ListRecords/record/metadata").exists())
                .andReturn()

        // Verify identifier format is a valid URI
        val response1 = result1.response.contentAsString
        assert(response1.contains("/records/resource-1")) {
            "Identifier should contain the expected path"
        }

        // Second page with resumption token should return resource-2 without resumption token
        val snapshotsPage2 =
            listOf(
                UnionGraphResourceSnapshot(
                    unionGraphId = "test-order-6",
                    resourceId = "resource-2",
                    resourceType = "CONCEPT",
                    resourceGraphData =
                        "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" " +
                            "xmlns:ex=\"http://example.org/\"><rdf:Description rdf:about=\"http://example.org/resource2\">" +
                            "<rdf:type rdf:resource=\"http://example.org/Concept\"/></rdf:Description></rdf:RDF>",
                    resourceGraphFormat = "RDF_XML",
                ),
            )
        every {
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceTypePaginated(
                "test-order-6",
                "CONCEPT",
                1,
                50,
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns
            snapshotsPage2
        every {
            unionGraphResourceSnapshotRepository.countByUnionGraphIdAndResourceType(
                "test-order-6",
                "CONCEPT",
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns 2L

        val resumptionToken = "test-order-6:rdfxml:1"
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-6/oai-pmh")
                    .param("verb", "ListRecords")
                    .param("resumptionToken", resumptionToken),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(xpath("/OAI-PMH/ListRecords/record/header/identifier[contains(., '/records/resource-2')]").exists())
            .andExpect(xpath("/OAI-PMH/ListRecords/resumptionToken").doesNotExist())
    }

    @Test
    fun `should return 400 when verb is invalid`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-13",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-13") } returns order

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/test-order-13/oai-pmh").param("verb", "InvalidVerb"))
            .andExpect(status().isBadRequest)
            .andExpect(xpath("/OAI-PMH/error/@code").string("badVerb"))
    }

    @Test
    fun `should return 400 when verb is missing`() {
        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/test-order/oai-pmh"))
            .andExpect(status().isBadRequest)
            .andExpect(xpath("/OAI-PMH/error/@code").string("badVerb"))
    }

    @Test
    fun `Identify should return repository information`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-identify",
                name = "Test Union Graph",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        every { unionGraphService.getOrder("test-order-identify") } returns order

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/test-order-identify/oai-pmh").param("verb", "Identify"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(xpath("/OAI-PMH/Identify/repositoryName").string("FDK Union Graph: Test Union Graph"))
            .andExpect(xpath("/OAI-PMH/Identify/baseURL").exists())
            .andExpect(xpath("/OAI-PMH/Identify/protocolVersion").string("2.0"))
            .andExpect(xpath("/OAI-PMH/Identify/adminEmail").exists())
            .andExpect(xpath("/OAI-PMH/Identify/earliestDatestamp").exists())
            .andExpect(xpath("/OAI-PMH/Identify/deletedRecord").string("no"))
            .andExpect(xpath("/OAI-PMH/Identify/granularity").string("YYYY-MM-DDThh:mm:ssZ"))
    }

    @Test
    fun `Identify should return 404 when union graph does not exist`() {
        // Given
        every { unionGraphService.getOrder("non-existent") } returns null

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/non-existent/oai-pmh").param("verb", "Identify"))
            .andExpect(status().isNotFound)
            .andExpect(xpath("/OAI-PMH/error/@code").string("idDoesNotExist"))
    }

    @Test
    fun `ListMetadataFormats should return rdfxml format`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-formats",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-formats") } returns order

        // When & Then
        mockMvc
            .perform(get("/v1/union-graphs/test-order-formats/oai-pmh").param("verb", "ListMetadataFormats"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(xpath("/OAI-PMH/ListMetadataFormats/metadataFormat/metadataPrefix").string("rdfxml"))
            .andExpect(xpath("/OAI-PMH/ListMetadataFormats/metadataFormat/schema").exists())
            .andExpect(xpath("/OAI-PMH/ListMetadataFormats/metadataFormat/metadataNamespace").exists())
    }

    @Test
    fun `ListMetadataFormats should validate identifier when provided`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-formats",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        val snapshot =
            UnionGraphResourceSnapshot(
                unionGraphId = "test-order-formats",
                resourceId = "resource-1",
                resourceType = "CONCEPT",
                resourceGraphData = "<rdf:RDF></rdf:RDF>",
                resourceGraphFormat = "RDF_XML",
            )

        every { unionGraphService.getOrder("test-order-formats") } returns order
        every {
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceId(
                "test-order-formats",
                "resource-1",
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns snapshot

        // When & Then - valid identifier (using path-based format)
        val baseUrl = "http://localhost/v1/union-graphs/test-order-formats/oai-pmh"
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-formats/oai-pmh")
                    .param("verb", "ListMetadataFormats")
                    .param("identifier", "$baseUrl/records/resource-1"),
            ).andExpect(status().isOk)
            .andExpect(xpath("/OAI-PMH/ListMetadataFormats/metadataFormat/metadataPrefix").string("rdfxml"))

        // When & Then - invalid identifier
        every {
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceId(
                "test-order-formats",
                "invalid-resource",
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns null

        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-formats/oai-pmh")
                    .param("verb", "ListMetadataFormats")
                    .param("identifier", "$baseUrl/records/invalid-resource"),
            ).andExpect(status().isNotFound)
            .andExpect(xpath("/OAI-PMH/error/@code").string("idDoesNotExist"))
    }

    @Test
    fun `GetRecord should return a single record`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-get",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                processedAt = Instant.parse("2024-01-01T12:00:00Z"),
            )

        val snapshot =
            UnionGraphResourceSnapshot(
                unionGraphId = "test-order-get",
                resourceId = "resource-1",
                resourceType = "CONCEPT",
                resourceGraphData =
                    "<?xml version=\"1.0\"?><rdf:RDF xmlns:rdf=\"http://www.w3.org/1999/02/22-rdf-syntax-ns#\" " +
                        "xmlns:ex=\"http://example.org/\"><rdf:Description rdf:about=\"http://example.org/resource1\">" +
                        "<rdf:type rdf:resource=\"http://example.org/Concept\"/></rdf:Description></rdf:RDF>",
                resourceGraphFormat = "RDF_XML",
            )

        every { unionGraphService.getOrder("test-order-get") } returns order
        every {
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceId(
                "test-order-get",
                "resource-1",
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns snapshot

        // When & Then
        val baseUrl = "http://localhost/v1/union-graphs/test-order-get/oai-pmh"
        val result =
            mockMvc
                .perform(
                    get("/v1/union-graphs/test-order-get/oai-pmh")
                        .param("verb", "GetRecord")
                        .param("identifier", "$baseUrl/records/resource-1")
                        .param("metadataPrefix", "rdfxml"),
                ).andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(xpath("/OAI-PMH/GetRecord/record/header/identifier[contains(., '/records/resource-1')]").exists())
                .andExpect(xpath("/OAI-PMH/GetRecord/record/header/datestamp").exists())
                .andExpect(xpath("/OAI-PMH/GetRecord/record/metadata").exists())
                .andReturn()

        // Verify identifier is a valid URI
        val response = result.response.contentAsString
        assert(response.contains("/records/resource-1")) {
            "Identifier should contain the expected path"
        }
    }

    @Test
    fun `GetRecord should return 400 when identifier is missing`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-get",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-get") } returns order

        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-get/oai-pmh")
                    .param("verb", "GetRecord")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isBadRequest)
            .andExpect(xpath("/OAI-PMH/error/@code").string("badArgument"))
    }

    @Test
    fun `GetRecord should return 400 when metadataPrefix is missing`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-get",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-get") } returns order

        // When & Then
        val baseUrl = "http://localhost/v1/union-graphs/test-order-get/oai-pmh"
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-get/oai-pmh")
                    .param("verb", "GetRecord")
                    .param("identifier", "$baseUrl/records/resource-1"),
            ).andExpect(status().isBadRequest)
            .andExpect(xpath("/OAI-PMH/error/@code").string("badArgument"))
    }

    @Test
    fun `GetRecord should return 400 when metadataPrefix is not rdfxml`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-get",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-get") } returns order

        // When & Then
        val baseUrl = "http://localhost/v1/union-graphs/test-order-get/oai-pmh"
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-get/oai-pmh")
                    .param("verb", "GetRecord")
                    .param("identifier", "$baseUrl/records/resource-1")
                    .param("metadataPrefix", "oai_dc"),
            ).andExpect(status().isBadRequest)
            .andExpect(xpath("/OAI-PMH/error/@code").string("badArgument"))
    }

    @Test
    fun `GetRecord should return 404 when record does not exist`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-get",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-get") } returns order
        every {
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceId(
                "test-order-get",
                "non-existent",
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns null

        // When & Then
        val baseUrl = "http://localhost/v1/union-graphs/test-order-get/oai-pmh"
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-get/oai-pmh")
                    .param("verb", "GetRecord")
                    .param("identifier", "$baseUrl/records/non-existent")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isNotFound)
            .andExpect(xpath("/OAI-PMH/error/@code").string("idDoesNotExist"))
    }

    @Test
    fun `GetRecord should return 400 when identifier format is invalid`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-get",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-get") } returns order

        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-get/oai-pmh")
                    .param("verb", "GetRecord")
                    .param("identifier", "invalid-format")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isBadRequest)
            .andExpect(xpath("/OAI-PMH/error/@code").string("badArgument"))
    }

    @Test
    fun `ListIdentifiers should return record headers with pagination`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-identifiers",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                resourceTypes = listOf("CONCEPT"),
                processedAt = Instant.parse("2024-01-01T12:00:00Z"),
            )

        val snapshots =
            listOf(
                UnionGraphResourceSnapshot(
                    unionGraphId = "test-order-identifiers",
                    resourceId = "resource-1",
                    resourceType = "CONCEPT",
                    resourceGraphData = "<rdf:RDF></rdf:RDF>",
                    resourceGraphFormat = "RDF_XML",
                ),
            )

        every { unionGraphService.getOrder("test-order-identifiers") } returns order
        every {
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceTypePaginated(
                "test-order-identifiers",
                "CONCEPT",
                0,
                50,
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns snapshots
        every {
            unionGraphResourceSnapshotRepository.countByUnionGraphIdAndResourceType(
                "test-order-identifiers",
                "CONCEPT",
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns 1L

        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-identifiers/oai-pmh")
                    .param("verb", "ListIdentifiers")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_XML))
            .andExpect(xpath("/OAI-PMH/ListIdentifiers/header/identifier[contains(., '/records/resource-1')]").exists())
            .andExpect(xpath("/OAI-PMH/ListIdentifiers/header/datestamp").exists())
            // Should not have metadata element
            .andExpect(xpath("/OAI-PMH/ListIdentifiers/header/metadata").doesNotExist())
    }

    @Test
    fun `ListIdentifiers should support resumption tokens`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-identifiers",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                resourceTypes = listOf("CONCEPT"),
            )

        val snapshotsPage2 =
            listOf(
                UnionGraphResourceSnapshot(
                    unionGraphId = "test-order-identifiers",
                    resourceId = "resource-2",
                    resourceType = "CONCEPT",
                    resourceGraphData = "<rdf:RDF></rdf:RDF>",
                    resourceGraphFormat = "RDF_XML",
                ),
            )

        every { unionGraphService.getOrder("test-order-identifiers") } returns order
        every {
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceTypePaginated(
                "test-order-identifiers",
                "CONCEPT",
                1,
                50,
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns snapshotsPage2
        every {
            unionGraphResourceSnapshotRepository.countByUnionGraphIdAndResourceType(
                "test-order-identifiers",
                "CONCEPT",
                java.sql.Timestamp.valueOf("2099-12-31 23:59:59"),
            )
        } returns 2L

        // When & Then
        val resumptionToken = "test-order-identifiers:rdfxml:1"
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-identifiers/oai-pmh")
                    .param("verb", "ListIdentifiers")
                    .param("resumptionToken", resumptionToken),
            ).andExpect(status().isOk)
            .andExpect(xpath("/OAI-PMH/ListIdentifiers/header/identifier[contains(., '/records/resource-2')]").exists())
    }

    @Test
    fun `ListIdentifiers should return 400 when metadataPrefix is missing`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-identifiers",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-identifiers") } returns order

        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-identifiers/oai-pmh")
                    .param("verb", "ListIdentifiers"),
            ).andExpect(status().isBadRequest)
            .andExpect(xpath("/OAI-PMH/error/@code").string("badArgument"))
    }

    @Test
    fun `ListIdentifiers should return 400 when metadataPrefix is not rdfxml`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-identifiers",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
            )

        every { unionGraphService.getOrder("test-order-identifiers") } returns order

        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-identifiers/oai-pmh")
                    .param("verb", "ListIdentifiers")
                    .param("metadataPrefix", "oai_dc"),
            ).andExpect(status().isBadRequest)
            .andExpect(xpath("/OAI-PMH/error/@code").string("badArgument"))
    }

    @Test
    fun `ListIdentifiers should return 404 when union graph does not exist`() {
        // Given
        every { unionGraphService.getOrder("non-existent") } returns null

        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/non-existent/oai-pmh")
                    .param("verb", "ListIdentifiers")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isNotFound)
            .andExpect(xpath("/OAI-PMH/error/@code").string("idDoesNotExist"))
    }

    @Test
    fun `ListIdentifiers should return 404 when union graph is PENDING`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-pending",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.PENDING,
            )

        every { unionGraphService.getOrder("test-order-pending") } returns order

        // When & Then
        mockMvc
            .perform(
                get("/v1/union-graphs/test-order-pending/oai-pmh")
                    .param("verb", "ListIdentifiers")
                    .param("metadataPrefix", "rdfxml"),
            ).andExpect(status().isNotFound)
            .andExpect(xpath("/OAI-PMH/error/@code").string("idDoesNotExist"))
    }

    @Test
    fun `responseDate should be present and in ISO format for all responses`() {
        // Given
        val order =
            UnionGraphOrder(
                id = "test-order-response-date",
                name = "Test Order",
                status = UnionGraphOrder.GraphStatus.COMPLETED,
                createdAt = Instant.parse("2024-01-01T00:00:00Z"),
            )

        every { unionGraphService.getOrder("test-order-response-date") } returns order

        // When & Then - Test Identify verb
        val result =
            mockMvc
                .perform(get("/v1/union-graphs/test-order-response-date/oai-pmh").param("verb", "Identify"))
                .andExpect(status().isOk)
                .andExpect(content().contentType(MediaType.APPLICATION_XML))
                .andExpect(xpath("/OAI-PMH/responseDate").exists())
                .andReturn()

        // Extract responseDate and validate ISO format
        val responseContent = result.response.contentAsString
        val responseDateMatch =
            java.util.regex.Pattern
                .compile("<responseDate>(.*?)</responseDate>")
                .matcher(responseContent)
        assert(responseDateMatch.find()) { "responseDate element should be present" }

        val responseDate = responseDateMatch.group(1)
        // Validate ISO 8601 format: yyyy-MM-dd'T'HH:mm:ss'Z'
        val isoPattern =
            java.util.regex.Pattern
                .compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
        assert(isoPattern.matcher(responseDate).matches()) {
            "responseDate should be in ISO format (yyyy-MM-dd'T'HH:mm:ss'Z'), but was: $responseDate"
        }

        // Verify it can be parsed as Instant
        try {
            val parsedDate = Instant.parse(responseDate)
            assert(parsedDate.isAfter(Instant.now().minusSeconds(5))) {
                "responseDate should be recent (within last 5 seconds)"
            }
            assert(parsedDate.isBefore(Instant.now().plusSeconds(5))) {
                "responseDate should be recent (within next 5 seconds)"
            }
        } catch (e: Exception) {
            throw AssertionError("responseDate should be parseable as Instant: $responseDate", e)
        }
    }

    @Test
    fun `responseDate should be present in error responses`() {
        // When & Then - Test error response
        val result =
            mockMvc
                .perform(get("/v1/union-graphs/test-order/oai-pmh"))
                .andExpect(status().isBadRequest)
                .andExpect(xpath("/OAI-PMH/responseDate").exists())
                .andReturn()

        // Extract responseDate and validate ISO format
        val responseContent = result.response.contentAsString
        val responseDateMatch =
            java.util.regex.Pattern
                .compile("<responseDate>(.*?)</responseDate>")
                .matcher(responseContent)
        assert(responseDateMatch.find()) { "responseDate element should be present in error responses" }

        val responseDate = responseDateMatch.group(1)
        // Validate ISO 8601 format: yyyy-MM-dd'T'HH:mm:ss'Z'
        val isoPattern =
            java.util.regex.Pattern
                .compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z")
        assert(isoPattern.matcher(responseDate).matches()) {
            "responseDate should be in ISO format (yyyy-MM-dd'T'HH:mm:ss'Z'), but was: $responseDate"
        }
    }
}
