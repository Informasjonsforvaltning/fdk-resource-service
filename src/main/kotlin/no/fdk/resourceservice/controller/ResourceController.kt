package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import no.fdk.resourceservice.service.RdfService.RdfFormatStyle
import no.fdk.resourceservice.service.ResourceService
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

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
    rdfService: RdfService,
) : BaseController(resourceService, rdfService) {
    @GetMapping("/by-uri")
    @Operation(
        summary = "Get resource by URI",
        description = "Retrieve a specific resource by its URI across all resource types (datasets, concepts, services, etc.)",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved resource",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Resource not found",
            ),
        ],
    )
    fun getResourceByUri(
        @Parameter(description = "URI of the resource")
        @RequestParam uri: String,
    ): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting resource with uri: {}", uri)

        val resource = resourceService.getResourceJsonByUri(uri)
        return if (resource != null) {
            ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
        } else {
            ResponseEntity.notFound().build()
        }
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
        summary = "Get resource graph by URI",
        description =
            "Retrieve the RDF graph representation of a specific resource by its URI across all resource types. " +
                "Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads).",
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
                    Content(mediaType = "application/n-quads"),
                ],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Resource not found",
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format",
            ),
        ],
    )
    fun getResourceGraphByUri(
        @Parameter(description = "URI of the resource")
        @RequestParam uri: String,
        @Parameter(
            description =
                "Accept header for content negotiation: application/ld+json, text/turtle, " +
                    "application/rdf+xml, application/n-triples, application/n-quads",
            hidden = true,
        )
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
    ): ResponseEntity<Any> {
        logger.debug("Getting resource graph with uri: {}, Accept: {}", uri, acceptHeader)

        val entity = resourceService.getResourceEntityByUri(uri)
        return if (entity != null && entity.resourceGraphData != null) {
            val format = rdfService.getBestFormat(acceptHeader)

            // Convert string resource type to enum
            val resourceType =
                try {
                    ResourceType.valueOf(entity.resourceType)
                } catch (e: IllegalArgumentException) {
                    logger.warn("Unknown resource type: {}, using common prefixes", entity.resourceType)
                    null
                }

            // Get the stored format, defaulting to TURTLE if not specified
            val storedFormat = entity.resourceGraphFormat ?: "TURTLE"

            val convertedData =
                rdfService.convertFromFormat(
                    entity.resourceGraphData,
                    storedFormat,
                    format,
                    RdfFormatStyle.PRETTY,
                    expandUris = true,
                    resourceType, // Use resource-specific prefixes based on the resource type
                )

            if (convertedData != null) {
                ResponseEntity
                    .ok()
                    .contentType(rdfService.getContentType(format))
                    .body(convertedData)
            } else {
                ResponseEntity
                    .internalServerError()
                    .body("Failed to convert graph to requested format")
            }
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
