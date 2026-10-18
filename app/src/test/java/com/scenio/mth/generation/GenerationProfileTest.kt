package com.scenio.mth.generation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class GenerationProfileTest {
    @Test fun defaultPreservesOriginalPromptAndDirectionsNeverTruncateIt() {
        val original="  A fern with clear venation  "
        assertEquals(original,GenerationProfile().apply(original))
        val styled=GenerationProfile(true,"scientific",90,20,true).apply(original)
        assertTrue(styled.startsWith(original));assertTrue(styled.contains("fine detail"));assertTrue(styled.contains("flat illustrative"));assertTrue(styled.contains("human review required"))
    }
    @Test fun invalidControlsAndOverlongEffectivePromptsFailClearly() {
        assertThrows(IllegalArgumentException::class.java) { GenerationProfile(style="unknown") }
        assertThrows(IllegalArgumentException::class.java) { GenerationProfile(detail=-1) }
        assertThrows(IllegalArgumentException::class.java) { GenerationProfile(true).apply("x".repeat(2000)) }
    }
}
