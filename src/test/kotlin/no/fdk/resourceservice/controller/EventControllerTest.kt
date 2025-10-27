package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.ResourceType
import org.junit.jupiter.api.Test
import io.mockk.every
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class EventControllerTest : BaseControllerTest() {

    @Test
    fun `get event by id should return 200 when found`() {
        val eventId = "test-event-1"
        val mockEvent = mapOf(
            "id" to eventId,
            "type" to "EVENT",
            "title" to "Test Event"
        )

        every { resourceService.getResourceJson(eventId, ResourceType.EVENT) } returns mockEvent

        mockMvc.perform(get("/v1/events/{id}", eventId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(eventId))
            .andExpect(jsonPath("$.type").value("EVENT"))
            .andExpect(jsonPath("$.title").value("Test Event"))
    }

    @Test
    fun `get event by id should return 404 when not found`() {
        val eventId = "non-existent-event"

        every { resourceService.getResourceJson(eventId, ResourceType.EVENT) } returns null

        mockMvc.perform(get("/v1/events/{id}", eventId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get event by uri should return 200 when found`() {
        val uri = "https://example.com/event"
        val mockEvent = mapOf(
            "id" to "test-event-1",
            "type" to "EVENT",
            "uri" to uri
        )

        every { resourceService.getResourceJsonByUri(uri, ResourceType.EVENT) } returns mockEvent

        mockMvc.perform(get("/v1/events/by-uri")
            .param("uri", uri))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uri").value(uri))
    }

    @Test
    fun `get event by uri should return 404 when not found`() {
        val uri = "https://example.com/non-existent-event"

        every { resourceService.getResourceJsonByUri(uri, ResourceType.EVENT) } returns null

        mockMvc.perform(get("/v1/events/by-uri")
            .param("uri", uri))
            .andExpect(status().isNotFound)
    }
}
