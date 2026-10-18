package com.scenio.mth.editor

import android.graphics.Bitmap
import android.util.Base64
import android.util.Base64OutputStream
import com.scenio.mth.ForgeCanvas
import java.io.OutputStream
import java.io.Writer
import java.io.StringWriter
import java.util.Locale

object VectorExporter {
    private fun escape(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    fun svg(snapshot:ForgeCanvas.Snapshot)=StringWriter().also { write(snapshot,it) }.toString()
    /** Stream embedded bitmaps instead of holding a second full-size base64 copy in RAM. */
    fun write(snapshot:ForgeCanvas.Snapshot,out:Writer) {
        val base=requireNotNull(snapshot.base);val w=base.width.toFloat();val h=base.height.toFloat()
        fun emit(value:String) { out.write(value) }
        fun n(value:Float)=String.format(Locale.ROOT,"%.3f",value)
        fun image(bitmap:Bitmap) {
            emit("<image width=\"${base.width}\" height=\"${base.height}\" preserveAspectRatio=\"xMidYMid meet\" href=\"data:image/png;base64,")
            val bytes=object:OutputStream() {
                override fun write(value:Int) { out.write(value) }
                override fun write(data:ByteArray,offset:Int,length:Int) { out.write(String(data,offset,length,Charsets.US_ASCII)) }
            }
            Base64OutputStream(bytes,Base64.NO_WRAP or Base64.NO_CLOSE).use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) }
            emit("\"/>")
        }
        fun shape(s:ForgeCanvas.Stroke,maskColor:String?=null):String {
            val first=s.points.firstOrNull() ?: return "";val last=s.points.last()
            val color=maskColor ?: String.format(Locale.ROOT,"#%06x",s.color and 0xffffff)
            val opacity="opacity=\"${n(if(maskColor!=null) 1f else android.graphics.Color.alpha(s.color)/255f)}\""
            val style="$opacity stroke=\"$color\" stroke-width=\"${n(s.size*w)}\" stroke-linecap=\"round\" fill=\"none\""
            val x=first.x*w;val y=first.y*h;val ex=last.x*w;val ey=last.y*h
            return when(s.kind) {
                "text" -> "<text x=\"${n(x)}\" y=\"${n(y)}\" font-size=\"${n(s.size*w*8)}\" font-family=\"${s.font}\" fill=\"$color\" $opacity>${escape(s.text)}</text>"
                "line" -> "<line x1=\"${n(x)}\" y1=\"${n(y)}\" x2=\"${n(ex)}\" y2=\"${n(ey)}\" $style/>"
                "rectangle" -> "<rect x=\"${n(minOf(x,ex))}\" y=\"${n(minOf(y,ey))}\" width=\"${n(kotlin.math.abs(ex-x))}\" height=\"${n(kotlin.math.abs(ey-y))}\" $style/>"
                "ellipse" -> "<ellipse cx=\"${n((x+ex)/2)}\" cy=\"${n((y+ey)/2)}\" rx=\"${n(kotlin.math.abs(ex-x)/2)}\" ry=\"${n(kotlin.math.abs(ey-y)/2)}\" $style/>"
                "filled-path" -> {
                    val data=buildString { s.contourStarts.forEachIndexed { i,start ->
                        val end=s.contourStarts.getOrNull(i+1) ?: s.points.size
                        append("M${n(s.points[start].x*w)} ${n(s.points[start].y*h)}")
                        for(j in start+1 until end) append("L${n(s.points[j].x*w)} ${n(s.points[j].y*h)}")
                        append("Z")
                    } }
                    "<path d=\"$data\" fill-rule=\"evenodd\" fill=\"$color\" $opacity/>"
                }
                else -> "<circle cx=\"${n(x)}\" cy=\"${n(y)}\" r=\"${n(s.size*w*first.pressure/2)}\" fill=\"$color\" $opacity/>"+s.points.zipWithNext().joinToString("") { (a,b) -> "<line x1=\"${n(a.x*w)}\" y1=\"${n(a.y*h)}\" x2=\"${n(b.x*w)}\" y2=\"${n(b.y*h)}\" stroke=\"$color\" stroke-width=\"${n(s.size*w*b.pressure)}\" stroke-linecap=\"round\" $opacity/>" }
            }
        }
        fun startMask(id:String) { emit("<mask id=\"$id\" maskUnits=\"userSpaceOnUse\" x=\"0\" y=\"0\" width=\"${base.width}\" height=\"${base.height}\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/>") }
        emit("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${base.width}\" height=\"${base.height}\" viewBox=\"0 0 ${base.width} ${base.height}\">");image(base)
        snapshot.layers.filter { it.visible }.forEachIndexed { index,layer ->
            val strokes=snapshot.strokes.filter { it.layerId==layer.id };val marks=strokes.filter { !it.kind.startsWith("mask-") }
            val erasers=marks.indices.filter { marks[it].kind=="erase" };val masks=strokes.filter { it.kind.startsWith("mask-") }
            emit("<defs>")
            erasers.forEach { i -> startMask("erase-$index-$i");emit(shape(marks[i],"black"));emit("</mask>") }
            val masked=layer.maskEnabled && masks.isNotEmpty()
            if(masked) { startMask("layer-mask-$index");masks.forEach { emit(shape(it,if(it.kind=="mask-hide") "black" else "white")) };emit("</mask>") }
            emit("</defs><g opacity=\"${n(layer.opacity)}\" style=\"mix-blend-mode:${layer.blend}\"><g transform=\"translate(${n(w/2+layer.offsetX*w)} ${n(h/2+layer.offsetY*h)}) rotate(${n(layer.rotation)}) scale(${n(layer.scale)}) translate(${n(-w/2)} ${n(-h/2)})\">")
            if(masked) emit("<g mask=\"url(#layer-mask-$index)\">")
            erasers.reversed().forEach { i -> emit("<g mask=\"url(#erase-$index-$i)\">") }
            layer.raster?.let { image(it) }
            marks.forEach { if(it.kind=="erase") emit("</g>") else emit(shape(it)) }
            if(masked) emit("</g>")
            emit("</g></g>")
        }
        emit("</svg>");out.flush()
    }
}
