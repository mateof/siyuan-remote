// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import io.github.mateof.siyuanremote.R
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/**
 * The SiYuan web UI fills the screen and we cannot add entries to its menus
 * without injecting code into it, so the app keeps its own controls behind one
 * button that fades out of the way when it is not being used.
 *
 * It can be dragged, because a button pinned to a corner will always end up on
 * top of something eventually: a save button, a scrollbar, the editor toolbar.
 * Rather than guess a corner that is free on every screen, let it be moved.
 *
 * The position is kept as a fraction of the free space rather than in pixels,
 * so it lands in the same relative place after a rotation or on another device.
 *
 * [containerSize] is measured by the caller on purpose. Wrapping this in a
 * screen sized box would put a full screen layout node over the WebView, which
 * is a good way to break scrolling in ways that only show up on a device.
 */
@Composable
fun QuickActionButton(
    containerSize: IntSize,
    positionX: Float,
    positionY: Float,
    onPositionChanged: (Float, Float) -> Unit,
    modifier: Modifier = Modifier,
    onSettings: () -> Unit,
    onQuickCapture: () -> Unit,
    onReload: () -> Unit,
    onSync: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var dimmed by remember { mutableStateOf(false) }
    var dragging by remember { mutableStateOf(false) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var fractionX by remember(positionX) { mutableFloatStateOf(positionX) }
    var fractionY by remember(positionY) { mutableFloatStateOf(positionY) }

    LaunchedEffect(expanded, dragging) {
        dimmed = false
        if (!expanded && !dragging) {
            delay(IDLE_BEFORE_DIM_MS)
            dimmed = true
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (dimmed && !expanded) DIMMED_ALPHA else 1f,
        label = "quickActionAlpha",
    )

    val margin = with(LocalDensity.current) { EDGE_MARGIN.roundToPx() }

    // Clamping against the measured size keeps the whole control on screen,
    // including when expanding makes it taller while it sits near the bottom.
    val freeWidth = (containerSize.width - size.width - margin * 2).coerceAtLeast(0)
    val freeHeight = (containerSize.height - size.height - margin * 2).coerceAtLeast(0)

    Column(
        modifier = modifier
            .offset {
                IntOffset(
                    x = margin + (fractionX * freeWidth).roundToInt(),
                    y = margin + (fractionY * freeHeight).roundToInt(),
                )
            }
            .onSizeChanged { size = it }
            .alpha(alpha)
            .pointerInput(freeWidth, freeHeight) {
                detectDragGestures(
                    onDragStart = { dragging = true },
                    onDragEnd = {
                        dragging = false
                        onPositionChanged(fractionX, fractionY)
                    },
                    onDragCancel = { dragging = false },
                ) { change, drag ->
                    change.consume()
                    if (freeWidth > 0) {
                        fractionX = (fractionX + drag.x / freeWidth).coerceIn(0f, 1f)
                    }
                    if (freeHeight > 0) {
                        fractionY = (fractionY + drag.y / freeHeight).coerceIn(0f, 1f)
                    }
                }
            },
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnimatedVisibility(
            visible = expanded,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut(),
        ) {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MiniAction(Icons.Default.Add, R.string.action_quick_capture) {
                    expanded = false
                    onQuickCapture()
                }
                MiniAction(Icons.Default.Sync, R.string.action_sync) {
                    expanded = false
                    onSync()
                }
                MiniAction(Icons.Default.Refresh, R.string.action_reload) {
                    expanded = false
                    onReload()
                }
                MiniAction(Icons.Default.Settings, R.string.action_open_settings) {
                    expanded = false
                    onSettings()
                }
            }
        }

        SmallFloatingActionButton(
            onClick = { expanded = !expanded },
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ) {
            Icon(
                imageVector = if (expanded) Icons.Default.Close else Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.action_app_menu),
            )
        }
    }
}

@Composable
private fun MiniAction(icon: ImageVector, labelRes: Int, onClick: () -> Unit) {
    SmallFloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Icon(imageVector = icon, contentDescription = stringResource(labelRes))
    }
}

private const val IDLE_BEFORE_DIM_MS = 3_000L
private const val DIMMED_ALPHA = 0.25f
private val EDGE_MARGIN = 12.dp
