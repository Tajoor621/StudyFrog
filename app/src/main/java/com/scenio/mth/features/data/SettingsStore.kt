package com.scenio.mth.features.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
data class FeatureSettings(
    val onboarded: Boolean = false,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val reminders: Boolean = false,
    val analytics: Boolean = false,
    val crashes: Boolean = false
)

class SettingsStore(private val store: DataStore<Preferences>) {
    private object Keys {
        val onboarded = booleanPreferencesKey("onboarded")
        val theme = stringPreferencesKey("theme")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val reminders = booleanPreferencesKey("reminders")
        val analytics = booleanPreferencesKey("analytics_opt_in")
        val crashes = booleanPreferencesKey("crashes_opt_in")
    }
    // Read failures reach the UI; they are never mistaken for a completed onboarding.
    val data: Flow<FeatureSettings> = store.data.map { p ->
        FeatureSettings(
            onboarded = p[Keys.onboarded] ?: false,
            theme = ThemeMode.entries.firstOrNull { it.name == p[Keys.theme] } ?: ThemeMode.SYSTEM,
            dynamicColor = p[Keys.dynamic] ?: true,
            reminders = p[Keys.reminders] ?: false,
            analytics = p[Keys.analytics] ?: false,
            crashes = p[Keys.crashes] ?: false
        )
    }.distinctUntilChanged()
    suspend fun finishOnboarding() { store.edit { it[Keys.onboarded] = true } }
    suspend fun setTheme(value: ThemeMode) { store.edit { it[Keys.theme] = value.name } }
    suspend fun setDynamicColor(value: Boolean) { store.edit { it[Keys.dynamic] = value } }
    suspend fun setReminders(value: Boolean) { store.edit { it[Keys.reminders] = value } }
    suspend fun setAnalytics(value: Boolean) { store.edit { it[Keys.analytics] = value } }
    suspend fun setCrashes(value: Boolean) { store.edit { it[Keys.crashes] = value } }
}
