package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.config.UnionGraphConfig
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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
    private val rdfService: RdfService,
    private val metricsService: UnionGraphMetricsService,
    private val unionGraphConfig: UnionGraphConfig,
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
     * @param updateTtlHours Time to live in hours for automatic updates. 0 means never update automatically. Must be 0 or > 3.
     * @param webhookUrl Optional webhook URL to call when union graph status changes. Must be HTTPS if provided.
     * @param resourceFilters Optional per-resource-type filters to apply when building the graph.
     *                        For example, dataset filters can filter by isOpenData and isRelatedToTransportportal.
     *                        Filters are part of the union graph configuration, so union graphs with different filters
     *                        are considered different union graphs.
     * @param expandDistributionAccessServices If true, datasets with distributions that reference DataService URIs
     *                                          will have those DataService graphs automatically included in the union graph.
     *                                          This allows creating union graphs that include both datasets and their related
     *                                          data services in a single graph.
     * @param format The RDF format to use for the graph data (default: JSON_LD).
     * @param style The style to use for the graph format (PRETTY or STANDARD, default: PRETTY).
     * @param expandUris Whether to expand URIs in the graph data (default: true).
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
        format: UnionGraphOrder.GraphFormat = UnionGraphOrder.GraphFormat.JSON_LD,
        style: UnionGraphOrder.GraphStyle = UnionGraphOrder.GraphStyle.PRETTY,
        expandUris: Boolean = true,
        name: String,
        description: String? = null,
    ): CreateOrderResult {
        // Validate name is not blank
        if (name.isBlank()) {
            throw IllegalArgumentException("name is required and cannot be blank")
        }
        // Validate updateTtlHours: must be 0 (never update) or > 3
        if (updateTtlHours != 0 && updateTtlHours <= 3) {
            throw IllegalArgumentException("updateTtlHours must be 0 (never update) or greater than 3")
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

        // Check if an order with this configuration already exists (any status)
        val filtersPayload = resourceFilters?.normalized()
        val existingOrder =
            unionGraphOrderRepository.findByConfiguration(
                resourceTypesString,
                updateTtlHours,
                webhookUrl,
                filtersPayload?.let { objectMapper.writeValueAsString(it) },
                expandDistributionAccessServices,
                format.name,
                style.name,
                expandUris,
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
                format = format,
                style = style,
                expandUris = expandUris,
                name = name,
                description = description,
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
     * Calculate total resources for progress tracking.
     */
    private fun calculateTotalResources(
        resourceTypes: List<ResourceType>?,
        resourceFilters: UnionGraphResourceFilters?,
    ): Long {
        val typesToProcess = resourceTypes?.ifEmpty { null } ?: ResourceType.entries
        val datasetFilters = resourceFilters?.dataset
        var total = 0L
        for (type in typesToProcess) {
            total +=
                when {
                    type == ResourceType.DATASET && datasetFilters != null ->
                        resourceRepository.countDatasetsByFilters(
                            datasetFilters.isOpenData.toSqlBooleanText(),
                            datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                        )
                    else -> resourceRepository.countByResourceTypeAndDeletedFalse(type.name)
                }
        }
        return total
    }

    /**
     * Updates an existing union graph order.
     *
     * This method allows updating various fields of a union graph order.
     * If fields that affect the graph content are changed (resourceTypes, resourceFilters,
     * expandDistributionAccessServices, format, style, expandUris), the order will be
     * reset to PENDING status to trigger a rebuild with the new configuration.
     *
     * Safe fields that don't require a rebuild (updateTtlHours, webhookUrl) can be
     * updated without affecting the graph status.
     *
     * @param id The union graph ID to update
     * @param updateTtlHours Optional new TTL in hours. Must be 0 or > 3 if provided.
     * @param webhookUrl Optional new webhook URL. Must be HTTPS if provided. Set to empty string to remove.
     * @param resourceTypes Optional new resource types list. If provided, triggers rebuild.
     * @param resourceFilters Optional new resource filters. If provided, triggers rebuild.
     * @param expandDistributionAccessServices Optional new expansion setting. If provided, triggers rebuild.
     * @param format Optional new format. If provided, triggers rebuild.
     * @param style Optional new style. If provided, triggers rebuild.
     * @param expandUris Optional new expand URIs setting. If provided, triggers rebuild.
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
        format: UnionGraphOrder.GraphFormat? = null,
        style: UnionGraphOrder.GraphStyle? = null,
        expandUris: Boolean? = null,
        name: String? = null,
        description: String? = null,
    ): UnionGraphOrder? {
        logger.info("Updating union graph order: {}", id)

        val existingOrder = unionGraphOrderRepository.findById(id).orElse(null)
        if (existingOrder == null) {
            logger.warn("Order {} not found for update", id)
            return null
        }

        // Validate updateTtlHours if provided
        val newUpdateTtlHours = updateTtlHours ?: existingOrder.updateTtlHours
        if (newUpdateTtlHours != 0 && newUpdateTtlHours <= 3) {
            throw IllegalArgumentException("updateTtlHours must be 0 (never update) or greater than 3")
        }

        // Validate webhook URL if provided
        val newWebhookUrl =
            when {
                webhookUrl == null -> existingOrder.webhookUrl
                webhookUrl.isBlank() -> null // Empty string means remove webhook
                else -> {
                    if (!webhookUrl.startsWith("https://")) {
                        throw IllegalArgumentException("Webhook URL must use HTTPS protocol")
                    }
                    webhookUrl
                }
            }

        // Determine if graph-affecting fields are being changed
        val resourceTypesChanged = resourceTypes != null && resourceTypes.map { it.name }.sorted() != existingOrder.resourceTypes?.sorted()
        val filtersChanged = resourceFilters != null && resourceFilters.normalized() != existingOrder.resourceFilters
        val expandChanged =
            expandDistributionAccessServices != null && expandDistributionAccessServices != existingOrder.expandDistributionAccessServices
        val formatChanged = format != null && format != existingOrder.format
        val styleChanged = style != null && style != existingOrder.style
        val expandUrisChanged = expandUris != null && expandUris != existingOrder.expandUris

        val requiresRebuild = resourceTypesChanged || filtersChanged || expandChanged || formatChanged || styleChanged || expandUrisChanged

        // Prepare new values
        val newResourceTypes = resourceTypes?.map { it.name }?.sorted() ?: existingOrder.resourceTypes
        val newResourceFilters = resourceFilters?.normalized() ?: existingOrder.resourceFilters
        val newExpandDistributionAccessServices = expandDistributionAccessServices ?: existingOrder.expandDistributionAccessServices
        val newFormat = format ?: existingOrder.format
        val newStyle = style ?: existingOrder.style
        val newExpandUris = expandUris ?: existingOrder.expandUris
        val newName = name ?: existingOrder.name // name is required, so if not provided, keep existing
        val newDescription = description ?: existingOrder.description

        // Validate resource filters if provided
        validateResourceFilters(newResourceTypes?.map { ResourceType.valueOf(it) }, newResourceFilters)

        val previousStatus = existingOrder.status

        // Update the order
        val updatedOrder =
            unionGraphOrderRepository.updateOrder(
                id = id,
                updateTtlHours = newUpdateTtlHours,
                webhookUrl = newWebhookUrl,
                resourceTypes = newResourceTypes,
                resourceFilters = newResourceFilters?.let { objectMapper.writeValueAsString(it) },
                expandDistributionAccessServices = newExpandDistributionAccessServices,
                format = newFormat.name,
                style = newStyle.name,
                expandUris = newExpandUris,
                name = newName,
                description = newDescription,
                resetToPending = requiresRebuild,
            )

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
     * Builds a union graph from resource graphs using pagination to prevent memory exhaustion.
     *
     * This method:
     * 1. Retrieves resources of the specified types in batches (or all types if none specified)
     * 2. Applies resource type-specific filters if provided (e.g., dataset filters)
     * 3. Merges their JSON-LD graphs into a single union graph incrementally
     * 4. Returns the merged graph as a Map<String, Any>
     *
     * Resources are processed in batches to avoid loading all resources into memory at once,
     * which is important for memory-intensive graph operations.
     *
     * @param resourceTypes Optional list of resource types to include. If null or empty, all types are included.
     * @param resourceFilters Optional per-resource-type filters to apply when collecting resources.
     *                        For example, dataset filters can filter by isOpenData and isRelatedToTransportportal.
     *                        Only resources matching the filters will be included in the union graph.
     * @param expandDistributionAccessServices If true, datasets with distributions that reference DataService URIs
     *                                          (via distribution[].accessService[].uri) will have those DataService
     *                                          graphs automatically included in the union graph.
     * @return The merged union graph as a Jena Model, or null if no resources found.
     */
    fun buildUnionGraph(
        resourceTypes: List<ResourceType>? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean = false,
        orderId: String? = null, // Optional order ID for progress tracking
    ): org.apache.jena.rdf.model.Model? {
        logger.info(
            "Building union graph for resource types: {}, expandDistributionAccessServices: {}",
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
            return null
        }

        logger.info(
            "Found {} total resources to merge (processing in batches of {})",
            totalResources,
            unionGraphConfig.resourceBatchSize,
        )

        // Merge all JSON-LD graphs using Apache Jena
        val unionModel = ModelFactory.createDefaultModel()
        var processedCount = 0L
        val batchSize = unionGraphConfig.resourceBatchSize
        val progressUpdateInterval = UnionGraphConfig.UNION_GRAPH_PROGRESS_UPDATE_INTERVAL
        // Track expanded DataService URIs to avoid duplicates (thread-safe)
        val expandedDataServiceUris = java.util.Collections.synchronizedSet(mutableSetOf<String>())
        // Synchronization object for thread-safe model merging
        val modelLock = Any()

        try {
            // Process each resource type
            for (type in typesToProcess) {
                var offset = 0
                var hasMore = true

                // Process resources in batches
                while (hasMore) {
                    val batch =
                        when {
                            type == ResourceType.DATASET && datasetFilters != null ->
                                resourceRepository.findDatasetsByFiltersPaginated(
                                    offset,
                                    batchSize,
                                    datasetFilters.isOpenData.toSqlBooleanText(),
                                    datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                                )
                            else ->
                                resourceRepository.findByResourceTypeAndDeletedFalsePaginated(
                                    type.name,
                                    offset,
                                    batchSize,
                                )
                        }

                    if (batch.isEmpty()) {
                        hasMore = false
                    } else {
                        // Process resources in parallel, but batch the merges to reduce lock contention
                        // Use limited parallelism (4-8 threads) to reduce contention
                        val parallelism = minOf(8, batch.size, Runtime.getRuntime().availableProcessors())
                        val executor = Executors.newFixedThreadPool(parallelism)
                        val modelsToMerge = java.util.Collections.synchronizedList(mutableListOf<org.apache.jena.rdf.model.Model>())
                        val batchProcessedCount = AtomicLong(0)

                        try {
                            // Process all resources in parallel, collecting models
                            val futures =
                                batch.map { resource ->
                                    executor.submit {
                                        val jsonLd = resource.resourceJsonLd
                                        if (jsonLd != null) {
                                            try {
                                                // Convert JSON-LD Map to JSON string
                                                val jsonString = objectMapper.writeValueAsString(jsonLd)

                                                // Parse JSON-LD into Jena Model
                                                val resourceModel = ModelFactory.createDefaultModel()
                                                ByteArrayInputStream(jsonString.toByteArray()).use { inputStream ->
                                                    RDFDataMgr.read(resourceModel, inputStream, Lang.JSONLD)
                                                }

                                                // Collect model for batch merge (no lock contention here)
                                                modelsToMerge.add(resourceModel)
                                                batchProcessedCount.incrementAndGet()

                                                // If expanding distribution access services and this is a dataset,
                                                // extract DataService URIs (will be processed after batch)
                                                if (expandDistributionAccessServices && type == ResourceType.DATASET) {
                                                    val datasetJson = resource.resourceJson
                                                    if (datasetJson != null) {
                                                        // Extract URIs (thread-safe set)
                                                        extractDataServiceUris(datasetJson, expandedDataServiceUris)
                                                    }
                                                }
                                            } catch (e: Exception) {
                                                logger.warn("Failed to process resource {}: {}", resource.id, e.message)
                                            }
                                        }
                                    }
                                }

                            // Wait for all processing to complete
                            futures.forEach { it.get() }

                            // Merge all models in a single synchronized block (much faster than individual merges)
                            synchronized(modelLock) {
                                for (model in modelsToMerge) {
                                    unionModel.add(model)
                                    model.close()
                                }
                            }
                        } finally {
                            executor.shutdown()
                            executor.awaitTermination(60, TimeUnit.SECONDS)
                        }

                        processedCount += batchProcessedCount.get()

                        // Update progress metrics at intervals to reduce overhead
                        if (processedCount % progressUpdateInterval == 0L || batch.size < batchSize) {
                            if (orderId != null) {
                                metricsService.updateProcessingProgress(orderId, processedCount)
                            }
                            metricsService.recordResourcesProcessed(batchProcessedCount.get())
                        }

                        // Check if we've processed all resources for this type
                        if (batch.size < batchSize) {
                            hasMore = false
                        } else {
                            offset += batchSize
                        }

                        // Log progress periodically
                        if (processedCount % (batchSize * 5) == 0L) {
                            logger.debug("Processed {} resources so far...", processedCount)
                        }
                    }
                }
            }

            // Process expanded DataService graphs after all resources are merged
            // This is done sequentially to avoid excessive parallel database queries
            if (expandDistributionAccessServices && expandedDataServiceUris.isNotEmpty()) {
                logger.info("Processing {} expanded DataService graph(s)...", expandedDataServiceUris.size)
                for (uri in expandedDataServiceUris) {
                    try {
                        val dataServiceJsonLd = resourceService.getResourceJsonLdByUri(uri, ResourceType.DATA_SERVICE)
                        if (dataServiceJsonLd != null) {
                            // Convert JSON-LD Map to JSON string
                            val jsonString = objectMapper.writeValueAsString(dataServiceJsonLd)

                            // Parse JSON-LD into Jena Model
                            val dataServiceModel = ModelFactory.createDefaultModel()
                            ByteArrayInputStream(jsonString.toByteArray()).use { inputStream ->
                                RDFDataMgr.read(dataServiceModel, inputStream, Lang.JSONLD)
                            }

                            // Merge into union model (synchronized for consistency, though sequential here)
                            synchronized(modelLock) {
                                unionModel.add(dataServiceModel)
                            }
                            dataServiceModel.close()
                            logger.debug("Added DataService graph for URI: {}", uri)
                        } else {
                            logger.debug("DataService not found for URI: {}", uri)
                        }
                    } catch (e: Exception) {
                        logger.warn("Failed to add DataService graph for URI {}: {}", uri, e.message)
                        // Continue with other URIs
                    }
                }
            }

            if (unionModel.isEmpty) {
                logger.warn("Union graph is empty after merging")
                return null
            }

            if (expandDistributionAccessServices && expandedDataServiceUris.isNotEmpty()) {
                logger.info(
                    "Expanded {} DataService graph(s) from dataset distribution accessService references",
                    expandedDataServiceUris.size,
                )
                metricsService.recordDataServicesExpanded(expandedDataServiceUris.size)
            }

            logger.info("Processed {} resources, building final union graph...", processedCount)

            logger.info("Successfully built union graph with {} statements from {} resources", unionModel.size(), processedCount)
            // Return the model directly - caller will convert to desired format
            return unionModel
        } catch (e: Exception) {
            logger.error("Failed to build union graph", e)
            unionModel.close()
            return null
        }
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
                    is Map<*, *> -> listOf(distValue as Map<String, Any>)
                    else -> emptyList()
                }

            // Extract DataService URIs from distributions
            for (distribution in distributions) {
                val accessServices =
                    when (val accessServiceValue = distribution["accessService"]) {
                        is List<*> -> accessServiceValue.filterIsInstance<Map<String, Any>>()
                        is Map<*, *> -> listOf(accessServiceValue as Map<String, Any>)
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

            val processingStartTime = System.currentTimeMillis()

            // Start tracking progress
            val totalResources = calculateTotalResources(resourceTypes, lockedOrder.resourceFilters)
            metricsService.startProcessingProgress(lockedOrder.id, totalResources)

            val unionModel =
                buildUnionGraph(
                    resourceTypes,
                    lockedOrder.resourceFilters,
                    lockedOrder.expandDistributionAccessServices,
                    lockedOrder.id, // Pass order ID for progress tracking
                )

            val previousStatus = lockedOrder.status

            if (unionModel != null) {
                try {
                    // Convert the model directly to the requested format
                    val rdfFormat =
                        when (lockedOrder.format) {
                            UnionGraphOrder.GraphFormat.JSON_LD -> RdfService.RdfFormat.JSON_LD
                            UnionGraphOrder.GraphFormat.TURTLE -> RdfService.RdfFormat.TURTLE
                            UnionGraphOrder.GraphFormat.RDF_XML -> RdfService.RdfFormat.RDF_XML
                            UnionGraphOrder.GraphFormat.N_TRIPLES -> RdfService.RdfFormat.N_TRIPLES
                            UnionGraphOrder.GraphFormat.N_QUADS -> RdfService.RdfFormat.N_QUADS
                        }
                    val rdfStyle =
                        when (lockedOrder.style) {
                            UnionGraphOrder.GraphStyle.PRETTY -> RdfService.RdfFormatStyle.PRETTY
                            UnionGraphOrder.GraphStyle.STANDARD -> RdfService.RdfFormatStyle.STANDARD
                        }

                    val graphData =
                        rdfService.convertFromModel(
                            unionModel,
                            rdfFormat,
                            rdfStyle,
                            lockedOrder.expandUris,
                            null, // No specific resource type for union graphs
                        ) ?: throw IllegalStateException("Failed to convert union graph to requested format")

                    // Mark as completed
                    unionGraphOrderRepository.markAsCompleted(
                        lockedOrder.id,
                        graphData,
                        lockedOrder.format.name,
                        lockedOrder.style.name,
                        lockedOrder.expandUris,
                    )

                    // Record successful completion
                    val processingDuration = (System.currentTimeMillis() - processingStartTime) / 1000.0
                    metricsService.recordProcessingDuration(processingDuration)
                    metricsService.recordOrderCompleted()
                    metricsService.stopProcessingProgress(lockedOrder.id)
                } finally {
                    // Always close the model to free resources
                    unionModel.close()
                }
                logger.info("Successfully processed union graph order {}", lockedOrder.id)

                // Call webhook if configured
                val updatedOrder = unionGraphOrderRepository.findById(lockedOrder.id).orElse(null)
                if (updatedOrder != null) {
                    val webhookStartTime = System.currentTimeMillis()
                    val webhookSuccess =
                        try {
                            webhookService.callWebhook(updatedOrder, previousStatus)
                            true
                        } catch (e: Exception) {
                            false
                        }
                    val webhookDuration = (System.currentTimeMillis() - webhookStartTime) / 1000.0
                    metricsService.recordWebhookCall(webhookDuration, webhookSuccess)
                }
            } else {
                // Mark as failed if no graph was built
                unionGraphOrderRepository.markAsFailed(
                    lockedOrder.id,
                    "No resources found or failed to build union graph",
                )
                metricsService.recordOrderFailed("no_resources")
                metricsService.stopProcessingProgress(lockedOrder.id)
                logger.warn("Failed to build union graph for order {}", lockedOrder.id)

                // Call webhook if configured
                val updatedOrder = unionGraphOrderRepository.findById(lockedOrder.id).orElse(null)
                if (updatedOrder != null) {
                    webhookService.callWebhook(updatedOrder, previousStatus)
                }
            }
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
