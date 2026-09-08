package com.martdev.flickq.core.designsystem

fun groupDigits(amount: Long): String =
    amount.toString().reversed().chunked(3).joinToString(",").reversed()

fun formatNaira(amount: Long): String = "₦" + groupDigits(
    amount
)