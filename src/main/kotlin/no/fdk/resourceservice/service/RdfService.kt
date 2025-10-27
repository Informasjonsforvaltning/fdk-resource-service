package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.StringWriter
import java.io.ByteArrayInputStream

/**
 * Unified service for RDF processing, format conversion, and content negotiation.
 * 
 * This service combines the functionality of both RdfProcessingService and RdfFormatService
 * into a single, comprehensive service that handles all RDF-related operations.
 */
@Service
class RdfService(
    private val objectMapper: ObjectMapper
) {
    private val logger = LoggerFactory.getLogger(RdfService::class.java)

    /**
     * Supported RDF formats and their corresponding media types.
     */
    enum class RdfFormat(val mediaType: MediaType, val fileExtension: String) {
        JSON_LD(MediaType("application", "ld+json"), "jsonld"),
        TURTLE(MediaType("text", "turtle"), "ttl"),
        RDF_XML(MediaType("application", "rdf+xml"), "rdf"),
        N_TRIPLES(MediaType("application", "n-triples"), "nt"),
        N_QUADS(MediaType("application", "n-quads"), "nq")
    }

    /**
     * RDF format style options for all RDF serializations.
     */
    enum class RdfFormatStyle {
        /**
         * Pretty format with namespace prefixes, human-readable.
         */
        PRETTY,
        
        /**
         * Standard format with namespace prefixes and compact representation.
         */
        STANDARD
    }

    /**
     * Determines the best RDF format based on the Accept header.
     *
     * @param acceptHeader The value of the Accept HTTP header.
     * @return The best matching RdfFormat, or JSON_LD if no match or header is null/empty.
     */
    fun getBestFormat(acceptHeader: String?): RdfFormat {
        if (acceptHeader.isNullOrBlank()) {
            return RdfFormat.JSON_LD
        }

        val acceptTypes = acceptHeader.split(',')
            .map { it.trim().lowercase() }
            .filter { it.isNotBlank() }

        // Prioritize exact matches
        for (acceptType in acceptTypes) {
            for (format in RdfFormat.entries) {
                if (format.mediaType.toString() == acceptType) {
                    return format
                }
            }
        }

        // Fallback to JSON_LD if no specific match
        return RdfFormat.JSON_LD
    }

    /**
     * Returns the MediaType for a given RdfFormat.
     *
     * @param format The RdfFormat.
     * @return The corresponding MediaType.
     */
    fun getContentType(format: RdfFormat): MediaType {
        return format.mediaType
    }

    /**
     * Converts from Turtle format to any target format.
     */
    fun convertFromTurtle(turtleData: String, toFormat: RdfFormat, style: RdfFormatStyle, expandUris: Boolean = false): String? {
        val model = ModelFactory.createDefaultModel()
        val inputStream = ByteArrayInputStream(turtleData.toByteArray())
        RDFDataMgr.read(model, inputStream, Lang.TURTLE)

        if (expandUris) {
            model.clearNsPrefixMap()
        }

        val rdfFormat = getRdfFormat(toFormat, style)
        val outputStream = StringWriter()
        RDFDataMgr.write(outputStream, model, rdfFormat)
        
        return handleSpecialCases(outputStream.toString(), rdfFormat)
    }

    /**
     * Converts from JSON-LD format to any target format.
     */
    fun convertFromJsonLd(jsonLdData: Map<String, Any>, toFormat: RdfFormat, style: RdfFormatStyle, expandUris: Boolean = false): String? {
        // Handle JSON-LD pretty printing (no RDF conversion needed)
        if (toFormat == RdfFormat.JSON_LD && style == RdfFormatStyle.PRETTY) {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonLdData)
        }

        // Convert Map to JSON string and parse to model
        val jsonString = objectMapper.writeValueAsString(jsonLdData)
        val model = ModelFactory.createDefaultModel()
        val inputStream = ByteArrayInputStream(jsonString.toByteArray())
        RDFDataMgr.read(model, inputStream, Lang.JSONLD)

        if(expandUris) {
            model.clearNsPrefixMap()
        }

        val rdfFormat = getRdfFormat(toFormat, style)
        val outputStream = StringWriter()
        RDFDataMgr.write(outputStream, model, rdfFormat)
        
        return handleSpecialCases(outputStream.toString(), rdfFormat)
    }

    /**
     * Maps format and style enums to RDFFormat enum.
     */
    private fun getRdfFormat(format: RdfFormat, style: RdfFormatStyle): RDFFormat {
        return when (format) {
            RdfFormat.TURTLE -> if (style == RdfFormatStyle.PRETTY) RDFFormat.TURTLE_PRETTY else RDFFormat.TURTLE
            RdfFormat.RDF_XML -> if (style == RdfFormatStyle.PRETTY) RDFFormat.RDFXML_PRETTY else RDFFormat.RDFXML
            RdfFormat.N_TRIPLES -> RDFFormat.NTRIPLES
            RdfFormat.N_QUADS -> RDFFormat.NQUADS
            RdfFormat.JSON_LD -> if (style == RdfFormatStyle.PRETTY) RDFFormat.JSONLD_PRETTY else RDFFormat.JSONLD
        }
    }

    /**
     * Converts Turtle RDF to JSON-LD Map.
     * 
     * This method converts Turtle RDF data to JSON-LD format and returns it as a Map.
     * It's specifically designed for storing in the database where JSON-LD data is expected as Map<String, Any>.
     * 
     * @param turtleData The Turtle RDF data as a string
     * @param expandUris Whether to expand URIs (clear namespace prefixes, default: true for expanded URIs)
     * @return JSON-LD data as Map<String, Any>, or empty map if conversion fails
     */
    fun convertTurtleToJsonLdMap(turtleData: String, expandUris: Boolean = true): Map<String, Any> {
        return try {
            logger.debug("Converting Turtle to JSON-LD Map (expandUris: $expandUris)")
            
            // Convert Turtle to JSON-LD string
            val jsonLdString = convertFromTurtle(turtleData, RdfFormat.JSON_LD, RdfFormatStyle.PRETTY, expandUris)
            
            if (jsonLdString != null) {
                // Parse JSON-LD string to Map
                @Suppress("UNCHECKED_CAST")
                val jsonLdMap = objectMapper.readValue(jsonLdString, Map::class.java) as Map<String, Any>
                logger.debug("Successfully converted Turtle to JSON-LD Map")
                jsonLdMap
            } else {
                logger.warn("Turtle to JSON-LD conversion returned null, returning empty map")
                emptyMap<String, Any>()
            }
        } catch (e: Exception) {
            logger.error("Failed to convert Turtle to JSON-LD Map", e)
            emptyMap<String, Any>()
        }
    }

    /**
     * Handles special cases like XML declarations for RDF/XML.
     */
    private fun handleSpecialCases(result: String, rdfFormat: RDFFormat): String {
        return when {
            rdfFormat == RDFFormat.RDFXML || rdfFormat == RDFFormat.RDFXML_PRETTY -> {
                if (!result.startsWith("<?xml")) {
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$result"
                } else {
                    result
                }
            }
            else -> result
        }
    }
}
