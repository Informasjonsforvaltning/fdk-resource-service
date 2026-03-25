package no.fdk.resourceservice.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Primary
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
    @param:Value("\${app.union-graphs.resource-batch-size:250}")
    val resourceBatchSize: Int,
    @param:Value("\${app.union-graphs.batch-delay-ms:0}")
    val batchDelayMs: Long,
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
     * - Thread priority: LOW (to avoid interfering with API requests)
     *
     * Marked as @Primary to be used as the default TaskExecutor for @Async methods
     * when no specific executor is specified.
     */
    @Bean(name = ["unionGraphTaskExecutor", "taskExecutor"])
    @Primary
    fun unionGraphTaskExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = UNION_GRAPH_CORE_POOL_SIZE
        executor.maxPoolSize = UNION_GRAPH_MAX_POOL_SIZE
        executor.queueCapacity = UNION_GRAPH_QUEUE_CAPACITY
        executor.setThreadNamePrefix("union-graph-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)
        executor.initialize()

        // Set thread priority to lower priority for background tasks
        // This helps prevent union graph building from interfering with API requests
        // Note: Thread priority is set after initialization via custom task decorator
        executor.setTaskDecorator { runnable ->
            Runnable {
                val originalPriority = Thread.currentThread().priority
                try {
                    // Set minimum priority for union graph processing threads
                    // This ensures HTTP threads (NORM_PRIORITY) always get CPU time for liveness probes
                    Thread.currentThread().priority = Thread.MIN_PRIORITY
                    runnable.run()
                } finally {
                    Thread.currentThread().priority = originalPriority
                }
            }
        }
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
