package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import no.fdk.resourceservice.service.RdfService.RdfFormatStyle
import no.fdk.resourceservice.service.ResourceService
import org.springframework.http.HttpHeaders
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/v1/information-models")
@Tag(name = "Information Models", description = "API for retrieving information models")
class InformationModelController(
    resourceService: ResourceService,
    rdfService: RdfService,
) : BaseController(resourceService, rdfService) {
    @GetMapping("/{id}")
    @Operation(
        summary = "Get information model by ID",
        description = "Retrieve a specific information model by its unique identifier",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved information model",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Information model not found",
            ),
        ],
    )
    fun getInformationModel(
        @Parameter(description = "Unique identifier of the information model")
        @PathVariable id: String,
    ): ResponseEntity<Map<String, Any>> = handleJsonResourceRequest(id, ResourceType.INFORMATION_MODEL)

    @GetMapping("/by-uri")
    @Operation(
        summary = "Get information model by URI",
        description = "Retrieve a specific information model by its URI",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved information model",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Information model not found",
            ),
        ],
    )
    fun getInformationModelByUri(
        @Parameter(description = "URI of the information model")
        @RequestParam uri: String,
    ): ResponseEntity<Map<String, Any>> = handleJsonResourceRequestByUri(uri, ResourceType.INFORMATION_MODEL)

    @GetMapping(
        "/{id}/graph",
        produces = [
            "application/ld+json",
            "text/turtle",
            "application/rdf+xml",
            "application/n-triples",
            "application/n-quads",
        ],
    )
    @Operation(
        summary = "Get information model graph by ID",
        description =
            "Retrieve the RDF graph representation of a specific information model by its unique identifier. " +
                "Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved information model graph",
                content = [
                    Content(mediaType = "application/ld+json"),
                    Content(mediaType = "text/turtle"),
                    Content(mediaType = "application/rdf+xml"),
                    Content(mediaType = "application/n-triples"),
                    Content(mediaType = "application/n-quads"),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Information model not found",
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format",
            ),
        ],
    )
    fun getInformationModelGraph(
        @Parameter(description = "Unique identifier of the information model")
        @PathVariable id: String,
        @Parameter(
            description =
                "Accept header for content negotiation: application/ld+json, text/turtle, " +
                    "application/rdf+xml, application/n-triples, application/n-quads",
            hidden = true,
        )
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        @Parameter(
            description = "RDF format style: 'pretty' (human-readable) or 'standard' (compact). Default: pretty",
            schema = Schema(type = "string", allowableValues = ["pretty", "standard"]),
            example = "pretty",
        )
        @RequestParam(name = "style", required = false, defaultValue = "pretty") style: String?,
        @Parameter(description = "Whether to expand URIs (clear namespace prefixes). Default: true")
        @RequestParam(name = "expandUris", required = false, defaultValue = "true") expandUris: Boolean?,
    ): ResponseEntity<Any> {
        val rdfFormatStyle =
            try {
                RdfFormatStyle.valueOf((style ?: "pretty").uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid style parameter: $style, using default: pretty")
                RdfFormatStyle.PRETTY
            }

        return handleGraphRequest(id, ResourceType.INFORMATION_MODEL, acceptHeader, rdfFormatStyle, expandUris ?: true)
    }

    @GetMapping(
        "/by-uri/graph",
        produces = [
            "application/ld+json",
            "text/turtle",
            "application/rdf+xml",
            "application/n-triples",
            "application/n-quads",
        ],
    )
    @Operation(
        summary = "Get information model graph by URI",
        description =
            "Retrieve the RDF graph representation of a specific information model by its URI. " +
                "Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved information model graph",
                content = [
                    Content(mediaType = "application/ld+json"),
                    Content(mediaType = "text/turtle"),
                    Content(mediaType = "application/rdf+xml"),
                    Content(mediaType = "application/n-triples"),
                    Content(mediaType = "application/n-quads"),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Information model not found",
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format",
            ),
        ],
    )
    fun getInformationModelGraphByUri(
        @Parameter(description = "URI of the information model")
        @RequestParam uri: String,
        @Parameter(
            description =
                "Accept header for content negotiation: application/ld+json, text/turtle, " +
                    "application/rdf+xml, application/n-triples, application/n-quads",
            hidden = true,
        )
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        @Parameter(
            description = "RDF format style: 'pretty' (human-readable) or 'standard' (compact). Default: pretty",
            schema = Schema(type = "string", allowableValues = ["pretty", "standard"]),
            example = "pretty",
        )
        @RequestParam(name = "style", required = false, defaultValue = "pretty") style: String?,
        @Parameter(description = "Whether to expand URIs (clear namespace prefixes). Default: true")
        @RequestParam(name = "expandUris", required = false, defaultValue = "true") expandUris: Boolean?,
    ): ResponseEntity<Any> {
        val rdfFormatStyle =
            try {
                RdfFormatStyle.valueOf((style ?: "pretty").uppercase())
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid style parameter: $style, using default: pretty")
                RdfFormatStyle.PRETTY
            }

        return handleGraphRequestByUri(
            uri,
            ResourceType.INFORMATION_MODEL,
            acceptHeader,
            rdfFormatStyle,
            expandUris ?: true,
        )
    }
}
