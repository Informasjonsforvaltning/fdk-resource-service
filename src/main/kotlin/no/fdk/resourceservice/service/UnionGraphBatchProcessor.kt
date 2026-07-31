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
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional

/**
 * Processes union graph orders: kicks off processing, drives incremental batch
 * processing, and tracks processing state for a union graph order.
 */
@Service
class UnionGraphBatchProcessor(
    private val unionGraphOrderRepository: UnionGraphOrderRepository,
    private val resourceRepository: ResourceRepository,
    private val objectMapper: ObjectMapper,
    private val webhookService: WebhookService,
    private val metricsService: UnionGraphMetricsService,
    private val unionGraphConfig: UnionGraphConfig,
    private val unionGraphResourceSnapshotRepository: UnionGraphResourceSnapshotRepository,
    private val snapshotBuilder: UnionGraphSnapshotBuilder,
    private val orderService: UnionGraphOrderService,
) {
    private val logger = LoggerFactory.getLogger(UnionGraphBatchProcessor::class.java)

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
            val locked = orderService.lockOrderInNewTransaction(order.id, instanceId)
            if (!locked) {
                logger.warn("Failed to lock order {} for processing (may have been locked by another instance)", order.id)
                return
            }

            // Fetch the order fresh in a NEW transaction to get the updated status (PROCESSING) after the lock commits
            // Using REQUIRES_NEW ensures we see the committed state, not a cached entity from the current transaction
            val lockedOrder = orderService.getOrderInNewTransaction(order.id)
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

            // Initialize processing state; scheduler will process batches incrementally
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
            val resourceIds = order.resourceIds
            val resourceIdsString =
                if (resourceIds.isNullOrEmpty()) {
                    null
                } else {
                    "{" + resourceIds.sorted().joinToString(",") + "}"
                }
            val resourceUris = order.resourceUris
            val resourceUrisString =
                if (resourceUris.isNullOrEmpty()) {
                    null
                } else {
                    "{" + resourceUris.sorted().joinToString(",") + "}"
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
                    currentResourceType == ResourceType.DATASET && datasetFilters != null -> {
                        resourceRepository.findDatasetsByFiltersWithGraphDataPaginatedWithFilters(
                            state.currentOffset,
                            unionGraphConfig.resourceBatchSize,
                            datasetFilters.isOpenData.toSqlBooleanText(),
                            datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                            resourceIdsString,
                            resourceUrisString,
                        )
                    }

                    else -> {
                        resourceRepository.findByResourceTypeAndDeletedFalseWithGraphDataPaginatedWithFilters(
                            currentResourceType.name,
                            state.currentOffset,
                            unionGraphConfig.resourceBatchSize,
                            resourceIdsString,
                            resourceUrisString,
                        )
                    }
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
            val batchStartNanos = System.nanoTime()
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
                when (
                    val result =
                        snapshotBuilder.createSnapshotFromResource(
                            orderId = orderId,
                            resource = resource,
                            resourceType = currentResourceType,
                            datasetFilters = datasetFilters,
                            expandDistributionAccessServices = order.expandDistributionAccessServices,
                            includeCatalog = order.includeCatalog,
                        )
                ) {
                    is UnionGraphSnapshotBuilder.SnapshotResult.Included -> snapshotsToSave.add(result.snapshot)
                    is UnionGraphSnapshotBuilder.SnapshotResult.Skipped -> skippedCount++
                    is UnionGraphSnapshotBuilder.SnapshotResult.Error -> errorCount++
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
                metricsService.recordSnapshotBytes(
                    snapshotsToSave.sumOf { it.resourceGraphData.length.toLong() },
                )
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
            metricsService.recordBatchDuration((System.nanoTime() - batchStartNanos) / 1_000_000_000.0)

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
                    type == ResourceType.DATASET && datasetFilters != null -> {
                        resourceRepository.countDatasetsByFiltersWithFilters(
                            datasetFilters.isOpenData.toSqlBooleanText(),
                            datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                            resourceIdsString,
                            resourceUrisString,
                        )
                    }

                    else -> {
                        resourceRepository.countByResourceTypeAndDeletedFalseWithFilters(
                            type.name,
                            resourceIdsString,
                            resourceUrisString,
                        )
                    }
                }
        }
        return total
    }
}
