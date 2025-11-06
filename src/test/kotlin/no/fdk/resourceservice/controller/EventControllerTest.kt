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
            .andExpect(jsonPath("$.title").value("Test Event"))
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
            .andExpect(jsonPath("$.title").value("Test Event"))
    }

    @Test
    fun `should get event graph by id`() {
        val eventId = "test-event-id"
        val graphData = mapOf("@id" to "https://example.com/event", "title" to "Test Event")

        every { resourceService.getResourceJsonLd(eventId, ResourceType.EVENT) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                graphData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.EVENT,
            )
        } returns """{"@id":"https://example.com/event","title":"Test Event"}"""
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc
            .perform(get("/v1/events/{id}/graph", eventId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/event"))
            .andExpect(jsonPath("$.title").value("Test Event"))
    }

    @Test
    fun `should get event graph by uri`() {
        val uri = "https://example.com/event"
        val graphData = mapOf("@id" to uri, "title" to "Test Event")

        every { resourceService.getResourceJsonLdByUri(uri, ResourceType.EVENT) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                graphData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.EVENT,
            )
        } returns """{"@id":"https://example.com/event","title":"Test Event"}"""
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc
            .perform(
                get("/v1/events/by-uri/graph")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(uri))
            .andExpect(jsonPath("$.title").value("Test Event"))
    }

    @Test
    fun `should return 404 when event not found`() {
        val eventId = "non-existent-id"

        every { resourceService.getResourceJson(eventId, ResourceType.EVENT) } returns null

        mockMvc
            .perform(get("/v1/events/{id}", eventId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should get event graph by id with standard style`() {
        val eventId = "test-event-id"
        val graphData = mapOf("@id" to "https://example.com/event", "title" to "Test Event")
        val standardJsonLd = """{"@id":"https://example.com/event","title":"Test Event"}"""

        every { resourceService.getResourceJsonLd(eventId, ResourceType.EVENT) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                graphData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.STANDARD,
                true,
                ResourceType.EVENT,
            )
        } returns standardJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc
            .perform(
                get("/v1/events/{id}/graph", eventId)
                    .param("style", "standard"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/event"))
    }

    @Test
    fun `should get event graph by uri with standard style`() {
        val uri = "https://example.com/event"
        val graphData = mapOf("@id" to uri, "title" to "Test Event")
        val standardJsonLd = """{"@id":"https://example.com/event","title":"Test Event"}"""

        every { resourceService.getResourceJsonLdByUri(uri, ResourceType.EVENT) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                graphData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.STANDARD,
                true,
                ResourceType.EVENT,
            )
        } returns standardJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc
            .perform(
                get("/v1/events/by-uri/graph")
                    .param("uri", uri)
                    .param("style", "standard"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(uri))
    }
}
