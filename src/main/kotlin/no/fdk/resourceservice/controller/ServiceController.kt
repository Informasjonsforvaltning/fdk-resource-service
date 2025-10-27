package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.ResourceService
import no.fdk.resourceservice.service.RdfService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/services")
@Tag(name = "Services", description = "API for managing FDK services")
class ServiceController(
    resourceService: ResourceService,
    rdfService: RdfService
) : BaseController(resourceService, rdfService) {


    @GetMapping("/{id}")
    @Operation(
        summary = "Get service by ID",
        description = "Retrieve a specific service by its unique identifier"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved service",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Service not found"
            )
        ]
    )
    fun getService(
        @Parameter(description = "Unique identifier of the service")
        @PathVariable id: String
    ): ResponseEntity<Map<String, Any>> {
        return handleJsonResourceRequest(id, ResourceType.SERVICE)
    }

    @GetMapping("/by-uri")
    @Operation(
        summary = "Get service by URI",
        description = "Retrieve a specific service by its URI"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved service"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Service not found"
            )
        ]
    )
    fun getServiceByUri(
        @Parameter(description = "URI of the service")
        @RequestParam uri: String
    ): ResponseEntity<Map<String, Any>> {
        return handleJsonResourceRequestByUri(uri, ResourceType.SERVICE)
    }

    @GetMapping("/{id}/graph")
    @Operation(
        summary = "Get service graph by ID",
        description = "Retrieve the RDF graph representation of a specific service by its unique identifier. Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved service graph",
                content = [
                    Content(mediaType = "application/ld+json"),
                    Content(mediaType = "text/turtle"),
                    Content(mediaType = "application/rdf+xml"),
                    Content(mediaType = "application/n-triples"),
                    Content(mediaType = "application/n-quads")
                ]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Service not found"
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format"
            )
        ]
    )
    fun getServiceGraph(
        @Parameter(description = "Unique identifier of the service")
        @PathVariable id: String,
        @Parameter(description = "Accept header for content negotiation")
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?
    ): ResponseEntity<Any> {
        return handleGraphRequest(id, ResourceType.SERVICE, acceptHeader)
    }

    @GetMapping("/by-uri/graph")
    @Operation(
        summary = "Get service graph by URI",
        description = "Retrieve the RDF graph representation of a specific service by its URI. Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved service graph",
                content = [
                    Content(mediaType = "application/ld+json"),
                    Content(mediaType = "text/turtle"),
                    Content(mediaType = "application/rdf+xml"),
                    Content(mediaType = "application/n-triples"),
                    Content(mediaType = "application/n-quads")
                ]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Service not found"
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format"
            )
        ]
    )
    fun getServiceGraphByUri(
        @Parameter(description = "URI of the service")
        @RequestParam uri: String,
        @Parameter(description = "Accept header for content negotiation")
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?
    ): ResponseEntity<Any> {
        return handleGraphRequestByUri(uri, ResourceType.SERVICE, acceptHeader)
    }

}

