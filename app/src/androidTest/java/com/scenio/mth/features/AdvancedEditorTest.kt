package com.scenio.mth.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Path
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scenio.mth.ForgeCanvas
import com.scenio.mth.editor.VectorExporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID
import java.io.StringWriter

@RunWith(AndroidJUnit4::class)
class AdvancedEditorTest {
    private fun bitmap(color:Int)=Bitmap.createBitmap(200,200,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
    @Test fun maskCanBeDisabledOrRepaintedWithoutDestroyingRaster() {
        val base=bitmap(Color.WHITE);val raster=bitmap(Color.RED)
        val layer=ForgeCanvas.InkLayer(UUID.randomUUID().toString(),"Photo",raster=raster)
        val hide=ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.5f,.5f)),Color.BLACK,.05f,layer.id,"mask-hide")
        val snapshot=ForgeCanvas.Snapshot(base,listOf(hide),listOf(layer),layer.id)
        fun center(s:ForgeCanvas.Snapshot):Int { val rendered=ForgeCanvas.render(s);return try { rendered.getPixel(100,100) } finally { rendered.recycle() } }
        try {
            assertEquals(Color.WHITE,center(snapshot))
            assertEquals(Color.RED,center(snapshot.copy(layers=listOf(layer.copy(maskEnabled=false)))))
            assertEquals(Color.RED,center(snapshot.copy(strokes=listOf(hide,hide.copy(kind="mask-show")))))
            assertEquals(Color.RED,raster.getPixel(100,100))
        } finally { base.recycle();raster.recycle() }
    }
    @Test fun transformsAndGuidesRoundTripWhileGuidesStayOutOfExport() {
        val base=bitmap(Color.WHITE);val raster=bitmap(Color.RED)
        val layer=ForgeCanvas.InkLayer(UUID.randomUUID().toString(),"Photo",raster=raster,offsetX=.25f,scale=.5f,rotation=90f)
        val snapshot=ForgeCanvas.Snapshot(base,emptyList(),listOf(layer),layer.id,ForgeCanvas.Guides(10,true,"both",true,.8f))
        try {
            val decoded=ForgeCanvas.decodeDocument(ForgeCanvas.encodeInk(snapshot)) { raster }
            assertEquals(snapshot.guides,decoded.guides);assertEquals(layer,decoded.layers.single())
            val rendered=ForgeCanvas.render(snapshot)
            try { assertEquals(Color.RED,rendered.getPixel(150,100));assertEquals(Color.WHITE,rendered.getPixel(25,100)) } finally { rendered.recycle() }
            val withGuides=ForgeCanvas.render(snapshot);val without=ForgeCanvas.render(snapshot.copy(guides=ForgeCanvas.Guides()))
            try { assertTrue(withGuides.sameAs(without)) } finally { withGuides.recycle();without.recycle() }
            val streamed=StringWriter().also { VectorExporter.write(snapshot,it) }.toString()
            assertEquals(VectorExporter.svg(snapshot),streamed)
            assertTrue(streamed.contains("rotate(90.000)"));assertEquals(2,"<image ".toRegex().findAll(streamed).count())
        } finally { base.recycle();raster.recycle() }
    }
    @Test fun layerTransformsDeletionAndBooleanOperationsAreUndoable() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context=ApplicationProvider.getApplicationContext<Context>();val canvas=ForgeCanvas(context);val base=bitmap(Color.WHITE)
            try {
                canvas.image=base;canvas.addLayer();val original=canvas.layerList().last()
                canvas.updateLayer(original.copy(offsetX=.3f));canvas.undo();assertEquals(0f,canvas.layerList().last().offsetX,0f)
                canvas.redo();assertEquals(.3f,canvas.layerList().last().offsetX,0f)
                canvas.deleteLayer(original.id);canvas.undo();assertEquals(2,canvas.layerList().size)
                val rectangle=ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.1f,.1f),ForgeCanvas.Point(.6f,.6f)),Color.BLUE,.01f,kind="rectangle")
                canvas.restore(ForgeCanvas.Snapshot(base,listOf(rectangle,rectangle.copy(points=mutableListOf(ForgeCanvas.Point(.4f,.4f),ForgeCanvas.Point(.8f,.8f))))))
                canvas.combineShapes(Path.Op.UNION);assertEquals("filled-path",canvas.snapshot().strokes.single().kind)
                canvas.undo();assertEquals(2,canvas.snapshot().strokes.size);canvas.redo();assertEquals(1,canvas.snapshot().strokes.size)
            } finally { base.recycle() }
        }
    }
    @Test fun symmetryCreatesEditableMirroredMarksAndOneUndoRemovesGesture() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val canvas=ForgeCanvas(ApplicationProvider.getApplicationContext());val base=bitmap(Color.WHITE)
            try {
                canvas.layout(0,0,200,200);canvas.image=base;canvas.drawing=true;canvas.configureGuides(ForgeCanvas.Guides(symmetry="both"))
                for(action in listOf(MotionEvent.ACTION_DOWN,MotionEvent.ACTION_UP)) {
                    val event=MotionEvent.obtain(1,2,action,50f,50f,0);try { assertTrue(canvas.onTouchEvent(event)) } finally { event.recycle() }
                }
                assertEquals(4,canvas.snapshot().strokes.size)
                assertEquals(setOf(.25f,.75f),canvas.snapshot().strokes.map { it.points.first().x }.toSet())
                canvas.undo();assertTrue(canvas.snapshot().strokes.isEmpty());canvas.redo();assertEquals(4,canvas.snapshot().strokes.size)
            } finally { base.recycle() }
        }
    }
    @Test fun malformedRasterReferencesAndTransformsAreRejected() {
        val valid="""{"version":4,"layers":[{"id":"ink","name":"Ink","visible":true,"opacity":1,"blend":"normal"}],"strokes":[]}"""
        assertThrows(IllegalArgumentException::class.java) { ForgeCanvas.decodeDocument(valid.replace("\"blend\":\"normal\"","\"blend\":\"normal\",\"raster\":\"../escape.png\"")) }
        assertThrows(IllegalArgumentException::class.java) { ForgeCanvas.decodeDocument(valid.replace("\"blend\":\"normal\"","\"blend\":\"normal\",\"scale\":0")) }
        assertEquals(1,ForgeCanvas.decodeDocument(valid.replace("\"version\":4","\"version\":3")).layers.size)
    }
}
