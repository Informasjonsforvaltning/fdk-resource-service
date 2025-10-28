package no.fdk.resourceservice.config

import org.hibernate.type.format.FormatMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.hibernate.type.descriptor.WrapperOptions
import org.hibernate.type.descriptor.java.JavaType as HibernateJavaType

/**
 * Custom JSON format mapper for Hibernate that uses Kotlin module
 * but explicitly excludes Scala module to prevent Scala collections.
 */
class CustomJacksonJsonFormatMapper : FormatMapper {
    
    private val objectMapper: ObjectMapper = ObjectMapper().apply {
        registerKotlinModule() // No Scala module here!
    }
     
    @Suppress("UNCHECKED_CAST")
    override fun <T : Any?> fromString(
        charSequence: CharSequence?,
        hibernateJavaType: HibernateJavaType<T?>?,
        mutabilityPlan: WrapperOptions?
    ): T? {
        if (charSequence == null || hibernateJavaType == null) {
            return null
        }
        
        // Convert Hibernate JavaType to Jackson JavaType
        val jacksonJavaType = objectMapper.typeFactory.constructType(hibernateJavaType.javaType)
        return objectMapper.readValue(charSequence.toString(), jacksonJavaType) as? T
    }

    override fun <T : Any?> toString(
        value: T?,
        hibernateJavaType: HibernateJavaType<T?>?,
        mutabilityPlan: WrapperOptions?
    ): String? {
        if (value == null) {
            return null
        }
        
        return objectMapper.writeValueAsString(value)
    }
}

