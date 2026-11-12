package com.ayushman.kinetiq.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ayushman.kinetiq.ui.theme.KinetiqBorder
import com.ayushman.kinetiq.ui.theme.KinetiqCharcoalCard

@Composable
fun KinetiqCard(
    modifier: Modifier = Modifier,
    border: BorderStroke = BorderStroke(1.dp, KinetiqBorder),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = KinetiqCharcoalCard,
        border = border
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            content = content
        )
    }
}

