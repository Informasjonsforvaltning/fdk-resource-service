package no.fdk.resourceservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.ninjasquad.springmockk.MockkBean
import no.fdk.resourceservice.service.RdfService
import no.fdk.resourceservice.service.ResourceService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.TestConfiguration
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Import
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.web.servlet.MockMvc

/**
 * Base class for controller tests that provides common setup and dependencies.
 *
 * This class eliminates code duplication across controller tests by providing:
 * - WebMvcTest annotation for web layer testing only (no database, no full context)
 * - MockMvc setup for HTTP testing
 * - Mocked ResourceService and RdfFormatService for unit testing using MockK
 * - Fast execution without external dependencies
 * - Security disabled via application-test.yml
 */
@WebMvcTest
@ActiveProfiles("test")
@Import(BaseControllerTest.TestObjectMapperConfig::class)
abstract class BaseControllerTest {
    @Autowired
    protected lateinit var mockMvc: MockMvc

    @MockkBean
    protected lateinit var resourceService: ResourceService

    @MockkBean
    protected lateinit var rdfService: RdfService

    @TestConfiguration
    class TestObjectMapperConfig {
        @Bean
        fun objectMapper(): ObjectMapper = jacksonObjectMapper()
    }
}
