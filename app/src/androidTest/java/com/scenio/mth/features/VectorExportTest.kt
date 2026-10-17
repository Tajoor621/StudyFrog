package com.scenio.mth.features

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.ForgeCanvas
import com.scenio.mth.editor.VectorExporter
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(AndroidJUnit4::class)
class VectorExportTest {
    @Test fun svgRetainsColorOpacityPressureAndEscapedLabelContent() {
        val base=Bitmap.createBitmap(100,100,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }
        try {
            val ink=Color.argb(128,255,0,0)
            val strokes=listOf(
                ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.1f,.1f,.5f)),ink,.04f),
                ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.2f,.2f,.5f),ForgeCanvas.Point(.3f,.3f,1.5f)),ink,.04f),
                ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.4f,.4f)),ink,.004f,kind="text",text="A < B & C")
            )
            val xml=VectorExporter.svg(ForgeCanvas.Snapshot(base,strokes))
            val document=DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xml.byteInputStream())
            val texts=document.getElementsByTagName("text")
            assertEquals("A < B & C",texts.item(0).textContent)
            assertEquals("0.502",texts.item(0).attributes.getNamedItem("opacity").nodeValue)
            val circles=document.getElementsByTagName("circle")
            assertEquals(2,circles.length)
            assertEquals("1.000",circles.item(0).attributes.getNamedItem("r").nodeValue)
            assertEquals("0.502",circles.item(1).attributes.getNamedItem("opacity").nodeValue)
            val line=document.getElementsByTagName("line").item(0)
            assertEquals("6.000",line.attributes.getNamedItem("stroke-width").nodeValue)
            assertEquals("0.502",line.attributes.getNamedItem("opacity").nodeValue)
        } finally { base.recycle() }
    }
}
