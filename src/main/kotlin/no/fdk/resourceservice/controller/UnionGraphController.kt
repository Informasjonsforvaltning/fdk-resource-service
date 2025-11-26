package no.fdk.resourceservice.controller

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
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.service.RdfService
import no.fdk.resourceservice.service.RdfService.RdfFormatStyle
import no.fdk.resourceservice.service.UnionGraphService
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
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
                "For example, dataset filters can filter by isOpenData and isRelatedToTransportportal fields. " +
                "You can also enable automatic expansion of DataService graphs when datasets reference them " +
                "via distribution accessService URIs (expandDistributionAccessServices). " +
                "The updateTtlHours must be 0 (never update) or greater than 3. " +
                "If a union graph with the same configuration (resource types, update TTL, webhook URL, filters, " +
                "and expansion settings) already exists, it will be returned with HTTP 409 Conflict. " +
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
                                    "resourceTypes": ["DATASET"],
                                    "updateTtlHours": 24,
                                    "webhookUrl": "https://example.com/webhook",
                                    "resourceFilters": {
                                        "dataset": {
                                            "isOpenData": true,
                                            "isRelatedToTransportportal": false
                                        }
                                    },
                                    "expandDistributionAccessServices": true
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

        val resourceTypes =
            requestBody?.resourceTypes?.mapNotNull { typeName ->
                try {
                    ResourceType.valueOf(typeName.uppercase())
                } catch (e: IllegalArgumentException) {
                    logger.warn("Unknown resource type: {}", typeName)
                    null
                }
            }

        val updateTtlHours = requestBody?.updateTtlHours ?: 0
        val webhookUrl = requestBody?.webhookUrl
        val resourceFilters = requestBody?.toDomainFilters()
        val expandDistributionAccessServices = requestBody?.expandDistributionAccessServices ?: false

        // Validate updateTtlHours: must be 0 (never update) or > 3
        if (updateTtlHours != 0 && updateTtlHours <= 3) {
            logger.warn("Invalid updateTtlHours: {} (must be 0 or > 3)", updateTtlHours)
            return ResponseEntity.badRequest().build()
        }

        val result =
            try {
                unionGraphService.createOrder(resourceTypes, updateTtlHours, webhookUrl, resourceFilters, expandDistributionAccessServices)
            } catch (e: IllegalArgumentException) {
                // Handle validation errors (e.g., invalid webhook URL, invalid updateTtlHours)
                logger.warn("Invalid request: {}", e.message)
                return ResponseEntity.badRequest().build()
            }

        val order = result.order

        val response =
            UnionGraphOrderResponse(
                id = order.id,
                status = order.status.name,
                resourceTypes = order.resourceTypes,
                updateTtlHours = order.updateTtlHours,
                webhookUrl = order.webhookUrl,
                createdAt = order.createdAt.toString(),
                resourceFilters = toResponseFilters(order.resourceFilters),
                expandDistributionAccessServices = order.expandDistributionAccessServices,
            )

        // Return 201 Created for new union graphs, 409 Conflict for existing ones
        val httpStatus =
            if (result.isNew) {
                org.springframework.http.HttpStatus.CREATED // 201
            } else {
                org.springframework.http.HttpStatus.CONFLICT // 409
            }

        val responseBuilder =
            ResponseEntity
                .status(httpStatus)
                .contentType(MediaType.APPLICATION_JSON)
                .header("Location", "/v1/union-graphs/${order.id}")

        return responseBuilder.body(response)
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

        val response =
            orders.map { order ->
                UnionGraphOrderSummaryResponse(
                    id = order.id,
                    status = order.status.name,
                    resourceTypes = order.resourceTypes,
                    updateTtlHours = order.updateTtlHours,
                    webhookUrl = order.webhookUrl,
                    errorMessage = order.errorMessage,
                    createdAt = order.createdAt.toString(),
                    updatedAt = order.updatedAt.toString(),
                    processedAt = order.processedAt?.toString(),
                    resourceFilters = toResponseFilters(order.resourceFilters),
                    expandDistributionAccessServices = order.expandDistributionAccessServices,
                )
            }

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

        val response =
            UnionGraphOrderResponse(
                id = order.id,
                status = order.status.name,
                resourceTypes = order.resourceTypes,
                updateTtlHours = order.updateTtlHours,
                webhookUrl = order.webhookUrl,
                createdAt = order.createdAt.toString(),
                resourceFilters = toResponseFilters(order.resourceFilters),
                expandDistributionAccessServices = order.expandDistributionAccessServices,
            )

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)
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

        val response =
            UnionGraphOrderStatusResponse(
                id = order.id,
                status = order.status.name,
                resourceTypes = order.resourceTypes,
                updateTtlHours = order.updateTtlHours,
                webhookUrl = order.webhookUrl,
                errorMessage = order.errorMessage,
                createdAt = order.createdAt.toString(),
                updatedAt = order.updatedAt.toString(),
                processedAt = order.processedAt?.toString(),
                resourceFilters = toResponseFilters(order.resourceFilters),
                expandDistributionAccessServices = order.expandDistributionAccessServices,
            )

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)
    }

    /**
     * Gets the built union graph.
     *
     * Supports content negotiation for all RDF formats:
     * - application/ld+json (JSON-LD) - default
     * - text/turtle (Turtle)
     * - application/rdf+xml (RDF/XML)
     * - application/n-triples (N-Triples)
     * - application/n-quads (N-Quads)
     *
     * @param id The union graph ID.
     * @param acceptHeader The Accept header for content negotiation.
     * @param style Optional RDF format style (pretty or standard, defaults to pretty).
     * @param expandUris Whether to expand URIs (defaults to true).
     * @return The graph in the requested format.
     */
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
        summary = "Get union graph",
        description =
            "Retrieve the built union graph. " +
                "Supports content negotiation for multiple RDF formats (JSON-LD, Turtle, RDF/XML, N-Triples, N-Quads). " +
                "The graph must be in COMPLETED status. This endpoint is publicly accessible.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Graph retrieved successfully",
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
                description = "Union graph not found or graph not yet built",
            ),
            ApiResponse(
                responseCode = "400",
                description = "Graph is not yet completed",
            ),
            ApiResponse(
                responseCode = "500",
                description = "Failed to convert graph to requested format",
            ),
        ],
    )
    fun getGraph(
        @Parameter(description = "Union graph ID")
        @PathVariable id: String,
        @RequestHeader(HttpHeaders.ACCEPT, required = false) acceptHeader: String?,
        @Parameter(description = "RDF format style (pretty or standard)")
        @RequestParam(name = "style", required = false) style: String?,
        @Parameter(description = "Whether to expand URIs (default: true)")
        @RequestParam(name = "expandUris", required = false, defaultValue = "true") expandUris: Boolean?,
    ): ResponseEntity<Any> {
        logger.debug("Getting graph for order: {}, Accept: {}, style: {}, expandUris: {}", id, acceptHeader, style, expandUris)

        val order =
            unionGraphService.getOrder(id)
                ?: return ResponseEntity.notFound().build()

        if (order.status != UnionGraphOrder.GraphStatus.COMPLETED) {
            return ResponseEntity
                .badRequest()
                .body("Graph is not yet completed. Current status: ${order.status}")
        }

        val graphJsonLd =
            order.graphJsonLd
                ?: return ResponseEntity.notFound().build()

        val format = rdfService.getBestFormat(acceptHeader)
        val styleEnum =
            when (style?.lowercase()) {
                "standard" -> RdfFormatStyle.STANDARD
                else -> RdfFormatStyle.PRETTY
            }

        val convertedData =
            rdfService.convertFromJsonLd(
                graphJsonLd,
                format,
                styleEnum,
                expandUris ?: true,
                null, // No specific resource type for union graphs
            )

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

    /**
     * Request DTO for creating a union graph.
     */
    data class UnionGraphOrderRequest(
        /**
         * List of resource types to include in the union graph.
         * If null or empty, all resource types will be included.
         * Valid values: CONCEPT, DATASET, DATA_SERVICE, INFORMATION_MODEL, SERVICE, EVENT
         */
        val resourceTypes: List<String>? = null,
        /**
         * Time to live in hours for automatic graph updates.
         * 0 means never update automatically.
         * Otherwise, the graph will be automatically updated after this many hours.
         * Must be 0 or greater than 3.
         */
        val updateTtlHours: Int? = null,
        /**
         * Webhook URL to call when the union graph status changes.
         * The webhook will be called with a POST request containing the union graph status.
         * Must use HTTPS protocol.
         */
        val webhookUrl: String? = null,
        /**
         * Optional per-resource-type filters to apply when building the union graph.
         * Filters allow you to include only resources that match specific criteria.
         * For example, dataset filters can filter by isOpenData and isRelatedToTransportportal.
         * Filters are part of the union graph configuration, so union graphs with different filters are considered different.
         */
        val resourceFilters: ResourceFiltersRequest? = null,
        /**
         * If true, when building union graphs, datasets with distributions that reference
         * DataService URIs (via distribution[].accessService[].uri) will have those
         * DataService graphs automatically included in the union graph.
         *
         * This allows creating union graphs that include both datasets and their related
         * data services in a single graph, making it easier to query and navigate the
         * relationships between datasets and data services.
         *
         * Default: false
         */
        val expandDistributionAccessServices: Boolean? = null,
    ) {
        fun toDomainFilters(): UnionGraphResourceFilters? = resourceFilters?.toDomain()
    }

    /**
     * Request DTO for resource type-specific filters.
     * Each resource type can define its own filter structure.
     */
    data class ResourceFiltersRequest(
        /**
         * Filters for DATASET resource type.
         * Only datasets matching these criteria will be included in the union graph.
         */
        val dataset: DatasetFiltersRequest? = null,
    ) {
        fun toDomain(): UnionGraphResourceFilters? {
            val datasetFilters = dataset?.toDomain()
            return if (datasetFilters == null) {
                null
            } else {
                UnionGraphResourceFilters(dataset = datasetFilters).normalized()
            }
        }
    }

    /**
     * Request DTO for dataset-specific filters.
     * Filters datasets based on their metadata fields.
     */
    data class DatasetFiltersRequest(
        /**
         * Filter datasets by the isOpenData field.
         * If true, only open data datasets are included.
         * If false, only non-open data datasets are included.
         * If null, this filter is not applied.
         */
        val isOpenData: Boolean? = null,
        /**
         * Filter datasets by the isRelatedToTransportportal field.
         * If true, only datasets related to transport portal are included.
         * If false, only datasets not related to transport portal are included.
         * If null, this filter is not applied.
         */
        val isRelatedToTransportportal: Boolean? = null,
    ) {
        fun toDomain(): UnionGraphResourceFilters.DatasetFilters? =
            if (isOpenData == null && isRelatedToTransportportal == null) {
                null
            } else {
                UnionGraphResourceFilters.DatasetFilters(isOpenData, isRelatedToTransportportal)
            }
    }

    /**
     * Response DTO for a union graph.
     */
    data class UnionGraphOrderResponse(
        val id: String,
        val status: String,
        val resourceTypes: List<String>?,
        val updateTtlHours: Int,
        val webhookUrl: String?,
        val createdAt: String,
        /**
         * The resource filters that were applied when creating this union graph.
         * Null if no filters were specified.
         */
        val resourceFilters: ResourceFiltersResponse?,
        /**
         * Whether DataService graphs are automatically included when datasets reference them via distribution accessService.
         */
        val expandDistributionAccessServices: Boolean,
    )

    /**
     * Response DTO for union graph status.
     */
    data class UnionGraphOrderStatusResponse(
        val id: String,
        val status: String,
        val resourceTypes: List<String>?,
        val updateTtlHours: Int,
        val webhookUrl: String?,
        val errorMessage: String?,
        val createdAt: String,
        val updatedAt: String,
        val processedAt: String?,
        /**
         * The resource filters that were applied when creating this union graph.
         * Null if no filters were specified.
         */
        val resourceFilters: ResourceFiltersResponse?,
        /**
         * Whether DataService graphs are automatically included when datasets reference them via distribution accessService.
         */
        val expandDistributionAccessServices: Boolean,
    )

    /**
     * Response DTO for union graph summary (without graph data).
     * Used for listing all union graphs without loading the potentially large graph data.
     */
    data class UnionGraphOrderSummaryResponse(
        val id: String,
        val status: String,
        val resourceTypes: List<String>?,
        val updateTtlHours: Int,
        val webhookUrl: String?,
        val errorMessage: String?,
        val createdAt: String,
        val updatedAt: String,
        val processedAt: String?,
        /**
         * The resource filters that were applied when creating this union graph.
         * Null if no filters were specified.
         */
        val resourceFilters: ResourceFiltersResponse?,
        /**
         * Whether DataService graphs are automatically included when datasets reference them via distribution accessService.
         */
        val expandDistributionAccessServices: Boolean,
    )

    /**
     * Response DTO for resource type-specific filters.
     */
    data class ResourceFiltersResponse(
        /**
         * Dataset filters that were applied.
         * Present only if dataset filters were specified when creating the union graph.
         */
        val dataset: DatasetFiltersResponse?,
    )

    /**
     * Response DTO for dataset-specific filters.
     */
    data class DatasetFiltersResponse(
        /**
         * The isOpenData filter value that was applied.
         * Null if this filter was not specified.
         */
        val isOpenData: Boolean?,
        /**
         * The isRelatedToTransportportal filter value that was applied.
         * Null if this filter was not specified.
         */
        val isRelatedToTransportportal: Boolean?,
    )

    private fun toResponseFilters(filters: UnionGraphResourceFilters?): ResourceFiltersResponse? {
        val normalized = filters?.normalized() ?: return null
        val dataset = normalized.dataset ?: return null

        return ResourceFiltersResponse(
            dataset =
                DatasetFiltersResponse(
                    isOpenData = dataset.isOpenData,
                    isRelatedToTransportportal = dataset.isRelatedToTransportportal,
                ),
        )
    }
}
