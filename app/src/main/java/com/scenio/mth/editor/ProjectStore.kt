package com.scenio.mth.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.AtomicFile
import com.scenio.mth.ForgeCanvas
import org.json.JSONObject
import java.io.File
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ProjectInfo(val id: String, val title: String, val meta: JSONObject)
data class LoadedProject(val info: ProjectInfo, val snapshot: ForgeCanvas.Snapshot)

class ProjectStore(context: Context) {
    private val root=File(context.filesDir,"projects").apply { mkdirs() }
    private val allowed=setOf("project.json","image.png","base.png","ink.json")
    private fun directory(id: String): File {
        require(UUID.fromString(id).toString()==id) { "Invalid project identifier" }
        return File(root,id)
    }
    private val pointer=File(context.filesDir,"editor-draft.json")
    fun autosave(snapshot:ForgeCanvas.Snapshot,meta:JSONObject) {
        draftExecutor.execute { runCatching {
            val old=runCatching { JSONObject(pointer.readText()).getString("id") }.getOrNull()
            val id=save(snapshot,meta.put("draft",true));val file=AtomicFile(pointer);val stream=file.startWrite()
            try { stream.write(JSONObject().put("id",id).toString().toByteArray());file.finishWrite(stream) }
            catch(e:Exception) { file.failWrite(stream);throw e }
            if(old!=null && old!=id) runCatching { delete(old) }
        } }
    }
    fun loadDraft():LoadedProject? = runCatching { load(JSONObject(pointer.readText()).getString("id")) }.getOrNull()
    companion object { private val draftExecutor=java.util.concurrent.Executors.newSingleThreadExecutor() }
    fun list(): List<ProjectInfo> = root.listFiles().orEmpty().filter { it.isDirectory && runCatching { UUID.fromString(it.name).toString()==it.name }.getOrDefault(false) }
        .mapNotNull { dir -> runCatching {
            val meta=JSONObject(File(dir,"project.json").readText())
            ProjectInfo(dir.name,meta.optString("title",meta.optString("prompt","Untitled project")).take(160),meta)
        }.getOrNull() }.filter { !it.meta.optBoolean("draft",false) }.sortedByDescending { it.meta.optLong("created",0) }
    fun save(snapshot: ForgeCanvas.Snapshot, meta: JSONObject): String {
        requireNotNull(snapshot.base) { "Import or generate an image first" }
        require(snapshot.strokes.sumOf { it.points.size }<=100_000) { "Ink point limit reached" }
        val id=UUID.randomUUID().toString();val pending=File(root,"pending-$id").apply { mkdirs() }
        try {
            File(pending,"base.png").outputStream().use { require(snapshot.base.compress(Bitmap.CompressFormat.PNG,100,it)) }
            val preview=ForgeCanvas.render(snapshot)
            try { File(pending,"image.png").outputStream().use { require(preview.compress(Bitmap.CompressFormat.PNG,100,it)) } }
            finally { preview.recycle() }
            File(pending,"ink.json").writeText(ForgeCanvas.encodeInk(snapshot))
            meta.put("schema",2).put("created",System.currentTimeMillis()).put("flattened",false)
            File(pending,"project.json").writeText(meta.toString())
            check(pending.renameTo(directory(id))) { "Could not commit project" }
            return id
        } finally { pending.deleteRecursively() }
    }
    fun load(id: String): LoadedProject {
        val dir=directory(id);val meta=JSONObject(File(dir,"project.json").readText())
        val baseFile=File(dir,"base.png").takeIf { it.isFile } ?: File(dir,"image.png")
        val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true };BitmapFactory.decodeFile(baseFile.path,bounds)
        require(bounds.outWidth>0 && bounds.outHeight>0 && bounds.outWidth.toLong()*bounds.outHeight<=16_000_000) { "Invalid project image" }
        val options=BitmapFactory.Options().apply { inSampleSize=1 }
        while(bounds.outWidth/options.inSampleSize>2048 || bounds.outHeight/options.inSampleSize>2048) options.inSampleSize*=2
        val bitmap=requireNotNull(BitmapFactory.decodeFile(baseFile.path,options)) { "Project image is missing" }
        val ink=try { File(dir,"ink.json").takeIf { it.isFile }?.let { ForgeCanvas.decodeInk(it.readText()) }.orEmpty() } catch(e:Exception) { bitmap.recycle();throw e }
        return LoadedProject(ProjectInfo(id,meta.optString("title",meta.optString("prompt","Untitled project")),meta),ForgeCanvas.Snapshot(bitmap,ink))
    }
    fun rename(id: String, title: String) {
        require(title.isNotBlank() && title.length<=160)
        val file=File(directory(id),"project.json");val meta=JSONObject(file.readText()).put("title",title.trim())
        val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try { stream.write(meta.toString().toByteArray());atomic.finishWrite(stream) }
        catch(e:Exception) { atomic.failWrite(stream);throw e }
    }
    fun delete(id: String) { check(directory(id).deleteRecursively()) { "Could not delete project" } }
    fun archive(id: String, output: File) {
        val dir=directory(id);require(File(dir,"project.json").isFile)
        ZipOutputStream(output.outputStream().buffered()).use { zip ->
            allowed.sorted().forEach { name -> val file=File(dir,name);if(file.isFile) {
                zip.putNextEntry(ZipEntry(name));file.inputStream().use { it.copyTo(zip) };zip.closeEntry()
            } }
        }
    }
    fun importArchive(input: java.io.InputStream): String {
        val id=UUID.randomUUID().toString();val pending=File(root,"pending-$id").apply { mkdirs() }
        try {
            var bytes=0L;val seen=mutableSetOf<String>()
            ZipInputStream(input.buffered()).use { zip ->
                while(true) {
                    val entry=zip.nextEntry ?: break
                    require(!entry.isDirectory && entry.name in allowed && seen.add(entry.name)) { "Invalid backup entry" }
                    var entryBytes=0L
                    val limit=when(entry.name) { "project.json" -> 512_000; "ink.json" -> 8_000_000; else -> 24_000_000 }
                    File(pending,entry.name).outputStream().use { out ->
                        val buffer=ByteArray(8192)
                        while(true) { val n=zip.read(buffer);if(n<0) break;bytes+=n;entryBytes+=n;require(bytes<=64_000_000 && entryBytes<=limit) { "Backup exceeds 64 MB" };out.write(buffer,0,n) }
                    }
                    zip.closeEntry()
                }
            }
            require("project.json" in seen && "image.png" in seen) { "Backup is incomplete" }
            val metadata=JSONObject(File(pending,"project.json").readText())
            require(metadata.optInt("schema",1) in 1..2) { "Unsupported backup version" }
            metadata.put("created",System.currentTimeMillis()).put("draft",false);File(pending,"project.json").writeText(metadata.toString())
            check(pending.renameTo(directory(id))) { "Could not import backup" }
            try { load(id).snapshot.base?.recycle() } catch(e:Exception) { directory(id).deleteRecursively();throw e }
            return id
        } finally { pending.deleteRecursively() }
    }
}
