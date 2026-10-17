package com.scenio.mth.inference

import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GenerationAwaitTest {
    @Test fun cancellationWaitsForNativeLikeWorkBeforeReturningToFileCleanup() = runTest {
        val release=CompletableDeferred<Unit>();var finished=false;var nativeCancelled=false;var cleaned=false
        val task=async { withContext(NonCancellable) { release.await();finished=true } }
        val waiter=launch {
            try { task.awaitGeneration { nativeCancelled=true } }
            finally { assertTrue(finished,"File cleanup must follow native task completion");cleaned=true }
        }
        runCurrent();waiter.cancel();runCurrent()
        assertTrue(nativeCancelled);assertFalse(cleaned);assertFalse(waiter.isCompleted)
        release.complete(Unit);waiter.join();assertTrue(cleaned)
    }
}
