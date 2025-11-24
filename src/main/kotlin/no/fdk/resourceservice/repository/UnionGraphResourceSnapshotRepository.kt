package no.fdk.resourceservice.repository

import no.fdk.resourceservice.model.UnionGraphResourceSnapshot
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
interface UnionGraphResourceSnapshotRepository : JpaRepository<UnionGraphResourceSnapshot, Long> {
    /**
     * Finds all snapshots for a given union graph, ordered by resource ID.
     * Returns only the latest snapshot per resource (handles duplicates during rebuilds).
     * If beforeTimestamp is provided, only returns snapshots created before that timestamp.
     * Used for OAI-PMH pagination.
     *
     * @param unionGraphId The union graph ID
     * @param offset Number of records to skip
     * @param limit Maximum number of records to return
     * @param beforeTimestamp Optional timestamp to filter snapshots (only return snapshots created before this time)
     * @return List of resource snapshots
     */
    @Query(
        value = """
        SELECT DISTINCT ON (resource_id) *
        FROM union_graph_resource_snapshots 
        WHERE union_graph_id = CAST(:unionGraphId AS VARCHAR)
        AND (created_at AT TIME ZONE 'UTC') < (:beforeTimestamp AT TIME ZONE 'UTC')
        ORDER BY resource_id ASC, created_at DESC
        LIMIT CAST(:limit AS INTEGER) OFFSET CAST(:offset AS INTEGER)
    """,
        nativeQuery = true,
    )
    fun findByUnionGraphIdPaginated(
        @Param("unionGraphId") unionGraphId: String,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
        @Param("beforeTimestamp") beforeTimestamp: java.sql.Timestamp,
    ): List<UnionGraphResourceSnapshot>

    /**
     * Finds snapshots for a given union graph filtered by resource type, ordered by resource ID.
     * Returns only the latest snapshot per resource (handles duplicates during rebuilds).
     * If beforeTimestamp is provided, only returns snapshots created before that timestamp.
     * Used for OAI-PMH pagination when filtering by resource type.
     *
     * @param unionGraphId The union graph ID
     * @param resourceType The resource type to filter by
     * @param offset Number of records to skip
     * @param limit Maximum number of records to return
     * @param beforeTimestamp Optional timestamp to filter snapshots (only return snapshots created before this time)
     * @return List of resource snapshots
     */
    @Query(
        value = """
        SELECT DISTINCT ON (resource_id) *
        FROM union_graph_resource_snapshots 
        WHERE union_graph_id = CAST(:unionGraphId AS VARCHAR)
        AND resource_type = CAST(:resourceType AS VARCHAR)
        AND (created_at AT TIME ZONE 'UTC') < (:beforeTimestamp AT TIME ZONE 'UTC')
        ORDER BY resource_id ASC, created_at DESC
        LIMIT CAST(:limit AS INTEGER) OFFSET CAST(:offset AS INTEGER)
    """,
        nativeQuery = true,
    )
    fun findByUnionGraphIdAndResourceTypePaginated(
        @Param("unionGraphId") unionGraphId: String,
        @Param("resourceType") resourceType: String,
        @Param("offset") offset: Int,
        @Param("limit") limit: Int,
        @Param("beforeTimestamp") beforeTimestamp: java.sql.Timestamp,
    ): List<UnionGraphResourceSnapshot>

    /**
     * Counts unique resources (latest snapshot per resource) for a given union graph.
     * Used to determine total number of resources for pagination.
     * Handles duplicates during rebuilds by counting distinct resource_ids.
     * If beforeTimestamp is provided, only counts snapshots created before that timestamp.
     *
     * @param unionGraphId The union graph ID
     * @param beforeTimestamp Optional timestamp to filter snapshots (only count snapshots created before this time)
     * @return Total count of unique resources
     */
    @Query(
        value = """
        SELECT COUNT(DISTINCT resource_id) FROM union_graph_resource_snapshots 
        WHERE union_graph_id = CAST(:unionGraphId AS VARCHAR)
        AND (created_at AT TIME ZONE 'UTC') < (:beforeTimestamp AT TIME ZONE 'UTC')
    """,
        nativeQuery = true,
    )
    fun countByUnionGraphId(
        @Param("unionGraphId") unionGraphId: String,
        @Param("beforeTimestamp") beforeTimestamp: java.sql.Timestamp,
    ): Long

    /**
     * Counts unique resources (latest snapshot per resource) for a given union graph filtered by resource type.
     * Handles duplicates during rebuilds by counting distinct resource_ids.
     * If beforeTimestamp is provided, only counts snapshots created before that timestamp.
     *
     * @param unionGraphId The union graph ID
     * @param resourceType The resource type to filter by
     * @param beforeTimestamp Optional timestamp to filter snapshots (only count snapshots created before this time)
     * @return Total count of unique resources
     */
    @Query(
        value = """
        SELECT COUNT(DISTINCT resource_id) FROM union_graph_resource_snapshots 
        WHERE union_graph_id = CAST(:unionGraphId AS VARCHAR)
        AND resource_type = CAST(:resourceType AS VARCHAR)
        AND (created_at AT TIME ZONE 'UTC') < (:beforeTimestamp AT TIME ZONE 'UTC')
    """,
        nativeQuery = true,
    )
    fun countByUnionGraphIdAndResourceType(
        @Param("unionGraphId") unionGraphId: String,
        @Param("resourceType") resourceType: String,
        @Param("beforeTimestamp") beforeTimestamp: java.sql.Timestamp,
    ): Long

    /**
     * Deletes all snapshots for a given union graph.
     * Used when deleting a union graph.
     *
     * @param unionGraphId The union graph ID
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        DELETE FROM union_graph_resource_snapshots 
        WHERE union_graph_id = :unionGraphId
    """,
        nativeQuery = true,
    )
    fun deleteByUnionGraphId(
        @Param("unionGraphId") unionGraphId: String,
    )

    /**
     * Deletes snapshots for a given union graph that were created before a specified timestamp.
     * Used to clean up old snapshots after a new build completes, while keeping new snapshots.
     *
     * @param unionGraphId The union graph ID
     * @param beforeTimestamp Delete snapshots created before this timestamp
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query(
        value = """
        DELETE FROM union_graph_resource_snapshots 
        WHERE union_graph_id = CAST(:unionGraphId AS VARCHAR)
        AND (created_at AT TIME ZONE 'UTC') < (:beforeTimestamp AT TIME ZONE 'UTC')
    """,
        nativeQuery = true,
    )
    fun deleteByUnionGraphIdCreatedBefore(
        @Param("unionGraphId") unionGraphId: String,
        @Param("beforeTimestamp") beforeTimestamp: java.sql.Timestamp,
    )

    /**
     * Finds the latest snapshot for a specific resource in a union graph.
     * Used for GetRecord verb to retrieve a single record by identifier.
     * If beforeTimestamp is provided, only returns snapshots created before that timestamp.
     *
     * @param unionGraphId The union graph ID
     * @param resourceId The resource ID
     * @param beforeTimestamp Optional timestamp to filter snapshots (only return snapshots created before this time)
     * @return The latest snapshot for the resource, or null if not found
     */
    @Query(
        value = """
        SELECT DISTINCT ON (resource_id) *
        FROM union_graph_resource_snapshots 
        WHERE union_graph_id = CAST(:unionGraphId AS VARCHAR)
        AND resource_id = CAST(:resourceId AS VARCHAR)
        AND (created_at AT TIME ZONE 'UTC') < (:beforeTimestamp AT TIME ZONE 'UTC')
        ORDER BY resource_id ASC, created_at DESC
        LIMIT 1
    """,
        nativeQuery = true,
    )
    fun findByUnionGraphIdAndResourceId(
        @Param("unionGraphId") unionGraphId: String,
        @Param("resourceId") resourceId: String,
        @Param("beforeTimestamp") beforeTimestamp: java.sql.Timestamp,
    ): UnionGraphResourceSnapshot?
}
