package com.scenio.mth.features

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.science.StudyExplorerActivity
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyExplorerTest {
    @Test fun navigatingDeeperSurvivesActivityRecreation() {
        ActivityScenario.launch(StudyExplorerActivity::class.java).use { scenario ->
            onView(withText("Explore deeper: Cardiovascular system")).perform(scrollTo(),click())
            scenario.recreate()
            onView(withText("Cardiovascular system")).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Explore deeper: Heart: chamber flow")).perform(scrollTo(),click())
            onView(withText("Previous view: Cardiovascular system")).perform(scrollTo(),click())
            onView(withText("Cardiovascular system")).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }
}
