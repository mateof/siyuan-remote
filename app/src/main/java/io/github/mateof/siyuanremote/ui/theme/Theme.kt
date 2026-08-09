// Copyright (C) 2026 mateof
// SPDX-License-Identifier: GPL-3.0-or-later

package io.github.mateof.siyuanremote.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val SiyuanBlue = Color(0xFF3575F3)
private val SiyuanBlueDark = Color(0xFF9CBEFF)

private val LightColors = lightColorScheme(
    primary = SiyuanBlue,
    onPrimary = Color.White,
    secondary = Color(0xFF565E71),
    background = Color(0xFFFDFBFF),
    surface = Color(0xFFFDFBFF),
)

private val DarkColors = darkColorScheme(
    primary = SiyuanBlueDark,
    onPrimary = Color(0xFF002E6A),
    secondary = Color(0xFFBEC6DC),
    background = Color(0xFF1A1B1F),
    surface = Color(0xFF1A1B1F),
)

@Composable
fun SiyuanRemoteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}
