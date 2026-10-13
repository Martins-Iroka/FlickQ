package com.martdev.flickq.shared.domain.job

/** Jobs that must run on ONE instance at a time. [id] is part of the lock key: never renumber an entry. */
enum class SingletonJob(val id: Int) {
    REFRESH_TOKEN_CLEANUP(1),
    RESERVATION_EXPIRY(2),
    PAYMENT_RECONCILE(3),
    NOTIFICATION_HOUSEKEEPING(4);

    /** 'FLKQ' in the high 32 bits keeps our keys clear of any other app's advisory locks on the same database. */
    val lockKey: Long get() = (NAMESPACE shl 32) or id.toLong()

    private companion object {
        const val NAMESPACE = 0x464C4B51L
    }
}

interface JobLock {
    /** Runs [block] only if no other instance holds [key]. Returns null, without running it, otherwise. */
    suspend fun <T> runExclusive(key: Long, block: suspend () -> T): T?
}