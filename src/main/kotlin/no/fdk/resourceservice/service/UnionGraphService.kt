package no.fdk.resourceservice.service

import no.fdk.resourceservice.model.ResourceType
import no.fdk.resourceservice.model.UnionGraphOrder
import no.fdk.resourceservice.model.UnionGraphResourceFilters
import no.fdk.resourceservice.repository.UnionGraphOrderRepository
import org.springframework.stereotype.Service

/**
 * Facade for managing union graphs and building union graphs.
 *
 * Union graphs are built from multiple resource graphs by combining them.
 * This service delegates to three collaborators, each owning a slice of the behavior
 * previously implemented directly on this class:
 * - [UnionGraphOrderService] handles CRUD/lifecycle operations for union graph orders
 *   (create, get, update, reset, delete).
 * - [UnionGraphSnapshotBuilder] builds union graph snapshots from resource graphs
 *   (RDF parsing, filtering, and conversion).
 * - [UnionGraphBatchProcessor] drives order processing and incremental batch processing.
 *
 * The public API here is kept stable so existing callers (controllers, the background
 * processor, and tests) can keep depending on [UnionGraphService] directly.
 */
@Service
class UnionGraphService(
    private val orderService: UnionGraphOrderService,
    private val snapshotBuilder: UnionGraphSnapshotBuilder,
    private val batchProcessor: UnionGraphBatchProcessor,
    private val metricsService: UnionGraphMetricsService,
    private val unionGraphOrderRepository: UnionGraphOrderRepository,
) {
    init {
        // Initialize metrics service with repository for gauge callbacks
        metricsService.initialize(unionGraphOrderRepository)
    }

    /**
     * Result of creating a union graph, indicating whether it's new or existing.
     */
    data class CreateOrderResult(val order: UnionGraphOrder, val isNew: Boolean)

    /**
     * Data class to track which fields should be updated.
     * This allows distinguishing between "not provided" (keep existing) and "explicitly null" (set to null).
     */
    data class UpdateFields(
        val updateTtlHours: Int? = null,
        val webhookUrl: String? = null,
        val resourceTypes: List<ResourceType>? = null,
        val resourceFilters: UnionGraphResourceFilters? = null,
        val expandDistributionAccessServices: Boolean? = null,
        val name: String? = null,
        val description: String? = null,
        val resourceIds: List<String>? = null,
        val resourceUris: List<String>? = null,
        val includeCatalog: Boolean? = null,
        val providedFields: Set<String> = emptySet(),
    ) {
        fun has(field: String) = providedFields.contains(field)
    }

    /**
     * Creates a new union graph, or returns an existing one if one already exists.
     *
     * @see UnionGraphOrderService.createOrder
     */
    fun createOrder(
        resourceTypes: List<ResourceType>? = null,
        updateTtlHours: Int = 0,
        webhookUrl: String? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean = false,
        name: String,
        description: String? = null,
        resourceIds: List<String>? = null,
        resourceUris: List<String>? = null,
        includeCatalog: Boolean = true,
    ): CreateOrderResult = orderService.createOrder(
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

    /**
     * Resets a union graph to PENDING status for retry.
     *
     * @see UnionGraphOrderService.resetOrderToPending
     */
    fun resetOrderToPending(id: String): UnionGraphOrder? = orderService.resetOrderToPending(id)

    /**
     * Gets a union graph by ID.
     *
     * @see UnionGraphOrderService.getOrder
     */
    fun getOrder(id: String): UnionGraphOrder? = orderService.getOrder(id)

    /**
     * Gets all union graphs without the graph data.
     *
     * @see UnionGraphOrderService.getAllOrders
     */
    fun getAllOrders(): List<UnionGraphOrder> = orderService.getAllOrders()

    /**
     * Gets all union graphs that have graph data available.
     *
     * @see UnionGraphOrderService.getAvailableOrders
     */
    fun getAvailableOrders(): List<UnionGraphOrder> = orderService.getAvailableOrders()

    /**
     * Gets the count of resources in a union graph from snapshots.
     *
     * @see UnionGraphOrderService.getResourceCount
     */
    fun getResourceCount(orderId: String): Long = orderService.getResourceCount(orderId)

    /**
     * Updates an existing union graph order.
     *
     * @see UnionGraphOrderService.updateOrder
     */
    fun updateOrder(
        id: String,
        updateTtlHours: Int? = null,
        webhookUrl: String? = null,
        resourceTypes: List<ResourceType>? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean? = null,
        name: String? = null,
        description: String? = null,
        includeCatalog: Boolean? = null,
    ): UnionGraphOrder? = orderService.updateOrder(
        id = id,
        updateTtlHours = updateTtlHours,
        webhookUrl = webhookUrl,
        resourceTypes = resourceTypes,
        resourceFilters = resourceFilters,
        expandDistributionAccessServices = expandDistributionAccessServices,
        name = name,
        description = description,
        includeCatalog = includeCatalog,
    )

    fun updateOrder(id: String, fields: UpdateFields): UnionGraphOrder? = orderService.updateOrder(id, fields)

    /**
     * Deletes a union graph.
     *
     * @see UnionGraphOrderService.deleteOrder
     */
    fun deleteOrder(id: String): Boolean = orderService.deleteOrder(id)

    /**
     * Builds a union graph by saving snapshots of resource graphs.
     *
     * @see UnionGraphSnapshotBuilder.buildUnionGraph
     */
    fun buildUnionGraph(
        resourceTypes: List<ResourceType>? = null,
        resourceFilters: UnionGraphResourceFilters? = null,
        expandDistributionAccessServices: Boolean = false,
        rdfFormat: RdfService.RdfFormat = RdfService.RdfFormat.TURTLE,
        orderId: String? = null, // Required for saving snapshots
        includeCatalog: Boolean = true,
    ): Boolean =
        snapshotBuilder.buildUnionGraph(
            resourceTypes = resourceTypes,
            resourceFilters = resourceFilters,
            expandDistributionAccessServices = expandDistributionAccessServices,
            rdfFormat = rdfFormat,
            orderId = orderId,
            includeCatalog = includeCatalog,
        )

    /**
     * Locks an order for processing in a new transaction to ensure the status update commits immediately.
     *
     * @see UnionGraphOrderService.lockOrderInNewTransaction
     */
    fun lockOrderInNewTransaction(orderId: String, instanceId: String): Boolean =
        orderService.lockOrderInNewTransaction(orderId, instanceId)

    /**
     * Fetches an order in a new transaction to ensure we see the committed state.
     *
     * @see UnionGraphOrderService.getOrderInNewTransaction
     */
    fun getOrderInNewTransaction(orderId: String): UnionGraphOrder? = orderService.getOrderInNewTransaction(orderId)

    /**
     * Processes a union graph by building the union graph and updating it.
     *
     * @see UnionGraphBatchProcessor.processOrder
     */
    fun processOrder(order: UnionGraphOrder, instanceId: String) = batchProcessor.processOrder(order, instanceId)

    /**
     * Processes the next batch of resources for a union graph order.
     *
     * @see UnionGraphBatchProcessor.processNextBatch
     */
    fun processNextBatch(orderId: String): Boolean = batchProcessor.processNextBatch(orderId)

    /**
     * Initializes the processing state for a union graph order.
     *
     * @see UnionGraphBatchProcessor.initializeProcessingState
     */
    fun initializeProcessingState(orderId: String): Boolean = batchProcessor.initializeProcessingState(orderId)
}
