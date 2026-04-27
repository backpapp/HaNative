package com.backpapp.hanative.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class WindowSizeClassTest {

    @Test
    fun windowSizeClass_hasThreeVariants() {
        val values = WindowSizeClass.entries
        assertEquals(3, values.size)
    }

    @Test
    fun windowSizeClass_compactIsFirst() {
        assertEquals(WindowSizeClass.COMPACT, WindowSizeClass.entries.first())
    }

    @Test
    fun windowSizeClass_expandedIsLast() {
        assertEquals(WindowSizeClass.EXPANDED, WindowSizeClass.entries.last())
    }

    @Test
    fun windowSizeClass_compactOrdinalIsZero() {
        assertEquals(0, WindowSizeClass.COMPACT.ordinal)
    }

    @Test
    fun windowSizeClass_mediumIsBetweenCompactAndExpanded() {
        val values = WindowSizeClass.entries
        val compactIdx = values.indexOf(WindowSizeClass.COMPACT)
        val mediumIdx = values.indexOf(WindowSizeClass.MEDIUM)
        val expandedIdx = values.indexOf(WindowSizeClass.EXPANDED)
        assert(compactIdx < mediumIdx && mediumIdx < expandedIdx)
    }
}
