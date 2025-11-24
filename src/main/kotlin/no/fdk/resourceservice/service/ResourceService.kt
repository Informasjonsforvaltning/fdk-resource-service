package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.repository.ResourceRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

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
    private val resourceRepository: ResourceRepository,
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
     * Gets a resource entity by ID and type.
     *
     * @param id The unique identifier of the resource
     * @param resourceType The type of resource to retrieve
     * @return ResourceEntity, or null if not found or type mismatch
     */
    fun getResourceEntity(
        id: String,
        resourceType: ResourceType,
    ): ResourceEntity? {
        logger.debug("Getting resource entity with id: {} and type: {}", id, resourceType)

        val entity = resourceRepository.findById(id).orElse(null)
        return if (entity?.resourceType == resourceType.name) {
            entity
        } else {
            null
        }
    }

    /**
     * Retrieves a specific resource by its unique identifier as JSON representation.
     *
     * @param id The unique identifier of the resource
     * @param resourceType The type of resource to retrieve
     * @return JSON representation of the resource, or null if not found or type mismatch
     */
    fun getResourceJson(
        id: String,
        resourceType: ResourceType,
    ): Map<String, Any>? {
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
    fun shouldUpdateResource(
        id: String,
        timestamp: Long,
    ): Boolean {
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
     * For concepts, also checks the 'identifier' field as a fallback.
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

        // For concepts, also try the identifier field
        if (entity == null) {
            logger.debug("Resource not found, trying identifier field for concepts with URI: {}", uri)
            entity = resourceRepository.findConceptByIdentifier(uri)
            if (entity != null) {
                logger.debug("Concept found by identifier field for URI: {}", uri)
            }
        }

        if (entity == null) {
            logger.debug("No resource found with URI: {}", uri)
            return null
        }
        return entity
    }

    /**
     * Retrieves the original graph data for a resource by its URI and type.
     *
     * @param uri The URI of the resource
     * @param resourceType The type of resource to retrieve
     * @return Graph data representation of the resource (typically Turtle text), or null if not found or type mismatch
     */
    fun getResourceGraphDataByUri(
        uri: String,
        resourceType: ResourceType,
    ): String? {
        logger.debug("Getting resource graph data with uri: {} and type: {}", uri, resourceType)

        val entity = getResourceEntityByUri(uri)
        return if (entity?.resourceType == resourceType.name) {
            entity.resourceGraphData
        } else {
            null
        }
    }

    /**
     * Retrieves a specific resource by its URI as JSON representation.
     *
     * Uses getResourceEntityByUri for consistent URI lookup with fallback support,
     * then filters by resource type.
     * For concepts, also checks the 'identifier' field in addition to 'uri'.
     *
     * @param uri The URI of the resource
     * @param resourceType The type of resource to retrieve
     * @return JSON representation of the resource, or null if not found or type mismatch
     */
    fun getResourceJsonByUri(
        uri: String,
        resourceType: ResourceType,
    ): Map<String, Any>? {
        logger.debug("Getting resource JSON with uri: {} and type: {}", uri, resourceType)

        // For concepts, try identifier field first
        if (resourceType == ResourceType.CONCEPT) {
            val conceptEntity = resourceRepository.findConceptByIdentifier(uri)
            if (conceptEntity != null && !conceptEntity.deleted) {
                logger.debug("Concept found by identifier field for URI: {}", uri)
                return conceptEntity.resourceJson
            }
        }

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
        timestamp: Long,
    ) {
        // Note: Timestamp check already done in CircuitBreakerService, but double-check here as safety
        val existingEntity = resourceRepository.findById(id).orElse(null)

        if (existingEntity != null) {
            if (existingEntity.timestamp > timestamp) {
                logger.info("Skipped (older timestamp): id=$id, existingTs=${existingEntity.timestamp}, newTs=$timestamp")
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
            val newEntity =
                ResourceEntity(
                    id = id,
                    resourceType = resourceType.name,
                    resourceJson = resourceJson,
                    uri = uri,
                    timestamp = timestamp,
                    deleted = false,
                )
            resourceRepository.save(newEntity)
            logger.debug("Created JSON: id=$id, type=$resourceType")
        }
    }

    /**
     * Stores a resource with original graph data.
     *
     * This method stores a resource with the original RDF graph data (resourceGraphData).
     * The graph data is typically in Turtle format but can support other formats in the future.
     *
     * Note: URI is not extracted from graph data. The URI should be set from the parsed JSON (resourceJson)
     * when storing via storeResourceJson.
     *
     * @param id The unique identifier for the resource
     * @param resourceType The type of resource (CONCEPT, DATASET, etc.)
     * @param graphData The original graph data representation (typically Turtle text)
     * @param format The format of the original RDF data (default: TURTLE)
     * @param timestamp The timestamp when the resource was processed
     */
    fun storeResourceGraphData(
        id: String,
        resourceType: ResourceType,
        graphData: String,
        format: String = "TURTLE",
        timestamp: Long,
    ) {
        // Note: Timestamp check already done in CircuitBreakerService, but double-check here as safety
        val existingEntity = resourceRepository.findById(id).orElse(null)

        if (existingEntity != null) {
            if (existingEntity.timestamp > timestamp) {
                logger.info("Skipped (older timestamp): id=$id, existingTs=${existingEntity.timestamp}, newTs=$timestamp")
                return
            }
            val updateCount =
                resourceRepository.updateResourceGraphData(
                    id = id,
                    graphData = graphData,
                    format = format,
                    timestamp = timestamp,
                )
            resourceRepository.flush()
            logger.info("Updated graph data: id=$id, type=$resourceType, format=$format, rows=$updateCount")
        } else {
            val newEntity =
                ResourceEntity(
                    id = id,
                    resourceType = resourceType.name,
                    resourceJson = null,
                    resourceGraphData = graphData,
                    resourceGraphFormat = format,
                    uri = null,
                    timestamp = timestamp,
                    deleted = false,
                )
            resourceRepository.save(newEntity)
            resourceRepository.flush()
            logger.info("Created graph data: id=$id, type=$resourceType, format=$format")
        }
    }

    /**
     * Retrieves all resources of a specific type that were updated since a given timestamp as JSON representations.
     *
     * @param resourceType The type of resource to retrieve
     * @param since The timestamp to filter resources (only resources updated after this timestamp)
     * @return List of JSON representations (may include null values for resources without JSON data)
     */
    fun getResourceJsonListSince(
        resourceType: ResourceType,
        since: Long,
    ): List<Map<String, Any>?> {
        logger.debug("Getting resource JSON for type: {} since: {}", resourceType, since)

        val entities = resourceRepository.findResourcesSince(resourceType.name, since)
        return entities.map { it.resourceJson }
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
        timestamp: Long,
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
            val newEntity =
                ResourceEntity(
                    id = id,
                    resourceType = resourceType.name,
                    resourceJson = null,
                    uri = null,
                    timestamp = timestamp,
                    deleted = true,
                )
            resourceRepository.save(newEntity)
        }
    }
}
