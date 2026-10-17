package com.scenio.mth.features

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.scenio.mth.features.data.ProgressDatabase
import com.scenio.mth.features.data.ProgressRepository
import com.scenio.mth.features.data.SettingsStore
import com.scenio.mth.features.reminders.ReminderScheduler
import com.scenio.mth.features.telemetry.Telemetry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class FeatureGraph internal constructor(context: Context) {
    private val app = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val settings = SettingsStore(PreferenceDataStoreFactory.create(
        scope = scope,
        produceFile = { app.preferencesDataStoreFile("studyforge_feature_settings") }
    ))
    private val database = Room.databaseBuilder(
        app, ProgressDatabase::class.java, "studyforge_feature_progress.db"
    ).build()
    val progress = ProgressRepository(database)
    val telemetry = Telemetry(app)
    private val scheduler = ReminderScheduler(app)
    private val started = java.util.concurrent.atomic.AtomicBoolean(false)
    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            try {
                settings.data.map { it.reminders }.distinctUntilChanged().collect {
                    scheduler.setEnabled(it)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.e("StudyForge", "Reminder settings unavailable") }
        }
        scope.launch {
            try {
                settings.data.map { it.analytics to it.crashes }.distinctUntilChanged().collect {
                    telemetry.applyConsent(it.first, it.second)
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { Log.e("StudyForge", "Telemetry settings unavailable") }
        }
    }
}

object FeatureRuntime {
    @Volatile private var instance: FeatureGraph? = null
    fun graph(context: Context): FeatureGraph = instance ?: synchronized(this) {
        instance ?: FeatureGraph(context).also { instance = it }
    }
    fun start(application: Application) { graph(application).start() }
}

// Use this only if the existing application has no custom Application class.
class StudyForgeApplication : Application() {
    override fun onCreate() { super.onCreate(); FeatureRuntime.start(this) }
}
