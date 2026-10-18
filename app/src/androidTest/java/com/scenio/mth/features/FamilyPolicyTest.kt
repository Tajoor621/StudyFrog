package com.scenio.mth.features

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.family.FamilyPolicy
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FamilyPolicyTest {
    private fun isolated(block:(Context)->Unit) {
        val base=ApplicationProvider.getApplicationContext<Context>();val folder=File(base.cacheDir,"family-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val context=object:ContextWrapper(base) { override fun getNoBackupFilesDir()=folder }
        try { block(context) } finally { folder.deleteRecursively() }
    }
    @Test fun lockAndChangedPinPersistAndNeverStorePlainPin() = isolated { context ->
        assertFalse(FamilyPolicy.restricted(context));FamilyPolicy.configureAndLock(context,"003621")
        assertTrue(FamilyPolicy.restricted(context));assertTrue(FamilyPolicy.status(context).configured)
        val file=File(context.noBackupFilesDir,"family-policy.json");assertFalse(file.readText().contains("003621"))
        FamilyPolicy.changePin(context,"003621","009821")
        assertThrows(IllegalStateException::class.java) { FamilyPolicy.setRestricted(context,"003621",false) }
        assertTrue(FamilyPolicy.restricted(context));FamilyPolicy.setRestricted(context,"009821",false);assertFalse(FamilyPolicy.restricted(context))
        FamilyPolicy.setRestricted(context,"009821",true);assertTrue(FamilyPolicy.restricted(context))
    }
    @Test fun fifthFailurePersistsCooldownAndDamagedRecordFailsClosed() = isolated { context ->
        FamilyPolicy.configureAndLock(context,"003621")
        repeat(5) { assertThrows(IllegalStateException::class.java) { FamilyPolicy.setRestricted(context,"000000",false) } }
        assertTrue(FamilyPolicy.status(context).blockedUntil>System.currentTimeMillis())
        assertThrows(IllegalStateException::class.java) { FamilyPolicy.setRestricted(context,"003621",false) }
        File(context.noBackupFilesDir,"family-policy.json").writeText("damaged")
        assertTrue(FamilyPolicy.status(context).configured);assertTrue(FamilyPolicy.restricted(context))
    }
}
