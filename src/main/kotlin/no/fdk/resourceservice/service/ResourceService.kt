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
     * Retrieves a specific resource by its URI as JSON representation.
     * 
     * @param uri The URI of the resource
     * @param resourceType The type of resource to retrieve
     * @return JSON representation of the resource, or null if not found
     */
    fun getResourceJsonByUri(uri: String, resourceType: ResourceType): Map<String, Any>? {
        logger.debug("Getting resource JSON with uri: {} and type: {}", uri, resourceType)
        
        val entity = resourceRepository.findByResourceTypeAndUri(resourceType.name, uri)
        return entity?.resourceJson
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
        logger.debug("Storing resource JSON with id: {}, type: {}, timestamp: {}", id, resourceType, timestamp)
        
        val existingEntity = resourceRepository.findById(id).orElse(null)
        
        if (existingEntity != null) {
            // Check if current timestamp is higher than existing
            if (existingEntity.timestamp > timestamp) {
                logger.debug("Existing resource has higher timestamp, skipping update")
            } else {
                // Update existing resource - only JSON field
                val jsonString = objectMapper.writeValueAsString(resourceJson)
                resourceRepository.updateResourceJson(id, jsonString, timestamp)
            }
        } else {
            // Create new resource with only JSON
            val newEntity = ResourceEntity(
                id = id,
                resourceType = resourceType.name,
                resourceJson = resourceJson,
                resourceJsonLd = null,
                timestamp = timestamp,
                deleted = false
            )
            resourceRepository.save(newEntity)
        }
    }

    /**
     * Stores a resource with JSON-LD graph representation.
     * 
     * This method stores a resource with the complete RDF graph in JSON-LD 1.1 format (resourceJsonLd).
     * The resourceJsonLd contains the full RDF structure with expanded URIs in JSON-LD 1.1 pretty format 
     * (no @context/@graph wrapper).
     * 
     * @param id The unique identifier for the resource
     * @param resourceType The type of resource (CONCEPT, DATASET, etc.)
     * @param resourceJsonLd The JSON-LD 1.1 representation in pretty format (JSON object with expanded URIs)
     * @param timestamp The timestamp when the resource was processed
     */
    fun storeResourceJsonLd(
        id: String,
        resourceType: ResourceType,
        resourceJsonLd: Map<String, Any>,
        timestamp: Long
    ) {
        logger.debug("Storing resource JSON-LD with id: {}, type: {}, timestamp: {}", id, resourceType, timestamp)
        
        val existingEntity = resourceRepository.findById(id).orElse(null)
        
        if (existingEntity != null) {
            // Check if current timestamp is higher than existing
            if (existingEntity.timestamp > timestamp) {
                logger.debug("Existing resource has higher timestamp, skipping update")
            } else {
                // Update existing resource - JSON-LD field and ensure it's not deleted (harvested resources are always active)
                val jsonLdString = objectMapper.writeValueAsString(resourceJsonLd)
                resourceRepository.updateResourceJsonLdAndUndelete(id, jsonLdString, timestamp)
            }
        } else {
            // Create new resource with only JSON-LD
            val newEntity = ResourceEntity(
                id = id,
                resourceType = resourceType.name,
                resourceJson = null,
                resourceJsonLd = resourceJsonLd,
                timestamp = timestamp,
                deleted = false
            )
            resourceRepository.save(newEntity)
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
     * @param uri The URI of the resource
     * @param resourceType The type of resource to retrieve
     * @return JSON-LD representation of the resource, or null if not found
     */
    fun getResourceJsonLdByUri(uri: String, resourceType: ResourceType): Map<String, Any>? {
        logger.debug("Getting resource JSON-LD with uri: {} and type: {}", uri, resourceType)
        
        val entity = resourceRepository.findByResourceTypeAndUri(resourceType.name, uri)
        return entity?.resourceJsonLd
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
                timestamp = timestamp,
                deleted = true
            )
            resourceRepository.save(newEntity)
        }
    }
}
