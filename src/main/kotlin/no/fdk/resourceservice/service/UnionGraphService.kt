package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.config.UnionGraphConfig
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphProcessingState
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.model.UnionGraphResourceSnapshot
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import no.fdk.resourceservice.repository.UnionGraphResourceSnapshotRepository
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.util.UUID

/**
 * Service for managing union graphs and building union graphs.
 *
 * Union graphs are built from multiple resource graphs by combining them.
 * The service handles the asynchronous building of these graphs.
 */
@Service
@Transactional
class UnionGraphService(
    private val unionGraphOrderRepository: UnionGraphOrderRepository,
    private val resourceRepository: ResourceRepository,
    private val resourceService: ResourceService,
    private val objectMapper: ObjectMapper,
    private val webhookService: WebhookService,
    private val metricsService: UnionGraphMetricsService,
    private val unionGraphConfig: UnionGraphConfig,
    private val unionGraphResourceSnapshotRepository: UnionGraphResourceSnapshotRepository,
) {
    private val logger = LoggerFactory.getLogger(UnionGraphService::class.java)

    init {
        // Initialize metrics service with repository for gauge callbacks
        metricsService.initialize(unionGraphOrderRepository)
    }

    /**
     * Result of creating a union graph, indicating whether it's new or existing.
     */
    data class CreateOrderResult(
        val order: UnionGraphOrder,
        val isNew: Boolean,
    )

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
    ): CreateOrderResult {
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
            return CreateOrderResult(order = existingOrder, isNew = false)
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
        return CreateOrderResult(order = saved, isNew = true)
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
     * Calculate total resources for progress tracking.
     */
    private fun calculateTotalResources(
        resourceTypes: List<ResourceType>?,
        resourceFilters: UnionGraphResourceFilters?,
        resourceIds: List<String>? = null,
        resourceUris: List<String>? = null,
    ): Long {
        val typesToProcess = resourceTypes?.ifEmpty { null } ?: ResourceType.entries
        val datasetFilters = resourceFilters?.dataset

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

        var total = 0L
        for (type in typesToProcess) {
            total +=
                when {
                    type == ResourceType.DATASET && datasetFilters != null ->
                        resourceRepository.countDatasetsByFiltersWithFilters(
                            datasetFilters.isOpenData.toSqlBooleanText(),
                            datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                            resourceIdsString,
                            resourceUrisString,
                        )
                    else ->
                        resourceRepository.countByResourceTypeAndDeletedFalseWithFilters(
                            type.name,
                            resourceIdsString,
                            resourceUrisString,
                        )
                }
        }
        return total
    }

    /**
     * Data class to track which fields should be updated.
     * This allows distinguishing between "not provided" (keep existing) and "explicitly null" (set to null).
     */
    data class UpdateFields(
        val updateTtlHours: Int? = null,
        val webhookUrl: String? = null,
        val resourceTypes: List<ResourceType>? = null,
        val resourceFilters: UnionGraphResourceFilters? = null,
        val expandDistributionAccessServices: Boolean? = null,
        val name: String? = null,
        val description: String? = null,
        val resourceIds: List<String>? = null,
        val resourceUris: List<String>? = null,
        val includeCatalog: Boolean? = null,
        val providedFields: Set<String> = emptySet(),
    ) {
        fun has(field: String) = providedFields.contains(field)
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
            UpdateFields(
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
        fields: UpdateFields,
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
     * Builds a union graph by saving snapshots of resource graphs.
     *
     * This method processes resources in batches and saves snapshots of each resource's graph data
     * to ensure consistency when serving via OAI-PMH. Resources are processed in batches to avoid
     * loading all resources into memory at once.
     *
     * @param resourceTypes Optional list of resource types to include. If null or empty, all types are included.
     * @param resourceFilters Optional per-resource-type filters to apply when collecting resources.
     *                        For example, dataset filters can filter by isOpenData, isRelatedToTransportportal, and isDatasetSeries.
     *                        Only resources matching the filters will be included in the union graph.
     * @param expandDistributionAccessServices If true, datasets with distributions that reference DataService URIs
     *                                          (via distribution[].accessService[].uri) will have those DataService
     *                                          graphs automatically included in the union graph.
     * @param rdfFormat The RDF format (not used for snapshots, kept for compatibility).
     * @param orderId Optional order ID for progress tracking. Required for saving snapshots.
     * @return true if successful, false if no resources found or orderId is null.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    fun buildUnionGraph(
        resourceTypes: List<ResourceType>? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean = false,
        rdfFormat: RdfService.RdfFormat = RdfService.RdfFormat.TURTLE,
        orderId: String? = null, // Required for saving snapshots
        includeCatalog: Boolean = true,
    ): Boolean {
        if (orderId == null) {
            logger.warn("buildUnionGraph called without orderId - snapshots cannot be saved")
            return false
        }

        logger.info(
            "Building union graph snapshots for resource types: {}, expandDistributionAccessServices: {}",
            resourceTypes,
            expandDistributionAccessServices,
        )

        // Determine which resource types to process
        val typesToProcess = resourceTypes?.ifEmpty { null } ?: ResourceType.entries
        val datasetFilters = resourceFilters?.dataset

        // Count total resources to process
        var totalResources = 0L
        for (type in typesToProcess) {
            totalResources +=
                when {
                    type == ResourceType.DATASET && datasetFilters != null ->
                        resourceRepository.countDatasetsByFilters(
                            datasetFilters.isOpenData.toSqlBooleanText(),
                            datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                        )
                    else -> resourceRepository.countByResourceTypeAndDeletedFalse(type.name)
                }
        }

        if (totalResources == 0L) {
            logger.warn("No resources found for union graph")
            return false
        }

        logger.info(
            "Found {} total resources to snapshot (processing in batches of {})",
            totalResources,
            unionGraphConfig.resourceBatchSize,
        )

        val totalStartTime = System.nanoTime()

        try {
            var processedCount = 0L
            val batchSize = unionGraphConfig.resourceBatchSize

            // Process each resource type
            for (type in typesToProcess) {
                var offset = 0
                var hasMore = true

                // Process resources in batches
                while (hasMore) {
                    val batch =
                        when {
                            type == ResourceType.DATASET && datasetFilters != null ->
                                resourceRepository.findDatasetsByFiltersWithGraphDataPaginated(
                                    offset,
                                    batchSize,
                                    datasetFilters.isOpenData.toSqlBooleanText(),
                                    datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                                )
                            else ->
                                resourceRepository.findByResourceTypeAndDeletedFalseWithGraphDataPaginated(
                                    type.name,
                                    offset,
                                    batchSize,
                                )
                        }

                    if (batch.isEmpty()) {
                        hasMore = false
                    } else {
                        // Collect snapshots for batch insert
                        val snapshotsToSave = mutableListOf<UnionGraphResourceSnapshot>()

                        // Process each resource and prepare snapshots
                        for (resource in batch) {
                            val graphData = resource.resourceGraphData
                            if (graphData != null && graphData.isNotBlank()) {
                                // Parse graph once into a model
                                val model = parseGraphToModel(graphData, resource.resourceGraphFormat)
                                if (model == null) {
                                    logger.warn("Failed to parse graph for resource {}, skipping", resource.id)
                                    continue
                                }

                                try {
                                    // Apply isDatasetSeries filter if specified
                                    val shouldInclude =
                                        if (type == ResourceType.DATASET && datasetFilters?.isDatasetSeries != null) {
                                            val isDatasetSeries = isDatasetSeriesInModel(model, resource.uri)
                                            // Filter logic: null = both, true = only series, false = no series
                                            when (datasetFilters.isDatasetSeries!!) {
                                                true -> isDatasetSeries // Include only if IS DatasetSeries
                                                false -> !isDatasetSeries // Include only if NOT DatasetSeries
                                            }
                                        } else {
                                            true // No filter, include all
                                        }

                                    if (!shouldInclude) {
                                        model.close()
                                        continue
                                    }

                                    // Process model: merge DataService graphs, filter Catalog, convert to RDF/XML
                                    val rdfXmlData =
                                        processResourceModelToRdfXml(
                                            model,
                                            resource,
                                            expandDistributionAccessServices,
                                            includeCatalog,
                                        )

                                    if (rdfXmlData != null) {
                                        // Create snapshot of resource graph data in RDF-XML format
                                        val resourceModifiedAt = parseResourceModifiedAt(resource.resourceJson)
                                        val publisherOrgnr = parsePublisherOrgnr(resource.resourceJson)
                                        val snapshot =
                                            UnionGraphResourceSnapshot(
                                                unionGraphId = orderId,
                                                resourceId = resource.id,
                                                resourceType = resource.resourceType,
                                                resourceGraphData = rdfXmlData,
                                                resourceGraphFormat = "RDF_XML",
                                                resourceModifiedAt = resourceModifiedAt,
                                                publisherOrgnr = publisherOrgnr,
                                            )
                                        snapshotsToSave.add(snapshot)
                                    } else {
                                        logger.warn("Failed to process resource {} to RDF/XML, skipping snapshot", resource.id)
                                    }
                                } finally {
                                    model.close()
                                }
                            }
                        }

                        // Batch insert all snapshots at once (much more efficient than individual saves)
                        if (snapshotsToSave.isNotEmpty()) {
                            unionGraphResourceSnapshotRepository.saveAll(snapshotsToSave)
                        }

                        processedCount += batch.size

                        // Update progress if orderId provided
                        metricsService.updateProcessingProgress(orderId, processedCount)
                        metricsService.recordResourcesProcessed(batch.size.toLong())

                        // Log progress periodically
                        if (processedCount % UnionGraphConfig.UNION_GRAPH_PROGRESS_UPDATE_INTERVAL == 0L) {
                            logger.debug("Processed {} resources so far...", processedCount)
                        }

                        // Check if we've processed all resources for this type
                        if (batch.size < batchSize) {
                            hasMore = false
                        } else {
                            offset += batchSize
                        }
                    }
                }
            }

            val totalTime = (System.nanoTime() - totalStartTime) / 1_000_000.0
            logger.info(
                "Successfully built union graph snapshots from {} resources in {} ms",
                processedCount,
                String.format("%.2f", totalTime),
            )
            logger.debug("=== Union Graph Build Performance ===")
            logger.debug("Total time: {} ms ({} s)", String.format("%.2f", totalTime), String.format("%.2f", totalTime / 1000.0))
            logger.debug(
                "Resources processed: {}, Average: {} ms/resource",
                processedCount,
                String.format(
                    "%.2f",
                    totalTime / processedCount,
                ),
            )

            // Note: Cleanup of old snapshots is handled in processOrder when the order is marked as COMPLETED
            // This ensures old snapshots remain accessible during the build and until the new build is complete

            return true
        } catch (e: Exception) {
            logger.error("Failed to build union graph snapshots", e)
            throw e
        }
    }

    /**
     * Extracts nodes from a JSON-LD Map and adds them to the provided list.
     *
     * Handles both @graph format (array of nodes) and single node format (@id present).
     * This method is thread-safe when used with a synchronized list.
     *
     * @param jsonLd The JSON-LD Map to extract nodes from
     * @param nodeList Thread-safe list to add nodes to
     */
    private fun extractJsonLdNodes(
        jsonLd: Map<String, Any>,
        nodeList: MutableList<Map<String, Any>>,
    ) {
        when {
            jsonLd.containsKey("@graph") -> {
                // @graph format: extract nodes from the array
                (jsonLd["@graph"] as? List<*>)?.forEach { node ->
                    if (node is Map<*, *>) {
                        synchronized(nodeList) {
                            nodeList.add(node as Map<String, Any>)
                        }
                    }
                }
            }
            jsonLd.containsKey("@id") -> {
                // Single node format: add the node directly
                synchronized(nodeList) {
                    nodeList.add(jsonLd)
                }
            }
            else -> {
                // Fallback: if it looks like a node (has keys), add it
                // This handles edge cases where @id might be missing
                if (jsonLd.isNotEmpty()) {
                    synchronized(nodeList) {
                        nodeList.add(jsonLd)
                    }
                }
            }
        }
    }

    /**
     * Extracts resource modified date from FDK resource JSON (harvest.modified, ISO-8601).
     * Used for OAI-PMH from/until and datestamp.
     */
    private fun parseResourceModifiedAt(resourceJson: Map<String, Any>?): java.time.Instant? {
        if (resourceJson == null) return null
        val harvest = resourceJson["harvest"] as? Map<*, *> ?: return null
        val modified = harvest["modified"] as? String ?: return null
        return try {
            java.time.Instant.parse(modified)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts publisher organization number from FDK resource JSON (publisher.id).
     * Used for OAI-PMH set filter org:orgnr and setSpec in headers.
     */
    private fun parsePublisherOrgnr(resourceJson: Map<String, Any>?): String? {
        if (resourceJson == null) return null
        val publisher = resourceJson["publisher"] as? Map<*, *> ?: return null
        val id = publisher["id"] ?: return null
        return id.toString().takeIf { it.isNotBlank() }
    }

    /**
     * Merges DataService graphs into an existing Jena Model.
     *
     * @param model The existing model to merge DataService graphs into (modified in place)
     * @param dataServiceUris Set of DataService URIs to fetch and merge
     */
    private fun mergeDataServiceGraphsIntoModel(
        model: org.apache.jena.rdf.model.Model,
        dataServiceUris: Set<String>,
    ) {
        if (dataServiceUris.isEmpty()) {
            return
        }

        var mergedCount = 0
        for (uri in dataServiceUris) {
            try {
                val dataServiceEntity = resourceService.getResourceEntityByUri(uri)
                if (dataServiceEntity != null &&
                    dataServiceEntity.resourceGraphData != null &&
                    dataServiceEntity.resourceGraphData.isNotBlank()
                ) {
                    val dataServiceLang = parseLang(dataServiceEntity.resourceGraphFormat ?: "TURTLE")
                    ByteArrayInputStream(dataServiceEntity.resourceGraphData.toByteArray()).use { inputStream ->
                        RDFDataMgr.read(model, inputStream, dataServiceLang)
                    }
                    mergedCount++
                }
            } catch (e: Exception) {
                logger.warn("Failed to merge DataService graph for URI {}: {}", uri, e.message)
            }
        }

        if (mergedCount > 0) {
            logger.debug("Merged {} DataService graph(s) into dataset graph", mergedCount)
        }
    }

    /**
     * Merges DataService graphs into a dataset graph.
     *
     * @param datasetGraphData The dataset graph data
     * @param datasetGraphFormat The format of the dataset graph
     * @param dataServiceUris Set of DataService URIs to fetch and merge
     * @return The merged graph data in the same format as the input
     */
    private fun mergeDataServiceGraphs(
        datasetGraphData: String,
        datasetGraphFormat: String,
        dataServiceUris: Set<String>,
    ): String {
        if (dataServiceUris.isEmpty()) {
            return datasetGraphData
        }

        try {
            // Create a model and load the dataset graph
            val model = ModelFactory.createDefaultModel()
            val datasetLang = parseLang(datasetGraphFormat)
            ByteArrayInputStream(datasetGraphData.toByteArray()).use { inputStream ->
                RDFDataMgr.read(model, inputStream, datasetLang)
            }

            // Merge DataService graphs into the model
            mergeDataServiceGraphsIntoModel(model, dataServiceUris)

            // Convert back to the original format
            val writer = StringWriter()
            RDFDataMgr.write(writer, model, datasetLang)
            model.close()
            return writer.toString()
        } catch (e: Exception) {
            logger.warn("Failed to merge DataService graphs, using original dataset graph: {}", e.message)
        }

        return datasetGraphData
    }

    /**
     * Filters out Catalog and CatalogRecord resources from a Jena Model.
     * Removes all statements where Catalog or CatalogRecord is the subject,
     * but preserves statements where their URIs appear as objects (references).
     *
     * @param model The Jena Model to filter (modified in place)
     */
    private fun filterCatalogFromModel(model: org.apache.jena.rdf.model.Model) {
        try {
            // Find all resources with type dcat:Catalog or dcat:CatalogRecord
            val catalogType = model.createResource("http://www.w3.org/ns/dcat#Catalog")
            val catalogRecordType = model.createResource("http://www.w3.org/ns/dcat#CatalogRecord")
            val rdfType = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

            // Find all resources that are of type Catalog or CatalogRecord
            val catalogResources = mutableSetOf<org.apache.jena.rdf.model.Resource>()
            val catalogRecordResources = mutableSetOf<org.apache.jena.rdf.model.Resource>()

            // Query for resources with rdf:type = dcat:Catalog
            val catalogStmts = model.listStatements(null, rdfType, catalogType)
            while (catalogStmts.hasNext()) {
                val stmt = catalogStmts.nextStatement()
                val resource = stmt.subject
                if (resource.isURIResource) {
                    catalogResources.add(resource)
                }
            }

            // Query for resources with rdf:type = dcat:CatalogRecord
            val catalogRecordStmts = model.listStatements(null, rdfType, catalogRecordType)
            while (catalogRecordStmts.hasNext()) {
                val stmt = catalogRecordStmts.nextStatement()
                val resource = stmt.subject
                if (resource.isURIResource) {
                    catalogRecordResources.add(resource)
                }
            }

            // Remove all statements where Catalog or CatalogRecord resources are the subject
            // This removes the entire resource with all its properties, but preserves
            // statements where the Catalog/CatalogRecord URI appears as an object
            for (resource in catalogResources) {
                model.removeAll(resource, null, null)
            }
            for (resource in catalogRecordResources) {
                model.removeAll(resource, null, null)
            }
        } catch (e: Exception) {
            logger.warn("Failed to filter Catalog/CatalogRecord from model: {}", e.message)
            // Model remains unchanged on error
        }
    }

    /**
     * Parses RDF graph data into a Jena Model.
     *
     * @param graphData The RDF graph data (in any supported format)
     * @param graphFormat The format of the graph data (TURTLE, JSON-LD, RDF/XML, etc.)
     * @return The parsed model, or null on error
     */
    private fun parseGraphToModel(
        graphData: String,
        graphFormat: String?,
    ): org.apache.jena.rdf.model.Model? {
        return try {
            if (graphData.isBlank()) {
                return null
            }

            val model = ModelFactory.createDefaultModel()
            val lang = parseLang(graphFormat ?: "TURTLE")

            ByteArrayInputStream(graphData.toByteArray()).use { inputStream ->
                RDFDataMgr.read(model, inputStream, lang)
            }

            model
        } catch (e: Exception) {
            logger.warn("Failed to parse RDF graph: {}", e.message)
            null
        }
    }

    /**
     * Checks if a dataset resource has rdf:type = dcat:DatasetSeries in a Jena Model.
     *
     * @param model The Jena Model containing the RDF graph
     * @param datasetUri The URI of the dataset resource to check
     * @return true if the dataset is a DatasetSeries, false otherwise
     */
    private fun isDatasetSeriesInModel(
        model: org.apache.jena.rdf.model.Model,
        datasetUri: String?,
    ): Boolean {
        if (datasetUri.isNullOrBlank()) {
            return false
        }

        val datasetResource = model.createResource(datasetUri)
        val datasetSeriesType = model.createResource("http://www.w3.org/ns/dcat#DatasetSeries")
        val rdfType = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

        return model.contains(datasetResource, rdfType, datasetSeriesType)
    }

    /**
     * Processes a resource model: merges DataService graphs (if needed), filters Catalog (if needed),
     * and converts to RDF/XML for snapshot storage.
     *
     * @param model The Jena Model to process (will be modified in place)
     * @param resource The resource entity
     * @param expandDistributionAccessServices Whether to expand DataService graphs
     * @param includeCatalog Whether to include Catalog resources
     * @return The RDF/XML string for snapshot storage, or null on error
     */
    private fun processResourceModelToRdfXml(
        model: org.apache.jena.rdf.model.Model,
        resource: no.fdk.resourceservice.model.ResourceEntity,
        expandDistributionAccessServices: Boolean,
        includeCatalog: Boolean,
    ): String? {
        try {
            // Merge DataService graphs if needed
            if (expandDistributionAccessServices && resource.resourceType == ResourceType.DATASET.name) {
                val datasetJson = resource.resourceJson
                if (datasetJson != null) {
                    val dataServiceUris = mutableSetOf<String>()
                    extractDataServiceUris(datasetJson, dataServiceUris)
                    if (dataServiceUris.isNotEmpty()) {
                        mergeDataServiceGraphsIntoModel(model, dataServiceUris)
                    }
                }
            }

            // Filter Catalog if needed
            if (!includeCatalog) {
                filterCatalogFromModel(model)
            }

            // Check if model is empty after filtering
            if (model.isEmpty) {
                logger.warn("Model is empty after processing for resource {}, skipping", resource.id)
                return null
            }

            // Convert to RDF/XML
            val writer = StringWriter()
            RDFDataMgr.write(writer, model, Lang.RDFXML)
            return writer.toString()
        } catch (e: Exception) {
            logger.warn("Failed to process resource model to RDF/XML: {}", e.message)
            return null
        }
    }

    /**
     * Parses a format string to Jena Lang enum.
     */
    private fun parseLang(format: String): Lang =
        when (format.uppercase()) {
            "TURTLE" -> Lang.TURTLE
            "JSON-LD", "JSONLD" -> Lang.JSONLD
            "RDF/XML", "RDFXML" -> Lang.RDFXML
            "N-TRIPLES", "NTRIPLES" -> Lang.NTRIPLES
            "N-QUADS", "NQUADS" -> Lang.NQUADS
            else -> Lang.TURTLE
        }

    /**
     * Extracts DataService URIs from a dataset JSON payload.
     *
     * Follows the path: distribution[*].accessService[*].uri
     * Adds URIs to the provided set (thread-safe).
     *
     * @param datasetJson The dataset JSON map
     * @param uriSet Thread-safe set to add URIs to
     */
    private fun extractDataServiceUris(
        datasetJson: Map<String, Any>,
        uriSet: MutableSet<String>,
    ) {
        try {
            // Extract distributions from the dataset
            val distributions =
                when (val distValue = datasetJson["distribution"]) {
                    is List<*> -> distValue.filterIsInstance<Map<String, Any>>()
                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        listOf(distValue as Map<String, Any>)
                    }
                    else -> emptyList()
                }

            // Extract DataService URIs from distributions
            for (distribution in distributions) {
                val accessServices =
                    when (val accessServiceValue = distribution["accessService"]) {
                        is List<*> -> accessServiceValue.filterIsInstance<Map<String, Any>>()
                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            listOf(accessServiceValue as Map<String, Any>)
                        }
                        else -> emptyList()
                    }

                for (accessService in accessServices) {
                    when (val uriValue = accessService["uri"]) {
                        is String -> {
                            if (uriValue.isNotBlank()) {
                                uriSet.add(uriValue)
                            }
                        }
                        is List<*> -> {
                            uriValue
                                .filterIsInstance<String>()
                                .filter { it.isNotBlank() }
                                .forEach { uriSet.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract DataService URIs from dataset: {}", e.message)
            // Continue processing other resources
        }
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

    /**
     * Processes a union graph by building the union graph and updating it.
     *
     * This method is NOT transactional to avoid issues with long-running operations.
     * Each repository method handles its own transaction boundaries.
     *
     * @param order The union graph to process.
     * @param instanceId The identifier of the instance processing this union graph.
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun processOrder(
        order: UnionGraphOrder,
        instanceId: String,
    ) {
        logger.info("Processing union graph order {} by instance {}", order.id, instanceId)

        try {
            // Lock the order for processing in a NEW transaction to ensure status update commits immediately
            // This ensures the PROCESSING status is visible before starting the long-running graph build
            val locked = lockOrderInNewTransaction(order.id, instanceId)
            if (!locked) {
                logger.warn("Failed to lock order {} for processing (may have been locked by another instance)", order.id)
                return
            }

            // Fetch the order fresh in a NEW transaction to get the updated status (PROCESSING) after the lock commits
            // Using REQUIRES_NEW ensures we see the committed state, not a cached entity from the current transaction
            val lockedOrder = getOrderInNewTransaction(order.id)
            if (lockedOrder == null) {
                logger.warn("Order {} not found after locking", order.id)
                return
            }

            // Verify the status was actually updated to PROCESSING
            if (lockedOrder.status != UnionGraphOrder.GraphStatus.PROCESSING) {
                logger.warn(
                    "Order {} status is {} after locking, expected PROCESSING. Lock may have failed or been overridden.",
                    order.id,
                    lockedOrder.status,
                )
                return
            }

            // Build the union graph using the locked order's configuration
            val resourceTypes =
                lockedOrder.resourceTypes?.mapNotNull { typeName ->
                    try {
                        ResourceType.valueOf(typeName)
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Unknown resource type: {}", typeName)
                        null
                    }
                }

            // Store build start time to identify old snapshots for cleanup after completion
            // Old snapshots remain accessible during the build to ensure OAI-PMH continuity
            val buildStartTime = lockedOrder.processingStartedAt ?: java.time.Instant.now()

            // Initialize processing state
            val initialized = initializeProcessingState(lockedOrder.id)
            if (!initialized) {
                logger.warn("Failed to initialize processing state for order {}", lockedOrder.id)
                unionGraphOrderRepository.markAsFailed(
                    lockedOrder.id,
                    "Failed to initialize processing state",
                )
                metricsService.recordOrderFailed("initialization_error")
                return
            }

            logger.info(
                "Initialized processing state for order {}. Scheduler will process batches incrementally.",
                lockedOrder.id,
            )
        } catch (e: Exception) {
            logger.error("Error processing union graph order {}", order.id, e)
            val previousStatus = order.status
            unionGraphOrderRepository.markAsFailed(
                order.id,
                "Error processing order: ${e.message}",
            )
            metricsService.recordOrderFailed("processing_error")
            metricsService.stopProcessingProgress(order.id)

            // Call webhook if configured
            val updatedOrder = unionGraphOrderRepository.findById(order.id).orElse(null)
            if (updatedOrder != null) {
                webhookService.callWebhook(updatedOrder, previousStatus)
            }
        }
    }

    /**
     * Processes the next batch of resources for a union graph order.
     * This method is called by the scheduler to process one batch at a time,
     * reducing memory consumption by processing incrementally.
     *
     * @param orderId The order ID to process
     * @return true if processing should continue, false if complete or failed
     */
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    fun processNextBatch(orderId: String): Boolean {
        val order =
            unionGraphOrderRepository.findById(orderId).orElse(null)
                ?: return false

        if (order.status != UnionGraphOrder.GraphStatus.PROCESSING) {
            logger.debug("Order {} is not in PROCESSING status (current: {}), skipping", orderId, order.status)
            return false
        }

        val state =
            order.processingState
                ?: run {
                    logger.warn("Order {} has null processing state, cannot process batch", orderId)
                    return false // Should not happen if initialized correctly
                }

        try {
            val resourceTypes =
                order.resourceTypes?.mapNotNull { typeName ->
                    try {
                        ResourceType.valueOf(typeName)
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Unknown resource type: {}", typeName)
                        null
                    }
                } ?: ResourceType.entries

            if (state.isComplete(resourceTypes.size)) {
                // All resource types processed
                // Check if any resources were actually processed
                if (state.processedCount == 0L) {
                    // No resources found - mark as failed
                    unionGraphOrderRepository.markAsFailed(
                        orderId,
                        "No resources found for the specified resource types and filters",
                    )
                    metricsService.recordOrderFailed("no_resources_found")
                    metricsService.stopProcessingProgress(orderId)
                    logger.warn("Union graph order {} failed: No resources found", orderId)
                    return false
                }

                // Mark as completed
                unionGraphOrderRepository.markAsCompleted(orderId)

                // Clean up old snapshots created before this build started
                // This ensures old snapshots remained accessible during the build
                val buildStartTime = order.processingStartedAt
                if (buildStartTime != null) {
                    logger.info("Cleaning up old snapshots created before build start for union graph: {}", orderId)
                    try {
                        unionGraphResourceSnapshotRepository.deleteByUnionGraphIdCreatedBefore(
                            orderId,
                            java.sql.Timestamp.from(buildStartTime),
                        )
                    } catch (e: Exception) {
                        logger.warn("Failed to clean up old snapshots for union graph {}: {}", orderId, e.message, e)
                        // Don't fail the build if snapshot cleanup fails
                    }
                }

                metricsService.stopProcessingProgress(orderId)
                logger.info("Successfully completed union graph order {} with {} resources", orderId, state.processedCount)
                return false
            }

            // Get current resource type
            val currentResourceType = resourceTypes[state.currentResourceTypeIndex]
            val datasetFilters = order.resourceFilters?.dataset

            // Prepare resource IDs and URIs strings for query (sorted for consistency, formatted as PostgreSQL array)
            val resourceIdsString =
                if (order.resourceIds.isNullOrEmpty()) {
                    null
                } else {
                    "{" + order.resourceIds.sorted().joinToString(",") + "}"
                }
            val resourceUrisString =
                if (order.resourceUris.isNullOrEmpty()) {
                    null
                } else {
                    "{" + order.resourceUris.sorted().joinToString(",") + "}"
                }

            // Fetch one batch of resources
            logger.info(
                "Fetching batch for order {}: resourceType={}, offset={}, batchSize={}",
                orderId,
                currentResourceType,
                state.currentOffset,
                unionGraphConfig.resourceBatchSize,
            )
            val batch =
                when {
                    currentResourceType == ResourceType.DATASET && datasetFilters != null ->
                        resourceRepository.findDatasetsByFiltersWithGraphDataPaginatedWithFilters(
                            state.currentOffset,
                            unionGraphConfig.resourceBatchSize,
                            datasetFilters.isOpenData.toSqlBooleanText(),
                            datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                            resourceIdsString,
                            resourceUrisString,
                        )
                    else ->
                        resourceRepository.findByResourceTypeAndDeletedFalseWithGraphDataPaginatedWithFilters(
                            currentResourceType.name,
                            state.currentOffset,
                            unionGraphConfig.resourceBatchSize,
                            resourceIdsString,
                            resourceUrisString,
                        )
                }

            logger.info("Fetched batch of {} resources for order {}", batch.size, orderId)

            if (batch.isEmpty()) {
                // No more resources for this type, move to next type
                val newState =
                    state.copy(
                        currentResourceTypeIndex = state.currentResourceTypeIndex + 1,
                        currentOffset = 0,
                    )
                updateProcessingState(orderId, newState)
                return true // Continue processing
            }

            // Process batch and prepare snapshots for batch insert
            val snapshotsToSave = mutableListOf<UnionGraphResourceSnapshot>()

            logger.info("Starting to process {} resources in batch for order {}", batch.size, orderId)
            var processedCount = 0
            var skippedCount = 0
            var errorCount = 0

            for (resource in batch) {
                processedCount++
                if (processedCount % 10 == 0) {
                    logger.debug("Processing resource {}/{} in batch for order {}", processedCount, batch.size, orderId)
                }
                val graphData = resource.resourceGraphData
                if (graphData != null && graphData.isNotBlank()) {
                    // Parse graph once into a model
                    val model = parseGraphToModel(graphData, resource.resourceGraphFormat)
                    if (model == null) {
                        logger.warn("Failed to parse graph for resource {}, skipping", resource.id)
                        errorCount++
                        continue
                    }

                    try {
                        // Apply isDatasetSeries filter if specified
                        val shouldInclude =
                            if (currentResourceType == ResourceType.DATASET && datasetFilters?.isDatasetSeries != null) {
                                val isDatasetSeries = isDatasetSeriesInModel(model, resource.uri)
                                // Filter logic: null = both, true = only series, false = no series
                                when (datasetFilters.isDatasetSeries!!) {
                                    true -> isDatasetSeries // Include only if IS DatasetSeries
                                    false -> !isDatasetSeries // Include only if NOT DatasetSeries
                                }
                            } else {
                                true // No filter, include all
                            }

                        if (!shouldInclude) {
                            model.close()
                            skippedCount++
                            continue
                        }

                        // Process model: merge DataService graphs, filter Catalog, convert to RDF/XML
                        val rdfXmlData =
                            processResourceModelToRdfXml(
                                model,
                                resource,
                                order.expandDistributionAccessServices,
                                order.includeCatalog,
                            )

                        if (rdfXmlData != null) {
                            // Create snapshot of resource graph data in RDF-XML format
                            val resourceModifiedAt = parseResourceModifiedAt(resource.resourceJson)
                            val publisherOrgnr = parsePublisherOrgnr(resource.resourceJson)
                            val snapshot =
                                UnionGraphResourceSnapshot(
                                    unionGraphId = orderId,
                                    resourceId = resource.id,
                                    resourceType = resource.resourceType,
                                    resourceGraphData = rdfXmlData,
                                    resourceGraphFormat = "RDF_XML",
                                    resourceModifiedAt = resourceModifiedAt,
                                    publisherOrgnr = publisherOrgnr,
                                )
                            snapshotsToSave.add(snapshot)
                        } else {
                            logger.warn("Failed to process resource {} to RDF/XML, skipping snapshot", resource.id)
                            errorCount++
                        }
                    } finally {
                        model.close()
                    }
                } else {
                    skippedCount++
                }
            }

            logger.info(
                "Finished processing batch for order {}: processed={}, skipped={}, errors={}, snapshots={}",
                orderId,
                processedCount,
                skippedCount,
                errorCount,
                snapshotsToSave.size,
            )

            // Batch insert all snapshots at once (much more efficient than individual saves)
            if (snapshotsToSave.isNotEmpty()) {
                logger.info("Saving {} snapshots for order {}", snapshotsToSave.size, orderId)
                unionGraphResourceSnapshotRepository.saveAll(snapshotsToSave)
                logger.info("Saved {} snapshots for order {}", snapshotsToSave.size, orderId)
            }

            // Update state
            logger.info("Updating processing state for order {}", orderId)
            val newState =
                state.copy(
                    currentOffset = state.currentOffset + batch.size,
                    processedCount = state.processedCount + batch.size,
                )
            updateProcessingState(orderId, newState)
            logger.info("Updated processing state for order {}", orderId)

            // Update progress
            metricsService.updateProcessingProgress(orderId, newState.processedCount)
            metricsService.recordResourcesProcessed(batch.size.toLong())

            logger.info(
                "Processed batch for order {}: {} resources, {} snapshots created, total processed: {}",
                orderId,
                batch.size,
                snapshotsToSave.size,
                newState.processedCount,
            )

            return true // Continue processing
        } catch (e: Exception) {
            logger.error("Error processing batch for order {}", orderId, e)
            unionGraphOrderRepository.markAsFailed(
                orderId,
                "Error processing batch: ${e.message}",
            )
            metricsService.recordOrderFailed("batch_processing_error")
            metricsService.stopProcessingProgress(orderId)
            return false
        }
    }

    /**
     * Initializes the processing state for a union graph order.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun initializeProcessingState(orderId: String): Boolean {
        val order =
            unionGraphOrderRepository.findById(orderId).orElse(null)
                ?: return false

        // Initialize processing state
        val initialState = UnionGraphProcessingState()
        updateProcessingState(orderId, initialState)

        // Start progress tracking
        val resourceTypes =
            order.resourceTypes?.mapNotNull { typeName ->
                try {
                    ResourceType.valueOf(typeName)
                } catch (e: IllegalArgumentException) {
                    null
                }
            } ?: ResourceType.entries

        val totalResources = calculateTotalResources(resourceTypes, order.resourceFilters, order.resourceIds, order.resourceUris)
        metricsService.startProcessingProgress(orderId, totalResources)

        logger.info("Initialized processing state for order {}", orderId)
        return true
    }

    /**
     * Updates the processing state in the database.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    private fun updateProcessingState(
        orderId: String,
        state: UnionGraphProcessingState,
    ) {
        try {
            val stateJson = objectMapper.writeValueAsString(state)
            unionGraphOrderRepository.updateProcessingState(orderId, stateJson)
        } catch (e: Exception) {
            logger.error("Failed to update processing state for order {}", orderId, e)
            throw e
        }
    }

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

private fun Boolean?.toSqlBooleanText(): String? = this?.toString()
