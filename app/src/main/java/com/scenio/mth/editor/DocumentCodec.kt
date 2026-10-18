package com.scenio.mth.editor

import android.graphics.Bitmap
import com.scenio.mth.ForgeCanvas
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

object DocumentCodec {
    fun rasterName(layer:ForgeCanvas.InkLayer):String {
        require(UUID.fromString(layer.id).toString()==layer.id) { "Invalid raster layer ID" }
        return "layer-${layer.id}.png"
    }
    fun encode(s:ForgeCanvas.Snapshot):String = JSONObject().put("version",4).put("active",s.activeLayer)
        .put("guides",JSONObject().put("grid",s.guides.grid).put("rulers",s.guides.rulers).put("symmetry",s.guides.symmetry).put("perspective",s.guides.perspective).put("stabilization",s.guides.stabilization))
        .put("layers",JSONArray().apply { s.layers.forEach { layer -> put(JSONObject().put("id",layer.id).put("name",layer.name).put("visible",layer.visible).put("opacity",layer.opacity).put("blend",layer.blend)
            .put("offsetX",layer.offsetX).put("offsetY",layer.offsetY).put("scale",layer.scale).put("rotation",layer.rotation).put("maskEnabled",layer.maskEnabled).apply { if(layer.raster!=null) put("raster",rasterName(layer)) }) } })
        .put("strokes",JSONArray().apply { s.strokes.forEach { stroke -> put(JSONObject().put("color",stroke.color).put("size",stroke.size).put("layer",stroke.layerId).put("kind",stroke.kind).put("text",stroke.text).put("font",stroke.font)
            .put("contours",JSONArray(stroke.contourStarts)).put("points",JSONArray().apply { stroke.points.forEach { p -> put(JSONArray().put(p.x).put(p.y).put(p.pressure)) } })) } }).toString()
    fun decode(json:String,loadRaster:(String)->Bitmap? = { null }):ForgeCanvas.Snapshot {
        require(json.length<=8_000_000) { "Ink file too large" }
        val doc=if(json.trimStart().startsWith("{")) JSONObject(json) else null
        require(doc==null || doc.optInt("version",3) in 3..4) { "Unsupported layer document" }
        val layerItems=doc?.getJSONArray("layers");var pixels=0L;val allocated=mutableListOf<Bitmap>()
        try {
            val layers=if(layerItems==null) listOf(ForgeCanvas.InkLayer("ink","Ink")) else {
                require(layerItems.length() in 1..16)
                (0 until layerItems.length()).map { i ->
                    val l=layerItems.getJSONObject(i);val id=l.getString("id");val name=l.getString("name");val alpha=l.getDouble("opacity").toFloat();val blend=l.getString("blend")
                    require(id.length in 1..80 && name.length in 1..80 && alpha.isFinite() && alpha in 0f..1f && blend in listOf("normal","multiply","screen"))
                    val x=l.optDouble("offsetX",0.0).toFloat();val y=l.optDouble("offsetY",0.0).toFloat();val scale=l.optDouble("scale",1.0).toFloat();val rotation=l.optDouble("rotation",0.0).toFloat()
                    require(x in -1f..1f && y in -1f..1f && scale in .1f..4f && rotation in -180f..180f)
                    var layer=ForgeCanvas.InkLayer(id,name,l.getBoolean("visible"),alpha,blend,offsetX=x,offsetY=y,scale=scale,rotation=rotation,maskEnabled=l.optBoolean("maskEnabled",true))
                    if(l.has("raster")) {
                        val asset=l.getString("raster");require(asset==rasterName(layer)) { "Invalid raster asset reference" }
                        val bitmap=requireNotNull(loadRaster(asset)) { "Raster asset is missing" };allocated.add(bitmap)
                        pixels+=bitmap.width.toLong()*bitmap.height
                        require(bitmap.width<=1024 && bitmap.height<=1024 && pixels<=8_000_000) { "Raster layer memory limit exceeded" }
                        layer=layer.copy(raster=bitmap)
                    }
                    layer
                }.also { require(it.map { l -> l.id }.distinct().size==it.size) }
            }
            val items=doc?.getJSONArray("strokes") ?: JSONArray(json);require(items.length()<=512);var total=0
            val strokes=(0 until items.length()).map { i ->
                val item=items.getJSONObject(i);val size=item.getDouble("size").toFloat();require(size.isFinite() && size in .001f.. .05f)
                val points=item.getJSONArray("points");total+=points.length();require(points.length() in 1..5000 && total<=100_000)
                val layer=item.optString("layer","ink");val kind=item.optString("kind","brush");val label=item.optString("text","");val font=item.optString("font","sans-serif")
                require(layers.any { it.id==layer } && kind in listOf("brush","erase","mask-hide","mask-show","line","rectangle","ellipse","text","filled-path") && label.length<=200 && font in listOf("sans-serif","serif","monospace"))
                val contours=item.optJSONArray("contours")?.let { a -> (0 until a.length()).map { a.getInt(it) } } ?: emptyList()
                require(contours.size<=64 && contours==contours.distinct().sorted() && contours.all { it in 0 until points.length() })
                if(kind=="filled-path") require(points.length()>=3 && contours.firstOrNull()==0)
                ForgeCanvas.Stroke((0 until points.length()).map { j ->
                    val p=points.getJSONArray(j);val x=p.getDouble(0).toFloat();val y=p.getDouble(1).toFloat();val pressure=p.optDouble(2,1.0).toFloat()
                    require(x in 0f..1f && y in 0f..1f && pressure in .15f..2f);ForgeCanvas.Point(x,y,pressure)
                }.toMutableList(),item.getInt("color"),size,layer,kind,label,font,contours)
            }
            val g=doc?.optJSONObject("guides")
            val guides=ForgeCanvas.Guides(g?.optInt("grid",0) ?: 0,g?.optBoolean("rulers",false) ?: false,g?.optString("symmetry","none") ?: "none",g?.optBoolean("perspective",false) ?: false,g?.optDouble("stabilization",.2)?.toFloat() ?: .2f)
            require(guides.grid in listOf(0,5,10,20) && guides.symmetry in listOf("none","vertical","horizontal","both") && guides.stabilization in 0f.. .95f)
            return ForgeCanvas.Snapshot(null,strokes,layers,doc?.optString("active","ink") ?: "ink",guides)
        } catch(e:Exception) { allocated.forEach { it.recycle() };throw e }
    }
}
