package com.scenio.mth

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.scenio.mth.editor.fitImage
import org.json.JSONArray
import org.json.JSONObject

/** Base image + editable pressure-aware ink. Image aspect ratio is never stretched. */
class ForgeCanvas(context: Context) : View(context) {
    data class Point(val x: Float, val y: Float, val pressure: Float = 1f)
    data class Stroke(val points: MutableList<Point>, val color: Int, val size: Float)
    data class Snapshot(val base: Bitmap?, val strokes: List<Stroke>)
    private val strokes = mutableListOf<Stroke>()
    private val undone = mutableListOf<Stroke>()
    var onEdited: (() -> Unit)? = null
    var image: Bitmap? = null
        set(value) { field=value;strokes.clear();undone.clear();invalidate();onEdited?.invoke() }
    var inkColor = Color.rgb(94,234,212)
    var inkSize = .004f
    var drawing = false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    init { contentDescription="Creative canvas. Use the drawing switch to annotate the image." }
    fun snapshot() = Snapshot(image,strokes.map { it.copy(points=it.points.toMutableList()) })
    fun restore(snapshot: Snapshot) {
        image=snapshot.base
        strokes.addAll(snapshot.strokes.map { it.copy(points=it.points.toMutableList()) })
        invalidate()
    }
    override fun onDraw(canvas: Canvas) {
        if(width<=0 || height<=0) return
        canvas.drawColor(Color.rgb(15,23,42))
        val base=image ?: return
        val bounds=fitImage(width.toFloat(),height.toFloat(),base.width,base.height)
        drawSnapshot(canvas,Snapshot(image,strokes),RectF(bounds.left,bounds.top,bounds.left+bounds.width,bounds.top+bounds.height),paint)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean {
        val base=image ?: return false
        if(!drawing || width==0 || height==0) return false
        val bounds=fitImage(width.toFloat(),height.toFloat(),base.width,base.height)
        if(e.actionMasked==MotionEvent.ACTION_DOWN && !bounds.contains(e.x,e.y)) return false
        val pressure=if(e.getToolType(0)==MotionEvent.TOOL_TYPE_STYLUS) e.pressure.coerceIn(.15f,2f) else 1f
        val point=Point(((e.x-bounds.left)/bounds.width).coerceIn(0f,1f),((e.y-bounds.top)/bounds.height).coerceIn(0f,1f),pressure)
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if(strokes.size>=512) return false
                parent.requestDisallowInterceptTouchEvent(true);undone.clear()
                strokes.add(Stroke(mutableListOf(point),inkColor,inkSize.coerceIn(.001f,.05f)))
            }
            MotionEvent.ACTION_MOVE -> strokes.lastOrNull()?.let { stroke ->
                if(stroke.points.size<5000 && strokes.sumOf { it.points.size }<100_000) {
                    val previous=stroke.points.last()
                    stroke.points.add(point.copy(x=previous.x*.2f+point.x*.8f,y=previous.y*.2f+point.y*.8f))
                }
            }
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false);performClick();onEdited?.invoke() }
            MotionEvent.ACTION_CANCEL -> parent.requestDisallowInterceptTouchEvent(false)
        }
        invalidate();return true
    }
    override fun performClick(): Boolean { super.performClick();return true }
    fun undo() { if(strokes.isNotEmpty()) { undone.add(strokes.removeAt(strokes.lastIndex));invalidate();onEdited?.invoke() } }
    fun redo() { if(undone.isNotEmpty()) { strokes.add(undone.removeAt(undone.lastIndex));invalidate();onEdited?.invoke() } }
    fun flatten() = render(snapshot())
    fun diagram(prompt: String, level: String): Bitmap {
        val b=Bitmap.createBitmap(1024,576,Bitmap.Config.ARGB_8888);val c=Canvas(b)
        c.drawColor(Color.rgb(15,23,42));val p=Paint(Paint.ANTI_ALIAS_FLAG)
        p.color=Color.WHITE;p.textSize=38f;c.drawText("StudyForge / $level",48f,64f,p)
        p.textSize=22f;c.drawText(prompt.take(75),48f,104f,p)
        val mitosis=prompt.contains("mitosis",true)
        val names=if(mitosis) listOf("Prophase","Metaphase","Anaphase","Telophase") else listOf("Whole","System","Organ","Cell")
        names.forEachIndexed { i,label ->
            val x=142f+i*246f;p.color=Color.rgb(94,234,212);p.style=Paint.Style.STROKE;p.strokeWidth=5f;c.drawCircle(x,285f,77f,p)
            p.style=Paint.Style.FILL;p.color=Color.rgb(192,132,252)
            if(mitosis) for(j in 0..3) c.drawCircle(x-28+j*18,275f+(j%2)*20,7f,p) else c.drawCircle(x,285f,25f,p)
            p.color=Color.WHITE;p.textSize=24f;c.drawText(label,x-65,405f,p)
        }
        p.textSize=19f;p.color=Color.LTGRAY;c.drawText("Schematic template • review labels before academic use",48f,530f,p)
        return b
    }
    companion object {
        private fun drawSnapshot(c: Canvas, snapshot: Snapshot, bounds: RectF, p: Paint) {
            p.style=Paint.Style.FILL;p.alpha=255
            snapshot.base?.let { c.drawBitmap(it,null,bounds,p) }
            p.style=Paint.Style.STROKE;p.strokeCap=Paint.Cap.ROUND;p.strokeJoin=Paint.Join.ROUND
            c.save();c.clipRect(bounds)
            for(stroke in snapshot.strokes) {
                p.color=stroke.color
                stroke.points.forEachIndexed { i,point ->
                    p.strokeWidth=stroke.size*bounds.width()*point.pressure
                    val x=bounds.left+point.x*bounds.width();val y=bounds.top+point.y*bounds.height()
                    if(i==0) c.drawPoint(x,y,p) else {
                        val before=stroke.points[i-1]
                        c.drawLine(bounds.left+before.x*bounds.width(),bounds.top+before.y*bounds.height(),x,y,p)
                    }
                }
            }
            c.restore();p.style=Paint.Style.FILL
        }
        fun render(snapshot: Snapshot): Bitmap {
            val base=requireNotNull(snapshot.base) { "Import or generate an image first" }
            val out=Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888)
            drawSnapshot(Canvas(out),snapshot,RectF(0f,0f,out.width.toFloat(),out.height.toFloat()),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return out
        }
        fun encodeInk(snapshot: Snapshot): String = JSONArray().apply {
            snapshot.strokes.forEach { stroke -> put(JSONObject().put("color",stroke.color).put("size",stroke.size).put("points",JSONArray().apply {
                stroke.points.forEach { point -> put(JSONArray().put(point.x).put(point.y).put(point.pressure)) }
            })) }
        }.toString()
        fun decodeInk(json: String): List<Stroke> {
            require(json.length<=8_000_000) { "Ink file too large" }
            val items=JSONArray(json);require(items.length()<=512)
            var total=0
            return (0 until items.length()).map { i ->
                val item=items.getJSONObject(i);val size=item.getDouble("size").toFloat()
                require(size.isFinite() && size in .001f.. .05f)
                val points=item.getJSONArray("points");total+=points.length();require(points.length()<=5000 && total<=100_000)
                Stroke((0 until points.length()).map { j ->
                    val p=points.getJSONArray(j);val x=p.getDouble(0).toFloat();val y=p.getDouble(1).toFloat();val pressure=p.optDouble(2,1.0).toFloat()
                    require(x.isFinite() && y.isFinite() && pressure.isFinite() && x in 0f..1f && y in 0f..1f && pressure in .15f..2f)
                    Point(x,y,pressure)
                }.toMutableList(),item.getInt("color"),size)
            }
        }
    }
}
