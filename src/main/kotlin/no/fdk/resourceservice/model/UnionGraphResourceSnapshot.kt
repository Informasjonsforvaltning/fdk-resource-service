package no.fdk.resourceservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

/**
 * Entity representing a snapshot of a resource's graph data at the time a union graph was built.
 *
 * This ensures consistency - when resources are served via OAI-PMH, they match
 * the version that was used when building the union graph, preventing inconsistencies
 * if resources are updated after the union graph is built.
 */
@Entity
@Table(name = "union_graph_resource_snapshots")
data class UnionGraphResourceSnapshot(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    val id: Long = 0L,
    /**
     * Reference to the union graph this snapshot belongs to.
     */
    @Column(name = "union_graph_id", nullable = false, length = 255)
    val unionGraphId: String,
    /**
     * ID of the resource that was snapshotted.
     */
    @Column(name = "resource_id", nullable = false, length = 255)
    val resourceId: String,
    /**
     * Type of the resource (DATASET, CONCEPT, etc.).
     */
    @Column(name = "resource_type", nullable = false, length = 50)
    val resourceType: String,
    /**
     * Snapshot of the resource graph data (typically Turtle text).
     */
    @Column(name = "resource_graph_data", nullable = false, columnDefinition = "text")
    val resourceGraphData: String,
    /**
     * Format of the snapshot data (TURTLE, JSON_LD, etc.).
     */
    @Column(name = "resource_graph_format", length = 50)
    val resourceGraphFormat: String? = null,
    /**
     * Timestamp when the snapshot was created.
     */
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    /**
     * Resource modified date (from harvest.modified) at snapshot time.
     * Used for OAI-PMH from/until filtering and datestamp in headers.
     */
    @Column(name = "resource_modified_at")
    val resourceModifiedAt: Instant? = null,
    /**
     * Publisher organization number (from publisher.id) at snapshot time.
     * Used for OAI-PMH set filter (org:orgnr) and setSpec in headers.
     */
    @Column(name = "publisher_orgnr", length = 50)
    val publisherOrgnr: String? = null,
)
