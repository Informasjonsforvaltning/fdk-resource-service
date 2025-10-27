package no.fdk.resourceservice.service

import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
import no.fdk.concept.ConceptEvent
import no.fdk.dataset.DatasetEvent
import no.fdk.dataservice.DataServiceEvent
import no.fdk.event.EventEvent
import no.fdk.informationmodel.InformationModelEvent
import no.fdk.rdf.parse.RdfParseEvent
import no.fdk.rdf.parse.RdfParseResourceType
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.RdfService.RdfFormat
import no.fdk.service.ServiceEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

/**
 * Service that handles Kafka event processing with circuit breaker and retry patterns.
 * 
 * This service processes various types of resource events from Kafka topics and stores
 * them in the database. It handles both the parsed JSON representation (resourceJson)
 * and the JSON-LD graph representation (resourceGraph) of RDF resources.
 * 
 * Key responsibilities:
 * - Process HARVESTED events: Store both resourceJson and resourceGraph (JSON-LD 1.1 in pretty format)
 * - Process REASONED events: Update resourceJson and resourceGraph
 * - Handle circuit breaker failures with fallback methods
 * - Convert Turtle RDF to JSON-LD 1.1 for resourceGraph storage
 */
@Service
class CircuitBreakerService(
    private val resourceService: ResourceService,
    private val rdfService: RdfService
) {
    private val logger = LoggerFactory.getLogger(CircuitBreakerService::class.java)

    @CircuitBreaker(name = "rdfParseConsumer", fallbackMethod = "handleRdfParseEventFallback")
    fun handleRdfParseEvent(event: RdfParseEvent) {
        logger.debug("Processing RDF parse event with circuit breaker: $event")
        
        when (event.resourceType) {
            RdfParseResourceType.CONCEPT -> {
                resourceService.storeResourceJson(
                    id = event.fdkId,
                    resourceType = ResourceType.CONCEPT,
                    resourceJson = mapOf("graph" to event.data),
                    timestamp = event.timestamp
                )
            }
            RdfParseResourceType.DATASET -> {
                resourceService.storeResourceJson(
                    id = event.fdkId,
                    resourceType = ResourceType.DATASET,
                    resourceJson = mapOf("graph" to event.data),
                    timestamp = event.timestamp
                )
            }
            RdfParseResourceType.DATA_SERVICE -> {
                resourceService.storeResourceJson(
                    id = event.fdkId,
                    resourceType = ResourceType.DATA_SERVICE,
                    resourceJson = mapOf("graph" to event.data),
                    timestamp = event.timestamp
                )
            }
            RdfParseResourceType.INFORMATION_MODEL -> {
                resourceService.storeResourceJson(
                    id = event.fdkId,
                    resourceType = ResourceType.INFORMATION_MODEL,
                    resourceJson = mapOf("graph" to event.data),
                    timestamp = event.timestamp
                )
            }
            RdfParseResourceType.SERVICE -> {
                resourceService.storeResourceJson(
                    id = event.fdkId,
                    resourceType = ResourceType.SERVICE,
                    resourceJson = mapOf("graph" to event.data),
                    timestamp = event.timestamp
                )
            }
            RdfParseResourceType.EVENT -> {
                resourceService.storeResourceJson(
                    id = event.fdkId,
                    resourceType = ResourceType.EVENT,
                    resourceJson = mapOf("graph" to event.data),
                    timestamp = event.timestamp
                )
            }
            else -> {
                logger.warn("Unknown resource type in RDF parse event: ${event.resourceType}")
            }
        }
    }

    @CircuitBreaker(name = "conceptConsumer", fallbackMethod = "handleConceptEventFallback")
    fun handleConceptEvent(event: ConceptEvent) {
        logger.info("Processing concept event with circuit breaker: $event")
        processResourceEvent(event.fdkId, event.graph, event.timestamp, ResourceType.CONCEPT, event.type.toString())        
    }

    @CircuitBreaker(name = "datasetConsumer", fallbackMethod = "handleDatasetEventFallback")
    fun handleDatasetEvent(event: DatasetEvent) {
        logger.debug("Processing dataset event with circuit breaker: $event")
        processResourceEvent(event.fdkId, event.graph, event.timestamp, ResourceType.DATASET, event.type.toString())
    }

    @CircuitBreaker(name = "dataServiceConsumer", fallbackMethod = "handleDataServiceEventFallback")
    fun handleDataServiceEvent(event: DataServiceEvent) {
        logger.debug("Processing data service event with circuit breaker: $event")
        processResourceEvent(event.fdkId, event.graph, event.timestamp, ResourceType.DATA_SERVICE, event.type.toString())
    }

    @CircuitBreaker(name = "informationModelConsumer", fallbackMethod = "handleInformationModelEventFallback")
    fun handleInformationModelEvent(event: InformationModelEvent) {
        logger.debug("Processing information model event with circuit breaker: $event")
        processResourceEvent(event.fdkId, event.graph, event.timestamp, ResourceType.INFORMATION_MODEL, event.type.toString())
    }

    @CircuitBreaker(name = "serviceConsumer", fallbackMethod = "handleServiceEventFallback")
    fun handleServiceEvent(event: ServiceEvent) {
        logger.debug("Processing service event with circuit breaker: $event")
        processResourceEvent(event.fdkId, event.graph, event.timestamp, ResourceType.SERVICE, event.type.toString())
    }

    @CircuitBreaker(name = "eventConsumer", fallbackMethod = "handleEventEventFallback")
    fun handleEventEvent(event: EventEvent) {
        logger.debug("Processing event event with circuit breaker: $event")
        processResourceEvent(event.fdkId, event.graph, event.timestamp, ResourceType.EVENT, event.type.toString())
    }

    private fun processResourceEvent(fdkId: String, graph: String, timestamp: Long, resourceType: ResourceType, eventType: String) {
        logger.info("📝 PROCESS RESOURCE: Processing resource event - fdkId=$fdkId, resourceType=$resourceType, eventType=$eventType")
        val action = when {
            eventType.endsWith("_HARVESTED") -> "HARVESTED"
            eventType.endsWith("_REMOVED") -> "REMOVED"
            else -> eventType.substringAfter("_")
        }
        logger.info("📝 PROCESS RESOURCE: Action extracted: $action")
        
        when (action) {
            "HARVESTED" -> {
                logger.info("📝 PROCESS RESOURCE: Processing HARVESTED event")
                // For HARVESTED events, convert Turtle to JSON-LD Map and store
                val jsonLdMap = rdfService.convertTurtleToJsonLdMap(graph, true)
                logger.info("📝 PROCESS RESOURCE: Converted to JSON-LD Map, calling resourceService.storeResource")
                
                // Store JSON-LD
                resourceService.storeResourceJsonLd(
                    id = fdkId,
                    resourceType = resourceType,
                    resourceJsonLd = jsonLdMap,
                    timestamp = timestamp
                )
                logger.info("📝 PROCESS RESOURCE: Successfully stored HARVESTED resource")
            }
            "REMOVED" -> {
                logger.info("📝 PROCESS RESOURCE: Processing REMOVED event")
                // For REMOVED events, mark the resource as deleted
                resourceService.markResourceAsDeleted(
                    id = fdkId,
                    resourceType = resourceType,
                    timestamp = timestamp
                )
                logger.info("📝 PROCESS RESOURCE: Successfully marked resource as deleted")
            }
            else -> {
                logger.warn("Unknown action in event type: $eventType")
            }
        }
    }

    // Fallback methods for circuit breaker
    fun handleRdfParseEventFallback(event: RdfParseEvent, ex: Exception) {
        logger.warn("Circuit breaker fallback triggered for RDF parse event: $event", ex)
    }

    fun handleConceptEventFallback(event: ConceptEvent, ex: Exception) {
        logger.warn("Circuit breaker fallback triggered for concept event: $event", ex)
    }

    fun handleDatasetEventFallback(event: DatasetEvent, ex: Exception) {
        logger.warn("Circuit breaker fallback triggered for dataset event: $event", ex)
    }

    fun handleDataServiceEventFallback(event: DataServiceEvent, ex: Exception) {
        logger.warn("Circuit breaker fallback triggered for data service event: $event", ex)
    }

    fun handleInformationModelEventFallback(event: InformationModelEvent, ex: Exception) {
        logger.warn("Circuit breaker fallback triggered for information model event: $event", ex)
    }

    fun handleServiceEventFallback(event: ServiceEvent, ex: Exception) {
        logger.warn("Circuit breaker fallback triggered for service event: $event", ex)
    }

    fun handleEventEventFallback(event: EventEvent, ex: Exception) {
        logger.warn("Circuit breaker fallback triggered for event event: $event", ex)
    }
}
