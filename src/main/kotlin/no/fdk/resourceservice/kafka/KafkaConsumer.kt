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
import no.fdk.resourceservice.service.KafkaConsumerMetricsService
import no.fdk.service.ServiceEvent
import no.fdk.service.ServiceEventType
import org.apache.avro.generic.GenericRecord
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
    private val kafkaConsumerMetricsService: KafkaConsumerMetricsService,
) {
    private val logger = LoggerFactory.getLogger(KafkaConsumer::class.java)

    private data class RequiredFields(
        val fdkId: String,
        val type: String,
        val timestamp: Long,
        val graph: String,
        val catalogGraph: String?,
    )

    private data class RdfParseRequiredFields(
        val fdkId: String,
        val timestamp: Long,
        val data: String,
    )

    private fun requireNonBlankString(
        value: GenericRecord,
        fieldName: String,
        eventTypeName: String,
    ): String {
        val fieldValue = value.get(fieldName)?.toString()
        if (fieldValue.isNullOrBlank()) {
            throw IllegalArgumentException("Missing or empty $fieldName in $eventTypeName")
        }
        return fieldValue
    }

    private fun requireLong(
        value: GenericRecord,
        fieldName: String,
        eventTypeName: String,
    ): Long {
        val fieldValue =
            value.get(fieldName) as? Long
                ?: throw IllegalArgumentException("Missing or invalid $fieldName in $eventTypeName")
        return fieldValue
    }

    /**
     * Validates and extracts required fields from a GenericRecord.
     * Throws IllegalArgumentException if any required field is missing or empty.
     * Graph can be empty, so it's not validated.
     */
    private fun extractRequiredFields(
        value: GenericRecord,
        eventTypeName: String,
    ): RequiredFields =
        RequiredFields(
            fdkId = requireNonBlankString(value, "fdkId", eventTypeName),
            type = requireNonBlankString(value, "type", eventTypeName),
            timestamp = requireLong(value, "timestamp", eventTypeName),
            graph = value.get("graph")?.toString() ?: "",
            catalogGraph = value.get("catalogGraph")?.toString(),
        )

    /**
     * Validates and extracts required fields from a GenericRecord for RdfParseEvent.
     * Throws IllegalArgumentException if any required field is missing or empty.
     * Data can be empty, so it's not validated.
     */
    private fun extractRdfParseRequiredFields(
        value: GenericRecord,
        eventTypeName: String,
    ): RdfParseRequiredFields =
        RdfParseRequiredFields(
            fdkId = requireNonBlankString(value, "fdkId", eventTypeName),
            timestamp = requireLong(value, "timestamp", eventTypeName),
            data = value.get("data")?.toString() ?: "",
        )

    private fun <T> processRecord(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        topic: String,
        partition: Int,
        offset: Long,
        receivedLabel: String,
        eventTypeName: String,
        extract: (ConsumerRecord<String, Any>) -> T?,
        process: (T) -> Unit,
    ) {
        logger.debug("Received $receivedLabel from topic: $topic, partition: $partition, offset: $offset")

        try {
            val event = extract(record)

            if (event != null) {
                process(event)
                acknowledgment.acknowledge()
                kafkaConsumerMetricsService.recordOutcome(topic, KafkaConsumerMetricsService.Outcome.ACKED)
                logger.debug("Successfully processed $receivedLabel, acknowledged")
            } else {
                logger.warn("Could not extract $eventTypeName from message, acknowledging to skip")
                acknowledgment.acknowledge()
                kafkaConsumerMetricsService.recordOutcome(topic, KafkaConsumerMetricsService.Outcome.SKIPPED_INVALID)
            }
        } catch (e: Exception) {
            logger.error("Failed to process $receivedLabel", e)
            acknowledgment.nack(Duration.ZERO)
            kafkaConsumerMetricsService.recordOutcome(topic, KafkaConsumerMetricsService.Outcome.NACKED)
        }
    }

    private inline fun <reified T> extractEvent(
        record: ConsumerRecord<String, Any>,
        eventTypeName: String,
        buildFromGeneric: (GenericRecord) -> T,
    ): T? {
        val value = record.value()
        return when (value) {
            is T -> {
                logger.debug("ConsumerRecord contains $eventTypeName")
                value
            }

            is GenericRecord -> {
                logger.debug("Converting GenericRecord to $eventTypeName")
                try {
                    buildFromGeneric(value)
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to $eventTypeName: ${e.message}")
                    null
                }
            }

            else -> {
                logger.warn(
                    "ConsumerRecord contains unsupported value type for $eventTypeName: " +
                        "${value?.javaClass?.simpleName ?: "null"}, ignoring message",
                )
                null
            }
        }
    }

    private fun extractConceptEvent(record: ConsumerRecord<String, Any>): ConceptEvent? =
        extractEvent(record, "ConceptEvent") { value ->
            val fields = extractRequiredFields(value, "ConceptEvent")
            ConceptEvent
                .newBuilder()
                .setFdkId(fields.fdkId)
                .setType(ConceptEventType.valueOf(fields.type))
                .setTimestamp(fields.timestamp)
                .setGraph(fields.graph)
                .setCatalogGraph(fields.catalogGraph)
                .setHarvestRunId(value.get("harvestRunId")?.toString())
                .setUri(value.get("uri")?.toString())
                .build()
        }

    private fun extractDatasetEvent(record: ConsumerRecord<String, Any>): DatasetEvent? =
        extractEvent(record, "DatasetEvent") { value ->
            val fields = extractRequiredFields(value, "DatasetEvent")
            DatasetEvent
                .newBuilder()
                .setFdkId(fields.fdkId)
                .setType(DatasetEventType.valueOf(fields.type))
                .setTimestamp(fields.timestamp)
                .setGraph(fields.graph)
                .setCatalogGraph(fields.catalogGraph)
                .setHarvestRunId(value.get("harvestRunId")?.toString())
                .setUri(value.get("uri")?.toString())
                .build()
        }

    private fun extractDataServiceEvent(record: ConsumerRecord<String, Any>): DataServiceEvent? =
        extractEvent(record, "DataServiceEvent") { value ->
            val fields = extractRequiredFields(value, "DataServiceEvent")
            DataServiceEvent
                .newBuilder()
                .setFdkId(fields.fdkId)
                .setType(DataServiceEventType.valueOf(fields.type))
                .setTimestamp(fields.timestamp)
                .setGraph(fields.graph)
                .setCatalogGraph(fields.catalogGraph)
                .setHarvestRunId(value.get("harvestRunId")?.toString())
                .setUri(value.get("uri")?.toString())
                .build()
        }

    private fun extractEventEvent(record: ConsumerRecord<String, Any>): EventEvent? =
        extractEvent(record, "EventEvent") { value ->
            val fields = extractRequiredFields(value, "EventEvent")
            EventEvent
                .newBuilder()
                .setFdkId(fields.fdkId)
                .setType(EventEventType.valueOf(fields.type))
                .setTimestamp(fields.timestamp)
                .setGraph(fields.graph)
                .setCatalogGraph(fields.catalogGraph)
                .setHarvestRunId(value.get("harvestRunId")?.toString())
                .setUri(value.get("uri")?.toString())
                .build()
        }

    private fun extractInformationModelEvent(record: ConsumerRecord<String, Any>): InformationModelEvent? =
        extractEvent(record, "InformationModelEvent") { value ->
            val fields = extractRequiredFields(value, "InformationModelEvent")
            InformationModelEvent
                .newBuilder()
                .setFdkId(fields.fdkId)
                .setType(InformationModelEventType.valueOf(fields.type))
                .setTimestamp(fields.timestamp)
                .setGraph(fields.graph)
                .setCatalogGraph(fields.catalogGraph)
                .setHarvestRunId(value.get("harvestRunId")?.toString())
                .setUri(value.get("uri")?.toString())
                .build()
        }

    private fun extractServiceEvent(record: ConsumerRecord<String, Any>): ServiceEvent? =
        extractEvent(record, "ServiceEvent") { value ->
            val fields = extractRequiredFields(value, "ServiceEvent")
            ServiceEvent
                .newBuilder()
                .setFdkId(fields.fdkId)
                .setType(ServiceEventType.valueOf(fields.type))
                .setTimestamp(fields.timestamp)
                .setGraph(fields.graph)
                .setCatalogGraph(fields.catalogGraph)
                .setHarvestRunId(value.get("harvestRunId")?.toString())
                .setUri(value.get("uri")?.toString())
                .build()
        }

    private fun extractRdfParseEvent(record: ConsumerRecord<String, Any>): RdfParseEvent? =
        extractEvent(record, "RdfParseEvent") { value ->
            val fields = extractRdfParseRequiredFields(value, "RdfParseEvent")
            val resourceTypeStr = requireNonBlankString(value, "resourceType", "RdfParseEvent")
            RdfParseEvent
                .newBuilder()
                .setFdkId(fields.fdkId)
                .setHarvestRunId(value.get("harvestRunId")?.toString())
                .setUri(value.get("uri")?.toString())
                .setResourceType(RdfParseResourceType.valueOf(resourceTypeStr))
                .setTimestamp(fields.timestamp)
                .setData(fields.data)
                .build()
        }

    @KafkaListener(topics = ["\${app.kafka.topics.rdf-parse}"], concurrency = "4")
    fun handleRdfParseEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        processRecord(
            record = record,
            acknowledgment = acknowledgment,
            topic = topic,
            partition = partition,
            offset = offset,
            receivedLabel = "RDF parse event",
            eventTypeName = "RdfParseEvent",
            extract = ::extractRdfParseEvent,
            process = circuitBreakerService::handleRdfParseEvent,
        )
    }

    @KafkaListener(topics = ["\${app.kafka.topics.concept}"], concurrency = "4")
    fun handleConceptEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        processRecord(
            record = record,
            acknowledgment = acknowledgment,
            topic = topic,
            partition = partition,
            offset = offset,
            receivedLabel = "concept event",
            eventTypeName = "ConceptEvent",
            extract = ::extractConceptEvent,
            process = circuitBreakerService::handleConceptEvent,
        )
    }

    @KafkaListener(topics = ["\${app.kafka.topics.dataset}"], concurrency = "4")
    fun handleDatasetEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        processRecord(
            record = record,
            acknowledgment = acknowledgment,
            topic = topic,
            partition = partition,
            offset = offset,
            receivedLabel = "dataset event",
            eventTypeName = "DatasetEvent",
            extract = ::extractDatasetEvent,
            process = circuitBreakerService::handleDatasetEvent,
        )
    }

    @KafkaListener(topics = ["\${app.kafka.topics.data-service}"], concurrency = "4")
    fun handleDataServiceEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        processRecord(
            record = record,
            acknowledgment = acknowledgment,
            topic = topic,
            partition = partition,
            offset = offset,
            receivedLabel = "data service event",
            eventTypeName = "DataServiceEvent",
            extract = ::extractDataServiceEvent,
            process = circuitBreakerService::handleDataServiceEvent,
        )
    }

    @KafkaListener(topics = ["\${app.kafka.topics.information-model}"], concurrency = "4")
    fun handleInformationModelEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        processRecord(
            record = record,
            acknowledgment = acknowledgment,
            topic = topic,
            partition = partition,
            offset = offset,
            receivedLabel = "information model event",
            eventTypeName = "InformationModelEvent",
            extract = ::extractInformationModelEvent,
            process = circuitBreakerService::handleInformationModelEvent,
        )
    }

    @KafkaListener(topics = ["\${app.kafka.topics.service}"], concurrency = "4")
    fun handleServiceEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        processRecord(
            record = record,
            acknowledgment = acknowledgment,
            topic = topic,
            partition = partition,
            offset = offset,
            receivedLabel = "service event",
            eventTypeName = "ServiceEvent",
            extract = ::extractServiceEvent,
            process = circuitBreakerService::handleServiceEvent,
        )
    }

    @KafkaListener(topics = ["\${app.kafka.topics.event}"], concurrency = "4")
    fun handleEventEvent(
        record: ConsumerRecord<String, Any>,
        acknowledgment: Acknowledgment,
        @Header(KafkaHeaders.RECEIVED_TOPIC) topic: String,
        @Header(KafkaHeaders.RECEIVED_PARTITION) partition: Int,
        @Header(KafkaHeaders.OFFSET) offset: Long,
    ) {
        processRecord(
            record = record,
            acknowledgment = acknowledgment,
            topic = topic,
            partition = partition,
            offset = offset,
            receivedLabel = "event event",
            eventTypeName = "EventEvent",
            extract = ::extractEventEvent,
            process = circuitBreakerService::handleEventEvent,
        )
    }
}
