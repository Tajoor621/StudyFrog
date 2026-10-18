package com.scenio.mth.editor

import android.graphics.*
import com.scenio.mth.ForgeCanvas

object LayerRenderer {
    fun matrix(layer:ForgeCanvas.InkLayer,b:RectF)=Matrix().apply {
        postTranslate(-b.centerX(),-b.centerY());postScale(layer.scale,layer.scale);postRotate(layer.rotation)
        postTranslate(b.centerX()+layer.offsetX*b.width(),b.centerY()+layer.offsetY*b.height())
    }
    fun geometry(stroke:ForgeCanvas.Stroke,b:RectF):Path {
        val path=Path();val first=stroke.points.firstOrNull() ?: return path;val last=stroke.points.last()
        fun x(p:ForgeCanvas.Point)=b.left+p.x*b.width()
        fun y(p:ForgeCanvas.Point)=b.top+p.y*b.height()
        val rect=RectF(minOf(x(first),x(last)),minOf(y(first),y(last)),maxOf(x(first),x(last)),maxOf(y(first),y(last)))
        when(stroke.kind) {
            "rectangle" -> path.addRect(rect,Path.Direction.CW)
            "ellipse" -> path.addOval(rect,Path.Direction.CW)
            "filled-path" -> {
                path.fillType=Path.FillType.EVEN_ODD
                val starts=stroke.contourStarts.ifEmpty { listOf(0) }
                starts.forEachIndexed { i,start ->
                    val end=starts.getOrNull(i+1) ?: stroke.points.size
                    path.moveTo(x(stroke.points[start]),y(stroke.points[start]))
                    for(index in start+1 until end) path.lineTo(x(stroke.points[index]),y(stroke.points[index]))
                    path.close()
                }
            }
        }
        return path
    }
    fun mark(c:Canvas,s:ForgeCanvas.Stroke,b:RectF,p:Paint,mask:Boolean=false) {
        val first=s.points.firstOrNull() ?: return;val last=s.points.last()
        fun x(point:ForgeCanvas.Point)=b.left+point.x*b.width()
        fun y(point:ForgeCanvas.Point)=b.top+point.y*b.height()
        p.color=if(mask) Color.WHITE else s.color;p.style=Paint.Style.STROKE;p.strokeWidth=s.size*b.width()
        p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
        p.xfermode=if(s.kind=="erase" || s.kind=="mask-hide") PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
        when(s.kind) {
            "text" -> { p.style=Paint.Style.FILL;p.typeface=Typeface.create(s.font,Typeface.NORMAL);p.textSize=s.size*b.width()*8;c.drawText(s.text,x(first),y(first),p) }
            "line" -> c.drawLine(x(first),y(first),x(last),y(last),p)
            "rectangle","ellipse","filled-path" -> { if(s.kind=="filled-path") p.style=Paint.Style.FILL;c.drawPath(geometry(s,b),p) }
            else -> s.points.forEachIndexed { i,point ->
                p.strokeWidth=s.size*b.width()*point.pressure
                if(i==0) c.drawPoint(x(point),y(point),p) else c.drawLine(x(s.points[i-1]),y(s.points[i-1]),x(point),y(point),p)
            }
        }
        p.xfermode=null
    }
    fun draw(c:Canvas,snapshot:ForgeCanvas.Snapshot,b:RectF) {
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        if(android.os.Build.VERSION.SDK_INT<29 && snapshot.layers.any { it.visible && it.blend=="multiply" }) {
            val composite=LegacyBlendRenderer.render(snapshot,b.width().toInt().coerceAtLeast(1),b.height().toInt().coerceAtLeast(1))
            try { c.drawBitmap(composite,null,b,p) } finally { if(!c.isHardwareAccelerated) composite.recycle() };return
        }
        snapshot.base?.let { c.drawBitmap(it,null,b,p) }
        val clip=c.save();c.clipRect(b)
        for(layer in snapshot.layers.filter { it.visible && it.opacity>0 }) {
            val blend=Paint().apply {
                alpha=(layer.opacity*255).toInt()
                if(android.os.Build.VERSION.SDK_INT>=29 && layer.blend=="multiply") blendMode=BlendMode.MULTIPLY
                else xfermode=PorterDuffXfermode(if(layer.blend=="screen") PorterDuff.Mode.SCREEN else PorterDuff.Mode.SRC_OVER)
            }
            val save=c.saveLayer(b,blend);c.concat(matrix(layer,b))
            layer.raster?.let { image ->
                val fit=fitImage(b.width(),b.height(),image.width,image.height)
                p.style=Paint.Style.FILL;p.color=Color.WHITE;p.alpha=255;p.xfermode=null
                c.drawBitmap(image,null,RectF(b.left+fit.left,b.top+fit.top,b.left+fit.left+fit.width,b.top+fit.top+fit.height),p)
            }
            val strokes=snapshot.strokes.filter { it.layerId==layer.id }
            strokes.filter { !it.kind.startsWith("mask-") }.forEach { mark(c,it,b,p) }
            val masks=strokes.filter { it.kind.startsWith("mask-") }
            if(layer.maskEnabled && masks.isNotEmpty()) {
                val maskPaint=Paint().apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN) }
                val mask=c.saveLayer(b,maskPaint)
                p.color=Color.WHITE;p.style=Paint.Style.FILL;p.xfermode=null;c.drawRect(b,p)
                masks.forEach { mark(c,it,b,p,true) };c.restoreToCount(mask)
            }
            c.restoreToCount(save)
        }
        c.restoreToCount(clip)
    }
    fun render(snapshot:ForgeCanvas.Snapshot):Bitmap {
        val base=requireNotNull(snapshot.base) { "Import or generate an image first" }
        return Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888).also { draw(Canvas(it),snapshot,RectF(0f,0f,it.width.toFloat(),it.height.toFloat())) }
    }
}
