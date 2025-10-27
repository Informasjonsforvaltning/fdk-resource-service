package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import io.mockk.every
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ConceptControllerTest : BaseControllerTest() {

    @Test
    fun `getConcept should return 200 with concept data when found`() {
        // Given
        val conceptData = mapOf(
            "id" to "test-concept-1",
            "title" to "Test Concept",
            "description" to "A test concept for unit testing",
            "type" to "Concept"
        )
        every { resourceService.getResourceJson("test-concept-1", ResourceType.CONCEPT) } returns conceptData

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-1"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("test-concept-1"))
            .andExpect(jsonPath("$.title").value("Test Concept"))
            .andExpect(jsonPath("$.description").value("A test concept for unit testing"))
            .andExpect(jsonPath("$.type").value("Concept"))
    }

    @Test
    fun `getConcept should return 404 when not found`() {
        // Given
        every { resourceService.getResourceJson("non-existent", ResourceType.CONCEPT) } returns null

        // When & Then
        mockMvc.perform(get("/v1/concepts/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getConceptByUri should return 200 with concept data when found by URI`() {
        // Given
        val conceptData = mapOf(
            "id" to "test-concept-2",
            "uri" to "https://example.com/concept-2",
            "title" to "Test Concept 2",
            "description" to "Another test concept",
            "type" to "Concept"
        )
        every { resourceService.getResourceJsonByUri("https://example.com/concept-2", ResourceType.CONCEPT) } returns conceptData

        // When & Then
        mockMvc.perform(get("/v1/concepts/by-uri")
            .param("uri", "https://example.com/concept-2"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("test-concept-2"))
            .andExpect(jsonPath("$.uri").value("https://example.com/concept-2"))
            .andExpect(jsonPath("$.title").value("Test Concept 2"))
            .andExpect(jsonPath("$.description").value("Another test concept"))
            .andExpect(jsonPath("$.type").value("Concept"))
    }

    @Test
    fun `getConceptByUri should return 404 when not found by URI`() {
        // Given
        every { resourceService.getResourceJsonByUri("https://example.com/non-existent", ResourceType.CONCEPT) } returns null

        // When & Then
        mockMvc.perform(get("/v1/concepts/by-uri")
            .param("uri", "https://example.com/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getConceptGraph should return 200 with JSON-LD graph when found`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-3",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 3",
            "http://purl.org/dc/terms/description" to "A test concept with RDF data"
        )
        val convertedData = """{
            "@id": "https://example.com/concept-3",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 3",
            "http://purl.org/dc/terms/description": "A test concept with RDF data"
        }"""
        
        every { resourceService.getResourceJsonLd("test-concept-3", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, false) } returns convertedData
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-3/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/concept-3"))
            .andExpect(jsonPath("$.@type").value("http://www.w3.org/2004/02/skos/core#Concept"))
    }

    @Test
    fun `getConceptGraph should return 200 with Turtle format when Accept header is text-turtle`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-4",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 4"
        )
        val turtleData = """@prefix dc: <http://purl.org/dc/elements/1.1/> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

<https://example.com/concept-4> a skos:Concept ;
    dc:title "Test Concept 4" ."""
        
        every { resourceService.getResourceJsonLd("test-concept-4", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, false) } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType.valueOf("text/turtle")

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-4/graph")
            .header("Accept", "text/turtle"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("text/turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getConceptGraph should return 404 when not found`() {
        // Given
        every { resourceService.getResourceJsonLd("non-existent", ResourceType.CONCEPT) } returns null

        // When & Then
        mockMvc.perform(get("/v1/concepts/non-existent/graph"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getConceptGraphByUri should return 200 with JSON-LD graph when found by URI`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-5",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 5"
        )
        val convertedData = """{
            "@id": "https://example.com/concept-5",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 5"
        }"""
        
        every { resourceService.getResourceJsonLdByUri("https://example.com/concept-5", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, false) } returns convertedData
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/concepts/by-uri/graph")
            .param("uri", "https://example.com/concept-5"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/concept-5"))
            .andExpect(jsonPath("$.@type").value("http://www.w3.org/2004/02/skos/core#Concept"))
    }

    @Test
    fun `getConceptGraphByUri should return 404 when not found by URI`() {
        // Given
        every { resourceService.getResourceJsonLdByUri("https://example.com/non-existent", ResourceType.CONCEPT) } returns null

        // When & Then
        mockMvc.perform(get("/v1/concepts/by-uri/graph")
            .param("uri", "https://example.com/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getConceptGraph should return 200 with standard JSON-LD format when format=standard`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-6",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 6"
        )
        val standardJsonLd = """{
            "@context": {
                "dc": "http://purl.org/dc/elements/1.1/",
                "skos": "http://www.w3.org/2004/02/skos/core#"
            },
            "@graph": [{
                "@id": "https://example.com/concept-6",
                "@type": "skos:Concept",
                "dc:title": "Test Concept 6"
            }]
        }"""
        
        every { resourceService.getResourceJsonLd("test-concept-6", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.STANDARD, false) } returns standardJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-6/graph")
            .param("style", "standard"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@context").exists())
            .andExpect(jsonPath("$.@graph").exists())
            .andExpect(jsonPath("$.@graph[0].@id").value("https://example.com/concept-6"))
    }

    @Test
    fun `getConceptGraph should return 200 with pretty JSON-LD format when format=pretty`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-7",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 7"
        )
        val prettyJsonLd = """{
            "@id": "https://example.com/concept-7",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 7"
        }"""
        
        every { resourceService.getResourceJsonLd("test-concept-7", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, false) } returns prettyJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-7/graph")
            .param("style", "pretty"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/concept-7"))
            .andExpect(jsonPath("$.@type").value("http://www.w3.org/2004/02/skos/core#Concept"))
    }

    @Test
    fun `getConceptGraph should default to pretty format when no format parameter provided`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-8",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 8"
        )
        val prettyJsonLd = """{
            "@id": "https://example.com/concept-8",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 8"
        }"""
        
        every { resourceService.getResourceJsonLd("test-concept-8", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, false) } returns prettyJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-8/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/concept-8"))
    }

    @Test
    fun `getConceptGraph should handle invalid format parameter gracefully`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-9",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 9"
        )
        val prettyJsonLd = """{
            "@id": "https://example.com/concept-9",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 9"
        }"""
        
        every { resourceService.getResourceJsonLd("test-concept-9", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, false) } returns prettyJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-9/graph")
            .param("format", "invalid"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/concept-9"))
    }

    @Test
    fun `getConceptGraphByUri should return 200 with standard JSON-LD format when format=standard`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-10",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 10"
        )
        val standardJsonLd = """{
            "@context": {
                "dc": "http://purl.org/dc/elements/1.1/",
                "skos": "http://www.w3.org/2004/02/skos/core#"
            },
            "@graph": [{
                "@id": "https://example.com/concept-10",
                "@type": "skos:Concept",
                "dc:title": "Test Concept 10"
            }]
        }"""
        
        every { resourceService.getResourceJsonLdByUri("https://example.com/concept-10", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.STANDARD, false) } returns standardJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        // When & Then
        mockMvc.perform(get("/v1/concepts/by-uri/graph")
            .param("uri", "https://example.com/concept-10")
            .param("style", "standard"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@context").exists())
            .andExpect(jsonPath("$.@graph").exists())
            .andExpect(jsonPath("$.@graph[0].@id").value("https://example.com/concept-10"))
    }

    @Test
    fun `getConceptGraph should return 200 with pretty Turtle format when format=pretty and Accept is text-turtle`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-11",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 11"
        )
        val prettyTurtle = """<https://example.com/concept-11> a <http://www.w3.org/2004/02/skos/core#Concept> ;
    <http://purl.org/dc/elements/1.1/title> "Test Concept 11" ."""
        
        every { resourceService.getResourceJsonLd("test-concept-11", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.PRETTY, false) } returns prettyTurtle
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType.valueOf("text/turtle")

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-11/graph")
            .header("Accept", "text/turtle")
            .param("style", "pretty"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("text/turtle")))
            .andExpect(content().string(prettyTurtle))
    }

    @Test
    fun `getConceptGraph should return 200 with standard Turtle format when format=standard and Accept is text-turtle`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-12",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 12"
        )
        val standardTurtle = """@prefix dc: <http://purl.org/dc/elements/1.1/> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

<https://example.com/concept-12> a skos:Concept ;
    dc:title "Test Concept 12" ."""
        
        every { resourceService.getResourceJsonLd("test-concept-12", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.TURTLE, RdfService.RdfFormatStyle.STANDARD, false) } returns standardTurtle
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType.valueOf("text/turtle")

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-12/graph")
            .header("Accept", "text/turtle")
            .param("style", "standard"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("text/turtle")))
            .andExpect(content().string(standardTurtle))
    }

    @Test
    fun `getConceptGraph should return 200 with pretty RDF XML format when format=pretty and Accept is application-rdf-xml`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-13",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 13"
        )
        val prettyRdfXml = """<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about="https://example.com/concept-13">
    <rdf:type rdf:resource="http://www.w3.org/2004/02/skos/core#Concept"/>
    <http://purl.org/dc/elements/1.1/title>Test Concept 13</http://purl.org/dc/elements/1.1/title>
  </rdf:Description>
</rdf:RDF>"""
        
        every { resourceService.getResourceJsonLd("test-concept-13", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat("application/rdf+xml") } returns RdfService.RdfFormat.RDF_XML
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.RDF_XML, RdfService.RdfFormatStyle.PRETTY, false) } returns prettyRdfXml
        every { rdfService.getContentType(RdfService.RdfFormat.RDF_XML) } returns MediaType.valueOf("application/rdf+xml")

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-13/graph")
            .header("Accept", "application/rdf+xml")
            .param("style", "pretty"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("application/rdf+xml")))
            .andExpect(content().string(prettyRdfXml))
    }

    @Test
    fun `getConceptGraph should return 200 with standard RDF XML format when format=standard and Accept is application-rdf-xml`() {
        // Given
        val jsonLdData = mapOf(
            "@id" to "https://example.com/concept-14",
            "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title" to "Test Concept 14"
        )
        val standardRdfXml = """<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#" 
         xmlns:dc="http://purl.org/dc/elements/1.1/" 
         xmlns:skos="http://www.w3.org/2004/02/skos/core#">
  <skos:Concept rdf:about="https://example.com/concept-14">
    <dc:title>Test Concept 14</dc:title>
  </skos:Concept>
</rdf:RDF>"""
        
        every { resourceService.getResourceJsonLd("test-concept-14", ResourceType.CONCEPT) } returns jsonLdData
        every { rdfService.getBestFormat("application/rdf+xml") } returns RdfService.RdfFormat.RDF_XML
        every { rdfService.convertFromJsonLd(jsonLdData, RdfService.RdfFormat.RDF_XML, RdfService.RdfFormatStyle.STANDARD, false) } returns standardRdfXml
        every { rdfService.getContentType(RdfService.RdfFormat.RDF_XML) } returns MediaType.valueOf("application/rdf+xml")

        // When & Then
        mockMvc.perform(get("/v1/concepts/test-concept-14/graph")
            .header("Accept", "application/rdf+xml")
            .param("style", "standard"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("application/rdf+xml")))
            .andExpect(content().string(standardRdfXml))
    }
}
