package com.scenio.mth.features

import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.view.WindowManager
import android.util.Log
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
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val deadline=SystemClock.uptimeMillis()+15000
        do {
            var decor:View?=null;var ready=false
            scenario.onActivity { activity ->
                // A long native-codec suite can outlast the emulator's screen timeout.
                // Keep only this test window awake and wait for real input focus.
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                decor=activity.window.decorView
                ready=decor!!.hasWindowFocus() && decor!!.isShown && !decor!!.isLayoutRequested
            }
            if(ready) return withDecorView(sameInstance(decor!!))
            instrumentation.waitForIdleSync();SystemClock.sleep(50)
        } while(SystemClock.uptimeMillis()<deadline)
        for(command in listOf("dumpsys window windows","dumpsys power")) {
            val output=ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand(command)).bufferedReader().use { it.readText() }
            Log.e("StudyExplorerWindow",output.lineSequence().filter { "Focus" in it || "Wakefulness" in it || "Keyguard" in it }.take(30).joinToString("\n"))
        }
        throw AssertionError("Study Explorer did not obtain a visible, laid-out, focused window")
    }
    @Test fun navigatingDeeperSurvivesActivityRecreation() {
        val automation=InstrumentationRegistry.getInstrumentation().uiAutomation
        for(command in listOf("input keyevent KEYCODE_WAKEUP","wm dismiss-keyguard","input keyevent KEYCODE_MENU")) {
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).use { it.readBytes() }
        }
        automation.waitForIdle(200,5000)
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
