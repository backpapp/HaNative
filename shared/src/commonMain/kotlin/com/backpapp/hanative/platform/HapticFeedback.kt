package com.backpapp.hanative.platform

import androidx.compose.runtime.staticCompositionLocalOf

sealed class HapticPattern {
    object ToggleOn : HapticPattern()
    object ToggleOff : HapticPattern()
    object StepperInc : HapticPattern()
    object StepperDec : HapticPattern()
    object ActionTriggered : HapticPattern()
    object ActionRejected : HapticPattern()
    object DashboardSwitch : HapticPattern()
}

interface HapticEngine {
    fun fire(pattern: HapticPattern)
}

val LocalHapticEngine = staticCompositionLocalOf<HapticEngine> {
    error("LocalHapticEngine not provided")
}
