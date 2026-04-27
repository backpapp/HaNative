package com.backpapp.hanative.ui.theme

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object Motion {
    // Cross-fade between named dashboards; 200ms feels instant without a flash
    val dashboardTransition: TweenSpec<Float> = tween(durationMillis = 200, easing = FastOutSlowInEasing)

    // Bottom sheet arrives with energy, settles without bounce — stiffness 400 / damping 0.8
    val bottomSheetOpen: SpringSpec<Float> = spring(stiffness = 400f, dampingRatio = 0.8f)

    // Bottom sheet dismisses with matching physics so open/close feel symmetric
    val bottomSheetDismiss: SpringSpec<Float> = spring(stiffness = 400f, dampingRatio = 0.8f)

    // Card depresses quickly on tap; high stiffness 600 / damping 0.7 gives snappy tactile feel
    val cardPress: SpringSpec<Float> = spring(stiffness = 600f, dampingRatio = 0.7f)

    // Entity state change cross-fades value/icon; 200ms keeps UI feeling live without jank
    val entityStateChange: TweenSpec<Float> = tween(durationMillis = 200, easing = FastOutSlowInEasing)

    // Rejected gesture snaps back with authority; stiffness 500 / damping 0.6 adds slight overshoot
    val snapBackRejection: SpringSpec<Float> = spring(stiffness = 500f, dampingRatio = 0.6f)

    // Stale indicator fades in slowly at 300ms so it reads as ambient state, not an alert
    val staleIndicatorFade: TweenSpec<Float> = tween(durationMillis = 300)
}
