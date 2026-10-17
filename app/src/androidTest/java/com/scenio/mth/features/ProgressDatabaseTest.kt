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
