package no.fdk.resourceservice.controller

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.service.UnionGraphService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Component

/**
 * Parses union-graph HTTP requests and maps domain orders to response DTOs.
 */
@Component
class UnionGraphRequestHandler {
    private val logger = LoggerFactory.getLogger(UnionGraphRequestHandler::class.java)

    data class ParsedCreateRequest(
        val resourceTypes: List<ResourceType>?,
        val updateTtlHours: Int,
        val webhookUrl: String?,
        val resourceFilters: UnionGraphResourceFilters?,
        val expandDistributionAccessServices: Boolean,
        val name: String,
        val description: String?,
        val resourceIds: List<String>?,
        val resourceUris: List<String>?,
        val includeCatalog: Boolean,
    )

    /**
     * Parses and validates a create request. Returns null if the request is invalid.
     */
    fun parseCreateRequest(requestBody: UnionGraphOrderRequest?): ParsedCreateRequest? {
        val resourceTypes =
            requestBody?.resourceTypes?.mapNotNull { typeName ->
                try {
                    ResourceType.valueOf(typeName.uppercase())
                } catch (e: IllegalArgumentException) {
                    logger.warn("Unknown resource type: {}", typeName)
                    null
                }
            }

        val updateTtlHours = requestBody?.updateTtlHours ?: 0
        val webhookUrl = requestBody?.webhookUrl
        val resourceFilters = requestBody?.toDomainFilters()
        val expandDistributionAccessServices = requestBody?.expandDistributionAccessServices ?: false
        val name = requestBody?.name
        val description = requestBody?.description
        val resourceIds = requestBody?.resourceIds?.ifEmpty { null }
        val resourceUris = requestBody?.resourceUris?.ifEmpty { null }
        val includeCatalog = requestBody?.includeCatalog ?: true

        if (name.isNullOrBlank()) {
            logger.warn("name is required for creating a union graph")
            return null
        }

        if (updateTtlHours != 0 && updateTtlHours < 24) {
            logger.warn("Invalid updateTtlHours: {} (must be 0 or >= 24)", updateTtlHours)
            return null
        }

        return ParsedCreateRequest(
            resourceTypes = resourceTypes,
            updateTtlHours = updateTtlHours,
            webhookUrl = webhookUrl,
            resourceFilters = resourceFilters,
            expandDistributionAccessServices = expandDistributionAccessServices,
            name = name,
            description = description,
            resourceIds = resourceIds,
            resourceUris = resourceUris,
            includeCatalog = includeCatalog,
        )
    }

    fun buildUpdateFields(
        rawBody: Map<String, Any?>?,
        request: UnionGraphOrderRequest,
    ): UnionGraphService.UpdateFields {
        fun hasField(fieldName: String): Boolean = rawBody?.containsKey(fieldName) == true

        fun <T> getIfPresent(
            field: String,
            value: T?,
        ): T? = if (hasField(field)) value else null

        val resourceTypes =
            getIfPresent("resourceTypes", request.resourceTypes)
                ?.mapNotNull { typeName ->
                    try {
                        ResourceType.valueOf(typeName.uppercase())
                    } catch (e: IllegalArgumentException) {
                        logger.warn("Invalid resource type: {}", typeName)
                        null
                    }
                }?.takeIf { it.isNotEmpty() }

        val resourceFilters = getIfPresent("resourceFilters", request.resourceFilters?.toDomain())
        val resourceIds = getIfPresent("resourceIds", request.resourceIds?.ifEmpty { null })
        val resourceUris = getIfPresent("resourceUris", request.resourceUris?.ifEmpty { null })

        val fieldNames =
            listOf(
                "updateTtlHours",
                "webhookUrl",
                "resourceTypes",
                "resourceFilters",
                "expandDistributionAccessServices",
                "name",
                "description",
                "resourceIds",
                "resourceUris",
                "includeCatalog",
            )
        val providedFields = fieldNames.filter { hasField(it) }.toSet()

        return UnionGraphService.UpdateFields(
            updateTtlHours = getIfPresent("updateTtlHours", request.updateTtlHours),
            webhookUrl = getIfPresent("webhookUrl", request.webhookUrl),
            resourceTypes = resourceTypes,
            resourceFilters = resourceFilters,
            expandDistributionAccessServices = getIfPresent("expandDistributionAccessServices", request.expandDistributionAccessServices),
            name = getIfPresent("name", request.name),
            description = getIfPresent("description", request.description),
            resourceIds = resourceIds,
            resourceUris = resourceUris,
            includeCatalog = getIfPresent("includeCatalog", request.includeCatalog),
            providedFields = providedFields,
        )
    }

    fun toOrderResponse(order: UnionGraphOrder): UnionGraphOrderResponse =
        UnionGraphOrderResponse(
            id = order.id,
            status = order.status.name,
            resourceTypes = order.resourceTypes,
            updateTtlHours = order.updateTtlHours,
            webhookUrl = order.webhookUrl,
            createdAt = order.createdAt.toString(),
            resourceFilters = toResponseFilters(order.resourceFilters),
            expandDistributionAccessServices = order.expandDistributionAccessServices,
            name = order.name,
            description = order.description,
            resourceIds = order.resourceIds,
            resourceUris = order.resourceUris,
        )

    fun toStatusResponse(order: UnionGraphOrder): UnionGraphOrderStatusResponse =
        UnionGraphOrderStatusResponse(
            id = order.id,
            status = order.status.name,
            resourceTypes = order.resourceTypes,
            updateTtlHours = order.updateTtlHours,
            webhookUrl = order.webhookUrl,
            errorMessage = order.errorMessage,
            createdAt = order.createdAt.toString(),
            updatedAt = order.updatedAt.toString(),
            processedAt = order.processedAt?.toString(),
            resourceFilters = toResponseFilters(order.resourceFilters),
            expandDistributionAccessServices = order.expandDistributionAccessServices,
            name = order.name,
            description = order.description,
            resourceIds = order.resourceIds,
            resourceUris = order.resourceUris,
        )

    fun toSummaryResponse(order: UnionGraphOrder): UnionGraphOrderSummaryResponse =
        UnionGraphOrderSummaryResponse(
            id = order.id,
            status = order.status.name,
            resourceTypes = order.resourceTypes,
            updateTtlHours = order.updateTtlHours,
            webhookUrl = order.webhookUrl,
            errorMessage = order.errorMessage,
            createdAt = order.createdAt.toString(),
            updatedAt = order.updatedAt.toString(),
            processedAt = order.processedAt?.toString(),
            resourceFilters = toResponseFilters(order.resourceFilters),
            expandDistributionAccessServices = order.expandDistributionAccessServices,
            name = order.name,
            description = order.description,
            resourceIds = order.resourceIds,
            resourceUris = order.resourceUris,
        )

    fun toMinimalInfoResponse(
        order: UnionGraphOrder,
        count: Long,
    ): UnionGraphMinimalInfoResponse =
        UnionGraphMinimalInfoResponse(
            id = order.id,
            name = order.name,
            description = order.description,
            resourceTypes = order.resourceTypes,
            createdAt = order.createdAt.toString(),
            updatedAt = order.updatedAt.toString(),
            count = count,
        )

    fun createdOrConflictResponse(
        order: UnionGraphOrder,
        isNew: Boolean,
    ): ResponseEntity<UnionGraphOrderResponse> {
        val httpStatus = if (isNew) HttpStatus.CREATED else HttpStatus.CONFLICT
        return ResponseEntity
            .status(httpStatus)
            .contentType(MediaType.APPLICATION_JSON)
            .header("Location", "/v1/union-graphs/${order.id}")
            .body(toOrderResponse(order))
    }

    fun toResponseFilters(filters: UnionGraphResourceFilters?): ResourceFiltersResponse? {
        val normalized = filters?.normalized() ?: return null
        val dataset = normalized.dataset ?: return null

        return ResourceFiltersResponse(
            dataset =
                DatasetFiltersResponse(
                    isOpenData = dataset.isOpenData,
                    isRelatedToTransportportal = dataset.isRelatedToTransportportal,
                    isDatasetSeries = dataset.isDatasetSeries,
                ),
        )
    }
}
