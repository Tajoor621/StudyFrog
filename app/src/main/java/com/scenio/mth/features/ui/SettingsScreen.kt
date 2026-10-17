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
