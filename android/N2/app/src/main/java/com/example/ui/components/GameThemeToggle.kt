package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.ThemeManager

@Composable
fun GameThemeToggle(
    modifier: Modifier = Modifier
) {
    val isDark = ThemeManager.isDarkMode

    Row(
        modifier = modifier.fillMaxWidth().padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        Text(
            text = if (isDark) "\uD83C\uDF19" else "\u2600\uFE0F",
            fontSize = 16.sp
        )
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = isDark,
            onCheckedChange = { ThemeManager.toggle() }
        )
    }
}