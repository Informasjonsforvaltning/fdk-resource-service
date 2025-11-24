package no.fdk.resourceservice.kafka

import no.fdk.concept.ConceptEvent
import no.fdk.concept.ConceptEventType
import no.fdk.dataservice.DataServiceEvent
import no.fdk.dataservice.DataServiceEventType
import no.fdk.dataset.DatasetEvent
import no.fdk.dataset.DatasetEventType
import no.fdk.event.EventEvent
import no.fdk.event.EventEventType
import no.fdk.informationmodel.InformationModelEvent
import no.fdk.informationmodel.InformationModelEventType
import no.fdk.rdf.parse.RdfParseEvent
import no.fdk.rdf.parse.RdfParseResourceType
import no.fdk.resourceservice.service.CircuitBreakerService
import no.fdk.service.ServiceEvent
import no.fdk.service.ServiceEventType
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.slf4j.LoggerFactory
import org.springframework.kafka.annotation.KafkaListener
import org.springframework.kafka.support.Acknowledgment
import org.springframework.kafka.support.KafkaHeaders
import org.springframework.messaging.handler.annotation.Header
import org.springframework.stereotype.Component
import java.time.Duration

@Component
class KafkaConsumer(
    private val circuitBreakerService: CircuitBreakerService,
) {
    private val logger = LoggerFactory.getLogger(KafkaConsumer::class.java)

    /**
     * Validates and extracts required fields from a GenericRecord.
     * Throws IllegalArgumentException if any required field is missing or empty.
     * Graph can be empty, so it's not validated.
     *
     * @param value The GenericRecord to extract fields from
     * @param eventTypeName The name of the event type (for logging)
     * @return A data class containing fdkId, type, timestamp, and graph
     * @throws IllegalArgumentException if fdkId, type, or timestamp is missing or invalid
     */
    private data class RequiredFields(
        val fdkId: String,
        val type: String,
        val timestamp: Long,
        val graph: String,
    )

    private fun extractRequiredFields(
        value: org.apache.avro.generic.GenericRecord,
        eventTypeName: String,
    ): RequiredFields {
        val fdkIdStr = value.get("fdkId")?.toString()
        if (fdkIdStr.isNullOrBlank()) {
            throw IllegalArgumentException("Missing or empty fdkId in $eventTypeName")
        }

        val typeStr = value.get("type")?.toString()
        if (typeStr.isNullOrBlank()) {
            throw IllegalArgumentException("Missing or empty type in $eventTypeName")
        }

        val timestamp = value.get("timestamp") as? Long
        if (timestamp == null) {
            throw IllegalArgumentException("Missing or invalid timestamp in $eventTypeName")
        }

        val graph = value.get("graph")?.toString() ?: ""

        return RequiredFields(fdkIdStr, typeStr, timestamp, graph)
    }

    /**
     * Validates and extracts required fields from a GenericRecord for RdfParseEvent.
     * Throws IllegalArgumentException if any required field is missing or empty.
     * Data can be empty, so it's not validated.
     *
     * @param value The GenericRecord to extract fields from
     * @param eventTypeName The name of the event type (for logging)
     * @return A Triple of (fdkId, timestamp, data)
     * @throws IllegalArgumentException if fdkId or timestamp is missing or invalid
     */
    private fun extractRdfParseRequiredFields(
        value: org.apache.avro.generic.GenericRecord,
        eventTypeName: String,
    ): Triple<String, Long, String> {
        val fdkIdStr = value.get("fdkId")?.toString()
        if (fdkIdStr.isNullOrBlank()) {
            throw IllegalArgumentException("Missing or empty fdkId in $eventTypeName")
        }

        val timestamp = value.get("timestamp") as? Long
        if (timestamp == null) {
            throw IllegalArgumentException("Missing or invalid timestamp in $eventTypeName")
        }

        val data = value.get("data")?.toString() ?: ""

        return Triple(fdkIdStr, timestamp, data)
    }

    private fun extractConceptEvent(record: ConsumerRecord<String, Any>): ConceptEvent? {
        val value = record.value()
        return when (value) {
            is ConceptEvent -> {
                logger.debug("ConsumerRecord contains ConceptEvent")
                value
            }
            is org.apache.avro.generic.GenericRecord -> {
                logger.debug("Converting GenericRecord to ConceptEvent")
                try {
                    val fields = extractRequiredFields(value, "ConceptEvent")

                    ConceptEvent
                        .newBuilder()
                        .setFdkId(fields.fdkId)
                        .setType(ConceptEventType.valueOf(fields.type))
                        .setTimestamp(fields.timestamp)
                        .setGraph(fields.graph)
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to ConceptEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn(
                    "ConsumerRecord contains unsupported value type for ConceptEvent: " +
                        "${value?.javaClass?.simpleName ?: "null"}, ignoring message",
                )
                null
            }
        }
    }

    private fun extractDatasetEvent(record: ConsumerRecord<String, Any>): DatasetEvent? {
        val value = record.value()
        return when (value) {
            is DatasetEvent -> {
                logger.debug("ConsumerRecord contains DatasetEvent")
                value
            }
            is org.apache.avro.generic.GenericRecord -> {
                logger.debug("Converting GenericRecord to DatasetEvent")
                try {
                    val fields = extractRequiredFields(value, "DatasetEvent")

                    DatasetEvent
                        .newBuilder()
                        .setFdkId(fields.fdkId)
                        .setType(DatasetEventType.valueOf(fields.type))
                        .setTimestamp(fields.timestamp)
                        .setGraph(fields.graph)
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to DatasetEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn(
                    "ConsumerRecord contains unsupported value type for DatasetEvent: " +
                        "${value?.javaClass?.simpleName ?: "null"}, ignoring message",
                )
                null
            }
        }
    }

    private fun extractDataServiceEvent(record: ConsumerRecord<String, Any>): DataServiceEvent? {
        val value = record.value()
        return when (value) {
            is DataServiceEvent -> {
                logger.debug("ConsumerRecord contains DataServiceEvent")
                value
            }
            is org.apache.avro.generic.GenericRecord -> {
                logger.debug("Converting GenericRecord to DataServiceEvent")
                try {
                    val fields = extractRequiredFields(value, "DataServiceEvent")

                    DataServiceEvent
                        .newBuilder()
                        .setFdkId(fields.fdkId)
                        .setType(DataServiceEventType.valueOf(fields.type))
                        .setTimestamp(fields.timestamp)
                        .setGraph(fields.graph)
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to DataServiceEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn(
                    "ConsumerRecord contains unsupported value type for DataServiceEvent: " +
                        "${value?.javaClass?.simpleName ?: "null"}, ignoring message",
                )
                null
            }
        }
    }

    private fun extractEventEvent(record: ConsumerRecord<String, Any>): EventEvent? {
        val value = record.value()
        return when (value) {
            is EventEvent -> {
                logger.debug("ConsumerRecord contains EventEvent")
                value
            }
            is org.apache.avro.generic.GenericRecord -> {
                logger.debug("Converting GenericRecord to EventEvent")
                try {
                    val fields = extractRequiredFields(value, "EventEvent")

                    EventEvent
                        .newBuilder()
                        .setFdkId(fields.fdkId)
                        .setType(EventEventType.valueOf(fields.type))
                        .setTimestamp(fields.timestamp)
                        .setGraph(fields.graph)
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to EventEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn(
                    "ConsumerRecord contains unsupported value type for EventEvent: " +
                        "${value?.javaClass?.simpleName ?: "null"}, ignoring message",
                )
                null
            }
        }
    }

    private fun extractInformationModelEvent(record: ConsumerRecord<String, Any>): InformationModelEvent? {
        val value = record.value()
        return when (value) {
            is InformationModelEvent -> {
                logger.debug("ConsumerRecord contains InformationModelEvent")
                value
            }
            is org.apache.avro.generic.GenericRecord -> {
                logger.debug("Converting GenericRecord to InformationModelEvent")
                try {
                    val fields = extractRequiredFields(value, "InformationModelEvent")

                    InformationModelEvent
                        .newBuilder()
                        .setFdkId(fields.fdkId)
                        .setType(InformationModelEventType.valueOf(fields.type))
                        .setTimestamp(fields.timestamp)
                        .setGraph(fields.graph)
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to InformationModelEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn(
                    "ConsumerRecord contains unsupported value type for InformationModelEvent: " +
                        "${value?.javaClass?.simpleName ?: "null"}, ignoring message",
                )
                null
            }
        }
    }

    private fun extractServiceEvent(record: ConsumerRecord<String, Any>): ServiceEvent? {
        val value = record.value()
        return when (value) {
            is ServiceEvent -> {
                logger.debug("ConsumerRecord contains ServiceEvent")
                value
            }
            is org.apache.avro.generic.GenericRecord -> {
                logger.debug("Converting GenericRecord to ServiceEvent")
                try {
                    val fields = extractRequiredFields(value, "ServiceEvent")

                    ServiceEvent
                        .newBuilder()
                        .setFdkId(fields.fdkId)
                        .setType(ServiceEventType.valueOf(fields.type))
                        .setTimestamp(fields.timestamp)
                        .setGraph(fields.graph)
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to ServiceEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn(
                    "ConsumerRecord contains unsupported value type for ServiceEvent: " +
                        "${value?.javaClass?.simpleName ?: "null"}, ignoring message",
                )
                null
            }
        }
    }

    private fun extractRdfParseEvent(record: ConsumerRecord<String, Any>): RdfParseEvent? {
        val value = record.value()
        return when (value) {
            is RdfParseEvent -> {
                logger.debug("ConsumerRecord contains RdfParseEvent")
                value
            }
            is org.apache.avro.generic.GenericRecord -> {
                logger.debug("Converting GenericRecord to RdfParseEvent")
                try {
                    val (fdkIdStr, timestamp, data) = extractRdfParseRequiredFields(value, "RdfParseEvent")

                    val resourceTypeStr = value.get("resourceType")?.toString()
                    if (resourceTypeStr.isNullOrBlank()) {
                        throw IllegalArgumentException("Missing or empty resourceType in RdfParseEvent")
                    }

                    RdfParseEvent
                        .newBuilder()
                        .setFdkId(fdkIdStr)
                        .setResourceType(RdfParseResourceType.valueOf(resourceTypeStr))
                        .setTimestamp(timestamp)
                        .setData(data)
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to RdfParseEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn(
                    "ConsumerRecord contains unsupported value type for RdfParseEvent: " +
                        "${value?.javaClass?.simpleName ?: "null"}, ignoring message",
                )
                null
            }
        }
    }

    @KafkaListener(topics = ["\${app.kafka.topics.rdf-parse}"], concurrency = "4")
    fun handleRdfParseEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        logger.debug("Received RDF parse event from topic: $topic, partition: $partition, offset: $offset")

        try {
            val rdfParseEvent = extractRdfParseEvent(record)

            if (rdfParseEvent != null) {
                circuitBreakerService.handleRdfParseEvent(rdfParseEvent)
                acknowledgment.acknowledge()
                logger.debug("Successfully processed RDF parse event, acknowledged")
            } else {
                logger.warn("Could not extract RdfParseEvent from message, acknowledging to skip")
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            logger.error("Failed to process RDF parse event", e)
            acknowledgment.nack(Duration.ZERO)
        }
    }

    @KafkaListener(topics = ["\${app.kafka.topics.concept}"], concurrency = "4")
    fun handleConceptEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        logger.debug("Received concept event from topic: $topic, partition: $partition, offset: $offset")

        try {
            val conceptEvent = extractConceptEvent(record)

            if (conceptEvent != null) {
                circuitBreakerService.handleConceptEvent(conceptEvent)
                acknowledgment.acknowledge()
                logger.debug("Successfully processed concept event, acknowledged")
            } else {
                logger.warn("Could not extract ConceptEvent from message, acknowledging to skip")
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            logger.error("Failed to process concept event", e)
            acknowledgment.nack(Duration.ZERO)
        }
    }

    @KafkaListener(topics = ["\${app.kafka.topics.dataset}"], concurrency = "4")
    fun handleDatasetEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        logger.debug("Received dataset event from topic: $topic, partition: $partition, offset: $offset")

        try {
            val datasetEvent = extractDatasetEvent(record)

            if (datasetEvent != null) {
                circuitBreakerService.handleDatasetEvent(datasetEvent)
                acknowledgment.acknowledge()
                logger.debug("Successfully processed dataset event, acknowledged")
            } else {
                logger.warn("Could not extract DatasetEvent from message, acknowledging to skip")
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            logger.error("Failed to process dataset event", e)
            acknowledgment.nack(Duration.ZERO)
        }
    }

    @KafkaListener(topics = ["\${app.kafka.topics.data-service}"], concurrency = "4")
    fun handleDataServiceEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        logger.debug("Received data service event from topic: $topic, partition: $partition, offset: $offset")

        try {
            val dataServiceEvent = extractDataServiceEvent(record)

            if (dataServiceEvent != null) {
                circuitBreakerService.handleDataServiceEvent(dataServiceEvent)
                acknowledgment.acknowledge()
                logger.debug("Successfully processed data service event, acknowledged")
            } else {
                logger.warn("Could not extract DataServiceEvent from message, acknowledging to skip")
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            logger.error("Failed to process data service event", e)
            acknowledgment.nack(Duration.ZERO)
        }
    }

    @KafkaListener(topics = ["\${app.kafka.topics.information-model}"], concurrency = "4")
    fun handleInformationModelEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        logger.debug("Received information model event from topic: $topic, partition: $partition, offset: $offset")

        try {
            val informationModelEvent = extractInformationModelEvent(record)

            if (informationModelEvent != null) {
                circuitBreakerService.handleInformationModelEvent(informationModelEvent)
                acknowledgment.acknowledge()
                logger.debug("Successfully processed information model event, acknowledged")
            } else {
                logger.warn("Could not extract InformationModelEvent from message, acknowledging to skip")
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            logger.error("Failed to process information model event", e)
            acknowledgment.nack(Duration.ZERO)
        }
    }

    @KafkaListener(topics = ["\${app.kafka.topics.service}"], concurrency = "4")
    fun handleServiceEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        logger.debug("Received service event from topic: $topic, partition: $partition, offset: $offset")

        try {
            val serviceEvent = extractServiceEvent(record)

            if (serviceEvent != null) {
                circuitBreakerService.handleServiceEvent(serviceEvent)
                acknowledgment.acknowledge()
                logger.debug("Successfully processed service event, acknowledged")
            } else {
                logger.warn("Could not extract ServiceEvent from message, acknowledging to skip")
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            logger.error("Failed to process service event", e)
            acknowledgment.nack(Duration.ZERO)
        }
    }

    @KafkaListener(topics = ["\${app.kafka.topics.event}"], concurrency = "4")
    fun handleEventEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        logger.debug("Received event event from topic: $topic, partition: $partition, offset: $offset")

        try {
            val eventEvent = extractEventEvent(record)

            if (eventEvent != null) {
                circuitBreakerService.handleEventEvent(eventEvent)
                acknowledgment.acknowledge()
                logger.debug("Successfully processed event event, acknowledged")
            } else {
                logger.warn("Could not extract EventEvent from message, acknowledging to skip")
                acknowledgment.acknowledge()
            }
        } catch (e: Exception) {
            logger.error("Failed to process event event", e)
            acknowledgment.nack(Duration.ZERO)
        }
    }
}
