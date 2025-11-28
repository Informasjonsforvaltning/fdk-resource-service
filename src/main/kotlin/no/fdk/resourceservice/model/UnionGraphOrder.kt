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
     * The built union graph data in the requested format.
     * Only populated when status is COMPLETED.
     * Stored as TEXT to support any RDF format (JSON-LD, Turtle, RDF/XML, etc.).
     */
    @Column(name = "graph_data", columnDefinition = "text")
    val graphData: String? = null,
    /**
     * The RDF format used for the graph data.
     * Default: JSON_LD
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 50)
    val format: GraphFormat = GraphFormat.JSON_LD,
    /**
     * The style used for the graph format (PRETTY or STANDARD).
     * Default: PRETTY
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "style", nullable = false, length = 50)
    val style: GraphStyle = GraphStyle.PRETTY,
    /**
     * Whether URIs are expanded in the graph data.
     * Default: true
     */
    @Column(name = "expand_uris", nullable = false)
    val expandUris: Boolean = true,
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

    enum class GraphFormat {
        JSON_LD,
        TURTLE,
        RDF_XML,
        N_TRIPLES,
        N_QUADS,
    }

    enum class GraphStyle {
        PRETTY,
        STANDARD,
    }
}
