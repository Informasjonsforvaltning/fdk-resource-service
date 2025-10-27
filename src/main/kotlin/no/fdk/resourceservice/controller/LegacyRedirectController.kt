package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.context.request.RequestContextHolder
import org.springframework.web.context.request.ServletRequestAttributes

/**
 * Controller for handling legacy endpoint redirects.
 * 
 * This controller provides temporary redirects (HTTP 307) from legacy Go service endpoints
 * to the new Spring Boot v1 API endpoints. This ensures backward compatibility during
 * the migration period.
 * 
 * @deprecated This controller should be removed after clients have migrated to v1 endpoints
 */
@RestController
@Tag(name = "Legacy Redirects", description = "Temporary redirects from legacy endpoints to v1 API")
@Deprecated("Legacy redirects - use v1 endpoints directly")
class LegacyRedirectController {

    private val logger = LoggerFactory.getLogger(LegacyRedirectController::class.java)

    // ===== CONCEPTS =====
    
    @GetMapping("/concepts")
    @Operation(
        summary = "Legacy concepts endpoint redirect",
        description = "Redirects from legacy /concepts to /v1/concepts"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/concepts")
        ]
    )
    fun redirectConcepts(): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/concepts")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/concepts")
            .build()
    }

    @GetMapping("/concepts/{id}")
    @Operation(
        summary = "Legacy concept endpoint redirect",
        description = "Redirects from legacy /concepts/{id} to /v1/concepts/{id}"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/concepts/{id}")
        ]
    )
    fun redirectConcept(
        @Parameter(description = "Concept ID")
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/concepts/$id")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/concepts/$id")
            .build()
    }

    // ===== DATA SERVICES =====
    
    @GetMapping("/data-services")
    @Operation(
        summary = "Legacy data services endpoint redirect",
        description = "Redirects from legacy /data-services to /v1/data-services"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/data-services")
        ]
    )
    fun redirectDataServices(): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/data-services")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/data-services")
            .build()
    }

    @GetMapping("/data-services/{id}")
    @Operation(
        summary = "Legacy data service endpoint redirect",
        description = "Redirects from legacy /data-services/{id} to /v1/data-services/{id}"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/data-services/{id}")
        ]
    )
    fun redirectDataService(
        @Parameter(description = "Data service ID")
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/data-services/$id")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/data-services/$id")
            .build()
    }

    // ===== DATASETS =====
    
    @GetMapping("/datasets")
    @Operation(
        summary = "Legacy datasets endpoint redirect",
        description = "Redirects from legacy /datasets to /v1/datasets"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/datasets")
        ]
    )
    fun redirectDatasets(): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/datasets")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/datasets")
            .build()
    }

    @GetMapping("/datasets/{id}")
    @Operation(
        summary = "Legacy dataset endpoint redirect",
        description = "Redirects from legacy /datasets/{id} to /v1/datasets/{id}"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/datasets/{id}")
        ]
    )
    fun redirectDataset(
        @Parameter(description = "Dataset ID")
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/datasets/$id")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/datasets/$id")
            .build()
    }

    // ===== EVENTS =====
    
    @GetMapping("/events")
    @Operation(
        summary = "Legacy events endpoint redirect",
        description = "Redirects from legacy /events to /v1/events"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/events")
        ]
    )
    fun redirectEvents(): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/events")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/events")
            .build()
    }

    @GetMapping("/events/{id}")
    @Operation(
        summary = "Legacy event endpoint redirect",
        description = "Redirects from legacy /events/{id} to /v1/events/{id}"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/events/{id}")
        ]
    )
    fun redirectEvent(
        @Parameter(description = "Event ID")
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/events/$id")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/events/$id")
            .build()
    }

    // ===== INFORMATION MODELS =====
    
    @GetMapping("/information-models")
    @Operation(
        summary = "Legacy information models endpoint redirect",
        description = "Redirects from legacy /information-models to /v1/information-models"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/information-models")
        ]
    )
    fun redirectInformationModels(): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/information-models")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/information-models")
            .build()
    }

    @GetMapping("/information-models/{id}")
    @Operation(
        summary = "Legacy information model endpoint redirect",
        description = "Redirects from legacy /information-models/{id} to /v1/information-models/{id}"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/information-models/{id}")
        ]
    )
    fun redirectInformationModel(
        @Parameter(description = "Information model ID")
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/information-models/$id")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/information-models/$id")
            .build()
    }

    // ===== SERVICES =====
    
    @GetMapping("/services")
    @Operation(
        summary = "Legacy services endpoint redirect",
        description = "Redirects from legacy /services to /v1/services"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/services")
        ]
    )
    fun redirectServices(): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/services")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/services")
            .build()
    }

    @GetMapping("/services/{id}")
    @Operation(
        summary = "Legacy service endpoint redirect",
        description = "Redirects from legacy /services/{id} to /v1/services/{id}"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /v1/services/{id}")
        ]
    )
    fun redirectService(
        @Parameter(description = "Service ID")
        @PathVariable id: String
    ): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /v1/services/$id")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/v1/services/$id")
            .build()
    }

    // ===== HEALTH ENDPOINTS =====
    
    @GetMapping("/ping")
    @Operation(
        summary = "Legacy ping endpoint redirect",
        description = "Redirects from legacy /ping to /health"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /health")
        ]
    )
    fun redirectPing(): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /health")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/health")
            .build()
    }

    @GetMapping("/ready")
    @Operation(
        summary = "Legacy ready endpoint redirect",
        description = "Redirects from legacy /ready to /health"
    )
    @ApiResponses(
        value = [
            ApiResponse(responseCode = "307", description = "Temporary redirect to /health")
        ]
    )
    fun redirectReady(): ResponseEntity<Void> {
        val request = (RequestContextHolder.currentRequestAttributes() as ServletRequestAttributes).request
        logger.info("Legacy redirect: ${request.requestURI} -> /health")
        return ResponseEntity.status(HttpStatus.TEMPORARY_REDIRECT)
            .header("Location", "/health")
            .build()
    }
}