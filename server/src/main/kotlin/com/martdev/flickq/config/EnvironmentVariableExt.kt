package com.martdev.flickq.config

import io.ktor.server.application.ApplicationEnvironment

fun ApplicationEnvironment.getEnvValue(key: String) = config.propertyOrNull(key)?.getString().orEmpty()