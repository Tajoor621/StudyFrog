package com.scenio.mth.features

import android.os.ParcelFileDescriptor
import android.view.View
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Root
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.RootMatchers.withDecorView
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scenio.mth.science.StudyExplorerActivity
import org.hamcrest.Matcher
import org.hamcrest.Matchers.sameInstance
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StudyExplorerTest {
    private fun currentRoot(scenario:ActivityScenario<StudyExplorerActivity>):Matcher<Root> {
        lateinit var decor:View
        scenario.onActivity { decor=it.window.decorView }
        return withDecorView(sameInstance(decor))
    }
    @Test fun navigatingDeeperSurvivesActivityRecreation() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        for(command in listOf("input keyevent KEYCODE_WAKEUP","wm dismiss-keyguard")) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        ActivityScenario.launch(StudyExplorerActivity::class.java).use { scenario ->
            // Bind each interaction to this scenario's live window, including after
            // recreation; previous test activities can still have transient roots.
            onView(withText("Explore deeper: Cardiovascular system")).inRoot(currentRoot(scenario)).perform(scrollTo(),click())
            scenario.recreate()
            onView(withText("Cardiovascular system")).inRoot(currentRoot(scenario)).perform(scrollTo()).check(matches(isDisplayed()))
            onView(withText("Explore deeper: Heart: chamber flow")).inRoot(currentRoot(scenario)).perform(scrollTo(),click())
            onView(withText("Previous view: Cardiovascular system")).inRoot(currentRoot(scenario)).perform(scrollTo(),click())
            onView(withText("Cardiovascular system")).inRoot(currentRoot(scenario)).perform(scrollTo()).check(matches(isDisplayed()))
        }
    }
}
