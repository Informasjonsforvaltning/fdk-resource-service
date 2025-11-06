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

class InformationModelControllerTest : BaseControllerTest() {
    @Test
    fun `should get information model by id`() {
        val informationModelId = "test-information-model-id"
        val informationModelData = mapOf("id" to informationModelId, "title" to "Test Information Model")

        every { resourceService.getResourceJson(informationModelId, ResourceType.INFORMATION_MODEL) } returns informationModelData

        mockMvc
            .perform(get("/v1/information-models/{id}", informationModelId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(informationModelId))
            .andExpect(jsonPath("$.title").value("Test Information Model"))
    }

    @Test
    fun `should get information model by uri`() {
        val uri = "https://example.com/information-model"
        val informationModelData = mapOf("uri" to uri, "title" to "Test Information Model")

        every { resourceService.getResourceJsonByUri(uri, ResourceType.INFORMATION_MODEL) } returns informationModelData

        mockMvc
            .perform(
                get("/v1/information-models/by-uri")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.uri").value(uri))
            .andExpect(jsonPath("$.title").value("Test Information Model"))
    }

    @Test
    fun `should get information model graph by id`() {
        val informationModelId = "test-information-model-id"
        val graphData = mapOf("@id" to "https://example.com/information-model", "title" to "Test Information Model")

        every { resourceService.getResourceJsonLd(informationModelId, ResourceType.INFORMATION_MODEL) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                graphData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.INFORMATION_MODEL,
            )
        } returns """{"@id":"https://example.com/information-model","title":"Test Information Model"}"""
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc
            .perform(get("/v1/information-models/{id}/graph", informationModelId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/information-model"))
            .andExpect(jsonPath("$.title").value("Test Information Model"))
    }

    @Test
    fun `should get information model graph by uri`() {
        val uri = "https://example.com/information-model"
        val graphData = mapOf("@id" to uri, "title" to "Test Information Model")

        every { resourceService.getResourceJsonLdByUri(uri, ResourceType.INFORMATION_MODEL) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                graphData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.INFORMATION_MODEL,
            )
        } returns """{"@id":"https://example.com/information-model","title":"Test Information Model"}"""
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc
            .perform(
                get("/v1/information-models/by-uri/graph")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(uri))
            .andExpect(jsonPath("$.title").value("Test Information Model"))
    }

    @Test
    fun `should return 404 when information model not found`() {
        val informationModelId = "non-existent-id"

        every { resourceService.getResourceJson(informationModelId, ResourceType.INFORMATION_MODEL) } returns null

        mockMvc
            .perform(get("/v1/information-models/{id}", informationModelId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should get information model graph by id with standard style`() {
        val informationModelId = "test-information-model-id"
        val graphData = mapOf("@id" to "https://example.com/information-model", "title" to "Test Information Model")
        val standardJsonLd = """{"@id":"https://example.com/information-model","title":"Test Information Model"}"""

        every { resourceService.getResourceJsonLd(informationModelId, ResourceType.INFORMATION_MODEL) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                graphData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.STANDARD,
                true,
                ResourceType.INFORMATION_MODEL,
            )
        } returns standardJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc
            .perform(
                get("/v1/information-models/{id}/graph", informationModelId)
                    .param("style", "standard"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/information-model"))
    }

    @Test
    fun `should get information model graph by uri with standard style`() {
        val uri = "https://example.com/information-model"
        val graphData = mapOf("@id" to uri, "title" to "Test Information Model")
        val standardJsonLd = """{"@id":"https://example.com/information-model","title":"Test Information Model"}"""

        every { resourceService.getResourceJsonLdByUri(uri, ResourceType.INFORMATION_MODEL) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every {
            rdfService.convertFromJsonLd(
                graphData,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.STANDARD,
                true,
                ResourceType.INFORMATION_MODEL,
            )
        } returns standardJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc
            .perform(
                get("/v1/information-models/by-uri/graph")
                    .param("uri", uri)
                    .param("style", "standard"),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(uri))
    }
}
