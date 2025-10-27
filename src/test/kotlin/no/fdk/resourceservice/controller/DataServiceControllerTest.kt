package no.fdk.resourceservice.controller

import io.mockk.every
import no.fdk.resourceservice.model.ResourceType
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class DataServiceControllerTest : BaseControllerTest() {

    @Test
    fun `get data service by id should return 200 when found`() {
        val dataServiceId = "test-data-service-1"
        val mockDataService = mapOf(
            "id" to dataServiceId,
            "type" to "DATA_SERVICE",
            "name" to "Test Data Service"
        )

        every { resourceService.getResourceJson(dataServiceId, ResourceType.DATA_SERVICE) } returns mockDataService

        mockMvc.perform(get("/v1/data-services/{id}", dataServiceId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(dataServiceId))
            .andExpect(jsonPath("$.type").value("DATA_SERVICE"))
            .andExpect(jsonPath("$.name").value("Test Data Service"))
    }

    @Test
    fun `get data service by id should return 404 when not found`() {
        val dataServiceId = "non-existent-data-service"

        every { resourceService.getResourceJson(dataServiceId, ResourceType.DATA_SERVICE) } returns null

        mockMvc.perform(get("/v1/data-services/{id}", dataServiceId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get data service by uri should return 200 when found`() {
        val uri = "https://example.com/data-service"
        val mockDataService = mapOf(
            "id" to "test-data-service-1",
            "type" to "DATA_SERVICE",
            "uri" to uri
        )

        every { resourceService.getResourceJsonByUri(uri, ResourceType.DATA_SERVICE) } returns mockDataService

        mockMvc.perform(get("/v1/data-services/by-uri")
            .param("uri", uri))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uri").value(uri))
    }

    @Test
    fun `get data service by uri should return 404 when not found`() {
        val uri = "https://example.com/non-existent-data-service"

        every { resourceService.getResourceJsonByUri(uri, ResourceType.DATA_SERVICE) } returns null

        mockMvc.perform(get("/v1/data-services/by-uri")
            .param("uri", uri))
            .andExpect(status().isNotFound)
    }
}
