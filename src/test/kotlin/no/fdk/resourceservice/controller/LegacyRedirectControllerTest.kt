package no.fdk.resourceservice.controller

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class LegacyRedirectControllerTest : BaseControllerTest() {
    @Test
    fun `should redirect concepts endpoint`() {
        mockMvc
            .perform(get("/concepts"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/concepts"))
    }

    @Test
    fun `should redirect datasets endpoint`() {
        mockMvc
            .perform(get("/datasets"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/datasets"))
    }

    @Test
    fun `should redirect services endpoint`() {
        mockMvc
            .perform(get("/services"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/services"))
    }

    @Test
    fun `should redirect data-services endpoint`() {
        mockMvc
            .perform(get("/data-services"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/data-services"))
    }

    @Test
    fun `should redirect information-models endpoint`() {
        mockMvc
            .perform(get("/information-models"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/information-models"))
    }

    @Test
    fun `should redirect events endpoint`() {
        mockMvc
            .perform(get("/events"))
            .andExpect(status().isTemporaryRedirect)
            .andExpect(header().string("Location", "/v1/events"))
    }

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
}
