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
        // Turtle format - content verified via string match
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
        // Turtle format - content verified via string match
    }

    @Test
    fun `should get information model graph by id`() {
        val informationModelId = "test-information-model-id"
        val turtleData =
            """<https://example.com/information-model> a <http://example.org/InformationModel> ;
                |<http://purl.org/dc/terms/title> "Test Information Model" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = informationModelId,
                resourceType = ResourceType.INFORMATION_MODEL.name,
                resourceGraphData = turtleData,
                resourceGraphFormat = "TURTLE",
            )

        every { resourceService.getResourceEntity(informationModelId, ResourceType.INFORMATION_MODEL) } returns entity
        every { rdfService.getBestFormat(null) } returns RdfService.RdfFormat.TURTLE
        every {
            rdfService.convertFromFormat(
                turtleData,
                "TURTLE",
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                true,
                ResourceType.INFORMATION_MODEL,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(get("/v1/information-models/{id}/graph", informationModelId))
            .andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
    }

    @Test
    fun `should get information model graph by uri`() {
        val uri = "https://example.com/information-model"
        val turtleData =
            """<https://example.com/information-model> a <http://example.org/InformationModel> ;
                |<http://purl.org/dc/terms/title> "Test Information Model" .
            """.trimMargin()
        val entity =
            ResourceEntity(
                id = "test-information-model-id",
                resourceType = ResourceType.INFORMATION_MODEL.name,
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
                ResourceType.INFORMATION_MODEL,
            )
        } returns turtleData
        every { rdfService.getContentType(RdfService.RdfFormat.TURTLE) } returns MediaType("text", "turtle")

        mockMvc
            .perform(
                get("/v1/information-models/by-uri/graph")
                    .param("uri", uri),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType("text", "turtle")))
            .andExpect(content().string(turtleData))
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
    fun `findInformationModels should return 200 with list of information models when found`() {
        val model1 =
            mapOf(
                "id" to "model-1",
                "title" to "First Information Model",
                "type" to "InformationModel",
            )
        val model2 =
            mapOf(
                "id" to "model-2",
                "title" to "Second Information Model",
                "type" to "InformationModel",
            )
        val ids = listOf("model-1", "model-2")
        val requestBody = """{"ids": ["model-1", "model-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.INFORMATION_MODEL) } returns listOf(model1, model2)

        mockMvc
            .perform(
                post("/v1/information-models")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value("model-1"))
            .andExpect(jsonPath("$[0].title").value("First Information Model"))
            .andExpect(jsonPath("$[1].id").value("model-2"))
            .andExpect(jsonPath("$[1].title").value("Second Information Model"))
    }

    @Test
    fun `findInformationModels should return 200 with empty list when no models found`() {
        val ids = listOf("non-existent-1", "non-existent-2")
        val requestBody = """{"ids": ["non-existent-1", "non-existent-2"]}"""

        every { resourceService.getResourceJsonListById(ids, ResourceType.INFORMATION_MODEL) } returns emptyList()

        mockMvc
            .perform(
                post("/v1/information-models")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(requestBody),
            ).andExpect(status().isOk)
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isArray)
            .andExpect(jsonPath("$.length()").value(0))
    }
}
