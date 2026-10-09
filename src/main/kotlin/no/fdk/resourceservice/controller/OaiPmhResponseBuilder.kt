package no.fdk.resourceservice.controller

import jakarta.servlet.http.HttpServletRequest
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceSnapshot
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * Builds OAI-PMH XML responses, identifiers, and resumption tokens.
 */
@Component
class OaiPmhResponseBuilder {
    private val logger = org.slf4j.LoggerFactory.getLogger(OaiPmhResponseBuilder::class.java)

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val transformerFactory = TransformerFactory.newInstance()

    companion object {
        const val OAI_NS = "http://www.openarchives.org/OAI/2.0/"
        const val OAI_SCHEMA = "http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd"
    }

    /**
     * Creates an OAI-PMH record from a resource snapshot.
     * Each snapshot becomes a separate record with its own identifier.
     * Datestamp uses resource_modified_at (harvest.modified) when present, else order.processedAt.
     * setSpec org:{publisherOrgnr} is added when publisher_orgnr is set.
     */
    fun createRecordFromSnapshot(
        doc: Document,
        id: String,
        snapshot: UnionGraphResourceSnapshot,
        order: UnionGraphOrder,
        metadataPrefix: String,
        httpRequest: HttpServletRequest,
    ): Element {
        val record = doc.createElement("record")
        val header = doc.createElement("header")

        val resourceIdentifier = createIdentifier(id, snapshot.resourceId, httpRequest)
        header.appendChild(createTextElement(doc, "identifier", resourceIdentifier))
        val datestamp = snapshot.resourceModifiedAt ?: order.processedAt ?: order.updatedAt
        header.appendChild(createTextElement(doc, "datestamp", formatDate(datestamp)))
        snapshot.publisherOrgnr?.let { orgnr ->
            header.appendChild(createTextElement(doc, "setSpec", "org:$orgnr"))
        }
        record.appendChild(header)

        val metadata = doc.createElement("metadata")
        // Get snapshot content in requested format
        val resourceContent = getSnapshotContent(snapshot, metadataPrefix)
        if (resourceContent != null) {
            // Parse the RDF-XML content and import it as actual XML elements (not CDATA)
            try {
                val rdfDoc = documentBuilder.parse(java.io.ByteArrayInputStream(resourceContent.toByteArray()))
                val rdfRoot = rdfDoc.documentElement
                // Import the root element (rdf:RDF) into the OAI-PMH document
                val importedNode = doc.importNode(rdfRoot, true)
                metadata.appendChild(importedNode)
            } catch (e: Exception) {
                logger.warn("Failed to parse RDF-XML for snapshot ${snapshot.id}: ${e.message}")
                // Fallback to CDATA if parsing fails
                val contentText = doc.createCDATASection(resourceContent)
                metadata.appendChild(contentText)
            }
        }
        record.appendChild(metadata)

        return record
    }

    /**
     * Gets snapshot content in RDF-XML format.
     * Snapshots are stored in RDF-XML format and returned directly without conversion.
     * The XML declaration is stripped so the metadata starts with <rdf:RDF.
     */
    fun getSnapshotContent(snapshot: UnionGraphResourceSnapshot, metadataPrefix: String): String? {
        // OAI-PMH only supports RDF-XML, so return the snapshot data directly
        val graphData = snapshot.resourceGraphData
        return if (graphData.isBlank()) {
            null
        } else {
            // Strip XML declaration if present (e.g., <?xml version="1.0" encoding="UTF-8"?>)
            // The metadata should start directly with <rdf:RDF
            val trimmed = graphData.trimStart()
            if (trimmed.startsWith("<?xml")) {
                trimmed.substringAfter("?>").trimStart()
            } else {
                trimmed
            }
        }
    }

    fun createOaiPmhDocument(): Document {
        val doc = documentBuilder.newDocument()
        val oaiPmh = doc.createElementNS(OAI_NS, "OAI-PMH")
        oaiPmh.setAttribute("xmlns", OAI_NS)
        oaiPmh.setAttribute("xmlns:xsi", "http://www.w3.org/2001/XMLSchema-instance")
        oaiPmh.setAttribute("xsi:schemaLocation", "$OAI_NS $OAI_SCHEMA")
        doc.appendChild(oaiPmh)

        // Add responseDate (required by OAI-PMH 2.0 spec)
        val responseDate = createTextElement(doc, "responseDate", formatDate(Instant.now()))
        oaiPmh.appendChild(responseDate)

        return doc
    }

    fun createRequestElement(
        doc: Document,
        id: String,
        params: Map<String, String> = emptyMap(),
        httpRequest: HttpServletRequest,
    ): Element {
        val request = doc.createElement("request")
        request.textContent = getBaseUrl(id, httpRequest)
        params.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                request.setAttribute(key, value)
            }
        }
        return request
    }

    fun createTextElement(doc: Document, tagName: String, text: String): Element {
        val element = doc.createElement(tagName)
        element.textContent = text
        return element
    }

    fun requestParams(
        verb: String,
        metadataPrefix: String? = null,
        identifier: String? = null,
        resumptionToken: String? = null,
        from: String? = null,
        until: String? = null,
        set: String? = null,
    ): Map<String, String> = buildMap {
        put("verb", verb)
        metadataPrefix?.takeIf { it.isNotBlank() }?.let { put("metadataPrefix", it) }
        identifier?.takeIf { it.isNotBlank() }?.let { put("identifier", it) }
        resumptionToken?.takeIf { it.isNotBlank() }?.let { put("resumptionToken", it) }
        from?.takeIf { it.isNotBlank() }?.let { put("from", it) }
        until?.takeIf { it.isNotBlank() }?.let { put("until", it) }
        set?.takeIf { it.isNotBlank() }?.let { put("set", it) }
    }

    fun errorResponse(
        code: String,
        message: String,
        id: String,
        httpRequest: HttpServletRequest,
        params: Map<String, String> = emptyMap(),
    ): ResponseEntity<String> {
        val doc = createOaiPmhDocument()
        val error = doc.createElement("error")
        error.setAttribute("code", code)
        error.textContent = message

        val attributes = if (code == "badVerb" || code == "badArgument") emptyMap() else params

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(createRequestElement(doc, id, attributes, httpRequest))
        response.appendChild(error)

        val status =
            when (code) {
                "badVerb", "badArgument" -> org.springframework.http.HttpStatus.BAD_REQUEST
                "idDoesNotExist" -> org.springframework.http.HttpStatus.NOT_FOUND
                else -> org.springframework.http.HttpStatus.BAD_REQUEST
            }

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_XML).body(documentToString(doc))
    }

    fun formatDate(instant: Instant): String =
        instant.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))

    fun getBaseUrl(id: String, httpRequest: HttpServletRequest): String {
        // Build full URL with scheme, host, and path
        return ServletUriComponentsBuilder
            .fromRequest(httpRequest)
            .replacePath("/v1/union-graphs/$id/oai-pmh")
            .replaceQuery(null)
            .build()
            .toUriString()
    }

    /**
     * Creates a valid OAI-PMH identifier URI.
     * OAI-PMH 2.0 requires identifiers to be valid URIs.
     * Format: {baseURL}/records/{resourceId}
     * The baseURL already contains the union graph ID, so we don't need to repeat it.
     * Uses "records" terminology as per OAI-PMH specification.
     */
    fun createIdentifier(id: String, resourceId: String, httpRequest: HttpServletRequest): String {
        val baseUrl = getBaseUrl(id, httpRequest)
        // Use path-based identifier to make it a valid URI
        // URL-encode the resourceId to handle special characters
        val encodedResourceId = java.net.URLEncoder.encode(resourceId, "UTF-8")
        return "$baseUrl/records/$encodedResourceId"
    }

    fun documentToString(doc: Document): String {
        val transformer = transformerFactory.newTransformer()
        transformer.setOutputProperty(OutputKeys.INDENT, "yes")
        transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        transformer.setOutputProperty(OutputKeys.METHOD, "xml")
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2")

        val source = DOMSource(doc)
        val result = java.io.StringWriter()
        transformer.transform(source, StreamResult(result))
        return result.toString()
    }

    /** OAI-PMH optional filter params (from/until dates and set orgnr). */
    data class OaiPmhFilterParams(val fromTs: java.sql.Timestamp?, val untilTs: java.sql.Timestamp?, val publisherOrgnr: String?)

    /**
     * Parses OAI-PMH date (from/until). Supports yyyy-MM-dd and yyyy-MM-dd'T'HH:mm:ss'Z'.
     */
    fun parseOaiDate(s: String?): Instant? {
        if (s.isNullOrBlank()) return null
        return try {
            if (s.length == 10) {
                java.time.LocalDate
                    .parse(s)
                    .atStartOfDay(java.time.ZoneOffset.UTC)
                    .toInstant()
            } else {
                Instant.parse(s)
            }
        } catch (e: DateTimeParseException) {
            null
        }
    }

    /**
     * Parses set parameter: must be org:{orgnr}. Returns orgnr or null if invalid.
     */
    fun parseSetOrgnr(set: String?): String? {
        if (set.isNullOrBlank()) return null
        if (!set.startsWith("org:")) return null
        val orgnr = set.removePrefix("org:").trim()
        return orgnr.takeIf { it.isNotEmpty() }
    }

    /**
     * Creates a resumption token for pagination.
     * Format without filters: {id}:{metadataPrefix}:{startIndex}
     * Format with filters: {id}:{metadataPrefix}:{startIndex}|{from}|{until}|{publisherOrgnr} (empty segment for absent)
     */
    fun createResumptionToken(id: String, metadataPrefix: String, startIndex: Int, filterParams: OaiPmhFilterParams? = null): String {
        val base = "$id:$metadataPrefix:$startIndex"
        if (filterParams == null ||
            (filterParams.fromTs == null && filterParams.untilTs == null && filterParams.publisherOrgnr.isNullOrBlank())
        ) {
            return base
        }
        val fromStr = filterParams.fromTs?.toInstant()?.toString() ?: ""
        val untilStr = filterParams.untilTs?.toInstant()?.toString() ?: ""
        val setStr = filterParams.publisherOrgnr ?: ""
        return "$base|$fromStr|$untilStr|$setStr"
    }

    /**
     * Parses a resumption token to extract offset, metadata prefix, and optional filter params.
     * Returns null if the token is invalid.
     */
    fun parseResumptionToken(token: String, expectedId: String): Pair<Int, String>? =
        parseResumptionTokenWithFilters(token, expectedId)?.let { (offset, prefix, _) -> Pair(offset, prefix) }

    /**
     * Parses a resumption token including optional from/until/set. Returns (offset, metadataPrefix, filterParams) or null.
     */
    fun parseResumptionTokenWithFilters(token: String, expectedId: String): Triple<Int, String, OaiPmhFilterParams?>? {
        val pipe = token.indexOf('|')
        val base = if (pipe >= 0) token.substring(0, pipe) else token
        val parts = base.split(":")
        if (parts.size != 3) return null
        val id = parts[0]
        val metadataPrefix = parts[1]
        val startIndex =
            try {
                parts[2].toInt()
            } catch (e: NumberFormatException) {
                return null
            }
        if (id != expectedId) return null

        val filterParams =
            if (pipe >= 0) {
                val rest = token.substring(pipe + 1)
                val segments = rest.split("|", limit = 3)
                val fromStr = segments.getOrNull(0)?.takeIf { it.isNotEmpty() }
                val untilStr = segments.getOrNull(1)?.takeIf { it.isNotEmpty() }
                val setStr = segments.getOrNull(2)?.takeIf { it.isNotEmpty() }
                val fromTs = parseOaiDate(fromStr)?.let { java.sql.Timestamp.from(it) }
                val untilTs = parseOaiDate(untilStr)?.let { java.sql.Timestamp.from(it) }
                OaiPmhFilterParams(fromTs, untilTs, setStr)
            } else {
                null
            }

        return Triple(startIndex, metadataPrefix, filterParams)
    }

    /**
     * Parses from/until/set request params and validates. Returns filter params or null if invalid or no filters.
     */
    fun parseAndValidateFilters(from: String?, until: String?, set: String?): OaiPmhFilterParams? {
        val fromTs = parseOaiDate(from)?.let { java.sql.Timestamp.from(it) }
        val untilTs = parseOaiDate(until)?.let { java.sql.Timestamp.from(it) }
        val publisherOrgnr = parseSetOrgnr(set)
        if (set != null && set.isNotBlank() && publisherOrgnr == null) {
            return null // badArgument: invalid set format
        }
        if (fromTs != null && untilTs != null && fromTs.after(untilTs)) {
            return null // badArgument: from > until
        }
        if (fromTs == null && untilTs == null && publisherOrgnr.isNullOrBlank()) {
            return null // no filters
        }
        return OaiPmhFilterParams(fromTs, untilTs, publisherOrgnr)
    }

    /**
     * Parses an OAI-PMH identifier to extract the resource ID.
     * Expected format: {baseURL}/records/{resourceId}
     * Where baseURL is /v1/union-graphs/{unionGraphId}/oai-pmh
     * Returns null if the format is invalid.
     */
    fun parseIdentifier(identifier: String, expectedUnionGraphId: String): String? {
        try {
            // Parse as URI to handle the path properly
            val uri = java.net.URI(identifier)
            val path = uri.path

            // Expected path format: /v1/union-graphs/{id}/oai-pmh/records/{resourceId}
            val pathParts = path.split("/").filter { it.isNotEmpty() }

            // Verify the path structure: v1, union-graphs, {id}, oai-pmh, records, {resourceId}
            if (pathParts.size < 6) {
                return null
            }

            // Verify path components
            if (pathParts[0] != "v1" ||
                pathParts[1] != "union-graphs" ||
                pathParts[3] != "oai-pmh" ||
                pathParts[4] != "records"
            ) {
                return null
            }

            // Extract and verify union graph ID
            val unionGraphId = pathParts[2]
            if (unionGraphId != expectedUnionGraphId) {
                return null
            }

            // Extract resourceId (last part of path)
            val encodedResourceId = pathParts[5]

            // URL-decode the resourceId
            return java.net.URLDecoder.decode(encodedResourceId, "UTF-8")
        } catch (e: Exception) {
            logger.debug("Failed to parse identifier: $identifier", e)
            return null
        }
    }
}
