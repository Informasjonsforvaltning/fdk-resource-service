package no.fdk.resourceservice.service

import com.fasterxml.jackson.core.type.TypeReference
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
) {
    private val logger = LoggerFactory.getLogger(UnionGraphService::class.java)

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
     * @param updateTtlHours Time to live in hours for automatic updates. 0 means never update automatically.
     * @param webhookUrl Optional webhook URL to call when union graph status changes. Must be HTTPS if provided.
     * @param resourceFilters Optional per-resource-type filters to apply when building the graph.
     *                        For example, dataset filters can filter by isOpenData and isRelatedToTransportportal.
     *                        Filters are part of the union graph configuration, so union graphs with different filters
     *                        are considered different union graphs.
     * @param expandDistributionAccessServices If true, datasets with distributions that reference DataService URIs
     *                                          will have those DataService graphs automatically included in the union graph.
     *                                          This allows creating union graphs that include both datasets and their related
     *                                          data services in a single graph.
     * @return CreateOrderResult containing the union graph and a flag indicating if it's new or existing.
     * @throws IllegalArgumentException if webhookUrl is provided but not HTTPS, or if filters are invalid
     */
    fun createOrder(
        resourceTypes: List<ResourceType>? = null,
        updateTtlHours: Int = 0,
        webhookUrl: String? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean = false,
    ): CreateOrderResult {
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
     * @return The merged union graph as JSON-LD Map, or null if no resources found.
     */
    fun buildUnionGraph(
        resourceTypes: List<ResourceType>? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean = false,
    ): Map<String, Any>? {
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
            UnionGraphConfig.UNION_GRAPH_RESOURCE_BATCH_SIZE,
        )

        // Merge all JSON-LD graphs using Apache Jena
        val unionModel = ModelFactory.createDefaultModel()
        var processedCount = 0L
        val batchSize = UnionGraphConfig.UNION_GRAPH_RESOURCE_BATCH_SIZE
        // Track expanded DataService URIs to avoid duplicates
        val expandedDataServiceUris = mutableSetOf<String>()

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
                        // Process each resource in the batch
                        for (resource in batch) {
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

                                    // Merge into union model
                                    unionModel.add(resourceModel)
                                    resourceModel.close()
                                    processedCount++

                                    // If expanding distribution access services and this is a dataset,
                                    // extract DataService URIs and add their graphs
                                    if (expandDistributionAccessServices && type == ResourceType.DATASET) {
                                        extractAndAddDataServices(jsonLd, unionModel, expandedDataServiceUris)
                                    }
                                } catch (e: Exception) {
                                    logger.warn("Failed to merge resource {}: {}", resource.id, e.message)
                                    // Continue with other resources
                                }
                            }
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

            if (unionModel.isEmpty) {
                logger.warn("Union graph is empty after merging")
                return null
            }

            if (expandDistributionAccessServices && expandedDataServiceUris.isNotEmpty()) {
                logger.info(
                    "Expanded {} DataService graph(s) from dataset distribution accessService references",
                    expandedDataServiceUris.size,
                )
            }

            logger.info("Processed {} resources, building final union graph...", processedCount)

            // Convert merged model back to JSON-LD Map
            val jsonLdString =
                StringWriter().use { writer ->
                    RDFDataMgr.write(writer, unionModel, org.apache.jena.riot.RDFFormat.JSONLD_PRETTY)
                    writer.toString()
                }

            val jsonLdMap =
                objectMapper.readValue(
                    jsonLdString,
                    object : TypeReference<Map<String, Any>>() {},
                )

            logger.info("Successfully built union graph with {} statements from {} resources", unionModel.size(), processedCount)
            return jsonLdMap
        } catch (e: Exception) {
            logger.error("Failed to build union graph", e)
            return null
        } finally {
            unionModel.close()
        }
    }

    /**
     * Extracts DataService URIs from a dataset JSON-LD and adds their graphs to the union model.
     *
     * Follows the path: distribution[*].accessService[*].uri
     * Only adds DataServices that haven't been expanded yet to avoid duplicates.
     *
     * @param datasetJsonLd The dataset JSON-LD map
     * @param unionModel The union model to add DataService graphs to
     * @param expandedUris Set of URIs that have already been expanded (modified in place)
     */
    private fun extractAndAddDataServices(
        datasetJsonLd: Map<String, Any>,
        unionModel: org.apache.jena.rdf.model.Model,
        expandedUris: MutableSet<String>,
    ) {
        try {
            // Extract distributions from the dataset
            val distributions =
                when (val distValue = datasetJsonLd["distribution"]) {
                    is List<*> -> distValue.filterIsInstance<Map<String, Any>>()
                    is Map<*, *> -> listOf(distValue as Map<String, Any>)
                    else -> emptyList()
                }

            // Extract DataService URIs from distributions
            val dataServiceUris = mutableSetOf<String>()
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
                                dataServiceUris.add(uriValue)
                            }
                        }
                        is List<*> -> {
                            uriValue
                                .filterIsInstance<String>()
                                .filter { it.isNotBlank() }
                                .forEach { dataServiceUris.add(it) }
                        }
                    }
                }
            }

            // Add DataService graphs for URIs that haven't been expanded yet
            for (uri in dataServiceUris) {
                if (uri !in expandedUris) {
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

                            // Merge into union model
                            unionModel.add(dataServiceModel)
                            dataServiceModel.close()
                            expandedUris.add(uri)
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
     * Processes a union graph by building the union graph and updating it.
     *
     * @param order The union graph to process.
     * @param instanceId The identifier of the instance processing this union graph.
     */
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

            // Fetch the order fresh to get the updated status (PROCESSING) after the lock transaction commits
            // This ensures the status update is visible and committed before starting the long-running graph build
            val lockedOrder = unionGraphOrderRepository.findById(order.id).orElse(null)
            if (lockedOrder == null) {
                logger.warn("Order {} not found after locking", order.id)
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

            val unionGraph = buildUnionGraph(resourceTypes, lockedOrder.resourceFilters, lockedOrder.expandDistributionAccessServices)

            val previousStatus = lockedOrder.status

            if (unionGraph != null) {
                // Convert to JSON string for database storage
                val graphJsonLdString = objectMapper.writeValueAsString(unionGraph)

                // Mark as completed
                unionGraphOrderRepository.markAsCompleted(lockedOrder.id, graphJsonLdString)
                logger.info("Successfully processed union graph order {}", lockedOrder.id)

                // Call webhook if configured
                val updatedOrder = unionGraphOrderRepository.findById(lockedOrder.id).orElse(null)
                if (updatedOrder != null) {
                    webhookService.callWebhook(updatedOrder, previousStatus)
                }
            } else {
                // Mark as failed if no graph was built
                unionGraphOrderRepository.markAsFailed(
                    lockedOrder.id,
                    "No resources found or failed to build union graph",
                )
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
