package com.scenio.mth.science

import android.content.Context
import android.graphics.*
import com.scenio.mth.ForgeCanvas
import org.json.JSONObject

data class StudySource(val id:String,val title:String,val url:String)
data class StudyLabel(val text:String,val x:Float,val y:Float)
data class StudyShape(val kind:String,val points:List<Float>,val color:Int,val fill:Boolean)
data class StudyNode(val id:String,val title:String,val level:String,val summary:String,val prompt:String,val alt:String,val sourceIds:List<String>,val children:List<String>,val parent:String?,val next:String?,val shapes:List<StudyShape>,val labels:List<StudyLabel>)

class StudyCatalog(context:Context) {
    private val data=JSONObject(context.assets.open("study-catalog.json").bufferedReader().use { it.readText() })
    val notice:String=data.getString("notice")
    val sources:List<StudySource>
    val nodes:List<StudyNode>
    val roots:List<String>
    init {
        fun strings(value:JSONObject,key:String):List<String> = value.getJSONArray(key).let { a -> (0 until a.length()).map { a.getString(it) } }
        roots=strings(data,"roots")
        sources=data.getJSONArray("sources").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { StudySource(it.getString("id"),it.getString("title"),it.getString("url")) } } }
        nodes=data.getJSONArray("nodes").let { a -> (0 until a.length()).map { i -> a.getJSONObject(i).let { n ->
            val shapes=n.getJSONArray("shapes").let { s -> (0 until s.length()).map { j -> s.getJSONObject(j).let { v -> StudyShape(v.getString("kind"),v.getJSONArray("points").let { p -> (0 until p.length()).map { p.getDouble(it).toFloat() } },Color.parseColor(v.getString("color")),v.getBoolean("fill")) } } }
            val labels=n.getJSONArray("labels").let { l -> (0 until l.length()).map { j -> l.getJSONObject(j).let { StudyLabel(it.getString("text"),it.getDouble("x").toFloat(),it.getDouble("y").toFloat()) } } }
            StudyNode(n.getString("id"),n.getString("title"),n.getString("level"),n.getString("summary"),n.getString("prompt"),n.getString("alt"),strings(n,"sourceIds"),strings(n,"children"),n.optString("parent").takeUnless { it=="null" || it.isEmpty() },n.optString("next").takeUnless { it=="null" || it.isEmpty() },shapes,labels)
        } } }
        require(data.getInt("version")==1 && nodes.map { it.id }.toSet().size==nodes.size)
        nodes.forEach { node -> path(node.id);require((node.children+listOfNotNull(node.next)).all { id -> nodes.any { it.id==id } });require(node.sourceIds.all { id -> sources.any { it.id==id } }) }
    }
    fun node(id:String):StudyNode = nodes.firstOrNull { it.id==id } ?: throw IllegalArgumentException("Unknown study view")
    fun path(id:String):List<StudyNode> {
        val result=mutableListOf<StudyNode>();var current:StudyNode?=node(id)
        while(true) { val item=current ?: break;require(result.none { it.id==item.id }) { "Cyclic study path" };result.add(item);current=item.parent?.let(::node) }
        return result.reversed()
    }
    fun generationPrompt(id:String):String = "Study path: "+path(id).joinToString(" > ") { it.title }+". "+node(id).prompt+" Educational illustration; verify all generated labels against the references."
    fun snapshot(id:String,labels:Boolean=true):ForgeCanvas.Snapshot {
        val node=node(id);val image=Bitmap.createBitmap(1200,900,Bitmap.Config.ARGB_8888);val canvas=Canvas(image)
        canvas.drawColor(Color.rgb(248,250,252));val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=Color.rgb(22,50,79);textSize=38f;typeface=Typeface.create(Typeface.SANS_SERIF,Typeface.BOLD) }
        canvas.drawText(node.title,60f,66f,paint);paint.textSize=23f;paint.typeface=Typeface.DEFAULT;canvas.drawText(node.level+" / simplified schematic",60f,106f,paint)
        for(shape in node.shapes) {
            paint.color=shape.color;paint.style=if(shape.fill) Paint.Style.FILL else Paint.Style.STROKE;paint.strokeWidth=5f;paint.strokeCap=Paint.Cap.ROUND
            val values=shape.points;fun x(i:Int)=60+values[i]*1080;fun y(i:Int)=150+values[i]*600
            when(shape.kind) {
                "ellipse" -> canvas.drawOval(x(0),y(1),x(2),y(3),paint)
                "rect" -> canvas.drawRoundRect(x(0),y(1),x(2),y(3),18f,18f,paint)
                "line" -> { val path=Path();path.moveTo(x(0),y(1));for(i in 2 until values.size step 2) path.lineTo(x(i),y(i+1));canvas.drawPath(path,paint) }
            }
        }
        paint.style=Paint.Style.FILL;paint.color=Color.rgb(22,50,79);paint.textSize=22f
        canvas.drawText("References: "+node.sourceIds.joinToString()+" / OpenStax",60f,823f,paint)
        canvas.drawText("Original conceptual diagram / not to scale / educational use",60f,862f,paint)
        val ink=if(labels) node.labels.map { label -> ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point((60+label.x*1080)/1200,(150+label.y*600)/900)),Color.rgb(22,50,79),.003f,"study-labels","text",label.text) } else emptyList()
        return ForgeCanvas.Snapshot(image,ink,listOf(ForgeCanvas.InkLayer("study-labels","Study labels")),"study-labels")
    }
}
