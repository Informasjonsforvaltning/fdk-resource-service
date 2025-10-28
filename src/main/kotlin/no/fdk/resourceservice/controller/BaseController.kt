package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.ResourceService
import no.fdk.resourceservice.service.RdfService
import no.fdk.resourceservice.service.RdfService.RdfFormatStyle
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestHeader

/**
 * Base controller providing common functionality for all resource endpoints.
 * 
 * This abstract class provides methods for handling common resource operations including:
 * - JSON resource retrieval by ID and URI
 * - RDF graph requests with content negotiation
 * - Consistent error handling and response formatting
 */
abstract class BaseController(
    protected val resourceService: ResourceService,
    protected val rdfService: RdfService
) {
    protected val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Handles a JSON resource request by ID.
     * 
     * @param id The resource ID
     * @param resourceType The type of resource
     * @return ResponseEntity with the JSON resource data
     */
    protected fun handleJsonResourceRequest(
        id: String,
        resourceType: ResourceType
    ): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting ${resourceType.name.lowercase()} with id: $id")
        
        val resource = resourceService.getResourceJson(id, resourceType)
        return if (resource != null) {
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Handles a JSON resource request by URI.
     * 
     * @param uri The resource URI
     * @param resourceType The type of resource
     * @return ResponseEntity with the JSON resource data
     */
    protected fun handleJsonResourceRequestByUri(
        uri: String,
        resourceType: ResourceType
    ): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting ${resourceType.name.lowercase()} with uri: $uri")
        
        val resource = resourceService.getResourceJsonByUri(uri, resourceType)
        return if (resource != null) {
            ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Handles a graph request by ID with content negotiation.
     * 
     * @param id The resource ID
     * @param resourceType The type of resource
     * @param acceptHeader The Accept header for content negotiation
     * @param style The RDF format style (optional, defaults to PRETTY)
     * @param expandUris Whether to expand URIs (clear namespace prefixes, optional, defaults to true)
     * @return ResponseEntity with the graph data in the requested format
     */
    protected fun handleGraphRequest(
        id: String,
        resourceType: ResourceType,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        style: RdfFormatStyle = RdfFormatStyle.PRETTY,
        expandUris: Boolean = true
    ): ResponseEntity<Any> {
        logger.debug(
            "Getting graph for {} with id: {}, Accept: {}, style: {}, expandUris: {}",
            resourceType.name.lowercase(),
            id,
            acceptHeader,
            style,
            expandUris
        )
        
        val jsonLdData = resourceService.getResourceJsonLd(id, resourceType)
        return if (jsonLdData != null) {
            val format = rdfService.getBestFormat(acceptHeader)
            val convertedData = rdfService.convertFromJsonLd(
                jsonLdData,
                format,
                style,
                expandUris,
                resourceType
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

    /**
     * Handles a graph request by URI with content negotiation.
     * 
     * @param uri The resource URI
     * @param resourceType The type of resource
     * @param acceptHeader The Accept header for content negotiation
     * @param style The RDF format style (optional, defaults to PRETTY)
     * @param expandUris Whether to expand URIs (clear namespace prefixes, optional, defaults to true)
     * @return ResponseEntity with the graph data in the requested format
     */
    protected fun handleGraphRequestByUri(
        uri: String,
        resourceType: ResourceType,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        style: RdfFormatStyle = RdfFormatStyle.PRETTY,
        expandUris: Boolean = true
    ): ResponseEntity<Any> {
        logger.debug("Getting graph for ${resourceType.name.lowercase()} with uri: $uri, Accept: $acceptHeader, style: $style, expandUris: $expandUris")
        
        val jsonLdData = resourceService.getResourceJsonLdByUri(uri, resourceType)
        return if (jsonLdData != null) {
            val format = rdfService.getBestFormat(acceptHeader)
            val convertedData = rdfService.convertFromJsonLd(
                jsonLdData,
                format,
                style,
                expandUris,
                resourceType
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
