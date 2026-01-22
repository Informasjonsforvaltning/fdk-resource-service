package no.fdk.resourceservice.controller

import io.mockk.every
import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.content
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status

class ServiceControllerTest : BaseControllerTest() {
    @Test
    fun `should get service by id`() {
        val serviceId = "test-service-id"
        val serviceData = mapOf("id" to serviceId, "title" to "Test Service")

        every { resourceService.getResourceJson(serviceId, ResourceType.SERVICE) } returns serviceData

        mockMvc
            .perform(get("/v1/services/{id}", serviceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(serviceId))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get service by uri`() {
        val uri = "https://example.com/service"
        val serviceData = mapOf("uri" to uri, "title" to "Test Service")

        every { resourceService.getResourceJsonByUri(uri, ResourceType.SERVICE) } returns serviceData

        mockMvc
            .perform(
                get("/v1/services/by-uri")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.uri").value(uri))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get service graph by id`() {
        val serviceId = "test-service-id"
        val turtleData =
            """<https://example.com/service> a <http://example.org/Service> ;
                |<http://purl.org/dc/terms/title> "Test Service" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = serviceId,
                resourceType = ResourceType.SERVICE.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity(serviceId, ResourceType.SERVICE) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.SERVICE,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(get("/v1/services/{id}/graph", serviceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should get service graph by uri`() {
        val uri = "https://example.com/service"
        val turtleData =
            """<https://example.com/service> a <http://example.org/Service> ;
                |<http://purl.org/dc/terms/title> "Test Service" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = "test-service-id",
                resourceType = ResourceType.SERVICE.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
                uri = uri,
            )

        every { resourceService.getResourceEntityByUri(uri) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.SERVICE,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(
                get("/v1/services/by-uri/graph")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should return 404 when service not found`() {
        val serviceId = "non-existent-id"

        every { resourceService.getResourceJson(serviceId, ResourceType.SERVICE) } returns null

        mockMvc
            .perform(get("/v1/services/{id}", serviceId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `findServices should return 200 with list of services when found`() {
        val service1 =
            mapOf(
                "id" to "service-1",
                "title" to "First Service",
                "type" to "Service",
            )
        val service2 =
            mapOf(
                "id" to "service-2",
                "title" to "Second Service",
                "type" to "Service",
            )
        val ids = listOf("service-1", "service-2")
        val requestBody = """{"ids": ["service-1", "service-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.SERVICE) } returns listOf(service1, service2)

        mockMvc
            .perform(
                post("/v1/services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("service-1"))
            .andExpect(jsonPath("$[0].title").value("First Service"))
            .andExpect(jsonPath("$[1].id").value("service-2"))
            .andExpect(jsonPath("$[1].title").value("Second Service"))
    }

    @Test
    fun `findServices should return 200 with empty list when no services found`() {
        val ids = listOf("non-existent-1", "non-existent-2")
        val requestBody = """{"ids": ["non-existent-1", "non-existent-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.SERVICE) } returns emptyList()

        mockMvc
            .perform(
                post("/v1/services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `findServices should return 400 when ids list exceeds 100 items`() {
        val ids = (1..101).map { "id-$it" }
        val idsJson = ids.joinToString(",") { "\"$it\"" }
        val requestBody = """{"ids": [$idsJson]}"""

        mockMvc
            .perform(
                post("/v1/services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `findServices should return 200 when ids list has exactly 100 items`() {
        val ids = (1..100).map { "id-$it" }
        val resources = ids.map { mapOf("id" to it, "title" to "Service $it") }
        val idsJson = ids.joinToString(",") { "\"$it\"" }
        val requestBody = """{"ids": [$idsJson]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.SERVICE) } returns resources

        mockMvc
            .perform(
                post("/v1/services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(100))
    }
}
