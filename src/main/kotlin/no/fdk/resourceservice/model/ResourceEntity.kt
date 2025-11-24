package no.fdk.resourceservice.model

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Entity representing a resource stored in the FDK Resource Service.
 *
 * This entity stores the parsed JSON representation of RDF resources (resourceJson)
 * and the original RDF graph data (resourceGraphData) for different use cases.
 */
@Entity
@Table(name = "resources")
data class ResourceEntity(
    @Id
    @Column(name = "id", nullable = false, length = 255)
    val id: String = "",
    @Column(name = "resource_type", nullable = false, length = 50)
    val resourceType: String = "",
    /**
     * The parsed JSON representation of the RDF resource (FDK internal model).
     *
     * This field contains the structured JSON data that represents the resource
     * in the FDK's internal data model format. It is populated when RDF data
     * is parsed and converted to the FDK's standard JSON structure.
     *
     * Example structure:
     * ```json
     * {
     *   "uri": "https://example.com/resource",
     *   "title": "Resource Title",
     *   "description": "Resource description",
     *   "type": "Dataset"
     * }
     * ```
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_json", columnDefinition = "jsonb")
    val resourceJson: Map<String, Any>? = null,
    /**
     * The original RDF graph data representation.
     *
     * This field stores the original RDF data (typically in Turtle format), which is used
     * for building union graphs and supporting multiple RDF formats in the future.
     */
    @Column(name = "resource_graph_data", columnDefinition = "text")
    val resourceGraphData: String? = null,
    /**
     * The format of the original RDF graph data.
     *
     * This field indicates the format of the original RDF data before conversion to Turtle.
     * Supported formats: TURTLE, JSON_LD, RDF_XML, N_TRIPLES, N_QUADS.
     * This allows supporting multiple input formats in the future.
     */
    @Column(name = "resource_graph_format", length = 50)
    val resourceGraphFormat: String? = null,
    /**
     * The URI of the resource extracted from the RDF graph.
     *
     * This field stores the URI extracted from the RDF representation,
     * supporting both single node format and @graph format with multiple root nodes.
     * The URI is extracted based on the resource type and RDF type matching.
     *
     * This allows efficient querying by URI without parsing JSONB or text.
     */
    @Column(name = "uri", length = 1000)
    val uri: String? = null,
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = 0L,
    @Column(name = "deleted", nullable = false)
    val deleted: Boolean = false,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now(),
) {
    @PreUpdate
    fun preUpdate() {
        // updatedAt will be set automatically by JPA
    }
}
