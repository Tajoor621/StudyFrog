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
    data class Stroke(val points: MutableList<Point>, val color: Int, val size: Float, val layerId:String="ink", val kind:String="brush", val text:String="")
    data class InkLayer(val id:String, val name:String, val visible:Boolean=true, val opacity:Float=1f, val blend:String="normal")
    data class Snapshot(val base: Bitmap?, val strokes: List<Stroke>,val layers:List<InkLayer> = listOf(InkLayer("ink","Ink")),val activeLayer:String="ink")
    private val strokes = mutableListOf<Stroke>()
    private val undone = mutableListOf<Stroke>()
    private val layers=mutableListOf(InkLayer("ink","Ink"))
    var activeLayer="ink"
        private set
    var tool="brush"
    fun layerList()=layers.toList()
    fun selectLayer(id:String) { require(layers.any { it.id==id });activeLayer=id }
    fun addLayer() { require(layers.size<16) { "Maximum 16 editable layers" };val id=java.util.UUID.randomUUID().toString();layers.add(InkLayer(id,"Layer ${layers.size+1}"));activeLayer=id;changed() }
    fun updateLayer(value:InkLayer) { require(value.name.isNotBlank() && value.name.length<=80 && value.opacity in 0f..1f && value.blend in listOf("normal","multiply","screen"));val index=layers.indexOfFirst { it.id==value.id };require(index>=0);layers[index]=value;changed() }
    fun moveLayer(id:String,delta:Int) { val i=layers.indexOfFirst { it.id==id };val to=(i+delta).coerceIn(0,layers.lastIndex);if(i>=0 && i!=to) { val layer=layers.removeAt(i);layers.add(to,layer);changed() } }
    fun deleteLayer(id:String) { require(layers.size>1);layers.removeAll { it.id==id };strokes.removeAll { it.layerId==id };undone.clear();activeLayer=layers.last().id;changed() }
    fun label(text:String) { require(text.isNotBlank() && text.length<=200);require(strokes.size<512);strokes.add(Stroke(mutableListOf(Point(.1f,.15f)),inkColor,inkSize,activeLayer,"text",text));changed() }
    fun textLabels():List<Pair<Int,Stroke>> = strokes.mapIndexedNotNull { i,stroke -> if(stroke.kind=="text") i to stroke.copy(points=stroke.points.toMutableList()) else null }
    fun updateTextLabel(index:Int,text:String,x:Float,y:Float) {
        require(index in strokes.indices && strokes[index].kind=="text");require(text.isNotBlank() && text.length<=200 && x in 0f..1f && y in 0f..1f)
        strokes[index]=strokes[index].copy(text=text,points=mutableListOf(Point(x,y)));undone.clear();changed()
    }
    private fun changed() { previewState=Snapshot(image,strokes,layers,activeLayer);invalidate();onEdited?.invoke() }
    private val viewport=RectF()
    private var previewState=Snapshot(null,strokes)
    var onEdited: (() -> Unit)? = null
    var image: Bitmap? = null
        set(value) { field=value;strokes.clear();undone.clear();layers.clear();layers.add(InkLayer("ink","Ink"));activeLayer="ink";previewState=Snapshot(value,strokes,layers,activeLayer);updateBounds();invalidate();onEdited?.invoke() }
    var inkColor = Color.rgb(94,234,212)
    var inkSize = .004f
    var drawing = false
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    init { contentDescription="Creative canvas. Use the drawing switch to annotate the image." }
    fun snapshot() = Snapshot(image,strokes.map { it.copy(points=it.points.toMutableList()) },layers.toList(),activeLayer)
    fun restore(snapshot: Snapshot) {
        image=snapshot.base
        layers.clear();layers.addAll(snapshot.layers);activeLayer=snapshot.activeLayer.takeIf { id -> layers.any { it.id==id } } ?: layers.last().id
        strokes.addAll(snapshot.strokes.map { it.copy(points=it.points.toMutableList()) })
        previewState=Snapshot(image,strokes,layers,activeLayer);invalidate()
    }
    private fun updateBounds() {
        val base=image ?: return
        if(width<=0 || height<=0) return
        val bounds=fitImage(width.toFloat(),height.toFloat(),base.width,base.height)
        viewport.set(bounds.left,bounds.top,bounds.left+bounds.width,bounds.top+bounds.height)
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh);updateBounds()
    }
    override fun onDraw(canvas: Canvas) {
        if(width<=0 || height<=0) return
        canvas.drawColor(Color.rgb(15,23,42))
        if(image==null) return
        drawSnapshot(canvas,previewState,viewport,paint)
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
                if(strokes.size>=512 || strokes.sumOf { it.points.size }>=100_000) return false
                parent.requestDisallowInterceptTouchEvent(true);undone.clear()
                strokes.add(Stroke(mutableListOf(point),inkColor,inkSize.coerceIn(.001f,.05f),activeLayer,tool))
            }
            MotionEvent.ACTION_MOVE -> strokes.lastOrNull()?.let { stroke ->
                if(stroke.points.size<5000 && strokes.sumOf { it.points.size }<100_000) {
                    val previous=stroke.points.last()
                    stroke.points.add(point.copy(x=previous.x*.2f+point.x*.8f,y=previous.y*.2f+point.y*.8f))
                }
            }
            MotionEvent.ACTION_UP -> { parent.requestDisallowInterceptTouchEvent(false);performClick();onEdited?.invoke() }
            MotionEvent.ACTION_CANCEL -> { parent.requestDisallowInterceptTouchEvent(false);onEdited?.invoke() }
        }
        invalidate();return true
    }
    override fun performClick(): Boolean { super.performClick();return true }
    fun undo() { val index=strokes.indexOfLast { it.layerId==activeLayer };if(index>=0) { undone.add(strokes.removeAt(index));changed() } }
    fun redo() { val index=undone.indexOfLast { it.layerId==activeLayer };if(index>=0) { strokes.add(undone.removeAt(index));changed() } }
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
            for(layer in snapshot.layers.filter { it.visible && it.opacity>0 }) {
                val layerPaint=Paint().apply { alpha=(layer.opacity*255).toInt();xfermode=PorterDuffXfermode(when(layer.blend) { "multiply" -> PorterDuff.Mode.MULTIPLY;"screen" -> PorterDuff.Mode.SCREEN;else -> PorterDuff.Mode.SRC_OVER }) }
                val save=c.saveLayer(bounds,layerPaint)
                for(stroke in snapshot.strokes.filter { it.layerId==layer.id }) {
                    p.color=stroke.color;p.style=Paint.Style.STROKE;p.strokeWidth=stroke.size*bounds.width()
                    p.xfermode=if(stroke.kind=="erase") PorterDuffXfermode(PorterDuff.Mode.CLEAR) else null
                    val first=stroke.points.firstOrNull() ?: continue;val last=stroke.points.last()
                    fun x(v:Point)=bounds.left+v.x*bounds.width()
                    fun y(v:Point)=bounds.top+v.y*bounds.height()
                    when(stroke.kind) {
                        "text" -> { p.style=Paint.Style.FILL;p.textSize=stroke.size*bounds.width()*8;c.drawText(stroke.text,x(first),y(first),p) }
                        "line" -> c.drawLine(x(first),y(first),x(last),y(last),p)
                        "rectangle","ellipse" -> { val box=RectF(minOf(x(first),x(last)),minOf(y(first),y(last)),maxOf(x(first),x(last)),maxOf(y(first),y(last)));if(stroke.kind=="rectangle") c.drawRect(box,p) else c.drawOval(box,p) }
                        else -> stroke.points.forEachIndexed { i,point ->
                            p.strokeWidth=stroke.size*bounds.width()*point.pressure
                            if(i==0) c.drawPoint(x(point),y(point),p) else c.drawLine(x(stroke.points[i-1]),y(stroke.points[i-1]),x(point),y(point),p)
                        }
                    }
                }
                p.xfermode=null;c.restoreToCount(save)
            }
            c.restore();p.style=Paint.Style.FILL
        }
        fun render(snapshot: Snapshot): Bitmap {
            val base=requireNotNull(snapshot.base) { "Import or generate an image first" }
            val out=Bitmap.createBitmap(base.width,base.height,Bitmap.Config.ARGB_8888)
            drawSnapshot(Canvas(out),snapshot,RectF(0f,0f,out.width.toFloat(),out.height.toFloat()),Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
            return out
        }
        fun encodeInk(snapshot: Snapshot): String = JSONObject().put("version",3).put("active",snapshot.activeLayer).put("layers",JSONArray().apply {
            snapshot.layers.forEach { put(JSONObject().put("id",it.id).put("name",it.name).put("visible",it.visible).put("opacity",it.opacity).put("blend",it.blend)) }
        }).put("strokes",JSONArray().apply {
            snapshot.strokes.forEach { stroke -> put(JSONObject().put("color",stroke.color).put("size",stroke.size).put("layer",stroke.layerId).put("kind",stroke.kind).put("text",stroke.text).put("points",JSONArray().apply {
                stroke.points.forEach { point -> put(JSONArray().put(point.x).put(point.y).put(point.pressure)) }
            })) }
        }).toString()
        fun decodeInk(json:String)=decodeDocument(json).strokes
        fun decodeDocument(json: String): Snapshot {
            require(json.length<=8_000_000) { "Ink file too large" }
            val document=if(json.trimStart().startsWith("{")) JSONObject(json) else null
            val layerItems=document?.getJSONArray("layers")
            val layers=if(layerItems==null) listOf(InkLayer("ink","Ink")) else {
                require(layerItems.length() in 1..16)
                (0 until layerItems.length()).map { i -> val l=layerItems.getJSONObject(i)
                    val id=l.getString("id");val name=l.getString("name");val opacity=l.getDouble("opacity").toFloat();val blend=l.getString("blend")
                    require(id.length in 1..80 && name.length in 1..80 && opacity.isFinite() && opacity in 0f..1f && blend in listOf("normal","multiply","screen"))
                    InkLayer(id,name,l.getBoolean("visible"),opacity,blend)
                }.also { require(it.map { layer -> layer.id }.distinct().size==it.size) }
            }
            val items=document?.getJSONArray("strokes") ?: JSONArray(json);require(items.length()<=512)
            var total=0
            val strokes=(0 until items.length()).map { i ->
                val item=items.getJSONObject(i);val size=item.getDouble("size").toFloat()
                require(size.isFinite() && size in .001f.. .05f)
                val points=item.getJSONArray("points");total+=points.length();require(points.length()<=5000 && total<=100_000)
                val layer=item.optString("layer","ink");val kind=item.optString("kind","brush");val label=item.optString("text","")
                require(layers.any { it.id==layer } && kind in listOf("brush","erase","line","rectangle","ellipse","text") && label.length<=200)
                Stroke((0 until points.length()).map { j ->
                    val p=points.getJSONArray(j);val x=p.getDouble(0).toFloat();val y=p.getDouble(1).toFloat();val pressure=p.optDouble(2,1.0).toFloat()
                    require(x.isFinite() && y.isFinite() && pressure.isFinite() && x in 0f..1f && y in 0f..1f && pressure in .15f..2f)
                    Point(x,y,pressure)
                }.toMutableList(),item.getInt("color"),size,layer,kind,label)
            }
            return Snapshot(null,strokes,layers,document?.optString("active","ink") ?: "ink")
        }
    }
}
