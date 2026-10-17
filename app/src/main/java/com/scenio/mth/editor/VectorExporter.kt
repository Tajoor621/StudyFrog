package com.scenio.mth.editor

import android.graphics.Bitmap
import android.util.Base64
import com.scenio.mth.ForgeCanvas
import java.io.ByteArrayOutputStream
import java.util.Locale

object VectorExporter {
    private fun escape(value:String)=value.replace("&","&amp;").replace("<","&lt;").replace(">","&gt;").replace("\"","&quot;")
    fun svg(snapshot:ForgeCanvas.Snapshot):String {
        val base=requireNotNull(snapshot.base)
        val png=ByteArrayOutputStream().use { check(base.compress(Bitmap.CompressFormat.PNG,100,it));Base64.encodeToString(it.toByteArray(),Base64.NO_WRAP) }
        val w=base.width.toFloat();val h=base.height.toFloat()
        fun n(value:Float)=String.format(Locale.ROOT,"%.3f",value)
        fun shape(stroke:ForgeCanvas.Stroke,mask:Boolean=false):String {
            val first=stroke.points.firstOrNull() ?: return "";val last=stroke.points.last()
            val color=if(mask) "black" else String.format(Locale.ROOT,"#%06x",stroke.color and 0xffffff)
            val style="stroke=\"$color\" stroke-width=\"${n(stroke.size*w)}\" stroke-linecap=\"round\" fill=\"none\""
            val x=first.x*w;val y=first.y*h;val ex=last.x*w;val ey=last.y*h
            return when(stroke.kind) {
                "text" -> "<text x=\"${n(x)}\" y=\"${n(y)}\" font-size=\"${n(stroke.size*w*8)}\" font-family=\"sans-serif\" fill=\"$color\">${escape(stroke.text)}</text>"
                "line" -> "<line x1=\"${n(x)}\" y1=\"${n(y)}\" x2=\"${n(ex)}\" y2=\"${n(ey)}\" $style/>"
                "rectangle" -> "<rect x=\"${n(minOf(x,ex))}\" y=\"${n(minOf(y,ey))}\" width=\"${n(kotlin.math.abs(ex-x))}\" height=\"${n(kotlin.math.abs(ey-y))}\" $style/>"
                "ellipse" -> "<ellipse cx=\"${n((x+ex)/2)}\" cy=\"${n((y+ey)/2)}\" rx=\"${n(kotlin.math.abs(ex-x)/2)}\" ry=\"${n(kotlin.math.abs(ey-y)/2)}\" $style/>"
                else -> if(stroke.points.size==1) "<circle cx=\"${n(x)}\" cy=\"${n(y)}\" r=\"${n(stroke.size*w/2)}\" fill=\"$color\"/>" else stroke.points.zipWithNext().joinToString("") { (a,b) -> "<line x1=\"${n(a.x*w)}\" y1=\"${n(a.y*h)}\" x2=\"${n(b.x*w)}\" y2=\"${n(b.y*h)}\" stroke=\"$color\" stroke-width=\"${n(stroke.size*w*b.pressure)}\" stroke-linecap=\"round\"/>" }
            }
        }
        return buildString {
            append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${base.width}\" height=\"${base.height}\" viewBox=\"0 0 ${base.width} ${base.height}\"><image width=\"100%\" height=\"100%\" href=\"data:image/png;base64,$png\"/>")
            snapshot.layers.filter { it.visible }.forEachIndexed { index,layer ->
                val strokes=snapshot.strokes.filter { it.layerId==layer.id }
                // Apply each erase only to earlier marks, preserving the raster and later ink.
                append("<g opacity=\"${n(layer.opacity)}\" style=\"mix-blend-mode:${layer.blend}\">")
                var content=""
                strokes.forEachIndexed { i,stroke ->
                    if(stroke.kind=="erase") { val id="erase-$index-$i";append("<defs><mask id=\"$id\" maskUnits=\"userSpaceOnUse\" x=\"0\" y=\"0\" width=\"${base.width}\" height=\"${base.height}\"><rect width=\"100%\" height=\"100%\" fill=\"white\"/>${shape(stroke,true)}</mask></defs>");content="<g mask=\"url(#$id)\">$content</g>" }
                    else content+=shape(stroke)
                }
                append(content);append("</g>")
            }
            append("</svg>")
        }
    }
}
