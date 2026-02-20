package no.fdk.resourceservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker
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
) {
    private val logger = LoggerFactory.getLogger(CircuitBreakerService::class.java)
    private val objectMapper = ObjectMapper()

    @CircuitBreaker(name = "rdfParseConsumer")
    @Transactional
    fun handleRdfParseEvent(event: RdfParseEvent) {
        logger.debug("RDF parse event: id=${event.fdkId}, type=${event.resourceType}, dataLen=${event.data.length}")

        val resourceType =
            when (event.resourceType) {
                RdfParseResourceType.CONCEPT -> ResourceType.CONCEPT
                RdfParseResourceType.DATASET -> ResourceType.DATASET
                RdfParseResourceType.DATA_SERVICE -> ResourceType.DATA_SERVICE
                RdfParseResourceType.INFORMATION_MODEL -> ResourceType.INFORMATION_MODEL
                RdfParseResourceType.SERVICE -> ResourceType.SERVICE
                RdfParseResourceType.EVENT -> ResourceType.EVENT
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

                    // Produce harvest event on successful completion
                    val endTime = System.currentTimeMillis()
                    harvestEventProducer.produceResourceFinishedEvent(
                        harvestRunId = event.harvestRunId?.toString(),
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

            // Produce harvest event on failure
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
                harvestRunId = event.harvestRunId?.toString(),
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

    @CircuitBreaker(name = "conceptConsumer")
    @Transactional
    fun handleConceptEvent(event: ConceptEvent) {
        logger.debug("Concept event: id=${event.fdkId}, type=${event.type}, graphLen=${event.graph.length}")
        val startTime = System.currentTimeMillis()
        try {
            val timeElapsed =
                measureTimedValue {
                    processResourceEvent(
                        fdkId = event.fdkId,
                        graph = event.graph,
                        timestamp = event.timestamp,
                        resourceType = ResourceType.CONCEPT,
                        eventType = event.type.toString(),
                        harvestRunId = event.harvestRunId?.toString(),
                        resourceUri = event.uri?.toString(),
                        startTime = startTime,
                    )
                }
            Metrics
                .timer("store_resource_jsonld", "type", "concept")
                .record(timeElapsed.duration.toJavaDuration())
        } catch (e: Exception) {
            logger.error("Error processing concept event: id=${event.fdkId}", e)
            Metrics.counter("store_resource_jsonld_error", "type", "concept", "error", e.javaClass.simpleName).increment()

            // Produce harvest event on failure
            val endTime = System.currentTimeMillis()
            harvestEventProducer.produceResourceFailedEvent(
                harvestRunId = event.harvestRunId?.toString(),
                resourceType = ResourceType.CONCEPT,
                fdkId = event.fdkId,
                resourceUri = event.uri?.toString(),
                startTime = startTime,
                endTime = endTime,
                errorMessage = e.message ?: e.javaClass.simpleName,
            )

            throw e
        }
    }

    @CircuitBreaker(name = "datasetConsumer")
    @Transactional
    fun handleDatasetEvent(event: DatasetEvent) {
        logger.debug("Dataset event: id=${event.fdkId}, type=${event.type}, graphLen=${event.graph.length}")
        val startTime = System.currentTimeMillis()
        try {
            val timeElapsed =
                measureTimedValue {
                    processResourceEvent(
                        fdkId = event.fdkId,
                        graph = event.graph,
                        timestamp = event.timestamp,
                        resourceType = ResourceType.DATASET,
                        eventType = event.type.toString(),
                        harvestRunId = event.harvestRunId?.toString(),
                        resourceUri = event.uri?.toString(),
                        startTime = startTime,
                    )
                }
            Metrics
                .timer("store_resource_jsonld", "type", "dataset")
                .record(timeElapsed.duration.toJavaDuration())
        } catch (e: Exception) {
            logger.error("Error processing dataset event: id=${event.fdkId}", e)
            Metrics.counter("store_resource_jsonld_error", "type", "dataset", "error", e.javaClass.simpleName).increment()

            // Produce harvest event on failure
            val endTime = System.currentTimeMillis()
            harvestEventProducer.produceResourceFailedEvent(
                harvestRunId = event.harvestRunId?.toString(),
                resourceType = ResourceType.DATASET,
                fdkId = event.fdkId,
                resourceUri = event.uri?.toString(),
                startTime = startTime,
                endTime = endTime,
                errorMessage = e.message ?: e.javaClass.simpleName,
            )

            throw e
        }
    }

    @CircuitBreaker(name = "dataServiceConsumer")
    @Transactional
    fun handleDataServiceEvent(event: DataServiceEvent) {
        logger.debug("DataService event: id=${event.fdkId}, type=${event.type}, graphLen=${event.graph.length}")
        val startTime = System.currentTimeMillis()
        try {
            val timeElapsed =
                measureTimedValue {
                    processResourceEvent(
                        fdkId = event.fdkId,
                        graph = event.graph,
                        timestamp = event.timestamp,
                        resourceType = ResourceType.DATA_SERVICE,
                        eventType = event.type.toString(),
                        harvestRunId = event.harvestRunId?.toString(),
                        resourceUri = event.uri?.toString(),
                        startTime = startTime,
                    )
                }
            Metrics
                .timer("store_resource_jsonld", "type", "data_service")
                .record(timeElapsed.duration.toJavaDuration())
        } catch (e: Exception) {
            logger.error("Error processing data service event: id=${event.fdkId}", e)
            Metrics.counter("store_resource_jsonld_error", "type", "data_service", "error", e.javaClass.simpleName).increment()

            // Produce harvest event on failure
            val endTime = System.currentTimeMillis()
            harvestEventProducer.produceResourceFailedEvent(
                harvestRunId = event.harvestRunId?.toString(),
                resourceType = ResourceType.DATA_SERVICE,
                fdkId = event.fdkId,
                resourceUri = event.uri?.toString(),
                startTime = startTime,
                endTime = endTime,
                errorMessage = e.message ?: e.javaClass.simpleName,
            )

            throw e
        }
    }

    @CircuitBreaker(name = "informationModelConsumer")
    @Transactional
    fun handleInformationModelEvent(event: InformationModelEvent) {
        logger.debug("InformationModel event: id=${event.fdkId}, type=${event.type}, graphLen=${event.graph.length}")
        val startTime = System.currentTimeMillis()
        try {
            val timeElapsed =
                measureTimedValue {
                    processResourceEvent(
                        fdkId = event.fdkId,
                        graph = event.graph,
                        timestamp = event.timestamp,
                        resourceType = ResourceType.INFORMATION_MODEL,
                        eventType = event.type.toString(),
                        harvestRunId = event.harvestRunId?.toString(),
                        resourceUri = event.uri?.toString(),
                        startTime = startTime,
                    )
                }
            Metrics
                .timer("store_resource_jsonld", "type", "information_model")
                .record(timeElapsed.duration.toJavaDuration())
        } catch (e: Exception) {
            logger.error("Error processing information model event: id=${event.fdkId}", e)
            Metrics.counter("store_resource_jsonld_error", "type", "information_model", "error", e.javaClass.simpleName).increment()

            // Produce harvest event on failure
            val endTime = System.currentTimeMillis()
            harvestEventProducer.produceResourceFailedEvent(
                harvestRunId = event.harvestRunId?.toString(),
                resourceType = ResourceType.INFORMATION_MODEL,
                fdkId = event.fdkId,
                resourceUri = event.uri?.toString(),
                startTime = startTime,
                endTime = endTime,
                errorMessage = e.message ?: e.javaClass.simpleName,
            )

            throw e
        }
    }

    @CircuitBreaker(name = "serviceConsumer")
    @Transactional
    fun handleServiceEvent(event: ServiceEvent) {
        logger.debug("Service event: id=${event.fdkId}, type=${event.type}, graphLen=${event.graph.length}")
        val startTime = System.currentTimeMillis()
        try {
            val timeElapsed =
                measureTimedValue {
                    processResourceEvent(
                        fdkId = event.fdkId,
                        graph = event.graph,
                        timestamp = event.timestamp,
                        resourceType = ResourceType.SERVICE,
                        eventType = event.type.toString(),
                        harvestRunId = event.harvestRunId?.toString(),
                        resourceUri = event.uri?.toString(),
                        startTime = startTime,
                    )
                }
            Metrics
                .timer("store_resource_jsonld", "type", "service")
                .record(timeElapsed.duration.toJavaDuration())
        } catch (e: Exception) {
            logger.error("Error processing service event: id=${event.fdkId}", e)
            Metrics.counter("store_resource_jsonld_error", "type", "service", "error", e.javaClass.simpleName).increment()

            // Produce harvest event on failure
            val endTime = System.currentTimeMillis()
            harvestEventProducer.produceResourceFailedEvent(
                harvestRunId = event.harvestRunId?.toString(),
                resourceType = ResourceType.SERVICE,
                fdkId = event.fdkId,
                resourceUri = event.uri?.toString(),
                startTime = startTime,
                endTime = endTime,
                errorMessage = e.message ?: e.javaClass.simpleName,
            )

            throw e
        }
    }

    @CircuitBreaker(name = "eventConsumer")
    @Transactional
    fun handleEventEvent(event: EventEvent) {
        logger.debug("Event event: id=${event.fdkId}, type=${event.type}, graphLen=${event.graph.length}")
        val startTime = System.currentTimeMillis()
        try {
            val timeElapsed =
                measureTimedValue {
                    processResourceEvent(
                        fdkId = event.fdkId,
                        graph = event.graph,
                        timestamp = event.timestamp,
                        resourceType = ResourceType.EVENT,
                        eventType = event.type.toString(),
                        harvestRunId = event.harvestRunId?.toString(),
                        resourceUri = event.uri?.toString(),
                        startTime = startTime,
                    )
                }
            Metrics
                .timer("store_resource_jsonld", "type", "event")
                .record(timeElapsed.duration.toJavaDuration())
        } catch (e: Exception) {
            logger.error("Error processing event event: id=${event.fdkId}", e)
            Metrics.counter("store_resource_jsonld_error", "type", "event", "error", e.javaClass.simpleName).increment()

            // Produce harvest event on failure
            val endTime = System.currentTimeMillis()
            harvestEventProducer.produceResourceFailedEvent(
                harvestRunId = event.harvestRunId?.toString(),
                resourceType = ResourceType.EVENT,
                fdkId = event.fdkId,
                resourceUri = event.uri?.toString(),
                startTime = startTime,
                endTime = endTime,
                errorMessage = e.message ?: e.javaClass.simpleName,
            )

            throw e
        }
    }

    private fun processResourceEvent(
        fdkId: String,
        graph: String,
        timestamp: Long,
        resourceType: ResourceType,
        eventType: String,
        harvestRunId: String?,
        resourceUri: String?,
        startTime: Long,
    ) {
        logger.debug("Processing event: id=$fdkId, type=$resourceType, event=$eventType, graphLen=${graph.length}")
        val action =
            when {
                eventType.endsWith("_REASONED") -> "REASONED"
                eventType.endsWith("_REMOVED") -> "REMOVED"
                else -> eventType.substringAfter("_")
            }

        when (action) {
            "REASONED" -> {
                // Check timestamp early to avoid expensive operations
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
                    graphData = graph, // Store original graph data (Turtle text)
                    format = "TURTLE", // Graph is in Turtle format
                    timestamp = timestamp,
                )
                logger.debug("Storage called: id=$fdkId, type=$resourceType")

                // Produce harvest event on successful completion
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

                // Produce harvest event when resource is removed
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
