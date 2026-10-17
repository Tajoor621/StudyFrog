package com.scenio.mth.editor

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class CanvasGeometryTest {
    @Test fun portraitPhotosRemainProportionalAndCentered() {
        val box=fitImage(1000f,500f,400,800)
        assertEquals(250f,box.width);assertEquals(500f,box.height)
        assertEquals(375f,box.left);assertEquals(0f,box.top)
        assertFalse(box.contains(100f,250f));assertTrue(box.contains(500f,250f))
    }
    @Test fun landscapeImagesAreLetterboxedWithoutCropping() {
        val box=fitImage(400f,800f,1600,800)
        assertEquals(400f,box.width);assertEquals(200f,box.height)
        assertEquals(300f,box.top);assertEquals(0f,box.left)
    }
    @Test fun invalidDimensionsFailBeforeAllocatingImages() {
        assertThrows(IllegalArgumentException::class.java) { fitImage(0f,10f,100,100) }
        assertThrows(IllegalArgumentException::class.java) { fitImage(100f,10f,-1,100) }
    }
}
