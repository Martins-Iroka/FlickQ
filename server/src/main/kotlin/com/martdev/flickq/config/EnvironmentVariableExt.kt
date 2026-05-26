package com.martdev.flickq.config

import io.ktor.server.application.*

fun ApplicationEnvironment.getEnvValue(key: String) = config.property(key).getString()