package com.martdev.flickq.report.model

import kotlin.time.Instant

enum class ReportBucketGranularity { DAY, WEEK, MONTH }

data class RevenueBucket(
    val bucketStart: Instant,
    val gross: Long,
    val refunds: Long,
    val net: Long,
    val ticketsSold: Long,
)

data class RevenueReport(
    val from: Instant,
    val to: Instant,
    val bucket: ReportBucketGranularity,
    val currency: String,
    val buckets: List<RevenueBucket>,
    val totalGross: Long,
    val totalRefunds: Long,
    val totalNet: Long,
    val totalTicketsSold: Long,
)
