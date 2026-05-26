package com.martdev.flickq.features.auth.infrastructure.observability

import com.martdev.flickq.features.auth.domain.observability.AuthMetrics
import org.koin.core.annotation.Single
import org.slf4j.LoggerFactory

@Single
class LoggingAuthMetrics : AuthMetrics {
    private val logger = LoggerFactory.getLogger("auth.metrics")

    override fun count(name: String, vararg tags: Pair<String, String>) {
        if (tags.isEmpty()) {
            logger.info("metric={}", name)
        } else {
            logger.info(
                "metric={} {}",
                name,
                tags.joinToString(" ") { "${it.first}=${it.second}" }
            )
        }
    }
}
