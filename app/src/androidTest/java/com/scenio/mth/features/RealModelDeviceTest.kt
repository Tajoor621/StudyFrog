package com.scenio.mth.features

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.inference.ModelStore
import com.scenio.mth.inference.NativeEngine
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Opt-in only. Ordinary emulator CI never claims to test real model quality. */
@RunWith(AndroidJUnit4::class)
class RealModelDeviceTest {
    @Test(timeout=1_200_000) fun generateFromAnExplicitlySuppliedLocalModel() {
        assumeTrue("A real device/model must be explicitly configured",InstrumentationRegistry.getArguments().getString("runRealInference")=="true")
        val context=ApplicationProvider.getApplicationContext<Context>()
        val model=ModelStore(context).model
        assertTrue("Import your licensed SD 1.x GGUF first",model.isFile)
        val started=SystemClock.elapsedRealtime()
        val pixels=NativeEngine.generate(model.path.toByteArray(),"A scientific botanical illustration of a green fern on white paper".toByteArray(),"blurry, distorted".toByteArray(),256,10,42,.65f,null)
        assertEquals(256*256,pixels.size)
        assertTrue("The model returned a constant image",pixels.distinct().size>16)
        val directory=File(context.filesDir,"device-test").apply { mkdirs() }
        val bitmap=Bitmap.createBitmap(pixels,256,256,Bitmap.Config.ARGB_8888)
        try { File(directory,"image.png").outputStream().use { assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
        File(directory,"report.json").writeText(JSONObject().put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("sdk",android.os.Build.VERSION.SDK_INT).put("device",android.os.Build.MODEL).put("seed",42).put("steps",10).put("width",256).put("height",256).put("quality_assessed",false).toString(2))
    }
}
