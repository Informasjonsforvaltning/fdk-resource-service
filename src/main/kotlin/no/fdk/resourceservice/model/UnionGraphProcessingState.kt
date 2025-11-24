package no.fdk.resourceservice.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * State tracking for incremental batch processing of union graphs.
 *
 * This state is persisted in the database to allow resuming processing
 * after each batch is completed, reducing memory consumption by processing
 * one batch at a time instead of all resources in a single operation.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UnionGraphProcessingState(
    /**
     * Index of the current resource type being processed.
     * Resource types are processed in order.
     */
    val currentResourceTypeIndex: Int = 0,
    /**
     * Current offset for the current resource type.
     * Used for pagination when fetching resources.
     */
    val currentOffset: Int = 0,
    /**
     * Total number of resources processed so far.
     */
    val processedCount: Long = 0,
    /**
     * Total number of statements across all processed resources.
     */
    val totalStatements: Long = 0,
) {
    /**
     * Checks if processing is complete (all resource types processed).
     */
    fun isComplete(totalResourceTypes: Int): Boolean = currentResourceTypeIndex >= totalResourceTypes
}
