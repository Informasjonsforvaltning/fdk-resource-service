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

class DataServiceControllerTest : BaseControllerTest() {
    @Test
    fun `should get data service by id`() {
        val dataServiceId = "test-data-service-id"
        val dataServiceData = mapOf("id" to dataServiceId, "title" to "Test Data Service")

        every { resourceService.getResourceJson(dataServiceId, ResourceType.DATA_SERVICE) } returns dataServiceData

        mockMvc
            .perform(get("/v1/data-services/{id}", dataServiceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(dataServiceId))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get data service by uri`() {
        val uri = "https://example.com/data-service"
        val dataServiceData = mapOf("uri" to uri, "title" to "Test Data Service")

        every { resourceService.getResourceJsonByUri(uri, ResourceType.DATA_SERVICE) } returns dataServiceData

        mockMvc
            .perform(
                get("/v1/data-services/by-uri")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.uri").value(uri))
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get data service graph by id`() {
        val dataServiceId = "test-data-service-id"
        val turtleData =
            """<https://example.com/data-service> a <http://example.org/DataService> ;
                |<http://purl.org/dc/terms/title> "Test Data Service" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = dataServiceId,
                resourceType = ResourceType.DATA_SERVICE.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity(dataServiceId, ResourceType.DATA_SERVICE) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.DATA_SERVICE,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(get("/v1/data-services/{id}/graph", dataServiceId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should get data service graph by uri`() {
        val uri = "https://example.com/data-service"
        val turtleData =
            """<https://example.com/data-service> a <http://example.org/DataService> ;
                |<http://purl.org/dc/terms/title> "Test Data Service" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = "test-data-service-id",
                resourceType = ResourceType.DATA_SERVICE.name,
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
                ResourceType.DATA_SERVICE,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(
                get("/v1/data-services/by-uri/graph")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should return 404 when data service not found`() {
        val dataServiceId = "non-existent-id"

        every { resourceService.getResourceJson(dataServiceId, ResourceType.DATA_SERVICE) } returns null

        mockMvc
            .perform(get("/v1/data-services/{id}", dataServiceId))
            .andExpect(status().isNotFound)
    }

    @Test
    fun `findDataServices should return 200 with list of data services when found`() {
        val dataService1 =
            mapOf(
                "id" to "data-service-1",
                "title" to "First Data Service",
                "type" to "DataService",
            )
        val dataService2 =
            mapOf(
                "id" to "data-service-2",
                "title" to "Second Data Service",
                "type" to "DataService",
            )
        val ids = listOf("data-service-1", "data-service-2")
        val requestBody = """{"ids": ["data-service-1", "data-service-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.DATA_SERVICE) } returns listOf(dataService1, dataService2)

        mockMvc
            .perform(
                post("/v1/data-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("data-service-1"))
            .andExpect(jsonPath("$[0].title").value("First Data Service"))
            .andExpect(jsonPath("$[1].id").value("data-service-2"))
            .andExpect(jsonPath("$[1].title").value("Second Data Service"))
    }

    @Test
    fun `findDataServices should return 200 with empty list when no data services found`() {
        val ids = listOf("non-existent-1", "non-existent-2")
        val requestBody = """{"ids": ["non-existent-1", "non-existent-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.DATA_SERVICE) } returns emptyList()

        mockMvc
            .perform(
                post("/v1/data-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }

    @Test
    fun `findDataServices should return 400 when ids list exceeds 100 items`() {
        val ids = (1..101).map { "id-$it" }
        val idsJson = ids.joinToString(",") { "\"$it\"" }
        val requestBody = """{"ids": [$idsJson]}"""

        mockMvc
            .perform(
                post("/v1/data-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isBadRequest)
    }

    @Test
    fun `findDataServices should return 200 when ids list has exactly 100 items`() {
        val ids = (1..100).map { "id-$it" }
        val resources = ids.map { mapOf("id" to it, "title" to "Data Service $it") }
        val idsJson = ids.joinToString(",") { "\"$it\"" }
        val requestBody = """{"ids": [$idsJson]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.DATA_SERVICE) } returns resources

        mockMvc
            .perform(
                post("/v1/data-services")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(100))
    }
}
