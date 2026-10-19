package com.scenio.mth.features

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.inference.DeviceDiagnostics
import com.scenio.mth.inference.ModelStore
import com.scenio.mth.inference.NativeEngine
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Opt-in physical measurements; every call includes runtime model loading. */
@RunWith(AndroidJUnit4::class)
class RealModelDeviceTest {
    @Test(timeout=3_600_000) fun benchmarkAnExplicitlySuppliedLocalModel() {
        val args=InstrumentationRegistry.getArguments()
        assumeTrue("A physical device/model must be explicitly configured",args.getString("runRealInference")=="true")
        val context=ApplicationProvider.getApplicationContext<Context>()
        val directory=File(context.filesDir,"device-test").apply { mkdirs() }
        val runId=args.getString("validationRunId") ?: java.util.UUID.randomUUID().toString()
        File(directory,"report.json").writeText(JSONObject().put("schema",1).put("run_id",runId).put("platform","android").put("status","preflight_failed").put("build_version",com.scenio.mth.BuildConfig.VERSION_NAME).put("attempts",JSONArray()).toString())
        val qemu=InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand("getprop ro.kernel.qemu").use { android.os.ParcelFileDescriptor.AutoCloseInputStream(it).bufferedReader().use { reader -> reader.readText().trim() } }
        assertNotEquals("Use a physical phone", "1",qemu)
        val expected=requireNotNull(args.getString("expectedModelSha256"));require(expected.matches(Regex("[0-9a-f]{64}")))
        val size=args.getString("benchmarkSize","256")!!.toInt();require(size in listOf(256,512))
        val repeats=args.getString("benchmarkRepeats","2")!!.toInt();require(repeats in 1..3)
        val model=ModelStore(context).model
        assertTrue("Import your licensed SD 1.x GGUF first",model.isFile)
        val digest=MessageDigest.getInstance("SHA-256");model.inputStream().use { stream -> val buffer=ByteArray(1_048_576);while(true) { val n=stream.read(buffer);if(n<0) break;digest.update(buffer,0,n) } }
        val actual=digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) };assertEquals("Imported model must match the selected benchmark model",expected,actual)
        val attempts=JSONArray();var runStatus="running"
        val prompts=listOf("botany" to "A scientific botanical illustration of a green fern on white paper", "product" to "A realistic red ceramic mug on a neutral table, soft daylight")
        fun write() { File(directory,"report.json").writeText(JSONObject().put("schema",1).put("run_id",runId).put("status",runStatus).put("platform","android").put("physical_device",true).put("build_version",com.scenio.mth.BuildConfig.VERSION_NAME).put("physical_device_requested",true).put("model_sha256",actual).put("device",Build.MODEL).put("manufacturer",Build.MANUFACTURER).put("sdk",Build.VERSION.SDK_INT).put("abi",Build.SUPPORTED_ABIS.firstOrNull()).put("runtime_commit","f0aac524d3f29140e60ba78f3cfb16d6acea87db").put("call_includes_model_load",true).put("quality_assessed",false).put("attempts",attempts).toString(2)) }
        try {
        for((case,prompt) in prompts) repeat(repeats) { iteration ->
            val before=DeviceDiagnostics.snapshot(context)
            require(!before.getBoolean("os_low_memory") && before.getLong("total_ram_bytes") >= if(size==512) 7_500_000_000L else 5_500_000_000L)
            require(before.getLong("available_ram_bytes") >= if(size==512) 4_000_000_000L else 2_000_000_000L)
            require(before.optInt("thermal_status",0)<3) { "Cool the device before benchmarking" }
            val requestToken=java.util.UUID.randomUUID().mostSignificantBits.let { if(it==0L) 1L else it }
            val peak=AtomicLong(before.getLong("process_pss_bytes"));val aborted=AtomicBoolean(false)
            val sampler=Executors.newSingleThreadScheduledExecutor();val started=SystemClock.elapsedRealtime()
            val attempt=JSONObject().put("case",case).put("iteration",iteration).put("seed",42).put("size",size).put("steps",10).put("before",before).put("status","running")
            attempts.put(attempt);write()
            sampler.scheduleAtFixedRate({ runCatching {
                val sample=DeviceDiagnostics.snapshot(context);peak.updateAndGet { maxOf(it,sample.getLong("process_pss_bytes")) }
                if(sample.getBoolean("os_low_memory") || sample.optInt("thermal_status",0)>=3 || SystemClock.elapsedRealtime()-started>900_000) { aborted.set(true);NativeEngine.cancelRequest(requestToken) }
            } },0,500,TimeUnit.MILLISECONDS)
            try {
                val pixels=NativeEngine.generate(model.path.toByteArray(),prompt.toByteArray(),"blurry, distorted".toByteArray(),size,10,42,.65f,null,requestToken)
                check(!aborted.get()) { "Run stopped for thermal, memory or time limits" };assertEquals(size*size,pixels.size);assertTrue(pixels.toSet().size>16)
                val image=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888)
                try { File(directory,"$case-$iteration.png").outputStream().use { assertTrue(image.compress(Bitmap.CompressFormat.PNG,100,it)) } } finally { image.recycle() }
                attempt.put("status","passed")
            } catch(error:Throwable) { attempt.put("status","failed").put("error_type",error.javaClass.simpleName);throw error }
            finally { sampler.shutdownNow();sampler.awaitTermination(5,TimeUnit.SECONDS);attempt.put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("sampled_peak_pss_bytes",peak.get()).put("after",DeviceDiagnostics.snapshot(context));write() }
        }
        runStatus="completed"
        } finally { if(runStatus!="completed") runStatus="failed";write() }
    }
}
