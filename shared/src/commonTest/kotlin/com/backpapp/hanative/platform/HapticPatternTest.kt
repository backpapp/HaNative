package com.backpapp.hanative.platform

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class HapticPatternTest {

    private val allPatterns = listOf(
        HapticPattern.ToggleOn,
        HapticPattern.ToggleOff,
        HapticPattern.StepperInc,
        HapticPattern.StepperDec,
        HapticPattern.ActionTriggered,
        HapticPattern.ActionRejected,
        HapticPattern.DashboardSwitch,
    )

    @Test
    fun sevenDistinctVariants() {
        assertEquals(7, allPatterns.distinct().size)
    }

    @Test
    fun allVariantsAreHapticPattern() {
        allPatterns.forEach { assertIs<HapticPattern>(it) }
    }
}
