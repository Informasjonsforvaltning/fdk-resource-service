package no.fdk.resourceservice.kafka

import no.fdk.harvest.HarvestEvent
import org.apache.kafka.clients.producer.ProducerConfig
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
import org.springframework.core.env.Environment
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate

@Configuration
class HarvestKafkaProducerConfig {
    @Bean
    @Primary
    fun harvestEventKafkaTemplate(env: Environment): KafkaTemplate<String, HarvestEvent> {
        val bootstrapServers = env.getRequiredProperty("spring.kafka.bootstrap-servers")
        val keySerializerClassName = env.getRequiredProperty("spring.kafka.producer.key-serializer")
        val valueSerializerClassName = env.getRequiredProperty("spring.kafka.producer.value-serializer")

        val schemaRegistryUrl =
            env.getProperty("spring.kafka.producer.properties.schema.registry.url")
                ?: throw IllegalStateException("Missing spring.kafka.producer.properties.schema.registry.url")

        val producerProperties =
            hashMapOf<String, Any>(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to Class.forName(keySerializerClassName),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to Class.forName(valueSerializerClassName),
                "schema.registry.url" to schemaRegistryUrl,
            )

        val producerFactory = DefaultKafkaProducerFactory<String, HarvestEvent>(producerProperties)
        return KafkaTemplate(producerFactory)
    }
}
