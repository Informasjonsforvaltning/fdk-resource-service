package no.fdk.resourceservice.controller

import com.ninjasquad.springmockk.MockkBean
import no.fdk.resourceservice.service.ResourceService
import no.fdk.resourceservice.service.RdfService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest
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
abstract class BaseControllerTest {

    @Autowired
    protected lateinit var mockMvc: MockMvc

    @MockkBean
    protected lateinit var resourceService: ResourceService

    @MockkBean
    protected lateinit var rdfService: RdfService
}
