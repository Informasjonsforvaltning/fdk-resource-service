package no.fdk.resourceservice.model

import jakarta.validation.constraints.Size

/**
 * Request model for retrieving multiple resources by their unique identifiers.
 *
 * This class is used in POST endpoints to support bulk resource retrieval operations,
 * allowing clients to fetch multiple resources in a single request.
 *
 * @property ids List of unique resource identifiers to retrieve (max 100 items)
 */
data class FindByIdsRequest(
    /**
     * List of unique identifiers for the resources to retrieve.
     * Each ID should correspond to an existing resource in the system.
     *
     * Constraints:
     * - Maximum 100 IDs per request
     */
    @field:Size(max = 100, message = "Maximum 100 IDs allowed per request")
    val ids: List<String>,
)
