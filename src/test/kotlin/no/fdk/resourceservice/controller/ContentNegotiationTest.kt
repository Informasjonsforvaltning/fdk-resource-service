package no.fdk.resourceservice.controller

import io.mockk.every
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ContentNegotiationTest : BaseControllerTest() {
    @Test
    fun `should return JSON-LD when Accept header is application ld+json`() {
        // Given
        val testDatasetId = "test-dataset-id"
        val testDatasetUri = "http://example.com/dataset"
        val jsonLdData =
            mapOf(
                "@id" to testDatasetUri,
                "@type" to "http://example.org/Dataset",
                "http://purl.org/dc/elements/1.1/title" to "Test Dataset",
            )

        every { resourceService.getResourceJsonLd(testDatasetId, ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("application/ld+json") } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns "{\"@id\":\"$testDatasetUri\",\"@type\":\"http://example.org/Dataset\"}"
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/$testDatasetId/graph")
                    .header("Accept", "application/ld+json"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(testDatasetUri))
            .andExpect(jsonPath("$.@type").value("http://example.org/Dataset"))
    }

    @Test
    fun `should return Turtle when Accept header is text turtle`() {
        // Given
        val testDatasetId = "test-dataset-id"
        val testDatasetUri = "http://example.com/dataset"
        val jsonLdData =
            mapOf(
                "@id" to testDatasetUri,
                "@type" to "http://example.org/Dataset",
            )
        val turtleContent = "<$testDatasetUri> a <http://example.org/Dataset> ."

        every { resourceService.getResourceJsonLd(testDatasetId, ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns turtleContent
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/$testDatasetId/graph")
                    .header("Accept", "text/turtle"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleContent))
    }

    @Test
    fun `should return RDF XML when Accept header is application rdf+xml`() {
        // Given
        val testDatasetId = "test-dataset-id"
        val testDatasetUri = "http://example.com/dataset"
        val jsonLdData =
            mapOf(
                "@id" to testDatasetUri,
                "@type" to "http://example.org/Dataset",
            )
        val rdfXmlContent = "<?xml version=\"1.0\"?><rdf:RDF>...</rdf:RDF>"

        every { resourceService.getResourceJsonLd(testDatasetId, ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("application/rdf+xml") } returns RdfService.RdfFormat.RDF_XML
        every {
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.RDF_XML,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns rdfXmlContent
        every { rdfService.getContentType(RdfService.RdfFormat.RDF_XML) } returns MediaType("application", "rdf+xml")

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/$testDatasetId/graph")
                    .header("Accept", "application/rdf+xml"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "rdf+xml")))
            .andExpect(content().string(rdfXmlContent))
    }

    @Test
    fun `should return N-Triples when Accept header is application n-triples`() {
        // Given
        val testDatasetId = "test-dataset-id"
        val testDatasetUri = "http://example.com/dataset"
        val jsonLdData =
            mapOf(
                "@id" to testDatasetUri,
                "@type" to "http://example.org/Dataset",
            )
        val nTriplesContent = "<$testDatasetUri> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Dataset> ."

        every { resourceService.getResourceJsonLd(testDatasetId, ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("application/n-triples") } returns RdfService.RdfFormat.N_TRIPLES
        every {
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.N_TRIPLES,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns nTriplesContent
        every { rdfService.getContentType(RdfService.RdfFormat.N_TRIPLES) } returns MediaType("application", "n-triples")

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/$testDatasetId/graph")
                    .header("Accept", "application/n-triples"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "n-triples")))
            .andExpect(content().string(nTriplesContent))
    }

    @Test
    fun `should return N-Quads when Accept header is application n-quads`() {
        // Given
        val testDatasetId = "test-dataset-id"
        val testDatasetUri = "http://example.com/dataset"
        val jsonLdData =
            mapOf(
                "@id" to testDatasetUri,
                "@type" to "http://example.org/Dataset",
            )
        val nQuadsContent =
            "<$testDatasetUri> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> " +
                "<http://example.org/Dataset> <$testDatasetUri> ."

        every { resourceService.getResourceJsonLd(testDatasetId, ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("application/n-quads") } returns RdfService.RdfFormat.N_QUADS
        every {
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.N_QUADS,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns nQuadsContent
        every { rdfService.getContentType(RdfService.RdfFormat.N_QUADS) } returns MediaType("application", "n-quads")

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/$testDatasetId/graph")
                    .header("Accept", "application/n-quads"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "n-quads")))
            .andExpect(content().string(nQuadsContent))
    }

    @Test
    fun `should return JSON-LD by default when no Accept header is provided`() {
        // Given
        val testDatasetId = "test-dataset-id"
        val testDatasetUri = "http://example.com/dataset"
        val jsonLdData =
            mapOf(
                "@id" to testDatasetUri,
                "@type" to "http://example.org/Dataset",
            )

        every { resourceService.getResourceJsonLd(testDatasetId, ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns "{\"@id\":\"$testDatasetUri\",\"@type\":\"http://example.org/Dataset\"}"
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc
            .perform(get("/v1/datasets/$testDatasetId/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(testDatasetUri))
            .andExpect(jsonPath("$.@type").value("http://example.org/Dataset"))
    }

    @Test
    fun `should return 404 when dataset is not found`() {
        // Given
        val testDatasetId = "non-existent-dataset"

        every { resourceService.getResourceJsonLd(testDatasetId, ResourceType.DATASET) } returns null

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/$testDatasetId/graph")
                    .header("Accept", "application/ld+json"),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `should return 500 when format conversion fails`() {
        // Given
        val testDatasetId = "test-dataset-id"
        val testDatasetUri = "http://example.com/dataset"
        val jsonLdData =
            mapOf(
                "@id" to testDatasetUri,
                "@type" to "http://example.org/Dataset",
            )

        every { resourceService.getResourceJsonLd(testDatasetId, ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns null // Conversion fails

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/$testDatasetId/graph")
                    .header("Accept", "text/turtle"),
            ).andExpect(status().isInternalServerError)
            .andExpect(content().string("Failed to convert graph to requested format"))
    }
}
