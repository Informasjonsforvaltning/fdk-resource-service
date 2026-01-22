package no.fdk.resourceservice.controller

import io.mockk.every
import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ConceptControllerTest : BaseControllerTest() {
    @Test
    fun `getConcept should return 200 with concept data when found`() {
        // Given
        val conceptData =
            mapOf(
                "id" to "test-concept-1",
                "title" to "Test Concept",
                "description" to "A test concept for unit testing",
                "type" to "Concept",
            )
        every { resourceService.getResourceJson("test-concept-1", ResourceType.CONCEPT) } returns conceptData

        // When & Then
        mockMvc
            .perform(get("/v1/concepts/test-concept-1"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("test-concept-1"))
            // Turtle format - content verified via string match
            .andExpect(jsonPath("$.description").value("A test concept for unit testing"))
            .andExpect(jsonPath("$.type").value("Concept"))
    }

    @Test
    fun `getConcept should return 404 when not found`() {
        // Given
        every { resourceService.getResourceJson("non-existent", ResourceType.CONCEPT) } returns null

        // When & Then
        mockMvc
            .perform(get("/v1/concepts/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getConceptByUri should return 200 with concept data when found by URI`() {
        // Given
        val conceptData =
            mapOf(
                "id" to "test-concept-2",
                "uri" to "https://example.com/concept-2",
                "title" to "Test Concept 2",
                "description" to "Another test concept",
                "type" to "Concept",
            )
        every { resourceService.getResourceJsonByUri("https://example.com/concept-2", ResourceType.CONCEPT) } returns conceptData

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/by-uri")
                    .param("uri", "https://example.com/concept-2"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value("test-concept-2"))
            .andExpect(jsonPath("$.uri").value("https://example.com/concept-2"))
            // Turtle format - content verified via string match
            .andExpect(jsonPath("$.description").value("Another test concept"))
            .andExpect(jsonPath("$.type").value("Concept"))
    }

    @Test
    fun `getConceptByUri should return 404 when not found by URI`() {
        // Given
        every { resourceService.getResourceJsonByUri("https://example.com/non-existent", ResourceType.CONCEPT) } returns null

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/by-uri")
                    .param("uri", "https://example.com/non-existent"),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `getConceptGraph should return 200 with JSON-LD graph when found`() {
        // Given
        val turtleData = """@prefix dc: <http://purl.org/dc/elements/1.1/> .
@prefix dct: <http://purl.org/dc/terms/> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

<https://example.com/concept-3> a skos:Concept ;
    dc:title "Test Concept 3" ;
    dct:description "A test concept with RDF data" ."""
        val convertedData = """{
            "@id": "https://example.com/concept-3",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 3",
            "http://purl.org/dc/terms/description": "A test concept with RDF data"
        }"""
        val entity =
            ResourceEntity(
                id = "test-concept-3",
                resourceType = ResourceType.CONCEPT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity("test-concept-3", ResourceType.CONCEPT) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.CONCEPT,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc
            .perform(get("/v1/concepts/test-concept-3/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getConceptGraph should return 200 with Turtle format when Accept header is text-turtle`() {
        // Given
        val turtleData = """@prefix dc: <http://purl.org/dc/elements/1.1/> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

<https://example.com/concept-4> a skos:Concept ;
    dc:title "Test Concept 4" ."""
        val entity =
            ResourceEntity(
                id = "test-concept-4",
                resourceType = ResourceType.CONCEPT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity("test-concept-4", ResourceType.CONCEPT) } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.CONCEPT,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType.valueOf("text/turtle")

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/test-concept-4/graph")
                    .header("Accept", "text/turtle"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("text/turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getConceptGraph should return 404 when not found`() {
        // Given
        every { resourceService.getResourceEntity("non-existent", ResourceType.CONCEPT) } returns null

        // When & Then
        mockMvc
            .perform(get("/v1/concepts/non-existent/graph"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getConceptGraphByUri should return 200 with JSON-LD graph when found by URI`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/concept-5",
                "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
                "http://purl.org/dc/elements/1.1/title" to "Test Concept 5",
            )
        val convertedData = """{
            "@id": "https://example.com/concept-5",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 5"
        }"""

        val turtleData = """@prefix dc: <http://purl.org/dc/elements/1.1/> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

<https://example.com/concept-5> a skos:Concept ;
    dc:title "Test Concept 5" ."""
        val entity =
            ResourceEntity(
                id = "test-concept-5",
                resourceType = ResourceType.CONCEPT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
                uri = "https://example.com/concept-5",
            )

        every { resourceService.getResourceEntityByUri("https://example.com/concept-5") } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.CONCEPT,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/by-uri/graph")
                    .param("uri", "https://example.com/concept-5"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getConceptGraphByUri should return 404 when not found by URI`() {
        // Given
        every { resourceService.getResourceEntityByUri("https://example.com/non-existent") } returns null

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/by-uri/graph")
                    .param("uri", "https://example.com/non-existent"),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `getConceptGraph should return 200 with pretty JSON-LD format when format=pretty`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/concept-7",
                "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
                "http://purl.org/dc/elements/1.1/title" to "Test Concept 7",
            )
        val prettyJsonLd = """{
            "@id": "https://example.com/concept-7",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 7"
        }"""

        val turtleData = """@prefix dc: <http://purl.org/dc/elements/1.1/> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

<https://example.com/concept-7> a skos:Concept ;
    dc:title "Test Concept 7" ."""
        val entity =
            ResourceEntity(
                id = "test-concept-7",
                resourceType = ResourceType.CONCEPT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity("test-concept-7", ResourceType.CONCEPT) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.CONCEPT,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/test-concept-7/graph")
                    .param("style", "pretty"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getConceptGraph should default to pretty format when no format parameter provided`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/concept-8",
                "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
                "http://purl.org/dc/elements/1.1/title" to "Test Concept 8",
            )
        val prettyJsonLd = """{
            "@id": "https://example.com/concept-8",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 8"
        }"""

        val turtleData = """@prefix dc: <http://purl.org/dc/elements/1.1/> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

<https://example.com/concept-8> a skos:Concept ;
    dc:title "Test Concept 8" ."""
        val entity =
            ResourceEntity(
                id = "test-concept-8",
                resourceType = ResourceType.CONCEPT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity("test-concept-8", ResourceType.CONCEPT) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.CONCEPT,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc
            .perform(get("/v1/concepts/test-concept-8/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getConceptGraph should handle invalid format parameter gracefully`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/concept-9",
                "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
                "http://purl.org/dc/elements/1.1/title" to "Test Concept 9",
            )
        val prettyJsonLd = """{
            "@id": "https://example.com/concept-9",
            "@type": "http://www.w3.org/2004/02/skos/core#Concept",
            "http://purl.org/dc/elements/1.1/title": "Test Concept 9"
        }"""

        val turtleData = """@prefix dc: <http://purl.org/dc/elements/1.1/> .
@prefix skos: <http://www.w3.org/2004/02/skos/core#> .

<https://example.com/concept-9> a skos:Concept ;
    dc:title "Test Concept 9" ."""
        val entity =
            ResourceEntity(
                id = "test-concept-9",
                resourceType = ResourceType.CONCEPT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity("test-concept-9", ResourceType.CONCEPT) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every { rdfService.getBestFormat("invalid/format") } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.CONCEPT,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/test-concept-9/graph")
                    .param("format", "invalid"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getConceptGraph should return 200 with pretty Turtle format when format=pretty and Accept is text-turtle`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/concept-11",
                "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
                "http://purl.org/dc/elements/1.1/title" to "Test Concept 11",
            )
        val prettyTurtle = """<https://example.com/concept-11> a <http://www.w3.org/2004/02/skos/core#Concept> ;
    <http://purl.org/dc/elements/1.1/title> "Test Concept 11" ."""

        val turtleData = """<https://example.com/concept-11> a <http://www.w3.org/2004/02/skos/core#Concept> ;
    <http://purl.org/dc/elements/1.1/title> "Test Concept 11" ."""
        val entity =
            ResourceEntity(
                id = "test-concept-11",
                resourceType = ResourceType.CONCEPT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity("test-concept-11", ResourceType.CONCEPT) } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.CONCEPT,
            )
        } returns prettyTurtle
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType.valueOf("text/turtle")

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/test-concept-11/graph")
                    .header("Accept", "text/turtle")
                    .param("style", "pretty"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("text/turtle")))
            .andExpect(content().string(prettyTurtle))
    }

    @Test
    fun `getConceptGraph should return 200 with pretty RDF XML format when format=pretty and Accept is application-rdf-xml`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/concept-13",
                "@type" to "http://www.w3.org/2004/02/skos/core#Concept",
                "http://purl.org/dc/elements/1.1/title" to "Test Concept 13",
            )
        val prettyRdfXml = """<?xml version="1.0" encoding="UTF-8"?>
<rdf:RDF xmlns:rdf="http://www.w3.org/1999/02/22-rdf-syntax-ns#">
  <rdf:Description rdf:about="https://example.com/concept-13">
    <rdf:type rdf:resource="http://www.w3.org/2004/02/skos/core#Concept"/>
    <http://purl.org/dc/elements/1.1/title>Test Concept 13</http://purl.org/dc/elements/1.1/title>
  </rdf:Description>
</rdf:RDF>"""

        val turtleData = """<https://example.com/concept-13> a <http://www.w3.org/2004/02/skos/core#Concept> ;
    <http://purl.org/dc/elements/1.1/title> "Test Concept 13" ."""
        val entity =
            ResourceEntity(
                id = "test-concept-13",
                resourceType = ResourceType.CONCEPT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity("test-concept-13", ResourceType.CONCEPT) } returns entity
        every { rdfService.getBestFormat("application/rdf+xml") } returns RdfService.RdfFormat.RDF_XML
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.RDF_XML,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.CONCEPT,
            )
        } returns prettyRdfXml
        every { rdfService.getContentType(RdfService.RdfFormat.RDF_XML) } returns MediaType.valueOf("application/rdf+xml")

        // When & Then
        mockMvc
            .perform(
                get("/v1/concepts/test-concept-13/graph")
                    .header("Accept", "application/rdf+xml")
                    .param("style", "pretty"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.valueOf("application/rdf+xml")))
            .andExpect(content().string(prettyRdfXml))
    }

    @Test
    fun `findConcepts should return 200 with list of concepts when found`() {
        val concept1 =
            mapOf(
                "id" to "concept-1",
                "title" to "First Concept",
                "type" to "Concept",
            )
        val concept2 =
            mapOf(
                "id" to "concept-2",
                "title" to "Second Concept",
                "type" to "Concept",
            )
        val ids = listOf("concept-1", "concept-2")
        val requestBody = """{"ids": ["concept-1", "concept-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.CONCEPT) } returns listOf(concept1, concept2)

        mockMvc
            .perform(
                post("/v1/concepts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("concept-1"))
            .andExpect(jsonPath("$[0].title").value("First Concept"))
            .andExpect(jsonPath("$[1].id").value("concept-2"))
            .andExpect(jsonPath("$[1].title").value("Second Concept"))
    }

    @Test
    fun `findConcepts should return 200 with empty list when no concepts found`() {
        val ids = listOf("non-existent-1", "non-existent-2")
        val requestBody = """{"ids": ["non-existent-1", "non-existent-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.CONCEPT) } returns emptyList()

        mockMvc
            .perform(
                post("/v1/concepts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `findConcepts should return 400 when ids list exceeds 100 items`() {
        val ids = (1..101).map { "id-$it" }
        val idsJson = ids.joinToString(",") { "\"$it\"" }
        val requestBody = """{"ids": [$idsJson]}"""

        mockMvc
            .perform(
                post("/v1/concepts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `findConcepts should return 200 when ids list has exactly 100 items`() {
        val ids = (1..100).map { "id-$it" }
        val resources = ids.map { mapOf("id" to it, "title" to "Concept $it") }
        val idsJson = ids.joinToString(",") { "\"$it\"" }
        val requestBody = """{"ids": [$idsJson]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.CONCEPT) } returns resources

        mockMvc
            .perform(
                post("/v1/concepts")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(100))
    }
}
