package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.parameters.RequestBody
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import no.fdk.resourceservice.config.UnionGraphFeatureConfig
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
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
@Tag(name = "Union Graphs", description = "API for ordering and retrieving union graphs")
class UnionGraphController(
    private val unionGraphService: UnionGraphService,
    private val rdfService: RdfService,
    private val unionGraphFeatureConfig: UnionGraphFeatureConfig,
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(UnionGraphController::class.java)

    /**
     * Creates a new union graph order.
     *
     * @param requestBody Optional request body with resource types to include.
     *                    If not provided or empty, all resource types will be included.
     * @return The created order with PENDING status.
     */
    @PostMapping
    @Operation(
        summary = "Order a union graph",
        description =
            "Create a new order for a union graph, or return an existing order if one with the same " +
                "configuration already exists. The graph will be built asynchronously in the background. " +
                "You can specify which resource types to include, or leave empty to include all types. " +
                "If an order with the same graph key already exists, it will be returned with HTTP 409 Conflict. " +
                "The response includes a Location header pointing to the order resource. " +
                "If a webhook URL is provided, it must use HTTPS protocol.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "201",
                description = "New order created successfully",
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
                    "An order with the same configuration already exists (any status). " +
                        "The existing order is returned in the response body.",
                content = [Content(mediaType = "application/json")],
            ),
        ],
    )
    fun createOrder(
        @RequestBody(required = false)
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

        val result =
            try {
                unionGraphService.createOrder(resourceTypes, updateTtlHours, webhookUrl)
            } catch (e: IllegalArgumentException) {
                // Handle validation errors (e.g., invalid webhook URL)
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
            )

        // Return 201 Created for new orders, 409 Conflict for existing ones
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
     * Gets all union graph orders (without graph data).
     *
     * Returns a list of all orders with their metadata (status, resource types, timestamps)
     * but excludes the actual graph data to keep the response lightweight.
     *
     * @return List of union graph orders (without graph data)
     */
    @GetMapping
    @Operation(
        summary = "List all union graph orders",
        description =
            "Retrieve a list of all union graph orders with their metadata. " +
                "The actual graph data is excluded to keep the response lightweight. " +
                "Use the individual order endpoints to retrieve the graph data.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Successfully retrieved list of orders",
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
                )
            }

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)
    }

    /**
     * Resets a union graph order to PENDING status for retry.
     *
     * This endpoint allows manually resetting a failed or stuck order
     * to PENDING so it can be processed again.
     *
     * @param id The order ID to reset.
     * @return The reset order with PENDING status.
     */
    @PostMapping("/{id}/reset")
    @Operation(
        summary = "Reset union graph order to PENDING",
        description =
            "Reset a union graph order to PENDING status for retry. " +
                "This clears error messages and releases any locks. " +
                "Useful for retrying failed orders or restarting stuck orders. " +
                "This endpoint must be explicitly enabled in configuration (app.union-graphs.reset-enabled).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Order reset to PENDING successfully",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "403",
                description = "Reset endpoint is disabled in configuration",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Order not found",
            ),
        ],
    )
    fun resetOrder(
        @Parameter(description = "Order ID")
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
            )

        return ResponseEntity
            .ok()
            .contentType(MediaType.APPLICATION_JSON)
            .body(response)
    }

    /**
     * Gets the status of a union graph order.
     *
     * @param id The order ID.
     * @return The order status.
     */
    @GetMapping("/{id}/status")
    @Operation(
        summary = "Get union graph order status",
        description = "Retrieve the current status of a union graph order.",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Order status retrieved successfully",
                content = [Content(mediaType = "application/json")],
            ),
            ApiResponse(
                responseCode = "404",
                description = "Order not found",
            ),
        ],
    )
    fun getStatus(
        @Parameter(description = "Order ID")
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
     * @param id The order ID.
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
                "The graph must be in COMPLETED status.",
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
                description = "Order not found or graph not yet built",
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
        @Parameter(description = "Order ID")
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
     * Deletes a union graph order.
     *
     * This endpoint permanently removes the order and its associated graph data.
     * Requires the delete endpoint to be enabled in configuration.
     *
     * @param id The order ID to delete.
     * @return No content on success, or 404 if order not found.
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "Delete union graph order",
        description =
            "Delete a union graph order. This permanently removes the order and its associated graph data. " +
                "Use with caution as this action cannot be undone. " +
                "This endpoint must be explicitly enabled in configuration (app.union-graphs.delete-enabled).",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "204",
                description = "Order deleted successfully",
            ),
            ApiResponse(
                responseCode = "403",
                description = "Delete endpoint is disabled in configuration",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Order not found",
            ),
        ],
    )
    fun deleteOrder(
        @Parameter(description = "Order ID")
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
     * Request DTO for creating a union graph order.
     */
    data class UnionGraphOrderRequest(
        val resourceTypes: List<String>? = null,
        /**
         * Time to live in hours for automatic graph updates.
         * 0 means never update automatically.
         * Otherwise, the graph will be automatically updated after this many hours.
         */
        val updateTtlHours: Int? = null,
        /**
         * Webhook URL to call when the order status changes.
         * The webhook will be called with a POST request containing the order status.
         * Must use HTTPS protocol.
         */
        val webhookUrl: String? = null,
    )

    /**
     * Response DTO for a union graph order.
     */
    data class UnionGraphOrderResponse(
        val id: String,
        val status: String,
        val resourceTypes: List<String>?,
        val updateTtlHours: Int,
        val webhookUrl: String?,
        val createdAt: String,
    )

    /**
     * Response DTO for union graph order status.
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
    )

    /**
     * Response DTO for union graph order summary (without graph data).
     * Used for listing all orders without loading the potentially large graph data.
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
    )
}
