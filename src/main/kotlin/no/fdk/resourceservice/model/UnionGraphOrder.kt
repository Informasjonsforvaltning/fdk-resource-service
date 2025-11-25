package no.fdk.resourceservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.util.UUID

/**
 * Entity representing a union graph order.
 *
 * Union graphs are built from multiple resource graphs by combining them.
 * The graph building process happens asynchronously in the background.
 */
@Entity
@Table(name = "union_graphs")
data class UnionGraphOrder(
    @Id
    @Column(name = "id", nullable = false, length = 255)
    val id: String = UUID.randomUUID().toString(),
    /**
     * Status of the graph building process.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 50)
    val status: GraphStatus = GraphStatus.PENDING,
    /**
     * List of resource types to include in the union graph.
     * If null or empty, all resource types will be included.
     */
    @Column(name = "resource_types", columnDefinition = "text[]")
    val resourceTypes: List<String>? = null,
    /**
     * Optional per-resource-type filters that should be applied when
     * collecting resources for the union graph.
     *
     * Filters allow you to include only resources that match specific criteria.
     * For example, dataset filters can filter by isOpenData and isRelatedToTransportportal fields.
     * Filters are part of the order configuration, so orders with different filters
     * are considered different orders.
     *
     * Stored as JSONB in the database to allow flexible evolution of filter structures
     * without requiring schema changes.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_filters", columnDefinition = "jsonb")
    val resourceFilters: UnionGraphResourceFilters? = null,
    /**
     * Time to live in hours for automatic graph updates.
     * 0 means never update automatically.
     * Otherwise, the graph will be automatically updated after this many hours.
     */
    @Column(name = "update_ttl_hours", nullable = false)
    val updateTtlHours: Int = 0,
    /**
     * The built union graph in JSON-LD format.
     * Only populated when status is COMPLETED.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "graph_json_ld", columnDefinition = "jsonb")
    val graphJsonLd: Map<String, Any>? = null,
    /**
     * Error message if the graph building failed.
     */
    @Column(name = "error_message", columnDefinition = "text")
    val errorMessage: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
    /**
     * Timestamp when the graph was successfully processed.
     */
    @Column(name = "processed_at")
    val processedAt: Instant? = null,
    /**
     * Timestamp when processing started (for distributed locking).
     */
    @Column(name = "processing_started_at")
    val processingStartedAt: Instant? = null,
    /**
     * Identifier of the instance processing this order (for distributed locking).
     */
    @Column(name = "locked_by", length = 255)
    val lockedBy: String? = null,
    /**
     * Timestamp when the order was locked (for distributed locking).
     */
    @Column(name = "locked_at")
    val lockedAt: Instant? = null,
    /**
     * Webhook URL to call when the order status changes.
     * The webhook will be called with a POST request containing the order status.
     */
    @Column(name = "webhook_url", columnDefinition = "text")
    val webhookUrl: String? = null,
) {
    @PreUpdate
    fun preUpdate() {
        // updatedAt will be set automatically by JPA
    }

    enum class GraphStatus {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED,
    }
}
