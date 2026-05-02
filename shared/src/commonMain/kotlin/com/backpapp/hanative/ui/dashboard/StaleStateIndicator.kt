package com.backpapp.hanative.ui.dashboard

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.backpapp.hanative.ui.theme.Motion
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlinx.coroutines.delay

@OptIn(ExperimentalTime::class)
@Composable
internal fun StaleStateIndicator(
    state: StaleIndicatorUi,
    modifier: Modifier = Modifier,
) {
    val initialSeconds: Long? = state.lastMessageEpochMs?.let { ms ->
        val now = Clock.System.now().toEpochMilliseconds()
        if (now < ms) null else (now - ms) / 1000L
    }
    val seconds by produceState<Long?>(
        initialValue = initialSeconds,
        key1 = state.lastMessageEpochMs,
        key2 = state.kind,
    ) {
        if (state.kind != StaleIndicatorKind.Stale) return@produceState
        val ms = state.lastMessageEpochMs
        if (ms == null) {
            value = null
            return@produceState
        }
        var firstIteration = true
        while (true) {
            val now = Clock.System.now().toEpochMilliseconds()
            val delta = now - ms
            value = if (delta < 0L) null else delta / 1000L
            val nextDelay = if (firstIteration) {
                firstIteration = false
                val remainder = if (delta < 0L) 0L else delta % 1000L
                (1000L - remainder).coerceIn(1L, 1000L)
            } else 1000L
            delay(nextDelay)
        }
    }

    val description = remember(state.kind, state.lastMessageEpochMs) {
        when (state.kind) {
            StaleIndicatorKind.Connected -> ""
            StaleIndicatorKind.Stale -> {
                val ms = state.lastMessageEpochMs
                if (ms != null) {
                    val now = Clock.System.now().toEpochMilliseconds()
                    val s = if (now < ms) null else (now - ms) / 1000L
                    if (s != null) {
                        "Connection lost. Last updated ${formatStaleAgo(s)} ago."
                    } else {
                        "Connection lost. Last updated --"
                    }
                } else {
                    "Connection lost. Never connected this session."
                }
            }
            StaleIndicatorKind.Reconnecting -> "Reconnecting to your home."
        }
    }

    Box(
        modifier = modifier
            .height(24.dp)
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = description
            },
        contentAlignment = Alignment.CenterEnd,
    ) {
        AnimatedContent(
            targetState = state.kind,
            transitionSpec = {
                (fadeIn(Motion.staleIndicatorFade) togetherWith fadeOut(Motion.staleIndicatorFade))
                    .using(SizeTransform(clip = false))
            },
            label = "staleIndicator",
        ) { kind ->
            when (kind) {
                StaleIndicatorKind.Connected -> Box(modifier = Modifier.size(0.dp))
                StaleIndicatorKind.Stale -> {
                    val label = if (state.lastMessageEpochMs != null) {
                        val s = seconds
                        if (s != null) "Last updated ${formatStaleAgo(s)} ago" else "Last updated --"
                    } else {
                        "Last updated --"
                    }
                    Text(
                        text = label,
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                StaleIndicatorKind.Reconnecting -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Reconnecting…",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
    }
}

internal fun formatStaleAgo(seconds: Long): String = when {
    seconds < 60L -> "${seconds}s"
    seconds < 3600L -> "${seconds / 60L}m"
    seconds < 86400L -> "${seconds / 3600L}h"
    else -> "${seconds / 86400L}d"
}
