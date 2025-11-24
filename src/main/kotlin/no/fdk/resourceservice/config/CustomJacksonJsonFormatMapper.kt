package no.fdk.resourceservice.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.format.FormatMapper
import org.slf4j.LoggerFactory
import java.io.StringWriter
import org.hibernate.type.descriptor.java.JavaType as HibernateJavaType

/**
 * Custom JSON format mapper for Hibernate that uses Kotlin module
 * but explicitly excludes Scala module to prevent Scala collections.
 *
 * This mapper is optimized to handle large objects efficiently and prevent OutOfMemoryError
 * by using streaming serialization and size limits.
 */
class CustomJacksonJsonFormatMapper : FormatMapper {
    private val logger = LoggerFactory.getLogger(CustomJacksonJsonFormatMapper::class.java)
    private val objectMapper: ObjectMapper =
        ObjectMapper().apply {
            registerKotlinModule() // No Scala module here!
        }

    // Maximum size for serialization to prevent OOM (100MB)
    private val maxSerializationSize = 100 * 1024 * 1024L

    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> fromString(
        charSequence: CharSequence?,
        hibernateJavaType: HibernateJavaType<T?>?,
        mutabilityPlan: WrapperOptions?,
    ): T? {
        if (charSequence == null || hibernateJavaType == null) {
            return null
        }

        try {
            // Convert Hibernate JavaType to Jackson JavaType
            val jacksonJavaType = objectMapper.typeFactory.constructType(hibernateJavaType.javaType)
            return objectMapper.readValue(charSequence.toString(), jacksonJavaType) as? T
        } catch (e: Exception) {
            val typeName = (hibernateJavaType.javaType as? Class<*>)?.simpleName ?: hibernateJavaType.javaType.typeName
            logger.error("Failed to deserialize JSON for type $typeName", e)
            throw e
        }
    }

    override fun <T : Any?> toString(
        value: T?,
        hibernateJavaType: HibernateJavaType<T?>?,
        mutabilityPlan: WrapperOptions?,
    ): String? {
        if (value == null) {
            return null
        }

        try {
            // Use streaming writer to avoid loading entire serialized string into memory at once
            // This is more memory-efficient for large objects
            val writer = StringWriter()
            objectMapper.writeValue(writer, value)
            val result = writer.toString()

            // Check size to prevent extremely large serializations
            if (result.length > maxSerializationSize) {
                val typeName =
                    hibernateJavaType?.let {
                        (it.javaType as? Class<*>)?.simpleName ?: it.javaType.typeName
                    } ?: value?.javaClass?.simpleName ?: "unknown"
                logger.warn(
                    "Serialized object is very large ({} bytes), which may cause memory issues. " +
                        "Type: {}, max allowed: {} bytes",
                    result.length,
                    typeName,
                    maxSerializationSize,
                )
            }

            return result
        } catch (e: OutOfMemoryError) {
            val typeName =
                hibernateJavaType?.let {
                    (it.javaType as? Class<*>)?.simpleName ?: it.javaType.typeName
                } ?: value?.javaClass?.simpleName ?: "unknown"
            logger.error(
                "OutOfMemoryError while serializing object of type $typeName. " +
                    "This may indicate the object is too large to serialize in memory.",
                e,
            )
            throw e
        } catch (e: Exception) {
            val typeName =
                hibernateJavaType?.let {
                    (it.javaType as? Class<*>)?.simpleName ?: it.javaType.typeName
                } ?: value?.javaClass?.simpleName ?: "unknown"
            logger.error("Failed to serialize object of type $typeName", e)
            throw e
        }
    }
}
