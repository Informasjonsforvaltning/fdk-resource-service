package no.fdk.resourceservice.repository

import no.fdk.resourceservice.model.UnionGraphOrder
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Repository
interface UnionGraphOrderRepository : JpaRepository<UnionGraphOrder, String> {
    /**
     * Finds the next pending order to process, using pessimistic locking
     * to ensure only one instance processes it in a distributed environment.
     *
     * Note: FOR UPDATE SKIP LOCKED is used directly in the SQL query since
     * @Lock annotation doesn't work with native queries.
     */
    @Query(
        value = """
        SELECT * FROM union_graphs 
        WHERE status = 'PENDING' 
        AND (locked_by IS NULL OR locked_at < :lockTimeout)
        ORDER BY created_at ASC
        LIMIT 1
        FOR UPDATE SKIP LOCKED
    """,
        nativeQuery = true,
    )
    fun findNextPendingOrder(
        @Param("lockTimeout") lockTimeout: Instant,
    ): UnionGraphOrder?

    /**
     * Finds multiple pending orders to process, using pessimistic locking.
     * Used for batch processing while respecting memory constraints.
     *
     * Note: FOR UPDATE SKIP LOCKED is used directly in the SQL query since
     * @Lock annotation doesn't work with native queries.
     *
     * @param lockTimeout Orders with locks older than this will be considered available
     * @param limit Maximum number of orders to retrieve
     * @return List of pending orders ready for processing
     */
    @Query(
        value = """
        SELECT * FROM union_graphs 
        WHERE status = 'PENDING' 
        AND (locked_by IS NULL OR locked_at < :lockTimeout)
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
    """,
        nativeQuery = true,
    )
    fun findNextPendingOrders(
        @Param("lockTimeout") lockTimeout: Instant,
        @Param("limit") limit: Int,
    ): List<UnionGraphOrder>

    /**
     * Locks an order for processing by a specific instance.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        UPDATE union_graphs 
        SET status = 'PROCESSING', 
            locked_by = :lockedBy, 
            locked_at = CURRENT_TIMESTAMP,
            processing_started_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id AND status = 'PENDING'
    """,
        nativeQuery = true,
    )
    fun lockOrderForProcessing(
        @Param("id") id: String,
        @Param("lockedBy") lockedBy: String,
    ): Int

    /**
     * Updates the order with the completed graph.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        UPDATE union_graphs 
        SET status = 'COMPLETED', 
            graph_data = :graphData,
            format = :format,
            style = :style,
            expand_uris = :expandUris,
            processed_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun markAsCompleted(
        @Param("id") id: String,
        @Param("graphData") graphData: String,
        @Param("format") format: String,
        @Param("style") style: String,
        @Param("expandUris") expandUris: Boolean,
    ): Int

    /**
     * Marks the order as failed with an error message.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        UPDATE union_graphs 
        SET status = 'FAILED', 
            error_message = :errorMessage,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun markAsFailed(
        @Param("id") id: String,
        @Param("errorMessage") errorMessage: String,
    ): Int

    /**
     * Releases the lock on an order (for cleanup or retry).
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        UPDATE union_graphs 
        SET locked_by = NULL, 
            locked_at = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun releaseLock(
        @Param("id") id: String,
    ): Int

    /**
     * Resets the status of an order to PENDING (for retry after stale lock cleanup or failed order retry).
     * Also clears the error message and releases any locks.
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        UPDATE union_graphs 
        SET status = 'PENDING',
            error_message = NULL,
            locked_by = NULL,
            locked_at = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun resetToPending(
        @Param("id") id: String,
    ): Int

    /**
     * Finds orders by status.
     */
    fun findByStatus(status: UnionGraphOrder.GraphStatus): List<UnionGraphOrder>

    /**
     * Finds all orders ordered by creation date (newest first).
     * Used for listing all orders without loading graph data.
     */
    fun findAllByOrderByCreatedAtDesc(): List<UnionGraphOrder>

    /**
     * Counts orders by status.
     * Used for metrics.
     */
    @Query("SELECT COUNT(o) FROM UnionGraphOrder o WHERE o.status = :status")
    fun countByStatus(
        @Param("status") status: UnionGraphOrder.GraphStatus,
    ): Long

    /**
     * Finds an order by configuration (resource types, update TTL, and webhook URL).
     * Used to check if an order with the same configuration already exists.
     *
     * @param resourceTypes Array of resource type names (null or empty means all types)
     * @param updateTtlHours Time to live in hours for automatic updates
     * @param webhookUrl Webhook URL (null if not provided)
     * @return The order if found (preferring COMPLETED, then by most recent), null otherwise
     */
    @Query(
        value = """
        SELECT * FROM union_graphs 
        WHERE (
            CASE
                WHEN CAST(:resourceTypes AS text) IS NULL OR CAST(:resourceTypes AS text) = '' THEN resource_types IS NULL
                ELSE resource_types = CAST(:resourceTypes AS text[])
            END
        )
        AND update_ttl_hours = :updateTtlHours
        AND (
            CASE
                WHEN :webhookUrl IS NULL THEN webhook_url IS NULL
                ELSE webhook_url = :webhookUrl
            END
        )
        AND (
            CASE
                WHEN :resourceFilters IS NULL THEN resource_filters IS NULL
                ELSE resource_filters = CAST(:resourceFilters AS jsonb)
            END
        )
        AND expand_distribution_access_services = :expandDistributionAccessServices
        AND format = :format
        AND style = :style
        AND expand_uris = :expandUris
        ORDER BY 
            CASE status 
                WHEN 'COMPLETED' THEN 1
                WHEN 'PROCESSING' THEN 2
                WHEN 'PENDING' THEN 3
                WHEN 'FAILED' THEN 4
            END,
            updated_at DESC
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findByConfiguration(
        @Param("resourceTypes") resourceTypes: String?,
        @Param("updateTtlHours") updateTtlHours: Int,
        @Param("webhookUrl") webhookUrl: String?,
        @Param("resourceFilters") resourceFilters: String?,
        @Param("expandDistributionAccessServices") expandDistributionAccessServices: Boolean,
        @Param("format") format: String,
        @Param("style") style: String,
        @Param("expandUris") expandUris: Boolean,
    ): UnionGraphOrder?

    /**
     * Updates an order with new configuration values.
     * If resetToPending is true, the order status is reset to PENDING and graph data is cleared.
     *
     * @param id The order ID to update
     * @param updateTtlHours New TTL in hours
     * @param webhookUrl New webhook URL (null to remove)
     * @param resourceTypes New resource types array (null means all types)
     * @param resourceFilters New resource filters JSON (null to remove)
     * @param expandDistributionAccessServices New expansion setting
     * @param format New format
     * @param style New style
     * @param expandUris New expand URIs setting
     * @param name New name for the union graph
     * @param description New description for the union graph
     * @param resetToPending If true, reset status to PENDING and clear graph data
     * @return Number of rows affected (0 if order not found, 1 if updated)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        UPDATE union_graphs 
        SET update_ttl_hours = :updateTtlHours,
            webhook_url = :webhookUrl,
            resource_types = CAST(:resourceTypes AS text[]),
            resource_filters = CASE 
                WHEN :resourceFilters IS NULL THEN NULL 
                ELSE CAST(:resourceFilters AS jsonb) 
            END,
            expand_distribution_access_services = :expandDistributionAccessServices,
            format = :format,
            style = :style,
            expand_uris = :expandUris,
            name = :name,
            description = :description,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun updateOrderWithoutReset(
        @Param("id") id: String,
        @Param("updateTtlHours") updateTtlHours: Int,
        @Param("webhookUrl") webhookUrl: String?,
        @Param("resourceTypes") resourceTypes: List<String>?,
        @Param("resourceFilters") resourceFilters: String?,
        @Param("expandDistributionAccessServices") expandDistributionAccessServices: Boolean,
        @Param("format") format: String,
        @Param("style") style: String,
        @Param("expandUris") expandUris: Boolean,
        @Param("name") name: String?,
        @Param("description") description: String?,
    ): Int

    /**
     * Updates an order with new configuration values and resets to PENDING status.
     * This clears graph data and releases any locks.
     *
     * @param id The order ID to update
     * @param updateTtlHours New TTL in hours
     * @param webhookUrl New webhook URL (null to remove)
     * @param resourceTypes New resource types array (null means all types)
     * @param resourceFilters New resource filters JSON (null to remove)
     * @param expandDistributionAccessServices New expansion setting
     * @param format New format
     * @param style New style
     * @param expandUris New expand URIs setting
     * @param name New name for the union graph
     * @param description New description for the union graph
     * @return Number of rows affected (0 if order not found, 1 if updated)
     */
    @Modifying(clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        UPDATE union_graphs 
        SET update_ttl_hours = :updateTtlHours,
            webhook_url = :webhookUrl,
            resource_types = CAST(:resourceTypes AS text[]),
            resource_filters = CASE 
                WHEN :resourceFilters IS NULL THEN NULL 
                ELSE CAST(:resourceFilters AS jsonb) 
            END,
            expand_distribution_access_services = :expandDistributionAccessServices,
            format = :format,
            style = :style,
            expand_uris = :expandUris,
            name = :name,
            description = :description,
            status = 'PENDING',
            graph_data = NULL,
            error_message = NULL,
            locked_by = NULL,
            locked_at = NULL,
            processed_at = NULL,
            processing_started_at = NULL,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun updateOrderWithReset(
        @Param("id") id: String,
        @Param("updateTtlHours") updateTtlHours: Int,
        @Param("webhookUrl") webhookUrl: String?,
        @Param("resourceTypes") resourceTypes: List<String>?,
        @Param("resourceFilters") resourceFilters: String?,
        @Param("expandDistributionAccessServices") expandDistributionAccessServices: Boolean,
        @Param("format") format: String,
        @Param("style") style: String,
        @Param("expandUris") expandUris: Boolean,
        @Param("name") name: String?,
        @Param("description") description: String?,
    ): Int

    /**
     * Updates an order with new configuration values.
     * This is a convenience method that calls the appropriate update method based on resetToPending.
     */
    fun updateOrder(
        id: String,
        updateTtlHours: Int,
        webhookUrl: String?,
        resourceTypes: List<String>?,
        resourceFilters: String?,
        expandDistributionAccessServices: Boolean,
        format: String,
        style: String,
        expandUris: Boolean,
        name: String?,
        description: String?,
        resetToPending: Boolean,
    ): Int =
        if (resetToPending) {
            updateOrderWithReset(
                id = id,
                updateTtlHours = updateTtlHours,
                webhookUrl = webhookUrl,
                resourceTypes = resourceTypes,
                resourceFilters = resourceFilters,
                expandDistributionAccessServices = expandDistributionAccessServices,
                format = format,
                style = style,
                expandUris = expandUris,
                name = name,
                description = description,
            )
        } else {
            updateOrderWithoutReset(
                id = id,
                updateTtlHours = updateTtlHours,
                webhookUrl = webhookUrl,
                resourceTypes = resourceTypes,
                resourceFilters = resourceFilters,
                expandDistributionAccessServices = expandDistributionAccessServices,
                format = format,
                style = style,
                expandUris = expandUris,
                name = name,
                description = description,
            )
        }
}
