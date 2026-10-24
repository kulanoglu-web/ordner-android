package com.ordner.app

import androidx.compose.foundation.border
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

@Composable
fun OrdnerTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    val dark = isSystemInDarkTheme() || isTv(ctx)
    val scheme = if (dark) {
        darkColorScheme(primary = Color(0xFF8AB4F8))
    } else {
        lightColorScheme(primary = Color(0xFF1A5FD0))
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Deutlicher Fokusrahmen für die Bedienung per Fernbedienung (D-Pad). */
fun Modifier.focusHighlight(): Modifier = composed {
    var focused by remember { mutableStateOf(false) }
    val color = MaterialTheme.colorScheme.primary
    this
        .onFocusChanged { focused = it.hasFocus }
        .then(if (focused) Modifier.border(3.dp, color, RoundedCornerShape(14.dp)) else Modifier)
}
