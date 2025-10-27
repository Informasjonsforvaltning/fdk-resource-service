package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@Tag(name = "Health", description = "Health check endpoints")
class HealthController {

    @GetMapping("/health")
    @Operation(
        summary = "Health check",
        description = "Check if the service is running and healthy"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Service is healthy",
                content = [Content(schema = Schema(implementation = Map::class))]
            )
        ]
    )
    fun health(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("status" to "UP"))
    }
}
