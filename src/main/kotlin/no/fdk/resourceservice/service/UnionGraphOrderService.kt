package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import no.fdk.resourceservice.repository.UnionGraphResourceSnapshotRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

/**
 * Handles CRUD and lifecycle operations for union graph orders: creation, retrieval,
 * update, reset to pending, and deletion.
 *
 * Note: [UnionGraphService.CreateOrderResult] and [UnionGraphService.UpdateFields] are used as the
 * parameter/return types here (rather than being duplicated on this class) so that
 * [UnionGraphService] can remain the single stable API surface for callers, without this service
 * depending on [UnionGraphService] itself.
 */
@Service
@Transactional
class UnionGraphOrderService(
    private val unionGraphOrderRepository: UnionGraphOrderRepository,
    private val unionGraphResourceSnapshotRepository: UnionGraphResourceSnapshotRepository,
    private val objectMapper: ObjectMapper,
    private val webhookService: WebhookService,
    private val metricsService: UnionGraphMetricsService,
) {
    private val logger = LoggerFactory.getLogger(UnionGraphOrderService::class.java)

    /**
     * Creates a new union graph, or returns an existing one if one already exists.
     *
     * This method prevents duplicate graph building by checking if a union graph
     * with the same configuration (resource types, update TTL, webhook URL, and filters)
     * already exists (any status). If found, the existing union graph is returned.
     * Otherwise, a new union graph is created.
     *
     * @param resourceTypes Optional list of resource types to include. If null or empty, all types are included.
     * @param updateTtlHours Time to live in hours for automatic updates. 0 means never update automatically. Must be 0 or >= 24.
     * @param webhookUrl Optional webhook URL to call when union graph status changes. Must be HTTPS if provided.
     * @param resourceFilters Optional per-resource-type filters to apply when building the graph.
     *                        For example, dataset filters can filter by isOpenData, isRelatedToTransportportal, and isDatasetSeries.
     *                        Filters are part of the union graph configuration, so union graphs with different filters
     *                        are considered different union graphs.
     * @param expandDistributionAccessServices If true, datasets with distributions that reference DataService URIs
     *                                          will have those DataService graphs automatically included in the union graph.
     *                                          This allows creating union graphs that include both datasets and their related
     *                                          data services in a single graph.
     * @param format The RDF format to use for the graph data (default: TURTLE).
     * @param name Human-readable name for the union graph (required).
     * @param description Optional human-readable description of the union graph.
     * @return CreateOrderResult containing the union graph and a flag indicating if it's new or existing.
     * @throws IllegalArgumentException if webhookUrl is provided but not HTTPS, or if filters are invalid
     */
    fun createOrder(
        resourceTypes: List<ResourceType>? = null,
        updateTtlHours: Int = 0,
        webhookUrl: String? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean = false,
        name: String,
        description: String? = null,
        resourceIds: List<String>? = null,
        resourceUris: List<String>? = null,
        includeCatalog: Boolean = true,
    ): UnionGraphService.CreateOrderResult {
        // Validate name is not blank
        if (name.isBlank()) {
            throw IllegalArgumentException("name is required and cannot be blank")
        }
        // Validate updateTtlHours: must be 0 (never update) or >= 24
        if (updateTtlHours != 0 && updateTtlHours < 24) {
            throw IllegalArgumentException("updateTtlHours must be 0 (never update) or at least 24")
        }
        // Validate webhook URL if provided
        if (!webhookUrl.isNullOrBlank() && !webhookUrl.startsWith("https://")) {
            throw IllegalArgumentException("Webhook URL must use HTTPS protocol")
        }
        validateResourceFilters(resourceTypes, resourceFilters)

        logger.info(
            "Creating union graph order with resource types: {}, updateTtlHours: {}, webhookUrl: {}",
            resourceTypes,
            updateTtlHours,
            if (webhookUrl != null) "provided" else "none",
        )

        // Prepare resource types string for query (sorted for consistency, formatted as PostgreSQL array)
        val resourceTypesString =
            if (resourceTypes.isNullOrEmpty()) {
                null
            } else {
                // Format as PostgreSQL array string: {value1,value2}
                "{" + resourceTypes.map { it.name }.sorted().joinToString(",") + "}"
            }

        // Prepare resource IDs and URIs strings for query (sorted for consistency, formatted as PostgreSQL array)
        val resourceIdsString =
            if (resourceIds.isNullOrEmpty()) {
                null
            } else {
                "{" + resourceIds.sorted().joinToString(",") + "}"
            }
        val resourceUrisString =
            if (resourceUris.isNullOrEmpty()) {
                null
            } else {
                "{" + resourceUris.sorted().joinToString(",") + "}"
            }

        // Check if an order with this configuration already exists (any status)
        val filtersPayload = resourceFilters?.normalized()
        val existingOrder =
            unionGraphOrderRepository.findByConfiguration(
                resourceTypesString,
                updateTtlHours,
                webhookUrl,
                filtersPayload?.let { objectMapper.writeValueAsString(it) },
                expandDistributionAccessServices,
                resourceIdsString,
                resourceUrisString,
                includeCatalog,
            )
        if (existingOrder != null) {
            logger.info(
                "Found existing order with same configuration (id: {}, status: {}), returning it",
                existingOrder.id,
                existingOrder.status,
            )
            return UnionGraphService.CreateOrderResult(order = existingOrder, isNew = false)
        }

        // No existing order found, create a new one
        logger.info("No existing order found with same configuration, creating new order")
        val order =
            UnionGraphOrder(
                id = UUID.randomUUID().toString(),
                status = UnionGraphOrder.GraphStatus.PENDING,
                resourceTypes = resourceTypes?.map { it.name }?.sorted(),
                updateTtlHours = updateTtlHours,
                webhookUrl = webhookUrl,
                resourceFilters = filtersPayload,
                expandDistributionAccessServices = expandDistributionAccessServices,
                name = name,
                description = description,
                resourceIds = resourceIds?.sorted(),
                resourceUris = resourceUris?.sorted(),
                includeCatalog = includeCatalog,
            )

        val saved = unionGraphOrderRepository.save(order)
        logger.info("Created union graph order with id: {}", saved.id)
        return UnionGraphService.CreateOrderResult(order = saved, isNew = true)
    }

    /**
     * Resets a union graph to PENDING status for retry.
     * Clears error messages and releases any locks.
     *
     * @param id The union graph ID to reset
     * @return The reset union graph, or null if not found
     */
    fun resetOrderToPending(id: String): UnionGraphOrder? {
        logger.info("Resetting order {} to PENDING", id)

        // Get the order before resetting to know the previous status
        val previousOrder = unionGraphOrderRepository.findById(id).orElse(null)
        if (previousOrder == null) {
            logger.warn("Order {} not found for reset", id)
            return null
        }

        val previousStatus = previousOrder.status
        metricsService.recordOrderReset()

        // Reset to PENDING, clear error message, and release locks in a single atomic operation
        val rowsAffected = unionGraphOrderRepository.resetToPending(id)
        if (rowsAffected == 0) {
            logger.warn("Order {} not found for reset", id)
            return null
        }

        // Reload the order to get updated status
        // clearAutomatically = true ensures we get fresh data from the database
        val resetOrder = unionGraphOrderRepository.findById(id).orElse(null)
        if (resetOrder != null) {
            logger.info("Successfully reset order {} to PENDING", id)

            // Call webhook if configured
            webhookService.callWebhook(resetOrder, previousStatus)
        } else {
            logger.warn("Order {} was reset but could not be reloaded", id)
        }
        return resetOrder
    }

    /**
     * Gets a union graph by ID.
     * Note: graphData field is lazy-loaded and will only be loaded when explicitly accessed.
     * Use getGraphData() if you only need the graph data.
     */
    fun getOrder(id: String): UnionGraphOrder? = unionGraphOrderRepository.findById(id).orElse(null)

    /**
     * Gets all union graphs without the graph data.
     * Union graphs are returned sorted by creation date (newest first).
     *
     * @return List of union graphs (without graph_json_ld field)
     */
    fun getAllOrders(): List<UnionGraphOrder> {
        logger.debug("Getting all union graph orders")
        return unionGraphOrderRepository.findAllByOrderByCreatedAtDesc()
    }

    /**
     * Gets all union graphs that have graph data available.
     * This includes graphs that are currently COMPLETED, as well as graphs that
     * were previously completed but are now being updated (PROCESSING status).
     *
     * @return List of all union graphs with available graph data, ordered by creation date (newest first).
     */
    fun getAvailableOrders(): List<UnionGraphOrder> {
        logger.debug("Getting all available union graph orders")
        return unionGraphOrderRepository.findAllWithGraphData()
    }

    /**
     * Gets the count of resources in a union graph from snapshots.
     * During rebuilds (PROCESSING status), only counts old snapshots to prevent inconsistency.
     * When COMPLETED, counts all snapshots (latest per resource).
     *
     * @param orderId The union graph order ID
     * @return The count of unique resources in the union graph
     */
    fun getResourceCount(orderId: String): Long {
        val order = unionGraphOrderRepository.findById(orderId).orElse(null) ?: return 0L

        // During rebuilds (PROCESSING status), only count old snapshots to prevent inconsistency
        // When COMPLETED, count all snapshots (latest per resource)
        // Use a far future timestamp as sentinel value when we don't want to filter
        // This avoids PostgreSQL type inference issues with NULL parameters
        val sentinelTimestamp = java.sql.Timestamp.valueOf("2099-12-31 23:59:59")
        val beforeTimestamp =
            if (order.status == UnionGraphOrder.GraphStatus.PROCESSING) {
                order.processingStartedAt?.let { java.sql.Timestamp.from(it) } ?: sentinelTimestamp
            } else {
                sentinelTimestamp
            }

        return unionGraphResourceSnapshotRepository.countByUnionGraphId(orderId, beforeTimestamp)
    }

    /**
     * Updates an existing union graph order.
     *
     * This method allows updating various fields of a union graph order.
     * If fields that affect the graph content are changed (resourceTypes, resourceFilters,
     * expandDistributionAccessServices, includeCatalog), the order will be
     * reset to PENDING status to trigger a rebuild with the new configuration.
     *
     * Safe fields that don't require a rebuild (updateTtlHours, webhookUrl) can be
     * updated without affecting the graph status.
     *
     * @param id The union graph ID to update
     * @param updateTtlHours Optional new TTL in hours. Must be 0 or >= 24 if provided.
     * @param webhookUrl Optional new webhook URL. Must be HTTPS if provided. Set to empty string to remove.
     * @param resourceTypes Optional new resource types list. If provided, triggers rebuild.
     * @param resourceFilters Optional new resource filters. If provided, triggers rebuild.
     * @param expandDistributionAccessServices Optional new expansion setting. If provided, triggers rebuild.
     * @param name Optional new name for the union graph. If not provided, keeps existing name.
     * @param description Optional new description for the union graph.
     * @return The updated union graph order, or null if not found
     * @throws IllegalArgumentException if validation fails (e.g., invalid webhook URL or TTL)
     */
    fun updateOrder(
        id: String,
        updateTtlHours: Int? = null,
        webhookUrl: String? = null,
        resourceTypes: List<ResourceType>? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean? = null,
        name: String? = null,
        description: String? = null,
        includeCatalog: Boolean? = null,
    ): UnionGraphOrder? {
        val providedFields =
            mutableSetOf<String>().apply {
                if (updateTtlHours != null) add("updateTtlHours")
                if (webhookUrl != null) add("webhookUrl")
                if (resourceTypes != null) add("resourceTypes")
                if (resourceFilters != null) add("resourceFilters")
                if (expandDistributionAccessServices != null) add("expandDistributionAccessServices")
                if (name != null) add("name")
                if (description != null) add("description")
                if (includeCatalog != null) add("includeCatalog")
            }
        return updateOrder(
            id,
            UnionGraphService.UpdateFields(
                updateTtlHours = updateTtlHours,
                webhookUrl = webhookUrl,
                resourceTypes = resourceTypes,
                resourceFilters = resourceFilters,
                expandDistributionAccessServices = expandDistributionAccessServices,
                name = name,
                description = description,
                includeCatalog = includeCatalog,
                providedFields = providedFields,
            ),
        )
    }

    fun updateOrder(
        id: String,
        fields: UnionGraphService.UpdateFields,
    ): UnionGraphOrder? {
        logger.info("Updating union graph order: {}", id)

        val existingOrder = unionGraphOrderRepository.findById(id).orElse(null)
        if (existingOrder == null) {
            logger.warn("Order {} not found for update", id)
            return null
        }

        // Helper to get value or keep existing (for nullable fields that can be set to null)
        fun <T> getOrKeepNullable(
            field: String,
            value: T?,
            existing: T?,
        ) = if (fields.has(field)) value else existing

        // Helper to get value or keep existing (for non-nullable fields - null values use existing)
        fun <T> getOrKeep(
            field: String,
            value: T?,
            existing: T,
        ) = if (fields.has(field) && value != null) value else existing

        // Validate updateTtlHours if provided
        val newUpdateTtlHours = getOrKeep("updateTtlHours", fields.updateTtlHours, existingOrder.updateTtlHours)
        if (newUpdateTtlHours != 0 && newUpdateTtlHours < 24) {
            throw IllegalArgumentException("updateTtlHours must be 0 (never update) or at least 24")
        }

        // Validate webhook URL if provided
        val newWebhookUrl =
            when {
                !fields.has("webhookUrl") -> existingOrder.webhookUrl
                fields.webhookUrl.isNullOrBlank() -> null // Empty string or null means remove webhook
                else -> {
                    if (!fields.webhookUrl.startsWith("https://")) {
                        throw IllegalArgumentException("Webhook URL must use HTTPS protocol")
                    }
                    fields.webhookUrl
                }
            }

        // Determine if graph-affecting fields are being changed
        val newResourceTypes =
            getOrKeepNullable("resourceTypes", fields.resourceTypes?.map { it.name }?.sorted(), existingOrder.resourceTypes)
        val newResourceFilters = getOrKeepNullable("resourceFilters", fields.resourceFilters?.normalized(), existingOrder.resourceFilters)
        val newExpandDistributionAccessServices =
            getOrKeep(
                "expandDistributionAccessServices",
                fields.expandDistributionAccessServices,
                existingOrder.expandDistributionAccessServices,
            )
        val newName = getOrKeep("name", fields.name, existingOrder.name)
        val newDescription = getOrKeepNullable("description", fields.description, existingOrder.description)
        val newResourceIds = getOrKeepNullable("resourceIds", fields.resourceIds?.sorted(), existingOrder.resourceIds)
        val newResourceUris = getOrKeepNullable("resourceUris", fields.resourceUris?.sorted(), existingOrder.resourceUris)
        val newIncludeCatalog =
            getOrKeep(
                "includeCatalog",
                fields.includeCatalog,
                existingOrder.includeCatalog,
            )

        val resourceTypesChanged = fields.has("resourceTypes") && newResourceTypes?.sorted() != existingOrder.resourceTypes?.sorted()
        val filtersChanged = fields.has("resourceFilters") && newResourceFilters != existingOrder.resourceFilters
        val expandChanged =
            fields.has("expandDistributionAccessServices") &&
                newExpandDistributionAccessServices != existingOrder.expandDistributionAccessServices
        val resourceIdsChanged = fields.has("resourceIds") && newResourceIds?.sorted() != existingOrder.resourceIds?.sorted()
        val resourceUrisChanged = fields.has("resourceUris") && newResourceUris?.sorted() != existingOrder.resourceUris?.sorted()
        val includeCatalogChanged = fields.has("includeCatalog") && newIncludeCatalog != existingOrder.includeCatalog

        val requiresRebuild =
            resourceTypesChanged || filtersChanged || expandChanged || resourceIdsChanged || resourceUrisChanged || includeCatalogChanged

        // Validate resource filters if provided
        validateResourceFilters(newResourceTypes?.map { ResourceType.valueOf(it) }, newResourceFilters)

        // Prepare resource types string for PostgreSQL array (null means all types)
        val resourceTypesString =
            if (newResourceTypes.isNullOrEmpty()) {
                null
            } else {
                // Format as PostgreSQL array string: {value1,value2}
                "{" + newResourceTypes.joinToString(",") + "}"
            }

        // Prepare resource IDs and URIs strings for PostgreSQL array
        val resourceIdsString =
            if (newResourceIds.isNullOrEmpty()) {
                null
            } else {
                "{" + newResourceIds.joinToString(",") + "}"
            }
        val resourceUrisString =
            if (newResourceUris.isNullOrEmpty()) {
                null
            } else {
                "{" + newResourceUris.joinToString(",") + "}"
            }

        val previousStatus = existingOrder.status

        logger.debug(
            "Updating order {} with values: updateTtlHours={}, webhookUrl={}, resourceTypes={}, name={}, description={}, resetToPending={}",
            id,
            newUpdateTtlHours,
            newWebhookUrl,
            resourceTypesString,
            newName,
            newDescription,
            requiresRebuild,
        )

        // Update the order
        val updatedOrder =
            unionGraphOrderRepository.updateOrder(
                id = id,
                updateTtlHours = newUpdateTtlHours,
                webhookUrl = newWebhookUrl,
                resourceTypes = resourceTypesString,
                resourceFilters = newResourceFilters?.let { objectMapper.writeValueAsString(it) },
                expandDistributionAccessServices = newExpandDistributionAccessServices,
                name = newName,
                description = newDescription,
                resourceIds = resourceIdsString,
                resourceUris = resourceUrisString,
                includeCatalog = newIncludeCatalog,
                resetToPending = requiresRebuild,
            )

        logger.debug("Update result: {} rows affected", updatedOrder)

        if (updatedOrder == 0) {
            logger.warn("Order {} not found for update", id)
            return null
        }

        // Reload the order to get updated status
        val reloadedOrder = unionGraphOrderRepository.findById(id).orElse(null)
        if (reloadedOrder != null) {
            logger.info(
                "Successfully updated order {} (requiresRebuild: {}, newStatus: {})",
                id,
                requiresRebuild,
                reloadedOrder.status,
            )

            // Call webhook if status changed or webhook URL changed
            if (previousStatus != reloadedOrder.status || newWebhookUrl != existingOrder.webhookUrl) {
                webhookService.callWebhook(reloadedOrder, previousStatus)
            }
        } else {
            logger.warn("Order {} was updated but could not be reloaded", id)
        }

        return reloadedOrder
    }

    /**
     * Deletes a union graph.
     *
     * @param id The union graph ID to delete
     * @return true if the union graph was deleted, false if not found
     */
    fun deleteOrder(id: String): Boolean {
        logger.info("Deleting union graph order: {}", id)

        val order = unionGraphOrderRepository.findById(id).orElse(null)
        if (order == null) {
            logger.warn("Order {} not found for deletion", id)
            return false
        }

        unionGraphOrderRepository.deleteById(id)
        logger.info("Successfully deleted union graph order: {}", id)
        return true
    }

    /**
     * Locks an order for processing in a new transaction to ensure the status update commits immediately.
     * This ensures the PROCESSING status is visible before starting the long-running graph build.
     *
     * @param orderId The order ID to lock
     * @param instanceId The instance ID locking the order
     * @return true if the lock was successful, false otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun lockOrderInNewTransaction(
        orderId: String,
        instanceId: String,
    ): Boolean {
        val locked = unionGraphOrderRepository.lockOrderForProcessing(orderId, instanceId)
        return locked > 0
    }

    /**
     * Fetches an order in a new transaction to ensure we see the committed state.
     * This is used after locking to get the updated status (PROCESSING).
     *
     * @param orderId The order ID to fetch
     * @return The order if found, null otherwise
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    fun getOrderInNewTransaction(orderId: String): UnionGraphOrder? = unionGraphOrderRepository.findById(orderId).orElse(null)

    private fun validateResourceFilters(
        resourceTypes: List<ResourceType>?,
        resourceFilters: UnionGraphResourceFilters?,
    ) {
        val filters = resourceFilters?.normalized() ?: return

        if (filters.dataset != null) {
            val includesDataset = resourceTypes.isNullOrEmpty() || resourceTypes.contains(ResourceType.DATASET)
            if (!includesDataset) {
                throw IllegalArgumentException("Dataset filters require the DATASET resource type")
            }
        }
    }
}
