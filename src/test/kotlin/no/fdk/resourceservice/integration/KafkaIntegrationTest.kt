package no.fdk.resourceservice.integration

import no.fdk.concept.ConceptEvent
import no.fdk.concept.ConceptEventType
import no.fdk.dataservice.DataServiceEvent
import no.fdk.dataservice.DataServiceEventType
import no.fdk.dataset.DatasetEvent
import no.fdk.dataset.DatasetEventType
import no.fdk.resourceservice.kafka.KafkaConsumer
import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.service.CircuitBreakerService
import no.fdk.resourceservice.service.ResourceService
import no.fdk.service.ServiceEvent
import no.fdk.service.ServiceEventType
import org.apache.kafka.clients.producer.KafkaProducer
import org.apache.kafka.clients.producer.ProducerRecord
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import java.util.Properties
import java.util.concurrent.TimeUnit

class KafkaIntegrationTest : BaseIntegrationTest() {
    @Autowired
    private lateinit var kafkaConsumer: KafkaConsumer

    @Autowired
    private lateinit var resourceService: ResourceService

    @Autowired
    private lateinit var circuitBreakerService: CircuitBreakerService

    @Test
    fun `should verify Kafka infrastructure is working`() {
        val producerProps =
            Properties().apply {
                put("bootstrap.servers", kafkaContainer.bootstrapServers)
                put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
                put("value.serializer", "org.apache.kafka.common.serialization.StringSerializer")
            }

        val producer = KafkaProducer<String, String>(producerProps)
        producer.use { producer ->
            producer.send(ProducerRecord("test-topic", "test-key", "test-value")).get(5, TimeUnit.SECONDS)
        }
    }

    @Test
    fun `should produce and consume Kafka messages end-to-end`() {
        val topic = "concept-events"
        val resourceId = "test-concept-1"
        val turtleData =
            """
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            
            <https://example.com/test-concept>
                dc:title "Test Concept" ;
                dc:description "A test concept for Kafka integration" ;
                rdf:type <http://example.org/Concept> .
            """.trimIndent()

        val producerProps =
            Properties().apply {
                put("bootstrap.servers", kafkaContainer.bootstrapServers)
                put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
                put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer")
                put("schema.registry.url", "http://${schemaRegistryContainer.host}:${schemaRegistryContainer.getMappedPort(8081)}")
            }

        val producer = KafkaProducer<String, ConceptEvent>(producerProps)

        producer.use { producer ->
            val event =
                ConceptEvent
                    .newBuilder()
                    .setFdkId(resourceId)
                    .setType(ConceptEventType.CONCEPT_REASONED)
                    .setTimestamp(System.currentTimeMillis())
                    .setGraph(turtleData)
                    .setHarvestRunId(null)
                    .setUri(null)
                    .build()

            producer.send(ProducerRecord(topic, resourceId, event)).get(10, TimeUnit.SECONDS)

            Thread.sleep(2000)

            val storedEntity = resourceService.getResourceEntity(resourceId, ResourceType.CONCEPT)
            assertNotNull(storedEntity, "Resource should be stored in database after Kafka message")
            assertNotNull(storedEntity!!.resourceGraphData, "Resource graph data should be stored")
            assertTrue(storedEntity.resourceGraphData!!.contains("https://example.com/test-concept"))
            assertTrue(storedEntity.resourceGraphData.contains("Test Concept"))
        }
    }

    @Test
    fun `should test business logic directly without Kafka`() {
        val resourceId = "test-concept-direct"
        val turtleData =
            """
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            
            <https://example.com/direct-concept>
                dc:title "Direct Concept" ;
                rdf:type <http://example.org/Concept> .
            """.trimIndent()

        val event =
            ConceptEvent
                .newBuilder()
                .setFdkId(resourceId)
                .setType(ConceptEventType.CONCEPT_REASONED)
                .setTimestamp(System.currentTimeMillis())
                .setGraph(turtleData)
                .setHarvestRunId(null)
                .setUri(null)
                .build()

        circuitBreakerService.handleConceptEvent(event)

        val storedEntity = resourceService.getResourceEntity(resourceId, ResourceType.CONCEPT)
        assertNotNull(storedEntity, "Resource entity should be stored after direct REASONED event")
        assertNotNull(storedEntity!!.resourceGraphData, "Resource graph data should be stored")
        assertTrue(storedEntity.resourceGraphData!!.contains("https://example.com/direct-concept"))
        assertTrue(storedEntity.resourceGraphData.contains("Direct Concept"))
    }

    @Test
    fun `should handle single concept resource via Kafka`() {
        val topic = "concept-events"
        val resourceId = "test-concept-simple"
        val turtleData =
            """
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            
            <https://example.com/simple-concept>
                dc:title "Simple Concept" ;
                rdf:type <http://example.org/Concept> .
            """.trimIndent()

        val producerProps =
            Properties().apply {
                put("bootstrap.servers", kafkaContainer.bootstrapServers)
                put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
                put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer")
                put("schema.registry.url", "http://${schemaRegistryContainer.host}:${schemaRegistryContainer.getMappedPort(8081)}")
            }

        val producer = KafkaProducer<String, Any>(producerProps)

        try {
            val event =
                ConceptEvent
                    .newBuilder()
                    .setFdkId(resourceId)
                    .setType(ConceptEventType.CONCEPT_REASONED)
                    .setTimestamp(System.currentTimeMillis())
                    .setGraph(turtleData)
                    .setHarvestRunId(null)
                    .setUri(null)
                    .build()

            producer.send(ProducerRecord(topic, resourceId, event)).get(10, TimeUnit.SECONDS)

            var storedEntity: ResourceEntity? = null
            for (attempt in 1..30) {
                Thread.sleep(1000)
                storedEntity = resourceService.getResourceEntity(resourceId, ResourceType.CONCEPT)
                if (storedEntity != null) break
            }

            assertNotNull(storedEntity, "Resource should be stored in database within 30 seconds")
            assertNotNull(storedEntity!!.resourceGraphData, "Resource graph data should be stored")
            assertTrue(storedEntity.resourceGraphData!!.contains("https://example.com/simple-concept"))
            assertTrue(storedEntity.resourceGraphData.contains("Simple Concept"))
        } finally {
            producer.close()
        }
    }

    @Test
    fun `should handle multiple resource types via Kafka`() {
        data class ResourceTestData(
            val topic: String,
            val resourceType: ResourceType,
            val resourceUri: String,
            val expectedTitle: String,
        )

        val resources =
            listOf(
                ResourceTestData("dataset-events", ResourceType.DATASET, "https://example.com/dataset", "Test Dataset"),
                ResourceTestData("data-service-events", ResourceType.DATA_SERVICE, "https://example.com/data-service", "Test Data Service"),
                ResourceTestData("service-events", ResourceType.SERVICE, "https://example.com/service", "Test Service"),
            )

        val producerProps =
            Properties().apply {
                put("bootstrap.servers", kafkaContainer.bootstrapServers)
                put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
                put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer")
                put("schema.registry.url", "http://${schemaRegistryContainer.host}:${schemaRegistryContainer.getMappedPort(8081)}")
            }

        val producer = KafkaProducer<String, Any>(producerProps)

        try {
            resources.forEach { testData ->
                val resourceId = "test-${testData.resourceType.name.lowercase()}-1"
                val turtleData =
                    """
                    @prefix dc: <http://purl.org/dc/elements/1.1/> .
                    @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
                    
                    <${testData.resourceUri}>
                        dc:title "${testData.expectedTitle}" ;
                        rdf:type <http://example.org/${testData.resourceType.name}> .
                    """.trimIndent()

                val event: Any =
                    when (testData.resourceType) {
                        ResourceType.DATASET ->
                            DatasetEvent
                                .newBuilder()
                                .setFdkId(resourceId)
                                .setType(DatasetEventType.DATASET_REASONED)
                                .setTimestamp(System.currentTimeMillis())
                                .setGraph(turtleData)
                                .setHarvestRunId(null)
                                .setUri(null)
                                .build()
                        ResourceType.DATA_SERVICE ->
                            DataServiceEvent
                                .newBuilder()
                                .setFdkId(resourceId)
                                .setType(DataServiceEventType.DATA_SERVICE_REASONED)
                                .setTimestamp(System.currentTimeMillis())
                                .setGraph(turtleData)
                                .setHarvestRunId(null)
                                .setUri(null)
                                .build()
                        ResourceType.SERVICE ->
                            ServiceEvent
                                .newBuilder()
                                .setFdkId(resourceId)
                                .setType(ServiceEventType.SERVICE_REASONED)
                                .setTimestamp(System.currentTimeMillis())
                                .setGraph(turtleData)
                                .setHarvestRunId(null)
                                .setUri(null)
                                .build()
                        else -> throw IllegalArgumentException("Unsupported resource type: ${testData.resourceType}")
                    }

                producer.send(ProducerRecord(testData.topic, resourceId, event)).get(10, TimeUnit.SECONDS)
            }

            Thread.sleep(10000)

            resources.forEach { testData ->
                val resourceId = "test-${testData.resourceType.name.lowercase()}-1"
                val storedEntity = resourceService.getResourceEntity(resourceId, testData.resourceType)
                assertNotNull(storedEntity, "Resource $resourceId should be stored in database")
                assertNotNull(storedEntity!!.resourceGraphData, "Graph data for $resourceId should be stored")
                assertTrue(storedEntity.resourceGraphData!!.contains(testData.resourceUri))
                assertTrue(storedEntity.resourceGraphData.contains(testData.expectedTitle))
            }
        } finally {
            producer.close()
        }
    }

    @Test
    fun `should handle resource updates via Kafka`() {
        val topic = "concept-events"
        val resourceId = "test-concept-update"
        val initialTurtleData =
            """
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            
            <https://example.com/concept>
                dc:title "Initial Title" ;
                rdf:type <http://example.org/Concept> .
            """.trimIndent()

        val updatedTurtleData =
            """
            @prefix dc: <http://purl.org/dc/elements/1.1/> .
            @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
            
            <https://example.com/concept>
                dc:title "Updated Title" ;
                rdf:type <http://example.org/Concept> .
            """.trimIndent()

        val producerProps =
            Properties().apply {
                put("bootstrap.servers", kafkaContainer.bootstrapServers)
                put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer")
                put("value.serializer", "io.confluent.kafka.serializers.KafkaAvroSerializer")
                put("schema.registry.url", "http://${schemaRegistryContainer.host}:${schemaRegistryContainer.getMappedPort(8081)}")
            }

        val producer = KafkaProducer<String, ConceptEvent>(producerProps)

        producer.use { producer ->
            val createEvent =
                ConceptEvent
                    .newBuilder()
                    .setFdkId(resourceId)
                    .setType(ConceptEventType.CONCEPT_REASONED)
                    .setTimestamp(System.currentTimeMillis())
                    .setGraph(initialTurtleData)
                    .setHarvestRunId(null)
                    .setUri(null)
                    .build()

            producer.send(ProducerRecord(topic, resourceId, createEvent)).get(10, TimeUnit.SECONDS)
            Thread.sleep(2000)

            val initialEntity = resourceService.getResourceEntity(resourceId, ResourceType.CONCEPT)
            assertNotNull(initialEntity, "Initial resource should be stored")
            assertNotNull(initialEntity!!.resourceGraphData, "Initial graph data should be stored")
            assertTrue(initialEntity.resourceGraphData!!.contains("https://example.com/concept"))
            assertTrue(initialEntity.resourceGraphData.contains("Initial Title"))

            val updateEvent =
                ConceptEvent
                    .newBuilder()
                    .setFdkId(resourceId)
                    .setType(ConceptEventType.CONCEPT_REASONED)
                    .setTimestamp(System.currentTimeMillis() + 1000)
                    .setGraph(updatedTurtleData)
                    .setHarvestRunId(null)
                    .setUri(null)
                    .build()

            producer.send(ProducerRecord(topic, resourceId, updateEvent)).get(10, TimeUnit.SECONDS)
            Thread.sleep(2000)

            val updatedEntity = resourceService.getResourceEntity(resourceId, ResourceType.CONCEPT)
            assertNotNull(updatedEntity, "Updated resource should be stored")
            assertNotNull(updatedEntity!!.resourceGraphData, "Updated graph data should be stored")
            assertTrue(updatedEntity.resourceGraphData!!.contains("https://example.com/concept"))
            assertTrue(updatedEntity.resourceGraphData.contains("Updated Title"))
        }
    }
}
