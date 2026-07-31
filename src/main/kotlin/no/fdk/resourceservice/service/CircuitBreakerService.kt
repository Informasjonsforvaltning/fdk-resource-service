package no.fdk.resourceservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry
import io.micrometer.core.instrument.Metrics
import no.fdk.concept.ConceptEvent
import no.fdk.dataservice.DataServiceEvent
import no.fdk.dataset.DatasetEvent
import no.fdk.event.EventEvent
import no.fdk.informationmodel.InformationModelEvent
import no.fdk.rdf.parse.RdfParseEvent
import no.fdk.rdf.parse.RdfParseResourceType
import no.fdk.resourceservice.kafka.HarvestEventProducer
import no.fdk.resourceservice.model.ResourceType
import no.fdk.service.ServiceEvent
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import kotlin.time.measureTimedValue
import kotlin.time.toJavaDuration

/**
 * Service that handles Kafka event processing with circuit breaker and retry patterns.
 *
 * This service processes various types of resource events from Kafka topics and stores
 * them in the database. It handles both the parsed JSON representation (resourceJson)
 * and the JSON-LD graph representation (resourceGraph) of RDF resources.
 *
 * Key responsibilities:
 * - Process REASONED events: Store both resourceJson and resourceGraph (JSON-LD 1.1 in pretty format)
 * - Handle circuit breaker failures with fallback methods
 * - Convert Turtle RDF to JSON-LD 1.1 for resourceGraph storage
 */
@Service
class CircuitBreakerService(
    private val resourceService: ResourceService,
    private val rdfService: RdfService,
    private val harvestEventProducer: HarvestEventProducer,
    private val circuitBreakerRegistry: CircuitBreakerRegistry,
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(CircuitBreakerService::class.java)

    @Transactional
    fun handleRdfParseEvent(event: RdfParseEvent) {
        circuitBreakerRegistry.circuitBreaker("rdfParseConsumer").executeRunnable {
            logger.debug("RDF parse event: id=${event.fdkId}, type=${event.resourceType}, dataLen=${event.data.length}")

            val resourceType =
                when (event.resourceType) {
                    RdfParseResourceType.CONCEPT -> {
                        ResourceType.CONCEPT
                    }

                    RdfParseResourceType.DATASET -> {
                        ResourceType.DATASET
                    }

                    RdfParseResourceType.DATA_SERVICE -> {
                        ResourceType.DATA_SERVICE
                    }

                    RdfParseResourceType.INFORMATION_MODEL -> {
                        ResourceType.INFORMATION_MODEL
                    }

                    RdfParseResourceType.SERVICE -> {
                        ResourceType.SERVICE
                    }

                    RdfParseResourceType.EVENT -> {
                        ResourceType.EVENT
                    }

                    else -> {
                        logger.error("Unknown resource type in RDF parse event: ${event.resourceType}")
                        Metrics
                            .counter(
                                "store_resource_json_error",
                                "type",
                                "unknown",
                                "error",
                                "unknown_resource_type",
                            ).increment()
                        throw IllegalArgumentException("Unknown resource type: ${event.resourceType}")
                    }
                }

            val startTime = System.currentTimeMillis()
            try {
                val timeElapsed =
                    measureTimedValue {
                        if (!resourceService.shouldUpdateResource(event.fdkId, event.timestamp)) {
                            logger.info("Skipped (older timestamp): id=${event.fdkId}, type=$resourceType")
                            return@measureTimedValue
                        }

                        val resourceJson =
                            try {
                                objectMapper.readValue(
                                    event.data,
                                    object : TypeReference<Map<String, Any>>() {},
                                )
                            } catch (e: Exception) {
                                logger.error("JSON parse failed: id=${event.fdkId}, error=${e.message}", e)
                                Metrics
                                    .counter(
                                        "store_resource_json_error",
                                        "type",
                                        resourceType.name.lowercase(),
                                        "error",
                                        "json_parse_failed",
                                    ).increment()
                                throw e
                            }

                        val resourceUri = resourceJson["uri"] as? String ?: event.uri

                        resourceService.storeResourceJson(
                            id = event.fdkId,
                            resourceType = resourceType,
                            resourceJson = resourceJson,
                            timestamp = event.timestamp,
                        )

                        val endTime = System.currentTimeMillis()
                        harvestEventProducer.produceResourceFinishedEvent(
                            harvestRunId = event.harvestRunId,
                            resourceType = resourceType,
                            fdkId = event.fdkId,
                            resourceUri = resourceUri,
                            startTime = startTime,
                            endTime = endTime,
                        )
                    }
                Metrics
                    .timer("store_resource_json", "type", event.resourceType.name.lowercase())
                    .record(timeElapsed.duration.toJavaDuration())
            } catch (e: Exception) {
                logger.error("Error processing RDF parse event: id=${event.fdkId}", e)
                Metrics
                    .counter(
                        "store_resource_json_error",
                        "type",
                        event.resourceType.name.lowercase(),
                        "error",
                        e.javaClass.simpleName,
                    ).increment()

                val resourceUri =
                    try {
                        @Suppress("UNCHECKED_CAST")
                        val resourceJson = objectMapper.readValue(event.data, Map::class.java) as Map<String, Any>
                        resourceJson["uri"] as? String ?: event.uri
                    } catch (ex: Exception) {
                        event.uri
                    }

                val endTime = System.currentTimeMillis()
                harvestEventProducer.produceResourceFailedEvent(
                    harvestRunId = event.harvestRunId,
                    resourceType = resourceType,
                    fdkId = event.fdkId,
                    resourceUri = resourceUri,
                    startTime = startTime,
                    endTime = endTime,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )

                throw e
            }
        }
    }

    @Transactional
    fun handleConceptEvent(event: ConceptEvent) {
        executeResourceEvent(
            circuitBreakerName = "conceptConsumer",
            metricType = "concept",
            resourceType = ResourceType.CONCEPT,
            debugLabel = "Concept event",
            errorLabel = "concept event",
            fdkId = event.fdkId,
            eventType = event.type,
            graph = event.graph,
            catalogGraph = event.catalogGraph,
            timestamp = event.timestamp,
            harvestRunId = event.harvestRunId,
            resourceUri = event.uri,
        )
    }

    @Transactional
    fun handleDatasetEvent(event: DatasetEvent) {
        executeResourceEvent(
            circuitBreakerName = "datasetConsumer",
            metricType = "dataset",
            resourceType = ResourceType.DATASET,
            debugLabel = "Dataset event",
            errorLabel = "dataset event",
            fdkId = event.fdkId,
            eventType = event.type,
            graph = event.graph,
            catalogGraph = event.catalogGraph,
            timestamp = event.timestamp,
            harvestRunId = event.harvestRunId,
            resourceUri = event.uri,
        )
    }

    @Transactional
    fun handleDataServiceEvent(event: DataServiceEvent) {
        executeResourceEvent(
            circuitBreakerName = "dataServiceConsumer",
            metricType = "data_service",
            resourceType = ResourceType.DATA_SERVICE,
            debugLabel = "DataService event",
            errorLabel = "data service event",
            fdkId = event.fdkId,
            eventType = event.type,
            graph = event.graph,
            catalogGraph = event.catalogGraph,
            timestamp = event.timestamp,
            harvestRunId = event.harvestRunId,
            resourceUri = event.uri,
        )
    }

    @Transactional
    fun handleInformationModelEvent(event: InformationModelEvent) {
        executeResourceEvent(
            circuitBreakerName = "informationModelConsumer",
            metricType = "information_model",
            resourceType = ResourceType.INFORMATION_MODEL,
            debugLabel = "InformationModel event",
            errorLabel = "information model event",
            fdkId = event.fdkId,
            eventType = event.type,
            graph = event.graph,
            catalogGraph = event.catalogGraph,
            timestamp = event.timestamp,
            harvestRunId = event.harvestRunId,
            resourceUri = event.uri,
        )
    }

    @Transactional
    fun handleServiceEvent(event: ServiceEvent) {
        executeResourceEvent(
            circuitBreakerName = "serviceConsumer",
            metricType = "service",
            resourceType = ResourceType.SERVICE,
            debugLabel = "Service event",
            errorLabel = "service event",
            fdkId = event.fdkId,
            eventType = event.type,
            graph = event.graph,
            catalogGraph = event.catalogGraph,
            timestamp = event.timestamp,
            harvestRunId = event.harvestRunId,
            resourceUri = event.uri,
        )
    }

    @Transactional
    fun handleEventEvent(event: EventEvent) {
        executeResourceEvent(
            circuitBreakerName = "eventConsumer",
            metricType = "event",
            resourceType = ResourceType.EVENT,
            debugLabel = "Event event",
            errorLabel = "event event",
            fdkId = event.fdkId,
            eventType = event.type,
            graph = event.graph,
            catalogGraph = event.catalogGraph,
            timestamp = event.timestamp,
            harvestRunId = event.harvestRunId,
            resourceUri = event.uri,
        )
    }

    private fun executeResourceEvent(
        circuitBreakerName: String,
        metricType: String,
        resourceType: ResourceType,
        debugLabel: String,
        errorLabel: String,
        fdkId: String,
        eventType: Any,
        graph: String,
        catalogGraph: String?,
        timestamp: Long,
        harvestRunId: String?,
        resourceUri: String?,
    ) {
        circuitBreakerRegistry.circuitBreaker(circuitBreakerName).executeRunnable {
            logger.debug("$debugLabel: id=$fdkId, type=$eventType, graphLen=${graph.length}")
            val startTime = System.currentTimeMillis()
            try {
                val timeElapsed =
                    measureTimedValue {
                        processResourceEvent(
                            fdkId = fdkId,
                            graph = graph,
                            catalogGraph = catalogGraph,
                            timestamp = timestamp,
                            resourceType = resourceType,
                            eventType = eventType.toString(),
                            harvestRunId = harvestRunId,
                            resourceUri = resourceUri,
                            startTime = startTime,
                        )
                    }
                Metrics
                    .timer("store_resource_jsonld", "type", metricType)
                    .record(timeElapsed.duration.toJavaDuration())
            } catch (e: Exception) {
                logger.error("Error processing $errorLabel: id=$fdkId", e)
                Metrics
                    .counter(
                        "store_resource_jsonld_error",
                        "type",
                        metricType,
                        "error",
                        e.javaClass.simpleName,
                    ).increment()

                val endTime = System.currentTimeMillis()
                harvestEventProducer.produceResourceFailedEvent(
                    harvestRunId = harvestRunId,
                    resourceType = resourceType,
                    fdkId = fdkId,
                    resourceUri = resourceUri,
                    startTime = startTime,
                    endTime = endTime,
                    errorMessage = e.message ?: e.javaClass.simpleName,
                )

                throw e
            }
        }
    }

    private fun processResourceEvent(
        fdkId: String,
        graph: String,
        catalogGraph: String?,
        timestamp: Long,
        resourceType: ResourceType,
        eventType: String,
        harvestRunId: String?,
        resourceUri: String?,
        startTime: Long,
    ) {
        logger.debug(
            "Processing event: id=$fdkId, type=$resourceType, event=$eventType, graphLen=${graph.length}, " +
                "catalogGraphLen=${catalogGraph?.length ?: 0}",
        )
        val action =
            when {
                eventType.endsWith("_REASONED") -> "REASONED"
                eventType.endsWith("_REMOVED") -> "REMOVED"
                else -> eventType.substringAfter("_")
            }

        when (action) {
            "REASONED" -> {
                if (!resourceService.shouldUpdateResource(fdkId, timestamp)) {
                    logger.info("Skipped (older timestamp): id=$fdkId, type=$resourceType")
                    return
                }

                if (graph.isBlank()) {
                    logger.error("Graph data is empty: id=$fdkId, type=$resourceType")
                    Metrics
                        .counter(
                            "store_resource_graph_data_error",
                            "type",
                            resourceType.name.lowercase(),
                            "error",
                            "empty_graph",
                        ).increment()
                    throw IllegalStateException("Graph data is empty for id=$fdkId")
                }

                resourceService.storeResourceGraphData(
                    id = fdkId,
                    resourceType = resourceType,
                    graphData = graph,
                    format = "TURTLE",
                    timestamp = timestamp,
                )

                if (!catalogGraph.isNullOrBlank()) {
                    resourceService.storeCatalogGraphData(
                        id = fdkId,
                        graphData = catalogGraph,
                        format = "TURTLE",
                        timestamp = timestamp,
                    )
                } else {
                    resourceService.clearCatalogGraphData(fdkId)
                }

                logger.debug("Storage called: id=$fdkId, type=$resourceType")

                val endTime = System.currentTimeMillis()
                harvestEventProducer.produceResourceFinishedEvent(
                    harvestRunId = harvestRunId,
                    resourceType = resourceType,
                    fdkId = fdkId,
                    resourceUri = resourceUri,
                    startTime = startTime,
                    endTime = endTime,
                )
            }

            "REMOVED" -> {
                resourceService.markResourceAsDeleted(
                    id = fdkId,
                    resourceType = resourceType,
                    timestamp = timestamp,
                )
                logger.debug("Marked deleted: id=$fdkId, type=$resourceType")

                val endTime = System.currentTimeMillis()
                harvestEventProducer.produceResourceRemovedEvent(
                    harvestRunId = harvestRunId,
                    resourceType = resourceType,
                    fdkId = fdkId,
                    resourceUri = resourceUri,
                    startTime = startTime,
                    endTime = endTime,
                )
            }

            else -> {
                logger.warn("Unknown action: id=$fdkId, event=$eventType")
            }
        }
    }
}
