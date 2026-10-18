package com.scenio.mth

import android.content.Context
import android.graphics.*
import android.view.MotionEvent
import android.view.View
import com.scenio.mth.editor.DocumentCodec
import com.scenio.mth.editor.LayerRenderer
import com.scenio.mth.editor.fitImage
import kotlin.math.ceil
import java.util.UUID

class ForgeCanvas(context:Context):View(context) {
    data class Point(val x:Float,val y:Float,val pressure:Float=1f)
    data class Stroke(val points:MutableList<Point>,val color:Int,val size:Float,val layerId:String="ink",val kind:String="brush",val text:String="",val font:String="sans-serif",val contourStarts:List<Int> = emptyList())
    data class InkLayer(val id:String,val name:String,val visible:Boolean=true,val opacity:Float=1f,val blend:String="normal",val raster:Bitmap?=null,val offsetX:Float=0f,val offsetY:Float=0f,val scale:Float=1f,val rotation:Float=0f,val maskEnabled:Boolean=true)
    data class Guides(val grid:Int=0,val rulers:Boolean=false,val symmetry:String="none",val perspective:Boolean=false,val stabilization:Float=.2f)
    data class Snapshot(val base:Bitmap?,val strokes:List<Stroke>,val layers:List<InkLayer> = listOf(InkLayer("ink","Ink")),val activeLayer:String="ink",val guides:Guides=Guides())
    private val strokes=mutableListOf<Stroke>()
    private val layers=mutableListOf(InkLayer("ink","Ink"))
    private val history=java.util.ArrayDeque<Snapshot>()
    private val future=java.util.ArrayDeque<Snapshot>()
    private val viewport=RectF()
    private var live:Stroke?=null
    var activeLayer="ink";private set
    var guides=Guides();private set
    var tool="brush"
    var font="sans-serif"
    var pencilTilt=false
    var inkColor=Color.rgb(94,234,212)
    var inkSize=.004f
    var drawing=false
    var onEdited:(()->Unit)?=null
    private var baseImage:Bitmap?=null
    var image:Bitmap?
        get()=baseImage
        set(value) { baseImage=value;strokes.clear();layers.clear();layers.add(InkLayer("ink","Ink"));activeLayer="ink";history.clear();future.clear();live=null;updateBounds();changed() }
    init { contentDescription="Creative canvas. Use drawing tools, guides and layers to refine the image." }
    fun layerList()=layers.toList()
    fun snapshot()=Snapshot(image,strokes.map { it.copy(points=it.points.toMutableList()) },layers.toList(),activeLayer,guides)
    private fun apply(s:Snapshot) { baseImage=s.base;live=null;strokes.clear();layers.clear();layers.addAll(s.layers);strokes.addAll(s.strokes.map { it.copy(points=it.points.toMutableList()) });activeLayer=s.activeLayer.takeIf { id -> layers.any { it.id==id } } ?: layers.first().id;guides=s.guides;updateBounds();changed() }
    fun restore(s:Snapshot) { require(s.layers.isNotEmpty());history.clear();future.clear();apply(s) }
    private fun checkpoint() {
        history.addLast(snapshot());future.clear()
        while(history.size>32 || history.sumOf { s -> s.strokes.sumOf { it.points.size } }>250_000) history.removeFirst()
    }
    private fun changed() {
        fun rasterPixels():Long {
            val seen=java.util.IdentityHashMap<Bitmap,Boolean>();val states=history.toList()+future.toList()+listOf(Snapshot(image,emptyList(),layers))
            states.forEach { s -> s.layers.forEach { it.raster?.let { b -> seen[b]=true } } }
            return seen.keys.sumOf { it.width.toLong()*it.height }
        }
        while(history.size>32 || history.sumOf { s -> s.strokes.sumOf { it.points.size } }>250_000) history.removeFirst()
        while((history.isNotEmpty() || future.isNotEmpty()) && rasterPixels()>16_000_000) { if(history.isNotEmpty()) history.removeFirst() else future.removeFirst() }
        invalidate();onEdited?.invoke()
    }
    fun selectLayer(id:String) { require(layers.any { it.id==id });activeLayer=id }
    fun addLayer() { require(layers.size<16) { "Maximum 16 layers" };checkpoint();val id=UUID.randomUUID().toString();layers.add(InkLayer(id,"Layer ${layers.size+1}"));activeLayer=id;changed() }
    fun addRaster(bitmap:Bitmap) {
        require(image!=null) { "Create or import a base image first" };require(layers.size<16)
        require(bitmap.width<=1024 && bitmap.height<=1024 && layers.sumOf { (it.raster?.width ?: 0).toLong()*(it.raster?.height ?: 0) }+bitmap.width.toLong()*bitmap.height<=8_000_000) { "Raster layers exceed the memory limit" }
        checkpoint();val id=UUID.randomUUID().toString();layers.add(InkLayer(id,"Photo ${layers.size+1}",raster=bitmap));activeLayer=id;changed()
    }
    fun updateLayer(value:InkLayer) {
        require(value.name.isNotBlank() && value.name.length<=80 && value.opacity in 0f..1f && value.blend in listOf("normal","multiply","screen") && value.offsetX in -1f..1f && value.offsetY in -1f..1f && value.scale in .1f..4f && value.rotation in -180f..180f)
        val i=layers.indexOfFirst { it.id==value.id };require(i>=0);checkpoint();layers[i]=value;changed()
    }
    fun moveLayer(id:String,delta:Int) { val i=layers.indexOfFirst { it.id==id };require(i>=0);val to=(i+delta).coerceIn(0,layers.lastIndex);if(i!=to) { checkpoint();val value=layers.removeAt(i);layers.add(to,value);changed() } }
    fun deleteLayer(id:String) { require(layers.size>1 && layers.any { it.id==id });checkpoint();layers.removeAll { it.id==id };strokes.removeAll { it.layerId==id };activeLayer=layers.last().id;changed() }
    fun clearMask() { checkpoint();strokes.removeAll { it.layerId==activeLayer && it.kind.startsWith("mask-") };changed() }
    fun configureGuides(value:Guides) { require(value.grid in listOf(0,5,10,20) && value.symmetry in listOf("none","vertical","horizontal","both") && value.stabilization in 0f.. .95f);checkpoint();guides=value;changed() }
    fun label(text:String) { require(text.isNotBlank() && text.length<=200 && strokes.size<512 && strokes.sumOf { it.points.size }<100_000);checkpoint();strokes.add(Stroke(mutableListOf(Point(.1f,.15f)),inkColor,inkSize,activeLayer,"text",text,font));changed() }
    fun textLabels()=strokes.mapIndexedNotNull { i,s -> if(s.kind=="text") i to s.copy(points=s.points.toMutableList()) else null }
    fun updateTextLabel(index:Int,text:String,x:Float,y:Float) { require(index in strokes.indices && strokes[index].kind=="text" && text.isNotBlank() && text.length<=200 && x in 0f..1f && y in 0f..1f);checkpoint();strokes[index]=strokes[index].copy(text=text,points=mutableListOf(Point(x,y)));changed() }
    fun undo() { if(history.isEmpty()) return;val previous=history.removeLast();future.addLast(snapshot());apply(previous) }
    fun redo() { if(future.isEmpty()) return;val next=future.removeLast();history.addLast(snapshot());apply(next) }
    fun combineShapes(operation:Path.Op) {
        val base=requireNotNull(image);val chosenIndices=strokes.indices.filter { strokes[it].layerId==activeLayer && strokes[it].kind in listOf("rectangle","ellipse","filled-path") }.takeLast(2)
        val chosen=chosenIndices.map { strokes[it] }
        require(chosen.size==2) { "Draw two closed shapes on the active layer first" }
        val b=RectF(0f,0f,1024f,1024f*base.height/base.width);val result=Path()
        check(result.op(LayerRenderer.geometry(chosen[0],b),LayerRenderer.geometry(chosen[1],b),operation))
        val measure=PathMeasure(result,true);val lengths=mutableListOf<Float>()
        do { if(measure.length>0) lengths.add(measure.length) } while(measure.nextContour())
        require(lengths.isNotEmpty() && lengths.size<=64) { "The selected shapes have no usable result" }
        val points=mutableListOf<Point>();val starts=mutableListOf<Int>();val position=FloatArray(2);val sample=PathMeasure(result,true);val step=maxOf(1f,lengths.sum()/3500f)
        do { val length=sample.length;if(length>0) { starts.add(points.size);val count=ceil(length/step).toInt().coerceAtLeast(3)
            repeat(count) { i -> sample.getPosTan(length*i/count,position,null);points.add(Point((position[0]/b.width()).coerceIn(0f,1f),(position[1]/b.height()).coerceIn(0f,1f))) }
        } } while(sample.nextContour())
        require(points.size<=5000 && strokes.sumOf { it.points.size }-chosen.sumOf { it.points.size }+points.size<=100_000)
        checkpoint();chosenIndices.reversed().forEach { strokes.removeAt(it) };strokes.add(Stroke(points,chosen.last().color,inkSize,activeLayer,"filled-path",contourStarts=starts));changed()
    }
    private fun updateBounds() { val base=image ?: return;if(width>0 && height>0) { val fit=fitImage(width.toFloat(),height.toFloat(),base.width,base.height);viewport.set(fit.left,fit.top,fit.left+fit.width,fit.top+fit.height) } }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) { super.onSizeChanged(w,h,oldw,oldh);updateBounds() }
    override fun onDraw(c:Canvas) {
        c.drawColor(Color.rgb(15,23,42));if(image==null || viewport.isEmpty) return
        LayerRenderer.draw(c,Snapshot(image,strokes,layers,activeLayer,guides),viewport)
        val save=c.save();c.clipRect(viewport);val p=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0x8880a0b0.toInt();strokeWidth=1f;textSize=20f }
        if(guides.grid>0) for(i in 1 until guides.grid) { val x=viewport.left+viewport.width()*i/guides.grid;val y=viewport.top+viewport.height()*i/guides.grid;c.drawLine(x,viewport.top,x,viewport.bottom,p);c.drawLine(viewport.left,y,viewport.right,y,p) }
        if(guides.rulers) for(i in 0..10) { val x=viewport.left+viewport.width()*i/10;c.drawLine(x,viewport.top,x,viewport.top+12,p);c.drawText("${i*10}",x+2,viewport.top+30,p) }
        if(guides.perspective) for(i in 0..10) c.drawLine(viewport.centerX(),viewport.top+viewport.height()*.35f,viewport.left+viewport.width()*i/10,viewport.bottom,p)
        c.restoreToCount(save)
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(image==null || !drawing || viewport.isEmpty) return false
        val layer=layers.first { it.id==activeLayer };val inverse=Matrix();if(!LayerRenderer.matrix(layer,viewport).invert(inverse)) return false
        val raw=floatArrayOf(e.x,e.y);inverse.mapPoints(raw)
        val nx=(raw[0]-viewport.left)/viewport.width();val ny=(raw[1]-viewport.top)/viewport.height()
        if(e.actionMasked==MotionEvent.ACTION_DOWN && (!viewport.contains(e.x,e.y) || nx !in 0f..1f || ny !in 0f..1f)) return false
        val tilt=if(pencilTilt && e.getToolType(0)==MotionEvent.TOOL_TYPE_STYLUS) e.getAxisValue(MotionEvent.AXIS_TILT).coerceIn(0f,1.57f) else 0f
        val pressure=if(e.getToolType(0)==MotionEvent.TOOL_TYPE_STYLUS) (e.pressure*(1+tilt*.6f)).coerceIn(.15f,2f) else 1f
        val point=Point(nx.coerceIn(0f,1f),ny.coerceIn(0f,1f),pressure)
        val copies=if(guides.symmetry=="both") 4 else if(guides.symmetry=="none") 1 else 2
        when(e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if(strokes.size+copies>512 || strokes.sumOf { it.points.size }+copies>100_000) return false
                checkpoint();parent?.requestDisallowInterceptTouchEvent(true)
                live=Stroke(mutableListOf(point),inkColor,inkSize.coerceIn(.001f,.05f),activeLayer,if(tool=="perspective") "line" else tool,font=font);strokes.add(live!!)
            }
            MotionEvent.ACTION_MOVE -> live?.let { stroke ->
                val budget=(100_000-strokes.filter { it!==stroke }.sumOf { it.points.size })/copies
                if(stroke.points.size<minOf(5000,budget)) { val previous=stroke.points.last();val hold=guides.stabilization;stroke.points.add(point.copy(x=previous.x*hold+point.x*(1-hold),y=previous.y*hold+point.y*(1-hold))) }
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL -> {
                live?.let { stroke ->
                    if(tool=="perspective") { val target=floatArrayOf(viewport.centerX(),viewport.top+viewport.height()*.35f);inverse.mapPoints(target);if(stroke.points.size>=minOf(5000,(100_000-strokes.filter { it!==stroke }.sumOf { it.points.size })/copies)) stroke.points.removeAt(stroke.points.lastIndex);stroke.points.add(Point(((target[0]-viewport.left)/viewport.width()).coerceIn(0f,1f),((target[1]-viewport.top)/viewport.height()).coerceIn(0f,1f))) }
                    val mirrors=when(guides.symmetry) { "vertical" -> listOf(true to false);"horizontal" -> listOf(false to true);"both" -> listOf(true to false,false to true,true to true);else -> emptyList() }
                    mirrors.forEach { (x,y) -> strokes.add(stroke.copy(points=stroke.points.map { it.copy(x=if(x) 1-it.x else it.x,y=if(y) 1-it.y else it.y) }.toMutableList())) }
                }
                live=null;parent?.requestDisallowInterceptTouchEvent(false);performClick();changed()
            }
        }
        invalidate();return true
    }
    override fun performClick():Boolean { super.performClick();return true }
    fun flatten()=render(snapshot())
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
        fun render(snapshot:Snapshot)=LayerRenderer.render(snapshot)
        fun encodeInk(snapshot:Snapshot)=DocumentCodec.encode(snapshot)
        fun decodeInk(json:String)=decodeDocument(json).strokes
        fun decodeDocument(json:String,loadRaster:(String)->Bitmap? = { null })=DocumentCodec.decode(json,loadRaster)
    }
}
