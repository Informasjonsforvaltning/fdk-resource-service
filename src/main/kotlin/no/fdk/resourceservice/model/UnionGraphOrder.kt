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
     * Human-readable name for the union graph (required).
     */
    @Column(name = "name", nullable = false, length = 255)
    val name: String,
    /**
     * Optional human-readable description of the union graph.
     */
    @Column(name = "description", columnDefinition = "text")
    val description: String? = null,
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
     * For example, dataset filters can filter by isOpenData, isRelatedToTransportportal, and isDatasetSeries fields.
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
    /**
     * If true, when building union graphs, datasets with distributions that reference
     * DataService URIs (via distribution[].accessService[].uri) will have those
     * DataService graphs automatically included in the union graph.
     *
     * This allows creating union graphs that include both datasets and their related
     * data services in a single graph, making it easier to query and navigate the
     * relationships between datasets and data services.
     */
    @Column(name = "expand_distribution_access_services", nullable = false)
    val expandDistributionAccessServices: Boolean = false,
    /**
     * Processing state for incremental batch processing.
     * Stored as JSONB to allow flexible state tracking during graph building.
     * Only populated when status is PROCESSING.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "processing_state", columnDefinition = "jsonb")
    val processingState: UnionGraphProcessingState? = null,
    /**
     * Optional list of resource IDs (fdkId) to filter by.
     * If provided, only resources with matching IDs will be included in the union graph.
     * Stored as text[] array in the database.
     */
    @Column(name = "resource_ids", columnDefinition = "text[]")
    val resourceIds: List<String>? = null,
    /**
     * Optional list of resource URIs to filter by.
     * If provided, only resources with matching URIs will be included in the union graph.
     * Stored as text[] array in the database.
     */
    @Column(name = "resource_uris", columnDefinition = "text[]")
    val resourceUris: List<String>? = null,
    /**
     * If true (default), Catalog and CatalogRecord resources are included in union graph snapshots.
     * If false, Catalog and CatalogRecord resources are removed from snapshots (as subjects),
     * but references to their URIs (as objects) are preserved.
     */
    @Column(name = "include_catalog", nullable = false)
    val includeCatalog: Boolean = true,
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
