package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService.RdfFormatStyle
import no.fdk.resourceservice.service.ResourceService
import no.fdk.resourceservice.service.RdfService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/v1/concepts")
@Tag(name = "Concepts", description = "API for managing FDK concepts")
class ConceptController(
    resourceService: ResourceService,
    rdfService: RdfService
) : BaseController(resourceService, rdfService) {


    @GetMapping("/{id}")
    @Operation(
        summary = "Get concept by ID",
        description = "Retrieve a specific concept by its unique identifier"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved concept"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Concept not found"
            )
        ]
    )
    fun getConcept(
        @Parameter(description = "Unique identifier of the concept")
        @PathVariable id: String
    ): ResponseEntity<Map<String, Any>> {
        return handleJsonResourceRequest(id, ResourceType.CONCEPT)
    }

    @GetMapping("/by-uri")
    @Operation(
        summary = "Get concept by URI",
        description = "Retrieve a specific concept by its URI"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved concept"
            ),
            ApiResponse(
                responseCode = "404",
                description = "Concept not found"
            )
        ]
    )
    fun getConceptByUri(
        @Parameter(description = "URI of the concept")
        @RequestParam uri: String
    ): ResponseEntity<Map<String, Any>> {
        return handleJsonResourceRequestByUri(uri, ResourceType.CONCEPT)
    }

    @GetMapping("/{id}/graph")
    @Operation(
        summary = "Get concept graph by ID",
        description = "Retrieve the RDF graph representation of a specific concept by its unique identifier. Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved concept graph",
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
                description = "Concept not found"
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format"
            )
        ]
    )
    fun getConceptGraph(
        @Parameter(description = "Unique identifier of the concept")
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
        
        return handleGraphRequest(id, ResourceType.CONCEPT, acceptHeader, rdfFormatStyle, expandUris ?: false)
    }

    @GetMapping("/by-uri/graph")
    @Operation(
        summary = "Get concept graph by URI",
        description = "Retrieve the RDF graph representation of a specific concept by its URI. Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved concept graph",
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
                description = "Concept not found"
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format"
            )
        ]
    )
    fun getConceptGraphByUri(
        @Parameter(description = "URI of the concept")
        @RequestParam uri: String,
        @Parameter(description = "Accept header for content negotiation")
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        @Parameter(description = "RDF format style: 'pretty' (with namespace prefixes, human-readable) or 'standard' (with namespace prefixes, compact)")
        @RequestParam(name = "style", required = false, defaultValue = "pretty") style: String?,
        @Parameter(description = "Whether to expand URIs (clear namespace prefixes, default: false)")
        @RequestParam(name = "expandUris", required = false, defaultValue = "false") expandUris: Boolean?
    ): ResponseEntity<Any> {
        val rdfFormatStyle = try {
            when (style?.lowercase()) {
                "pretty" -> RdfFormatStyle.PRETTY
                "standard" -> RdfFormatStyle.STANDARD
                else -> RdfFormatStyle.PRETTY
            }
        } catch (e: IllegalArgumentException) {
            logger.warn("Invalid style parameter: $style, using default: PRETTY")
            RdfFormatStyle.PRETTY
        }
        
        return handleGraphRequestByUri(uri, ResourceType.CONCEPT, acceptHeader, rdfFormatStyle, expandUris ?: false)
    }

}
