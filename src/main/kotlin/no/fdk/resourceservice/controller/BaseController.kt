package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService
import no.fdk.resourceservice.service.RdfService.RdfFormatStyle
import no.fdk.resourceservice.service.ResourceService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity

/**
 * Base controller providing common functionality for all resource endpoints.
 *
 * This abstract class provides methods for handling common resource operations including:
 * - JSON resource retrieval by ID and URI
 * - RDF graph requests with content negotiation
 * - Consistent error handling and response formatting
 */
abstract class BaseController(protected val resourceService: ResourceService, protected val rdfService: RdfService) {
    protected val logger = LoggerFactory.getLogger(this::class.java)

    /**
     * Handles a JSON resource request by ID.
     *
     * @param id The resource ID
     * @param resourceType The type of resource
     * @return ResponseEntity with the JSON resource data
     */
    protected fun handleJsonResourceRequest(id: String, resourceType: ResourceType): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting ${resourceType.name.lowercase()} with id: $id")

        val resource = resourceService.getResourceJson(id, resourceType)
        return if (resource != null) {
            ResponseEntity
                .ok()
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
    protected fun handleJsonResourceRequestByUri(uri: String, resourceType: ResourceType): ResponseEntity<Map<String, Any>> {
        logger.debug("Getting ${resourceType.name.lowercase()} with uri: $uri")

        val resource = resourceService.getResourceJsonByUri(uri, resourceType)
        return if (resource != null) {
            ResponseEntity
                .ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(resource)
        } else {
            ResponseEntity.notFound().build()
        }
    }

    /**
     * Handles a JSON resource list request by IDs.
     *
     * @param ids The resource IDs
     * @param resourceType The type of resource
     * @return ResponseEntity with the list of JSON resource data
     */
    protected fun handleJsonResourceListRequest(ids: List<String>, resourceType: ResourceType): ResponseEntity<List<Map<String, Any>>> {
        logger.debug("Retrieve list of {} for ids: {}", resourceType.name.lowercase(), ids)

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(resourceService.getResourceJsonListById(ids, resourceType))
    }

    /**
     * Handles a graph request by ID with content negotiation.
     *
     * @param id The resource ID
     * @param resourceType The type of resource
     * @param acceptHeader The Accept header for content negotiation
     * @param includeCatalog If true, merges the stored catalog graph (dcat:Catalog, dcat:CatalogRecord,
     *   skos:Collection) into the returned graph
     * @return ResponseEntity with the graph data in the requested format
     */
    protected fun handleGraphRequest(
        id: String,
        resourceType: ResourceType,
        acceptHeader: String?,
        includeCatalog: Boolean = false,
    ): ResponseEntity<Any> {
        logger.debug(
            "Getting graph for {} with id: {}, Accept: {}, includeCatalog: {}",
            resourceType.name.lowercase(),
            id,
            acceptHeader,
            includeCatalog,
        )

        val entity = resourceService.getResourceEntity(id, resourceType) ?: return ResponseEntity.notFound().build()
        return graphResponse(entity, resourceType, acceptHeader, includeCatalog)
    }

    /**
     * Handles a graph request by URI with content negotiation.
     *
     * @param uri The resource URI
     * @param resourceType The type of resource
     * @param acceptHeader The Accept header for content negotiation
     * @param includeCatalog If true, merges the stored catalog graph into the returned graph
     * @return ResponseEntity with the graph data in the requested format
     */
    protected fun handleGraphRequestByUri(
        uri: String,
        resourceType: ResourceType,
        acceptHeader: String?,
        includeCatalog: Boolean = false,
    ): ResponseEntity<Any> {
        logger.debug(
            "Getting graph for {} with uri: {}, Accept: {}, includeCatalog: {}",
            resourceType.name.lowercase(),
            uri,
            acceptHeader,
            includeCatalog,
        )

        val entity = resourceService.getResourceEntityByUri(uri)
        if (entity == null || entity.resourceType != resourceType.name) {
            return ResponseEntity.notFound().build()
        }
        return graphResponse(entity, resourceType, acceptHeader, includeCatalog)
    }

    /**
     * Converts an entity's resource graph (optionally merged with its catalog graph) to the
     * format negotiated from the Accept header and wraps it in a response.
     *
     * Returns 404 if the entity has no resource graph data, 500 if conversion fails.
     * When [includeCatalog] is true but the entity has no catalog graph, the resource graph
     * is returned as-is.
     */
    protected fun graphResponse(
        entity: ResourceEntity,
        resourceType: ResourceType?,
        acceptHeader: String?,
        includeCatalog: Boolean,
    ): ResponseEntity<Any> {
        val resourceGraphData = entity.resourceGraphData ?: return ResponseEntity.notFound().build()
        val format = rdfService.getBestFormat(acceptHeader)
        val storedFormat = entity.resourceGraphFormat ?: "TURTLE"
        val catalogGraphData = entity.catalogGraphData

        val convertedData =
            if (includeCatalog && !catalogGraphData.isNullOrBlank()) {
                rdfService.convertMergedFromFormats(
                    listOf(
                        resourceGraphData to storedFormat,
                        catalogGraphData to (entity.catalogGraphFormat ?: "TURTLE"),
                    ),
                    format,
                    RdfFormatStyle.PRETTY,
                    expandUris = true,
                    resourceType,
                )
            } else {
                rdfService.convertFromFormat(
                    resourceGraphData,
                    storedFormat,
                    format,
                    RdfFormatStyle.PRETTY,
                    expandUris = true,
                    resourceType,
                )
            }

        return if (convertedData != null) {
            ResponseEntity
                .ok()
                .contentType(rdfService.getContentType(format))
                .body(convertedData)
        } else {
            ResponseEntity
                .internalServerError()
                .body("Failed to convert graph to requested format")
        }
    }
}
