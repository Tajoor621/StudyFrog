> Integrated build update: Room is pinned to 2.7.2 with KSP2. The obsolete android.bundle.enableUncompressedNativeLibs property is omitted. Use root app/build.gradle.kts and gradle.properties as authoritative. The full original guide below is retained as a workflow reference.

> Reference document from the feature integration package. Its source additions are applied in this repository; the root README and build files describe the integrated app.

# StudyForge — Android feature integration

Assumptions: existing module `:app`, namespace/application ID `com.scenio.mth`,
original studio class `com.scenio.mth.MainActivity`, minSdk 24, target/compile
35, JDK 17, Gradle 8.9, AGP 8.7.3 and Kotlin 2.0.21. Pins below form a conservative
reference set, not the newest available stack. Preserve newer existing versions
if your actual project already requires them and verify its dependency graph.

The actual com.scenio.mth source was not provided. These are complete feature
source files and explicit merge instructions, not a claim that an unseen app
has already been modified or compiled. Read MERGE.md before copying anything.
Existing generation, native build, ABI/density splits and versioning remain
owned by your original project. Do not manually create Play-derived metadata.

This workflow uses no paid API, subscription, paid developer console, paid
license or credit-card-required service. Core data/UI/reminders work locally.
Firebase is optional, Spark-only and off by default. GitHub-hosted CI stays
within your account's free allowance; local Gradle commands do not rely on CI.

New-key signing cannot update an installation signed by a different key. Keep
the original signing identity for updates. The observed derived APK ID is not
a signing credential. Back up user data before any manual uninstall; no command
below automatically uninstalls the app or clears its data.

## 1. Offline-first data layer — Room + DataStore

**Value:** progress survives restarts, reads update through Flow, and preference
writes are atomic. No account or connection is required. A separate database
and preferences file preserve unrelated existing schemas and backups.

**Files:** create ProgressStore.kt, SettingsStore.kt and FeatureRuntime.kt below;
create sf_backup_rules.xml and sf_data_extraction_rules.xml. Modify root/app
Gradle, gradle.properties and the existing Application/manifest as described.

**Pinned dependencies:** Room runtime/ktx/compiler 2.6.1, DataStore Preferences
1.1.7, Coroutines Android 1.9.0, KSP 2.0.21-1.0.28. Full Gradle declarations are
in Build & Ship. Add `android.useAndroidX=true`. Keep existing native packaging.

**Manifest:** register StudyForgeApplication only if there is no existing custom
Application. Otherwise keep that Application and add `FeatureRuntime.start(this)`
after its super.onCreate. Attach the supplied backup rules only if you do not
already have rules; otherwise merge their exclusions into both legacy/modern rules.

**`app/src/main/java/com/scenio/mth/features/data/ProgressStore.kt`**

```kotlin
package com.scenio.mth.features.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "sf_progress")
data class StudyProgress(
    @PrimaryKey val projectId: String,
    val completedSteps: Int,
    val totalSteps: Int,
    val updatedAtMillis: Long
) {
    init {
        require(projectId.isNotBlank() && projectId.length <= 200)
        require(totalSteps > 0 && completedSteps in 0..totalSteps)
    }
}

val StudyProgress.fraction: Float get() = completedSteps.toFloat() / totalSteps

@Dao
interface ProgressDao {
    @Query("SELECT * FROM sf_progress WHERE projectId = :id")
    fun observe(id: String): Flow<StudyProgress?>
    @Query("SELECT * FROM sf_progress WHERE projectId = :id")
    suspend fun get(id: String): StudyProgress?
    @Upsert suspend fun upsert(progress: StudyProgress)
}

@Database(entities = [StudyProgress::class], version = 1, exportSchema = true)
abstract class ProgressDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
}

class ProgressRepository(private val db: ProgressDatabase) {
    fun observe(id: String) = db.progressDao().observe(id)
    suspend fun recordStep(id: String, step: Int, total: Int) {
        val candidate = StudyProgress(id, step, total, System.currentTimeMillis())
        db.withTransaction {
            val previous = db.progressDao().get(id)
            require(previous == null || previous.totalSteps == total) {
                "Progress total changed; use a versioned project identifier"
            }
            db.progressDao().upsert(candidate.copy(
                completedSteps = maxOf(previous?.completedSteps ?: 0, step)
            ))
        }
    }
}
```

**`app/src/main/java/com/scenio/mth/features/data/SettingsStore.kt`**

```kotlin
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
```

**`app/src/main/java/com/scenio/mth/features/FeatureRuntime.kt`**

```kotlin
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
```

**`app/src/main/res/xml/sf_backup_rules.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<full-backup-content>

<exclude domain="database" path="studyforge_feature_progress.db" />
<exclude domain="database" path="studyforge_feature_progress.db-wal" />
<exclude domain="database" path="studyforge_feature_progress.db-shm" />
<exclude domain="file" path="datastore/studyforge_feature_settings.preferences_pb" />
</full-backup-content>
```

**`app/src/main/res/xml/sf_data_extraction_rules.xml`**

```xml
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
<cloud-backup>

<exclude domain="database" path="studyforge_feature_progress.db" />
<exclude domain="database" path="studyforge_feature_progress.db-wal" />
<exclude domain="database" path="studyforge_feature_progress.db-shm" />
<exclude domain="file" path="datastore/studyforge_feature_settings.preferences_pb" />
</cloud-backup>
<device-transfer>

<exclude domain="database" path="studyforge_feature_progress.db" />
<exclude domain="database" path="studyforge_feature_progress.db-wal" />
<exclude domain="database" path="studyforge_feature_progress.db-shm" />
<exclude domain="file" path="datastore/studyforge_feature_settings.preferences_pb" />
</device-transfer>
</data-extraction-rules>
```

**Permission/privacy:** Room and DataStore need no runtime permission. Files
are private to the app sandbox but not application-level encrypted. The new
preferences/database are excluded from cloud backup and device transfer so
progress and consent are not copied silently. Retain any existing
allowBackup=false. No destructive Room fallback is used. Future schema changes
require explicit migrations. The graph is a single-process singleton.

**Testing:** run SettingsStoreTest and ProgressDatabaseTest; restart/reopen;
concurrently save out-of-order steps; check progress never regresses; test disk
full/read failure; verify existing app database and project files are untouched.
Hook real generation/learning events to `recordStep(projectId, step, total)`.
Only actual onboarding completion is hooked in this reference; it does not
invent engine progress or infer an unseen engine's events.

## 2. Onboarding — Compose + Navigation

**Value:** first-use explanation is shown until successful persistence, then
removed from the back stack. The existing studio opens from the feature shell.

**Files:** create FeaturesActivity.kt, ui/OnboardingScreen.kt and ui/FeatureViewModel.kt.
Modify the original AndroidManifest.xml launcher declaration. Keep MainActivity
registered and preserve its valid deep-link/file-intent filters. Only one
activity should retain MAIN/LAUNCHER.

**Dependencies:** Activity Compose 1.10.1; Navigation Compose 2.8.9; Lifecycle
runtime-compose/viewmodel-compose/viewmodel-ktx 2.8.7; Compose BOM 2025.02.00.
Apply Kotlin's Compose compiler plugin 2.0.21 to match Kotlin 2.0.21.

**Manifest activity to add:**

```xml
<activity android:name="com.scenio.mth.features.FeaturesActivity"
    android:exported="true" android:theme="@android:style/Theme.Material.Light.NoActionBar">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

**`app/src/main/java/com/scenio/mth/features/ui/FeatureViewModel.kt`**

```kotlin
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
```

**`app/src/main/java/com/scenio/mth/features/ui/OnboardingScreen.kt`**

```kotlin
package com.scenio.mth.features.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.scenio.mth.R

@Composable
fun OnboardingScreen(busy: Boolean, onContinue: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(stringResource(R.string.sf_welcome), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.sf_onboarding_body))
        Text(stringResource(R.string.sf_onboarding_privacy))
        Button(onClick = onContinue, enabled = !busy, modifier = Modifier.heightIn(min = 48.dp)) {
            Text(stringResource(R.string.sf_get_started))
        }
    }
}
```

**`app/src/main/java/com/scenio/mth/features/FeaturesActivity.kt`**

```kotlin
package com.scenio.mth.features

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import com.scenio.mth.R
import com.scenio.mth.features.ui.*

class FeaturesActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val graph = FeatureRuntime.graph(this)
        setContent {
            val vm: FeatureViewModel = viewModel(factory = FeatureViewModel.Factory(graph))
            val load by vm.state.collectAsStateWithLifecycle()
            val busy by vm.busy.collectAsStateWithLifecycle()
            val error by vm.error.collectAsStateWithLifecycle()
            when (val state = load) {
                SettingsLoad.Loading -> MaterialTheme {
                    Surface(Modifier.fillMaxSize()) { Box(Modifier.padding(24.dp)) {
                        Text(stringResource(R.string.sf_loading))
                    } }
                }
                SettingsLoad.Failed -> MaterialTheme {
                    Surface(Modifier.fillMaxSize()) { Text(stringResource(R.string.sf_storage_error),
                        modifier = Modifier.safeDrawingPadding().padding(24.dp)) }
                }
                is SettingsLoad.Ready -> StudyForgeTheme(state.value) {
                    val nav = rememberNavController()
                    val initial = remember { if (state.value.onboarded) "home" else "onboarding" }
                    LaunchedEffect(state.value.onboarded) {
                        if (state.value.onboarded && nav.currentDestination?.route == "onboarding") {
                            nav.navigate("home") {
                                popUpTo("onboarding") { inclusive = true }
                                launchSingleTop = true
                            }
                        }
                    }
                    Surface(Modifier.fillMaxSize()) {
                        Column(Modifier.safeDrawingPadding()) {
                            if (error != null) Text(error.orEmpty(),
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
                                    .padding(horizontal = 24.dp, vertical = 8.dp))
                            NavHost(nav, startDestination = initial, modifier = Modifier.weight(1f)) {
                                composable("onboarding") { OnboardingScreen(busy, vm::finishOnboarding) }
                                composable("home") {
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        Text(stringResource(R.string.sf_app_name),
                                            style = MaterialTheme.typography.headlineMedium,
                                            modifier = Modifier.semantics { heading() })
                                        Button(onClick = {
                                            try {
                                                startActivity(Intent().setClassName(packageName,
                                                    "com.scenio.mth.MainActivity"))
                                                graph.telemetry.studioOpened()
                                            } catch (_: ActivityNotFoundException) {
                                                vm.message(getString(R.string.sf_studio_unavailable))
                                            }
                                        }) { Text(stringResource(R.string.sf_open_studio)) }
                                        OutlinedButton(onClick = { nav.navigate("settings") }) {
                                            Text(stringResource(R.string.sf_settings))
                                        }
                                    }
                                }
                                composable("settings") {
                                    SettingsScreen(state.value, vm, busy) { nav.popBackStack() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
```

**Permission/privacy:** no new permission. Onboarding completion is local;
continuing does not opt into notifications or telemetry. This introduction is
not an authentication, age-verification or parental-control security boundary.

**Testing:** fresh install; process death before completion; failed storage
write; repeated taps; Back after completion; screen rotation; launch existing
studio; relaunch and verify onboarding does not return. OnboardingTest exercises
completion/recreation; device reboot and kill/relaunch remain acceptance checks.

## 3. Dark mode + dynamic color — Material 3

**Value:** users can choose system/light/dark and optionally use Android 12+
wallpaper-derived palettes. Older devices use Material's fallback schemes.

**Files:** create ui/StudyForgeTheme.kt; wire it in FeaturesActivity. If the
original studio is Compose, wrap its content in the same theme/flow; a legacy
View studio needs its own theme wiring and is not silently recolored by this overlay.

**Dependencies:** Compose BOM 2025.02.00 pins Material 3 and Compose UI versions;
no additional manifest permission or downloaded font is required.

**`app/src/main/java/com/scenio/mth/features/ui/StudyForgeTheme.kt`**

```kotlin
package com.scenio.mth.features.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.scenio.mth.features.data.FeatureSettings
import com.scenio.mth.features.data.ThemeMode

@Composable
fun StudyForgeTheme(settings: FeatureSettings, content: @Composable () -> Unit) {
    val dark = when (settings.theme) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }
    val context = LocalContext.current
    val colors = when {
        settings.dynamicColor && Build.VERSION.SDK_INT >= 31 ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = colors, typography = Typography(), content = content)
}
```

**Permission/privacy:** no wallpaper file access or network request. Dynamic
color uses Android's provided palette. System typography avoids font downloads.

**Testing:** API 24 fallback; API 31+ dynamic palettes; all three modes; system
night-mode change; rotation/restart; high-contrast text and 200% font scaling.

## 4. Settings — DataStore-backed

**Value:** appearance, reminders and separate analytics/crash choices persist.
Writes are serialized; UI errors are visible and do not silently advance state.

**Files:** create ui/SettingsScreen.kt. Reuse data/SettingsStore.kt and
ui/FeatureViewModel.kt from earlier sections. Add sf_strings.xml in section 7.

**Dependencies:** DataStore 1.1.7, Activity Compose 1.10.1, Core KTX 1.15.0 and
Material 3 from the Compose BOM. No extra settings permission is requested.
The notification screen is opened through a standard system intent.

**`app/src/main/java/com/scenio/mth/features/ui/SettingsScreen.kt`**

```kotlin
package com.scenio.mth.features.ui

import android.Manifest
import android.content.Intent
import android.content.ActivityNotFoundException
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.scenio.mth.BuildConfig
import com.scenio.mth.R
import com.scenio.mth.features.data.FeatureSettings
import com.scenio.mth.features.data.ThemeMode

@Composable
fun PreferenceSwitch(label: String, value: Boolean, enabled: Boolean = true,
                     onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .toggleable(value = value, enabled = enabled, role = Role.Switch, onValueChange = onChange)
        .semantics(mergeDescendants = true) {}.padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = value, enabled = enabled, onCheckedChange = null)
    }
}

@Composable
fun SettingsScreen(settings: FeatureSettings, vm: FeatureViewModel, busy: Boolean,
                   onBack: () -> Unit) {
    val context = LocalContext.current
    val notifications = NotificationManagerCompat.from(context)
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted && notifications.areNotificationsEnabled()) vm.reminders(true)
        else vm.message(context.getString(R.string.sf_permission_denied))
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(stringResource(R.string.sf_settings), style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.semantics { heading() })
        Text(stringResource(R.string.sf_theme))
        Column(Modifier.selectableGroup()) {
        ThemeMode.entries.forEach { mode ->
            val label = stringResource(when (mode) {
                ThemeMode.SYSTEM -> R.string.sf_system
                ThemeMode.LIGHT -> R.string.sf_light
                ThemeMode.DARK -> R.string.sf_dark
            })
            Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
                .selectable(selected = settings.theme == mode, role = Role.RadioButton, enabled = !busy,
                    onClick = { vm.theme(mode) }).padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                RadioButton(selected = settings.theme == mode, onClick = null, enabled = !busy)
                Text(label)
            }
        }
        }
        PreferenceSwitch(stringResource(R.string.sf_dynamic), settings.dynamicColor, !busy, vm::dynamic)
        Text(stringResource(R.string.sf_reminder_explanation))
        PreferenceSwitch(stringResource(R.string.sf_reminders), settings.reminders, !busy) { enabled ->
            when {
                !enabled -> vm.reminders(false)
                Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                    Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED ->
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                notifications.areNotificationsEnabled() -> vm.reminders(true)
                else -> vm.message(context.getString(R.string.sf_permission_denied))
            }
        }
        TextButton(onClick = {
            val intent = if (Build.VERSION.SDK_INT >= 26) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            } else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:${context.packageName}"))
            try { context.startActivity(intent) }
            catch (_: ActivityNotFoundException) { vm.message(context.getString(R.string.sf_permission_denied)) }
        }) { Text(stringResource(R.string.sf_notification_settings)) }
        Text(stringResource(R.string.sf_telemetry_explanation))
        if (!BuildConfig.FIREBASE_ENABLED) Text(stringResource(R.string.sf_telemetry_unavailable))
        PreferenceSwitch(stringResource(R.string.sf_analytics), settings.analytics,
            !busy && BuildConfig.FIREBASE_ENABLED, vm::analytics)
        PreferenceSwitch(stringResource(R.string.sf_crashes), settings.crashes,
            !busy && BuildConfig.FIREBASE_ENABLED, vm::crashes)
        Text(stringResource(R.string.sf_consent_details), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = onBack) { Text(stringResource(R.string.sf_back)) }
    }
}
```

**Permission/privacy:** reminders and cloud reporting default off. No setting
is tied to a subscription, login or feature paywall. The Firebase options are
disabled in a local-only build. Explicit SDK/pending-report details are shown
next to consent choices. No prompts or images are attached by this feature.

**Testing:** write/reopen each setting; rapidly change options; deny notification
permission; permanently deny then open OS settings; disable a notification
channel; run local-only build with no google-services.json; simulate failed writes.

## 5. Local reminders — WorkManager

**Value:** optional general reminders continue across ordinary process death
and reboots. One named periodic request prevents duplicated schedules.

**Files:** create reminders/ReminderWorker.kt and sf_ic_notification.xml; wire
the scheduler through FeatureGraph.start and the Settings permission launcher.

**Dependencies:** Work runtime-ktx 2.10.0, Core KTX 1.15.0. Add only
`android.permission.POST_NOTIFICATIONS` for notification posting. WorkManager's
own library components are merged automatically; do not add duplicate services
or request exact-alarm access.

**`app/src/main/java/com/scenio/mth/features/reminders/ReminderWorker.kt`**

```kotlin
package com.scenio.mth.features.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.scenio.mth.R
import com.scenio.mth.features.FeatureRuntime
import com.scenio.mth.features.FeaturesActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import java.io.IOException
import java.util.concurrent.TimeUnit

class ReminderScheduler(private val context: Context) {
    fun setEnabled(enabled: Boolean) {
        val manager = WorkManager.getInstance(context)
        if (!enabled) { manager.cancelUniqueWork(NAME); return }
        val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(24, TimeUnit.HOURS).build()
        manager.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
    }
    companion object { const val NAME = "studyforge-study-reminder-v1" }
}

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val context = applicationContext
        val settings = try { FeatureRuntime.graph(context).settings.data.first() }
        catch (e: CancellationException) { throw e }
        catch (_: IOException) { return if (runAttemptCount < 3) Result.retry() else Result.failure() }
        if (!settings.reminders) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return Result.success()
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL,
                context.getString(R.string.sf_reminder_channel), NotificationManager.IMPORTANCE_DEFAULT))
            if (manager.getNotificationChannel(CHANNEL)?.importance == NotificationManager.IMPORTANCE_NONE)
                return Result.success()
        }
        val intent = PendingIntent.getActivity(context, 0,
            Intent(context, FeaturesActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.sf_ic_notification)
            .setContentTitle(context.getString(R.string.sf_reminder_title))
            .setContentText(context.getString(R.string.sf_reminder_body))
            .setContentIntent(intent).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setCategory(NotificationCompat.CATEGORY_REMINDER).build()
        try { manager.notify(7301, notification) }
        catch (_: SecurityException) { return Result.success() }
        return Result.success()
    }
    companion object { const val CHANNEL = "studyforge-study-v1" }
}
```

**`app/src/main/res/drawable/sf_ic_notification.xml`**

```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">
    <path android:fillColor="#FFFFFFFF" android:pathData="M4,3h7v18H4zM13,3h7v18h-7z" />
</vector>
```

**Permission/privacy:** request notification permission on Android 13+ only
when the user enables reminders. Re-check permission and channel state at delivery,
and handle revocation races. Reminder text is generic, never a prompt, project
name or medical detail. Periodic work is inexact and may be delayed by Doze,
app restrictions or force-stop. It is not an exact daily alarm. Turning off
cancels scheduled work and each worker also checks the stored preference.

**Testing:** consent/deny/revoke; channel disabled; off cancels named work; repeat
enable does not duplicate; reboot; battery saver; force-stop/reopen; no network;
verify generic lock-screen text. ReminderWorkerTest verifies the off path.

## 6. Crash reporting + analytics — optional Firebase Spark

**Value:** users can separately choose usage diagnostics and crash reporting
while using every core feature without either. Spark Analytics/Crashlytics do
not require enabling paid cloud products or a billing account.

**Files:** create telemetry/Telemetry.kt; merge integration/manifest-additions.xml;
optionally add your own app/google-services.json. Add the google-services and
Crashlytics Gradle plugins only when `-PfirebaseEnabled=true` is selected.

**Dependencies:** Firebase BOM 33.9.0 pins firebase-crashlytics and firebase-analytics;
Google Services plugin 4.4.2; Crashlytics plugin 3.0.3. No obsolete -ktx Firebase
artifacts are used. This covers Java/Kotlin crashes; native symbolicated crash
reporting needs a separate NDK integration and is not claimed here.

**`app/src/main/java/com/scenio/mth/features/telemetry/Telemetry.kt`**

```kotlin
package com.scenio.mth.features.telemetry

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.FirebaseAnalytics
import com.google.firebase.crashlytics.FirebaseCrashlytics
import com.scenio.mth.BuildConfig

/** No Firebase initialization until at least one explicit opt-in is present. */
class Telemetry(private val context: Context) {
    private var initialized = false
    private var analyticsAllowed = false
    @Synchronized
    fun applyConsent(analytics: Boolean, crashes: Boolean) {
        if (!BuildConfig.FIREBASE_ENABLED) return
        if (!initialized && !analytics && !crashes) return
        if (!initialized) {
            checkNotNull(FirebaseApp.initializeApp(context)) { "Firebase configuration missing" }
            initialized = true
        }
        val stats = FirebaseAnalytics.getInstance(context)
        stats.setConsent(mapOf(
            FirebaseAnalytics.ConsentType.ANALYTICS_STORAGE to
                if (analytics) FirebaseAnalytics.ConsentStatus.GRANTED else FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_STORAGE to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_USER_DATA to FirebaseAnalytics.ConsentStatus.DENIED,
            FirebaseAnalytics.ConsentType.AD_PERSONALIZATION to FirebaseAnalytics.ConsentStatus.DENIED
        ))
        stats.setAnalyticsCollectionEnabled(analytics)
        if (!analytics && analyticsAllowed) stats.resetAnalyticsData()
        val reports = FirebaseCrashlytics.getInstance()
        reports.setCrashlyticsCollectionEnabled(crashes)
        if (!crashes) reports.deleteUnsentReports()
        analyticsAllowed = analytics
    }
    @Synchronized
    fun disableAnalyticsNow() {
        if (initialized) {
            FirebaseAnalytics.getInstance(context).setAnalyticsCollectionEnabled(false)
            FirebaseAnalytics.getInstance(context).resetAnalyticsData()
        }
        analyticsAllowed = false
    }
    @Synchronized
    fun disableCrashesNow() {
        if (initialized) {
            FirebaseCrashlytics.getInstance().setCrashlyticsCollectionEnabled(false)
            FirebaseCrashlytics.getInstance().deleteUnsentReports()
        }
    }
    @Synchronized
    fun studioOpened() {
        if (initialized && analyticsAllowed) FirebaseAnalytics.getInstance(context)
            .logEvent("studio_opened", null)
    }
}
```

**`integration/manifest-additions.xml`**

```xml
<!-- Merge into app/src/main/AndroidManifest.xml. Do not overwrite existing components. -->
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="com.google.android.gms.permission.AD_ID" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_AD_ID" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_ATTRIBUTION" tools:node="remove" />
    <uses-permission android:name="android.permission.ACCESS_ADSERVICES_TOPICS" tools:node="remove" />
    <application android:name="com.scenio.mth.features.StudyForgeApplication"
        android:extractNativeLibs="true"
        android:fullBackupContent="@xml/sf_backup_rules"
        android:dataExtractionRules="@xml/sf_data_extraction_rules">
        <provider android:name="com.google.firebase.provider.FirebaseInitProvider"
            android:authorities="${applicationId}.firebaseinitprovider" tools:node="remove" />
        <meta-data android:name="firebase_crashlytics_collection_enabled" android:value="false" />
        <meta-data android:name="firebase_analytics_collection_enabled" android:value="false" />
        <meta-data android:name="google_analytics_automatic_screen_reporting_enabled" android:value="false" />
        <meta-data android:name="google_analytics_adid_collection_enabled" android:value="false" />
        <meta-data android:name="google_analytics_default_allow_ad_personalization_signals" android:value="false" />
        <activity android:name="com.scenio.mth.features.FeaturesActivity" android:exported="true"
            android:theme="@android:style/Theme.Material.Light.NoActionBar">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>
</manifest>
```

**Setup:** in a free Firebase Spark project, register the exact release package
com.scenio.mth and download its configuration to app/google-services.json. No
Play Console is needed. Do not enable Blaze, billing-dependent storage, paid
functions or paid API calls. If your existing app uses Firebase before this
feature starts, audit and coordinate those initializers first; the new graph
must own analytics/crash consent. The SDKs are bundled but not initialized by
the feature until at least one opt-in exists. The auto-init provider is removed.

**Permission/privacy:** INTERNET is required for opted-in telemetry. Advertising
ID permission is removed; ad storage/personalization consent remains denied.
SDKs can use pseudonymous installation identifiers: do not call this anonymous
or say "no data collected" when users opt in. Disabling sharing stops automatic
reporting and deletes locally pending crash reports available at that point;
SDKs may still keep local crash records after being initialized. Re-enabling
may send pending reports, as disclosed. It does not delete data already received
by Firebase. Never put prompts, images, health details or user IDs into custom
events, breadcrumbs, exception messages or crash keys. Review your actual SDK
behavior and update privacy policy/Data safety disclosures accordingly.

**Testing:** fresh local-only launch makes no Firebase initialization; configured
build remains silent before consent; enable analytics only; crashes only; both;
revoke each; offline/reconnect; cold restart with each choice; inspect captured
traffic. For crash testing in a disposable debug build, trigger a test crash via
Android Studio after opt-in and relaunch. Do not ship a deliberate crash button.
Validate the consent lifecycle against any older Firebase preferences already
present in installations of the existing app.

## 7. Accessibility pass

**Value:** settings/onboarding work with TalkBack, large fonts, keyboard/switch
access and Material light/dark contrast. Text-bearing controls do not have
duplicate content descriptions; setting rows expose a single labeled target.

**Files:** UI implementations in sections 2–4; create res/values/sf_strings.xml.
The original app's unseen studio, canvas, custom controls and dialogs still
need their own accessibility audit. This is not a whole-app compliance claim.

**Dependencies:** existing Compose UI/Material 3 BOM; no accessibility-service
permission, storage permission, custom font download or new manifest component.

**`app/src/main/res/values/sf_strings.xml`**

```xml
<resources>
    <string name="sf_app_name">StudyForge</string>
    <string name="sf_welcome">Welcome to StudyForge</string>
    <string name="sf_onboarding_body">Create, learn and keep your study progress on this device. Your existing studio remains available.</string>
    <string name="sf_onboarding_privacy">Reminders, analytics and crash sharing are off until you choose them in Settings. You can use the app without signing in.</string>
    <string name="sf_get_started">Get started</string>
    <string name="sf_settings">Settings</string>
    <string name="sf_theme">Appearance</string>
    <string name="sf_system">Follow system</string>
    <string name="sf_light">Light</string>
    <string name="sf_dark">Dark</string>
    <string name="sf_dynamic">Use wallpaper colors on Android 12 or later</string>
    <string name="sf_reminders">Study reminders</string>
    <string name="sf_reminder_explanation">Receive a general reminder roughly every 24 hours. Android may delay delivery to save battery.</string>
    <string name="sf_permission_denied">Notifications are unavailable. You can enable them in system notification settings.</string>
    <string name="sf_notification_settings">System notification settings</string>
    <string name="sf_telemetry_explanation">Optional diagnostics. These settings do not affect your access to the app.</string>
    <string name="sf_telemetry_unavailable">Cloud diagnostics are not included in this build configuration.</string>
    <string name="sf_analytics">Share usage analytics</string>
    <string name="sf_crashes">Share crash diagnostics, including pending reports</string>
    <string name="sf_consent_details">If enabled, Firebase receives usage events or device, app and crash diagnostics. SDKs can use pseudonymous installation identifiers. Prompts and images are not deliberately logged by this feature. Disabling collection stops future automatic reporting; it does not delete data already received by Firebase. Earlier local crash reports may be sent when you enable crash sharing.</string>
    <string name="sf_back">Back</string>
    <string name="sf_loading">Loading preferences…</string>
    <string name="sf_storage_error">Preferences could not be loaded. Close and reopen the app after checking available storage.</string>
    <string name="sf_studio_unavailable">The original studio activity could not be opened.</string>
    <string name="sf_open_studio">Open studio</string>
    <string name="sf_reminder_channel">Study reminders</string>
    <string name="sf_reminder_title">A little time for learning</string>
    <string name="sf_reminder_body">Open StudyForge and continue when you are ready.</string>
</resources>
```

**Permission/privacy:** no accessibility service is installed or requested.
The original notification vector is Apache-2.0 source in this package; no paid
icons, fonts or remote asset services are used.

**Testing:** TalkBack reading/focus order; full label and checked state; one target
per switch; radio selected state; 48dp minimum actionable targets; 200% font
and enlarged display; landscape; RTL; keyboard/switch navigation; contrast
minimums 4.5:1 for normal text and 3:1 for large text/essential controls. Measure
actual dynamic palettes and actual studio assets on device. Screens scroll,
use sp typography, and do not cap the user's font scale.

## 8. Unit + UI tests

**Value:** persistence, concurrent progress updates, consent defaults, large-font
switch semantics, disabled reminders and onboarding recreation are exercised.

**Files:** add the test files below, preserving existing test sources.

**Dependencies:** JUnit Jupiter 5.11.4, JUnit Platform launcher 1.11.4, Vintage
engine 5.11.4 and JUnit4 4.13.2 for existing JVM tests; Coroutines test 1.9.0;
Turbine 1.2.0; AndroidX test runner 1.6.2/core 1.6.1/ext-JUnit 1.2.1;
Compose ui-test-junit4 via BOM; Room testing 2.6.1; Work testing 2.10.0.

JUnit5 is used for JVM tests. Standard Compose instrumentation uses AndroidJUnit4;
JUnit5 is not forced into the Android instrumentation runner. Enable
`useJUnitPlatform()` for Gradle Test tasks only and set AndroidJUnitRunner for
connected tests, as shown in the app build additions.

**`app/src/test/java/com/scenio/mth/features/ProgressTest.kt`**

```kotlin
package com.scenio.mth.features

import com.scenio.mth.features.data.StudyProgress
import com.scenio.mth.features.data.fraction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProgressTest {
    @Test fun `fraction represents persisted steps`() {
        assertEquals(0.5f, StudyProgress("mitosis-v1", 2, 4, 0).fraction)
    }
    @Test fun `invalid progress cannot enter repository`() {
        assertThrows(IllegalArgumentException::class.java) { StudyProgress("p", 4, 3, 0) }
        assertThrows(IllegalArgumentException::class.java) { StudyProgress("p", 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { StudyProgress(" ", 0, 3, 0) }
    }
}
```

**`app/src/test/java/com/scenio/mth/features/SettingsStoreTest.kt`**

```kotlin
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
```

**`app/src/androidTest/java/com/scenio/mth/features/AccessibilityTest.kt`**

```kotlin
package com.scenio.mth.features

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.features.ui.PreferenceSwitch
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccessibilityTest {
    @get:Rule val compose = createComposeRule()
    @Test fun switchHasOneLabeledTargetAtDoubleFontScale() {
        compose.setContent {
            var checked by remember { mutableStateOf(false) }
            CompositionLocalProvider(LocalDensity provides Density(1f, 2f)) {
                MaterialTheme { PreferenceSwitch("Study reminders", checked) { checked = it } }
            }
        }
        compose.onNodeWithText("Study reminders").assertIsDisplayed().assertIsOff().performClick().assertIsOn()
    }
}
```

**`app/src/androidTest/java/com/scenio/mth/features/OnboardingTest.kt`**

```kotlin
package com.scenio.mth.features

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OnboardingTest {
    @get:Rule val compose = createEmptyComposeRule()
    @Test fun completedOnboardingIsNotShownAfterRecreation() {
        ActivityScenario.launch(FeaturesActivity::class.java).use { scenario ->
            compose.waitUntil(timeoutMillis = 10000) {
                compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty() ||
                    compose.onAllNodesWithText("Open studio").fetchSemanticsNodes().isNotEmpty()
            }
            if (compose.onAllNodesWithText("Get started").fetchSemanticsNodes().isNotEmpty())
                compose.onNodeWithText("Get started").performClick()
            compose.waitUntil(timeoutMillis = 10000) {
                compose.onAllNodesWithText("Open studio").fetchSemanticsNodes().isNotEmpty()
            }
            scenario.recreate()
            compose.waitUntil(timeoutMillis = 10000) {
                compose.onAllNodesWithText("Open studio").fetchSemanticsNodes().isNotEmpty()
            }
            compose.onNodeWithText("Get started").assertDoesNotExist()
        }
    }
}
```

**`app/src/androidTest/java/com/scenio/mth/features/ProgressDatabaseTest.kt`**

```kotlin
package com.scenio.mth.features

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.features.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ProgressDatabaseTest {
    @Test fun concurrentStepsNeverLoseProgress() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, ProgressDatabase::class.java).build()
        try {
            val repo = ProgressRepository(db)
            coroutineScope { (1..20).map { step -> async(Dispatchers.IO) {
                repo.recordStep("science-v1", step, 20)
            } }.awaitAll() }
            val saved = repo.observe("science-v1").first()
            assertEquals(20, saved?.completedSteps)
            assertEquals(20, saved?.totalSteps)
        } finally { db.close() }
    }
    @Test fun progressSurvivesDatabaseReopening() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "sf-test-${java.util.UUID.randomUUID()}.db"
        var db = Room.databaseBuilder(context, ProgressDatabase::class.java, name).build()
        try {
            ProgressRepository(db).recordStep("cell-v1", 2, 5)
            db.close()
            db = Room.databaseBuilder(context, ProgressDatabase::class.java, name).build()
            assertEquals(2, ProgressRepository(db).observe("cell-v1").first()?.completedSteps)
        } finally { db.close(); context.deleteDatabase(name) }
    }
}
```

**`app/src/androidTest/java/com/scenio/mth/features/ReminderWorkerTest.kt`**

```kotlin
package com.scenio.mth.features

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.scenio.mth.features.reminders.ReminderWorker
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ReminderWorkerTest {
    @Test fun disabledRemindersExitWithoutPosting() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val settings = FeatureRuntime.graph(context).settings
        val previous = settings.data.first().reminders
        try {
            settings.setReminders(false)
            val worker = TestListenableWorkerBuilder<ReminderWorker>(context).build()
            assertEquals(ListenableWorker.Result.success(), worker.doWork())
        } finally { settings.setReminders(previous) }
    }
}
```

**Permission/privacy:** instrumentation runs against the debug app and should
use a test device or separately signed test installation. It does not require
cloud telemetry. Do not clear a user's installed release app to make tests pass.
Room tests use separate randomly named databases and delete only those test files.

**Testing:** execute testDebugUnitTest and connectedDebugAndroidTest; inspect test
counts/results, not just task exit status. Keep failing CI red. Schema migrations,
real network consent, permission-denial UI and full studio accessibility require
additional tests once integrated with the actual repository.

## 9. GitHub Actions CI — lint + test + assembleRelease

**Value:** pull requests and pushes receive repeatable lint, JVM test and release
compilation checks. Untrusted PR jobs do not receive signing/Firebase secrets.

**Files:** create .github/workflows/android-features.yml. Complete YAML is at the
end of Build & Ship; do not delete an existing packaging workflow.

**Dependencies:** GitHub Actions free allowance, Temurin JDK 17, free Android SDK,
Gradle wrapper, and the existing project's pinned NDK/CMake. No Kotlin or
manifest change is required specifically for CI. CI invokes the app code/tests
already supplied rather than introducing a runtime SDK.

The workflow runs `lintDebug testDebugUnitTest assembleRelease` in a local-only
configuration. Signed release additionally runs lintRelease/testReleaseUnitTest.
The pull-request release assembly is a compile check, not an installable signed
artifact. Connected UI tests run on your physical Android device because the
existing arm64-only native libraries may not support a free x86 emulator. If you
later add x86_64 native artifacts, a free emulator job can be added separately.

**Permission/privacy:** contents:read only; no pull_request_target; no secrets
on PR builds; a protected main-branch manual job accesses release secrets. Keep
CI logs and reports free of real prompts, photos and credentials. Free hosted
minutes are finite; local verification remains available without hosted CI.

**Testing:** PR lint/test failure blocks checks; unsigned assembly succeeds with
no secrets; fork PR cannot sign; manual signing fails if any secret is missing;
verify native build toolchain pins match the existing project.

## 10. Signed release configuration

**Value:** reproducible APK/AAB signing without a paid store or signing service.
Credentials are environment-based, checked as a complete set, and never embedded
in source. Signed builds fail closed when explicitly required but unavailable.

**Files:** merge release/signing blocks in integration/app-build.gradle.kts; merge
integration/gitignore-additions.txt; create the protected android-release GitHub
environment and its secrets. Keep your existing source versionCode increasing.
The exact local commands are below.

**Dependencies:** JDK17 keytool, Gradle/AGP already pinned, Android SDK apksigner,
optional bundletool 1.18.2 for split generation. No extra Kotlin SDK or manifest
permission is needed. Release signing cannot be implemented by a Kotlin Activity;
it is a build-time operation, so the exact Kotlin DSL implementation is included
in Build & Ship rather than adding unnecessary runtime code.

**Privacy/security:** SF_KEYSTORE_B64, SF_STORE_PASSWORD, SF_KEY_PASSWORD and
SF_KEY_ALIAS are protected GitHub secrets. GOOGLE_SERVICES_B64 is optional project
configuration; it is not a private signing key, but is kept out of this source
package. Base64 is encoding, not encryption. Preserve the private signing key
securely outside Git and keep recovery backups. A new key cannot update a
Play-signed installation with a different app-signing certificate. The derived
APK ID does not alter this rule. Use the original app-signing identity for
updates or use a clean, separately authorized test installation. No publishing
to Play or any paid channel occurs here.

**Testing:** missing/partial credentials fail; inspect signingReport and
apksigner certificates; verify a signed APK installs; verify update using same
certificate and a higher versionCode retains app data; test generated split
set using bundletool and confirm native .so files are still packaged. Inspect
extractNativeLibs in base-master's manifest, not a resource-only density split.

## Build & Ship

After following MERGE.md, these are the exact complete Gradle merge inputs.
Preserve original plugin/dependency declarations and native build blocks.

**`integration/root-build.gradle.kts`**

```kotlin
// Merge these plugin declarations into the existing root build.gradle.kts.
plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("com.google.devtools.ksp") version "2.0.21-1.0.28" apply false
    id("com.google.gms.google-services") version "4.4.2" apply false
    id("com.google.firebase.crashlytics") version "3.0.3" apply false
}
```

**`integration/app-build.gradle.kts`**

```kotlin
// Merge these blocks into app/build.gradle.kts; preserve existing dependencies,
// versionCode, versionName, externalNativeBuild, ABI filters and native options.
import org.gradle.api.tasks.testing.Test

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

val firebaseEnabled = providers.gradleProperty("firebaseEnabled")
    .map(String::toBooleanStrict).getOrElse(false)
if (firebaseEnabled) {
    apply(plugin = "com.google.gms.google-services")
    apply(plugin = "com.google.firebase.crashlytics")
    check(file("google-services.json").isFile) { "Firebase enabled but app/google-services.json is missing" }
}

val signingValues = listOf("SF_KEYSTORE_PATH", "SF_STORE_PASSWORD", "SF_KEY_ALIAS", "SF_KEY_PASSWORD")
    .associateWith { providers.environmentVariable(it).orNull }
val anySigning = signingValues.values.any { !it.isNullOrBlank() }
val signingReady = signingValues.values.all { !it.isNullOrBlank() }
check(!anySigning || signingReady) { "All four SF signing environment variables are required" }
val requireSigning = providers.gradleProperty("requireReleaseSigning")
    .map(String::toBooleanStrict).getOrElse(false)
check(!requireSigning || signingReady) { "Signed release requested without complete signing credentials" }

android {
    namespace = "com.scenio.mth"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.scenio.mth"
        minSdk = 24
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("boolean", "FIREBASE_ENABLED", firebaseEnabled.toString())
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    packaging {
        jniLibs { useLegacyPackaging = true }
        resources { excludes += setOf("META-INF/LICENSE.md", "META-INF/LICENSE-notice.md") }
    }
    signingConfigs {
        if (signingReady) create("studyforgeRelease") {
            storeFile = file(signingValues.getValue("SF_KEYSTORE_PATH")!!)
            storePassword = signingValues.getValue("SF_STORE_PASSWORD")
            keyAlias = signingValues.getValue("SF_KEY_ALIAS")
            keyPassword = signingValues.getValue("SF_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            if (signingReady) signingConfig = signingConfigs.getByName("studyforgeRelease")
        }
    }
    lint { abortOnError = true }
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
tasks.withType<Test>().configureEach { useJUnitPlatform() }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.02.00")
    implementation(composeBom)
    androidTestImplementation(composeBom)
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.navigation:navigation-compose:2.8.9")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.7")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("com.google.firebase:firebase-bom:33.9.0"))
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-analytics")
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
    testImplementation("junit:junit:4.13.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher:1.11.4")
    // Preserve execution of any existing JUnit4 JVM tests.
    testRuntimeOnly("org.junit.vintage:junit-vintage-engine:5.11.4")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("app.cash.turbine:turbine:1.2.0")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.room:room-testing:2.6.1")
    androidTestImplementation("androidx.work:work-testing:2.10.0")
    androidTestImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

**`integration/gradle.properties`**

```properties
# Merge with your existing properties.
android.useAndroidX=true
android.bundle.enableUncompressedNativeLibs=false
```

**`integration/gitignore-additions.txt`**

```gitignore
*.jks
*.keystore
app/google-services.json
signing-secrets/
local.properties
```

**Local commands — Android Studio SDK installed, JDK 17 selected:**

```bash
# Project root; Android Studio can set SDK location in local.properties.
export ANDROID_HOME="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/35.0.0:$PATH"
chmod +x gradlew
./gradlew wrapper --gradle-version 8.9 --distribution-type bin
./gradlew --no-daemon lintDebug testDebugUnitTest assembleRelease -PfirebaseEnabled=false

# Physical arm64 Android test device, with USB debugging authorized:
adb devices
./gradlew --no-daemon connectedDebugAndroidTest -PfirebaseEnabled=false

# Commit generated Room schemas after reviewing the first successful build.
git add app/schemas
```

If your installed release has a different signature from the debug key, use a
separate test device or configure your existing debug variant with its own
applicationIdSuffix. Do not uninstall and lose release app data to run tests.
The studio class is explicit com.scenio.mth.MainActivity, so an existing debug
applicationId suffix does not change the class lookup.

**Generate your own local release key only if you do not already have the key
required for your existing installation:**

```bash
mkdir -p signing-secrets
chmod 700 signing-secrets
export SF_KEYSTORE_PATH="$PWD/signing-secrets/studyforge-release.jks"
export SF_KEY_ALIAS=studyforge
read -rsp 'Keystore password: ' SF_STORE_PASSWORD
printf '\n'
export SF_STORE_PASSWORD
read -rsp 'Key password: ' SF_KEY_PASSWORD
printf '\n'
export SF_KEY_PASSWORD
keytool -genkeypair -storetype JKS -keystore "$SF_KEYSTORE_PATH" \
  -alias "$SF_KEY_ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
  -dname 'CN=StudyForge' \
  -storepass:env SF_STORE_PASSWORD -keypass:env SF_KEY_PASSWORD

./gradlew --no-daemon lintRelease testReleaseUnitTest assembleRelease bundleRelease \
  -PfirebaseEnabled=false -PrequireReleaseSigning=true

"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs \
  app/build/outputs/apk/release/app-release.apk
./gradlew signingReport
```

For an original existing key, set SF_KEYSTORE_PATH and SF_KEY_ALIAS to that key,
read its passwords into the same environment variables and skip key generation.

**Optional configured Firebase build:** register com.scenio.mth in a Spark
project, place its actual downloaded file at app/google-services.json, then:

```bash
./gradlew --no-daemon lintRelease testReleaseUnitTest assembleRelease bundleRelease \
  -PfirebaseEnabled=true -PrequireReleaseSigning=true
```

If you use a debug applicationId suffix, a configured Firebase debug build also
requires that exact debug package registered in the same Spark project.

**Generate/install local split APKs using the same signing identity:**

```bash
curl -fL https://github.com/google/bundletool/releases/download/1.18.2/bundletool-all-1.18.2.jar \
  -o bundletool-all-1.18.2.jar
export BUNDLETOOL_JAR="$PWD/bundletool-all-1.18.2.jar"
bash scripts/verify-release.sh
```

That script lists actual split names, inspects the base binary manifest,
checks the APK certificate, and installs the complete device-matched APK set.
Density names/file names remain bundletool-generated. Preserve all original
native library files and original ABI/density configuration. If an existing
installed app uses another certificate, installation fails rather than silently
replacing/uninstalling it. Direct APK installation after verifying the signer:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell am start -n com.scenio.mth/com.scenio.mth.features.FeaturesActivity
```

**GitHub secrets:** in repository Settings → Environments → android-release,
restrict deployment to main and add SF_KEYSTORE_B64, SF_STORE_PASSWORD,
SF_KEY_PASSWORD, SF_KEY_ALIAS, and optionally GOOGLE_SERVICES_B64. Add required
reviewers where available. Keep automatic paid overages disabled for your
account. Produce the base64 inputs locally without printing secrets to logs:

```bash
base64 < "$SF_KEYSTORE_PATH" | tr -d '\n' > signing-secrets/keystore.b64
# Only if you opted to configure Firebase:
base64 < app/google-services.json | tr -d '\n' > signing-secrets/google-services.b64
```

Use Actions → StudyForge Android → Run workflow on main. Leave signedRelease
false for checks alone; set signedRelease true for protected signed output.
Enable firebaseEnabled only if GOOGLE_SERVICES_B64 is supplied. Artifacts are
signed APK/AAB files for local distribution; this workflow does not publish them.

**Verification boundary:** XML/YAML parsing, shell syntax and resource-reference
checks were performed here. Kotlin compilation, Android lint, all JUnit/UI tests,
Firebase traffic validation and release builds must run against your actual
repository. No successful build or test run is claimed. See VALIDATION.md and
SOURCES.md for the exact checks and primary references.

**`.github/workflows/android-features.yml`:**

**`.github/workflows/android-features.yml`**

```yaml
name: StudyForge Android
on:
  pull_request:
  push:
    branches: [main]
  workflow_dispatch:
    inputs:
      signedRelease:
        description: Build a signed release using protected secrets
        type: boolean
        default: false
      firebaseEnabled:
        description: Include configured opt-in Firebase reporting
        type: boolean
        default: false
permissions:
  contents: read
concurrency:
  group: android-${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: true
jobs:
  checks:
    runs-on: ubuntu-latest
    timeout-minutes: 35
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - name: Install existing native build toolchain
        run: sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.2.12479018' 'cmake;3.22.1'
      - name: Lint, JVM tests, unsigned release assembly
        run: |
          chmod +x gradlew
          ./gradlew --no-daemon lintDebug testDebugUnitTest assembleRelease -PfirebaseEnabled=false
      - uses: actions/upload-artifact@v4
        if: always()
        with:
          name: validation-reports
          path: |
            app/build/reports/
            app/build/test-results/
          if-no-files-found: ignore
  signed-release:
    if: github.event_name == 'workflow_dispatch' && github.ref == 'refs/heads/main' && inputs.signedRelease
    needs: [checks]
    environment: android-release
    runs-on: ubuntu-latest
    timeout-minutes: 35
    env:
      SF_STORE_PASSWORD: ${{ secrets.SF_STORE_PASSWORD }}
      SF_KEY_PASSWORD: ${{ secrets.SF_KEY_PASSWORD }}
      SF_KEY_ALIAS: ${{ secrets.SF_KEY_ALIAS }}
      FIREBASE_ENABLED: ${{ inputs.firebaseEnabled }}
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: '17'
      - uses: android-actions/setup-android@v3
      - uses: gradle/actions/setup-gradle@v4
      - name: Install existing native build toolchain
        run: sdkmanager 'platforms;android-35' 'build-tools;35.0.0' 'ndk;27.2.12479018' 'cmake;3.22.1'
      - name: Restore signing and optional Firebase configuration
        env:
          KEYSTORE_B64: ${{ secrets.SF_KEYSTORE_B64 }}
          GOOGLE_SERVICES_B64: ${{ secrets.GOOGLE_SERVICES_B64 }}
        run: |
          set -euo pipefail
          test -n "$KEYSTORE_B64"
          umask 077
          printf '%s' "$KEYSTORE_B64" | base64 --decode > "$RUNNER_TEMP/studyforge-release.jks"
          echo "SF_KEYSTORE_PATH=$RUNNER_TEMP/studyforge-release.jks" >> "$GITHUB_ENV"
          if [ "$FIREBASE_ENABLED" = true ]; then
            test -n "$GOOGLE_SERVICES_B64"
            printf '%s' "$GOOGLE_SERVICES_B64" | base64 --decode > app/google-services.json
          fi
      - name: Build signed APK and AAB
        run: ./gradlew --no-daemon lintRelease testReleaseUnitTest assembleRelease bundleRelease -PrequireReleaseSigning=true -PfirebaseEnabled="$FIREBASE_ENABLED"
      - uses: actions/upload-artifact@v4
        with:
          name: studyforge-signed-release
          retention-days: 7
          path: |
            app/build/outputs/apk/release/*.apk
            app/build/outputs/bundle/release/*.aab
      - name: Remove sensitive files
        if: always()
        run: |
          rm -f "$RUNNER_TEMP/studyforge-release.jks"
          rm -f app/google-services.json
```

