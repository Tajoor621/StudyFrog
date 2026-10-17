package com.scenio.mth.features.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.scenio.mth.features.FeatureGraph
import com.scenio.mth.features.data.FeatureSettings
import com.scenio.mth.features.data.ThemeMode
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SettingsLoad {
    data object Loading : SettingsLoad
    data class Ready(val value: FeatureSettings) : SettingsLoad
    data object Failed : SettingsLoad
}

class FeatureViewModel(private val graph: FeatureGraph) : ViewModel() {
    private val mutableState = MutableStateFlow<SettingsLoad>(SettingsLoad.Loading)
    val state: StateFlow<SettingsLoad> = mutableState.asStateFlow()
    private val mutableError = MutableStateFlow<String?>(null)
    val error = mutableError.asStateFlow()
    private val mutableBusy = MutableStateFlow(false)
    val busy = mutableBusy.asStateFlow()
    private val mutex = Mutex()
    init {
        viewModelScope.launch {
            try { graph.settings.data.collect { mutableState.value = SettingsLoad.Ready(it) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { mutableState.value = SettingsLoad.Failed }
        }
    }
    private fun update(block: suspend () -> Unit) {
        viewModelScope.launch {
            mutex.withLock {
                mutableBusy.value = true
                try { block(); mutableError.value = null }
                catch (e: CancellationException) { throw e }
                catch (_: Exception) { mutableError.value = "Could not save. Please try again." }
                finally { mutableBusy.value = false }
            }
        }
    }
    fun finishOnboarding() = update {
        graph.progress.recordStep("studyforge-onboarding-v1", 1, 1)
        graph.settings.finishOnboarding()
    }
    fun theme(value: ThemeMode) = update { graph.settings.setTheme(value) }
    fun dynamic(value: Boolean) = update { graph.settings.setDynamicColor(value) }
    fun reminders(value: Boolean) = update { graph.settings.setReminders(value) }
    fun analytics(value: Boolean) = update {
        if (!value) graph.telemetry.disableAnalyticsNow()
        graph.settings.setAnalytics(value)
    }
    fun crashes(value: Boolean) = update {
        if (!value) graph.telemetry.disableCrashesNow()
        graph.settings.setCrashes(value)
    }
    fun message(value: String) { mutableError.value = value }
    class Factory(private val graph: FeatureGraph) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(FeatureViewModel::class.java))
            @Suppress("UNCHECKED_CAST") return FeatureViewModel(graph) as T
        }
    }
}
