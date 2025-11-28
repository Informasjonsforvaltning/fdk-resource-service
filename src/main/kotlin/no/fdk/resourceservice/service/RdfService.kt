package no.fdk.resourceservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.ResourceType
import org.apache.jena.rdf.model.Model
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.apache.jena.riot.RDFFormat
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.StringWriter

/**
 * Unified service for RDF processing, format conversion, and content negotiation.
 *
 * This service combines the functionality of both RdfProcessingService and RdfFormatService
 * into a single, comprehensive service that handles all RDF-related operations.
 */
@Service
class RdfService(
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(RdfService::class.java)

    /**
     * Supported RDF formats and their corresponding media types.
     */
    enum class RdfFormat(
        val mediaType: MediaType,
        val fileExtension: String,
    ) {
        JSON_LD(MediaType("application", "ld+json"), "jsonld"),
        TURTLE(MediaType("text", "turtle"), "ttl"),
        RDF_XML(MediaType("application", "rdf+xml"), "rdf"),
        N_TRIPLES(MediaType("application", "n-triples"), "nt"),
        N_QUADS(MediaType("application", "n-quads"), "nq"),
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
        STANDARD,
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

        val acceptTypes =
            acceptHeader
                .split(',')
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
    fun getContentType(format: RdfFormat): MediaType = format.mediaType

    /**
     * Converts from Turtle format to any target format.
     */
    fun convertFromTurtle(
        turtleData: String,
        toFormat: RdfFormat,
        style: RdfFormatStyle,
        expandUris: Boolean = false,
    ): String? {
        val model = ModelFactory.createDefaultModel()
        try {
            ByteArrayInputStream(turtleData.toByteArray()).use { inputStream ->
                RDFDataMgr.read(model, inputStream, Lang.TURTLE)
            }

            if (expandUris) {
                model.clearNsPrefixMap()
            }

            val rdfFormat = getRdfFormat(toFormat, style)
            val result =
                StringWriter().use { outputStream ->
                    RDFDataMgr.write(outputStream, model, rdfFormat)
                    outputStream.toString()
                }

            return handleSpecialCases(result, rdfFormat)
        } finally {
            model.close()
        }
    }

    /**
     * Converts a Jena Model directly to any target format.
     * This is more efficient than converting through intermediate formats.
     *
     * For large models (like union graphs), this method optimizes memory usage
     * by working directly on the model when possible, avoiding unnecessary copies.
     *
     * @param model The Jena Model to convert
     * @param toFormat The target RDF format
     * @param style The format style (PRETTY or STANDARD)
     * @param expandUris Whether to expand URIs (clear namespace prefixes, default: false)
     * @param resourceType Optional resource type to use resource-specific namespace prefixes
     * @return The converted RDF data as a String, or null if conversion failed
     */
    fun convertFromModel(
        model: org.apache.jena.rdf.model.Model,
        toFormat: RdfFormat,
        style: RdfFormatStyle,
        expandUris: Boolean = false,
        resourceType: ResourceType? = null,
    ): String? {
        // For large models (like union graphs), we optimize by avoiding unnecessary copies.
        // We only create a copy if we need to modify namespace prefixes for a specific resource type.
        // For union graphs (resourceType == null), we can work directly on the model since
        // it's only used once for conversion and will be closed by the caller.
        val needsCopy = !expandUris && resourceType != null
        val workingModel =
            if (needsCopy) {
                // Create a copy only when we need to add resource-specific prefixes
                val copy = ModelFactory.createDefaultModel()
                copy.add(model)
                copy
            } else {
                // Work directly on the original model (safe for union graphs)
                model
            }

        try {
            if (expandUris) {
                workingModel.clearNsPrefixMap()
            } else {
                // Add prefixes: resource-specific if resourceType provided, common otherwise
                addPrefixesForResourceType(workingModel, resourceType)
            }

            val rdfFormat = getRdfFormat(toFormat, style)
            val result =
                StringWriter().use { outputStream ->
                    RDFDataMgr.write(outputStream, workingModel, rdfFormat)
                    outputStream.toString()
                }

            return handleSpecialCases(result, rdfFormat)
        } catch (e: Exception) {
            logger.error("Failed to convert model to format {}: {}", toFormat, e.message, e)
            return null
        } finally {
            // Only close if we created a copy (the original model is closed by the caller)
            if (needsCopy) {
                workingModel.close()
            }
        }
    }

    /**
     * Converts from JSON-LD format to any target format.
     *
     * @param jsonLdData The JSON-LD data to convert
     * @param toFormat The target RDF format
     * @param style The format style (PRETTY or STANDARD)
     * @param expandUris Whether to expand URIs (clear namespace prefixes, default: false)
     * @param resourceType Optional resource type to use resource-specific namespace prefixes
     */
    fun convertFromJsonLd(
        jsonLdData: Map<String, Any>,
        toFormat: RdfFormat,
        style: RdfFormatStyle,
        expandUris: Boolean = false,
        resourceType: ResourceType? = null,
    ): String? {
        // Handle JSON-LD pretty printing (no RDF conversion needed)
        if (toFormat == RdfFormat.JSON_LD && style == RdfFormatStyle.PRETTY && expandUris) {
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonLdData)
        }

        // Convert Map to JSON string and parse to model
        val jsonString = objectMapper.writeValueAsString(jsonLdData)
        val model = ModelFactory.createDefaultModel()
        try {
            ByteArrayInputStream(jsonString.toByteArray()).use { inputStream ->
                RDFDataMgr.read(model, inputStream, Lang.JSONLD)
            }

            // Use the new convertFromModel method
            return convertFromModel(model, toFormat, style, expandUris, resourceType)
        } finally {
            model.close()
        }
    }

    /**
     * Maps format and style enums to RDFFormat enum.
     */
    private fun getRdfFormat(
        format: RdfFormat,
        style: RdfFormatStyle,
    ): RDFFormat =
        when (format) {
            RdfFormat.TURTLE -> if (style == RdfFormatStyle.PRETTY) RDFFormat.TURTLE_PRETTY else RDFFormat.TURTLE
            RdfFormat.RDF_XML -> if (style == RdfFormatStyle.PRETTY) RDFFormat.RDFXML_PRETTY else RDFFormat.RDFXML
            RdfFormat.N_TRIPLES -> RDFFormat.NTRIPLES
            RdfFormat.N_QUADS -> RDFFormat.NQUADS
            RdfFormat.JSON_LD -> if (style == RdfFormatStyle.PRETTY) RDFFormat.JSONLD_PRETTY else RDFFormat.JSONLD
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
    fun convertTurtleToJsonLdMap(
        turtleData: String,
        expandUris: Boolean = true,
    ): Map<String, Any> =
        try {
            logger.debug("Converting Turtle to JSON-LD Map (expandUris: $expandUris)")

            // Convert Turtle to JSON-LD string
            val jsonLdString = convertFromTurtle(turtleData, RdfFormat.JSON_LD, RdfFormatStyle.PRETTY, expandUris)

            if (jsonLdString != null) {
                // Parse JSON-LD string to Map
                val jsonLdMap =
                    objectMapper.readValue(
                        jsonLdString,
                        object : TypeReference<Map<String, Any>>() {},
                    )
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

    /**
     * Adds resource-type-specific prefixes to the model.
     * If no resource type is provided, adds common prefixes.
     *
     * @param model The RDF model to add prefixes to
     * @param resourceType The resource type, or null for common prefixes
     */
    private fun addPrefixesForResourceType(
        model: Model,
        resourceType: ResourceType?,
    ) {
        when (resourceType) {
            ResourceType.DATASET -> addDatasetPrefixes(model)
            ResourceType.DATA_SERVICE -> addDataServicePrefixes(model)
            ResourceType.CONCEPT -> addConceptPrefixes(model)
            ResourceType.INFORMATION_MODEL -> addInformationModelPrefixes(model)
            ResourceType.SERVICE -> addServicePrefixes(model)
            ResourceType.EVENT -> addEventPrefixes(model)
            null -> addCommonPrefixes(model)
        }
    }

    /**
     * Adds prefixes for Dataset resources according to DCAT-AP-NO specification.
     *
     * Namespaces are based on:
     * https://data.norge.no/specification/dcat-ap-no#URIer-i-bruk
     */
    private fun addDatasetPrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#")
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#")

        // DCAT-AP-NO core vocabularies
        model.setNsPrefix("dcat", "http://www.w3.org/ns/dcat#")
        model.setNsPrefix("dcatap", "http://data.europa.eu/r5r/")
        model.setNsPrefix("dcatno", "https://data.norge.no/vocabulary/dcatno#")
        model.setNsPrefix("dct", "http://purl.org/dc/terms/")

        // Additional DCAT-AP-NO vocabularies
        model.setNsPrefix("adms", "http://www.w3.org/ns/adms#")
        model.setNsPrefix("cv", "http://data.europa.eu/m8g/")
        model.setNsPrefix("cpsv", "http://purl.org/vocab/cpsv#")
        model.setNsPrefix("dqv", "http://www.w3.org/ns/dqv#")
        model.setNsPrefix("eli", "http://data.europa.eu/eli/ontology#")
        model.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNsPrefix("locn", "http://www.w3.org/ns/locn#")
        model.setNsPrefix("odrl", "http://www.w3.org/ns/odrl/2/")
        model.setNsPrefix("odrs", "http://schema.theodi.org/odrs#")
        model.setNsPrefix("prov", "http://www.w3.org/ns/prov#")
        model.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNsPrefix("spdx", "http://spdx.org/rdf/terms#")
        model.setNsPrefix("time", "http://www.w3.org/2006/time#")
        model.setNsPrefix("vcard", "http://www.w3.org/2006/vcard/ns#")
    }

    /**
     * Adds prefixes for Data Service resources.
     * Uses similar prefixes to Dataset as they are related DCAT resources.
     */
    private fun addDataServicePrefixes(model: Model) {
        addDatasetPrefixes(model) // Data services use similar vocabularies
    }

    /**
     * Adds prefixes for Concept resources according to SKOS-AP-NO-Begrep specification.
     *
     * Namespaces are based on:
     * https://data.norge.no/specification/skos-ap-no-begrep#Navnerom-brukt-i-standarden
     */
    private fun addConceptPrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#")
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#")

        // SKOS-AP-NO-Begrep core vocabularies
        model.setNsPrefix("adms", "http://www.w3.org/ns/adms#")
        model.setNsPrefix("dcat", "http://www.w3.org/ns/dcat#")
        model.setNsPrefix("dct", "http://purl.org/dc/terms/")
        model.setNsPrefix("euvoc", "http://publications.europa.eu/ontology/euvoc#")
        model.setNsPrefix("org", "http://www.w3.org/ns/org#")
        model.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNsPrefix("skosno", "https://data.norge.no/vocabulary/skosno#")
        model.setNsPrefix("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNsPrefix("xkos", "http://rdf-vocabulary.ddialliance.org/xkos#")
    }

    /**
     * Adds prefixes for Information Model resources according to ModellDCAT-AP-NO specification.
     *
     * Namespaces are based on:
     * https://data.norge.no/specification/modelldcat-ap-no#URIer-som-er-i-bruk
     */
    private fun addInformationModelPrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#")
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#")

        // ModellDCAT-AP-NO core vocabularies
        model.setNsPrefix("adms", "http://www.w3.org/ns/adms#")
        model.setNsPrefix("dcat", "http://www.w3.org/ns/dcat#")
        model.setNsPrefix("dct", "http://purl.org/dc/terms/")
        model.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNsPrefix("locn", "http://www.w3.org/ns/locn#")
        model.setNsPrefix("modelldcatno", "https://data.norge.no/vocabulary/modelldcatno#")
        model.setNsPrefix("prof", "https://www.w3.org/ns/dx/prof/")
        model.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNsPrefix("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNsPrefix("xkos", "http://rdf-vocabulary.ddialliance.org/xkos#")
    }

    /**
     * Adds prefixes for Service resources according to CPSV-AP-NO specification.
     *
     * Namespaces are based on:
     * https://data.norge.no/specification/cpsv-ap-no#Navnerom
     */
    private fun addServicePrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#")

        // CPSV-AP-NO core vocabularies
        model.setNsPrefix("adms", "http://www.w3.org/ns/adms#")
        model.setNsPrefix("cpsv", "http://purl.org/vocab/cpsv#")
        model.setNsPrefix("cpsvno", "https://data.norge.no/vocabulary/cpsvno#")
        model.setNsPrefix("cv", "http://data.europa.eu/m8g/")
        model.setNsPrefix("dcat", "http://www.w3.org/ns/dcat#")
        model.setNsPrefix("dcatno", "https://data.norge.no/vocabulary/dcatno#")
        model.setNsPrefix("dct", "http://purl.org/dc/terms/")
        model.setNsPrefix("eli", "http://data.europa.eu/eli/ontology#")
        model.setNsPrefix("epo", "http://data.europa.eu/a4g/ontology#")
        model.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNsPrefix("greg", "http://www.w3.org/ns/time/gregorian#")
        model.setNsPrefix("locn", "http://www.w3.org/ns/locn#")
        model.setNsPrefix("org", "http://www.w3.org/ns/org#")
        model.setNsPrefix("schema", "https://schema.org/")
        model.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNsPrefix("time", "http://www.w3.org/2006/time#")
        model.setNsPrefix("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNsPrefix("xkos", "http://rdf-vocabulary.ddialliance.org/xkos#")
    }

    /**
     * Adds prefixes for Event resources according to CPSV-AP-NO specification.
     *
     * Namespaces are based on:
     * https://data.norge.no/specification/cpsv-ap-no#Navnerom
     */
    private fun addEventPrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#")

        // CPSV-AP-NO core vocabularies
        model.setNsPrefix("adms", "http://www.w3.org/ns/adms#")
        model.setNsPrefix("cpsv", "http://purl.org/vocab/cpsv#")
        model.setNsPrefix("cpsvno", "https://data.norge.no/vocabulary/cpsvno#")
        model.setNsPrefix("cv", "http://data.europa.eu/m8g/")
        model.setNsPrefix("dcat", "http://www.w3.org/ns/dcat#")
        model.setNsPrefix("dcatno", "https://data.norge.no/vocabulary/dcatno#")
        model.setNsPrefix("dct", "http://purl.org/dc/terms/")
        model.setNsPrefix("eli", "http://data.europa.eu/eli/ontology#")
        model.setNsPrefix("epo", "http://data.europa.eu/a4g/ontology#")
        model.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNsPrefix("greg", "http://www.w3.org/ns/time/gregorian#")
        model.setNsPrefix("locn", "http://www.w3.org/ns/locn#")
        model.setNsPrefix("org", "http://www.w3.org/ns/org#")
        model.setNsPrefix("schema", "https://schema.org/")
        model.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNsPrefix("time", "http://www.w3.org/2006/time#")
        model.setNsPrefix("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNsPrefix("xkos", "http://rdf-vocabulary.ddialliance.org/xkos#")
    }

    /**
     * Adds common RDF prefixes to the model.
     * Used when no specific resource type is provided.
     *
     * Namespaces are verified against the fdk-parser-service vocabulary definitions:
     * https://github.com/Informasjonsforvaltning/fdk-parser-service/tree/main/src/main/kotlin/no/digdir/fdk/parserservice/vocabulary
     */
    private fun addCommonPrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNsPrefix("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNsPrefix("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNsPrefix("owl", "http://www.w3.org/2002/07/owl#")
        model.setNsPrefix("xsd", "http://www.w3.org/2001/XMLSchema#")

        // DCAT and Dublin Core vocabularies
        model.setNsPrefix("dcat", "http://www.w3.org/ns/dcat#")
        model.setNsPrefix("dct", "http://purl.org/dc/terms/")
        model.setNsPrefix("dc", "http://purl.org/dc/elements/1.1/")

        // Additional common vocabularies
        model.setNsPrefix("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNsPrefix("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNsPrefix("schema", "http://schema.org/")
        model.setNsPrefix("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNsPrefix("prov", "http://www.w3.org/ns/prov#")
        model.setNsPrefix("adms", "http://www.w3.org/ns/adms#")
        model.setNsPrefix("locn", "http://www.w3.org/ns/locn#")
    }

    /**
     * Handles special cases like XML declarations for RDF/XML.
     */
    private fun handleSpecialCases(
        result: String,
        rdfFormat: RDFFormat,
    ): String =
        when {
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
