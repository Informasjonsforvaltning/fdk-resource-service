package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.ResourceType
import org.junit.jupiter.api.Test
import io.mockk.every
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class InformationModelControllerTest : BaseControllerTest() {

    @Test
    fun `get information model by id should return 200 when found`() {
        val informationModelId = "test-information-model-1"
        val mockInformationModel = mapOf(
            "id" to informationModelId,
            "type" to "INFORMATION_MODEL",
            "title" to "Test Information Model"
        )

        every { resourceService.getResourceJson(informationModelId, ResourceType.INFORMATION_MODEL) } returns mockInformationModel

        mockMvc.perform(get("/v1/information-models/{id}", informationModelId))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.id").value(informationModelId))
            .andExpect(jsonPath("$.type").value("INFORMATION_MODEL"))
            .andExpect(jsonPath("$.title").value("Test Information Model"))
    }

    @Test
    fun `get information model by id should return 404 when not found`() {
        val informationModelId = "non-existent-information-model"

        every { resourceService.getResourceJson(informationModelId, ResourceType.INFORMATION_MODEL) } returns null

        mockMvc.perform(get("/v1/information-models/{id}", informationModelId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `get information model by uri should return 200 when found`() {
        val uri = "https://example.com/information-model"
        val mockInformationModel = mapOf(
            "id" to "test-information-model-1",
            "type" to "INFORMATION_MODEL",
            "uri" to uri
        )

        every { resourceService.getResourceJsonByUri(uri, ResourceType.INFORMATION_MODEL) } returns mockInformationModel

        mockMvc.perform(get("/v1/information-models/by-uri")
            .param("uri", uri))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.uri").value(uri))
    }

    @Test
    fun `get information model by uri should return 404 when not found`() {
        val uri = "https://example.com/non-existent-information-model"

        every { resourceService.getResourceJsonByUri(uri, ResourceType.INFORMATION_MODEL) } returns null

        mockMvc.perform(get("/v1/information-models/by-uri")
            .param("uri", uri))
            .andExpect(status().isNotFound)
    }
}
