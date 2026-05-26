package com.martdev.flickq.shared.api

import kotlinx.serialization.Serializable

@Serializable
data class DataResponse<T>(
    val data: T
)
