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
    
    @Query(value = """
        SELECT * FROM resources 
        WHERE resource_type = :resourceType 
        AND uri = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """, nativeQuery = true)
    fun findByResourceTypeAndUri(
        @Param("resourceType") resourceType: String,
        @Param("uri") uri: String
    ): ResourceEntity?
    
    @Query(value = """
        SELECT * FROM resources 
        WHERE resource_type = :resourceType 
        AND deleted = false 
        AND uri = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """, nativeQuery = true)
    fun findByResourceTypeAndUriAndDeletedFalse(
        @Param("resourceType") resourceType: String,
        @Param("uri") uri: String
    ): ResourceEntity?
    
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE resources 
        SET resource_json = CAST(:resourceJson AS jsonb), uri = :uri, timestamp = :timestamp, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """, nativeQuery = true)
    fun updateResourceJson(
        @Param("id") id: String,
        @Param("resourceJson") resourceJson: String,
        @Param("uri") uri: String?,
        @Param("timestamp") timestamp: Long
    ): Int
    
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE resources 
        SET resource_json_ld = CAST(:resourceJsonLd AS jsonb), timestamp = :timestamp, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """, nativeQuery = true)
    fun updateResourceJsonLd(
        @Param("id") id: String,
        @Param("resourceJsonLd") resourceJsonLd: String,
        @Param("timestamp") timestamp: Long
    ): Int
    
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE resources 
        SET deleted = true, timestamp = :timestamp, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """, nativeQuery = true)
    fun markAsDeleted(
        @Param("id") id: String,
        @Param("timestamp") timestamp: Long
    ): Int
    
    @Modifying
    @Transactional
    @Query(value = """
        UPDATE resources 
        SET resource_json_ld = CAST(:resourceJsonLd AS jsonb), deleted = false, timestamp = :timestamp, updated_at = CURRENT_TIMESTAMP
        WHERE id = :id
    """, nativeQuery = true)
    fun updateResourceJsonLdAndUndelete(
        @Param("id") id: String,
        @Param("resourceJsonLd") resourceJsonLd: String,
        @Param("timestamp") timestamp: Long
    ): Int
    
    
    @Query(value = """
        SELECT * FROM resources 
        WHERE uri = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """, nativeQuery = true)
    fun findByUri(@Param("uri") uri: String): ResourceEntity?
    
    @Query(value = """
        SELECT * FROM resources 
        WHERE resource_json->>'uri' = :uri
        ORDER BY timestamp DESC
        LIMIT 1
    """, nativeQuery = true)
    fun findByUriInJson(@Param("uri") uri: String): ResourceEntity?
    
    @Query(value = """
        SELECT * FROM resources 
        WHERE uri = :uri
        AND deleted = false
        ORDER BY timestamp DESC
        LIMIT 1
    """, nativeQuery = true)
    fun findByUriAndDeletedFalse(@Param("uri") uri: String): ResourceEntity?
    
    @Query("""
        SELECT r FROM ResourceEntity r 
        WHERE r.resourceType = :resourceType 
        AND r.timestamp > :since
        ORDER BY r.timestamp DESC
    """)
    fun findResourcesSince(
        @Param("resourceType") resourceType: String,
        @Param("since") since: Long
    ): List<ResourceEntity>
    
    @Query(value = """
        SELECT timestamp FROM resources 
        WHERE id = :id
        LIMIT 1
    """, nativeQuery = true)
    fun findTimestampById(@Param("id") id: String): Long?
}
