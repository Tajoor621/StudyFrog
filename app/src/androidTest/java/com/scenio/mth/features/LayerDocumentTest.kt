package com.scenio.mth.features
import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.ForgeCanvas
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LayerDocumentTest {
    @Test fun erasingInkPreservesBasePixelsAndLayerMetadataRoundTrips() {
        val base=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val stroke=ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.5f,.5f)),Color.RED,.05f,"labels")
        val layer=ForgeCanvas.InkLayer("labels","My labels",true,.5f,"normal")
        val snapshot=ForgeCanvas.Snapshot(base,listOf(stroke),listOf(layer),"labels")
        val rendered=ForgeCanvas.render(snapshot)
        try { assertTrue(Color.green(rendered.getPixel(128,128)) in 125..130) } finally { rendered.recycle() }
        val erased=ForgeCanvas.render(snapshot.copy(strokes=listOf(stroke,stroke.copy(kind="erase"))))
        try { assertEquals(Color.WHITE,erased.getPixel(128,128)) } finally { erased.recycle() }
        val restored=ForgeCanvas.decodeDocument(ForgeCanvas.encodeInk(snapshot))
        assertEquals(layer,restored.layers.single());assertEquals("labels",restored.strokes.single().layerId)
        base.recycle()
    }
    @Test fun legacyInkAndHiddenLayersRemainSupported() {
        val legacy="[{\"color\":-65536,\"size\":0.01,\"points\":[[0.5,0.5,1.0]]}]"
        assertEquals("ink",ForgeCanvas.decodeDocument(legacy).strokes.single().layerId)
        val base=Bitmap.createBitmap(256,256,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        val snapshot=ForgeCanvas.Snapshot(base,ForgeCanvas.decodeInk(legacy),listOf(ForgeCanvas.InkLayer("ink","Hidden",false)))
        val rendered=ForgeCanvas.render(snapshot)
        try { assertEquals(Color.WHITE,rendered.getPixel(128,128)) } finally { base.recycle();rendered.recycle() }
    }
}
