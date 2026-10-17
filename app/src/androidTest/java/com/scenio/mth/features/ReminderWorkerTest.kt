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
