package no.fdk.resourceservice.model

/**
 * Request model for retrieving multiple resources by their unique identifiers.
 *
 * This class is used in POST endpoints to support bulk resource retrieval operations,
 * allowing clients to fetch multiple resources in a single request.
 *
 * @property ids List of unique resource identifiers to retrieve
 */
data class FindByIdsRequest(
    /**
     * List of unique identifiers for the resources to retrieve.
     * Each ID should correspond to an existing resource in the system.
     */
    val ids: List<String>,
)
