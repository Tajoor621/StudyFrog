package com.scenio.mth.features
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.scenio.mth.inference.NativeEngine
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class NativeBoundaryTest {
    @Test fun bundledRuntimeLoadsAndRejectsUnsafeDimensionsBeforeAllocating() {
        assertTrue(NativeEngine.available())
        assertThrows(IllegalArgumentException::class.java) { NativeEngine.generate("/missing.gguf".toByteArray(),"Plant".toByteArray(),byteArrayOf(),128,20,42,.65f,null) }
        assertTrue(NativeEngine.progress() in 0..100)
        NativeEngine.cancel()
    }
}
