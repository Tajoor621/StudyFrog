package com.scenio.mth

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View

/** A raster base plus an independent ink layer. Coordinates are normalized. */
class ForgeCanvas(context: Context) : View(context) {
    data class Stroke(val points: MutableList<PointF>, val color: Int, val size: Float)
    var image: Bitmap? = null
        set(value) { field = value; strokes.clear(); invalidate() }
    private val strokes = mutableListOf<Stroke>()
    var inkColor = Color.rgb(94, 234, 212)
    var drawing = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private fun render(c: Canvas, w: Int, h: Int) {
        c.drawColor(Color.rgb(15,23,42))
        image?.let { c.drawBitmap(it, null, Rect(0,0,w,h), paint) }
        paint.style = Paint.Style.STROKE; paint.strokeCap = Paint.Cap.ROUND; paint.strokeJoin = Paint.Join.ROUND
        for (stroke in strokes) {
            paint.color = stroke.color; paint.strokeWidth = stroke.size * w
            val path = Path()
            stroke.points.forEachIndexed { i,p -> if(i==0) path.moveTo(p.x*w,p.y*h) else path.lineTo(p.x*w,p.y*h) }
            if (stroke.points.size == 1) { val p=stroke.points.first(); c.drawPoint(p.x*w,p.y*h,paint) } else c.drawPath(path,paint)
        }
        paint.style = Paint.Style.FILL
    }
    override fun onDraw(canvas: Canvas) { render(canvas,width,height) }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (!drawing || width==0 || height==0) return false
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { parent.requestDisallowInterceptTouchEvent(true); strokes.add(Stroke(mutableListOf(PointF(e.x/width,e.y/height)),inkColor,0.004f)) }
            MotionEvent.ACTION_MOVE -> strokes.lastOrNull()?.points?.add(PointF(e.x/width,e.y/height))
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
        }
        invalidate(); return true
    }
    fun undo() { if(strokes.isNotEmpty()) strokes.removeAt(strokes.lastIndex); invalidate() }
    fun flatten(): Bitmap = Bitmap.createBitmap(1024,576,Bitmap.Config.ARGB_8888).also { render(Canvas(it),it.width,it.height) }
    fun diagram(prompt: String, level: String): Bitmap {
        val b=Bitmap.createBitmap(1024,576,Bitmap.Config.ARGB_8888); val c=Canvas(b)
        c.drawColor(Color.rgb(15,23,42)); val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.color=Color.WHITE;p.textSize=38f;c.drawText("StudyForge / $level",48f,64f,p)
        p.textSize=22f;c.drawText(prompt.take(75),48f,104f,p)
        val mitosis=prompt.contains("mitosis",true)
        val names=if(mitosis) listOf("Prophase","Metaphase","Anaphase","Telophase") else listOf("Whole","System","Organ","Cell")
        names.forEachIndexed { i,label ->
            val x=142f+i*246f;p.color=Color.rgb(94,234,212);p.style=Paint.Style.STROKE;p.strokeWidth=5f;c.drawCircle(x,285f,77f,p)
            p.style=Paint.Style.FILL;p.color=Color.rgb(192,132,252)
            if(mitosis) { for(j in 0..3) c.drawCircle(x-28+j*18,275f+(j%2)*20,7f,p) } else c.drawCircle(x,285f,25f,p)
            p.color=Color.WHITE;p.textSize=24f;c.drawText(label,x-65,405f,p)
        }
        p.textSize=19f;p.color=Color.LTGRAY;c.drawText("Schematic template • review labels before academic use",48f,530f,p)
        return b
    }
}
