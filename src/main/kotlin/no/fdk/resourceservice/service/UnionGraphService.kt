package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.config.UnionGraphConfig
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.StringWriter
import java.util.UUID

/**
 * Service for managing union graph orders and building union graphs.
 *
 * Union graphs are built from multiple resource graphs by combining them.
 * The service handles the asynchronous building of these graphs.
 */
@Service
@Transactional
class UnionGraphService(
    private val unionGraphOrderRepository: UnionGraphOrderRepository,
    private val resourceRepository: ResourceRepository,
    private val objectMapper: ObjectMapper,
    private val webhookService: WebhookService,
) {
    private val logger = LoggerFactory.getLogger(UnionGraphService::class.java)

    /**
     * Result of creating an order, indicating whether it's new or existing.
     */
    data class CreateOrderResult(
        val order: UnionGraphOrder,
        val isNew: Boolean,
    )

    /**
     * Creates a new union graph order, or returns an existing order if one already exists.
     *
     * This method prevents duplicate graph building by checking if an order
     * with the same resource types and update TTL already exists (any status).
     * If found, the existing order is returned. Otherwise, a new order is created.
     *
     * @param resourceTypes Optional list of resource types to include. If null or empty, all types are included.
     * @param updateTtlHours Time to live in hours for automatic updates. 0 means never update automatically.
     * @param webhookUrl Optional webhook URL to call when order status changes. Must be HTTPS if provided.
     * @return CreateOrderResult containing the order and a flag indicating if it's new or existing.
     * @throws IllegalArgumentException if webhookUrl is provided but not HTTPS
     */
    fun createOrder(
        resourceTypes: List<ResourceType>? = null,
        updateTtlHours: Int = 0,
        webhookUrl: String? = null,
    ): CreateOrderResult {
        // Validate webhook URL if provided
        if (!webhookUrl.isNullOrBlank() && !webhookUrl.startsWith("https://")) {
            throw IllegalArgumentException("Webhook URL must use HTTPS protocol")
        }

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
        val existingOrder =
            unionGraphOrderRepository.findByConfiguration(
                resourceTypesString,
                updateTtlHours,
                webhookUrl,
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
            )

        val saved = unionGraphOrderRepository.save(order)
        logger.info("Created union graph order with id: {}", saved.id)
        return CreateOrderResult(order = saved, isNew = true)
    }

    /**
     * Resets an order to PENDING status for retry.
     * Clears error messages and releases any locks.
     *
     * @param id The order ID to reset
     * @return The reset order, or null if not found
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
     * Gets a union graph order by ID.
     */
    fun getOrder(id: String): UnionGraphOrder? = unionGraphOrderRepository.findById(id).orElse(null)

    /**
     * Gets all union graph orders without the graph data.
     * Orders are returned sorted by creation date (newest first).
     *
     * @return List of union graph orders (without graph_json_ld field)
     */
    fun getAllOrders(): List<UnionGraphOrder> {
        logger.debug("Getting all union graph orders")
        return unionGraphOrderRepository.findAllByOrderByCreatedAtDesc()
    }

    /**
     * Deletes a union graph order.
     *
     * @param id The order ID to delete
     * @return true if the order was deleted, false if not found
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
     * 2. Merges their JSON-LD graphs into a single union graph incrementally
     * 3. Returns the merged graph as a Map<String, Any>
     *
     * Resources are processed in batches to avoid loading all resources into memory at once,
     * which is important for memory-intensive graph operations.
     *
     * @param resourceTypes Optional list of resource types to include. If null or empty, all types are included.
     * @return The merged union graph as JSON-LD Map, or null if no resources found.
     */
    fun buildUnionGraph(resourceTypes: List<ResourceType>? = null): Map<String, Any>? {
        logger.info("Building union graph for resource types: {}", resourceTypes)

        // Determine which resource types to process
        val typesToProcess = resourceTypes?.ifEmpty { null } ?: ResourceType.entries

        // Count total resources to process
        var totalResources = 0L
        for (type in typesToProcess) {
            totalResources += resourceRepository.countByResourceTypeAndDeletedFalse(type.name)
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

        try {
            // Process each resource type
            for (type in typesToProcess) {
                var offset = 0
                var hasMore = true

                // Process resources in batches
                while (hasMore) {
                    val batch =
                        resourceRepository.findByResourceTypeAndDeletedFalsePaginated(
                            type.name,
                            offset,
                            batchSize,
                        )

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

            logger.info("Processed {} resources, building final union graph...", processedCount)

            // Convert merged model back to JSON-LD Map
            val jsonLdString =
                StringWriter().use { writer ->
                    RDFDataMgr.write(writer, unionModel, org.apache.jena.riot.RDFFormat.JSONLD_PRETTY)
                    writer.toString()
                }

            @Suppress("UNCHECKED_CAST")
            val jsonLdMap = objectMapper.readValue(jsonLdString, Map::class.java) as Map<String, Any>

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
     * Processes a union graph order by building the union graph and updating the order.
     *
     * @param order The order to process.
     * @param instanceId The identifier of the instance processing this order.
     */
    fun processOrder(
        order: UnionGraphOrder,
        instanceId: String,
    ) {
        logger.info("Processing union graph order {} by instance {}", order.id, instanceId)

        try {
            // Lock the order for processing
            val locked = unionGraphOrderRepository.lockOrderForProcessing(order.id, instanceId)
            if (locked == 0) {
                logger.warn("Failed to lock order {} for processing (may have been locked by another instance)", order.id)
                return
            }

            // Build the union graph
            val resourceTypes =
                order.resourceTypes?.mapNotNull { typeName ->
                    try {
                        ResourceType.valueOf(typeName)
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Unknown resource type: {}", typeName)
                        null
                    }
                }

            val unionGraph = buildUnionGraph(resourceTypes)

            val previousStatus = order.status

            if (unionGraph != null) {
                // Convert to JSON string for database storage
                val graphJsonLdString = objectMapper.writeValueAsString(unionGraph)

                // Mark as completed
                unionGraphOrderRepository.markAsCompleted(order.id, graphJsonLdString)
                logger.info("Successfully processed union graph order {}", order.id)

                // Call webhook if configured
                val updatedOrder = unionGraphOrderRepository.findById(order.id).orElse(null)
                if (updatedOrder != null) {
                    webhookService.callWebhook(updatedOrder, previousStatus)
                }
            } else {
                // Mark as failed if no graph was built
                unionGraphOrderRepository.markAsFailed(
                    order.id,
                    "No resources found or failed to build union graph",
                )
                logger.warn("Failed to build union graph for order {}", order.id)

                // Call webhook if configured
                val updatedOrder = unionGraphOrderRepository.findById(order.id).orElse(null)
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
}
