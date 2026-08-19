package no.fdk.resourceservice.controller

import com.fasterxml.jackson.databind.ObjectMapper
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.security.SecurityRequirement
import io.swagger.v3.oas.annotations.tags.Tag
import no.fdk.resourceservice.config.UnionGraphFeatureConfig
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.service.RdfService
import no.fdk.resourceservice.service.UnionGraphService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

/**
 * Controller for union graph operations.
 *
 * Union graphs are built from multiple resource graphs by combining them.
 * The building process happens asynchronously in the background.
 */
@RestController
@RequestMapping("/v1/union-graphs")
@Tag(name = "Union Graphs", description = "API for creating and retrieving union graphs")
class UnionGraphController(
    private val unionGraphService: UnionGraphService,
    private val rdfService: RdfService,
    private val unionGraphFeatureConfig: UnionGraphFeatureConfig,
    private val objectMapper: ObjectMapper,
    private val requestHandler: UnionGraphRequestHandler,
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(UnionGraphController::class.java)

    /**
     * Creates a new union graph.
     *
     * @param requestBody Optional request body with resource types to include and optional filters.
     *                    If resource types are not provided or empty, all resource types will be included.
     *                    Resource filters allow filtering resources by type-specific criteria (e.g., dataset filters).
     * @return The created union graph with PENDING status.
     */
    @PostMapping
    @Operation(
        summary = "Create a union graph",
        description =
        "Create a new union graph, or return an existing one if one with the same " +
            "configuration already exists. The graph will be built asynchronously in the background. " +
            "You can specify which resource types to include, or leave empty to include all types. " +
            "Optionally, you can provide resource filters to filter resources by type-specific criteria. " +
            "For example, dataset filters can filter by isOpenData, isRelatedToTransportportal, and isDatasetSeries fields. " +
            "You can also filter by specific resource IDs (fdkId) using resourceIds, or by resource URIs using resourceUris. " +
            "If both resourceIds and resourceUris are provided, resources matching either filter will be included. " +
            "You can also enable automatic expansion of DataService graphs when datasets reference them " +
            "via distribution accessService URIs (expandDistributionAccessServices). " +
            "The updateTtlHours must be 0 (never update) or at least 24. " +
            "If a union graph with the same configuration (resource types, update TTL, webhook URL, filters, " +
            "resource IDs, resource URIs, and expansion settings) already exists, it will be returned with HTTP 409 Conflict. " +
            "The response includes a Location header pointing to the union graph resource. " +
            "If a webhook URL is provided, it must use HTTPS protocol.",
        security = [SecurityRequirement(name = "ApiKeyAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "New union graph created successfully",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request (e.g., webhook URL not using HTTPS)",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "409",
                description =
                "A union graph with the same configuration already exists (any status). " +
                    "The existing union graph is returned in the response body.",
                content = [Content(mediaType = "application/json")],
            ),
        ],
    )
    fun createOrder(
        @RequestBody(
            required = false,
            description = "Union graph configuration",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UnionGraphOrderRequest::class),
                    examples = [
                        io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Basic example",
                            value =
                            """
                                {
                                    "name": "My Union Graph",
                                    "resourceTypes": ["DATASET", "DATA_SERVICE"],
                                    "updateTtlHours": 24,
                                    "webhookUrl": "https://example.com/webhook"
                                }
                                """,
                        ),
                        io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "With filters and expansion",
                            value =
                            """
                                {
                                    "name": "Open Data Datasets with Data Services",
                                    "description": "Union graph containing only open data datasets with expanded data services",
                                    "resourceTypes": ["DATASET"],
                                    "updateTtlHours": 24,
                                    "webhookUrl": "https://example.com/webhook",
                                    "resourceFilters": {
                                        "dataset": {
                                            "isOpenData": true,
                                            "isRelatedToTransportportal": false,
                                            "isDatasetSeries": false
                                        }
                                    },
                                    "expandDistributionAccessServices": true
                                }
                                """,
                        ),
                        io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "With resource ID and URI filters",
                            value =
                            """
                                {
                                    "name": "Filtered Union Graph",
                                    "description": "Union graph with specific resources",
                                    "resourceTypes": ["DATASET", "DATA_SERVICE"],
                                    "updateTtlHours": 24,
                                    "resourceIds": ["resource-id-1", "resource-id-2"],
                                    "resourceUris": ["https://example.com/dataset/1", "https://example.com/dataset/2"]
                                }
                                """,
                        ),
                        io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "DatasetSeries only",
                            value =
                            """
                                {
                                    "name": "Dataset Series Union Graph",
                                    "description": "Union graph containing only DatasetSeries resources",
                                    "resourceTypes": ["DATASET"],
                                    "updateTtlHours": 48,
                                    "resourceFilters": {
                                        "dataset": {
                                            "isDatasetSeries": true
                                        }
                                    }
                                }
                                """,
                        ),
                        io.swagger.v3.oas.annotations.media.ExampleObject(
                            name = "Without Catalog resources",
                            value =
                            """
                                {
                                    "name": "Union Graph Without Catalogs",
                                    "description": "Union graph excluding Catalog and CatalogRecord resources",
                                    "resourceTypes": ["DATASET", "CONCEPT"],
                                    "updateTtlHours": 24,
                                    "includeCatalog": false
                                }
                                """,
                        ),
                    ],
                ),
            ],
        )
        @org.springframework.web.bind.annotation.RequestBody(required = false)
        requestBody: UnionGraphOrderRequest?,
    ): ResponseEntity<UnionGraphOrderResponse> {
        logger.info(
            "Creating union graph order with resource types: {}, updateTtlHours: {}",
            requestBody?.resourceTypes,
            requestBody?.updateTtlHours,
        )

        val parsed =
            requestHandler.parseCreateRequest(requestBody)
                ?: return ResponseEntity.badRequest().build()

        val result =
            try {
                unionGraphService.createOrder(
                    parsed.resourceTypes,
                    parsed.updateTtlHours,
                    parsed.webhookUrl,
                    parsed.resourceFilters,
                    parsed.expandDistributionAccessServices,
                    parsed.name,
                    parsed.description,
                    parsed.resourceIds,
                    parsed.resourceUris,
                    parsed.includeCatalog,
                )
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid request: {}", e.message)
                return ResponseEntity.badRequest().build()
            }

        return requestHandler.createdOrConflictResponse(result.order, result.isNew)
    }

    /**
     * Updates an existing union graph.
     *
     * This endpoint allows updating various fields of a union graph order.
     * If fields that affect the graph content are changed (resourceTypes, resourceFilters,
     * resourceIds, resourceUris, expandDistributionAccessServices, includeCatalog), the order will be
     * reset to PENDING status to trigger a rebuild with the new configuration.
     *
     * Safe fields that don't require a rebuild (updateTtlHours, webhookUrl, name, description) can be
     * updated without affecting the graph status.
     *
     * @param id The union graph ID to update
     * @param requestBody Request body with fields to update
     * @return The updated union graph
     */
    @PutMapping("/{id}")
    @Operation(
        summary = "Update a union graph",
        description =
        "Update an existing union graph order. " +
            "You can update any field of the union graph configuration. " +
            "If fields that affect the graph content are changed (resourceTypes, resourceFilters, " +
            "resourceIds, resourceUris, expandDistributionAccessServices, includeCatalog), the order will be " +
            "reset to PENDING status to trigger a rebuild with the new configuration. " +
            "Safe fields that don't require a rebuild (updateTtlHours, webhookUrl, name, description) can be " +
            "updated without affecting the graph status. " +
            "The updateTtlHours must be 0 (never update) or at least 24. " +
            "If a webhook URL is provided, it must use HTTPS protocol. " +
            "To remove a webhook, set webhookUrl to an empty string.",
        security = [SecurityRequirement(name = "ApiKeyAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Union graph updated successfully",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Invalid request (e.g., webhook URL not using HTTPS, invalid updateTtlHours)",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Union graph not found",
            ),
        ],
    )
    fun updateOrder(
        @Parameter(description = "Union graph ID")
        @PathVariable id: String,
        @RequestBody(
            required = false,
            description = "Union graph update configuration",
            content = [
                Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = UnionGraphOrderRequest::class),
                ),
            ],
        )
        @org.springframework.web.bind.annotation.RequestBody(required = false)
        rawBody: Map<String, Any?>?,
    ): ResponseEntity<UnionGraphOrderResponse> {
        logger.info("Updating union graph order: {}", id)

        val request =
            try {
                if (rawBody != null) {
                    objectMapper.convertValue(rawBody, UnionGraphOrderRequest::class.java)
                } else {
                    UnionGraphOrderRequest()
                }
            } catch (e: Exception) {
                logger.warn("Could not parse JSON body: {}", e.message)
                return ResponseEntity.badRequest().build()
            }

        val updatedOrder =
            try {
                unionGraphService.updateOrder(
                    id = id,
                    fields = requestHandler.buildUpdateFields(rawBody, request),
                )
            } catch (e: IllegalArgumentException) {
                logger.warn("Invalid request: {}", e.message)
                return ResponseEntity.badRequest().build()
            }

        if (updatedOrder == null) {
            logger.warn("Order {} not found for update", id)
            return ResponseEntity.notFound().build()
        }

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestHandler.toOrderResponse(updatedOrder))
    }

    /**
     * Gets all union graphs (without graph data).
     *
     * Returns a list of all union graphs with their metadata (status, resource types, timestamps)
     * but excludes the actual graph data to keep the response lightweight.
     *
     * @return List of union graphs (without graph data)
     */
    @GetMapping
    @Operation(
        summary = "List all union graphs",
        description =
        "Retrieve a list of all union graphs with their metadata. " +
            "The actual graph data is excluded to keep the response lightweight. " +
            "Use the individual graph endpoints to retrieve the graph data.",
        security = [SecurityRequirement(name = "ApiKeyAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved list of union graphs",
                content = [Content(mediaType = "application/json")],
            ),
        ],
    )
    fun getAllOrders(): ResponseEntity<List<UnionGraphOrderSummaryResponse>> {
        logger.debug("Getting all union graph orders")

        val orders = unionGraphService.getAllOrders()

        val response = orders.map { requestHandler.toSummaryResponse(it) }

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)
    }

    /**
     * Resets a union graph to PENDING status for retry.
     *
     * This endpoint allows manually resetting a failed or stuck union graph
     * to PENDING so it can be processed again.
     *
     * @param id The union graph ID to reset.
     * @return The reset union graph with PENDING status.
     */
    @PostMapping("/{id}/reset")
    @Operation(
        summary = "Reset union graph to PENDING",
        description =
        "Reset a union graph to PENDING status for retry. " +
            "This clears error messages and releases any locks. " +
            "Useful for retrying failed union graphs or restarting stuck ones. " +
            "This endpoint must be explicitly enabled in configuration (app.union-graphs.reset-enabled).",
        security = [SecurityRequirement(name = "ApiKeyAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Union graph reset to PENDING successfully",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Reset endpoint is disabled in configuration",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Union graph not found",
            ),
        ],
    )
    fun resetOrder(
        @Parameter(description = "Union graph ID")
        @PathVariable id: String,
    ): ResponseEntity<UnionGraphOrderResponse> {
        if (!unionGraphFeatureConfig.resetEnabled) {
            logger.warn("Reset endpoint is disabled. Attempt to reset order {} was rejected", id)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        logger.info("Resetting order {} to PENDING", id)

        val order =
            unionGraphService.resetOrderToPending(id)
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestHandler.toOrderResponse(order))
    }

    /**
     * Gets the status of a union graph.
     *
     * @param id The union graph ID.
     * @return The union graph status.
     */
    @GetMapping("/{id}/status")
    @Operation(
        summary = "Get union graph status",
        description = "Retrieve the current status of a union graph.",
        security = [SecurityRequirement(name = "ApiKeyAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Union graph status retrieved successfully",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Union graph not found",
            ),
        ],
    )
    fun getStatus(
        @Parameter(description = "Union graph ID")
        @PathVariable id: String,
    ): ResponseEntity<UnionGraphOrderStatusResponse> {
        logger.debug("Getting status for order: {}", id)

        val order =
            unionGraphService.getOrder(id)
                ?: return ResponseEntity.notFound().build()

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestHandler.toStatusResponse(order))
    }

    /**
     * Gets minimal information about all available union graphs (with graph data).
     *
     * This endpoint is publicly accessible and returns only basic information
     * about union graphs that have graph data available. This includes graphs that
     * are currently COMPLETED, as well as graphs that were previously completed
     * but are now being updated (PROCESSING status).
     *
     * @return List of available union graphs with minimal information.
     */
    @GetMapping("/available")
    @Operation(
        summary = "List available union graphs",
        description =
        "Retrieve a list of all union graphs that have graph data available. " +
            "This includes graphs that are currently COMPLETED, as well as graphs " +
            "that were previously completed but are now being updated. " +
            "This endpoint is publicly accessible and returns only minimal information " +
            "(id, name, description, resource types, and creation date). " +
            "Use this endpoint to discover available union graphs without authentication.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved list of available union graphs",
                content = [Content(mediaType = "application/json")],
            ),
        ],
    )
    fun getAvailableOrders(): ResponseEntity<List<UnionGraphMinimalInfoResponse>> {
        logger.debug("Getting all available union graph orders")

        val orders = unionGraphService.getAvailableOrders()

        val response =
            orders.map { order ->
                requestHandler.toMinimalInfoResponse(order, unionGraphService.getResourceCount(order.id))
            }

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)
    }

    /**
     * Gets minimal information about a specific union graph.
     *
     * This endpoint is publicly accessible and returns only basic information
     * about the union graph if it has graph data available.
     *
     * @param id The union graph ID.
     * @return Minimal information about the union graph, or 404 if not found or not available.
     */
    @GetMapping("/{id}/info")
    @Operation(
        summary = "Get union graph minimal information",
        description =
        "Retrieve minimal information about a specific union graph. " +
            "This endpoint is publicly accessible and returns only basic information " +
            "(id, name, description, resource types, and creation date). " +
            "Returns 404 if the union graph is not found or does not have graph data available.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved union graph information",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Union graph not found or graph not yet available",
            ),
        ],
    )
    fun getOrderInfo(
        @Parameter(description = "Union graph ID")
        @PathVariable id: String,
    ): ResponseEntity<UnionGraphMinimalInfoResponse> {
        logger.debug("Getting minimal info for order: {}", id)

        val order =
            unionGraphService.getOrder(id)
                ?: return ResponseEntity.notFound().build()

        // Check if order is available (COMPLETED or PROCESSING with snapshots)
        if (order.status != UnionGraphOrder.GraphStatus.COMPLETED && order.status != UnionGraphOrder.GraphStatus.PROCESSING) {
            return ResponseEntity.notFound().build()
        }

        val count = unionGraphService.getResourceCount(id)

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(requestHandler.toMinimalInfoResponse(order, count))
    }

    /**
     * Deletes a union graph.
     *
     * This endpoint permanently removes the union graph and its associated graph data.
     * Requires the delete endpoint to be enabled in configuration.
     *
     * @param id The union graph ID to delete.
     * @return No content on success, or 404 if union graph not found.
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete union graph",
        description =
        "Delete a union graph. This permanently removes the union graph and its associated graph data. " +
            "Use with caution as this action cannot be undone. " +
            "This endpoint must be explicitly enabled in configuration (app.union-graphs.delete-enabled).",
        security = [SecurityRequirement(name = "ApiKeyAuth")],
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Union graph deleted successfully",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Delete endpoint is disabled in configuration",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Union graph not found",
            ),
        ],
    )
    fun deleteOrder(
        @Parameter(description = "Union graph ID")
        @PathVariable id: String,
    ): ResponseEntity<Void> {
        if (!unionGraphFeatureConfig.deleteEnabled) {
            logger.warn("Delete endpoint is disabled. Attempt to delete order {} was rejected", id)
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

        logger.info("Deleting union graph order: {}", id)

        val deleted = unionGraphService.deleteOrder(id)
        return if (deleted) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.notFound().build()
        }
    }
}
