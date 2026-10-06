package no.fdk.resourceservice.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.servlet.http.HttpServletRequest
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.repository.UnionGraphResourceSnapshotRepository
import no.fdk.resourceservice.service.UnionGraphService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.w3c.dom.Element

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
    private val responseBuilder: OaiPmhResponseBuilder,
) {
    private val logger = org.slf4j.LoggerFactory.getLogger(OaiPmhController::class.java)

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
        @Parameter(description = "OAI-PMH from date (optional, for ListIdentifiers/ListRecords). ISO-8601 UTC.")
        @RequestParam(required = false) from: String?,
        @Parameter(description = "OAI-PMH until date (optional, for ListIdentifiers/ListRecords). ISO-8601 UTC.")
        @RequestParam(required = false) until: String?,
        @Parameter(description = "OAI-PMH set (optional, for ListIdentifiers/ListRecords). Use org:{orgnr} to filter by publisher.")
        @RequestParam(required = false) set: String?,
        request: HttpServletRequest,
    ): ResponseEntity<String> {
        logger.debug("OAI-PMH request: verb={}, id={}, metadataPrefix={}, identifier={}", verb, id, metadataPrefix, identifier)

        // Validate verb
        val actualVerb = verb?.uppercase() ?: return responseBuilder.errorResponse("badVerb", "Missing required argument: verb")

        // Route to appropriate handler
        return when (actualVerb) {
            "IDENTIFY" -> {
                handleIdentify(id, request)
            }

            "LISTMETADATAFORMATS" -> {
                handleListMetadataFormats(id, identifier, request)
            }

            "GETRECORD" -> {
                handleGetRecord(id, identifier, metadataPrefix, request)
            }

            "LISTIDENTIFIERS" -> {
                handleListIdentifiers(id, metadataPrefix, resumptionToken, from, until, set, request)
            }

            "LISTRECORDS" -> {
                handleListRecords(id, metadataPrefix, resumptionToken, from, until, set, request)
            }

            "LISTSETS" -> {
                handleListSets(id, request)
            }

            else -> {
                responseBuilder.errorResponse(
                    "badVerb",
                    "Illegal verb: $actualVerb. Supported verbs: Identify, ListMetadataFormats, GetRecord, ListIdentifiers, ListRecords, ListSets",
                )
            }
        }
    }

    private fun handleIdentify(id: String, httpRequest: HttpServletRequest): ResponseEntity<String> {
        // Get union graph order to verify it exists
        val order =
            unionGraphService.getOrder(id)
                ?: return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        val doc = responseBuilder.createOaiPmhDocument()
        val request = responseBuilder.createRequestElement(doc, "Identify", id, emptyMap(), httpRequest)
        val identify = doc.createElement("Identify")

        // Repository name
        identify.appendChild(responseBuilder.createTextElement(doc, "repositoryName", "FDK Union Graph: ${order.name}"))

        // Base URL - full URL with scheme
        identify.appendChild(responseBuilder.createTextElement(doc, "baseURL", responseBuilder.getBaseUrl(id, httpRequest)))

        // Protocol version
        identify.appendChild(responseBuilder.createTextElement(doc, "protocolVersion", "2.0"))

        // Admin email
        identify.appendChild(responseBuilder.createTextElement(doc, "adminEmail", "fellesdatakatalog@digdir.no"))

        // Earliest datestamp (use order creation date)
        val earliestDate = order.createdAt
        identify.appendChild(responseBuilder.createTextElement(doc, "earliestDatestamp", responseBuilder.formatDate(earliestDate)))

        // Deleted record support: no (we don't support deletions)
        identify.appendChild(responseBuilder.createTextElement(doc, "deletedRecord", "no"))

        // Granularity: YYYY-MM-DDThh:mm:ssZ
        identify.appendChild(responseBuilder.createTextElement(doc, "granularity", "YYYY-MM-DDThh:mm:ssZ"))

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(identify)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(responseBuilder.documentToString(doc))
    }

    private fun handleListMetadataFormats(id: String, identifier: String?, httpRequest: HttpServletRequest): ResponseEntity<String> {
        // Get union graph order to verify it exists
        val order =
            unionGraphService.getOrder(id)
                ?: return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        // If identifier is provided, verify the record exists
        if (identifier != null) {
            val resourceId =
                responseBuilder.parseIdentifier(identifier, id)
                    ?: return responseBuilder.errorResponse("badArgument", "Invalid identifier format. Expected: $id:resource:{resourceId}")

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
                return responseBuilder.errorResponse("idDoesNotExist", "Record with identifier '$identifier' does not exist")
            }
        }

        val doc = responseBuilder.createOaiPmhDocument()
        val requestParams = mutableMapOf<String, String>()
        if (identifier != null) {
            requestParams["identifier"] = identifier
        }
        val request = responseBuilder.createRequestElement(doc, "ListMetadataFormats", id, requestParams, httpRequest)
        val listMetadataFormats = doc.createElement("ListMetadataFormats")

        // Only support rdfxml format
        val metadataFormat = doc.createElement("metadataFormat")
        metadataFormat.appendChild(responseBuilder.createTextElement(doc, "metadataPrefix", "rdfxml"))
        metadataFormat.appendChild(responseBuilder.createTextElement(doc, "schema", "http://www.w3.org/1999/02/22-rdf-syntax-ns"))
        metadataFormat.appendChild(
            responseBuilder.createTextElement(doc, "metadataNamespace", "http://www.w3.org/1999/02/22-rdf-syntax-ns#"),
        )
        listMetadataFormats.appendChild(metadataFormat)

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(listMetadataFormats)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(responseBuilder.documentToString(doc))
    }

    private fun handleGetRecord(
        id: String,
        identifier: String?,
        metadataPrefix: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<String> {
        // Validate required parameters
        if (identifier == null) {
            return responseBuilder.errorResponse("badArgument", "Missing required argument: identifier")
        }
        if (metadataPrefix == null) {
            return responseBuilder.errorResponse("badArgument", "Missing required argument: metadataPrefix")
        }

        // Validate metadataPrefix
        if (metadataPrefix.lowercase() != "rdfxml") {
            return responseBuilder.errorResponse("badArgument", "Only 'rdfxml' metadataPrefix is supported. Received: $metadataPrefix")
        }

        // Get union graph order
        val order =
            unionGraphService.getOrder(id)
                ?: return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        // Block only when union graph has failed; PENDING (updating) or COMPLETED may still have snapshots
        if (order.status == UnionGraphOrder.GraphStatus.FAILED) {
            return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' is not available (status: ${order.status})")
        }

        // Parse identifier
        val resourceId =
            responseBuilder.parseIdentifier(identifier, id)
                ?: return responseBuilder.errorResponse("badArgument", "Invalid identifier format. Expected: $id:resource:{resourceId}")

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
                ?: return responseBuilder.errorResponse("idDoesNotExist", "Record with identifier '$identifier' does not exist")

        val doc = responseBuilder.createOaiPmhDocument()
        val request =
            responseBuilder.createRequestElement(
                doc,
                "GetRecord",
                id,
                mapOf("identifier" to identifier, "metadataPrefix" to metadataPrefix),
                httpRequest,
            )
        val getRecord = doc.createElement("GetRecord")

        val record = responseBuilder.createRecordFromSnapshot(doc, id, snapshot, order, metadataPrefix, httpRequest)
        getRecord.appendChild(record)

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(getRecord)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(responseBuilder.documentToString(doc))
    }

    private fun handleListSets(id: String, httpRequest: HttpServletRequest): ResponseEntity<String> {
        val order =
            unionGraphService.getOrder(id)
                ?: return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")
        if (order.status == UnionGraphOrder.GraphStatus.FAILED) {
            return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' is not available (status: ${order.status})")
        }
        val doc = responseBuilder.createOaiPmhDocument()
        val request = responseBuilder.createRequestElement(doc, "ListSets", id, emptyMap(), httpRequest)
        val listSets = doc.createElement("ListSets")
        val set = doc.createElement("set")
        set.appendChild(responseBuilder.createTextElement(doc, "setSpec", "org"))
        set.appendChild(responseBuilder.createTextElement(doc, "setName", "Organization (by orgnr)"))
        val setDescription = doc.createElement("setDescription")
        val dc = doc.createElementNS("http://purl.org/dc/elements/1.1/", "dc:description")
        dc.textContent = "Filter by publisher organization number. Use set=org:{orgnr} in ListIdentifiers/ListRecords."
        setDescription.appendChild(dc)
        set.appendChild(setDescription)
        listSets.appendChild(set)
        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(listSets)
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(responseBuilder.documentToString(doc))
    }

    private fun handleListIdentifiers(
        id: String,
        metadataPrefix: String?,
        resumptionToken: String?,
        from: String?,
        until: String?,
        set: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<String> {
        // Get union graph order
        val order =
            unionGraphService.getOrder(id)
                ?: return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        // Block only when union graph has failed; PENDING (updating) or COMPLETED may still have snapshots
        if (order.status == UnionGraphOrder.GraphStatus.FAILED) {
            return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' is not available (status: ${order.status})")
        }

        if (resumptionToken != null && (metadataPrefix != null || from != null || until != null || set != null)) {
            return responseBuilder.errorResponse(
                "badArgument",
                "resumptionToken is an exclusive argument and cannot be combined with metadataPrefix, from, until or set",
            )
        }

        // Validate metadataPrefix - must be "rdfxml" if provided
        if (metadataPrefix != null && metadataPrefix.lowercase() != "rdfxml") {
            return responseBuilder.errorResponse("badArgument", "Only 'rdfxml' metadataPrefix is supported. Received: $metadataPrefix")
        }

        if (metadataPrefix == null && resumptionToken == null) {
            return responseBuilder.errorResponse("badArgument", "Missing required argument: metadataPrefix")
        }

        // Parse resumption token or start from beginning (including optional from/until/set)
        val (resourceOffset, actualMetadataPrefix, filterParams) =
            if (resumptionToken != null) {
                val parsed =
                    responseBuilder.parseResumptionTokenWithFilters(resumptionToken, id)
                        ?: return responseBuilder.errorResponse("badResumptionToken", "Invalid resumption token")
                Triple(parsed.first, parsed.second, parsed.third)
            } else {
                val filters =
                    if (from != null || until != null || set != null) {
                        val f = responseBuilder.parseAndValidateFilters(from, until, set)
                        if (f == null) {
                            if (set != null && set.isNotBlank() && responseBuilder.parseSetOrgnr(set) == null) {
                                return responseBuilder.errorResponse("badArgument", "Invalid set format. Expected org:{orgnr}")
                            }
                            if (from != null && until != null) {
                                val fromTs = responseBuilder.parseOaiDate(from)
                                val untilTs = responseBuilder.parseOaiDate(until)
                                if (fromTs != null && untilTs != null && fromTs.isAfter(untilTs)) {
                                    return responseBuilder.errorResponse("badArgument", "from must be less than or equal to until")
                                }
                            }
                            null
                        } else {
                            f
                        }
                    } else {
                        null
                    }
                Triple(0, metadataPrefix?.lowercase() ?: "rdfxml", filters)
            }

        // Validate that the metadataPrefix is rdfxml (from token or parameter)
        if (actualMetadataPrefix.lowercase() != "rdfxml") {
            return responseBuilder.errorResponse(
                "badArgument",
                "Only 'rdfxml' metadataPrefix is supported. Received: $actualMetadataPrefix",
            )
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
                ?: return responseBuilder.errorResponse("badArgument", "No valid resource types found")

        // Determine beforeTimestamp for consistency during rebuilds
        val sentinelTimestamp = java.sql.Timestamp.valueOf("2099-12-31 23:59:59")
        val beforeTimestamp =
            if (order.status == UnionGraphOrder.GraphStatus.PROCESSING) {
                order.processingStartedAt?.let { java.sql.Timestamp.from(it) } ?: sentinelTimestamp
            } else {
                sentinelTimestamp
            }

        val fromTs = filterParams?.fromTs
        val untilTs = filterParams?.untilTs
        val publisherOrgnr = filterParams?.publisherOrgnr

        // Fetch snapshots from database (50 per page), with optional from/until/set filters
        val pageSize = 50
        val snapshots =
            if (fromTs != null || untilTs != null || !publisherOrgnr.isNullOrBlank()) {
                if (order.resourceTypes?.size == 1) {
                    unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceTypePaginated(
                        id,
                        currentResourceType.name,
                        resourceOffset,
                        pageSize,
                        beforeTimestamp,
                        fromTs,
                        untilTs,
                        publisherOrgnr,
                    )
                } else {
                    unionGraphResourceSnapshotRepository.findByUnionGraphIdPaginated(
                        id,
                        resourceOffset,
                        pageSize,
                        beforeTimestamp,
                        fromTs,
                        untilTs,
                        publisherOrgnr,
                    )
                }
            } else {
                if (order.resourceTypes?.size == 1) {
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
            }

        if (resumptionToken != null && resourceOffset > 0 && snapshots.isEmpty()) {
            return responseBuilder.errorResponse("badResumptionToken", "Resumption token is out of range")
        }

        val doc = responseBuilder.createOaiPmhDocument()
        val requestParams =
            mutableMapOf<String, String>().apply {
                if (resumptionToken == null) {
                    put("metadataPrefix", actualMetadataPrefix)
                    from?.takeIf { it.isNotBlank() }?.let { put("from", it) }
                    until?.takeIf { it.isNotBlank() }?.let { put("until", it) }
                    set?.takeIf { it.isNotBlank() }?.let { put("set", it) }
                }
            }
        val request = responseBuilder.createRequestElement(doc, "ListIdentifiers", id, requestParams, httpRequest)
        val listIdentifiers = doc.createElement("ListIdentifiers")

        for (snapshot in snapshots) {
            val header = doc.createElement("header")
            val resourceIdentifier = responseBuilder.createIdentifier(id, snapshot.resourceId, httpRequest)
            header.appendChild(responseBuilder.createTextElement(doc, "identifier", resourceIdentifier))
            val datestamp = snapshot.resourceModifiedAt ?: order.processedAt ?: order.updatedAt
            header.appendChild(responseBuilder.createTextElement(doc, "datestamp", responseBuilder.formatDate(datestamp)))
            snapshot.publisherOrgnr?.let { orgnr ->
                header.appendChild(responseBuilder.createTextElement(doc, "setSpec", "org:$orgnr"))
            }
            listIdentifiers.appendChild(header)
        }

        val totalCount =
            if (fromTs != null || untilTs != null || !publisherOrgnr.isNullOrBlank()) {
                if (order.resourceTypes?.size == 1) {
                    unionGraphResourceSnapshotRepository.countByUnionGraphIdAndResourceType(
                        id,
                        currentResourceType.name,
                        beforeTimestamp,
                        fromTs,
                        untilTs,
                        publisherOrgnr,
                    )
                } else {
                    unionGraphResourceSnapshotRepository.countByUnionGraphId(id, beforeTimestamp, fromTs, untilTs, publisherOrgnr)
                }
            } else {
                if (order.resourceTypes?.size == 1) {
                    unionGraphResourceSnapshotRepository.countByUnionGraphIdAndResourceType(id, currentResourceType.name, beforeTimestamp)
                } else {
                    unionGraphResourceSnapshotRepository.countByUnionGraphId(id, beforeTimestamp)
                }
            }

        val hasMoreRecords = resourceOffset + snapshots.size < totalCount
        if (resumptionToken != null || hasMoreRecords) {
            val resumptionTokenElement = doc.createElement("resumptionToken")
            resumptionTokenElement.setAttribute("completeListSize", totalCount.toString())
            if (hasMoreRecords) {
                resumptionTokenElement.textContent =
                    responseBuilder.createResumptionToken(id, actualMetadataPrefix, resourceOffset + snapshots.size, filterParams)
            }
            listIdentifiers.appendChild(resumptionTokenElement)
        }

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(listIdentifiers)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(responseBuilder.documentToString(doc))
    }

    private fun handleListRecords(
        id: String,
        metadataPrefix: String?,
        resumptionToken: String?,
        from: String?,
        until: String?,
        set: String?,
        httpRequest: HttpServletRequest,
    ): ResponseEntity<String> {
        val order =
            unionGraphService.getOrder(id)
                ?: return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' does not exist")

        if (order.status == UnionGraphOrder.GraphStatus.FAILED) {
            return responseBuilder.errorResponse("idDoesNotExist", "Union graph with id '$id' is not available (status: ${order.status})")
        }
        if (resumptionToken != null && (metadataPrefix != null || from != null || until != null || set != null)) {
            return responseBuilder.errorResponse(
                "badArgument",
                "resumptionToken is an exclusive argument and cannot be combined with metadataPrefix, from, until or set",
            )
        }

        if (metadataPrefix != null && metadataPrefix.lowercase() != "rdfxml") {
            return responseBuilder.errorResponse("badArgument", "Only 'rdfxml' metadataPrefix is supported. Received: $metadataPrefix")
        }

        if (metadataPrefix == null && resumptionToken == null) {
            return responseBuilder.errorResponse("badArgument", "Missing required argument: metadataPrefix")
        }

        val (resourceOffset, actualMetadataPrefix, filterParams) =
            if (resumptionToken != null) {
                val parsed =
                    responseBuilder.parseResumptionTokenWithFilters(resumptionToken, id)
                        ?: return responseBuilder.errorResponse("badResumptionToken", "Invalid resumption token")
                Triple(parsed.first, parsed.second, parsed.third)
            } else {
                val filters =
                    if (from != null || until != null || set != null) {
                        val f = responseBuilder.parseAndValidateFilters(from, until, set)
                        if (f == null) {
                            if (set != null && set.isNotBlank() && responseBuilder.parseSetOrgnr(set) == null) {
                                return responseBuilder.errorResponse("badArgument", "Invalid set format. Expected org:{orgnr}")
                            }
                            if (from != null && until != null) {
                                val fromTs = responseBuilder.parseOaiDate(from)
                                val untilTs = responseBuilder.parseOaiDate(until)
                                if (fromTs != null && untilTs != null && fromTs.isAfter(untilTs)) {
                                    return responseBuilder.errorResponse("badArgument", "from must be less than or equal to until")
                                }
                            }
                            null
                        } else {
                            f
                        }
                    } else {
                        null
                    }
                Triple(0, metadataPrefix?.lowercase() ?: "rdfxml", filters)
            }

        if (actualMetadataPrefix.lowercase() != "rdfxml") {
            return responseBuilder.errorResponse(
                "badArgument",
                "Only 'rdfxml' metadataPrefix is supported. Received: $actualMetadataPrefix",
            )
        }

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
                ?: return responseBuilder.errorResponse("badArgument", "No valid resource types found")

        val sentinelTimestamp = java.sql.Timestamp.valueOf("2099-12-31 23:59:59")
        val beforeTimestamp =
            if (order.status == UnionGraphOrder.GraphStatus.PROCESSING) {
                order.processingStartedAt?.let { java.sql.Timestamp.from(it) } ?: sentinelTimestamp
            } else {
                sentinelTimestamp
            }

        val fromTs = filterParams?.fromTs
        val untilTs = filterParams?.untilTs
        val publisherOrgnr = filterParams?.publisherOrgnr

        val pageSize = 50
        val snapshots =
            if (fromTs != null || untilTs != null || !publisherOrgnr.isNullOrBlank()) {
                if (order.resourceTypes?.size == 1) {
                    unionGraphResourceSnapshotRepository.findByUnionGraphIdAndResourceTypePaginated(
                        id,
                        currentResourceType.name,
                        resourceOffset,
                        pageSize,
                        beforeTimestamp,
                        fromTs,
                        untilTs,
                        publisherOrgnr,
                    )
                } else {
                    unionGraphResourceSnapshotRepository.findByUnionGraphIdPaginated(
                        id,
                        resourceOffset,
                        pageSize,
                        beforeTimestamp,
                        fromTs,
                        untilTs,
                        publisherOrgnr,
                    )
                }
            } else {
                if (order.resourceTypes?.size == 1) {
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
            }

        if (resumptionToken != null && resourceOffset > 0 && snapshots.isEmpty()) {
            return responseBuilder.errorResponse("badResumptionToken", "Resumption token is out of range")
        }

        val doc = responseBuilder.createOaiPmhDocument()
        val requestParams =
            mutableMapOf<String, String>().apply {
                if (resumptionToken == null) {
                    put("metadataPrefix", actualMetadataPrefix)
                    from?.takeIf { it.isNotBlank() }?.let { put("from", it) }
                    until?.takeIf { it.isNotBlank() }?.let { put("until", it) }
                    set?.takeIf { it.isNotBlank() }?.let { put("set", it) }
                }
            }
        val request = responseBuilder.createRequestElement(doc, "ListRecords", id, requestParams, httpRequest)
        val listRecords = doc.createElement("ListRecords")

        for (snapshot in snapshots) {
            val record = responseBuilder.createRecordFromSnapshot(doc, id, snapshot, order, actualMetadataPrefix, httpRequest)
            listRecords.appendChild(record)
        }

        val totalCount =
            if (fromTs != null || untilTs != null || !publisherOrgnr.isNullOrBlank()) {
                if (order.resourceTypes?.size == 1) {
                    unionGraphResourceSnapshotRepository.countByUnionGraphIdAndResourceType(
                        id,
                        currentResourceType.name,
                        beforeTimestamp,
                        fromTs,
                        untilTs,
                        publisherOrgnr,
                    )
                } else {
                    unionGraphResourceSnapshotRepository.countByUnionGraphId(id, beforeTimestamp, fromTs, untilTs, publisherOrgnr)
                }
            } else {
                if (order.resourceTypes?.size == 1) {
                    unionGraphResourceSnapshotRepository.countByUnionGraphIdAndResourceType(id, currentResourceType.name, beforeTimestamp)
                } else {
                    unionGraphResourceSnapshotRepository.countByUnionGraphId(id, beforeTimestamp)
                }
            }

        val hasMoreRecords = resourceOffset + snapshots.size < totalCount
        if (resumptionToken != null || hasMoreRecords) {
            val resumptionTokenElement = doc.createElement("resumptionToken")
            resumptionTokenElement.setAttribute("completeListSize", totalCount.toString())
            if (hasMoreRecords) {
                resumptionTokenElement.textContent =
                    responseBuilder.createResumptionToken(id, actualMetadataPrefix, resourceOffset + snapshots.size, filterParams)
            }
            listRecords.appendChild(resumptionTokenElement)
        }

        val response = doc.getElementsByTagName("OAI-PMH").item(0) as Element
        response.appendChild(request)
        response.appendChild(listRecords)

        return ResponseEntity.ok().contentType(MediaType.APPLICATION_XML).body(responseBuilder.documentToString(doc))
    }
}
