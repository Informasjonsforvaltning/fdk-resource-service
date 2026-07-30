package no.fdk.resourceservice.service

/**
 * Converts a nullable Boolean to its text representation for use as a PostgreSQL query parameter.
 * Shared by [UnionGraphSnapshotBuilder] and [UnionGraphBatchProcessor].
 */
internal fun Boolean?.toSqlBooleanText(): String? = this?.toString()
