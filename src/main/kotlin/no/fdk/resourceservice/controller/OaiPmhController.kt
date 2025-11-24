package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceSnapshot
import no.fdk.resourceservice.repository.UnionGraphResourceSnapshotRepository
import no.fdk.resourceservice.service.UnionGraphService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

/**
 * OAI-PMH (Open Archives Initiative Protocol for Metadata Harvesting) controller for union graphs.
 *
 * Implements OAI-PMH 2.0 protocol to allow harvesting of union graph resources.
 * Each resource is treated as a record in the OAI-PMH repository.
 * Resources are served from snapshots taken when the union graph was built to ensure consistency.
 */
@RestController
@RequestMapping("/v1/union-graphs/{id}/oai-pmh")
@Tag(name = "OAI-PMH", description = "OAI-PMH 2.0 protocol endpoint for harvesting union graph resources")
class OaiPmhController(
    private val unionGraphService: UnionGraphService,
    private val unionGraphResourceSnapshotRepository: UnionGraphResourceSnapshotRepository,
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(OaiPmhController::class.java)

    private val documentBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder()
    private val transformerFactory = TransformerFactory.newInstance()

    companion object {
        const val OAI_NS = "http://www.openarchives.org/OAI/2.0/"
        const val OAI_SCHEMA = "http://www.openarchives.org/OAI/2.0/OAI-PMH.xsd"
    }

    @GetMapping(produces = [MediaType.APPLICATION_XML_VALUE])
    @Operation(
        summary = "OAI-PMH endpoint",
        description =
            "OAI-PMH 2.0 protocol endpoint for harvesting union graph resources. " +
                "Supports Identify, ListMetadataFormats, GetRecord, ListIdentifiers, and ListRecords verbs. " +
                "Each resource is treated as a record with identifier format: {unionGraphId}:resource:{resourceId}. " +
                "\n\n" +
                "**Supported Verbs:**\n" +
                "- `Identify`: Returns repository information\n" +
                "- `ListMetadataFormats`: Lists available metadata formats (only rdfxml)\n" +
                "- `GetRecord`: Retrieves a single record by identifier\n" +
                "- `ListIdentifiers`: Lists record identifiers (headers only)\n" +
                "- `ListRecords`: Lists complete records with metadata\n" +
                "\n\n" +
                "**metadataPrefix Usage:**\n" +
                "The metadataPrefix parameter must be `rdfxml` (RDF/XML format). " +
                "This is the only supported format for OAI-PMH. " +
                "Resources are stored as RDF-XML snapshots and returned directly without conversion. " +
                "\n\n" +
                "**Pagination:**\n" +
                "Pagination is supported via resumption tokens for ListIdentifiers and ListRecords. " +
                "When a resumption token is provided, the metadataPrefix " +
                "is extracted from the token and does not need to be specified again. " +
                "The resumption token format is: `{unionGraphId}:rdfxml:{resourceOffset}`",
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "OAI-PMH response",
                content = [
                    io.swagger.v3.oas.annotations.media
                        .Content(mediaType = "application/xml"),
                ],
            ),
            ApiResponse(
                responseCode = "400",
                description = "Bad request (invalid verb or parameters)",
            ),
            ApiResponse(
                responseCode = "404",
                description = "Union graph not found",
            ),
        ],
    )
    fun oaiPmh(
        @Parameter(description = "Union graph ID")
        @PathVariable id: String,
        @Parameter(description = "OAI-PMH verb (Identify, ListMetadataFormats, GetRecord, ListIdentifiers, ListRecords)")
        @RequestParam(required = false) verb: String?,
        @Parameter(
            description =
                "Metadata prefix must be `rdfxml` (RDF/XML format). " +
                    "Required for GetRecord, ListIdentifiers, and ListRecords (unless resumptionToken is provided).",
            example = "rdfxml",
        )
        @RequestParam(required = false) metadataPrefix: String?,
        @Parameter(description = "Record identifier (required for GetRecord, optional for ListMetadataFormats)")
        @RequestParam(required = false) identifier: String?,
        @Parameter(description = "Resumption token (for pagination in ListIdentifiers and ListRecords)")
        @RequestParam(required = false) resumptionToken: String?,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        logger.debug("OAI-PMH request: verb={}, id={}, metadataPrefix={}, identifier={}", verb, id, metadataPrefix, identifier)

        // Validate verb
        val actualVerb = verb?.uppercase() ?: return errorResponse("badVerb", "Missing required argument: verb")

        // Route to appropriate handler
        return when (actualVerb) {
            "IDENTIFY" -> handleIdentify(id, request)
            "LISTMETADATAFORMATS" -> handleListMetadataFormats(id, identifier, request)
            "GETRECORD" -> handleGetRecord(id, identifier, metadataPrefix, request)
            "LISTIDENTIFIERS" -> handleListIdentifiers(id, metadataPrefix, resumptionToken, request)
            "LISTRECORDS" -> handleListRecords(id, metadataPrefix, resumptionToken, request)
            else ->
                errorResponse(
                    "badVerb",
                    "Illegal verb: $actualVerb. Supported verbs: Identify, ListMetadataFormats, GetRecord, ListIdentifiers, ListRecords",
                )
        }
    }

    private fun handleIdentify(
        id: String,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<String> {
        // Get union graph order to verify it exists
        val order =
            unionGraphService.getOrder(id)
                ?: return errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        val doc = createOaiPmhDocument()
        val request = createRequestElement(doc, "Identify", id, emptyMap(), httpRequest)
        val identify = doc.createElement("Identify")

        // Repository name
        identify.appendChild(createTextElement(doc, "repositoryName", "FDK Union Graph: ${order.name ?: id}"))

        // Base URL - full URL with scheme
        identify.appendChild(createTextElement(doc, "baseURL", getBaseUrl(id, httpRequest)))

        // Protocol version
        identify.appendChild(createTextElement(doc, "protocolVersion", "2.0"))

        // Admin email
        identify.appendChild(createTextElement(doc, "adminEmail", "fellesdatakatalog@digdir.no"))

        // Earliest datestamp (use order creation date)
        val earliestDate = order.createdAt ?: order.updatedAt
        identify.appendChild(createTextElement(doc, "earliestDatestamp", formatDate(earliestDate)))

        // Deleted record support: no (we don't support deletions)
        identify.appendChild(createTextElement(doc, "deletedRecord", "no"))

        // Granularity: YYYY-MM-DDThh:mm:ssZ
        identify.appendChild(createTextElement(doc, "granularity", "YYYY-MM-DDThh:mm:ssZ"))

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(identify)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(documentToString(doc))
    }

    private fun handleListMetadataFormats(
        id: String,
        identifier: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<String> {
        // Get union graph order to verify it exists
        val order =
            unionGraphService.getOrder(id)
                ?: return errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        // If identifier is provided, verify the record exists
        if (identifier != null) {
            val resourceId =
                parseIdentifier(identifier, id)
                    ?: return errorResponse("badArgument", "Invalid identifier format. Expected: $id:resource:{resourceId}")

            val sentinelTimestamp = java.sql.Timestamp.valueOf("2099-12-31 23:59:59")
            val beforeTimestamp =
                if (order.status == UnionGraphOrder.GraphStatus.PROCESSING) {
                    order.processingStartedAt?.let { java.sql.Timestamp.from(it) } ?: sentinelTimestamp
                } else {
                    sentinelTimestamp
                }

            val snapshot =
                unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceId(
                    id,
                    resourceId,
                    beforeTimestamp,
                )
            if (snapshot == null) {
                return errorResponse("idDoesNotExist", "Record with identifier '$identifier' does not exist")
            }
        }

        val doc = createOaiPmhDocument()
        val requestParams = mutableMapOf<String, String>()
        if (identifier != null) {
            requestParams["identifier"] = identifier
        }
        val request = createRequestElement(doc, "ListMetadataFormats", id, requestParams, httpRequest)
        val listMetadataFormats = doc.createElement("ListMetadataFormats")

        // Only support rdfxml format
        val metadataFormat = doc.createElement("metadataFormat")
        metadataFormat.appendChild(createTextElement(doc, "metadataPrefix", "rdfxml"))
        metadataFormat.appendChild(createTextElement(doc, "schema", "http://www.w3.org/1999/02/22-rdf-syntax-ns"))
        metadataFormat.appendChild(createTextElement(doc, "metadataNamespace", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"))
        listMetadataFormats.appendChild(metadataFormat)

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(listMetadataFormats)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(documentToString(doc))
    }

    private fun handleGetRecord(
        id: String,
        identifier: String?,
        metadataPrefix: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<String> {
        // Validate required parameters
        if (identifier == null) {
            return errorResponse("badArgument", "Missing required argument: identifier")
        }
        if (metadataPrefix == null) {
            return errorResponse("badArgument", "Missing required argument: metadataPrefix")
        }

        // Validate metadataPrefix
        if (metadataPrefix.lowercase() != "rdfxml") {
            return errorResponse("badArgument", "Only 'rdfxml' metadataPrefix is supported. Received: $metadataPrefix")
        }

        // Get union graph order
        val order =
            unionGraphService.getOrder(id)
                ?: return errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        // Only allow access if the union graph is completed (has snapshots available)
        if (order.status == UnionGraphOrder.GraphStatus.PENDING || order.status == UnionGraphOrder.GraphStatus.FAILED) {
            return errorResponse("idDoesNotExist", "Union graph with id '$id' is not available (status: ${order.status})")
        }

        // Parse identifier
        val resourceId =
            parseIdentifier(identifier, id)
                ?: return errorResponse("badArgument", "Invalid identifier format. Expected: $id:resource:{resourceId}")

        // Determine beforeTimestamp for consistency during rebuilds
        val sentinelTimestamp = java.sql.Timestamp.valueOf("2099-12-31 23:59:59")
        val beforeTimestamp =
            if (order.status == UnionGraphOrder.GraphStatus.PROCESSING) {
                order.processingStartedAt?.let { java.sql.Timestamp.from(it) } ?: sentinelTimestamp
            } else {
                sentinelTimestamp
            }

        // Find the snapshot
        val snapshot =
            unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceId(
                id,
                resourceId,
                beforeTimestamp,
            )
                ?: return errorResponse("idDoesNotExist", "Record with identifier '$identifier' does not exist")

        val doc = createOaiPmhDocument()
        val request =
            createRequestElement(
                doc,
                "GetRecord",
                id,
                mapOf("identifier" to identifier, "metadataPrefix" to metadataPrefix),
                httpRequest,
            )
        val getRecord = doc.createElement("GetRecord")

        val record = createRecordFromSnapshot(doc, id, snapshot, order, metadataPrefix, httpRequest)
        getRecord.appendChild(record)

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(getRecord)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(documentToString(doc))
    }

    private fun handleListIdentifiers(
        id: String,
        metadataPrefix: String?,
        resumptionToken: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<String> {
        // Get union graph order
        val order =
            unionGraphService.getOrder(id)
                ?: return errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        // Only allow access if the union graph is completed (has snapshots available)
        if (order.status == UnionGraphOrder.GraphStatus.PENDING || order.status == UnionGraphOrder.GraphStatus.FAILED) {
            return errorResponse("idDoesNotExist", "Union graph with id '$id' is not available (status: ${order.status})")
        }

        // Validate metadataPrefix - must be "rdfxml" if provided
        if (metadataPrefix != null && metadataPrefix.lowercase() != "rdfxml") {
            return errorResponse("badArgument", "Only 'rdfxml' metadataPrefix is supported. Received: $metadataPrefix")
        }

        if (metadataPrefix == null && resumptionToken == null) {
            return errorResponse("badArgument", "Missing required argument: metadataPrefix")
        }

        // Parse resumption token or start from beginning
        val (resourceOffset, actualMetadataPrefix) =
            if (resumptionToken != null) {
                parseResumptionToken(resumptionToken, id)
                    ?: return errorResponse("badResumptionToken", "Invalid resumption token")
            } else {
                Pair(0, metadataPrefix?.lowercase() ?: "rdfxml")
            }

        // Validate that the metadataPrefix is rdfxml (from token or parameter)
        if (actualMetadataPrefix.lowercase() != "rdfxml") {
            return errorResponse("badArgument", "Only 'rdfxml' metadataPrefix is supported. Received: $actualMetadataPrefix")
        }

        // Determine resource types to query
        val resourceTypes =
            order.resourceTypes?.mapNotNull { typeName ->
                try {
                    no.fdk.resourceservice.model.ResourceType
                        .valueOf(typeName)
                } catch (e: IllegalArgumentException) {
                    logger.warn("Unknown resource type: {}", typeName)
                    null
                }
            } ?: no.fdk.resourceservice.model.ResourceType.entries

        val currentResourceType =
            resourceTypes.firstOrNull()
                ?: return errorResponse("badArgument", "No valid resource types found")

        // Determine beforeTimestamp for consistency during rebuilds
        val sentinelTimestamp = java.sql.Timestamp.valueOf("2099-12-31 23:59:59")
        val beforeTimestamp =
            if (order.status == UnionGraphOrder.GraphStatus.PROCESSING) {
                order.processingStartedAt?.let { java.sql.Timestamp.from(it) } ?: sentinelTimestamp
            } else {
                sentinelTimestamp
            }

        // Fetch snapshots from database (50 per page)
        val pageSize = 50
        val snapshots =
            if (order.resourceTypes != null && order.resourceTypes.size == 1) {
                unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceTypePaginated(
                    id,
                    currentResourceType.name,
                    resourceOffset,
                    pageSize,
                    beforeTimestamp,
                )
            } else {
                unionGraphResourceSnapshotRepository.findByUnionGraphIdPaginated(
                    id,
                    resourceOffset,
                    pageSize,
                    beforeTimestamp,
                )
            }

        val doc = createOaiPmhDocument()
        val requestParams =
            mutableMapOf<String, String>().apply {
                if (resumptionToken == null) {
                    put("metadataPrefix", actualMetadataPrefix)
                }
            }
        val request = createRequestElement(doc, "ListIdentifiers", id, requestParams, httpRequest)
        val listIdentifiers = doc.createElement("ListIdentifiers")

        // Create a header for each snapshot (no metadata)
        for (snapshot in snapshots) {
            val header = doc.createElement("header")
            val resourceIdentifier = createIdentifier(id, snapshot.resourceId, httpRequest)
            header.appendChild(createTextElement(doc, "identifier", resourceIdentifier))
            header.appendChild(createTextElement(doc, "datestamp", formatDate(order.processedAt ?: order.updatedAt)))
            listIdentifiers.appendChild(header)
        }

        // Add resumption token if there are more snapshots
        if (snapshots.size >= pageSize) {
            val resumptionTokenElement = doc.createElement("resumptionToken")
            val nextToken = createResumptionToken(id, actualMetadataPrefix, resourceOffset + snapshots.size)
            resumptionTokenElement.textContent = nextToken
            listIdentifiers.appendChild(resumptionTokenElement)
        }

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(listIdentifiers)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(documentToString(doc))
    }

    private fun handleListRecords(
        id: String,
        metadataPrefix: String?,
        resumptionToken: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<String> {
        // Get union graph order
        val order =
            unionGraphService.getOrder(id)
                ?: return errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        // Only allow access if the union graph is completed (has snapshots available)
        if (order.status == UnionGraphOrder.GraphStatus.PENDING || order.status == UnionGraphOrder.GraphStatus.FAILED) {
            return errorResponse("idDoesNotExist", "Union graph with id '$id' is not available (status: ${order.status})")
        }
        // Validate metadataPrefix - must be "rdfxml" if provided
        if (metadataPrefix != null && metadataPrefix.lowercase() != "rdfxml") {
            return errorResponse("badArgument", "Only 'rdfxml' metadataPrefix is supported. Received: $metadataPrefix")
        }

        if (metadataPrefix == null && resumptionToken == null) {
            return errorResponse("badArgument", "Missing required argument: metadataPrefix")
        }

        // Parse resumption token or start from beginning
        // Default to "rdfxml" since it's the only supported format
        val (resourceOffset, actualMetadataPrefix) =
            if (resumptionToken != null) {
                parseResumptionToken(resumptionToken, id)
                    ?: return errorResponse("badResumptionToken", "Invalid resumption token")
            } else {
                Pair(0, metadataPrefix?.lowercase() ?: "rdfxml")
            }

        // Validate that the metadataPrefix is rdfxml (from token or parameter)
        if (actualMetadataPrefix.lowercase() != "rdfxml") {
            return errorResponse("badArgument", "Only 'rdfxml' metadataPrefix is supported. Received: $actualMetadataPrefix")
        }

        // Determine resource types to query (if order specifies types, filter by first type for simplicity)
        val resourceTypes =
            order.resourceTypes?.mapNotNull { typeName ->
                try {
                    no.fdk.resourceservice.model.ResourceType
                        .valueOf(typeName)
                } catch (e: IllegalArgumentException) {
                    logger.warn("Unknown resource type: {}", typeName)
                    null
                }
            } ?: no.fdk.resourceservice.model.ResourceType.entries

        // Use first resource type (or all if none specified)
        val currentResourceType =
            resourceTypes.firstOrNull()
                ?: return errorResponse("badArgument", "No valid resource types found")

        // During rebuilds (PROCESSING status), only return old snapshots to prevent inconsistency
        // When COMPLETED, return all snapshots (latest per resource)
        // Use a far future timestamp as sentinel value when we don't want to filter
        // This avoids PostgreSQL type inference issues with NULL parameters
        val sentinelTimestamp = java.sql.Timestamp.valueOf("2099-12-31 23:59:59")
        val beforeTimestamp =
            if (order.status == UnionGraphOrder.GraphStatus.PROCESSING) {
                order.processingStartedAt?.let { java.sql.Timestamp.from(it) } ?: sentinelTimestamp
            } else {
                sentinelTimestamp
            }

        // Fetch snapshots from database (50 per page)
        val pageSize = 50
        val snapshots =
            if (order.resourceTypes != null && order.resourceTypes.size == 1) {
                // Filter by resource type if order specifies a single type
                unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceTypePaginated(
                    id,
                    currentResourceType.name,
                    resourceOffset,
                    pageSize,
                    beforeTimestamp,
                )
            } else {
                // Otherwise fetch all snapshots (order by resource_id ensures consistent pagination)
                unionGraphResourceSnapshotRepository.findByUnionGraphIdPaginated(
                    id,
                    resourceOffset,
                    pageSize,
                    beforeTimestamp,
                )
            }

        val doc = createOaiPmhDocument()
        val requestParams =
            mutableMapOf<String, String>().apply {
                if (resumptionToken == null) {
                    put("metadataPrefix", actualMetadataPrefix)
                }
            }
        val request = createRequestElement(doc, "ListRecords", id, requestParams, httpRequest)
        val listRecords = doc.createElement("ListRecords")

        // Create a record for each snapshot
        for (snapshot in snapshots) {
            val record = createRecordFromSnapshot(doc, id, snapshot, order, actualMetadataPrefix, httpRequest)
            listRecords.appendChild(record)
        }

        // Add resumption token if there are more snapshots
        if (snapshots.size >= pageSize) {
            val resumptionTokenElement = doc.createElement("resumptionToken")
            val nextToken = createResumptionToken(id, actualMetadataPrefix, resourceOffset + snapshots.size)
            resumptionTokenElement.textContent = nextToken
            listRecords.appendChild(resumptionTokenElement)
        }

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(listRecords)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(documentToString(doc))
    }

    /**
     * Creates an OAI-PMH record from a resource snapshot.
     * Each snapshot becomes a separate record with its own identifier.
     */
    private fun createRecordFromSnapshot(
        doc: Document,
        id: String,
        snapshot: UnionGraphResourceSnapshot,
        order: UnionGraphOrder,
        metadataPrefix: String,
        httpRequest: HttpServletRequest,
    ): Element {
        val record = doc.createElement("record")
        val header = doc.createElement("header")

        // Use resource ID as identifier (valid URI format)
        val resourceIdentifier = createIdentifier(id, snapshot.resourceId, httpRequest)
        header.appendChild(createTextElement(doc, "identifier", resourceIdentifier))
        header.appendChild(createTextElement(doc, "datestamp", formatDate(order.processedAt ?: order.updatedAt)))
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
    private fun getSnapshotContent(
        snapshot: UnionGraphResourceSnapshot,
        metadataPrefix: String,
    ): String? {
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

    private fun createOaiPmhDocument(): Document {
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

    private fun createRequestElement(
        doc: Document,
        verb: String,
        id: String,
        params: Map<String, String> = emptyMap(),
        httpRequest: HttpServletRequest,
    ): Element {
        val request = doc.createElement("request")
        request.setAttribute("verb", verb)
        request.textContent = getBaseUrl(id, httpRequest)
        params.forEach { (key, value) ->
            if (value.isNotEmpty()) {
                request.setAttribute(key, value)
            }
        }
        return request
    }

    private fun createTextElement(
        doc: Document,
        tagName: String,
        text: String,
    ): Element {
        val element = doc.createElement(tagName)
        element.textContent = text
        return element
    }

    private fun errorResponse(
        code: String,
        message: String,
    ): ResponseEntity<String> {
        val doc = createOaiPmhDocument()
        val error = doc.createElement("error")
        error.setAttribute("code", code)
        error.textContent = message

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(error)

        val status =
            when (code) {
                "badVerb", "badArgument" -> org.springframework.http.HttpStatus.BAD_REQUEST
                "idDoesNotExist" -> org.springframework.http.HttpStatus.NOT_FOUND
                else -> org.springframework.http.HttpStatus.BAD_REQUEST
            }

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_XML).body(documentToString(doc))
    }

    private fun formatDate(instant: Instant): String =
        instant.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"))

    private fun getBaseUrl(
        id: String,
        httpRequest: HttpServletRequest,
    ): String {
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
    private fun createIdentifier(
        id: String,
        resourceId: String,
        httpRequest: HttpServletRequest,
    ): String {
        val baseUrl = getBaseUrl(id, httpRequest)
        // Use path-based identifier to make it a valid URI
        // URL-encode the resourceId to handle special characters
        val encodedResourceId = java.net.URLEncoder.encode(resourceId, "UTF-8")
        return "$baseUrl/records/$encodedResourceId"
    }

    private fun documentToString(doc: Document): String {
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

    /**
     * Creates a resumption token for pagination.
     * Format: {id}:{metadataPrefix}:{startIndex}
     */
    private fun createResumptionToken(
        id: String,
        metadataPrefix: String,
        startIndex: Int,
    ): String = "$id:$metadataPrefix:$startIndex"

    /**
     * Parses a resumption token to extract union graph ID, metadata prefix, and start index.
     * Returns null if the token is invalid.
     */
    private fun parseResumptionToken(
        token: String,
        expectedId: String,
    ): Pair<Int, String>? {
        val parts = token.split(":")
        if (parts.size != 3) {
            return null
        }
        val id = parts[0]
        val metadataPrefix = parts[1]
        val startIndex =
            try {
                parts[2].toInt()
            } catch (e: NumberFormatException) {
                return null
            }

        // Verify the ID matches
        if (id != expectedId) {
            return null
        }

        return Pair(startIndex, metadataPrefix)
    }

    /**
     * Parses an OAI-PMH identifier to extract the resource ID.
     * Expected format: {baseURL}/records/{resourceId}
     * Where baseURL is /v1/union-graphs/{unionGraphId}/oai-pmh
     * Returns null if the format is invalid.
     */
    private fun parseIdentifier(
        identifier: String,
        expectedUnionGraphId: String,
    ): String? {
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
