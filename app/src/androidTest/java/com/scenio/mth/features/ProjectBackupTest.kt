package com.scenio.mth.features

import android.content.Context
import android.graphics.Bitmap
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.ForgeCanvas
import com.scenio.mth.editor.ProjectStore
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(AndroidJUnit4::class)
class ProjectBackupTest {
    private val context=ApplicationProvider.getApplicationContext<Context>()
    @Test fun backupRetainsEditableInkAndRenamedTitle() {
        val store=ProjectStore(context)
        val bitmap=Bitmap.createBitmap(80,120,Bitmap.Config.ARGB_8888)
        val snapshot=ForgeCanvas.Snapshot(bitmap,listOf(ForgeCanvas.Stroke(mutableListOf(ForgeCanvas.Point(.2f,.3f,1f)),0xff00ff00.toInt(),.01f)))
        val id=store.save(snapshot,JSONObject().put("prompt","Test plant"));val archive=File.createTempFile("project-test-",".zip",context.cacheDir)
        var imported:String?=null
        try {
            store.rename(id,"Botany");store.archive(id,archive)
            val restored=archive.inputStream().use { store.importArchive(it) };imported=restored
            val loaded=store.load(restored)
            try {
                assertEquals("Botany",loaded.info.title)
                assertEquals(80,loaded.snapshot.base!!.width);assertEquals(120,loaded.snapshot.base!!.height)
                assertEquals(1,loaded.snapshot.strokes.size)
                assertEquals(.2f,loaded.snapshot.strokes.first().points.first().x,.0001f)
            } finally { loaded.snapshot.base?.recycle() }
        } finally { bitmap.recycle();archive.delete();store.delete(id);imported?.let { store.delete(it) } }
    }
    @Test fun backupRejectsPathTraversal() {
        val out=ByteArrayOutputStream()
        ZipOutputStream(out).use { zip -> zip.putNextEntry(ZipEntry("../escape.txt"));zip.write(byteArrayOf(1));zip.closeEntry() }
        val failure=assertThrows(Exception::class.java) { ProjectStore(context).importArchive(ByteArrayInputStream(out.toByteArray())) }
        // Android 14+ can reject traversal before app validation runs.
        assertTrue(failure is IllegalArgumentException || failure is java.util.zip.ZipException)
        assertFalse(File(context.filesDir,"projects/escape.txt").exists())
    }
}
