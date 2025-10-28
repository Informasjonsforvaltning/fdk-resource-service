package no.fdk.resourceservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.mockk.every
import io.mockk.mockk
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.ResourceService
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.*

class DataServiceControllerTest : BaseControllerTest() {

    @Test
    fun `should get data service by id`() {
        val dataServiceId = "test-data-service-id"
        val dataServiceData = mapOf("id" to dataServiceId, "title" to "Test Data Service")

        every { resourceService.getResourceJson(dataServiceId, ResourceType.DATA_SERVICE) } returns dataServiceData

        mockMvc.perform(get("/v1/data-services/{id}", dataServiceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(dataServiceId))
            .andExpect(jsonPath("$.title").value("Test Data Service"))
    }

    @Test
    fun `should get data service by uri`() {
        val uri = "https://example.com/data-service"
        val dataServiceData = mapOf("uri" to uri, "title" to "Test Data Service")

        every { resourceService.getResourceJsonByUri(uri, ResourceType.DATA_SERVICE) } returns dataServiceData

        mockMvc.perform(get("/v1/data-services/by-uri")
            .param("uri", uri))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.uri").value(uri))
            .andExpect(jsonPath("$.title").value("Test Data Service"))
    }

    @Test
    fun `should get data service graph by id`() {
        val dataServiceId = "test-data-service-id"
        val graphData = mapOf("@id" to "https://example.com/data-service", "title" to "Test Data Service")

        every { resourceService.getResourceJsonLd(dataServiceId, ResourceType.DATA_SERVICE) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(graphData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, false) } returns """{"@id":"https://example.com/data-service","title":"Test Data Service"}"""
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc.perform(get("/v1/data-services/{id}/graph", dataServiceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/data-service"))
            .andExpect(jsonPath("$.title").value("Test Data Service"))
    }

    @Test
    fun `should get data service graph by uri`() {
        val uri = "https://example.com/data-service"
        val graphData = mapOf("@id" to uri, "title" to "Test Data Service")

        every { resourceService.getResourceJsonLdByUri(uri, ResourceType.DATA_SERVICE) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(graphData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, false) } returns """{"@id":"https://example.com/data-service","title":"Test Data Service"}"""
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc.perform(get("/v1/data-services/by-uri/graph")
            .param("uri", uri))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(uri))
            .andExpect(jsonPath("$.title").value("Test Data Service"))
    }

    @Test
    fun `should return 404 when data service not found`() {
        val dataServiceId = "non-existent-id"

        every { resourceService.getResourceJson(dataServiceId, ResourceType.DATA_SERVICE) } returns null

        mockMvc.perform(get("/v1/data-services/{id}", dataServiceId))
            .andExpect(status().isNotFound)
    }
}