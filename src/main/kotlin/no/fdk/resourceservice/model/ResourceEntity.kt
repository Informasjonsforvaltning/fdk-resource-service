package no.fdk.resourceservice.model

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant

/**
 * Entity representing a resource stored in the FDK Resource Service.
 * 
 * This entity stores both the parsed JSON representation of RDF resources
 * and their JSON-LD graph representations for different use cases.
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
     * The JSON-LD 1.1 representation of the RDF graph without namespace prefixes.
     * 
     * This field contains the RDF data converted to JSON-LD 1.1 format with all
     * namespace prefixes expanded to full URIs. It provides a standardized
     * way to access the complete RDF graph data in JSON format.
     * 
     * Key differences from resourceJson:
     * - Contains the full RDF graph structure
     * - Uses expanded URIs (no namespace prefixes)
     * - Stored in JSON-LD 1.1 pretty format (no @context or @graph wrapper)
     * - Populated during HARVESTED and REASONED events
     * 
     * Example structure (JSON-LD 1.1 pretty format):
     * ```json
     * {
     *   "@id": "http://example.com/resource",
     *   "@type": "http://example.org/Dataset",
     *   "http://purl.org/dc/elements/1.1/title": "Resource Title",
     *   "http://purl.org/dc/terms/description": "Resource description"
     * }
     * ```
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "resource_json_ld", columnDefinition = "jsonb")
    val resourceJsonLd: Map<String, Any>? = null,
    
    @Column(name = "timestamp", nullable = false)
    val timestamp: Long = 0L,
    
    @Column(name = "deleted", nullable = false)
    val deleted: Boolean = false,
    
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
    
    @Column(name = "updated_at", nullable = false)
    val updatedAt: Instant = Instant.now()
) {
    @PreUpdate
    fun preUpdate() {
        // updatedAt will be set automatically by JPA
    }
}



