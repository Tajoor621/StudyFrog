package com.scenio.mth.editor

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.RectF
import android.util.Base64
import com.scenio.mth.ForgeCanvas
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.security.MessageDigest
import java.util.UUID
import kotlin.math.sqrt

/** Versioned, local-only layered raster exchange. Native ink documents remain unchanged. */
object PortableProject {
    private const val LIMIT = 16_000_000
    data class Document(val snapshot: ForgeCanvas.Snapshot, val metadata: JSONObject)
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun text(value: String, limit: Int): String {
        require(value.length <= limit && value.none { it.isISOControl() && it != '\n' && it != '\t' }) { "Invalid portable project text" }
        return value
    }
    private fun asset(bitmap: Bitmap): JSONObject {
        val data = ByteArrayOutputStream().use { out -> check(bitmap.compress(Bitmap.CompressFormat.PNG,100,out));out.toByteArray() }
        return JSONObject().put("width",bitmap.width).put("height",bitmap.height).put("sha256",hash(data)).put("png",Base64.encodeToString(data,Base64.NO_WRAP))
    }
    fun encode(snapshot: ForgeCanvas.Snapshot, metadata: JSONObject): String {
        val base = requireNotNull(snapshot.base) { "Create an image first" }
        require(base.width in 1..2048 && base.height in 1..2048)
        // Reuse the native codec's complete stroke, layer and transform validation.
        DocumentCodec.decode(DocumentCodec.encode(snapshot.copy(layers=snapshot.layers.map { it.copy(raster=null) })))
        require(snapshot.layers.sumOf { (it.raster?.width ?: 0).toLong()*(it.raster?.height ?: 0) } <= 8_000_000)
        val side = minOf(1024,sqrt(8_000_000.0/snapshot.layers.size).toInt())
        val factor = minOf(1.0,side.toDouble()/maxOf(base.width,base.height))
        val width = maxOf(1,(base.width*factor).toInt());val height = maxOf(1,(base.height*factor).toInt())
        val baseAsset=asset(base)
        var encodedBudget=baseAsset.getString("png").length.toLong()+32_000
        require(encodedBudget<=LIMIT) { "Portable project exceeds 16 MB; reduce image or layer detail" }
        val doc = JSONObject().put("format","studyforge-layered-raster").put("version",1)
            .put("title",text(metadata.optString("title",metadata.optString("prompt").take(160)),160))
            .put("prompt",text(metadata.optString("prompt"),2000)).put("negative",text(metadata.optString("negative"),2000))
            .put("folder",text(metadata.optString("folder"),80)).put("selected",snapshot.layers.indexOfFirst { it.id == snapshot.activeLayer }.coerceAtLeast(0))
            .put("base",baseAsset)
        doc.put("layers",JSONArray().apply {
            snapshot.layers.forEach { layer ->
                val raster = Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
                try {
                    val untransformed = layer.copy(visible=true,opacity=1f,blend="normal",offsetX=0f,offsetY=0f,scale=1f,rotation=0f)
                    LayerRenderer.draw(Canvas(raster),snapshot.copy(base=null,layers=listOf(untransformed)),RectF(0f,0f,width.toFloat(),height.toFloat()))
                    val image=asset(raster)
                    encodedBudget+=image.getString("png").length
                    require(encodedBudget<=LIMIT) { "Portable project exceeds 16 MB; reduce image or layer detail" }
                    put(JSONObject().put("name",text(layer.name,80)).put("visible",layer.visible).put("opacity",layer.opacity).put("blend",layer.blend)
                        .put("transform",JSONObject().put("x",layer.offsetX).put("y",layer.offsetY).put("scale",layer.scale).put("rotation",layer.rotation)).put("image",image))
                } finally { raster.recycle() }
            }
        })
        return doc.toString().also { require(it.toByteArray(Charsets.UTF_8).size <= LIMIT) { "Portable project exceeds 16 MB" } }
    }
    fun decode(input: InputStream): Document {
        val bytes = ByteArrayOutputStream()
        input.use { source -> val buffer=ByteArray(8192);while(true) { val count=source.read(buffer);if(count<0) break;require(bytes.size().toLong()+count<=LIMIT) { "Portable project exceeds 16 MB" };bytes.write(buffer,0,count) } }
        val json = Charsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes.toByteArray())).toString()
        var depth=0;var quoted=false;var escaped=false
        json.forEach { c ->
            if(quoted) { if(escaped) escaped=false else if(c=='\\') escaped=true else if(c=='"') quoted=false }
            else when(c) { '"' -> quoted=true;'{','[' -> { depth++;require(depth<=16) { "Portable document nesting limit exceeded" } };'}',']' -> depth-- }
        }
        val doc = JSONObject(json);require(doc.getString("format")=="studyforge-layered-raster" && doc.getInt("version")==1) { "Unsupported portable project" }
        val metadata = JSONObject().put("title",text(doc.getString("title"),160)).put("prompt",text(doc.getString("prompt"),2000)).put("negative",text(doc.getString("negative"),2000)).put("folder",text(doc.getString("folder"),80))
        require(metadata.getString("folder").none { it.isISOControl() })
        val items=doc.getJSONArray("layers");require(items.length() in 1..16)
        val selected=doc.getInt("selected");require(selected in 0 until items.length())
        val allocated=mutableListOf<Bitmap>();var pixels=0L
        fun readAsset(value: JSONObject, side: Int, byteLimit: Int): Bitmap {
            val width=value.getInt("width");val height=value.getInt("height");require(width in 1..side && height in 1..side)
            val encoded=value.getString("png");require(encoded.length<=((byteLimit+2)/3)*4 && encoded.matches(Regex("[A-Za-z0-9+/]*={0,2}")))
            val data=Base64.decode(encoded,Base64.NO_WRAP);require(data.size<=byteLimit && hash(data)==value.getString("sha256")) { "Portable image checksum mismatch" }
            require(data.size>=24 && data.copyOfRange(0,8).contentEquals(byteArrayOf(-119,80,78,71,13,10,26,10))) { "Choose PNG assets" }
            val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeByteArray(data,0,data.size,bounds)
            require(bounds.outWidth==width && bounds.outHeight==height) { "Portable image dimensions mismatch" }
            return requireNotNull(BitmapFactory.decodeByteArray(data,0,data.size)).also { allocated.add(it) }
        }
        try {
            val base=readAsset(doc.getJSONObject("base"),2048,32_000_000)
            val layers=(0 until items.length()).map { i ->
                val item=items.getJSONObject(i);val transform=item.getJSONObject("transform");val image=item.getJSONObject("image")
                pixels+=image.getInt("width").toLong()*image.getInt("height");require(pixels<=8_000_000)
                ForgeCanvas.InkLayer(UUID.randomUUID().toString(),text(item.getString("name"),80).also { require(it.isNotBlank()) },item.getBoolean("visible"),item.getDouble("opacity").toFloat(),item.getString("blend"),readAsset(image,1024,5_000_000),transform.getDouble("x").toFloat(),transform.getDouble("y").toFloat(),transform.getDouble("scale").toFloat(),transform.getDouble("rotation").toFloat())
            }
            val snapshot=ForgeCanvas.Snapshot(base,emptyList(),layers,layers[selected].id)
            // Validate scalar settings without allocating another set of bitmaps.
            val withoutRasters=snapshot.copy(layers=layers.map { it.copy(raster=null) })
            DocumentCodec.decode(DocumentCodec.encode(withoutRasters))
            return Document(snapshot,metadata)
        } catch (error: Exception) { allocated.forEach { it.recycle() };throw error }
    }
}
