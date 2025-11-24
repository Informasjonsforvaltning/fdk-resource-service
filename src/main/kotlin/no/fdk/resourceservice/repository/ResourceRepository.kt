package no.fdk.resourceservice.repository

import no.fdk.resourceservice.model.ResourceEntity
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface ResourceRepository : JpaRepository<ResourceEntity, String> {
    fun findByResourceType(resourceType: String): List<ResourceEntity>

    fun findByResourceTypeAndDeletedFalse(resourceType: String): List<ResourceEntity>

    fun findByIdAndDeletedFalse(id: String): ResourceEntity?

    @Query(
        value = """
        SELECT * FROM resources 
        WHERE resource_type = :resourceType 
        AND uri = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findByResourceTypeAndUri(
        @Param("resourceType") resourceType: String,
        @Param("uri") uri: String,
    ): ResourceEntity?

    @Query(
        value = """
        SELECT * FROM resources 
        WHERE resource_type = :resourceType 
        AND deleted = false 
        AND uri = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findByResourceTypeAndUriAndDeletedFalse(
        @Param("resourceType") resourceType: String,
        @Param("uri") uri: String,
    ): ResourceEntity?

    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE resources 
        SET resource_json = CAST(:resourceJson AS jsonb), uri = :uri, timestamp = :timestamp, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun updateResourceJson(
        @Param("id") id: String,
        @Param("resourceJson") resourceJson: String,
        @Param("uri") uri: String?,
        @Param("timestamp") timestamp: Long,
    ): Int

    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE resources 
        SET deleted = true, timestamp = :timestamp, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun markAsDeleted(
        @Param("id") id: String,
        @Param("timestamp") timestamp: Long,
    ): Int

    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE resources 
        SET resource_graph_data = :graphData, 
            resource_graph_format = :format,
            deleted = false,
            timestamp = :timestamp, 
            updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """,
        nativeQuery = true,
    )
    fun updateResourceGraphData(
        @Param("id") id: String,
        @Param("graphData") graphData: String,
        @Param("format") format: String,
        @Param("timestamp") timestamp: Long,
    ): Int

    @Query(
        value = """
        SELECT * FROM resources 
        WHERE uri = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findByUri(
        @Param("uri") uri: String,
    ): ResourceEntity?

    @Query(
        value = """
        SELECT * FROM resources 
        WHERE resource_json->>'uri' = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findByUriInJson(
        @Param("uri") uri: String,
    ): ResourceEntity?

    @Query(
        value = """
        SELECT * FROM resources 
        WHERE resource_type = 'CONCEPT' 
        AND resource_json->>'identifier' = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findConceptByIdentifier(
        @Param("uri") uri: String,
    ): ResourceEntity?

    @Query(
        value = """
        SELECT * FROM resources 
        WHERE uri = :uri
        AND deleted = false
        ORDER BY timestamp DESC
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findByUriAndDeletedFalse(
        @Param("uri") uri: String,
    ): ResourceEntity?

    @Query(
        """
        SELECT r FROM ResourceEntity r 
        WHERE r.resourceType = :resourceType 
        AND r.timestamp > :since
        ORDER BY r.timestamp DESC
    """,
    )
    fun findResourcesSince(
        @Param("resourceType") resourceType: String,
        @Param("since") since: Long,
    ): List<ResourceEntity>

    @Query(
        value = """
        SELECT timestamp FROM resources 
        WHERE id = :id
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findTimestampById(
        @Param("id") id: String,
    ): Long?

    /**
     * Finds resources by type with pagination for memory-efficient processing.
     * Only returns resources that have graph data (resource_graph_data IS NOT NULL and not empty).
     * Used for building union graphs in batches.
     *
     * @param resourceType The resource type to filter by
     * @param offset Number of records to skip
     * @param limit Maximum number of records to return
     * @return List of resource entities with graph data
     */
    @Query(
        value = """
        SELECT * FROM resources 
        WHERE resource_type = :resourceType 
        AND deleted = false
        AND resource_graph_data IS NOT NULL
        AND resource_graph_data != ''
        ORDER BY id ASC
        LIMIT :limit OFFSET :offset
    """,
        nativeQuery = true,
    )
    fun findByResourceTypeAndDeletedFalseWithGraphDataPaginated(
        @Param("resourceType") resourceType: String,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
    ): List<ResourceEntity>

    /**
     * Finds datasets with filters and pagination for memory-efficient processing.
     * Only returns resources that have graph data (resource_graph_data IS NOT NULL and not empty).
     * Used for building union graphs in batches.
     *
     * @param offset Number of records to skip
     * @param limit Maximum number of records to return
     * @param isOpenData Optional filter for open data flag
     * @param isRelatedToTransportportal Optional filter for transport portal relation
     * @return List of dataset entities with graph data
     */
    @Query(
        value = """
        SELECT * FROM resources
        WHERE resource_type = 'DATASET'
        AND deleted = false
        AND resource_graph_data IS NOT NULL
        AND resource_graph_data != ''
        AND (:isOpenData IS NULL OR resource_json->>'isOpenData' = :isOpenData)
        AND (:isRelatedToTransportportal IS NULL OR resource_json->>'isRelatedToTransportportal' = :isRelatedToTransportportal)
        ORDER BY id ASC
        LIMIT :limit OFFSET :offset
    """,
        nativeQuery = true,
    )
    fun findDatasetsByFiltersWithGraphDataPaginated(
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
        @Param("isOpenData") isOpenData: String?,
        @Param("isRelatedToTransportportal") isRelatedToTransportportal: String?,
    ): List<ResourceEntity>

    /**
     * Counts resources by type (non-deleted only).
     * Used to determine total number of resources for pagination.
     *
     * @param resourceType The resource type to count
     * @return Total count of non-deleted resources of the specified type
     */
    @Query(
        value = """
        SELECT COUNT(*) FROM resources 
        WHERE resource_type = :resourceType 
        AND deleted = false
        AND resource_graph_data IS NOT NULL
        AND resource_graph_data != ''
    """,
        nativeQuery = true,
    )
    fun countByResourceTypeAndDeletedFalse(
        @Param("resourceType") resourceType: String,
    ): Long

    @Query(
        value = """
        SELECT COUNT(*) FROM resources
        WHERE resource_type = 'DATASET'
        AND deleted = false
        AND resource_graph_data IS NOT NULL
        AND resource_graph_data != ''
        AND (:isOpenData IS NULL OR resource_json->>'isOpenData' = :isOpenData)
        AND (:isRelatedToTransportportal IS NULL OR resource_json->>'isRelatedToTransportportal' = :isRelatedToTransportportal)
    """,
        nativeQuery = true,
    )
    fun countDatasetsByFilters(
        @Param("isOpenData") isOpenData: String?,
        @Param("isRelatedToTransportportal") isRelatedToTransportportal: String?,
    ): Long

    /**
     * Finds resources by type with pagination and optional ID/URI filters.
     * Only returns resources that have graph data (resource_graph_data IS NOT NULL and not empty).
     * Used for building union graphs in batches with ID/URI filtering.
     *
     * @param resourceType The resource type to filter by
     * @param offset Number of records to skip
     * @param limit Maximum number of records to return
     * @param resourceIds Optional list of resource IDs (fdkId) to filter by
     * @param resourceUris Optional list of resource URIs to filter by
     * @return List of resource entities with graph data
     */
    @Query(
        value = """
        SELECT * FROM resources 
        WHERE resource_type = :resourceType 
        AND deleted = false
        AND resource_graph_data IS NOT NULL
        AND resource_graph_data != ''
        AND (:resourceIds IS NULL OR id = ANY(CAST(:resourceIds AS text[])))
        AND (:resourceUris IS NULL OR uri = ANY(CAST(:resourceUris AS text[])))
        ORDER BY id ASC
        LIMIT :limit OFFSET :offset
    """,
        nativeQuery = true,
    )
    fun findByResourceTypeAndDeletedFalseWithGraphDataPaginatedWithFilters(
        @Param("resourceType") resourceType: String,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
        @Param("resourceIds") resourceIds: String?,
        @Param("resourceUris") resourceUris: String?,
    ): List<ResourceEntity>

    /**
     * Finds datasets with filters and pagination, including optional ID/URI filters.
     * Only returns resources that have graph data (resource_graph_data IS NOT NULL and not empty).
     * Used for building union graphs in batches.
     *
     * @param offset Number of records to skip
     * @param limit Maximum number of records to return
     * @param isOpenData Optional filter for open data flag
     * @param isRelatedToTransportportal Optional filter for transport portal relation
     * @param resourceIds Optional list of resource IDs (fdkId) to filter by
     * @param resourceUris Optional list of resource URIs to filter by
     * @return List of dataset entities with graph data
     */
    @Query(
        value = """
        SELECT * FROM resources
        WHERE resource_type = 'DATASET'
        AND deleted = false
        AND resource_graph_data IS NOT NULL
        AND resource_graph_data != ''
        AND (:isOpenData IS NULL OR resource_json->>'isOpenData' = :isOpenData)
        AND (:isRelatedToTransportportal IS NULL OR resource_json->>'isRelatedToTransportportal' = :isRelatedToTransportportal)
        AND (:resourceIds IS NULL OR id = ANY(CAST(:resourceIds AS text[])))
        AND (:resourceUris IS NULL OR uri = ANY(CAST(:resourceUris AS text[])))
        ORDER BY id ASC
        LIMIT :limit OFFSET :offset
    """,
        nativeQuery = true,
    )
    fun findDatasetsByFiltersWithGraphDataPaginatedWithFilters(
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
        @Param("isOpenData") isOpenData: String?,
        @Param("isRelatedToTransportportal") isRelatedToTransportportal: String?,
        @Param("resourceIds") resourceIds: String?,
        @Param("resourceUris") resourceUris: String?,
    ): List<ResourceEntity>

    /**
     * Counts resources by type with optional ID/URI filters (non-deleted only).
     * Used to determine total number of resources for pagination.
     *
     * @param resourceType The resource type to count
     * @param resourceIds Optional list of resource IDs (fdkId) to filter by
     * @param resourceUris Optional list of resource URIs to filter by
     * @return Total count of non-deleted resources of the specified type
     */
    @Query(
        value = """
        SELECT COUNT(*) FROM resources 
        WHERE resource_type = :resourceType 
        AND deleted = false
        AND resource_graph_data IS NOT NULL
        AND resource_graph_data != ''
        AND (:resourceIds IS NULL OR id = ANY(CAST(:resourceIds AS text[])))
        AND (:resourceUris IS NULL OR uri = ANY(CAST(:resourceUris AS text[])))
    """,
        nativeQuery = true,
    )
    fun countByResourceTypeAndDeletedFalseWithFilters(
        @Param("resourceType") resourceType: String,
        @Param("resourceIds") resourceIds: String?,
        @Param("resourceUris") resourceUris: String?,
    ): Long

    /**
     * Counts datasets with filters, including optional ID/URI filters.
     * Used to determine total number of datasets for pagination.
     *
     * @param isOpenData Optional filter for open data flag
     * @param isRelatedToTransportportal Optional filter for transport portal relation
     * @param resourceIds Optional list of resource IDs (fdkId) to filter by
     * @param resourceUris Optional list of resource URIs to filter by
     * @return Total count of non-deleted datasets matching the filters
     */
    @Query(
        value = """
        SELECT COUNT(*) FROM resources
        WHERE resource_type = 'DATASET'
        AND deleted = false
        AND resource_graph_data IS NOT NULL
        AND resource_graph_data != ''
        AND (:isOpenData IS NULL OR resource_json->>'isOpenData' = :isOpenData)
        AND (:isRelatedToTransportportal IS NULL OR resource_json->>'isRelatedToTransportportal' = :isRelatedToTransportportal)
        AND (:resourceIds IS NULL OR id = ANY(CAST(:resourceIds AS text[])))
        AND (:resourceUris IS NULL OR uri = ANY(CAST(:resourceUris AS text[])))
    """,
        nativeQuery = true,
    )
    fun countDatasetsByFiltersWithFilters(
        @Param("isOpenData") isOpenData: String?,
        @Param("isRelatedToTransportportal") isRelatedToTransportportal: String?,
        @Param("resourceIds") resourceIds: String?,
        @Param("resourceUris") resourceUris: String?,
    ): Long
}
