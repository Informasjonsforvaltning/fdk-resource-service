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

class DatasetControllerTest : BaseControllerTest() {
    @Test
    fun `getDataset should return 404 when not found`() {
        // Given
        every { resourceService.getResourceJson("non-existent", ResourceType.DATASET) } returns null

        // When & Then
        mockMvc
            .perform(get("/v1/datasets/non-existent"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getDatasetByUri should return 404 when not found by URI`() {
        // Given
        every { resourceService.getResourceJsonByUri("https://example.com/non-existent", ResourceType.DATASET) } returns null

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/by-uri")
                    .param("uri", "https://example.com/non-existent"),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `getDatasetGraph should return 404 when not found`() {
        // Given
        every { resourceService.getResourceEntity("non-existent", ResourceType.DATASET) } returns null

        // When & Then
        mockMvc
            .perform(get("/v1/datasets/non-existent/graph"))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `getDatasetGraph should return JSON-LD by default`() {
        // Given
        val turtleData = """<http://example.com/dataset> a <http://example.org/Dataset> ."""
        val entity =
            ResourceEntity(
                id = "test-id",
                resourceType = ResourceType.DATASET.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )
        every { resourceService.getResourceEntity("test-id", ResourceType.DATASET) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc
            .perform(get("/v1/datasets/test-id/graph"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getDatasetGraph should return Turtle when Accept header is text-turtle`() {
        // Given
        val turtleData = "@prefix : <http://example.org/> .\n<http://example.com/dataset> a :Dataset ."
        val entity =
            ResourceEntity(
                id = "test-id",
                resourceType = ResourceType.DATASET.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity("test-id", ResourceType.DATASET) } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/test-id/graph")
                    .header("Accept", "text/turtle"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `getDatasetGraph should return 500 when conversion fails`() {
        // Given
        val turtleData = """<http://example.com/dataset> a <http://example.org/Dataset> ."""
        val entity =
            ResourceEntity(
                id = "test-id",
                resourceType = ResourceType.DATASET.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )
        every { resourceService.getResourceEntity("test-id", ResourceType.DATASET) } returns entity
        every { rdfService.getBestFormat("text/turtle") } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns null

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/test-id/graph")
                    .header("Accept", "text/turtle"),
            ).andExpect(status().isInternalServerError)
            .andExpect(content().string("Failed to convert graph to requested format"))
    }

    @Test
    fun `getDatasetGraphByUri should return 404 when not found`() {
        // Given
        every { resourceService.getResourceEntityByUri("https://example.com/non-existent") } returns null

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/by-uri/graph")
                    .param("uri", "https://example.com/non-existent"),
            ).andExpect(status().isNotFound)
    }

    @Test
    fun `getDatasetGraphByUri should return RDF-XML when Accept header is application-rdf+xml`() {
        // Given
        val turtleData = """<http://example.com/dataset> a <http://example.org/Dataset> ."""
        val rdfXmlData = "<?xml version=\"1.0\"?><rdf:RDF>...</rdf:RDF>"
        val entity =
            ResourceEntity(
                id = "test-id",
                resourceType = ResourceType.DATASET.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
                uri = "https://example.com/dataset",
            )

        every { resourceService.getResourceEntityByUri("https://example.com/dataset") } returns entity
        every { rdfService.getBestFormat("application/rdf+xml") } returns RdfService.RdfFormat.RDF_XML
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.RDF_XML,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATASET,
            )
        } returns rdfXmlData
        every { rdfService.getContentType(RdfService.RdfFormat.RDF_XML) } returns MediaType("application", "rdf+xml")

        // When & Then
        mockMvc
            .perform(
                get("/v1/datasets/by-uri/graph")
                    .param("uri", "https://example.com/dataset")
                    .header("Accept", "application/rdf+xml"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("application", "rdf+xml")))
            .andExpect(content().string(rdfXmlData))
    }

    @Test
    fun `findDatasets should return 200 with list of datasets when found`() {
        val dataset1 =
            mapOf(
                "id" to "dataset-1",
                "title" to "First Dataset",
                "type" to "Dataset",
            )
        val dataset2 =
            mapOf(
                "id" to "dataset-2",
                "title" to "Second Dataset",
                "type" to "Dataset",
            )
        val ids = listOf("dataset-1", "dataset-2")
        val requestBody = """{"ids": ["dataset-1", "dataset-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.DATASET) } returns listOf(dataset1, dataset2)

        mockMvc
            .perform(
                post("/v1/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("dataset-1"))
            .andExpect(jsonPath("$[0].title").value("First Dataset"))
            .andExpect(jsonPath("$[1].id").value("dataset-2"))
            .andExpect(jsonPath("$[1].title").value("Second Dataset"))
    }

    @Test
    fun `findDatasets should return 200 with empty list when no datasets found`() {
        val ids = listOf("non-existent-1", "non-existent-2")
        val requestBody = """{"ids": ["non-existent-1", "non-existent-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.DATASET) } returns emptyList()

        mockMvc
            .perform(
                post("/v1/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `findDatasets should return 400 when ids list exceeds 100 items`() {
        val ids = (1..101).map { "id-$it" }
        val idsJson = ids.joinToString(",") { "\"$it\"" }
        val requestBody = """{"ids": [$idsJson]}"""

        mockMvc
            .perform(
                post("/v1/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `findDatasets should return 200 when ids list has exactly 100 items`() {
        val ids = (1..100).map { "id-$it" }
        val resources = ids.map { mapOf("id" to it, "title" to "Dataset $it") }
        val idsJson = ids.joinToString(",") { "\"$it\"" }
        val requestBody = """{"ids": [$idsJson]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.DATASET) } returns resources

        mockMvc
            .perform(
                post("/v1/datasets")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(100))
    }
}
