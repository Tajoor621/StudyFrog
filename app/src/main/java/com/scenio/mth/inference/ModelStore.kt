package com.scenio.mth.inference

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

class ModelStore(private val context:Context) {
    private val root=File(context.noBackupFilesDir,"models").apply { mkdirs() }
    val model:File get()=File(root,"image.gguf")
    val metadata:File get()=File(root,"image.json")
    fun description():String = if(!model.isFile) "No model imported · photo editing and bundled science work without one" else runCatching {
        val data=JSONObject(AtomicFile(metadata).openRead().bufferedReader().use { it.readText() })
        require(data.getLong("bytes")==model.length())
        "${model.length()/1_048_576} MiB · SHA-256 ${data.getString("sha256").take(16)} · GGUF verified; model compatibility is checked during generation"
    }.getOrDefault("Local model present · import again to refresh its checksum")
    fun import(uri:Uri,expectedSHA256:String="",cancelled:()->Boolean={false},progress:(Long,Long?)->Unit={_,_->}):String = synchronized(lock) {
        val expected=expectedSHA256.trim().lowercase(java.util.Locale.ROOT)
        require(expected.isEmpty() || expected.matches(Regex("[0-9a-f]{64}"))) { "Use a complete 64-character SHA-256 checksum or leave it blank" }
        fun checkCancelled() { if(cancelled() || Thread.currentThread().isInterrupted) throw java.util.concurrent.CancellationException("Model import cancelled; previous model retained") }
        checkCancelled()
        root.listFiles().orEmpty().filter { file -> file.isFile && file.extension=="part" && (file.name=="import.part" || runCatching { UUID.fromString(file.nameWithoutExtension) }.isSuccess) && Uri.fromFile(file)!=uri }.forEach {
            check(it.delete()) { "Cannot clean an interrupted model import; previous model retained" }
        }
        val declared=runCatching { context.contentResolver.query(uri,arrayOf(OpenableColumns.SIZE),null,null,null)?.use { c -> if(c.moveToFirst() && !c.isNull(0)) c.getLong(0).takeIf { it>=0 } else null } }.getOrNull()
        require(declared==null || declared in 32_000_000..4_294_967_296L) { "Choose a complete model between 32 MB and 4 GB" }
        require(root.usableSpace>(declared ?: 32_000_000L)+64_000_000L) { "Free space for the model plus 64 MB before importing" }
        val temporary=File(root,"${UUID.randomUUID()}.part");val digest=MessageDigest.getInstance("SHA-256");var total=0L
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val header=ByteArray(8);try { java.io.DataInputStream(input).readFully(header) } catch(_:java.io.EOFException) { error("File is truncated; choose a complete local SD 1.x GGUF") }
                val version=java.nio.ByteBuffer.wrap(header,4,4).order(java.nio.ByteOrder.LITTLE_ENDIAN).int
                require(String(header,0,4,Charsets.US_ASCII)=="GGUF" && version in 2..3) { "Choose a GGUF v2/v3 file converted for SD 1.x, not an LLM, SDXL or FLUX model" }
                java.io.FileOutputStream(temporary).use { out ->
                    out.write(header);digest.update(header);total=8;progress(total,declared)
                    val buffer=ByteArray(1_048_576)
                    while(true) {
                        checkCancelled();val count=input.read(buffer);if(count<0) break
                        total+=count;require(total<=4_294_967_296L) { "Model exceeds 4 GB" }
                        require(root.usableSpace>buffer.size+64_000_000L) { "Not enough storage; previous model retained" }
                        digest.update(buffer,0,count);out.write(buffer,0,count);progress(total,declared)
                    }
                    out.fd.sync()
                }
            } ?: error("Cannot open model; choose a locally available file")
            checkCancelled();require(total>=32_000_000 && (declared==null || total==declared)) { "Incomplete model; previous model retained" }
            val hash=digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            require(expected.isEmpty() || expected==hash) { "Checksum mismatch; previous model retained" }
            // Invalidate the old receipt before replacement, including the crash window.
            val file=AtomicFile(metadata);file.delete();check(!metadata.exists()) { "Cannot invalidate old checksum; previous model retained" }
            check(temporary.renameTo(model)) { "Could not replace model; previous model retained" }
            val stream=file.startWrite()
            try { stream.write(JSONObject().put("sha256",hash).put("bytes",total).toString().toByteArray());file.finishWrite(stream) }
            catch(e:Exception) { file.failWrite(stream);throw e }
            progress(total,total);description()
        } finally { temporary.delete() }
    }
    fun remove() = synchronized(lock) { check(!model.exists() || model.delete());AtomicFile(metadata).delete() }
    companion object { private val lock=Any() }
}
