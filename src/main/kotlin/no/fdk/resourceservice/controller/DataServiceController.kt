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
import no.fdk.resourceservice.service.RdfService.RdfFormatStyle
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/data-services")
@Tag(name = "Data Services", description = "API for managing FDK data services")
class DataServiceController(
    resourceService: ResourceService,
    rdfService: RdfService
) : BaseController(resourceService, rdfService) {


    @GetMapping("/{id}")
    @Operation(
        summary = "Get data service by ID",
        description = "Retrieve a specific data service by its unique identifier"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved data service",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Data service not found"
            )
        ]
    )
    fun getDataService(
        @Parameter(description = "Unique identifier of the data service")
        @PathVariable id: String
    ): ResponseEntity<Map<String, Any>> {
        return handleJsonResourceRequest(id, ResourceType.DATA_SERVICE)
    }

    @GetMapping("/by-uri")
    @Operation(
        summary = "Get data service by URI",
        description = "Retrieve a specific data service by its URI"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved data service"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Data service not found"
            )
        ]
    )
    fun getDataServiceByUri(
        @Parameter(description = "URI of the data service")
        @RequestParam uri: String
    ): ResponseEntity<Map<String, Any>> {
        return handleJsonResourceRequestByUri(uri, ResourceType.DATA_SERVICE)
    }

    @GetMapping("/{id}/graph")
    @Operation(
        summary = "Get data service graph by ID",
        description = "Retrieve the RDF graph representation of a specific data service by its unique identifier. Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved data service graph",
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
                description = "Data service not found"
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format"
            )
        ]
    )
    fun getDataServiceGraph(
        @Parameter(description = "Unique identifier of the data service")
        @PathVariable id: String,
        @Parameter(description = "Accept header for content negotiation")
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        @Parameter(description = "RDF format style: 'pretty' (with namespace prefixes, human-readable) or 'standard' (with namespace prefixes, compact)")
        @RequestParam(name = "style", required = false, defaultValue = "pretty") style: String?,
        @Parameter(description = "Whether to expand URIs (clear namespace prefixes, default: false)")
        @RequestParam(name = "expandUris", required = false, defaultValue = "false") expandUris: Boolean?
    ): ResponseEntity<Any> {
        val rdfFormatStyle = try {
            RdfFormatStyle.valueOf(style?.uppercase() ?: "PRETTY")
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid style parameter: $style, using default: PRETTY")
            RdfFormatStyle.PRETTY
        }
        
        return handleGraphRequest(id, ResourceType.DATA_SERVICE, acceptHeader, rdfFormatStyle, expandUris ?: false)
    }

    @GetMapping("/by-uri/graph")
    @Operation(
        summary = "Get data service graph by URI",
        description = "Retrieve the RDF graph representation of a specific data service by its URI. Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved data service graph",
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
                description = "Data service not found"
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format"
            )
        ]
    )
    fun getDataServiceGraphByUri(
        @Parameter(description = "URI of the data service")
        @RequestParam uri: String,
        @Parameter(description = "Accept header for content negotiation")
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?
    ): ResponseEntity<Any> {
        return handleGraphRequestByUri(uri, ResourceType.DATA_SERVICE, acceptHeader)
    }

}
