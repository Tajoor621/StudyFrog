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
