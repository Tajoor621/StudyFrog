package com.scenio.mth.inference

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.withContext

/** Native work may finish after coroutine cancellation. Join it before file cleanup. */
internal suspend fun <T> Deferred<T>.awaitGeneration(cancelNative: () -> Unit): T = try {
    await()
} catch (cancelled: CancellationException) {
    try { cancelNative() }
    finally { withContext(NonCancellable) { cancelAndJoin() } }
    throw cancelled
}
