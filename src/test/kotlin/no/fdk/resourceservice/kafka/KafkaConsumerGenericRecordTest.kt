package no.fdk.resourceservice.kafka

import no.fdk.concept.ConceptEvent
import no.fdk.concept.ConceptEventType
import no.fdk.dataset.DatasetEvent
import no.fdk.dataset.DatasetEventType
import no.fdk.rdf.parse.RdfParseEvent
import no.fdk.rdf.parse.RdfParseResourceType
import no.fdk.resourceservice.service.CircuitBreakerService
import org.apache.avro.generic.GenericData
import org.apache.avro.generic.GenericRecord
import org.apache.kafka.clients.consumer.ConsumerRecord
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.springframework.kafka.support.Acknowledgment

@ExtendWith(MockitoExtension::class)
class KafkaConsumerGenericRecordTest {
    @Mock
    private lateinit var circuitBreakerService: CircuitBreakerService

    @Mock
    private lateinit var acknowledgment: Acknowledgment

    private lateinit var kafkaConsumer: KafkaConsumer

    @BeforeEach
    fun setUp() {
        kafkaConsumer = KafkaConsumer(circuitBreakerService)
    }

    @Test
    fun `should convert GenericRecord to ConceptEvent with catalogGraph and delegate`() {
        val timestamp = 1_700_000_000_000L
        val genericRecord =
            conceptGenericRecord(
                fdkId = "concept-from-generic",
                type = ConceptEventType.CONCEPT_REASONED.name,
                graph = "resource-turtle",
                catalogGraph = "catalog-turtle",
                timestamp = timestamp,
                harvestRunId = "harvest-123",
                uri = "https://example.com/concept/1",
            )
        val record = consumerRecord(genericRecord)

        kafkaConsumer.handleConceptEvent(record, acknowledgment, "concept-topic", 0, 0L)

        val captor = argumentCaptor<ConceptEvent>()
        verify(circuitBreakerService, times(1)).handleConceptEvent(captor.capture())
        verify(acknowledgment, times(1)).acknowledge()

        val event = captor.firstValue
        assertEquals("concept-from-generic", event.fdkId)
        assertEquals(ConceptEventType.CONCEPT_REASONED, event.type)
        assertEquals("resource-turtle", event.graph)
        assertEquals("catalog-turtle", event.catalogGraph)
        assertEquals(timestamp, event.timestamp)
        assertEquals("harvest-123", event.harvestRunId)
        assertEquals("https://example.com/concept/1", event.uri)
    }

    @Test
    fun `should convert GenericRecord to DatasetEvent without catalogGraph`() {
        val timestamp = 1_700_000_001_000L
        val genericRecord =
            datasetGenericRecord(
                fdkId = "dataset-from-generic",
                type = DatasetEventType.DATASET_REASONED.name,
                graph = "dataset-turtle",
                catalogGraph = null,
                timestamp = timestamp,
            )
        val record = consumerRecord(genericRecord)

        kafkaConsumer.handleDatasetEvent(record, acknowledgment, "dataset-topic", 0, 0L)

        val captor = argumentCaptor<DatasetEvent>()
        verify(circuitBreakerService, times(1)).handleDatasetEvent(captor.capture())
        verify(acknowledgment, times(1)).acknowledge()

        val event = captor.firstValue
        assertEquals("dataset-from-generic", event.fdkId)
        assertEquals(DatasetEventType.DATASET_REASONED, event.type)
        assertEquals("dataset-turtle", event.graph)
        assertNull(event.catalogGraph)
        assertEquals(timestamp, event.timestamp)
    }

    @Test
    fun `should acknowledge and skip ConceptEvent when GenericRecord is missing required fields`() {
        val schema = ConceptEvent.getClassSchema()
        val genericRecord = GenericData.Record(schema)
        genericRecord.put("type", enumSymbol(schema, "type", ConceptEventType.CONCEPT_REASONED.name))
        genericRecord.put("graph", "test-graph")
        genericRecord.put("timestamp", 1_700_000_000_000L)
        val record = consumerRecord(genericRecord)

        kafkaConsumer.handleConceptEvent(record, acknowledgment, "concept-topic", 0, 0L)

        verify(circuitBreakerService, never()).handleConceptEvent(any())
        verify(acknowledgment, times(1)).acknowledge()
    }

    @Test
    fun `should convert GenericRecord to RdfParseEvent and delegate`() {
        val timestamp = 1_700_000_002_000L
        val genericRecord =
            rdfParseGenericRecord(
                fdkId = "rdf-from-generic",
                resourceType = RdfParseResourceType.CONCEPT.name,
                data = "parsed-turtle",
                timestamp = timestamp,
                harvestRunId = "harvest-456",
                uri = "https://example.com/rdf/1",
            )
        val record = consumerRecord(genericRecord)

        kafkaConsumer.handleRdfParseEvent(record, acknowledgment, "rdf-parse-topic", 0, 0L)

        val captor = argumentCaptor<RdfParseEvent>()
        verify(circuitBreakerService, times(1)).handleRdfParseEvent(captor.capture())
        verify(acknowledgment, times(1)).acknowledge()

        val event = captor.firstValue
        assertEquals("rdf-from-generic", event.fdkId)
        assertEquals(RdfParseResourceType.CONCEPT, event.resourceType)
        assertEquals("parsed-turtle", event.data)
        assertEquals(timestamp, event.timestamp)
        assertEquals("harvest-456", event.harvestRunId)
        assertEquals("https://example.com/rdf/1", event.uri)
    }

    private fun consumerRecord(value: Any): ConsumerRecord<String, Any> = ConsumerRecord("test-topic", 0, 0L, "test-key", value)

    private fun conceptGenericRecord(
        fdkId: String,
        type: String,
        graph: String,
        catalogGraph: String?,
        timestamp: Long,
        harvestRunId: String? = null,
        uri: String? = null,
    ): GenericRecord {
        val schema = ConceptEvent.getClassSchema()
        val record = GenericData.Record(schema)
        record.put("type", enumSymbol(schema, "type", type))
        record.put("fdkId", fdkId)
        record.put("graph", graph)
        record.put("timestamp", timestamp)
        record.put("catalogGraph", catalogGraph)
        record.put("harvestRunId", harvestRunId)
        record.put("uri", uri)
        return record
    }

    private fun datasetGenericRecord(
        fdkId: String,
        type: String,
        graph: String,
        catalogGraph: String?,
        timestamp: Long,
    ): GenericRecord {
        val schema = DatasetEvent.getClassSchema()
        val record = GenericData.Record(schema)
        record.put("type", enumSymbol(schema, "type", type))
        record.put("fdkId", fdkId)
        record.put("graph", graph)
        record.put("timestamp", timestamp)
        record.put("catalogGraph", catalogGraph)
        return record
    }

    private fun rdfParseGenericRecord(
        fdkId: String,
        resourceType: String,
        data: String,
        timestamp: Long,
        harvestRunId: String? = null,
        uri: String? = null,
    ): GenericRecord {
        val schema = RdfParseEvent.getClassSchema()
        val record = GenericData.Record(schema)
        record.put("resourceType", enumSymbol(schema, "resourceType", resourceType))
        record.put("fdkId", fdkId)
        record.put("data", data)
        record.put("timestamp", timestamp)
        record.put("harvestRunId", harvestRunId)
        record.put("uri", uri)
        return record
    }

    private fun enumSymbol(
        schema: org.apache.avro.Schema,
        fieldName: String,
        symbol: String,
    ): GenericData.EnumSymbol = GenericData.EnumSymbol(schema.getField(fieldName).schema(), symbol)
}
