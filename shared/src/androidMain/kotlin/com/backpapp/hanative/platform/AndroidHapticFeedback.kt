package com.backpapp.hanative.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class AndroidHapticEngine(private val context: Context) : HapticEngine {

    private val vibrator: Vibrator by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        }
    }

    override fun fire(pattern: HapticPattern) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val effect = when (pattern) {
            HapticPattern.ToggleOn -> VibrationEffect.createOneShot(20, 180)
            HapticPattern.ToggleOff -> VibrationEffect.createOneShot(15, 100)
            HapticPattern.StepperInc -> VibrationEffect.createOneShot(10, 200)
            HapticPattern.StepperDec -> VibrationEffect.createOneShot(10, 120)
            HapticPattern.ActionTriggered -> VibrationEffect.createOneShot(30, 255)
            HapticPattern.ActionRejected -> VibrationEffect.createWaveform(
                longArrayOf(0, 20, 60, 20), intArrayOf(0, 200, 0, 150), -1
            )
            HapticPattern.DashboardSwitch -> VibrationEffect.createOneShot(8, 80)
        }
        vibrator.vibrate(effect)
    }
}
