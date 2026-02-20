package no.fdk.resourceservice.controller

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class LegacyRedirectControllerTest : BaseControllerTest() {
    @Test
    fun `should redirect concepts graph endpoint`() {
        mockMvc
            .perform(get("/concepts/graph"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/concepts/graph"))
    }

    @Test
    fun `should redirect datasets graph endpoint`() {
        mockMvc
            .perform(get("/datasets/graph"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/datasets/graph"))
    }

    @Test
    fun `should redirect services graph endpoint`() {
        mockMvc
            .perform(get("/services/graph"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/services/graph"))
    }

    @Test
    fun `should redirect data-services graph endpoint`() {
        mockMvc
            .perform(get("/data-services/graph"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/data-services/graph"))
    }

    @Test
    fun `should redirect information-models graph endpoint`() {
        mockMvc
            .perform(get("/information-models/graph"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/information-models/graph"))
    }

    @Test
    fun `should redirect events graph endpoint`() {
        mockMvc
            .perform(get("/events/graph"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/events/graph"))
    }

    @Test
    fun `should redirect concept by id endpoint`() {
        mockMvc
            .perform(get("/concepts/test-concept-id"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/concepts/test-concept-id"))
    }

    @Test
    fun `should redirect dataset by id endpoint`() {
        mockMvc
            .perform(get("/datasets/test-dataset-id"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/datasets/test-dataset-id"))
    }

    @Test
    fun `should redirect service by id endpoint`() {
        mockMvc
            .perform(get("/services/test-service-id"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/services/test-service-id"))
    }

    @Test
    fun `should redirect data-service by id endpoint`() {
        mockMvc
            .perform(get("/data-services/test-dataservice-id"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/data-services/test-dataservice-id"))
    }

    @Test
    fun `should redirect event by id endpoint`() {
        mockMvc
            .perform(get("/events/test-event-id"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/events/test-event-id"))
    }

    @Test
    fun `should redirect information-model by id endpoint`() {
        mockMvc
            .perform(get("/information-models/test-infomodel-id"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/information-models/test-infomodel-id"))
    }

    @Test
    fun `should redirect ping to health`() {
        mockMvc
            .perform(get("/ping"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/health"))
    }

    @Test
    fun `should redirect ready to health`() {
        mockMvc
            .perform(get("/ready"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/health"))
    }
}
