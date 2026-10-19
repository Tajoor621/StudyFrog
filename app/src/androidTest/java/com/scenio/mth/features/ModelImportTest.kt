package com.scenio.mth.features

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.inference.ModelStore
import com.scenio.mth.inference.DeviceDiagnostics
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.RandomAccessFile
import java.security.MessageDigest
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ModelImportTest {
    private fun isolated(block:(ModelStore,File)->Unit) {
        val base=ApplicationProvider.getApplicationContext<Context>();val root=File(base.cacheDir,"import-test-${UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(base) { override fun getNoBackupFilesDir()=root }
        try { block(ModelStore(context),root) } finally { root.deleteRecursively() }
    }
    @Test fun invalidAndCancelledImportsPreserveExistingModel()=isolated { store,root ->
        store.model.writeText("existing");val bad=File(root,"bad.gguf").apply { writeText("not a model") }
        assertTrue(runCatching { store.import(Uri.fromFile(bad)) }.isFailure);assertEquals("existing",store.model.readText())
        assertTrue(runCatching { store.import(Uri.fromFile(bad),cancelled={true}) }.isFailure);assertEquals("existing",store.model.readText())
        assertTrue(root.listFiles().orEmpty().none { it.extension=="part" })
    }
    @Test fun checksumVerificationAndProgressWorkWithoutClaimingSyntheticFixtureIsAModel()=isolated { store,root ->
        val source=File(root,"transport-fixture.gguf")
        RandomAccessFile(source,"rw").use { it.write(byteArrayOf(71,71,85,70,3,0,0,0));it.setLength(32_000_000) }
        val digest=MessageDigest.getInstance("SHA-256");source.inputStream().use { input -> val buffer=ByteArray(1_048_576);while(true) { val n=input.read(buffer);if(n<0) break;digest.update(buffer,0,n) } }
        val hash=digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) }
        store.model.writeText("existing")
        assertTrue(runCatching { store.import(Uri.fromFile(source),"0".repeat(64)) }.isFailure);assertEquals("existing",store.model.readText())
        var staged=0L
        assertTrue(runCatching { store.import(Uri.fromFile(source),hash,cancelled={staged>2_000_000},progress={bytes,_-> staged=bytes}) }.isFailure)
        assertEquals("existing",store.model.readText())
        var copied=0L;store.import(Uri.fromFile(source),hash,progress={bytes,_-> copied=bytes})
        assertEquals(32_000_000L,copied);assertEquals(32_000_000L,store.model.length());assertTrue(store.description().contains(hash.take(16)))
        assertTrue(File(root,"models").listFiles().orEmpty().none { it.extension=="part" })
    }
    @Test fun diagnosticsDescribeMemoryWithoutClaimingClinicalOrQualityEvidence() {
        val snapshot=DeviceDiagnostics.snapshot(ApplicationProvider.getApplicationContext())
        assertTrue(snapshot.getLong("total_ram_bytes")>0);assertTrue(snapshot.getLong("process_pss_bytes")>0);assertTrue(snapshot.has("thermal_status"))
    }
}
