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

class EventControllerTest : BaseControllerTest() {
    @Test
    fun `should get event by id`() {
        val eventId = "test-event-id"
        val eventData = mapOf("id" to eventId, "title" to "Test Event")

        every { resourceService.getResourceJson(eventId, ResourceType.EVENT) } returns eventData

        mockMvc
            .perform(get("/v1/events/{id}", eventId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(eventId))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get event by uri`() {
        val uri = "https://example.com/event"
        val eventData = mapOf("uri" to uri, "title" to "Test Event")

        every { resourceService.getResourceJsonByUri(uri, ResourceType.EVENT) } returns eventData

        mockMvc
            .perform(
                get("/v1/events/by-uri")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.uri").value(uri))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get event graph by id`() {
        val eventId = "test-event-id"
        val turtleData = """<https://example.com/event> a <http://example.org/Event> ; <http://purl.org/dc/terms/title> "Test Event" ."""
        val entity =
            ResourceEntity(
                id = eventId,
                resourceType = ResourceType.EVENT.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity(eventId, ResourceType.EVENT) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.EVENT,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(get("/v1/events/{id}/graph", eventId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should get event graph by uri`() {
        val uri = "https://example.com/event"
        val turtleData = """<https://example.com/event> a <http://example.org/Event> ; <http://purl.org/dc/terms/title> "Test Event" ."""
        val entity =
            ResourceEntity(
                id = "test-event-id",
                resourceType = ResourceType.EVENT.name,
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
                ResourceType.EVENT,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(
                get("/v1/events/by-uri/graph")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should return 404 when event not found`() {
        val eventId = "non-existent-id"

        every { resourceService.getResourceJson(eventId, ResourceType.EVENT) } returns null

        mockMvc
            .perform(get("/v1/events/{id}", eventId))
            .andExpect(status().isNotFound)
    }
}
