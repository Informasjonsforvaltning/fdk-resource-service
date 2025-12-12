package no.fdk.resourceservice.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import no.fdk.resourceservice.model.ResourceType
import org.eclipse.rdf4j.model.Model
import org.eclipse.rdf4j.model.ModelFactory
import org.eclipse.rdf4j.model.impl.LinkedHashModelFactory
import org.eclipse.rdf4j.rio.RDFFormat
import org.eclipse.rdf4j.rio.Rio
import org.eclipse.rdf4j.rio.WriterConfig
import org.eclipse.rdf4j.rio.helpers.BasicWriterSettings
import org.eclipse.rdf4j.rio.helpers.JSONLDMode
import org.eclipse.rdf4j.rio.helpers.JSONLDSettings
import org.slf4j.LoggerFactory
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.StringWriter

/**
 * Unified service for RDF processing, format conversion, and content negotiation.
 *
 * This service combines the functionality of both RdfProcessingService and RdfFormatService
 * into a single, comprehensive service that handles all RDF-related operations.
 *
 * Uses Eclipse RDF4J for better performance and memory efficiency compared to Apache Jena.
 */
@Service
class RdfService(
    private val objectMapper: ObjectMapper,
) {
    private val logger = LoggerFactory.getLogger(RdfService::class.java)
    private val modelFactory: ModelFactory = LinkedHashModelFactory()

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
     * @return The best matching RdfFormat, or TURTLE if no match or header is null/empty.
     */
    fun getBestFormat(acceptHeader: String?): RdfFormat {
        if (acceptHeader.isNullOrBlank()) {
            return RdfFormat.TURTLE
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

        // Fallback to TURTLE if no specific match
        return RdfFormat.TURTLE
    }

    /**
     * Returns the MediaType for a given RdfFormat.
     *
     * @param format The RdfFormat.
     * @return The corresponding MediaType.
     */
    fun getContentType(format: RdfFormat): MediaType = format.mediaType

    /**
     * Converts from any RDF format to any target format.
     *
     * @param graphData The RDF graph data as a string
     * @param fromFormat The source RDF format (TURTLE, JSON_LD, RDF_XML, N_TRIPLES, N_QUADS)
     * @param toFormat The target RDF format
     * @param style The format style (PRETTY or STANDARD)
     * @param expandUris Whether to expand URIs (clear namespace prefixes, default: false)
     * @param resourceType Optional resource type to use resource-specific namespace prefixes
     */
    fun convertFromFormat(
        graphData: String,
        fromFormat: String,
        toFormat: RdfFormat,
        style: RdfFormatStyle,
        expandUris: Boolean = false,
        resourceType: ResourceType? = null,
    ): String? {
        val fromLang =
            mapFormatToLang(fromFormat)
                ?: run {
                    logger.error("Unsupported source format: $fromFormat")
                    return null
                }

        val model = ModelFactory.createDefaultModel()
        try {
            ByteArrayInputStream(graphData.toByteArray()).use { inputStream ->
                RDFDataMgr.read(model, inputStream, fromLang)
            }

            // Use convertFromModel to handle prefixes and formatting
            return convertFromModel(model, toFormat, style, expandUris, resourceType)
        } catch (e: Exception) {
            logger.error("Failed to parse graph data from format $fromFormat: ${e.message}", e)
            return null
        } finally {
            model.close()
        }
    }

    /**
     * Maps format string to Jena Lang enum.
     *
     * @param format The format string (TURTLE, JSON_LD, RDF_XML, N_TRIPLES, N_QUADS)
     * @return The corresponding Jena Lang, or null if unsupported
     */
    private fun mapFormatToLang(format: String?): Lang? {
        if (format == null) {
            return Lang.TURTLE // Default to TURTLE if format is null
        }
        return when (format.uppercase()) {
            "TURTLE" -> Lang.TURTLE
            "JSON_LD", "JSONLD" -> Lang.JSONLD
            "RDF_XML", "RDFXML" -> Lang.RDFXML
            "N_TRIPLES", "NTRIPLES" -> Lang.NTRIPLES
            "N_QUADS", "NQUADS" -> Lang.NQUADS
            else -> null
        }
    }

    /**
     * Converts from Turtle format to any target format.
     *
     * @param turtleData The Turtle RDF data as a string
     * @param toFormat The target RDF format
     * @param style The format style (PRETTY or STANDARD)
     * @param expandUris Whether to expand URIs (clear namespace prefixes, default: false)
     * @param resourceType Optional resource type to use resource-specific namespace prefixes
     */
    fun convertFromTurtle(
        turtleData: String,
        toFormat: RdfFormat,
        style: RdfFormatStyle,
        expandUris: Boolean = false,
        resourceType: ResourceType? = null,
    ): String? = convertFromFormat(turtleData, "TURTLE", toFormat, style, expandUris, resourceType)

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

            val rdfFormat = getRdfFormat(toFormat)
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
        val model =
            try {
                ByteArrayInputStream(jsonString.toByteArray()).use { inputStream ->
                    Rio.parse(inputStream, "", RDFFormat.JSONLD)
                }
            } catch (e: Exception) {
                logger.error("Failed to parse JSON-LD data", e)
                return null
            }

            // Use the new convertFromModel method
            return convertFromModel(model, toFormat, style, expandUris, resourceType)
        } finally {
            model.clear()
        }
    }

    /**
     * Converts a Jena Model to the requested format with optimized defaults for union graphs.
     * Uses STANDARD style and keeps prefixes (expandUris=false) for best performance.
     *
     * @param model The Jena Model to convert
     * @param toFormat The target RDF format
     * @return The converted RDF data as a String, or null if conversion failed
     */
    fun convertFromModelForUnionGraph(
        model: org.apache.jena.rdf.model.Model,
        toFormat: RdfFormat,
    ): String? = convertFromModel(model, toFormat, RdfFormatStyle.STANDARD, expandUris = false, resourceType = null)

    /**
     * Maps format and style enums to RDFFormat enum.
     */
    private fun getRdfFormat(format: RdfFormat): RDFFormat =
        when (format) {
            RdfFormat.TURTLE -> RDFFormat.TURTLE
            RdfFormat.RDF_XML -> RDFFormat.RDFXML
            RdfFormat.N_TRIPLES -> RDFFormat.NTRIPLES
            RdfFormat.N_QUADS -> RDFFormat.NQUADS
            RdfFormat.JSON_LD -> RDFFormat.JSONLD
        }

    /**
     * Converts Turtle RDF to JSON-LD Map.
     *
     * This method converts Turtle RDF data to JSON-LD format and returns it as a Map.
     * It's specifically designed for storing in the database where JSON-LD data is expected as Map<String, Any>.
     *
     * Optimized version using RDF4J that:
     * - Uses memory-efficient LinkedHashModel for better performance
     * - Directly writes to byte array instead of StringWriter
     * - Uses Jackson's streaming parser for faster JSON parsing
     * - Avoids unnecessary string intermediate steps
     *
     * @param turtleData The Turtle RDF data as a string
     * @param expandUris Whether to expand URIs (clear namespace prefixes, default: true for expanded URIs)
     * @return JSON-LD data as Map<String, Any>, or empty map if conversion fails
     */
    fun convertTurtleToJsonLdMap(
        turtleData: String,
        expandUris: Boolean = true,
    ): Map<String, Any> {
        return try {
            logger.debug("Converting Turtle to JSON-LD Map (expandUris: $expandUris, size: ${turtleData.length})")

            // Use memory-efficient model with minimal overhead (RDF4J LinkedHashModel is optimized)
            val model =
                try {
                    // Read Turtle directly from byte array (faster than String)
                    val turtleBytes = turtleData.toByteArray(Charsets.UTF_8)
                    ByteArrayInputStream(turtleBytes).use { inputStream ->
                        Rio.parse(inputStream, "", RDFFormat.TURTLE)
                    }
                } catch (e: Exception) {
                    logger.error("Failed to parse Turtle data in convertTurtleToJsonLdMap", e)
                    return emptyMap<String, Any>()
                }

            try {
                if (expandUris) {
                    // RDF4J doesn't have clearNamespaces(), so we remove them individually
                    // Collect prefixes first to avoid ConcurrentModificationException
                    val prefixes = model.namespaces.map { it.prefix }.toList()
                    prefixes.forEach { model.removeNamespace(it) }
                }

                // Write directly to byte array output stream (more efficient than StringWriter)
                val outputStream = ByteArrayOutputStream()
                val writer = Rio.createWriter(RDFFormat.JSONLD, outputStream)
                val config = WriterConfig()

                // Configure JSON-LD to include namespace prefixes in @context when expandUris is false
                if (!expandUris) {
                    // COMPACT mode will automatically use the model's namespaces in the @context
                    config.set(JSONLDSettings.JSONLD_MODE, JSONLDMode.COMPACT)
                } else {
                    // Use EXPAND mode when expandUris is true to ensure full URIs
                    config.set(JSONLDSettings.JSONLD_MODE, JSONLDMode.EXPAND)
                }

                writer.setWriterConfig(config)
                Rio.write(model, writer)

                // Parse JSON-LD directly from bytes (faster than string conversion)
                // RDF4J may output JSON-LD as an array, so we handle both cases
                val jsonBytes = outputStream.toByteArray()
                if (jsonBytes.isEmpty()) {
                    logger.warn("RDF4J produced empty JSON-LD output")
                    return emptyMap<String, Any>()
                }

                val jsonString = String(jsonBytes, Charsets.UTF_8)
                logger.debug("RDF4J JSON-LD output (first 500 chars): ${jsonString.take(500)}")

                val jsonNode =
                    try {
                        objectMapper.readTree(jsonBytes)
                    } catch (e: Exception) {
                        logger.error("Failed to parse JSON-LD from RDF4J output: ${jsonString.take(200)}", e)
                        return emptyMap<String, Any>()
                    }

                val jsonLdMap =
                    when {
                        jsonNode.isObject -> {
                            @Suppress("UNCHECKED_CAST")
                            objectMapper.convertValue(jsonNode, Map::class.java) as Map<String, Any>
                        }
                        jsonNode.isArray -> {
                            if (jsonNode.size() == 0) {
                                logger.warn("RDF4J produced empty JSON-LD array")
                                emptyMap<String, Any>()
                            } else if (jsonNode.size() == 1) {
                                // Single graph in array - extract it
                                @Suppress("UNCHECKED_CAST")
                                objectMapper.convertValue(jsonNode[0], Map::class.java) as Map<String, Any>
                            } else {
                                // Multiple graphs - wrap in @graph
                                @Suppress("UNCHECKED_CAST")
                                mapOf("@graph" to objectMapper.convertValue(jsonNode, List::class.java)) as Map<String, Any>
                            }
                        }
                        else -> {
                            logger.warn("Unexpected JSON-LD format: ${jsonNode.nodeType}")
                            emptyMap<String, Any>()
                        }
                    }

                logger.debug("Successfully converted Turtle to JSON-LD Map (result size: ${jsonLdMap.size})")
                jsonLdMap
            } finally {
                model.clear()
            }
        } catch (e: Exception) {
            logger.error("Failed to convert Turtle to JSON-LD Map", e)
            emptyMap<String, Any>()
        }
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
        model.setNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNamespace("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNamespace("owl", "http://www.w3.org/2002/07/owl#")
        model.setNamespace("xsd", "http://www.w3.org/2001/XMLSchema#")

        // DCAT-AP-NO core vocabularies
        model.setNamespace("dcat", "http://www.w3.org/ns/dcat#")
        model.setNamespace("dcatap", "http://data.europa.eu/r5r/")
        model.setNamespace("dcatno", "https://data.norge.no/vocabulary/dcatno#")
        model.setNamespace("dct", "http://purl.org/dc/terms/")

        // Additional DCAT-AP-NO vocabularies
        model.setNamespace("adms", "http://www.w3.org/ns/adms#")
        model.setNamespace("cv", "http://data.europa.eu/m8g/")
        model.setNamespace("cpsv", "http://purl.org/vocab/cpsv#")
        model.setNamespace("dqv", "http://www.w3.org/ns/dqv#")
        model.setNamespace("eli", "http://data.europa.eu/eli/ontology#")
        model.setNamespace("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNamespace("locn", "http://www.w3.org/ns/locn#")
        model.setNamespace("odrl", "http://www.w3.org/ns/odrl/2/")
        model.setNamespace("odrs", "http://schema.theodi.org/odrs#")
        model.setNamespace("prov", "http://www.w3.org/ns/prov#")
        model.setNamespace("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNamespace("spdx", "http://spdx.org/rdf/terms#")
        model.setNamespace("time", "http://www.w3.org/2006/time#")
        model.setNamespace("vcard", "http://www.w3.org/2006/vcard/ns#")
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
        model.setNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNamespace("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNamespace("owl", "http://www.w3.org/2002/07/owl#")
        model.setNamespace("xsd", "http://www.w3.org/2001/XMLSchema#")

        // SKOS-AP-NO-Begrep core vocabularies
        model.setNamespace("adms", "http://www.w3.org/ns/adms#")
        model.setNamespace("dcat", "http://www.w3.org/ns/dcat#")
        model.setNamespace("dct", "http://purl.org/dc/terms/")
        model.setNamespace("euvoc", "http://publications.europa.eu/ontology/euvoc#")
        model.setNamespace("org", "http://www.w3.org/ns/org#")
        model.setNamespace("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNamespace("skosno", "https://data.norge.no/vocabulary/skosno#")
        model.setNamespace("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNamespace("xkos", "http://rdf-vocabulary.ddialliance.org/xkos#")
    }

    /**
     * Adds prefixes for Information Model resources according to ModellDCAT-AP-NO specification.
     *
     * Namespaces are based on:
     * https://data.norge.no/specification/modelldcat-ap-no#URIer-som-er-i-bruk
     */
    private fun addInformationModelPrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNamespace("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNamespace("owl", "http://www.w3.org/2002/07/owl#")
        model.setNamespace("xsd", "http://www.w3.org/2001/XMLSchema#")

        // ModellDCAT-AP-NO core vocabularies
        model.setNamespace("adms", "http://www.w3.org/ns/adms#")
        model.setNamespace("dcat", "http://www.w3.org/ns/dcat#")
        model.setNamespace("dct", "http://purl.org/dc/terms/")
        model.setNamespace("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNamespace("locn", "http://www.w3.org/ns/locn#")
        model.setNamespace("modelldcatno", "https://data.norge.no/vocabulary/modelldcatno#")
        model.setNamespace("prof", "https://www.w3.org/ns/dx/prof/")
        model.setNamespace("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNamespace("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNamespace("xkos", "http://rdf-vocabulary.ddialliance.org/xkos#")
    }

    /**
     * Adds prefixes for Service resources according to CPSV-AP-NO specification.
     *
     * Namespaces are based on:
     * https://data.norge.no/specification/cpsv-ap-no#Navnerom
     */
    private fun addServicePrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNamespace("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNamespace("xsd", "http://www.w3.org/2001/XMLSchema#")

        // CPSV-AP-NO core vocabularies
        model.setNamespace("adms", "http://www.w3.org/ns/adms#")
        model.setNamespace("cpsv", "http://purl.org/vocab/cpsv#")
        model.setNamespace("cpsvno", "https://data.norge.no/vocabulary/cpsvno#")
        model.setNamespace("cv", "http://data.europa.eu/m8g/")
        model.setNamespace("dcat", "http://www.w3.org/ns/dcat#")
        model.setNamespace("dcatno", "https://data.norge.no/vocabulary/dcatno#")
        model.setNamespace("dct", "http://purl.org/dc/terms/")
        model.setNamespace("eli", "http://data.europa.eu/eli/ontology#")
        model.setNamespace("epo", "http://data.europa.eu/a4g/ontology#")
        model.setNamespace("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNamespace("greg", "http://www.w3.org/ns/time/gregorian#")
        model.setNamespace("locn", "http://www.w3.org/ns/locn#")
        model.setNamespace("org", "http://www.w3.org/ns/org#")
        model.setNamespace("schema", "https://schema.org/")
        model.setNamespace("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNamespace("time", "http://www.w3.org/2006/time#")
        model.setNamespace("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNamespace("xkos", "http://rdf-vocabulary.ddialliance.org/xkos#")
    }

    /**
     * Adds prefixes for Event resources according to CPSV-AP-NO specification.
     *
     * Namespaces are based on:
     * https://data.norge.no/specification/cpsv-ap-no#Navnerom
     */
    private fun addEventPrefixes(model: Model) {
        // Core RDF vocabularies
        model.setNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNamespace("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNamespace("xsd", "http://www.w3.org/2001/XMLSchema#")

        // CPSV-AP-NO core vocabularies
        model.setNamespace("adms", "http://www.w3.org/ns/adms#")
        model.setNamespace("cpsv", "http://purl.org/vocab/cpsv#")
        model.setNamespace("cpsvno", "https://data.norge.no/vocabulary/cpsvno#")
        model.setNamespace("cv", "http://data.europa.eu/m8g/")
        model.setNamespace("dcat", "http://www.w3.org/ns/dcat#")
        model.setNamespace("dcatno", "https://data.norge.no/vocabulary/dcatno#")
        model.setNamespace("dct", "http://purl.org/dc/terms/")
        model.setNamespace("eli", "http://data.europa.eu/eli/ontology#")
        model.setNamespace("epo", "http://data.europa.eu/a4g/ontology#")
        model.setNamespace("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNamespace("greg", "http://www.w3.org/ns/time/gregorian#")
        model.setNamespace("locn", "http://www.w3.org/ns/locn#")
        model.setNamespace("org", "http://www.w3.org/ns/org#")
        model.setNamespace("schema", "https://schema.org/")
        model.setNamespace("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNamespace("time", "http://www.w3.org/2006/time#")
        model.setNamespace("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNamespace("xkos", "http://rdf-vocabulary.ddialliance.org/xkos#")
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
        model.setNamespace("rdf", "http://www.w3.org/1999/02/22-rdf-syntax-ns#")
        model.setNamespace("rdfs", "http://www.w3.org/2000/01/rdf-schema#")
        model.setNamespace("owl", "http://www.w3.org/2002/07/owl#")
        model.setNamespace("xsd", "http://www.w3.org/2001/XMLSchema#")

        // DCAT and Dublin Core vocabularies
        model.setNamespace("dcat", "http://www.w3.org/ns/dcat#")
        model.setNamespace("dct", "http://purl.org/dc/terms/")
        model.setNamespace("dc", "http://purl.org/dc/elements/1.1/")

        // Additional common vocabularies
        model.setNamespace("foaf", "http://xmlns.com/foaf/0.1/")
        model.setNamespace("skos", "http://www.w3.org/2004/02/skos/core#")
        model.setNamespace("schema", "http://schema.org/")
        model.setNamespace("vcard", "http://www.w3.org/2006/vcard/ns#")
        model.setNamespace("prov", "http://www.w3.org/ns/prov#")
        model.setNamespace("adms", "http://www.w3.org/ns/adms#")
        model.setNamespace("locn", "http://www.w3.org/ns/locn#")
    }

    /**
     * Handles special cases like XML declarations for RDF/XML.
     */
    private fun handleSpecialCases(
        result: String,
        rdfFormat: RDFFormat,
    ): String =
        when {
            rdfFormat == RDFFormat.RDFXML -> {
                if (!result.startsWith("<?xml")) {
                    "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n$result"
                } else {
                    result
                }
            }
            else -> result
        }
}
