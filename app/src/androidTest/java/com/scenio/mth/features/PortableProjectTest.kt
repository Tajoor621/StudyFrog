package com.scenio.mth.features

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scenio.mth.ForgeCanvas
import com.scenio.mth.editor.PortableProject
import com.scenio.mth.editor.ProjectStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PortableProjectTest {
    private fun fixture() = InstrumentationRegistry.getInstrumentation().context.assets.open("portable-project-v1.json").bufferedReader().use { it.readText() }
    private fun decode(json: String) = PortableProject.decode(json.byteInputStream())
    private fun recycle(snapshot: ForgeCanvas.Snapshot) { snapshot.base?.recycle();snapshot.layers.forEach { it.raster?.recycle() } }
    @Test fun sharedFixturePreservesLayersPixelsTransformsAndNativeSave() {
        val document=decode(fixture());val snapshot=document.snapshot
        val store=ProjectStore(InstrumentationRegistry.getInstrumentation().targetContext)
        var id:String?=null
        try {
            assertEquals("Biology",document.metadata.getString("folder"));assertEquals(2,snapshot.layers.size)
            assertEquals(snapshot.layers[1].id,snapshot.activeLayer);assertFalse(snapshot.layers[0].visible)
            assertEquals(.2f,snapshot.layers[0].offsetX,0f);assertEquals(90f,snapshot.layers[0].rotation,0f)
            assertEquals("screen",snapshot.layers[0].blend);assertEquals(.4f,snapshot.layers[0].opacity,0f)
            assertEquals(Color.BLUE,snapshot.layers[1].raster!!.getPixel(0,0))
            val composite=ForgeCanvas.render(snapshot)
            try { val pixel=composite.getPixel(1,1);assertTrue(Color.red(pixel) in 126..129);assertEquals(255,Color.blue(pixel)) } finally { composite.recycle() }
            val roundTrip=decode(PortableProject.encode(snapshot,document.metadata))
            try { assertEquals("Hidden photo",roundTrip.snapshot.layers[0].name);assertEquals(.8f,roundTrip.snapshot.layers[0].scale,0f);assertEquals(2,roundTrip.snapshot.layers.size) } finally { recycle(roundTrip.snapshot) }
            val saved=store.save(snapshot,document.metadata);id=saved
            val loaded=store.load(saved);try { assertEquals(2,loaded.snapshot.layers.size);assertEquals("Biology",loaded.info.folder);assertEquals("multiply",loaded.snapshot.layers[1].blend) } finally { recycle(loaded.snapshot) }
        } finally { recycle(snapshot);id?.let { store.delete(it) } }
    }
    @Test fun damagedAssetsVersionsDimensionsAndSettingsAreRejected() {
        val changes:List<(JSONObject)->Unit> = listOf(
            { it.put("version",2) },{ it.put("selected",2) },
            { it.getJSONObject("base").put("sha256","0".repeat(64)) },
            { it.getJSONObject("base").put("width",2049) },
            { it.getJSONObject("base").put("width",1) },
            { it.getJSONArray("layers").getJSONObject(0).put("blend","unsupported") },
            { it.getJSONArray("layers").getJSONObject(0).put("opacity",2) },
            { it.getJSONArray("layers").getJSONObject(0).getJSONObject("transform").put("scale",0) },
            { it.put("folder","bad\nfolder") },{ it.put("layers",org.json.JSONArray()) }
        )
        for(change in changes) {
            val json=JSONObject(fixture()).also(change).toString()
            assertTrue(runCatching { decode(json).also { recycle(it.snapshot) } }.isFailure)
        }
        assertTrue(runCatching { decode("[".repeat(17)+"]".repeat(17)) }.isFailure)
    }
    @Test fun exportingBakesInkAndMaskButDoesNotMutateOriginalDocument() {
        val source=decode(fixture());val layer=source.snapshot.layers[1]
        val stroke=ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.5f,.5f)),Color.RED,.05f,layer.id)
        val snapshot=source.snapshot.copy(strokes=listOf(stroke))
        try {
            val imported=decode(PortableProject.encode(snapshot,source.metadata))
            try { assertTrue(imported.snapshot.strokes.isEmpty());assertNotNull(imported.snapshot.layers[1].raster);assertEquals(listOf(stroke),snapshot.strokes);assertEquals(Color.BLUE,layer.raster!!.getPixel(0,0)) } finally { recycle(imported.snapshot) }
        } finally { recycle(source.snapshot) }
    }
}
