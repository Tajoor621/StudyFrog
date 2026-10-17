package com.scenio.mth.inference

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.scenio.mth.MainActivity
import com.scenio.mth.R
import kotlinx.coroutines.*
import java.io.File
import org.json.JSONObject
import android.os.SystemClock

class LocalGenerationWorker(context:Context,params:WorkerParameters):CoroutineWorker(context,params) {
    override suspend fun doWork():Result = supervisorScope {
        setForeground(foreground())
        val model=ModelStore(applicationContext).model
        if(!model.isFile) return@supervisorScope Result.failure(workDataOf("error" to "Import an SD 1.x GGUF model first"))
        val request=File(applicationContext.noBackupFilesDir,"local-jobs/$id.json")
        if(!request.isFile || request.length()>64_000) return@supervisorScope Result.failure(workDataOf("error" to "Local request is missing or invalid"))
        val data=try { JSONObject(request.readText()) } catch(_:Exception) { return@supervisorScope Result.failure(workDataOf("error" to "Local request could not be read")) }
        val prompt=data.optString("prompt");val negative=data.optString("negative")
        if(prompt.isBlank() || prompt.length>2000 || negative.length>2000 || '\u0000' in prompt || '\u0000' in negative) return@supervisorScope Result.failure(workDataOf("error" to "Invalid prompt"))
        val size=data.optInt("size",256);val steps=data.optInt("steps",20)
        val started=SystemClock.elapsedRealtime()
        var peakNativeBytes=0L
        val output=File(applicationContext.filesDir,"generated/$id.png").apply { parentFile?.mkdirs() }
        val task=async(Dispatchers.IO) {
            val initial=data.optString("initial").takeIf { it.isNotBlank() }?.let { name ->
                require(name=="$id-input.png")
                val file=File(applicationContext.cacheDir,name)
                val bitmap=requireNotNull(BitmapFactory.decodeFile(file.path));val scaled=Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888)
                val bounds=com.scenio.mth.editor.fitImage(size.toFloat(),size.toFloat(),bitmap.width,bitmap.height)
                android.graphics.Canvas(scaled).apply { drawColor(android.graphics.Color.BLACK);drawBitmap(bitmap,null,android.graphics.RectF(bounds.left,bounds.top,bounds.left+bounds.width,bounds.top+bounds.height),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG)) }
                try { IntArray(size*size).also { scaled.getPixels(it,0,size,0,0,size,size) } }
                finally { if(scaled!==bitmap) scaled.recycle();bitmap.recycle();file.delete() }
            }
            val pixels=NativeEngine.generate(model.path.toByteArray(),prompt.toByteArray(),negative.toByteArray(),size,steps,data.optLong("seed",42),data.optDouble("strength",.65).toFloat(),initial)
            val bitmap=Bitmap.createBitmap(pixels,size,size,Bitmap.Config.ARGB_8888)
            try { output.outputStream().use { check(bitmap.compress(Bitmap.CompressFormat.PNG,100,it)) } } finally { bitmap.recycle() }
        }
        val monitor=launch { while(isActive) { peakNativeBytes=maxOf(peakNativeBytes,android.os.Debug.getNativeHeapAllocatedSize());setProgress(workDataOf("percent" to NativeEngine.progress()));delay(500) } }
        try {
            task.await()
            val report=JSONObject().put("elapsed_ms",SystemClock.elapsedRealtime()-started).put("sampled_peak_native_heap_bytes",peakNativeBytes)
                .put("sdk",Build.VERSION.SDK_INT).put("device",Build.MODEL).put("abi",Build.SUPPORTED_ABIS.firstOrNull()).put("size",size).put("steps",steps).put("runtime_commit","f0aac524d3f29140e60ba78f3cfb16d6acea87db")
            File(output.parentFile,"benchmark-latest.json").writeText(report.toString(2))
            File(output.parentFile,"$id.json").writeText(data.toString())
            Result.success(workDataOf("path" to output.path))
        }
        catch(e:CancellationException) { NativeEngine.cancel();output.delete();throw e }
        catch(e:Exception) { output.delete();Result.failure(workDataOf("error" to (e.message ?: "Local inference failed").take(300))) }
        finally { monitor.cancel();File(applicationContext.cacheDir,"$id-input.png").delete() }
    }
    private fun foreground():ForegroundInfo {
        val context=applicationContext
        if(Build.VERSION.SDK_INT>=26) (context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(NotificationChannel("local-generation","Local generation",NotificationManager.IMPORTANCE_LOW))
        val open=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification=NotificationCompat.Builder(context,"local-generation").setSmallIcon(R.drawable.sf_ic_notification).setContentTitle("Generating on this device").setContentText("Your model, prompt and image stay on this device").setOngoing(true).setContentIntent(open)
            .addAction(0,"Cancel",WorkManager.getInstance(context).createCancelPendingIntent(id)).build()
        return if(Build.VERSION.SDK_INT>=29) ForegroundInfo(7401,notification,if(Build.VERSION.SDK_INT>=35) ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING else ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC) else ForegroundInfo(7401,notification)
    }
    companion object { const val NAME="studyforge-local-generation" }
}
