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

class ServiceControllerTest : BaseControllerTest() {
    @Test
    fun `should get service by id`() {
        val serviceId = "test-service-id"
        val serviceData = mapOf("id" to serviceId, "title" to "Test Service")

        every { resourceService.getResourceJson(serviceId, ResourceType.SERVICE) } returns serviceData

        mockMvc
            .perform(get("/v1/services/{id}", serviceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(serviceId))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get service by uri`() {
        val uri = "https://example.com/service"
        val serviceData = mapOf("uri" to uri, "title" to "Test Service")

        every { resourceService.getResourceJsonByUri(uri, ResourceType.SERVICE) } returns serviceData

        mockMvc
            .perform(
                get("/v1/services/by-uri")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.uri").value(uri))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get service graph by id`() {
        val serviceId = "test-service-id"
        val turtleData =
            """<https://example.com/service> a <http://example.org/Service> ;
                |<http://purl.org/dc/terms/title> "Test Service" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = serviceId,
                resourceType = ResourceType.SERVICE.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity(serviceId, ResourceType.SERVICE) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.SERVICE,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(get("/v1/services/{id}/graph", serviceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should get service graph by uri`() {
        val uri = "https://example.com/service"
        val turtleData =
            """<https://example.com/service> a <http://example.org/Service> ;
                |<http://purl.org/dc/terms/title> "Test Service" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = "test-service-id",
                resourceType = ResourceType.SERVICE.name,
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
                ResourceType.SERVICE,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(
                get("/v1/services/by-uri/graph")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should return 404 when service not found`() {
        val serviceId = "non-existent-id"

        every { resourceService.getResourceJson(serviceId, ResourceType.SERVICE) } returns null

        mockMvc
            .perform(get("/v1/services/{id}", serviceId))
            .andExpect(status().isNotFound)
    }
}
