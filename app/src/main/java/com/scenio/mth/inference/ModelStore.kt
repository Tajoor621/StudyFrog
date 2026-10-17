package com.scenio.mth.inference

import android.content.Context
import android.net.Uri
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

class ModelStore(private val context:Context) {
    private val root=File(context.noBackupFilesDir,"models").apply { mkdirs() }
    val model:File get()=File(root,"image.gguf")
    val metadata:File get()=File(root,"image.json")
    fun description():String = if(!model.isFile) "No model imported" else runCatching {
        val data=JSONObject(metadata.readText());"${model.length()/1_048_576} MB · SHA-256 ${data.getString("sha256").take(16)}"
    }.getOrDefault("Imported local model")
    fun import(uri:Uri):String {
        val temporary=File(root,"import.part");val digest=MessageDigest.getInstance("SHA-256");var total=0L
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val magic=ByteArray(4);java.io.DataInputStream(input).readFully(magic);require(String(magic,Charsets.US_ASCII)=="GGUF") { "Choose a complete SD 1.x GGUF model" }
                temporary.outputStream().use { out ->
                    out.write(magic);digest.update(magic);total=4
                    val buffer=ByteArray(1_048_576)
                    while(true) { val count=input.read(buffer);if(count<0) break;total+=count;require(total<=4_294_967_296L) { "Model exceeds 4 GB" };require(root.usableSpace>buffer.size*2) { "Not enough storage" };digest.update(buffer,0,count);out.write(buffer,0,count) }
                }
            } ?: error("Cannot open model")
            require(total>=32_000_000) { "The model is incomplete or too small" }
            val hash=digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
            check(temporary.renameTo(model)) { "Could not install model" }
            metadata.writeText(JSONObject().put("sha256",hash).put("bytes",total).toString());return description()
        } finally { temporary.delete() }
    }
    fun remove() { check(!model.exists() || model.delete());metadata.delete() }
}
