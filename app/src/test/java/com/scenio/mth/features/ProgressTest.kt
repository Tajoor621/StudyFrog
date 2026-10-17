package com.scenio.mth.features

import com.scenio.mth.features.data.StudyProgress
import com.scenio.mth.features.data.fraction
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class ProgressTest {
    @Test fun `fraction represents persisted steps`() {
        assertEquals(0.5f, StudyProgress("mitosis-v1", 2, 4, 0).fraction)
    }
    @Test fun `invalid progress cannot enter repository`() {
        assertThrows(IllegalArgumentException::class.java) { StudyProgress("p", 4, 3, 0) }
        assertThrows(IllegalArgumentException::class.java) { StudyProgress("p", 0, 0, 0) }
        assertThrows(IllegalArgumentException::class.java) { StudyProgress(" ", 0, 3, 0) }
    }
}
