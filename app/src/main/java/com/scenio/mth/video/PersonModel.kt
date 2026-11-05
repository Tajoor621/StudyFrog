package com.scenio.mth.video

import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import ai.onnxruntime.TensorInfo
import android.content.Context
import android.graphics.Bitmap
import java.io.File
import java.io.InputStream
import java.nio.FloatBuffer
import java.security.MessageDigest
import java.util.UUID

/** Only the published Apache-2.0 OpenCV Zoo FP32 model is accepted. */
object PersonModel {
    const val SHA256="552d8a984054e59b5d773d24b9b12022b22046ceb2bbc4c9aaeaceb36a9ddf24"
    const val BYTES=6_163_938L
    fun file(context:Context)=File(context.noBackupFilesDir,"video-person.onnx")
    fun verified(file:File):Boolean = file.isFile && file.length()==BYTES && file.inputStream().use { input ->
        val digest=MessageDigest.getInstance("SHA-256");val buffer=ByteArray(65536)
        while(true) { val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n) }
        digest.digest().joinToString("") { "%02x".format(it) }==SHA256
    }
    @Synchronized fun install(context:Context,input:InputStream,checkpoint:()->Unit={}) {
        val stage=File(context.noBackupFilesDir,"person-${UUID.randomUUID()}.part")
        try {
            var size=0L
            stage.outputStream().use { output ->
                val buffer=ByteArray(65536)
                while(true) { checkpoint();val n=input.read(buffer);if(n<0) break;size+=n;require(size<=BYTES) { "Choose the 6.16 MB PPHumanSeg FP32 ONNX, not a GGUF or pointer file" };output.write(buffer,0,n) }
                output.fd.sync()
            }
            require(verified(stage)) { "Person model checksum mismatch; use the model linked in Help" }
            checkpoint();check(stage.renameTo(file(context))) { "Could not install person model" }
        } finally { stage.delete() }
    }
}

class PersonMask(file:File):AutoCloseable {
    private val env=OrtEnvironment.getEnvironment()
    private val session:OrtSession
    init {
        require(PersonModel.verified(file)) { "Import the verified PPHumanSeg ONNX first" }
        session=OrtSession.SessionOptions().use { options -> options.setIntraOpNumThreads(2);options.setInterOpNumThreads(1);env.createSession(file.path,options) }
    }
    fun mask(frame:Bitmap):FloatArray {
        val scaled=Bitmap.createScaledBitmap(frame,192,192,true)
        val pixels=IntArray(192*192)
        try { scaled.getPixels(pixels,0,192,0,0,192,192) } finally { if(scaled!==frame) scaled.recycle() }
        val plane=pixels.size;val rgb=FloatArray(plane*3)
        for(i in pixels.indices) { val p=pixels[i];rgb[i]=((p shr 16) and 255)/127.5f-1;rgb[plane+i]=((p shr 8) and 255)/127.5f-1;rgb[plane*2+i]=(p and 255)/127.5f-1 }
        OnnxTensor.createTensor(env,FloatBuffer.wrap(rgb),longArrayOf(1,3,192,192)).use { tensor ->
            session.run(mapOf(session.inputNames.single() to tensor)).use { result ->
                val output=result[0] as? OnnxTensor ?: error("Invalid segmentation output")
                require((output.info as TensorInfo).shape.contentEquals(longArrayOf(1,2,192,192))) { "Incompatible segmentation output" }
                val scores=FloatArray(plane*2);output.floatBuffer.get().get(scores)
                require(scores.all { it.isFinite() }) { "Invalid mask values" }
                return FloatArray(plane) { if(scores[it+plane]>scores[it]) 1f else 0f }
            }
        }
    }
    override fun close()=session.close()
}
