package com.ayushman.kinetiq.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ayushman.kinetiq.R
import com.ayushman.kinetiq.ui.theme.KinetiqLavender
import com.ayushman.kinetiq.ui.theme.KinetiqOffWhite
import com.ayushman.kinetiq.ui.theme.KinetiqTextMuted

@Composable
fun KinetiqHeader(
    onNavigateSettings: () -> Unit,
    onNavigateCalibration: () -> Unit,
    onNavigateDiagnostics: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(id = R.drawable.ic_kinetiq_symbol),
                contentDescription = "Kinetiq Logo",
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "KINETIQ",
                    style = MaterialTheme.typography.titleLarge,
                    color = KinetiqOffWhite
                )
                Text(
                    text = "Motion Gesture System",
                    style = MaterialTheme.typography.labelSmall,
                    color = KinetiqTextMuted
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onNavigateCalibration) {
                Icon(
                    imageVector = Icons.Outlined.Tune,
                    contentDescription = "Calibration",
                    tint = KinetiqTextMuted
                )
            }
            IconButton(onClick = onNavigateDiagnostics) {
                Icon(
                    imageVector = Icons.Outlined.BugReport,
                    contentDescription = "Diagnostics",
                    tint = KinetiqTextMuted
                )
            }
            IconButton(onClick = onNavigateSettings) {
                Icon(
                    imageVector = Icons.Outlined.Settings,
                    contentDescription = "Settings",
                    tint = KinetiqTextMuted
                )
            }
        }
    }
}

