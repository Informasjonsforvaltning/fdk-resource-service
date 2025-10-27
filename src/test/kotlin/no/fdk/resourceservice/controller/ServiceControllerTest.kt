package no.fdk.resourceservice.controller

import io.mockk.every
import no.fdk.resourceservice.model.ResourceType
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class ServiceControllerTest : BaseControllerTest() {

    @Test
    fun `get service by id should return 200 when found`() {
        val serviceId = "test-service-1"
        val mockService = mapOf(
            "id" to serviceId,
            "type" to "SERVICE",
            "title" to "Test Service"
        )

        every { resourceService.getResourceJson(serviceId, ResourceType.SERVICE) } returns mockService

        mockMvc.perform(get("/v1/services/{id}", serviceId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(serviceId))
            .andExpect(jsonPath("$.type").value("SERVICE"))
            .andExpect(jsonPath("$.title").value("Test Service"))
    }

    @Test
    fun `get service by id should return 404 when not found`() {
        val serviceId = "non-existent-service"

        every { resourceService.getResourceJson(serviceId, ResourceType.SERVICE) } returns null

        mockMvc.perform(get("/v1/services/{id}", serviceId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get service by uri should return 200 when found`() {
        val uri = "https://example.com/service"
        val mockService = mapOf(
            "id" to "test-service-1",
            "type" to "SERVICE",
            "uri" to uri
        )

        every { resourceService.getResourceJsonByUri(uri, ResourceType.SERVICE) } returns mockService

        mockMvc.perform(get("/v1/services/by-uri")
            .param("uri", uri))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uri").value(uri))
    }

    @Test
    fun `get service by uri should return 404 when not found`() {
        val uri = "https://example.com/non-existent-service"

        every { resourceService.getResourceJsonByUri(uri, ResourceType.SERVICE) } returns null

        mockMvc.perform(get("/v1/services/by-uri")
            .param("uri", uri))
            .andExpect(status().isNotFound)
    }
}
