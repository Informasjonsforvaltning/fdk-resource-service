package no.fdk.resourceservice.kafka

import no.fdk.harvest.DataType
import no.fdk.harvest.HarvestEvent
import no.fdk.harvest.HarvestPhase
import no.fdk.resourceservice.model.ResourceType
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Service
import java.time.Instant

/**
 * Service for producing harvest events to Kafka.
 *
 * This service sends harvest events when resource processing is finished,
 * removed, or failed, using the harvestRunId from the source event.
 */
@Service
class HarvestEventProducer(
    private val kafkaTemplate: KafkaTemplate<String, HarvestEvent>,
    @Value("\${app.kafka.topics.harvest}") private val harvestTopic: String,
) {
    private val logger = LoggerFactory.getLogger(HarvestEventProducer::class.java)

    /**
     * Maps ResourceType to HarvestEvent DataType enum.
     * Note: Avro enum values match the schema symbols exactly (case-sensitive).
     */
    private fun mapResourceTypeToDataType(resourceType: ResourceType): DataType {
        // Avro generates enums with exact case from schema, so we use valueOf with exact symbol names
        return when (resourceType) {
            ResourceType.CONCEPT -> DataType.valueOf("concept")
            ResourceType.DATASET -> DataType.valueOf("dataset")
            ResourceType.INFORMATION_MODEL -> DataType.valueOf("informationmodel")
            ResourceType.DATA_SERVICE -> DataType.valueOf("dataservice")
            ResourceType.SERVICE -> DataType.valueOf("publicService")
            ResourceType.EVENT -> DataType.valueOf("event")
        }
    }

    /**
     * Produces a harvest event when resource processing is finished successfully.
     *
     * @param harvestRunId The harvest run ID from the source event
     * @param resourceType The type of resource that was processed
     * @param fdkId The FDK ID of the resource
     * @param resourceUri The URI of the resource (optional)
     * @param startTime The timestamp when processing started (start of update process)
     * @param endTime The timestamp when processing finished (current time)
     */
    fun produceResourceFinishedEvent(
        harvestRunId: String?,
        resourceType: ResourceType,
        fdkId: String,
        resourceUri: String?,
        startTime: Long,
        endTime: Long,
    ) {
        if (harvestRunId == null) {
            logger.debug("Skipping harvest event production: harvestRunId is null for resource $fdkId")
            return
        }

        try {
            val dataType = mapResourceTypeToDataType(resourceType)
            val startTimeStr = Instant.ofEpochMilli(startTime).toString()
            val endTimeStr = Instant.ofEpochMilli(endTime).toString()

            val harvestEvent =
                HarvestEvent
                    .newBuilder()
                    .setPhase(HarvestPhase.RESOURCE_PROCESSING)
                    .setRunId(harvestRunId)
                    .setDataType(dataType)
                    .setFdkId(fdkId)
                    .setResourceUri(resourceUri)
                    .setStartTime(startTimeStr)
                    .setEndTime(endTimeStr)
                    .setDataSourceId(null)
                    .setDataSourceUrl(null)
                    .setAcceptHeader(null)
                    .setErrorMessage(null)
                    .setChangedResourcesCount(null)
                    .setUnchangedResourcesCount(null)
                    .setRemovedResourcesCount(null)
                    .build()

            kafkaTemplate.send(harvestTopic, harvestRunId, harvestEvent)
            logger.debug("Produced harvest event: runId=$harvestRunId, fdkId=$fdkId, type=$resourceType, phase=RESOURCE_PROCESSING")
        } catch (e: Exception) {
            logger.error("Failed to produce harvest event for resource $fdkId", e)
            // Don't throw - we don't want harvest event production failures to break resource processing
        }
    }

    /**
     * Produces a harvest event when a resource is removed.
     *
     * @param harvestRunId The harvest run ID from the source event
     * @param resourceType The type of resource that was removed
     * @param fdkId The FDK ID of the resource
     * @param resourceUri The URI of the resource (optional)
     * @param startTime The timestamp when processing started (start of update process)
     * @param endTime The timestamp when processing finished (current time)
     */
    fun produceResourceRemovedEvent(
        harvestRunId: String?,
        resourceType: ResourceType,
        fdkId: String,
        resourceUri: String?,
        startTime: Long,
        endTime: Long,
    ) {
        if (harvestRunId == null) {
            logger.debug("Skipping harvest event production: harvestRunId is null for resource $fdkId")
            return
        }

        try {
            val dataType = mapResourceTypeToDataType(resourceType)
            val startTimeStr = Instant.ofEpochMilli(startTime).toString()
            val endTimeStr = Instant.ofEpochMilli(endTime).toString()

            val harvestEvent =
                HarvestEvent
                    .newBuilder()
                    .setPhase(HarvestPhase.RESOURCE_PROCESSING)
                    .setRunId(harvestRunId)
                    .setDataType(dataType)
                    .setFdkId(fdkId)
                    .setResourceUri(resourceUri)
                    .setStartTime(startTimeStr)
                    .setEndTime(endTimeStr)
                    .setDataSourceId(null)
                    .setDataSourceUrl(null)
                    .setAcceptHeader(null)
                    .setErrorMessage(null)
                    .setChangedResourcesCount(null)
                    .setUnchangedResourcesCount(null)
                    .setRemovedResourcesCount(null)
                    .build()

            kafkaTemplate.send(harvestTopic, harvestRunId, harvestEvent)
            logger.debug(
                "Produced harvest event: runId=$harvestRunId, fdkId=$fdkId, type=$resourceType, phase=RESOURCE_PROCESSING (removed)",
            )
        } catch (e: Exception) {
            logger.error("Failed to produce harvest event for removed resource $fdkId", e)
            // Don't throw - we don't want harvest event production failures to break resource processing
        }
    }

    /**
     * Produces a harvest event when resource processing fails.
     *
     * @param harvestRunId The harvest run ID from the source event
     * @param resourceType The type of resource that failed
     * @param fdkId The FDK ID of the resource
     * @param resourceUri The URI of the resource (optional)
     * @param startTime The timestamp when processing started (start of update process)
     * @param endTime The timestamp when processing failed (current time)
     * @param errorMessage The error message describing the failure
     */
    fun produceResourceFailedEvent(
        harvestRunId: String?,
        resourceType: ResourceType,
        fdkId: String,
        resourceUri: String?,
        startTime: Long,
        endTime: Long,
        errorMessage: String,
    ) {
        if (harvestRunId == null) {
            logger.debug("Skipping harvest event production: harvestRunId is null for resource $fdkId")
            return
        }

        try {
            val dataType = mapResourceTypeToDataType(resourceType)
            val startTimeStr = Instant.ofEpochMilli(startTime).toString()
            val endTimeStr = Instant.ofEpochMilli(endTime).toString()

            val harvestEvent =
                HarvestEvent
                    .newBuilder()
                    .setPhase(HarvestPhase.RESOURCE_PROCESSING)
                    .setRunId(harvestRunId)
                    .setDataType(dataType)
                    .setFdkId(fdkId)
                    .setResourceUri(resourceUri)
                    .setStartTime(startTimeStr)
                    .setEndTime(endTimeStr)
                    .setErrorMessage(errorMessage)
                    .setDataSourceId(null)
                    .setDataSourceUrl(null)
                    .setAcceptHeader(null)
                    .setChangedResourcesCount(null)
                    .setUnchangedResourcesCount(null)
                    .setRemovedResourcesCount(null)
                    .build()

            kafkaTemplate.send(harvestTopic, harvestRunId, harvestEvent)
            logger.debug(
                "Produced harvest event: runId=$harvestRunId, fdkId=$fdkId, type=$resourceType, phase=RESOURCE_PROCESSING (failed)",
            )
        } catch (e: Exception) {
            logger.error("Failed to produce harvest event for failed resource $fdkId", e)
            // Don't throw - we don't want harvest event production failures to break resource processing
        }
    }
}

