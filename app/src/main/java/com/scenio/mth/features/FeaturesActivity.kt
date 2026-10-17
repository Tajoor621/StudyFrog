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
import com.scenio.mth.BuildConfig
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
                    var showAbout by remember { mutableStateOf(false) }
                    if(showAbout) AlertDialog(onDismissRequest={ showAbout=false },
                        title={ Text(stringResource(R.string.sf_app_name)) },
                        text={ Column { BrandHeader();Text(stringResource(R.string.sf_about,BuildConfig.VERSION_NAME)) } },
                        confirmButton={ TextButton(onClick={ showAbout=false }) { Text(stringResource(R.string.sf_close)) } })
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
                                    val setup by graph.progress.observe("studyforge-onboarding-v1")
                                        .collectAsStateWithLifecycle(initialValue = null)
                                    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())
                                        .padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                        BrandHeader()
                                        Text(stringResource(R.string.sf_app_name),
                                            style = MaterialTheme.typography.headlineMedium,
                                            modifier = Modifier.semantics { heading() })
                                        if (setup?.completedSteps == 1) Text(stringResource(R.string.sf_setup_complete))
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
                                        TextButton(onClick={ showAbout=true }) { Text(stringResource(R.string.sf_about_title)) }
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
