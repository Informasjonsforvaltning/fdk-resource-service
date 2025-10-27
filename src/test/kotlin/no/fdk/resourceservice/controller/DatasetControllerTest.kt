package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import io.mockk.every
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class DatasetControllerTest : BaseControllerTest() {

    @Test
    fun `getDataset should return 404 when not found`() {
        // Given
        every { resourceService.getResourceJson("non-existent", ResourceType.DATASET) } returns null

        // When & Then
        mockMvc.perform(get("/v1/datasets/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getDatasetByUri should return 404 when not found by URI`() {
        // Given
        every { resourceService.getResourceJsonByUri("https://example.com/non-existent", ResourceType.DATASET) } returns null

        // When & Then
        mockMvc.perform(get("/v1/datasets/by-uri")
            .param("uri", "https://example.com/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getDatasetGraph should return 404 when not found`() {
        // Given
        every { resourceService.getResourceJsonLd("non-existent", ResourceType.DATASET) } returns null

        // When & Then
        mockMvc.perform(get("/v1/datasets/non-existent/graph"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getDatasetGraph should return JSON-LD by default`() {
        // Given
        val jsonLdData = mapOf("@id" to "http://example.com/dataset", "@type" to "http://example.org/Dataset")
        every { resourceService.getResourceJsonLd("test-id", ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, false) } returns "{\"@id\":\"http://example.com/dataset\",\"@type\":\"http://example.org/Dataset\"}"
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/datasets/test-id/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string("{\"@id\":\"http://example.com/dataset\",\"@type\":\"http://example.org/Dataset\"}"))
    }

    @Test
    fun `getDatasetGraph should return Turtle when Accept header is text-turtle`() {
        // Given
        val jsonLdData = mapOf("@id" to "http://example.com/dataset", "@type" to "http://example.org/Dataset")
        val turtleData = "@prefix : <http://example.org/> .\n<http://example.com/dataset> a :Dataset ."
        
        every { resourceService.getResourceJsonLd("test-id", ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, false) } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc.perform(get("/v1/datasets/test-id/graph")
            .header("Accept", "text/turtle"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getDatasetGraph should return 500 when conversion fails`() {
        // Given
        val jsonLdData = mapOf("@id" to "http://example.com/dataset")
        every { resourceService.getResourceJsonLd("test-id", ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, false) } returns null

        // When & Then
        mockMvc.perform(get("/v1/datasets/test-id/graph")
            .header("Accept", "text/turtle"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().string("Failed to convert graph to requested format"))
    }

    @Test
    fun `getDatasetGraphByUri should return 404 when not found`() {
        // Given
        every { resourceService.getResourceJsonLdByUri("https://example.com/non-existent", ResourceType.DATASET) } returns null

        // When & Then
        mockMvc.perform(get("/v1/datasets/by-uri/graph")
            .param("uri", "https://example.com/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getDatasetGraphByUri should return RDF-XML when Accept header is application-rdf+xml`() {
        // Given
        val jsonLdData = mapOf("@id" to "http://example.com/dataset")
        val rdfXmlData = "<?xml version=\"1.0\"?><rdf:RDF>...</rdf:RDF>"
        
        every { resourceService.getResourceJsonLdByUri("https://example.com/dataset", ResourceType.DATASET) } returns jsonLdData
        every { rdfService.getBestFormat("application/rdf+xml") } returns RdfService.RdfFormat.RDF_XML
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.RDF_XML, RdfService.RdfFormatStyle.PRETTY, false) } returns rdfXmlData
        every { rdfService.getContentType(RdfService.RdfFormat.RDF_XML) } returns MediaType("application", "rdf+xml")

        // When & Then
        mockMvc.perform(get("/v1/datasets/by-uri/graph")
            .param("uri", "https://example.com/dataset")
            .header("Accept", "application/rdf+xml"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "rdf+xml")))
            .andExpect(content().string(rdfXmlData))
    }
}
