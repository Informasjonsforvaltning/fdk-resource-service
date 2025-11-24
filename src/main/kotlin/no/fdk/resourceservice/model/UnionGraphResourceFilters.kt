package no.fdk.resourceservice.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * Type-specific filters that can be applied when building union graphs.
 *
 * Each resource type can define its own optional filter structure.
 * The payload is stored as JSON in the database so we can evolve it
 * without requiring additional schema changes.
 *
 * Filters are applied when collecting resources for the union graph.
 * Only resources matching the specified filter criteria will be included.
 *
 * Example usage:
 * ```
 * UnionGraphResourceFilters(
 *     dataset = DatasetFilters(
 *         isOpenData = true,
 *         isRelatedToTransportportal = false
 *     )
 * )
 * ```
 * This would include only datasets that are open data and not related to transport portal.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class UnionGraphResourceFilters(
    /**
     * Filters for DATASET resource type.
     * Only datasets matching these criteria will be included in the union graph.
     */
    val dataset: DatasetFilters? = null,
) {
    /**
     * Filters for dataset resources based on metadata fields.
     *
     * @param isOpenData Filter by the isOpenData field. If true, only open data datasets are included.
     *                   If false, only non-open data datasets are included. If null, this filter is not applied.
     * @param isRelatedToTransportportal Filter by the isRelatedToTransportportal field.
     *                                   If true, only datasets related to transport portal are included.
     *                                   If false, only datasets not related to transport portal are included.
     *                                   If null, this filter is not applied.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class DatasetFilters(
        val isOpenData: Boolean? = null,
        val isRelatedToTransportportal: Boolean? = null,
    ) {
        /**
         * Checks if all filter fields are null (empty filter).
         */
        fun isEmpty(): Boolean = isOpenData == null && isRelatedToTransportportal == null
    }

    /**
     * Checks if all filter types are null (empty filter).
     */
    fun isEmpty(): Boolean = dataset == null

    /**
     * Normalizes the filters by removing empty filter types.
     * Returns null if all filters are empty.
     *
     * @return Normalized filters with empty filter types removed, or null if all filters are empty.
     */
    fun normalized(): UnionGraphResourceFilters? {
        val normalizedDataset = dataset?.takeUnless { it.isEmpty() }
        return if (normalizedDataset == null) {
            null
        } else {
            copy(dataset = normalizedDataset)
        }
    }
}
