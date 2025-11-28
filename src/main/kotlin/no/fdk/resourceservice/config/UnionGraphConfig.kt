package no.fdk.resourceservice.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.SchedulingConfigurer
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler
import org.springframework.scheduling.config.ScheduledTaskRegistrar
import org.springframework.web.client.RestTemplate
import java.util.concurrent.Executor

/**
 * Configuration for union graph operations.
 *
 * This enables async processing for union graph building tasks
 * with a controlled thread pool to prevent resource exhaustion.
 */
@Configuration
@EnableAsync
@EnableScheduling
class UnionGraphConfig(
    @Value("\${app.union-graphs.resource-batch-size:25}")
    val resourceBatchSize: Int,
) : SchedulingConfigurer {
    companion object {
        /**
         * Maximum number of concurrent union graph building operations.
         * This limits memory usage for memory-intensive graph operations.
         */
        const val UNION_GRAPH_MAX_POOL_SIZE = 2

        /**
         * Core pool size - minimum threads always available.
         */
        const val UNION_GRAPH_CORE_POOL_SIZE = 1

        /**
         * Queue capacity for pending tasks.
         */
        const val UNION_GRAPH_QUEUE_CAPACITY = 5

        /**
         * Progress update interval - update progress metrics every N resources.
         * This reduces the overhead of frequent metric updates during processing.
         */
        const val UNION_GRAPH_PROGRESS_UPDATE_INTERVAL = 25
    }

    /**
     * Task executor for processing union graph orders.
     * Uses a bounded thread pool to prevent resource exhaustion.
     *
     * Conservative settings for memory-intensive graph operations:
     * - Core pool: 1 thread (always available)
     * - Max pool: 2 threads (limits concurrent graph building to prevent OOM)
     * - Queue: 5 tasks (small queue to prevent memory buildup)
     */
    @Bean(name = ["unionGraphTaskExecutor"])
    fun unionGraphTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = UNION_GRAPH_CORE_POOL_SIZE
        executor.maxPoolSize = UNION_GRAPH_MAX_POOL_SIZE
        executor.queueCapacity = UNION_GRAPH_QUEUE_CAPACITY
        executor.setThreadNamePrefix("union-graph-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)
        executor.initialize()
        return executor
    }

    override fun configureTasks(taskRegistrar: ScheduledTaskRegistrar) {
        // Create scheduler directly here to avoid circular dependency
        // This ensures scheduled methods run on separate threads and don't block each other
        val scheduler = ThreadPoolTaskScheduler()
        scheduler.poolSize = 3 // Enough threads for all scheduled methods
        scheduler.setThreadNamePrefix("scheduler-")
        scheduler.setWaitForTasksToCompleteOnShutdown(true)
        scheduler.setAwaitTerminationSeconds(60)
        scheduler.initialize()
        taskRegistrar.setScheduler(scheduler)
    }

    @Bean
    fun restTemplate(): RestTemplate = RestTemplate()
}
