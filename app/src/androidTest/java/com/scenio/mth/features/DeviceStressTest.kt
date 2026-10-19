package com.scenio.mth.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scenio.mth.ForgeCanvas
import com.scenio.mth.RevealVideo
import com.scenio.mth.editor.PortableProject
import com.scenio.mth.inference.DeviceDiagnostics
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class DeviceStressTest {
    @Test(timeout=900_000) fun repeatedEncoderAndBoundedLayerStress() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("Opt-in physical stress test",args.getString("runDeviceStress")=="true")
        val qemu=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText().trim() } }
        assertNotEquals("Use a physical device", "1",qemu)
        val context=ApplicationProvider.getApplicationContext<Context>();val directory=File(context.filesDir,"device-test").apply { mkdirs() }
        val results=JSONArray();val report=JSONObject().put("schema",1).put("run_id",args.getString("validationRunId") ?: java.util.UUID.randomUUID().toString()).put("status","running").put("build_version",com.scenio.mth.BuildConfig.VERSION_NAME).put("platform","android").put("physical_device_requested",true).put("cases",results).put("manual_accessibility_verified",false).put("os_pressure_simulated",false)
        fun write() { File(directory,"stress.json").writeText(report.toString(2)) }
        write();var complete=false
        val base=Bitmap.createBitmap(512,512,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            repeat(2) { index ->
                val before=DeviceDiagnostics.snapshot(context);require(before.getLong("free_storage_bytes")>256_000_000 && !before.getBoolean("os_low_memory"))
                val result=JSONObject().put("case","encoder").put("iteration",index).put("before",before).put("status","running");results.put(result);write()
                val output=File(directory,"encoder-$index.mp4");val started=SystemClock.elapsedRealtime()
                try {
                    RevealVideo.render(base,output) {
                        val state=DeviceDiagnostics.snapshot(context)
                        check(!state.getBoolean("os_low_memory") && state.optInt("thermal_status",0)<3) { "Stop: low memory or severe thermal state" }
                    }
                    val extractor=MediaExtractor()
                    try {
                        extractor.setDataSource(output.path);val track=(0 until extractor.trackCount).first { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true }
                        val format=extractor.getTrackFormat(track);extractor.selectTrack(track)
                        assertEquals(1920,format.getInteger(MediaFormat.KEY_WIDTH));assertEquals(1080,format.getInteger(MediaFormat.KEY_HEIGHT))
                        assertTrue(kotlin.math.abs(format.getLong(MediaFormat.KEY_DURATION)-30_000_000L)<100_000L)
                        var samples=0;while(extractor.sampleTime>=0) { samples++;if(!extractor.advance()) break };assertEquals(720,samples)
                        result.put("frames",samples).put("status","passed")
                    } finally { extractor.release() }
                } catch(error:Throwable) { result.put("status","failed").put("error_type",error.javaClass.simpleName);throw error }
                finally { result.put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("after",DeviceDiagnostics.snapshot(context));write();output.delete() }
            }
            // Bounded repeated document allocation; this is not OS low-memory certification.
            repeat(5) {
                val layers=(0 until 16).map { i -> ForgeCanvas.InkLayer("layer-$i","Layer $i") }
                val snapshot=ForgeCanvas.Snapshot(base,emptyList(),layers,layers[0].id)
                val decoded=PortableProject.decode(PortableProject.encode(snapshot,JSONObject()).byteInputStream())
                assertEquals(16,decoded.snapshot.layers.size);decoded.snapshot.base?.recycle();decoded.snapshot.layers.forEach { it.raster?.recycle() }
            }
            results.put(JSONObject().put("case","five_16_layer_roundtrips").put("status","passed").put("after",DeviceDiagnostics.snapshot(context)));complete=true;write()
        } finally { report.put("status",if(complete) "completed" else "failed");write();base.recycle() }
    }
}
