package com.scenio.mth.features

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.R
import com.scenio.mth.features.ui.OnboardingScreen
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicBoolean

@RunWith(AndroidJUnit4::class)
class SetupScreenTest {
    @get:Rule val compose=createComposeRule()
    @Test fun modelFreeFirstRunRemainsActionableAtLargeFontScale() {
        val continued=AtomicBoolean(false)
        compose.setContent {
            val density=LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density,2f)) {
                MaterialTheme { OnboardingScreen(false) { continued.set(true) } }
            }
        }
        val context=ApplicationProvider.getApplicationContext<android.content.Context>()
        compose.onNodeWithText(context.getString(R.string.sf_onboarding_body)).assertExists()
        compose.onNodeWithText(context.getString(R.string.sf_get_started)).performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertTrue(continued.get()) }
    }
}
