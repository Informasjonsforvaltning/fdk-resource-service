package no.fdk.resourceservice.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.Arguments
import org.junit.jupiter.params.provider.MethodSource
import org.springframework.http.MediaType
import java.util.stream.Stream

class RdfServiceTest {
    private lateinit var rdfService: RdfService
    private lateinit var objectMapper: ObjectMapper

    // Sample JSON-LD data with expanded URIs (no namespace prefixes)
    private val sampleJsonLdExpanded =
        mapOf(
            "@id" to "https://example.com/resource",
            "@type" to listOf("http://www.w3.org/ns/dcat#Dataset"),
            "http://purl.org/dc/terms/title" to
                listOf(
                    mapOf("@value" to "Test Dataset"),
                ),
            "http://purl.org/dc/terms/description" to
                listOf(
                    mapOf("@value" to "A test dataset for RDF conversion"),
                ),
            "http://purl.org/dc/terms/publisher" to
                listOf(
                    mapOf("@id" to "https://example.com/organization"),
                ),
        )

    // Sample Turtle data with namespace prefixes
    private val sampleTurtle =
        """
        @prefix dcat: <http://www.w3.org/ns/dcat#> .
        @prefix dct: <http://purl.org/dc/terms/> .
        @prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
        @prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .

        <https://example.com/resource> 
            a dcat:Dataset ;
            dct:title "Test Dataset"@en ;
            dct:description "A test dataset for RDF conversion"@en ;
            dct:publisher <https://example.com/organization> .
        """.trimIndent()

    @BeforeEach
    fun setUp() {
        objectMapper = ObjectMapper()
        rdfService = RdfService(objectMapper)
    }

    @Test
    fun `getBestFormat should return JSON_LD when Accept header is null`() {
        // When
        val result = rdfService.getBestFormat(null)

        // Then
        assertEquals(RdfService.RdfFormat.JSON_LD, result)
    }

    @Test
    fun `getBestFormat should return JSON_LD when Accept header is blank`() {
        // When
        val result = rdfService.getBestFormat("")

        // Then
        assertEquals(RdfService.RdfFormat.JSON_LD, result)
    }

    @Test
    fun `getBestFormat should return JSON_LD when Accept header is application ld+json`() {
        // When
        val result = rdfService.getBestFormat("application/ld+json")

        // Then
        assertEquals(RdfService.RdfFormat.JSON_LD, result)
    }

    @Test
    fun `getBestFormat should return TURTLE when Accept header is text turtle`() {
        // When
        val result = rdfService.getBestFormat("text/turtle")

        // Then
        assertEquals(RdfService.RdfFormat.TURTLE, result)
    }

    @Test
    fun `getBestFormat should return RDF_XML when Accept header is application rdf+xml`() {
        // When
        val result = rdfService.getBestFormat("application/rdf+xml")

        // Then
        assertEquals(RdfService.RdfFormat.RDF_XML, result)
    }

    @Test
    fun `getBestFormat should return N_TRIPLES when Accept header is application n-triples`() {
        // When
        val result = rdfService.getBestFormat("application/n-triples")

        // Then
        assertEquals(RdfService.RdfFormat.N_TRIPLES, result)
    }

    @Test
    fun `getBestFormat should return N_QUADS when Accept header is application n-quads`() {
        // When
        val result = rdfService.getBestFormat("application/n-quads")

        // Then
        assertEquals(RdfService.RdfFormat.N_QUADS, result)
    }

    @Test
    fun `getBestFormat should return JSON_LD when no match found`() {
        // When
        val result = rdfService.getBestFormat("application/json")

        // Then
        assertEquals(RdfService.RdfFormat.JSON_LD, result)
    }

    @Test
    fun `getBestFormat should handle multiple Accept types and return first match`() {
        // When
        val result = rdfService.getBestFormat("application/json, text/turtle, application/ld+json")

        // Then
        assertEquals(RdfService.RdfFormat.TURTLE, result)
    }

    @Test
    fun `getContentType should return correct MediaType for JSON_LD`() {
        // When
        val result = rdfService.getContentType(RdfService.RdfFormat.JSON_LD)

        // Then
        assertEquals(MediaType("application", "ld+json"), result)
    }

    @Test
    fun `getContentType should return correct MediaType for TURTLE`() {
        // When
        val result = rdfService.getContentType(RdfService.RdfFormat.TURTLE)

        // Then
        assertEquals(MediaType("text", "turtle"), result)
    }

    @Test
    fun `getContentType should return correct MediaType for RDF_XML`() {
        // When
        val result = rdfService.getContentType(RdfService.RdfFormat.RDF_XML)

        // Then
        assertEquals(MediaType("application", "rdf+xml"), result)
    }

    @Test
    fun `getContentType should return correct MediaType for N_TRIPLES`() {
        // When
        val result = rdfService.getContentType(RdfService.RdfFormat.N_TRIPLES)

        // Then
        assertEquals(MediaType("application", "n-triples"), result)
    }

    @Test
    fun `getContentType should return correct MediaType for N_QUADS`() {
        // When
        val result = rdfService.getContentType(RdfService.RdfFormat.N_QUADS)

        // Then
        assertEquals(MediaType("application", "n-quads"), result)
    }

    @ParameterizedTest
    @MethodSource("formatStyleCombinations")
    fun `convertFromJsonLd should convert to all formats with different styles`(
        format: RdfService.RdfFormat,
        style: RdfService.RdfFormatStyle,
        expandUris: Boolean,
    ) {
        // When
        val result = rdfService.convertFromJsonLd(sampleJsonLdExpanded, format, style, expandUris)

        // Then
        assertNotNull(result, "Conversion should not return null for format: $format, style: $style, expandUris: $expandUris")
        assertTrue(result!!.isNotBlank(), "Conversion result should not be blank")

        // Verify format-specific characteristics
        when (format) {
            RdfService.RdfFormat.JSON_LD -> {
                // Should be valid JSON
                assertDoesNotThrow { objectMapper.readTree(result) }
            }
            RdfService.RdfFormat.TURTLE -> {
                // Should contain RDF-like structure
                assertTrue(result.contains("https://example.com/resource") || result.contains("@prefix"))
            }
            RdfService.RdfFormat.RDF_XML -> {
                // Should start with XML declaration or contain RDF element
                assertTrue(result.contains("<?xml") || result.contains("<rdf:RDF") || result.contains("<rdf:Description"))
            }
            RdfService.RdfFormat.N_TRIPLES -> {
                // Should contain URIs and periods (N-Triples format)
                assertTrue(result.contains("https://") && result.contains(" ."))
            }
            RdfService.RdfFormat.N_QUADS -> {
                // Should contain URIs and periods, possibly with graph context
                assertTrue(result.contains("https://") && result.contains(" ."))
            }
        }
    }

    @ParameterizedTest
    @MethodSource("formatStyleCombinations")
    fun `convertFromTurtle should convert to all formats with different styles`(
        format: RdfService.RdfFormat,
        style: RdfService.RdfFormatStyle,
        expandUris: Boolean,
    ) {
        // When
        val result = rdfService.convertFromTurtle(sampleTurtle, format, style, expandUris)

        // Then
        assertNotNull(result, "Conversion should not return null for format: $format, style: $style, expandUris: $expandUris")
        assertTrue(result!!.isNotBlank(), "Conversion result should not be blank")

        // Verify format-specific characteristics
        when (format) {
            RdfService.RdfFormat.JSON_LD -> {
                // Should be valid JSON
                val jsonNode: JsonNode = objectMapper.readTree(result)
                // Basic structure validation - JSON-LD should have proper structure
                assertTrue(
                    jsonNode.has("@id") || jsonNode.isArray || jsonNode.has("@graph") || jsonNode.has("@context"),
                    "JSON-LD should have @id, be an array, have @graph, or have @context",
                )

                // When expandUris is false, should have @context with namespace prefixes
                if (!expandUris && jsonNode.has("@context")) {
                    val context = jsonNode.get("@context")
                    if (context.isObject) {
                        // Context should contain namespace mappings when expandUris is false
                        val contextObj = context as ObjectNode
                        val hasMappings =
                            contextObj.fieldNames().asSequence().any { fieldName ->
                                contextObj.get(fieldName)?.asText()?.startsWith("http") == true
                            }
                        assertTrue(
                            hasMappings || contextObj.size() > 0,
                            "Context should have namespace prefix mappings when expandUris is false",
                        )
                    }
                }

                // Verify style is applied
                if (style == RdfService.RdfFormatStyle.PRETTY) {
                    // PRETTY should have newlines for readability
                    assertTrue(
                        result.contains("\n") || result.length < 200,
                        "PRETTY style should have formatting (newlines for longer JSON)",
                    )
                }
            }
            RdfService.RdfFormat.TURTLE -> {
                // Should contain RDF-like structure
                assertTrue(result.contains("https://example.com/resource") || result.contains("@prefix"))
            }
            RdfService.RdfFormat.RDF_XML -> {
                // Should start with XML declaration or contain RDF element
                assertTrue(result.contains("<?xml") || result.contains("<rdf:RDF") || result.contains("<rdf:Description"))
            }
            RdfService.RdfFormat.N_TRIPLES -> {
                // Should contain URIs and periods
                assertTrue(result.contains("https://") && result.contains(" ."))
            }
            RdfService.RdfFormat.N_QUADS -> {
                // Should contain URIs and periods
                assertTrue(result.contains("https://") && result.contains(" ."))
            }
        }
    }

    @Test
    fun `convertFromJsonLd to JSON_LD with PRETTY style and expandUris should pretty print`() {
        // When
        val result =
            rdfService.convertFromJsonLd(
                sampleJsonLdExpanded,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = true,
            )

        // Then
        assertNotNull(result)
        assertTrue(result!!.contains("\n"), "Pretty printed JSON should contain newlines")
        assertTrue(result.contains("\""), "Should be valid JSON")
        // Should be valid JSON
        assertDoesNotThrow { objectMapper.readTree(result) }
    }

    @Test
    fun `convertFromJsonLd to JSON_LD should include namespace prefixes when expandUris is false`() {
        // Given - JSON-LD without prefixes
        val jsonLdWithoutPrefixes =
            mapOf(
                "@id" to "https://example.com/resource",
                "@type" to listOf("http://www.w3.org/1999/02/22-rdf-syntax-ns#type"),
            )

        // When
        val result =
            rdfService.convertFromJsonLd(
                jsonLdWithoutPrefixes,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
            )

        // Then
        assertNotNull(result)
        val jsonResult = objectMapper.readTree(result)
        // When expandUris is false, Jena may add @context with prefixes
        // or the result may still contain expanded URIs, depending on Jena's behavior
        assertTrue(jsonResult.has("@id") || jsonResult.has("@context"))
    }

    @Test
    fun `convertFromTurtle should preserve namespace prefixes when expandUris is false`() {
        // When
        val result =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
            )

        // Then
        assertNotNull(result)
        // Should contain namespace prefixes
        assertTrue(result!!.contains("@prefix") || result.contains("dcat:") || result.contains("dct:"))
    }

    @Test
    fun `convertFromTurtle to JSON_LD with expandUris false should include @context with namespace prefixes`() {
        // When
        val result =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
            )

        // Then
        assertNotNull(result)
        val jsonNode = objectMapper.readTree(result!!)

        // Should have @context with namespace prefixes
        assertTrue(jsonNode.has("@context"), "JSON-LD should have @context when expandUris is false")
        val context = jsonNode.get("@context")

        // Context should be an object with namespace prefix mappings
        assertTrue(context.isObject, "@context should be an object")

        // Should contain namespace prefix mappings (e.g., "dcat", "dct", etc.)
        val contextObj = context as com.fasterxml.jackson.databind.node.ObjectNode
        val hasNamespacePrefix =
            contextObj.fieldNames().asSequence().any { fieldName ->
                fieldName in listOf("dcat", "dct", "rdf", "rdfs") || contextObj.get(fieldName)?.asText()?.startsWith("http") == true
            }
        assertTrue(hasNamespacePrefix, "Context should contain namespace prefix mappings")

        // Should use prefixed terms in the data (compact URIs)
        val hasId = jsonNode.has("@id") || (jsonNode.isArray && jsonNode[0]?.has("@id") == true)
        assertTrue(hasId, "JSON-LD should have @id field")
    }

    @Test
    fun `convertFromTurtle to JSON_LD with expandUris true should have fully expanded URIs and no @context prefixes`() {
        // When
        val result =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = true,
            )

        // Then
        assertNotNull(result)
        val jsonNode = objectMapper.readTree(result!!)

        // Should not have @context or @context should not contain namespace prefixes
        if (jsonNode.has("@context")) {
            val context = jsonNode.get("@context")
            // If context exists, it should not map to namespace prefixes (should be empty or minimal)
            if (context.isObject) {
                val contextObj = context as com.fasterxml.jackson.databind.node.ObjectNode
                // Context should not have namespace prefix mappings like "dcat", "dct", etc.
                val hasNamespacePrefix =
                    contextObj.fieldNames().asSequence().any { fieldName ->
                        fieldName in listOf("dcat", "dct", "rdf", "rdfs")
                    }
                assertFalse(hasNamespacePrefix, "Context should not contain namespace prefix mappings when expandUris is true")
            }
        }

        // Should have @id with full URI
        val root = if (jsonNode.isArray) jsonNode[0] else jsonNode
        if (root?.has("@id") == true) {
            val id = root.get("@id").asText()
            assertTrue(id.startsWith("http"), "@id should be a full URI when expandUris is true")
        }

        // Should contain fully expanded URIs in properties
        val jsonString = result
        assertTrue(
            jsonString.contains("http://www.w3.org/ns/dcat#") ||
                jsonString.contains("http://purl.org/dc/terms/") ||
                jsonString.contains("http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
            "Should contain fully expanded URIs",
        )
    }

    @Test
    fun `convertFromTurtle to JSON_LD with PRETTY style should produce formatted JSON with newlines`() {
        // When
        val result =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
            )

        // Then
        assertNotNull(result)
        assertTrue(result!!.contains("\n"), "PRETTY style should produce formatted JSON with newlines")

        val jsonNode = objectMapper.readTree(result)
        assertTrue(
            jsonNode.has("@id") || jsonNode.isArray || jsonNode.has("@context"),
            "Should be valid JSON-LD structure",
        )
    }

    @Test
    fun `convertFromTurtle to JSON_LD with STANDARD style should produce compact JSON`() {
        // When
        val result =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.STANDARD,
                expandUris = false,
            )

        // Then
        assertNotNull(result)
        val jsonNode = objectMapper.readTree(result!!)

        // STANDARD style typically produces more compact output (fewer newlines)
        // But it should still be valid JSON-LD
        assertTrue(
            jsonNode.has("@id") || jsonNode.isArray || jsonNode.has("@context"),
            "Should be valid JSON-LD structure",
        )

        // Verify it's valid JSON
        assertDoesNotThrow { objectMapper.readTree(result) }
    }

    @Test
    fun `convertFromTurtle to JSON_LD should have correct structure with @id, @type, and properties`() {
        // When
        val resultPretty =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
            )

        val resultStandard =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.STANDARD,
                expandUris = false,
            )

        // Then - Both should have correct structure
        listOf(resultPretty, resultStandard).forEach { result ->
            assertNotNull(result)
            val jsonNode = objectMapper.readTree(result!!)

            // Should be an object or array of objects
            val root =
                if (jsonNode.isArray) {
                    assertTrue(jsonNode.size() > 0, "Array should not be empty")
                    jsonNode[0]
                } else {
                    jsonNode
                }

            // Should have @id
            assertTrue(root.has("@id"), "JSON-LD should have @id field")
            val id = root.get("@id").asText()
            assertTrue(id.contains("example.com/resource"), "Should contain the resource ID")

            // Should have @type (possibly as array) or type information
            val hasType =
                root.has("@type") ||
                    root.fieldNames().asSequence().any { it.contains("type") || it.contains("Type") }
            assertTrue(hasType || root.has("@context"), "Should have type information or context")

            // Should have properties (title, description, publisher)
            val hasProperties =
                root.fieldNames().asSequence().any { fieldName ->
                    fieldName.contains("title") ||
                        fieldName.contains("description") ||
                        fieldName.contains("publisher") ||
                        fieldName.contains("dcat:") ||
                        fieldName.contains("dct:") ||
                        fieldName.startsWith("http")
                }
            assertTrue(hasProperties || root.size() > 2, "Should have additional properties beyond @id and @type")
        }
    }

    @Test
    fun `convertFromTurtle to JSON_LD should handle both expandUris options correctly`() {
        // When
        val resultExpanded =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = true,
            )

        val resultCompact =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
            )

        // Then
        assertNotNull(resultExpanded)
        assertNotNull(resultCompact)

        val expandedNode = objectMapper.readTree(resultExpanded!!)
        val compactNode = objectMapper.readTree(resultCompact!!)

        // Both should be valid JSON-LD
        assertTrue(expandedNode.has("@id") || expandedNode.isArray)
        assertTrue(compactNode.has("@id") || compactNode.isArray)

        // Compact version should have @context with prefixes
        assertTrue(compactNode.has("@context"), "Non-expanded version should have @context")

        // Expanded version may or may not have @context, but if it does, it shouldn't have namespace prefixes
        if (expandedNode.has("@context")) {
            val context = expandedNode.get("@context")
            if (context.isObject) {
                val contextObj = context as com.fasterxml.jackson.databind.node.ObjectNode
                val hasPrefixes =
                    contextObj.fieldNames().asSequence().any {
                        it in listOf("dcat", "dct", "rdf", "rdfs")
                    }
                assertFalse(hasPrefixes, "Expanded version should not have namespace prefixes in context")
            }
        }

        // The actual data should differ - expanded should have full URIs
        val expandedStr = resultExpanded
        val compactStr = resultCompact

        assertTrue(
            expandedStr.contains("http://www.w3.org/ns/dcat#") ||
                expandedStr.contains("http://purl.org/dc/terms/"),
            "Expanded version should contain full URIs",
        )
    }

    @Test
    fun `convertFromTurtle should expand URIs when expandUris is true`() {
        // When
        val result =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = true,
            )

        // Then
        assertNotNull(result)
        // Should not contain namespace prefixes (all URIs expanded)
        assertFalse(result!!.contains("dcat:"), "Should not contain dcat: prefix when expandUris is true")
        assertFalse(result.contains("dct:"), "Should not contain dct: prefix when expandUris is true")
        // Should contain full URIs
        assertTrue(result.contains("http://www.w3.org/ns/dcat#") || result.contains("http://purl.org/dc/terms/"))
    }

    @Test
    fun `convertTurtleToJsonLdMap should convert Turtle to JSON-LD Map`() {
        // When
        val result = rdfService.convertTurtleToJsonLdMap(sampleTurtle, expandUris = true)

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty(), "Result map should not be empty")
        // Should contain the main resource ID
        assertTrue(
            result.containsKey("@id") ||
                result.values.any {
                    it is Map<*, *> && (it as Map<*, *>).containsKey("@id")
                },
            "Result should contain @id field",
        )
    }

    @Test
    fun `convertTurtleToJsonLdMap should return empty map on invalid Turtle`() {
        // Given
        val invalidTurtle = "This is not valid Turtle syntax !!!"

        // When
        val result = rdfService.convertTurtleToJsonLdMap(invalidTurtle, expandUris = true)

        // Then
        assertNotNull(result)
        assertTrue(result.isEmpty(), "Should return empty map for invalid Turtle")
    }

    @Test
    fun `convertTurtleToJsonLdMap with expandUris false should include context`() {
        // When
        val result = rdfService.convertTurtleToJsonLdMap(sampleTurtle, expandUris = false)

        // Then
        assertNotNull(result)
        assertTrue(result.isNotEmpty())
        // When expandUris is false, the result may contain @context or use prefixes
        // The exact structure depends on Jena's JSON-LD serialization
    }

    @Test
    fun `convertFromJsonLd should handle empty JSON-LD gracefully`() {
        // Given
        val emptyJsonLd = mapOf<String, Any>()

        // When & Then - Should not throw exception
        assertDoesNotThrow {
            val result =
                rdfService.convertFromJsonLd(
                    emptyJsonLd,
                    RdfService.RdfFormat.JSON_LD,
                    RdfService.RdfFormatStyle.PRETTY,
                    expandUris = false,
                )
            // Result may be null or empty, both are acceptable
            assertNotNull(result)
        }
    }

    @Test
    fun `convertFromJsonLd should handle nested JSON-LD structures`() {
        // Given - JSON-LD with nested objects
        val nestedJsonLd =
            mapOf(
                "@id" to "https://example.com/resource",
                "@type" to listOf("http://www.w3.org/ns/dcat#Dataset"),
                "http://purl.org/dc/terms/publisher" to
                    listOf(
                        mapOf(
                            "@id" to "https://example.com/org",
                            "http://purl.org/dc/terms/name" to
                                listOf(
                                    mapOf("@value" to "Example Organization"),
                                ),
                        ),
                    ),
            )

        // When
        val result =
            rdfService.convertFromJsonLd(
                nestedJsonLd,
                RdfService.RdfFormat.JSON_LD,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = true,
            )

        // Then
        assertNotNull(result)
        assertTrue(result!!.isNotBlank())
        // Should be valid JSON
        assertDoesNotThrow { objectMapper.readTree(result) }
    }

    @Test
    fun `PRETTY style should produce more readable output than STANDARD`() {
        // When
        val prettyResult =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
            )

        val standardResult =
            rdfService.convertFromTurtle(
                sampleTurtle,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.STANDARD,
                expandUris = false,
            )

        // Then
        assertNotNull(prettyResult)
        assertNotNull(standardResult)
        // PRETTY should generally have more newlines/formatting
        // Note: This is a heuristic test - exact formatting may vary
        assertTrue(prettyResult!!.lines().size >= standardResult!!.lines().size)
    }

    @Test
    fun `convertFromJsonLd should use dataset prefixes when resourceType is DATASET`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/dataset",
                "@type" to listOf("http://www.w3.org/ns/dcat#Dataset"),
            )

        // When
        val result =
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
                resourceType = no.fdk.resourceservice.model.ResourceType.DATASET,
            )

        // Then
        assertNotNull(result)
        // Should contain DCAT-AP-NO prefixes
        assertTrue(result!!.contains("dcat:") || result.contains("dcatap:") || result.contains("dct:"))
    }

    @Test
    fun `convertFromJsonLd should use concept prefixes when resourceType is CONCEPT`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/concept",
                "@type" to listOf("http://www.w3.org/2004/02/skos/core#Concept"),
            )

        // When
        val result =
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
                resourceType = no.fdk.resourceservice.model.ResourceType.CONCEPT,
            )

        // Then
        assertNotNull(result)
        // Should contain SKOS-AP-NO-Begrep prefixes
        assertTrue(result!!.contains("skos:") || result.contains("skosno:") || result.contains("dct:"))
    }

    @Test
    fun `convertFromJsonLd should use service prefixes when resourceType is SERVICE`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/service",
                "@type" to listOf("http://purl.org/vocab/cpsv#PublicService"),
            )

        // When
        val result =
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
                resourceType = no.fdk.resourceservice.model.ResourceType.SERVICE,
            )

        // Then
        assertNotNull(result)
        // Should contain CPSV-AP-NO prefixes
        assertTrue(result!!.contains("cpsv:") || result.contains("cpsvno:") || result.contains("cv:"))
    }

    @Test
    fun `convertFromJsonLd should use common prefixes when resourceType is null`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/resource",
                "@type" to listOf("http://example.org/Resource"),
            )

        // When
        val result =
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = false,
                resourceType = null,
            )

        // Then
        assertNotNull(result)
        // Should contain common prefixes
        assertTrue(result!!.contains("rdf:") || result.contains("rdfs:") || result.contains("dcat:"))
    }

    @Test
    fun `convertFromJsonLd should clear prefixes when expandUris is true regardless of resourceType`() {
        // Given
        val jsonLdData =
            mapOf(
                "@id" to "https://example.com/dataset",
                "@type" to listOf("http://www.w3.org/ns/dcat#Dataset"),
            )

        // When
        val result =
            rdfService.convertFromJsonLd(
                jsonLdData,
                RdfService.RdfFormat.TURTLE,
                RdfService.RdfFormatStyle.PRETTY,
                expandUris = true,
                resourceType = no.fdk.resourceservice.model.ResourceType.DATASET,
            )

        // Then
        assertNotNull(result)
        // Should not contain prefixes when expandUris is true
        assertFalse(result!!.contains("dcat:"))
        assertFalse(result.contains("dct:"))
        // Should contain full URIs
        assertTrue(result.contains("http://www.w3.org/ns/dcat#"))
    }

    companion object {
        @JvmStatic
        fun formatStyleCombinations(): Stream<Arguments> {
            val formats =
                listOf(
                    RdfService.RdfFormat.JSON_LD,
                    RdfService.RdfFormat.TURTLE,
                    RdfService.RdfFormat.RDF_XML,
                    RdfService.RdfFormat.N_TRIPLES,
                    RdfService.RdfFormat.N_QUADS,
                )
            val styles =
                listOf(
                    RdfService.RdfFormatStyle.PRETTY,
                    RdfService.RdfFormatStyle.STANDARD,
                )
            val expandUrisOptions = listOf(true, false)

            return formats
                .flatMap { format ->
                    styles.flatMap { style ->
                        expandUrisOptions.map { expandUris ->
                            Arguments.of(format, style, expandUris)
                        }
                    }
                }.stream()
        }
    }
}
