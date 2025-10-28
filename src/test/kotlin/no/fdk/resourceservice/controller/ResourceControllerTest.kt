package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import io.mockk.every
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ResourceControllerTest : BaseControllerTest() {

    @Test
    fun `getResourceByUri should return 404 when resource not found`() {
        // Given
        every { resourceService.getResourceJsonByUri("https://example.com/non-existent") } returns null

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri")
            .param("uri", "https://example.com/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getResourceByUri should return JSON resource when found`() {
        // Given
        val resourceData = mapOf(
            "id" to "test-resource-id",
            "title" to "Test Resource",
            "description" to "A test resource",
            "uri" to "https://example.com/resource"
        )
        every { resourceService.getResourceJsonByUri("https://example.com/resource") } returns resourceData

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri")
            .param("uri", "https://example.com/resource"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("test-resource-id"))
            .andExpect(jsonPath("$.title").value("Test Resource"))
            .andExpect(jsonPath("$.description").value("A test resource"))
            .andExpect(jsonPath("$.uri").value("https://example.com/resource"))
    }

    @Test
    fun `getResourceByUri should return 404 when URI parameter is missing`() {
        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `getResourceGraphByUri should return 404 when resource not found`() {
        // Given
        every { resourceService.getResourceEntityByUri("https://example.com/non-existent") } returns null

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getResourceGraphByUri should return JSON-LD by default`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/resource",
            "@type" to "http://example.org/Resource",
            "title" to "Test Resource"
        )
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns "{\"@id\":\"https://example.com/resource\",\"@type\":\"http://example.org/Resource\",\"title\":\"Test Resource\"}"
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(content().string("{\"@id\":\"https://example.com/resource\",\"@type\":\"http://example.org/Resource\",\"title\":\"Test Resource\"}"))
    }

    @Test
    fun `getResourceGraphByUri should return Turtle when Accept header is text-turtle`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/resource",
            "@type" to "http://example.org/Resource"
        )
        val turtleData = "@prefix : <http://example.org/> .\n<https://example.com/resource> a :Resource ."
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .header("Accept", "text/turtle"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getResourceGraphByUri should return RDF-XML when Accept header is application-rdf+xml`() {
        // Given
        val jsonLdData = mapOf("@id" to "https://example.com/resource")
        val rdfXmlData = "<?xml version=\"1.0\"?><rdf:RDF>...</rdf:RDF>"
        
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("application/rdf+xml") } returns RdfService.RdfFormat.RDF_XML
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.RDF_XML, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns rdfXmlData
        every { rdfService.getContentType(RdfService.RdfFormat.RDF_XML) } returns MediaType("application", "rdf+xml")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .header("Accept", "application/rdf+xml"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "rdf+xml")))
            .andExpect(content().string(rdfXmlData))
    }

    @Test
    fun `getResourceGraphByUri should return N-Triples when Accept header is application-n-triples`() {
        // Given
        val jsonLdData = mapOf("@id" to "https://example.com/resource")
        val nTriplesData = "<https://example.com/resource> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Resource> ."
        
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("application/n-triples") } returns RdfService.RdfFormat.N_TRIPLES
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.N_TRIPLES, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns nTriplesData
        every { rdfService.getContentType(RdfService.RdfFormat.N_TRIPLES) } returns MediaType("application", "n-triples")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .header("Accept", "application/n-triples"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "n-triples")))
            .andExpect(content().string(nTriplesData))
    }

    @Test
    fun `getResourceGraphByUri should return N-Quads when Accept header is application-n-quads`() {
        // Given
        val jsonLdData = mapOf("@id" to "https://example.com/resource")
        val nQuadsData = "<https://example.com/resource> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Resource> <https://example.com/graph> ."
        
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("application/n-quads") } returns RdfService.RdfFormat.N_QUADS
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.N_QUADS, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns nQuadsData
        every { rdfService.getContentType(RdfService.RdfFormat.N_QUADS) } returns MediaType("application", "n-quads")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .header("Accept", "application/n-quads"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "n-quads")))
            .andExpect(content().string(nQuadsData))
    }

    @Test
    fun `getResourceGraphByUri should use pretty style when style parameter is pretty`() {
        // Given
        val jsonLdData = mapOf("@id" to "https://example.com/resource")
        val turtleData = "@prefix : <http://example.org/> .\n<https://example.com/resource> a :Resource ."
        
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .param("style", "pretty")
            .header("Accept", "text/turtle"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getResourceGraphByUri should use standard style when style parameter is standard`() {
        // Given
        val jsonLdData = mapOf("@id" to "https://example.com/resource")
        val turtleData = "@prefix : <http://example.org/> .\n<https://example.com/resource> a :Resource ."
        
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.STANDARD, true, ResourceType.DATASET) } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .param("style", "standard")
            .header("Accept", "text/turtle"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getResourceGraphByUri should expand URIs when expandUris parameter is true`() {
        // Given
        val jsonLdData = mapOf("@id" to "https://example.com/resource")
        val turtleData = "<https://example.com/resource> <http://www.w3.org/1999/02/22-rdf-syntax-ns#type> <http://example.org/Resource> ."
        
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .param("expandUris", "true")
            .header("Accept", "text/turtle"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getResourceGraphByUri should return 500 when conversion fails`() {
        // Given
        val jsonLdData = mapOf("@id" to "https://example.com/resource")
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns null

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .header("Accept", "text/turtle"))
            .andExpect(status().isInternalServerError)
            .andExpect(content().string("Failed to convert graph to requested format"))
    }

    @Test
    fun `getResourceGraphByUri should return 404 when URI parameter is missing`() {
        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph"))
            .andExpect(status().isBadRequest)
    }

    @Test
    fun `getResourceGraphByUri should handle multiple Accept headers`() {
        // Given
        val jsonLdData = mapOf("@id" to "https://example.com/resource")
        val turtleData = "@prefix : <http://example.org/> .\n<https://example.com/resource> a :Resource ."
        
        val entity = ResourceEntity(
            id = "test-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/resource"
        )
        every { resourceService.getResourceEntityByUri("https://example.com/resource") } returns entity
        every { rdfService.getBestFormat("application/ld+json, text/turtle;q=0.8") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/resource")
            .header("Accept", "application/ld+json, text/turtle;q=0.8"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getResourceGraphByUri should use resource-specific prefixes based on resource type`() {
        // Given - Dataset resource
        val jsonLdData = mapOf("@id" to "https://example.com/dataset")
        val datasetEntity = ResourceEntity(
            id = "dataset-id",
            resourceType = "DATASET",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/dataset"
        )
        
        every { resourceService.getResourceEntityByUri("https://example.com/dataset") } returns datasetEntity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.DATASET) } returns "@prefix dcat: <http://www.w3.org/ns/dcat#> ."
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/dataset")
            .header("Accept", "text/turtle"))
            .andExpect(status().isOk)
    }

    @Test
    fun `getResourceGraphByUri should handle unknown resource type gracefully`() {
        // Given - Unknown resource type
        val jsonLdData = mapOf("@id" to "https://example.com/unknown")
        val unknownEntity = ResourceEntity(
            id = "unknown-id",
            resourceType = "UNKNOWN_TYPE",
            resourceJsonLd = jsonLdData,
            uri = "https://example.com/unknown"
        )
        
        every { resourceService.getResourceEntityByUri("https://example.com/unknown") } returns unknownEntity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, true, null) } returns "@prefix : <http://example.org/> ."
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then - Should use common prefixes when resource type is unknown
        mockMvc.perform(get("/v1/resources/by-uri/graph")
            .param("uri", "https://example.com/unknown")
            .header("Accept", "text/turtle"))
            .andExpect(status().isOk)
    }
}
