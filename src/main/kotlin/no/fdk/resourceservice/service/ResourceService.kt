package no.fdk.resourceservice.service

import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.repository.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import com.fasterxml.jackson.databind.ObjectMapper

/**
 * Service for managing resource data in the FDK Resource Service.
 * 
 * This service handles the storage and retrieval of resources, including
 * both the parsed JSON representation (resourceJson) and the JSON-LD graph
 * representation (resourceGraph) of RDF resources.
 */
@Service
@Transactional
class ResourceService(
    private val resourceRepository: ResourceRepository
) {
    private val logger = LoggerFactory.getLogger(ResourceService::class.java)
    private val objectMapper = ObjectMapper()

    /**
     * Retrieves all resources of a specific type as JSON representations.
     * 
     * @param resourceType The type of resource to retrieve
     * @return List of JSON representations (may include null values for resources without JSON data)
     */
    fun getResourceJsonList(resourceType: ResourceType): List<Map<String, Any>?> {
        logger.debug("Getting resource JSON for type: {}", resourceType)
        
        val entities = resourceRepository.findByResourceType(resourceType.name)
        return entities.map { it.resourceJson }
    }

    /**
     * Retrieves a specific resource by its unique identifier as JSON representation.
     * 
     * @param id The unique identifier of the resource
     * @param resourceType The type of resource to retrieve
     * @return JSON representation of the resource, or null if not found or type mismatch
     */
    fun getResourceJson(id: String, resourceType: ResourceType): Map<String, Any>? {
        logger.debug("Getting resource JSON with id: {} and type: {}", id, resourceType)
        
        val entity = resourceRepository.findById(id).orElse(null)
        return if (entity?.resourceType == resourceType.name) {
            entity.resourceJson
        } else {
            null
        }
    }

    /**
     * Retrieves a resource by its URI across all resource types as JSON representation.
     * 
     * Uses getResourceEntityByUri for consistent URI lookup with fallback support.
     * 
     * @param uri The URI of the resource
     * @return JSON representation of the resource, or null if not found
     */
    fun getResourceJsonByUri(uri: String): Map<String, Any>? {
        logger.debug("Getting resource JSON with uri: {} across all types", uri)
        
        val entity = getResourceEntityByUri(uri)
        return entity?.resourceJson
    }

    /**
     * Checks if a resource should be updated based on timestamp comparison.
     * 
     * This is a lightweight check that only queries the timestamp column,
     * avoiding expensive operations when the resource is already up-to-date.
     * 
     * @param id The unique identifier of the resource
     * @param timestamp The new timestamp to compare
     * @return true if resource should be updated (doesn't exist or new timestamp is >= existing), false otherwise
     */
    fun shouldUpdateResource(id: String, timestamp: Long): Boolean {
        val existingTimestamp = resourceRepository.findTimestampById(id)
        return when {
            existingTimestamp == null -> true // Resource doesn't exist, should create
            timestamp >= existingTimestamp -> true // New timestamp is equal or newer, should update
            else -> false // Existing timestamp is newer, skip update
        }
    }

    /**
     * Retrieves a resource entity by its URI across all resource types.
     * 
     * First attempts to find the resource using the URI column for efficient querying.
     * If not found, falls back to searching the resource_json column's 'uri' field.
     * 
     * @param uri The URI of the resource
     * @return ResourceEntity, or null if not found
     */
    fun getResourceEntityByUri(uri: String): ResourceEntity? {
        logger.debug("Getting resource entity with uri: {} across all types", uri)
        
        // First try the URI column (most efficient)
        var entity = resourceRepository.findByUri(uri)
        
        // If not found, try searching in the JSON column as fallback
        if (entity == null) {
            logger.debug("Resource not found in URI column, trying JSON column fallback for URI: {}", uri)
            entity = resourceRepository.findByUriInJson(uri)
            if (entity != null) {
                logger.debug("Resource found in JSON column fallback for URI: {}", uri)
            }
        }
        
        if (entity == null) {
            logger.debug("No resource found with URI: {}", uri)
            return null
        }
        return entity
    }

    /**
     * Retrieves a resource by its URI across all resource types as JSON-LD representation.
     * 
     * Uses getResourceEntityByUri for consistent URI lookup with fallback support.
     * Returns the complete stored JSON-LD graph, whether it's in single node format
     * or @graph format with multiple nodes.
     * 
     * @param uri The URI of the resource
     * @return JSON-LD representation of the resource (complete graph), or null if not found
     */
    fun getResourceJsonLdByUri(uri: String): Map<String, Any>? {
        logger.debug("Getting resource JSON-LD with uri: {} across all types", uri)
        
        val entity = getResourceEntityByUri(uri)
        return entity?.resourceJsonLd
    }

    /**
     * Retrieves a specific resource by its URI as JSON representation.
     * 
     * Uses getResourceEntityByUri for consistent URI lookup with fallback support,
     * then filters by resource type.
     * 
     * @param uri The URI of the resource
     * @param resourceType The type of resource to retrieve
     * @return JSON representation of the resource, or null if not found or type mismatch
     */
    fun getResourceJsonByUri(uri: String, resourceType: ResourceType): Map<String, Any>? {
        logger.debug("Getting resource JSON with uri: {} and type: {}", uri, resourceType)
        
        val entity = getResourceEntityByUri(uri)
        return if (entity?.resourceType == resourceType.name) {
            entity.resourceJson
        } else {
            null
        }
    }

    /**
     * Stores a resource with only the parsed JSON representation.
     * 
     * @param id The unique identifier for the resource
     * @param resourceType The type of resource (CONCEPT, DATASET, etc.)
     * @param resourceJson The parsed JSON representation of the resource (FDK internal model)
     * @param timestamp The timestamp when the resource was processed
     */
    fun storeResourceJson(
        id: String,
        resourceType: ResourceType,
        resourceJson: Map<String, Any>,
        timestamp: Long
    ) {
        // Note: Timestamp check already done in CircuitBreakerService, but double-check here as safety
        val existingEntity = resourceRepository.findById(id).orElse(null)
        
        if (existingEntity != null) {
            if (existingEntity.timestamp > timestamp) {
                logger.debug("Skipped (older timestamp): id=$id, existingTs=${existingEntity.timestamp}, newTs=$timestamp")
                return
            }
            // Extract URI from resourceJson if available
            val uri = resourceJson["uri"] as? String
            // Update existing resource - JSON field and URI
            val jsonString = objectMapper.writeValueAsString(resourceJson)
            resourceRepository.updateResourceJson(id, jsonString, uri, timestamp)
            logger.debug("Updated JSON: id=$id, type=$resourceType")
        } else {
            // Extract URI from resourceJson if available
            val uri = resourceJson["uri"] as? String
            // Create new resource with only JSON
            val newEntity = ResourceEntity(
                id = id,
                resourceType = resourceType.name,
                resourceJson = resourceJson,
                resourceJsonLd = null,
                uri = uri,
                timestamp = timestamp,
                deleted = false
            )
            resourceRepository.save(newEntity)
            logger.debug("Created JSON: id=$id, type=$resourceType")
        }
    }

    /**
     * Stores a resource with JSON-LD graph representation.
     * 
     * This method stores a resource with the complete RDF graph in JSON-LD 1.1 format (resourceJsonLd).
     * The resourceJsonLd contains the full RDF structure with expanded URIs in JSON-LD 1.1 pretty format.
     * Supports both single node format and @graph format with multiple root nodes.
     * 
     * Note: URI is not extracted from JSON-LD. The URI should be set from the parsed JSON (resourceJson)
     * when storing via storeResourceJson.
     * 
     * @param id The unique identifier for the resource
     * @param resourceType The type of resource (CONCEPT, DATASET, etc.)
     * @param resourceJsonLd The JSON-LD 1.1 representation (single node or @graph format)
     * @param timestamp The timestamp when the resource was processed
     */
    fun storeResourceJsonLd(
        id: String,
        resourceType: ResourceType,
        resourceJsonLd: Map<String, Any>,
        timestamp: Long
    ) {
        // Note: Timestamp check already done in CircuitBreakerService, but double-check here as safety
        val existingEntity = resourceRepository.findById(id).orElse(null)
        
        if (existingEntity != null) {
            if (existingEntity.timestamp > timestamp) {
                logger.debug("Skipped (older timestamp): id=$id, existingTs=${existingEntity.timestamp}, newTs=$timestamp")
                return
            }
            val jsonLdSize = jsonLdSize(resourceJsonLd)
            val jsonLdString = objectMapper.writeValueAsString(resourceJsonLd)
            val updateCount = resourceRepository.updateResourceJsonLdAndUndelete(id, jsonLdString, timestamp)
            resourceRepository.flush()
            logger.info("Updated JSON-LD: id=$id, type=$resourceType, size=$jsonLdSize, rows=$updateCount")
        } else {
            val jsonLdSize = jsonLdSize(resourceJsonLd)
            val newEntity = ResourceEntity(
                id = id,
                resourceType = resourceType.name,
                resourceJson = null,
                resourceJsonLd = resourceJsonLd,
                uri = null,
                timestamp = timestamp,
                deleted = false
            )
            resourceRepository.save(newEntity)
            logger.info("Created JSON-LD: id=$id, type=$resourceType, size=$jsonLdSize")
        }
    }
    
    private fun jsonLdSize(jsonLd: Map<String, Any>): String {
        val str = objectMapper.writeValueAsString(jsonLd)
        return when {
            str.length < 1000 -> "${str.length}B"
            str.length < 1_000_000 -> "${str.length / 1000}KB"
            else -> "${str.length / 1_000_000}MB"
        }
    }

    /**
     * Retrieves all resources of a specific type that were updated since a given timestamp as JSON representations.
     * 
     * @param resourceType The type of resource to retrieve
     * @param since The timestamp to filter resources (only resources updated after this timestamp)
     * @return List of JSON representations (may include null values for resources without JSON data)
     */
    fun getResourceJsonListSince(resourceType: ResourceType, since: Long): List<Map<String, Any>?> {
        logger.debug("Getting resource JSON for type: {} since: {}", resourceType, since)
        
        val entities = resourceRepository.findResourcesSince(resourceType.name, since)
        return entities.map { it.resourceJson }
    }

    /**
     * Retrieves a specific resource by its unique identifier as JSON-LD representation.
     * 
     * @param id The unique identifier of the resource
     * @param resourceType The type of resource to retrieve
     * @return JSON-LD representation of the resource, or null if not found or type mismatch
     */
    fun getResourceJsonLd(id: String, resourceType: ResourceType): Map<String, Any>? {
        logger.debug("Getting resource JSON-LD with id: {} and type: {}", id, resourceType)
        
        val entity = resourceRepository.findById(id).orElse(null)
        return if (entity?.resourceType == resourceType.name) {
            entity.resourceJsonLd
        } else {
            null
        }
    }

    /**
     * Retrieves a specific resource by its URI as JSON-LD representation.
     * 
     * Uses getResourceEntityByUri for consistent URI lookup with fallback support,
     * then filters by resource type. Returns the complete stored JSON-LD graph,
     * whether it's in single node format or @graph format with multiple nodes.
     * 
     * @param uri The URI of the resource
     * @param resourceType The type of resource to retrieve
     * @return JSON-LD representation of the resource (complete graph), or null if not found or type mismatch
     */
    fun getResourceJsonLdByUri(uri: String, resourceType: ResourceType): Map<String, Any>? {
        logger.debug("Getting resource JSON-LD with uri: {} and type: {}", uri, resourceType)
        
        val entity = getResourceEntityByUri(uri)
        return if (entity?.resourceType == resourceType.name) {
            entity.resourceJsonLd
        } else {
            null
        }
    }

    /**
     * Retrieves all resources of a specific type as JSON-LD representations.
     * 
     * @param resourceType The type of resource to retrieve
     * @return List of JSON-LD representations (may include null values for resources without JSON-LD data)
     */
    fun getResourceJsonLdList(resourceType: ResourceType): List<Map<String, Any>?> {
        logger.debug("Getting resource JSON-LD for type: {}", resourceType)
        
        val entities = resourceRepository.findByResourceType(resourceType.name)
        return entities.map { it.resourceJsonLd }
    }

    /**
     * Retrieves all resources of a specific type that were updated since a given timestamp as JSON-LD representations.
     * 
     * @param resourceType The type of resource to retrieve
     * @param since The timestamp to filter resources (only resources updated after this timestamp)
     * @return List of JSON-LD representations (may include null values for resources without JSON-LD data)
     */
    fun getResourceJsonLdListSince(resourceType: ResourceType, since: Long): List<Map<String, Any>?> {
        logger.debug("Getting resource JSON-LD for type: {} since: {}", resourceType, since)
        
        val entities = resourceRepository.findResourcesSince(resourceType.name, since)
        return entities.map { it.resourceJsonLd }
    }

    /**
     * Marks a resource as deleted.
     * 
     * This method sets the deleted flag to true for the specified resource,
     * effectively removing it from active queries while preserving the data.
     * 
     * @param id The unique identifier for the resource
     * @param resourceType The type of resource (CONCEPT, DATASET, etc.)
     * @param timestamp The timestamp when the resource was deleted
     */
    fun markResourceAsDeleted(
        id: String,
        resourceType: ResourceType,
        timestamp: Long
    ) {
        logger.debug("Marking resource as deleted with id: {}, type: {}, timestamp: {}", id, resourceType, timestamp)
        
        val existingEntity = resourceRepository.findById(id).orElse(null)
        
        if (existingEntity != null) {
            // Check if current timestamp is higher than existing
            if (existingEntity.timestamp > timestamp) {
                logger.debug("Existing resource has higher timestamp, skipping deletion")
            } else {
                // Mark as deleted
                resourceRepository.markAsDeleted(id, timestamp)
                logger.info("Successfully marked resource as deleted: {}", id)
            }
        } else {
            logger.warn("Adding resource marked as deleted: {}", id)
            // Create new resource marked as deleted
            val newEntity = ResourceEntity(
                id = id,
                resourceType = resourceType.name,
                resourceJson = null,
                resourceJsonLd = null,
                uri = null,
                timestamp = timestamp,
                deleted = true
            )
            resourceRepository.save(newEntity)
        }
    }
}
