package no.fdk.resourceservice.kafka

import no.fdk.concept.ConceptEvent
import no.fdk.concept.ConceptEventType
import no.fdk.dataset.DatasetEvent
import no.fdk.dataset.DatasetEventType
import no.fdk.dataservice.DataServiceEvent
import no.fdk.dataservice.DataServiceEventType
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
    private val circuitBreakerService: CircuitBreakerService
) {
    private val logger = LoggerFactory.getLogger(KafkaConsumer::class.java)
    
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
                    ConceptEvent.newBuilder()
                        .setFdkId(value.get("fdkId")?.toString() ?: "")
                        .setType(ConceptEventType.valueOf(value.get("type")?.toString() ?: "CONCEPT_HARVESTED"))
                        .setTimestamp(value.get("timestamp") as? Long ?: System.currentTimeMillis())
                        .setGraph(value.get("graph")?.toString() ?: "")
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to ConceptEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn("ConsumerRecord contains unsupported value type for ConceptEvent: ${value?.javaClass?.simpleName ?: "null"}, ignoring message")
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
                    DatasetEvent.newBuilder()
                        .setFdkId(value.get("fdkId")?.toString() ?: "")
                        .setType(DatasetEventType.valueOf(value.get("type")?.toString() ?: "DATASET_HARVESTED"))
                        .setTimestamp(value.get("timestamp") as? Long ?: System.currentTimeMillis())
                        .setGraph(value.get("graph")?.toString() ?: "")
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to DatasetEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn("ConsumerRecord contains unsupported value type for DatasetEvent: ${value?.javaClass?.simpleName ?: "null"}, ignoring message")
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
                    DataServiceEvent.newBuilder()
                        .setFdkId(value.get("fdkId")?.toString() ?: "")
                        .setType(DataServiceEventType.valueOf(value.get("type")?.toString() ?: "DATA_SERVICE_HARVESTED"))
                        .setTimestamp(value.get("timestamp") as? Long ?: System.currentTimeMillis())
                        .setGraph(value.get("graph")?.toString() ?: "")
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to DataServiceEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn("ConsumerRecord contains unsupported value type for DataServiceEvent: ${value?.javaClass?.simpleName ?: "null"}, ignoring message")
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
                    EventEvent.newBuilder()
                        .setFdkId(value.get("fdkId")?.toString() ?: "")
                        .setType(EventEventType.valueOf(value.get("type")?.toString() ?: "EVENT_HARVESTED"))
                        .setTimestamp(value.get("timestamp") as? Long ?: System.currentTimeMillis())
                        .setGraph(value.get("graph")?.toString() ?: "")
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to EventEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn("ConsumerRecord contains unsupported value type for EventEvent: ${value?.javaClass?.simpleName ?: "null"}, ignoring message")
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
                    InformationModelEvent.newBuilder()
                        .setFdkId(value.get("fdkId")?.toString() ?: "")
                        .setType(InformationModelEventType.valueOf(value.get("type")?.toString() ?: "INFORMATION_MODEL_HARVESTED"))
                        .setTimestamp(value.get("timestamp") as? Long ?: System.currentTimeMillis())
                        .setGraph(value.get("graph")?.toString() ?: "")
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to InformationModelEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn("ConsumerRecord contains unsupported value type for InformationModelEvent: ${value?.javaClass?.simpleName ?: "null"}, ignoring message")
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
                    ServiceEvent.newBuilder()
                        .setFdkId(value.get("fdkId")?.toString() ?: "")
                        .setType(ServiceEventType.valueOf(value.get("type")?.toString() ?: "SERVICE_HARVESTED"))
                        .setTimestamp(value.get("timestamp") as? Long ?: System.currentTimeMillis())
                        .setGraph(value.get("graph")?.toString() ?: "")
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to ServiceEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn("ConsumerRecord contains unsupported value type for ServiceEvent: ${value?.javaClass?.simpleName ?: "null"}, ignoring message")
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
                    val resourceTypeStr = value.get("resourceType")?.toString()
                    if (resourceTypeStr.isNullOrBlank()) {
                        logger.warn("Missing or empty resourceType in RdfParseEvent, ignoring message")
                        return null
                    }
                    
                    RdfParseEvent.newBuilder()
                        .setFdkId(value.get("fdkId")?.toString() ?: "")
                        .setResourceType(RdfParseResourceType.valueOf(resourceTypeStr))
                        .setTimestamp(value.get("timestamp") as? Long ?: System.currentTimeMillis())
                        .setData(value.get("data")?.toString() ?: "")
                        .build()
                } catch (e: Exception) {
                    logger.warn("Failed to convert GenericRecord to RdfParseEvent: ${e.message}")
                    null
                }
            }
            else -> {
                logger.warn("ConsumerRecord contains unsupported value type for RdfParseEvent: ${value?.javaClass?.simpleName ?: "null"}, ignoring message")
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
        @Header(KafkaHeaders.OFFSET) offset: Long
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
        @Header(KafkaHeaders.OFFSET) offset: Long
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
        @Header(KafkaHeaders.OFFSET) offset: Long
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
        @Header(KafkaHeaders.OFFSET) offset: Long
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
        @Header(KafkaHeaders.OFFSET) offset: Long
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
        @Header(KafkaHeaders.OFFSET) offset: Long
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
        @Header(KafkaHeaders.OFFSET) offset: Long
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
