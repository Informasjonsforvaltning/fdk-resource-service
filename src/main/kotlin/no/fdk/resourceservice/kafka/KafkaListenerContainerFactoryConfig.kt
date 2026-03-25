package no.fdk.resourceservice.kafka

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.env.Environment
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.DefaultKafkaConsumerFactory
import org.springframework.kafka.listener.ContainerProperties

@Configuration
class KafkaListenerContainerFactoryConfig {
    @Bean("kafkaListenerContainerFactory")
    @ConditionalOnMissingBean(name = ["kafkaListenerContainerFactory"])
    fun kafkaListenerContainerFactory(env: Environment): ConcurrentKafkaListenerContainerFactory<String, Any> {
        val bootstrapServers = env.getRequiredProperty("spring.kafka.bootstrap-servers")
        val groupId = env.getRequiredProperty("spring.kafka.consumer.group-id")

        val keyDeserializerClassName = env.getRequiredProperty("spring.kafka.consumer.key-deserializer")
        val valueDeserializerClassName = env.getRequiredProperty("spring.kafka.consumer.value-deserializer")

        val autoOffsetReset = env.getProperty("spring.kafka.consumer.auto-offset-reset") ?: "latest"
        val enableAutoCommit = env.getProperty("spring.kafka.consumer.enable-auto-commit")?.toBoolean() ?: false

        val schemaRegistryUrl =
            env.getRequiredProperty("spring.kafka.consumer.properties.schema.registry.url")

        val consumerProperties =
            hashMapOf<String, Any>(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG to groupId,
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to autoOffsetReset,
                ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to enableAutoCommit,
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to Class.forName(keyDeserializerClassName),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to Class.forName(valueDeserializerClassName),
                "schema.registry.url" to schemaRegistryUrl,
            )

        env.getProperty("spring.kafka.consumer.properties.specific.avro.reader")?.let { specificReader ->
            consumerProperties["specific.avro.reader"] = specificReader.toBoolean()
        }

        val consumerFactory = DefaultKafkaConsumerFactory<String, Any>(consumerProperties)

        val factory = ConcurrentKafkaListenerContainerFactory<String, Any>()
        factory.setConsumerFactory(consumerFactory)

        factory.containerProperties.ackMode = ContainerProperties.AckMode.MANUAL

        env.getProperty("spring.kafka.listener.auto-startup")?.let { factory.setAutoStartup(it.toBoolean()) }

        return factory
    }
}
