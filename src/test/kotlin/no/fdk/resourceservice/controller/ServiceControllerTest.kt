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

class ServiceControllerTest : BaseControllerTest() {

    @Test
    fun `should get service by id`() {
        val serviceId = "test-service-id"
        val serviceData = mapOf("id" to serviceId, "title" to "Test Service")

        every { resourceService.getResourceJson(serviceId, ResourceType.SERVICE) } returns serviceData

        mockMvc.perform(get("/v1/services/{id}", serviceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(serviceId))
            .andExpect(jsonPath("$.title").value("Test Service"))
    }

    @Test
    fun `should get service by uri`() {
        val uri = "https://example.com/service"
        val serviceData = mapOf("uri" to uri, "title" to "Test Service")

        every { resourceService.getResourceJsonByUri(uri, ResourceType.SERVICE) } returns serviceData

        mockMvc.perform(get("/v1/services/by-uri")
            .param("uri", uri))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.uri").value(uri))
            .andExpect(jsonPath("$.title").value("Test Service"))
    }

    @Test
    fun `should get service graph by id`() {
        val serviceId = "test-service-id"
        val graphData = mapOf("@id" to "https://example.com/service", "title" to "Test Service")

        every { resourceService.getResourceJsonLd(serviceId, ResourceType.SERVICE) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(graphData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.SERVICE) } returns """{"@id":"https://example.com/service","title":"Test Service"}"""
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc.perform(get("/v1/services/{id}/graph", serviceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/service"))
            .andExpect(jsonPath("$.title").value("Test Service"))
    }

    @Test
    fun `should get service graph by uri`() {
        val uri = "https://example.com/service"
        val graphData = mapOf("@id" to uri, "title" to "Test Service")

        every { resourceService.getResourceJsonLdByUri(uri, ResourceType.SERVICE) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(graphData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.PRETTY, true, ResourceType.SERVICE) } returns """{"@id":"https://example.com/service","title":"Test Service"}"""
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc.perform(get("/v1/services/by-uri/graph")
            .param("uri", uri))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(uri))
            .andExpect(jsonPath("$.title").value("Test Service"))
    }

    @Test
    fun `should return 404 when service not found`() {
        val serviceId = "non-existent-id"

        every { resourceService.getResourceJson(serviceId, ResourceType.SERVICE) } returns null

        mockMvc.perform(get("/v1/services/{id}", serviceId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `should get service graph by id with standard style`() {
        val serviceId = "test-service-id"
        val graphData = mapOf("@id" to "https://example.com/service", "title" to "Test Service")
        val standardJsonLd = """{"@id":"https://example.com/service","title":"Test Service"}"""

        every { resourceService.getResourceJsonLd(serviceId, ResourceType.SERVICE) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(graphData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.STANDARD, true, ResourceType.SERVICE) } returns standardJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc.perform(get("/v1/services/{id}/graph", serviceId)
            .param("style", "standard"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value("https://example.com/service"))
    }

    @Test
    fun `should get service graph by uri with standard style`() {
        val uri = "https://example.com/service"
        val graphData = mapOf("@id" to uri, "title" to "Test Service")
        val standardJsonLd = """{"@id":"https://example.com/service","title":"Test Service"}"""

        every { resourceService.getResourceJsonLdByUri(uri, ResourceType.SERVICE) } returns graphData
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.JSON_LD
        every { rdfService.convertFromJsonLd(graphData, RdfService.RdfFormat.JSON_LD, RdfService.RdfFormatStyle.STANDARD, true, ResourceType.SERVICE) } returns standardJsonLd
        every { rdfService.getContentType(RdfService.RdfFormat.JSON_LD) } returns MediaType.APPLICATION_JSON

        mockMvc.perform(get("/v1/services/by-uri/graph")
            .param("uri", uri)
            .param("style", "standard"))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.@id").value(uri))
    }
}