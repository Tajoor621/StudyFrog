package com.scenio.mth.features

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.scenio.mth.video.LocalVideoRemix
import com.scenio.mth.video.PersonMask
import com.scenio.mth.video.PersonModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class PhoneVideoRemixTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    private val assets=InstrumentationRegistry.getInstrumentation().context.assets
    @Test fun modelImportHashAndActualOrtInference() {
        val model=File(context.cacheDir,"person-${UUID.randomUUID()}.onnx")
        assets.open("phone-person.onnx").use { input -> model.outputStream().use { input.copyTo(it) } }
        try {
            assertTrue(PersonModel.verified(model))
            val frame=Bitmap.createBitmap(160,96,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.RED) }
            try { PersonMask(model).use { mask -> val values=mask.mask(frame);assertEquals(192*192,values.size);assertTrue(values.all { it in 0f..1f }) } }
            finally { frame.recycle() }
            model.appendText("corrupt");assertFalse(PersonModel.verified(model))
        } finally { model.delete() }
    }
    @Test(timeout=180000) fun offlineGreenVideoPreservesAudioPixelsAndTrim() {
        val source=File(context.cacheDir,"source-${UUID.randomUUID()}.mp4")
        val output=File(context.cacheDir,"remix-${UUID.randomUUID()}.mp4")
        assets.open("phone-source.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
        val bg=Bitmap.createBitmap(160,96,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            LocalVideoRemix.render(context,source,bg,output,false,0.0,1.0,{}, {})
            val extractor=MediaExtractor()
            try {
                extractor.setDataSource(output.path)
                val formats=(0 until extractor.trackCount).map { extractor.getTrackFormat(it) }
                assertTrue(formats.any { it.getString(MediaFormat.KEY_MIME)=="audio/mp4a-latm" })
                val video=formats.first { it.getString(MediaFormat.KEY_MIME)=="video/avc" }
                assertEquals(160,video.getInteger(MediaFormat.KEY_WIDTH));assertEquals(96,video.getInteger(MediaFormat.KEY_HEIGHT))
                assertTrue(kotlin.math.abs(video.getLong(MediaFormat.KEY_DURATION)-1_000_000L)<100000)
            } finally { extractor.release() }
            val reader=MediaMetadataRetriever()
            try {
                reader.setDataSource(output.path)
                val frame=reader.getFrameAtTime(100000,MediaMetadataRetriever.OPTION_CLOSEST)!!
                try { assertTrue(Color.blue(frame.getPixel(5,5))>170);assertTrue(Color.red(frame.getPixel(80,48))>170) }
                finally { frame.recycle() }
            } finally { reader.release() }
            LocalVideoRemix.render(context,source,bg,output.apply { delete() },false,0.5,0.5,{}, {})
            assertTrue(output.length()>1000)
            assets.open("phone-portrait.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
            LocalVideoRemix.render(context,source,bg,output.apply { delete() },false,0.0,1.0,{}, {})
            val portrait=MediaMetadataRetriever()
            try { portrait.setDataSource(output.path);assertEquals("96",portrait.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH));assertEquals("160",portrait.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)) }
            finally { portrait.release() }
            var checkpoints=0
            assertThrows(IllegalStateException::class.java) { LocalVideoRemix.render(context,source,bg,output.apply { delete() },false,0.0,1.0,{ if(++checkpoints>=12) error("cancelled") },{}) }
            assertFalse(output.exists())
        } finally { source.delete();output.delete();bg.recycle() }
    }
    @Test(timeout=180000) fun actualPersonVideoKeepsSilentSourceSilent() {
        val root=File(context.cacheDir,"phone-test-${UUID.randomUUID()}").apply { mkdirs() }
        val isolated=object:android.content.ContextWrapper(context) { override fun getNoBackupFilesDir()=root }
        val source=File(root,"source.mp4");val output=File(root,"result.mp4")
        val bg=Bitmap.createBitmap(160,96,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try {
            assets.open("phone-person.onnx").use { PersonModel.install(isolated,it) }
            assertThrows(IllegalArgumentException::class.java) { PersonModel.install(isolated,java.io.ByteArrayInputStream(byteArrayOf(1,2,3))) }
            assertTrue(PersonModel.verified(PersonModel.file(isolated)))
            assets.open("phone-silent.mp4").use { input -> source.outputStream().use { input.copyTo(it) } }
            LocalVideoRemix.render(isolated,source,bg,output,true,0.0,1.0,{}, {})
            val extractor=MediaExtractor()
            try { extractor.setDataSource(output.path);assertEquals(1,extractor.trackCount);assertEquals("video/avc",extractor.getTrackFormat(0).getString(MediaFormat.KEY_MIME)) }
            finally { extractor.release() }
        } finally { bg.recycle();root.deleteRecursively() }
    }
    @Test fun maskAndPortraitBoundsAreDeterministic() {
        assertEquals(480 to 852,LocalVideoRemix.dimensions(1080,1920))
        assertThrows(IllegalArgumentException::class.java) { LocalVideoRemix.dimensions(3840,2160) }
        val frame=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.GREEN) }
        val bg=Bitmap.createBitmap(16,16,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.BLUE) }
        try { val result=LocalVideoRemix.composite(frame,bg,null);try { assertEquals(Color.BLUE,result.getPixel(8,8)) } finally { result.recycle() } }
        finally { frame.recycle();bg.recycle() }
    }
}
