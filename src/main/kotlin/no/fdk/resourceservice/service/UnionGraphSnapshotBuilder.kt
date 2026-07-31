package no.fdk.resourceservice.service

import no.fdk.resourceservice.config.UnionGraphConfig
import no.fdk.resourceservice.model.ResourceEntity
import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.model.UnionGraphResourceSnapshot
import no.fdk.resourceservice.repository.ResourceRepository
import no.fdk.resourceservice.repository.UnionGraphResourceSnapshotRepository
import org.apache.jena.rdf.model.ModelFactory
import org.apache.jena.riot.Lang
import org.apache.jena.riot.RDFDataMgr
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.io.ByteArrayInputStream
import java.io.StringWriter

/**
 * Builds union graph snapshots from resource graphs.
 *
 * Handles the RDF processing needed to turn a resource's stored graph data into a
 * union-graph snapshot: parsing, dataset-series filtering, DataService graph expansion,
 * catalog graph merging/filtering, and conversion to RDF/XML for snapshot storage.
 */
@Service
class UnionGraphSnapshotBuilder(
    private val resourceRepository: ResourceRepository,
    private val resourceService: ResourceService,
    private val unionGraphConfig: UnionGraphConfig,
    private val metricsService: UnionGraphMetricsService,
    private val unionGraphResourceSnapshotRepository: UnionGraphResourceSnapshotRepository,
) {
    private val logger = LoggerFactory.getLogger(UnionGraphSnapshotBuilder::class.java)

    /**
     * Outcome of attempting to create a snapshot from a single resource.
     */
    internal sealed class SnapshotResult {
        data class Included(
            val snapshot: UnionGraphResourceSnapshot,
        ) : SnapshotResult()

        data object Skipped : SnapshotResult()

        data object Error : SnapshotResult()
    }

    /**
     * Parses a resource graph, applies dataset-series filtering, converts to RDF/XML,
     * and builds a union-graph snapshot when the resource should be included.
     */
    internal fun createSnapshotFromResource(
        orderId: String,
        resource: ResourceEntity,
        resourceType: ResourceType,
        datasetFilters: UnionGraphResourceFilters.DatasetFilters?,
        expandDistributionAccessServices: Boolean,
        includeCatalog: Boolean,
    ): SnapshotResult {
        val graphData = resource.resourceGraphData
        if (graphData.isNullOrBlank()) {
            return SnapshotResult.Skipped
        }

        val model = parseGraphToModel(graphData, resource.resourceGraphFormat)
        if (model == null) {
            logger.warn("Failed to parse graph for resource {}, skipping", resource.id)
            return SnapshotResult.Error
        }

        try {
            val isDatasetSeriesFilter = datasetFilters?.isDatasetSeries
            val shouldInclude =
                if (resourceType == ResourceType.DATASET && isDatasetSeriesFilter != null) {
                    val isDatasetSeries = isDatasetSeriesInModel(model, resource.uri)
                    // Filter logic: null = both, true = only series, false = no series
                    when (isDatasetSeriesFilter) {
                        true -> isDatasetSeries
                        false -> !isDatasetSeries
                    }
                } else {
                    true
                }

            if (!shouldInclude) {
                return SnapshotResult.Skipped
            }

            val rdfXmlData =
                processResourceModelToRdfXml(
                    model,
                    resource,
                    expandDistributionAccessServices,
                    includeCatalog,
                )

            if (rdfXmlData == null) {
                logger.warn("Failed to process resource {} to RDF/XML, skipping snapshot", resource.id)
                return SnapshotResult.Error
            }

            return SnapshotResult.Included(
                UnionGraphResourceSnapshot(
                    unionGraphId = orderId,
                    resourceId = resource.id,
                    resourceType = resource.resourceType,
                    resourceGraphData = rdfXmlData,
                    resourceGraphFormat = "RDF_XML",
                    resourceModifiedAt = parseResourceModifiedAt(resource.resourceJson),
                    publisherOrgnr = parsePublisherOrgnr(resource.resourceJson),
                ),
            )
        } finally {
            model.close()
        }
    }

    /**
     * Builds a union graph by saving snapshots of resource graphs.
     *
     * This method processes resources in batches and saves snapshots of each resource's graph data
     * to ensure consistency when serving via OAI-PMH. Resources are processed in batches to avoid
     * loading all resources into memory at once.
     *
     * @param resourceTypes Optional list of resource types to include. If null or empty, all types are included.
     * @param resourceFilters Optional per-resource-type filters to apply when collecting resources.
     *                        For example, dataset filters can filter by isOpenData, isRelatedToTransportportal, and isDatasetSeries.
     *                        Only resources matching the filters will be included in the union graph.
     * @param expandDistributionAccessServices If true, datasets with distributions that reference DataService URIs
     *                                          (via distribution[].accessService[].uri) will have those DataService
     *                                          graphs automatically included in the union graph.
     * @param rdfFormat The RDF format (not used for snapshots, kept for compatibility).
     * @param orderId Optional order ID for progress tracking. Required for saving snapshots.
     * @return true if successful, false if no resources found or orderId is null.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = false)
    fun buildUnionGraph(
        resourceTypes: List<ResourceType>? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean = false,
        rdfFormat: RdfService.RdfFormat = RdfService.RdfFormat.TURTLE,
        orderId: String? = null, // Required for saving snapshots
        includeCatalog: Boolean = true,
    ): Boolean {
        if (orderId == null) {
            logger.warn("buildUnionGraph called without orderId - snapshots cannot be saved")
            return false
        }

        logger.info(
            "Building union graph snapshots for resource types: {}, expandDistributionAccessServices: {}",
            resourceTypes,
            expandDistributionAccessServices,
        )

        // Determine which resource types to process
        val typesToProcess = resourceTypes?.ifEmpty { null } ?: ResourceType.entries
        val datasetFilters = resourceFilters?.dataset

        // Count total resources to process
        var totalResources = 0L
        for (type in typesToProcess) {
            totalResources +=
                when {
                    type == ResourceType.DATASET && datasetFilters != null -> {
                        resourceRepository.countDatasetsByFilters(
                            datasetFilters.isOpenData.toSqlBooleanText(),
                            datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                        )
                    }

                    else -> {
                        resourceRepository.countByResourceTypeAndDeletedFalse(type.name)
                    }
                }
        }

        if (totalResources == 0L) {
            logger.warn("No resources found for union graph")
            return false
        }

        logger.info(
            "Found {} total resources to snapshot (processing in batches of {})",
            totalResources,
            unionGraphConfig.resourceBatchSize,
        )

        val totalStartTime = System.nanoTime()

        try {
            var processedCount = 0L
            val batchSize = unionGraphConfig.resourceBatchSize

            // Process each resource type
            for (type in typesToProcess) {
                var offset = 0
                var hasMore = true

                // Process resources in batches
                while (hasMore) {
                    val batch =
                        when {
                            type == ResourceType.DATASET && datasetFilters != null -> {
                                resourceRepository.findDatasetsByFiltersWithGraphDataPaginated(
                                    offset,
                                    batchSize,
                                    datasetFilters.isOpenData.toSqlBooleanText(),
                                    datasetFilters.isRelatedToTransportportal.toSqlBooleanText(),
                                )
                            }

                            else -> {
                                resourceRepository.findByResourceTypeAndDeletedFalseWithGraphDataPaginated(
                                    type.name,
                                    offset,
                                    batchSize,
                                )
                            }
                        }

                    if (batch.isEmpty()) {
                        hasMore = false
                    } else {
                        // Collect snapshots for batch insert
                        val snapshotsToSave = mutableListOf<UnionGraphResourceSnapshot>()

                        for (resource in batch) {
                            when (
                                val result =
                                    createSnapshotFromResource(
                                        orderId = orderId,
                                        resource = resource,
                                        resourceType = type,
                                        datasetFilters = datasetFilters,
                                        expandDistributionAccessServices = expandDistributionAccessServices,
                                        includeCatalog = includeCatalog,
                                    )
                            ) {
                                is SnapshotResult.Included -> snapshotsToSave.add(result.snapshot)
                                is SnapshotResult.Skipped, is SnapshotResult.Error -> Unit
                            }
                        }

                        // Batch insert all snapshots at once (much more efficient than individual saves)
                        if (snapshotsToSave.isNotEmpty()) {
                            unionGraphResourceSnapshotRepository.saveAll(snapshotsToSave)
                        }

                        processedCount += batch.size

                        // Update progress if orderId provided
                        metricsService.updateProcessingProgress(orderId, processedCount)
                        metricsService.recordResourcesProcessed(batch.size.toLong())

                        // Log progress periodically
                        if (processedCount % UnionGraphConfig.UNION_GRAPH_PROGRESS_UPDATE_INTERVAL == 0L) {
                            logger.debug("Processed {} resources so far...", processedCount)
                        }

                        // Check if we've processed all resources for this type
                        if (batch.size < batchSize) {
                            hasMore = false
                        } else {
                            offset += batchSize
                        }
                    }
                }
            }

            val totalTime = (System.nanoTime() - totalStartTime) / 1_000_000.0
            logger.info(
                "Successfully built union graph snapshots from {} resources in {} ms",
                processedCount,
                String.format("%.2f", totalTime),
            )
            logger.debug("=== Union Graph Build Performance ===")
            logger.debug("Total time: {} ms ({} s)", String.format("%.2f", totalTime), String.format("%.2f", totalTime / 1000.0))
            logger.debug(
                "Resources processed: {}, Average: {} ms/resource",
                processedCount,
                String.format(
                    "%.2f",
                    totalTime / processedCount,
                ),
            )

            // Note: Cleanup of old snapshots is handled in processOrder when the order is marked as COMPLETED
            // This ensures old snapshots remain accessible during the build and until the new build is complete

            return true
        } catch (e: Exception) {
            logger.error("Failed to build union graph snapshots", e)
            throw e
        }
    }

    /**
     * Extracts resource modified date from FDK resource JSON (harvest.modified, ISO-8601).
     * Used for OAI-PMH from/until and datestamp.
     */
    private fun parseResourceModifiedAt(resourceJson: Map<String, Any>?): java.time.Instant? {
        if (resourceJson == null) return null
        val harvest = resourceJson["harvest"] as? Map<*, *> ?: return null
        val modified = harvest["modified"] as? String ?: return null
        return try {
            java.time.Instant.parse(modified)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Extracts publisher organization number from FDK resource JSON (publisher.id).
     * Used for OAI-PMH set filter org:orgnr and setSpec in headers.
     */
    private fun parsePublisherOrgnr(resourceJson: Map<String, Any>?): String? {
        if (resourceJson == null) return null
        val publisher = resourceJson["publisher"] as? Map<*, *> ?: return null
        val id = publisher["id"] ?: return null
        return id.toString().takeIf { it.isNotBlank() }
    }

    /**
     * Merges DataService graphs into an existing Jena Model.
     *
     * @param model The existing model to merge DataService graphs into (modified in place)
     * @param dataServiceUris Set of DataService URIs to fetch and merge
     */
    private fun mergeDataServiceGraphsIntoModel(
        model: org.apache.jena.rdf.model.Model,
        dataServiceUris: Set<String>,
    ) {
        if (dataServiceUris.isEmpty()) {
            return
        }

        var mergedCount = 0
        for (uri in dataServiceUris) {
            try {
                val dataServiceEntity = resourceService.getResourceEntityByUri(uri) ?: continue
                val resourceGraphData = dataServiceEntity.resourceGraphData
                if (!resourceGraphData.isNullOrBlank()) {
                    val dataServiceLang = parseLang(dataServiceEntity.resourceGraphFormat ?: "TURTLE")
                    ByteArrayInputStream(resourceGraphData.toByteArray()).use { inputStream ->
                        RDFDataMgr.read(model, inputStream, dataServiceLang)
                    }
                    mergedCount++
                }
            } catch (e: Exception) {
                logger.warn("Failed to merge DataService graph for URI {}: {}", uri, e.message)
            }
        }

        if (mergedCount > 0) {
            logger.debug("Merged {} DataService graph(s) into dataset graph", mergedCount)
        }
    }

    /**
     * Filters out catalog-type resources from a Jena Model.
     * Removes all statements where dcat:Catalog, dcat:CatalogRecord, or skos:Collection is the subject,
     * but preserves statements where their URIs appear as objects (references).
     *
     * Used as a legacy fallback for resources where catalog metadata was embedded in resource_graph_data
     * before the catalogGraph split in Kafka events.
     *
     * @param model The Jena Model to filter (modified in place)
     */
    private fun filterCatalogFromModel(model: org.apache.jena.rdf.model.Model) {
        try {
            val catalogType = model.createResource("http://www.w3.org/ns/dcat#Catalog")
            val catalogRecordType = model.createResource("http://www.w3.org/ns/dcat#CatalogRecord")
            val collectionType = model.createResource("http://www.w3.org/2004/02/skos/core#Collection")
            val rdfType = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

            val resourcesToRemove = mutableSetOf<org.apache.jena.rdf.model.Resource>()

            for (type in listOf(catalogType, catalogRecordType, collectionType)) {
                val stmts = model.listStatements(null, rdfType, type)
                while (stmts.hasNext()) {
                    val resource = stmts.nextStatement().subject
                    if (resource.isURIResource) {
                        resourcesToRemove.add(resource)
                    }
                }
            }

            for (resource in resourcesToRemove) {
                model.removeAll(resource, null, null)
            }
        } catch (e: Exception) {
            logger.warn("Failed to filter catalog types from model: {}", e.message)
        }
    }

    /**
     * Merges catalog graph data into a resource model for union graph snapshots.
     */
    private fun mergeCatalogGraphIntoModel(
        model: org.apache.jena.rdf.model.Model,
        catalogGraphData: String,
        catalogGraphFormat: String?,
    ) {
        val catalogModel = parseGraphToModel(catalogGraphData, catalogGraphFormat) ?: return
        try {
            model.add(catalogModel)
        } finally {
            catalogModel.close()
        }
    }

    /**
     * Parses RDF graph data into a Jena Model.
     *
     * @param graphData The RDF graph data (in any supported format)
     * @param graphFormat The format of the graph data (TURTLE, JSON-LD, RDF/XML, etc.)
     * @return The parsed model, or null on error
     */
    private fun parseGraphToModel(
        graphData: String,
        graphFormat: String?,
    ): org.apache.jena.rdf.model.Model? {
        return try {
            if (graphData.isBlank()) {
                return null
            }

            val model = ModelFactory.createDefaultModel()
            val lang = parseLang(graphFormat ?: "TURTLE")

            ByteArrayInputStream(graphData.toByteArray()).use { inputStream ->
                RDFDataMgr.read(model, inputStream, lang)
            }

            model
        } catch (e: Exception) {
            logger.warn("Failed to parse RDF graph: {}", e.message)
            null
        }
    }

    /**
     * Checks if a dataset resource has rdf:type = dcat:DatasetSeries in a Jena Model.
     *
     * @param model The Jena Model containing the RDF graph
     * @param datasetUri The URI of the dataset resource to check
     * @return true if the dataset is a DatasetSeries, false otherwise
     */
    private fun isDatasetSeriesInModel(
        model: org.apache.jena.rdf.model.Model,
        datasetUri: String?,
    ): Boolean {
        if (datasetUri.isNullOrBlank()) {
            return false
        }

        val datasetResource = model.createResource(datasetUri)
        val datasetSeriesType = model.createResource("http://www.w3.org/ns/dcat#DatasetSeries")
        val rdfType = model.createProperty("http://www.w3.org/1999/02/22-rdf-syntax-ns#type")

        return model.contains(datasetResource, rdfType, datasetSeriesType)
    }

    /**
     * Processes a resource model: merges DataService graphs (if needed), merges or filters catalog
     * metadata (if needed), and converts to RDF/XML for snapshot storage.
     *
     * @param model The Jena Model to process (will be modified in place)
     * @param resource The resource entity
     * @param expandDistributionAccessServices Whether to expand DataService graphs
     * @param includeCatalog Whether to merge catalog_graph_data into the snapshot
     * @return The RDF/XML string for snapshot storage, or null on error
     */
    private fun processResourceModelToRdfXml(
        model: org.apache.jena.rdf.model.Model,
        resource: ResourceEntity,
        expandDistributionAccessServices: Boolean,
        includeCatalog: Boolean,
    ): String? {
        try {
            // Merge DataService graphs if needed
            if (expandDistributionAccessServices && resource.resourceType == ResourceType.DATASET.name) {
                val datasetJson = resource.resourceJson
                if (datasetJson != null) {
                    val dataServiceUris = mutableSetOf<String>()
                    extractDataServiceUris(datasetJson, dataServiceUris)
                    if (dataServiceUris.isNotEmpty()) {
                        mergeDataServiceGraphsIntoModel(model, dataServiceUris)
                    }
                }
            }

            if (includeCatalog) {
                val catalogGraphData = resource.catalogGraphData
                if (!catalogGraphData.isNullOrBlank()) {
                    mergeCatalogGraphIntoModel(model, catalogGraphData, resource.catalogGraphFormat)
                }
            } else {
                // Legacy fallback: filter catalog types embedded in resource_graph_data
                filterCatalogFromModel(model)
            }

            // Check if model is empty after filtering
            if (model.isEmpty) {
                logger.warn("Model is empty after processing for resource {}, skipping", resource.id)
                return null
            }

            // Convert to RDF/XML
            val writer = StringWriter()
            RDFDataMgr.write(writer, model, Lang.RDFXML)
            return writer.toString()
        } catch (e: Exception) {
            logger.warn("Failed to process resource model to RDF/XML: {}", e.message)
            return null
        }
    }

    /**
     * Parses a format string to Jena Lang enum.
     */
    private fun parseLang(format: String): Lang =
        when (format.uppercase()) {
            "TURTLE" -> Lang.TURTLE
            "JSON-LD", "JSONLD" -> Lang.JSONLD
            "RDF/XML", "RDFXML" -> Lang.RDFXML
            "N-TRIPLES", "NTRIPLES" -> Lang.NTRIPLES
            "N-QUADS", "NQUADS" -> Lang.NQUADS
            else -> Lang.TURTLE
        }

    /**
     * Extracts DataService URIs from a dataset JSON payload.
     *
     * Follows the path: distribution[*].accessService[*].uri
     * Adds URIs to the provided set (thread-safe).
     *
     * @param datasetJson The dataset JSON map
     * @param uriSet Thread-safe set to add URIs to
     */
    private fun extractDataServiceUris(
        datasetJson: Map<String, Any>,
        uriSet: MutableSet<String>,
    ) {
        try {
            // Extract distributions from the dataset
            val distributions =
                when (val distValue = datasetJson["distribution"]) {
                    is List<*> -> {
                        distValue.filterIsInstance<Map<String, Any>>()
                    }

                    is Map<*, *> -> {
                        @Suppress("UNCHECKED_CAST")
                        listOf(distValue as Map<String, Any>)
                    }

                    else -> {
                        emptyList()
                    }
                }

            // Extract DataService URIs from distributions
            for (distribution in distributions) {
                val accessServices =
                    when (val accessServiceValue = distribution["accessService"]) {
                        is List<*> -> {
                            accessServiceValue.filterIsInstance<Map<String, Any>>()
                        }

                        is Map<*, *> -> {
                            @Suppress("UNCHECKED_CAST")
                            listOf(accessServiceValue as Map<String, Any>)
                        }

                        else -> {
                            emptyList()
                        }
                    }

                for (accessService in accessServices) {
                    when (val uriValue = accessService["uri"]) {
                        is String -> {
                            if (uriValue.isNotBlank()) {
                                uriSet.add(uriValue)
                            }
                        }

                        is List<*> -> {
                            uriValue
                                .filterIsInstance<String>()
                                .filter { it.isNotBlank() }
                                .forEach { uriSet.add(it) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger.warn("Failed to extract DataService URIs from dataset: {}", e.message)
            // Continue processing other resources
        }
    }
}
