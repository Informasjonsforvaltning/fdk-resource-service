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
            graph_json_ld = CAST(:graphJsonLd AS jsonb),
            processed_at = CURRENT_TIMESTAMP,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun markAsCompleted(
        @Param("id") id: String,
        @Param("graphJsonLd") graphJsonLd: String,
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
    ): UnionGraphOrder?
}
