package no.fdk.resourceservice.controller

import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class HealthControllerTest : BaseControllerTest() {
    @Test
    fun `health endpoint should return 200 OK`() {
        mockMvc
            .perform(get("/health"))
            .andExpect(status().isOk)
            .andExpect(content().json("""{"status":"UP"}"""))
    }
}
