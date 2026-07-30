package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.UnionGraphResourceFilters

/**
 * Request DTO for creating a union graph.
 */
data class UnionGraphOrderRequest(
    /**
     * List of resource types to include in the union graph.
     * If null or empty, all resource types will be included.
     * Valid values: CONCEPT, DATASET, DATA_SERVICE, INFORMATION_MODEL, SERVICE, EVENT
     */
    @param:io.swagger.v3.oas.annotations.media.Schema(
        description =
            "List of resource types to include in the union graph. " +
                "Valid values: CONCEPT, DATASET, DATA_SERVICE, INFORMATION_MODEL, SERVICE, EVENT",
        example = "[\"DATASET\", \"DATA_SERVICE\"]",
    )
    val resourceTypes: List<String>? = null,
    /**
     * Time to live in hours for automatic graph updates.
     * 0 means never update automatically.
     * Otherwise, the graph will be automatically updated after this many hours.
     * Must be 0 or greater than 3.
     */
    val updateTtlHours: Int? = null,
    /**
     * Webhook URL to call when the union graph status changes.
     * The webhook will be called with a POST request containing the union graph status.
     * Must use HTTPS protocol.
     */
    val webhookUrl: String? = null,
    /**
     * Optional per-resource-type filters to apply when building the union graph.
     * Filters allow you to include only resources that match specific criteria.
     * For example, dataset filters can filter by isOpenData, isRelatedToTransportportal, and isDatasetSeries.
     * Filters are part of the union graph configuration, so union graphs with different filters are considered different.
     */
    val resourceFilters: ResourceFiltersRequest? = null,
    /**
     * If true, when building union graphs, datasets with distributions that reference
     * DataService URIs (via distribution[].accessService[].uri) will have those
     * DataService graphs automatically included in the union graph.
     *
     * This allows creating union graphs that include both datasets and their related
     * data services in a single graph, making it easier to query and navigate the
     * relationships between datasets and data services.
     *
     * Default: false
     */
    val expandDistributionAccessServices: Boolean? = null,
    /**
     * Human-readable name for the union graph (required for creation, optional for updates).
     */
    val name: String? = null,
    /**
     * Optional human-readable description of the union graph.
     */
    val description: String? = null,
    /**
     * Optional list of resource IDs (fdkId) to filter by.
     * If provided, only resources with matching IDs will be included in the union graph.
     */
    @param:io.swagger.v3.oas.annotations.media.Schema(
        description =
            "Optional list of resource IDs (fdkId) to filter by. " +
                "If provided, only resources with matching IDs will be included in the union graph.",
        example = "[\"resource-id-1\", \"resource-id-2\"]",
    )
    val resourceIds: List<String>? = null,
    /**
     * Optional list of resource URIs to filter by.
     * If provided, only resources with matching URIs will be included in the union graph.
     */
    @param:io.swagger.v3.oas.annotations.media.Schema(
        description =
            "Optional list of resource URIs to filter by. " +
                "If provided, only resources with matching URIs will be included in the union graph.",
        example = "[\"https://example.com/resource1\", \"https://example.com/resource2\"]",
    )
    val resourceUris: List<String>? = null,
    /**
     * If true (default), Catalog and CatalogRecord resources are included in union graph snapshots.
     * If false, Catalog and CatalogRecord resources are removed from snapshots (as subjects),
     * but references to their URIs (as objects) are preserved.
     *
     * Default: true
     */
    @param:io.swagger.v3.oas.annotations.media.Schema(
        description =
            "If true (default), Catalog and CatalogRecord resources are included in union graph snapshots. " +
                "If false, Catalog and CatalogRecord resources are removed from snapshots (as subjects), " +
                "but references to their URIs (as objects) are preserved.",
        example = "true",
    )
    val includeCatalog: Boolean? = null,
) {
    fun toDomainFilters(): UnionGraphResourceFilters? = resourceFilters?.toDomain()
}

/**
 * Request DTO for resource type-specific filters.
 * Each resource type can define its own filter structure.
 */
data class ResourceFiltersRequest(
    /**
     * Filters for DATASET resource type.
     * Only datasets matching these criteria will be included in the union graph.
     */
    val dataset: DatasetFiltersRequest? = null,
) {
    fun toDomain(): UnionGraphResourceFilters? {
        val datasetFilters = dataset?.toDomain()
        return if (datasetFilters == null) {
            null
        } else {
            UnionGraphResourceFilters(dataset = datasetFilters).normalized()
        }
    }
}

/**
 * Request DTO for dataset-specific filters.
 * Filters datasets based on their metadata fields.
 */
data class DatasetFiltersRequest(
    /**
     * Filter datasets by the isOpenData field.
     * If true, only open data datasets are included.
     * If false, only non-open data datasets are included.
     * If null, this filter is not applied.
     */
    val isOpenData: Boolean? = null,
    /**
     * Filter datasets by the isRelatedToTransportportal field.
     * If true, only datasets related to transport portal are included.
     * If false, only datasets not related to transport portal are included.
     * If null, this filter is not applied.
     */
    val isRelatedToTransportportal: Boolean? = null,
    /**
     * Filter datasets by whether they are DatasetSeries (have rdf:type = dcat:DatasetSeries).
     * If true, only datasets that ARE DatasetSeries are included.
     * If false, only datasets that are NOT DatasetSeries are included.
     * If null, this filter is not applied (both series and non-series are included).
     */
    @param:io.swagger.v3.oas.annotations.media.Schema(
        description =
            "Filter datasets by whether they are DatasetSeries (have rdf:type = dcat:DatasetSeries). " +
                "If true, only datasets that ARE DatasetSeries are included. " +
                "If false, only datasets that are NOT DatasetSeries are included. " +
                "If null, this filter is not applied (both series and non-series are included).",
        example = "true",
    )
    val isDatasetSeries: Boolean? = null,
) {
    fun toDomain(): UnionGraphResourceFilters.DatasetFilters? =
        if (isOpenData == null && isRelatedToTransportportal == null && isDatasetSeries == null) {
            null
        } else {
            UnionGraphResourceFilters.DatasetFilters(isOpenData, isRelatedToTransportportal, isDatasetSeries)
        }
}

/**
 * Response DTO for a union graph.
 */
data class UnionGraphOrderResponse(
    val id: String,
    val status: String,
    val resourceTypes: List<String>?,
    val updateTtlHours: Int,
    val webhookUrl: String?,
    val createdAt: String,
    /**
     * The resource filters that were applied when creating this union graph.
     * Null if no filters were specified.
     */
    val resourceFilters: ResourceFiltersResponse?,
    /**
     * Whether DataService graphs are automatically included when datasets reference them via distribution accessService.
     */
    val expandDistributionAccessServices: Boolean,
    /**
     * Human-readable name for the union graph.
     */
    val name: String,
    /**
     * Optional human-readable description of the union graph.
     */
    val description: String?,
    /**
     * Optional list of resource IDs (fdkId) that were used to filter resources.
     */
    val resourceIds: List<String>?,
    /**
     * Optional list of resource URIs that were used to filter resources.
     */
    val resourceUris: List<String>?,
)

/**
 * Response DTO for union graph status.
 */
data class UnionGraphOrderStatusResponse(
    val id: String,
    val status: String,
    val resourceTypes: List<String>?,
    val updateTtlHours: Int,
    val webhookUrl: String?,
    val errorMessage: String?,
    val createdAt: String,
    val updatedAt: String,
    val processedAt: String?,
    /**
     * The resource filters that were applied when creating this union graph.
     * Null if no filters were specified.
     */
    val resourceFilters: ResourceFiltersResponse?,
    /**
     * Whether DataService graphs are automatically included when datasets reference them via distribution accessService.
     */
    val expandDistributionAccessServices: Boolean,
    /**
     * Human-readable name for the union graph.
     */
    val name: String,
    /**
     * Optional human-readable description of the union graph.
     */
    val description: String?,
    /**
     * Optional list of resource IDs (fdkId) that were used to filter resources.
     */
    val resourceIds: List<String>?,
    /**
     * Optional list of resource URIs that were used to filter resources.
     */
    val resourceUris: List<String>?,
)

/**
 * Response DTO for union graph summary (without graph data).
 * Used for listing all union graphs without loading the potentially large graph data.
 */
data class UnionGraphOrderSummaryResponse(
    val id: String,
    val status: String,
    val resourceTypes: List<String>?,
    val updateTtlHours: Int,
    val webhookUrl: String?,
    val errorMessage: String?,
    val createdAt: String,
    val updatedAt: String,
    val processedAt: String?,
    /**
     * The resource filters that were applied when creating this union graph.
     * Null if no filters were specified.
     */
    val resourceFilters: ResourceFiltersResponse?,
    /**
     * Whether DataService graphs are automatically included when datasets reference them via distribution accessService.
     */
    val expandDistributionAccessServices: Boolean,
    /**
     * Human-readable name for the union graph.
     */
    val name: String,
    /**
     * Optional human-readable description of the union graph.
     */
    val description: String?,
    /**
     * Optional list of resource IDs (fdkId) that were used to filter resources.
     */
    val resourceIds: List<String>?,
    /**
     * Optional list of resource URIs that were used to filter resources.
     */
    val resourceUris: List<String>?,
)

/**
 * Response DTO for minimal union graph information.
 * Used for publicly accessible endpoints that provide basic information
 * about available union graphs without requiring authentication.
 */
data class UnionGraphMinimalInfoResponse(
    /**
     * The union graph ID.
     */
    val id: String,
    /**
     * Human-readable name for the union graph.
     */
    val name: String,
    /**
     * Optional human-readable description of the union graph.
     */
    val description: String?,
    /**
     * List of resource types included in the union graph.
     * Null if all resource types are included.
     */
    val resourceTypes: List<String>?,
    /**
     * When the union graph was created.
     */
    val createdAt: String,
    /**
     * When the union graph was last updated.
     */
    val updatedAt: String,
    /**
     * The number of resources in the union graph.
     */
    val count: Long,
)

/**
 * Response DTO for resource type-specific filters.
 */
data class ResourceFiltersResponse(
    /**
     * Dataset filters that were applied.
     * Present only if dataset filters were specified when creating the union graph.
     */
    val dataset: DatasetFiltersResponse?,
)

/**
 * Response DTO for dataset-specific filters.
 */
data class DatasetFiltersResponse(
    /**
     * The isOpenData filter value that was applied.
     * Null if this filter was not specified.
     */
    val isOpenData: Boolean?,
    /**
     * The isRelatedToTransportportal filter value that was applied.
     * Null if this filter was not specified.
     */
    val isRelatedToTransportportal: Boolean?,
    /**
     * The isDatasetSeries filter value that was applied.
     * Null if this filter was not specified.
     */
    val isDatasetSeries: Boolean?,
)
