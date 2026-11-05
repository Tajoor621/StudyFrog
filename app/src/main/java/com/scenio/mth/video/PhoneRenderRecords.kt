package com.scenio.mth.video

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.os.SystemClock
import android.util.AtomicFile
import com.scenio.mth.inference.DeviceDiagnostics
import org.json.JSONObject
import java.io.File
import java.util.UUID

/** Private journal for one active attempt and one retained completed result. */
class PhoneRenderRecords(private val context:Context,val directory:File) {
    data class Run(val id:String,val source:File,val output:File,val started:Long)
    private var active:String?=null
    val reportFile:File get()=File(directory,"report.json")
    private val latestFile:File get()=File(directory,"latest.json")
    private fun id(value:String):String { require(UUID.fromString(value).toString()==value) { "Invalid phone-job identity" };return value }
    private fun output(value:String)=File(directory,"result-${id(value)}.mp4")
    private fun source(value:String)=File(directory,"source-${id(value)}.mp4")
    private fun read(file:File):JSONObject? {
        val atomic=AtomicFile(file)
        if(!file.exists() && !File(file.path+".bak").exists()) return null
        val text=atomic.openRead().bufferedReader().use { it.readText() }
        require(text.length<=100000) { "Oversized phone-job record" }
        return JSONObject(text).also { require(it.getInt("schema")==1);id(it.getString("run_id")) }
    }
    private fun write(file:File,value:JSONObject) {
        directory.mkdirs();val atomic=AtomicFile(file);val stream=atomic.startWrite()
        try { stream.write(value.toString(2).toByteArray());atomic.finishWrite(stream) }
        catch(error:Throwable) { atomic.failWrite(stream);throw error }
    }
    private fun validate(file:File):JSONObject {
        require(file.isFile && file.length() in 1..64_000_000L) { "Saved phone video is unavailable or excessive" }
        val media=MediaExtractor()
        try {
            media.setDataSource(file.path)
            val formats=(0 until media.trackCount).map { media.getTrackFormat(it) }
            val video=formats.first { it.getString(MediaFormat.KEY_MIME)=="video/avc" }
            val w=video.getInteger(MediaFormat.KEY_WIDTH);val h=video.getInteger(MediaFormat.KEY_HEIGHT);val duration=video.getLong(MediaFormat.KEY_DURATION)
            require(w in 16..854 && h in 16..854 && minOf(w,h)<=480 && duration in 400000..15_200_000)
            return JSONObject().put("width",w).put("height",h).put("duration_us",duration).put("bytes",file.length())
                .put("audio_present",formats.any { it.getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true })
        } finally { media.release() }
    }
    private fun latestRecord():JSONObject? = read(latestFile)?.also { require(it.getString("status")=="done");validate(output(it.getString("run_id"))) }
    @Synchronized fun recover() {
        directory.mkdirs();if(active!=null) return
        val latest=latestRecord() // Corrupt receipts fail closed; never export an arbitrary path.
        val current=read(reportFile)
        if(current?.optString("status")=="running") {
            val value=if(latest?.getString("run_id")==current.getString("run_id")) latest else current.apply {
                put("status","interrupted");put("elapsed_ms",JSONObject.NULL);put("recovered_after_process_end",true)
            }
            write(reportFile,value!!)
        }
        val keep=latest?.getString("run_id")
        directory.listFiles()?.forEach { file ->
            val name=file.name
            val candidate=Regex("(?:source|result)-([0-9a-f-]{36})\\.mp4").matchEntire(name)
            if(candidate!=null && runCatching { id(candidate.groupValues[1]) }.isSuccess && name!="result-$keep.mp4") file.delete()
        }
    }
    @Synchronized fun begin(mode:String,start:Double,seconds:Double):Run {
        check(active==null) { "A phone video job is still running or cancelling" };recover()
        require(mode in listOf("person","green") && start.isFinite() && start in 0.0..3600.0 && seconds.isFinite() && seconds in 0.5..15.0)
        require(directory.usableSpace>512_000_000) { "Free at least 512 MB for phone remix" }
        val value=UUID.randomUUID().toString();val run=Run(value,source(value),output(value),SystemClock.elapsedRealtime())
        val record=JSONObject().put("schema",1).put("kind","phone_remix_attempt").put("run_id",value).put("status","running")
            .put("platform","android").put("build_version",com.scenio.mth.BuildConfig.VERSION_NAME).put("started_at_ms",System.currentTimeMillis())
            .put("mode",mode).put("start_seconds",start).put("requested_seconds",seconds).put("profile","mobile")
            .put("before",DeviceDiagnostics.snapshot(context)).put("quality_assessed",false).put("physical_execution_verified",false)
            .put("elapsed_scope","source copy, segmentation, encode and audio mux; before/after snapshots are not peak memory")
            .put("requested_model_sha256",if(mode=="person") PersonModel.SHA256 else JSONObject.NULL)
        write(reportFile,record);active=value;return run
    }
    @Synchronized fun complete(run:Run) {
        check(active==run.id);val current=read(reportFile) ?: error("Missing run journal");check(current.getString("run_id")==run.id)
        val previous=latestRecord()?.getString("run_id")
        val media=validate(run.output)
        current.put("status","done").put("elapsed_ms",SystemClock.elapsedRealtime()-run.started).put("after",DeviceDiagnostics.snapshot(context)).put("output",media)
        try {
            // A durable latest receipt proves completion if the process ends before report replacement.
            write(latestFile,current);write(reportFile,current)
            if(previous!=null && previous!=run.id) output(previous).delete()
        } finally { active=null;run.source.delete() }
    }
    @Synchronized fun fail(run:Run,cancelled:Boolean,errorType:String) {
        if(active!=run.id) return
        try {
            val current=read(reportFile) ?: return
            current.put("status",if(cancelled) "cancelled" else "failed").put("elapsed_ms",SystemClock.elapsedRealtime()-run.started)
                .put("after",DeviceDiagnostics.snapshot(context)).put("error_type",errorType.take(100))
            write(reportFile,current)
        } finally { active=null;run.source.delete();run.output.delete() }
    }
    @Synchronized fun latest():File? { recover();return latestRecord()?.let { output(it.getString("run_id")) } }
    @Synchronized fun deleteLatest() {
        check(active==null) { "Finish the phone job before deleting its saved result" }
        latestRecord()?.let { record ->
            output(record.getString("run_id")).delete();AtomicFile(latestFile).delete()
            read(reportFile)?.takeIf { it.getString("run_id")==record.getString("run_id") }?.let { write(reportFile,it.put("result_deleted_by_user",true)) }
        }
    }
    @Synchronized fun clear() {
        check(active==null) { "Finish the phone job before clearing its history" }
        directory.listFiles()?.forEach { file -> if(!file.isDirectory) check(file.delete()) { "Could not clear phone-video history" } }
    }
    companion object {
        private var instance:PhoneRenderRecords?=null
        @Synchronized fun shared(context:Context):PhoneRenderRecords = instance ?: PhoneRenderRecords(context.applicationContext,File(context.noBackupFilesDir,"phone-video-jobs")).also { instance=it }
    }
}
