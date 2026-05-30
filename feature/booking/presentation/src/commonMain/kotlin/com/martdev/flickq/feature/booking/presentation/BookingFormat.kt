package com.martdev.flickq.feature.booking.presentation

internal fun formatNaira(amount: Long): String =
    "₦" + amount.toString().reversed().chunked(3).joinToString(",").reversed()
