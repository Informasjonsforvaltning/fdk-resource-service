package no.fdk.resourceservice.controller

import io.mockk.every
import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class DataServiceControllerTest : BaseControllerTest() {
    @Test
    fun `should get data service by id`() {
        val dataServiceId = "test-data-service-id"
        val dataServiceData = mapOf("id" to dataServiceId, "title" to "Test Data Service")

        every { resourceService.getResourceJson(dataServiceId, ResourceType.DATA_SERVICE) } returns dataServiceData

        mockMvc
            .perform(get("/v1/data-services/{id}", dataServiceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(dataServiceId))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get data service by uri`() {
        val uri = "https://example.com/data-service"
        val dataServiceData = mapOf("uri" to uri, "title" to "Test Data Service")

        every { resourceService.getResourceJsonByUri(uri, ResourceType.DATA_SERVICE) } returns dataServiceData

        mockMvc
            .perform(
                get("/v1/data-services/by-uri")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.uri").value(uri))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get data service graph by id`() {
        val dataServiceId = "test-data-service-id"
        val turtleData =
            """<https://example.com/data-service> a <http://example.org/DataService> ;
                |<http://purl.org/dc/terms/title> "Test Data Service" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = dataServiceId,
                resourceType = ResourceType.DATA_SERVICE.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity(dataServiceId, ResourceType.DATA_SERVICE) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATA_SERVICE,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(get("/v1/data-services/{id}/graph", dataServiceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should get data service graph by uri`() {
        val uri = "https://example.com/data-service"
        val turtleData =
            """<https://example.com/data-service> a <http://example.org/DataService> ;
                |<http://purl.org/dc/terms/title> "Test Data Service" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = "test-data-service-id",
                resourceType = ResourceType.DATA_SERVICE.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
                uri = uri,
            )

        every { resourceService.getResourceEntityByUri(uri) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATA_SERVICE,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(
                get("/v1/data-services/by-uri/graph")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should return 404 when data service not found`() {
        val dataServiceId = "non-existent-id"

        every { resourceService.getResourceJson(dataServiceId, ResourceType.DATA_SERVICE) } returns null

        mockMvc
            .perform(get("/v1/data-services/{id}", dataServiceId))
            .andExpect(status().isNotFound)
    }
}
