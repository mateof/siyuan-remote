package io.github.mateof.siyuanremote.ui.web

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.mateof.siyuanremote.R
import kotlinx.coroutines.delay

/**
 * The SiYuan web UI fills the screen and we cannot add entries to its menus
 * without injecting code into it, so the app keeps its own controls behind one
 * button that fades out of the way when it is not being used.
 */
@Composable
fun QuickActionButton(
    modifier: Modifier = Modifier,
    onSettings: () -> Unit,
    onQuickCapture: () -> Unit,
    onReload: () -> Unit,
    onSync: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    var dimmed by remember { mutableStateOf(false) }

    LaunchedEffect(expanded) {
        dimmed = false
        if (!expanded) {
            delay(IDLE_BEFORE_DIM_MS)
            dimmed = true
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (dimmed && !expanded) DIMMED_ALPHA else 1f,
        label = "quickActionAlpha",
    )

    Column(
        modifier = modifier.alpha(alpha),
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
