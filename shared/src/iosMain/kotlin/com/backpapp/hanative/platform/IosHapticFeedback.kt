package com.backpapp.hanative.platform

import platform.UIKit.UIImpactFeedbackGenerator
import platform.UIKit.UIImpactFeedbackStyle
import platform.UIKit.UINotificationFeedbackGenerator
import platform.UIKit.UINotificationFeedbackType

private fun impact(style: UIImpactFeedbackStyle) =
    UIImpactFeedbackGenerator(style = style).also { it.prepare(); it.impactOccurred() }

private fun notification(type: UINotificationFeedbackType) =
    UINotificationFeedbackGenerator().also { it.prepare(); it.notificationOccurred(type) }

class IosHapticEngine : HapticEngine {
    override fun fire(pattern: HapticPattern) {
        when (pattern) {
            HapticPattern.ToggleOn -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleMedium)
            HapticPattern.ToggleOff -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            HapticPattern.StepperInc -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            HapticPattern.StepperDec -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleLight)
            HapticPattern.ActionTriggered -> notification(UINotificationFeedbackType.UINotificationFeedbackTypeSuccess)
            HapticPattern.ActionRejected -> notification(UINotificationFeedbackType.UINotificationFeedbackTypeError)
            HapticPattern.DashboardSwitch -> impact(UIImpactFeedbackStyle.UIImpactFeedbackStyleRigid)
        }
    }
}
