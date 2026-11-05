package com.scenio.mth.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scenio.mth.video.LocalVideoRemix
import com.scenio.mth.video.PhoneRenderRecords
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhoneRenderRecordsTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test(timeout=180000) fun retainedVideoSurvivesFailedAndInterruptedAttempt() {
        val root=File(context.cacheDir,"journal-${UUID.randomUUID()}").apply { mkdirs() }
        val records=PhoneRenderRecords(context,root)
        val bg=Bitmap.createBitmap(160,96,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            val success=records.begin("green",0.0,1.0)
            InstrumentationRegistry.getInstrumentation().context.assets.open("phone-source.mp4").use { input -> success.source.outputStream().use { input.copyTo(it) } }
            LocalVideoRemix.render(context,success.source,bg,success.output,false,0.0,1.0,{}, {})
            records.complete(success)
            assertEquals(success.output,records.latest());assertFalse(success.source.exists())
            val report=JSONObject(records.reportFile.readText())
            assertEquals("done",report.getString("status"));assertFalse(report.getBoolean("physical_execution_verified"));assertFalse(report.getBoolean("quality_assessed"))
            assertTrue(report.getLong("elapsed_ms")>=0);assertTrue(report.getJSONObject("before").has("process_pss_bytes"))
            val failed=records.begin("green",0.0,1.0);failed.source.writeText("source");failed.output.writeText("partial")
            records.fail(failed,false,"SyntheticFailure");assertEquals(success.output,records.latest());assertFalse(failed.source.exists());assertFalse(failed.output.exists())
            val interrupted=records.begin("green",0.0,1.0);interrupted.source.writeText("source");interrupted.output.writeText("partial")
            records.recover();assertEquals("running",JSONObject(records.reportFile.readText()).getString("status"));assertTrue(interrupted.source.exists())
            val restarted=PhoneRenderRecords(context,root);restarted.recover()
            assertEquals("interrupted",JSONObject(restarted.reportFile.readText()).getString("status"));assertTrue(JSONObject(restarted.reportFile.readText()).isNull("elapsed_ms"))
            assertFalse(interrupted.source.exists());assertFalse(interrupted.output.exists());assertEquals(success.output,restarted.latest())
            restarted.clear();assertNull(restarted.latest());assertFalse(restarted.reportFile.exists());assertFalse(success.output.exists())
        } finally { bg.recycle();root.deleteRecursively() }
    }
    @Test fun corruptReceiptCannotEscapeThePrivateDirectory() {
        val root=File(context.cacheDir,"journal-${UUID.randomUUID()}").apply { mkdirs() }
        val outside=File(context.cacheDir,"outside-${UUID.randomUUID()}.mp4").apply { writeText("original") }
        try {
            File(root,"latest.json").writeText(JSONObject().put("schema",1).put("run_id","../${outside.name}").put("status","done").toString())
            val records=PhoneRenderRecords(context,root)
            assertThrows(IllegalArgumentException::class.java) { records.latest() }
            assertEquals("original",outside.readText());records.clear();assertEquals("original",outside.readText());assertNull(records.latest())
        } finally { root.deleteRecursively();outside.delete() }
    }
}
