package com.arena.chargepulse.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PulseScheme = darkColorScheme(
    primary = Color(0xFF44D9FF),
    secondary = Color(0xFF8BA4FF),
    background = Color(0xFF050811),
    surface = Color(0xFF0D1424),
    onBackground = Color(0xFFF2F8FF),
    onSurface = Color(0xFFF2F8FF)
)

@Composable fun ChargePulseTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = PulseScheme, content = content)
