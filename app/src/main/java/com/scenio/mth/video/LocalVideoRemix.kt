package com.scenio.mth.video

import android.content.Context
import android.graphics.*
import android.media.*
import android.os.Build
import android.os.PowerManager
import java.io.File
import java.nio.ByteBuffer
import kotlin.math.*

/** First AAC track is copied on its source timeline; silent sources stay silent. */
class AudioClip(private val extractor:MediaExtractor, val format:MediaFormat, private val start:Long,private val duration:Long):AutoCloseable {
    fun write(muxer:MediaMuxer,track:Int,checkpoint:()->Unit) {
        extractor.seekTo(start,MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
        val buffer=ByteBuffer.allocateDirect(1_048_576);val info=MediaCodec.BufferInfo()
        while(extractor.sampleTime>=0) {
            checkpoint();val time=extractor.sampleTime
            if(time>=start+duration) break
            if(time>=start) {
                buffer.clear();val size=extractor.readSampleData(buffer,0);if(size<0) break
                info.set(0,size,time-start,if(extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC!=0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0)
                muxer.writeSampleData(track,buffer,info)
            }
            if(!extractor.advance()) break
        }
    }
    override fun close()=extractor.release()
    companion object {
        fun open(file:File,start:Long,duration:Long):AudioClip? {
            val extractor=MediaExtractor()
            try {
                extractor.setDataSource(file.path)
                val index=(0 until extractor.trackCount).firstOrNull { extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("audio/")==true }
                if(index==null) { extractor.release();return null }
                val format=extractor.getTrackFormat(index)
                require(format.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm") { "Phone remix requires AAC source audio; use the computer engine for other sound formats" }
                extractor.selectTrack(index);return AudioClip(extractor,format,start,duration)
            } catch(error:Throwable) { extractor.release();throw error }
        }
    }
}

object LocalVideoRemix {
    fun dimensions(w:Int,h:Int):Pair<Int,Int> {
        require(w in 16..1920 && h in 16..1920 && w.toLong()*h<=2_073_600) { "Phone remix accepts SDR video up to 1080p; use the computer engine for larger sources" }
        val scale=min(1.0,min((if(h>w) 480 else 854).toDouble()/w,(if(h>w) 854 else 480).toDouble()/h))
        val width=(w*scale).toInt()/2*2;val height=(h*scale).toInt()/2*2
        require(min(width,height)>=16) { "Source aspect ratio is too narrow" };return width to height
    }
    fun composite(frame:Bitmap,background:Bitmap,person:PersonMask?):Bitmap {
        val w=frame.width;val h=frame.height
        val output=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);val canvas=Canvas(output)
        val ratio=max(w.toFloat()/background.width,h.toFloat()/background.height)
        canvas.drawBitmap(background,null,RectF((w-background.width*ratio)/2,(h-background.height*ratio)/2,(w+background.width*ratio)/2,(h+background.height*ratio)/2),Paint(Paint.FILTER_BITMAP_FLAG))
        val mask=if(person!=null) {
            val values=person.mask(frame);Bitmap.createBitmap(IntArray(values.size) { if(values[it]>0.5f) Color.WHITE else Color.TRANSPARENT },192,192,Bitmap.Config.ARGB_8888)
        } else {
            val pixels=IntArray(w*h);frame.getPixels(pixels,0,w,0,0,w,h);val hsv=FloatArray(3)
            for(i in pixels.indices) { Color.colorToHSV(pixels[i],hsv);pixels[i]=if(hsv[0] in 70f..170f && hsv[1]>=0.24f && hsv[2]>=0.16f) Color.TRANSPARENT else Color.WHITE }
            Bitmap.createBitmap(pixels,w,h,Bitmap.Config.ARGB_8888)
        }
        try {
            val layer=canvas.saveLayer(0f,0f,w.toFloat(),h.toFloat(),null)
            canvas.drawBitmap(frame,0f,0f,null)
            canvas.drawBitmap(mask,null,Rect(0,0,w,h),Paint(Paint.FILTER_BITMAP_FLAG).apply { xfermode=PorterDuffXfermode(PorterDuff.Mode.DST_IN) })
            canvas.restoreToCount(layer)
        } finally { mask.recycle() }
        return output
    }
    fun render(context:Context,source:File,background:Bitmap,output:File,personMode:Boolean,start:Double,seconds:Double,checkpoint:()->Unit,progress:(Int)->Unit) {
        require(start.isFinite() && start in 0.0..3600.0 && seconds.isFinite() && seconds in 0.5..15.0)
        require(source.length() in 1..64_000_000L);require(context.cacheDir.usableSpace>512_000_000) { "Free at least 512 MB for phone remix" }
        val retriever=MediaMetadataRetriever();var model:PersonMask?=null;var audio:AudioClip?=null
        val deadline=android.os.SystemClock.elapsedRealtime()+1_800_000
        fun guard() {
            checkpoint();require(android.os.SystemClock.elapsedRealtime()<deadline) { "Phone remix exceeded 30 minutes; use a shorter clip" }
            val memory=android.app.ActivityManager.MemoryInfo();(context.getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(memory)
            check(!memory.lowMemory && memory.availMem>384_000_000) { "Stopped for low memory" }
            if(Build.VERSION.SDK_INT>=29) check((context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus<PowerManager.THERMAL_STATUS_SEVERE) { "Stopped because the phone is too hot" }
        }
        try {
            guard();retriever.setDataSource(source.path)
            val rawW=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: error("No video width")
            val rawH=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: error("No video height")
            val rotation=retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val swap=abs(rotation)%180==90
            val (w,h)=dimensions(if(swap) rawH else rawW,if(swap) rawW else rawH)
            val duration=(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toDoubleOrNull() ?: error("Unknown duration"))/1000
            require(duration.isFinite() && duration<=3600 && start<duration) { "Invalid source duration or clip start" }
            val clip=min(seconds,duration-start);require(clip>=0.5)
            val frames=ceil(clip*15).toInt();val actual=frames*1_000_000L/15;val from=(start*1_000_000).toLong()
            val media=MediaExtractor()
            try {
                media.setDataSource(source.path)
                val format=(0 until media.trackCount).map { media.getTrackFormat(it) }.first { it.getString(MediaFormat.KEY_MIME)?.startsWith("video/")==true }
                if(format.containsKey(MediaFormat.KEY_COLOR_TRANSFER)) require(format.getInteger(MediaFormat.KEY_COLOR_TRANSFER) !in listOf(6,7)) { "Export HDR as SDR before phone remix" }
            } finally { media.release() }
            model=if(personMode) PersonMask(PersonModel.file(context)) else null
            audio=AudioClip.open(source,from,actual)
            BitmapVideoEncoder.render(output,w,h,15,frames,{ index ->
                guard()
                val time=from+index*1_000_000L/15
                val decoded=if(Build.VERSION.SDK_INT>=27) retriever.getScaledFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST,w,h) else retriever.getFrameAtTime(time,MediaMetadataRetriever.OPTION_CLOSEST)
                requireNotNull(decoded) { "Cannot decode source frame" }
                try {
                    val frame=Bitmap.createScaledBitmap(decoded,w,h,true)
                    try { composite(frame,background,model) } finally { if(frame!==decoded) frame.recycle() }
                } finally { decoded.recycle() }
            },::guard,progress,audio)
            guard()
        } catch(error:Throwable) { output.delete();throw error }
        finally { audio?.close();model?.close();retriever.release() }
    }
}
