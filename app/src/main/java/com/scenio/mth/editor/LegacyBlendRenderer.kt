package com.scenio.mth.editor

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import com.scenio.mth.ForgeCanvas
import kotlin.math.roundToInt

/** Source-over multiply for API 24–28; PorterDuff.MULTIPLY instead modulates alpha. */
object LegacyBlendRenderer {
    fun render(snapshot:ForgeCanvas.Snapshot,width:Int,height:Int):Bitmap {
        val output=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(output);val bounds=RectF(0f,0f,width.toFloat(),height.toFloat())
        try {
            snapshot.base?.let { canvas.drawBitmap(it,null,bounds,Paint(Paint.FILTER_BITMAP_FLAG)) }
            snapshot.layers.filter { it.visible && it.opacity>0 }.forEach { layer ->
                val document=snapshot.copy(base=null,layers=listOf(layer))
                if(layer.blend!="multiply") LayerRenderer.draw(canvas,document,bounds)
                else {
                    val source=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                    try {
                        LayerRenderer.draw(Canvas(source),document.copy(layers=listOf(layer.copy(blend="normal"))),bounds)
                        multiply(output,source)
                    } finally { source.recycle() }
                }
            }
            return output
        } catch(e:Exception) { output.recycle();throw e }
    }
    private fun multiply(destination:Bitmap,source:Bitmap) {
        val width=destination.width;val rows=minOf(64,destination.height)
        val src=IntArray(width*rows);val dst=IntArray(width*rows)
        for(y in 0 until destination.height step rows) {
            val count=minOf(rows,destination.height-y)
            source.getPixels(src,0,width,0,y,width,count);destination.getPixels(dst,0,width,0,y,width,count)
            for(i in 0 until width*count) {
                val s=src[i];val d=dst[i];val sa=(s ushr 24)/255f;val da=(d ushr 24)/255f
                val alpha=sa+da*(1-sa)
                fun channel(shift:Int):Int {
                    val sc=(s ushr shift) and 255;val dc=(d ushr shift) and 255
                    return if(alpha==0f) 0 else ((sc*sa*(1-da)+dc*da*(1-sa)+sc*dc/255f*sa*da)/alpha).roundToInt().coerceIn(0,255)
                }
                dst[i]=((alpha*255).roundToInt().coerceIn(0,255) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
            }
            destination.setPixels(dst,0,width,0,y,width,count)
        }
    }
}
