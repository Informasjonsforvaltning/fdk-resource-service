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

/**
 * Controller for general resource operations across all resource types.
 * 
 * This controller provides endpoints for searching resources by URI across
 * all resource types (datasets, concepts, services, etc.) without needing
 * to know the specific resource type beforehand.
 */
@RestController
@RequestMapping("/v1/resources")
@Tag(name = "Resources", description = "API for retrieving resources across all types")
class ResourceController(
    resourceService: ResourceService,
    rdfService: RdfService
) : BaseController(resourceService, rdfService) {

    @GetMapping("/by-uri")
    @Operation(
        summary = "Get resource by URI",
        description = "Retrieve a specific resource by its URI across all resource types (datasets, concepts, services, etc.)"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved resource",
                content = [Content(mediaType = "application/json")]
            ),
            ApiResponse(
                responseCode = "404",
                description = "Resource not found"
            )
        ]
    )
    fun getResourceByUri(
        @Parameter(description = "URI of the resource")
        @RequestParam uri: String
    ): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting resource with uri: {}", uri)
        
        val resource = resourceService.getResourceJsonByUri(uri)
        return if (resource != null) {
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    @GetMapping("/by-uri/graph", produces = [
        "application/ld+json",
        "text/turtle",
        "application/rdf+xml",
        "application/n-triples",
        "application/n-quads"
    ])
    @Operation(
        summary = "Get resource graph by URI",
        description = "Retrieve the RDF graph representation of a specific resource by its URI across all resource types. Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads)."
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved resource graph",
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
                description = "Resource not found"
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format"
            )
        ]
    )
    fun getResourceGraphByUri(
        @Parameter(description = "URI of the resource")
        @RequestParam uri: String,
        @Parameter(description = "Accept header for content negotiation: application/ld+json, text/turtle, application/rdf+xml, application/n-triples, application/n-quads", hidden = true)
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        @Parameter(description = "RDF format style: 'pretty' (human-readable) or 'standard' (compact). Default: pretty", schema = Schema(type = "string", allowableValues = ["pretty", "standard"]), example = "pretty")
        @RequestParam(name = "style", required = false, defaultValue = "pretty") style: String?,
        @Parameter(description = "Whether to expand URIs (clear namespace prefixes). Default: true")
        @RequestParam(name = "expandUris", required = false, defaultValue = "true") expandUris: Boolean?
    ): ResponseEntity<Any> {
        logger.debug("Getting resource graph with uri: {}, Accept: {}, style: {}, expandUris: {}", uri, acceptHeader, style, expandUris)
        
        val entity = resourceService.getResourceEntityByUri(uri)
        return if (entity != null && entity.resourceJsonLd != null) {
            val format = rdfService.getBestFormat(acceptHeader)
            val styleEnum = when (style?.lowercase()) {
                "standard" -> RdfFormatStyle.STANDARD
                else -> RdfFormatStyle.PRETTY
            }
            
            // Convert string resource type to enum
            val resourceType = try {
                ResourceType.valueOf(entity.resourceType)
            } catch (e: IllegalArgumentException) {
                logger.warn("Unknown resource type: {}, using common prefixes", entity.resourceType)
                null
            }
            
            val convertedData = rdfService.convertFromJsonLd(
                entity.resourceJsonLd,
                format,
                styleEnum,
                expandUris ?: true,
                resourceType // Use resource-specific prefixes based on the resource type
            )
            
            if (convertedData != null) {
                ResponseEntity.ok()
                    .contentType(rdfService.getContentType(format))
                    .body(convertedData)
            } else {
                ResponseEntity.internalServerError()
                    .body("Failed to convert graph to requested format")
            }
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
