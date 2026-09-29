package com.martdev.flickq.features.notification.infrastructure.fcm

import com.google.api.core.ApiFuture
import com.google.api.core.ApiFutureCallback
import com.google.api.core.ApiFutures
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> ApiFuture<T>.await(): T = suspendCancellableCoroutine { cont ->
    ApiFutures.addCallback(this, object : ApiFutureCallback<T> {
        override fun onFailure(t: Throwable) {
            cont.resumeWithException(t)
        }

        override fun onSuccess(result: T) {
            cont.resume(result)
        }
    }, MoreExecutors.directExecutor())
    cont.invokeOnCancellation {
        cancel(true)
    }
}