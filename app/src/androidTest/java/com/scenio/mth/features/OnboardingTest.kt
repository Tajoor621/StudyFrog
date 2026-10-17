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
