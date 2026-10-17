package com.scenio.mth.features

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import app.cash.turbine.test
import com.scenio.mth.features.data.SettingsStore
import com.scenio.mth.features.data.ThemeMode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsStoreTest {
    @TempDir lateinit var dir: Path
    @Test fun `consent defaults off and changes preserve other settings`() = runTest {
        val store = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope,
            produceFile = { dir.resolve("settings.preferences_pb").toFile() }))
        store.data.test {
            val defaults = awaitItem()
            assertFalse(defaults.analytics); assertFalse(defaults.crashes); assertFalse(defaults.reminders)
            store.setTheme(ThemeMode.DARK)
            assertEquals(ThemeMode.DARK, awaitItem().theme)
            store.setReminders(true)
            val changed = awaitItem()
            assertEquals(ThemeMode.DARK, changed.theme)
            assertTrue(changed.reminders); assertFalse(changed.analytics)
            cancelAndIgnoreRemainingEvents()
        }
    }
    @Test fun `onboarding survives store reopening`() = runTest {
        val file = dir.resolve("onboarding.preferences_pb").toFile()
        val owner = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val first = SettingsStore(PreferenceDataStoreFactory.create(scope = owner, produceFile = { file }))
        first.finishOnboarding()
        owner.coroutineContext[Job]!!.cancelAndJoin()
        val reopened = SettingsStore(PreferenceDataStoreFactory.create(scope = backgroundScope, produceFile = { file }))
        assertTrue(reopened.data.first().onboarded)
    }
}
