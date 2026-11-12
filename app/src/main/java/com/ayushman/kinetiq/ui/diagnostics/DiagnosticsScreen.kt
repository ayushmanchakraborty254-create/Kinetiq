package com.ayushman.kinetiq.ui.diagnostics

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.FiberManualRecord
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ayushman.kinetiq.data.sensor.SensorSamplingRate
import com.ayushman.kinetiq.data.sensor.SensorTraceRecorder
import com.ayushman.kinetiq.data.sensor.TraceFileInfo
import com.ayushman.kinetiq.ui.components.KinetiqCard
import com.ayushman.kinetiq.ui.theme.KinetiqGreen
import com.ayushman.kinetiq.ui.theme.KinetiqLavender
import com.ayushman.kinetiq.ui.theme.KinetiqObsidian
import com.ayushman.kinetiq.ui.theme.KinetiqOffWhite
import com.ayushman.kinetiq.ui.theme.KinetiqRed
import com.ayushman.kinetiq.ui.theme.KinetiqTextMuted
import com.ayushman.kinetiq.ui.theme.KinetiqTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsScreen(
    viewModel: DiagnosticsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hardware = viewModel.hardwareStatus
    val prefs by viewModel.userPreferences.collectAsState()
    val liveData by viewModel.liveSensorReadout.collectAsState()
    val chopState by viewModel.chopState.collectAsState()
    val twistState by viewModel.twistState.collectAsState()
    val lastGesture by viewModel.lastRecognizedGesture.collectAsState()

    val isRecording by viewModel.isRecording.collectAsState()
    val recordedSampleCount by viewModel.recordedSampleCount.collectAsState()
    val recordingElapsedMs by viewModel.recordingElapsedMs.collectAsState()
    val currentTraceLabel by viewModel.currentTraceLabel.collectAsState()
    val selectedSamplingRate by viewModel.selectedSamplingRate.collectAsState()
    val lastSavedTrace by viewModel.lastSavedTrace.collectAsState()
    val savedTraces by viewModel.savedTraces.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KinetiqObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Diagnostics & Sensor State",
                        style = MaterialTheme.typography.titleLarge,
                        color = KinetiqOffWhite
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Back",
                            tint = KinetiqOffWhite
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.resetDiagnostics() }) {
                        Icon(
                            imageVector = Icons.Outlined.Refresh,
                            contentDescription = "Reset",
                            tint = KinetiqTextMuted
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = KinetiqObsidian)
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // TELEMETRY RECORDER (DIAGNOSTIC MODE)
            Text(
                text = "TELEMETRY RECORDER (PHYSICAL TRACE HARNESS)",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqLavender,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            KinetiqCard {
                Text(
                    text = "Record physical sensor traces (Accelerometer, Linear Accel, Gyroscope, Proximity) on the Nothing Phone (3a) without triggering the camera or flashlight.",
                    style = MaterialTheme.typography.bodySmall,
                    color = KinetiqTextMuted
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 1. Activity Label Selection
                Text(
                    text = "ACTIVITY TO PERFORM:",
                    style = MaterialTheme.typography.labelSmall,
                    color = KinetiqTextTertiary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SensorTraceRecorder.STANDARD_LABELS.forEach { label ->
                        val isSelected = (currentTraceLabel == label)
                        FilterChip(
                            selected = isSelected,
                            onClick = { if (!isRecording) viewModel.setTraceLabel(label) },
                            label = { Text(text = label, style = MaterialTheme.typography.labelMedium) },
                            enabled = !isRecording,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KinetiqLavender.copy(alpha = 0.25f),
                                selectedLabelColor = KinetiqLavender,
                                containerColor = Color.Transparent,
                                labelColor = KinetiqTextMuted
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 2. Sampling Rate Selection
                Text(
                    text = "SAMPLING RATE DELAY:",
                    style = MaterialTheme.typography.labelSmall,
                    color = KinetiqTextTertiary
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SensorSamplingRate.values().forEach { rate ->
                        val isSelected = (selectedSamplingRate == rate)
                        FilterChip(
                            selected = isSelected,
                            onClick = { if (!isRecording) viewModel.setSamplingRate(rate) },
                            label = { Text(text = rate.displayName, style = MaterialTheme.typography.labelSmall) },
                            enabled = !isRecording,
                            modifier = Modifier.weight(1f),
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = KinetiqLavender.copy(alpha = 0.25f),
                                selectedLabelColor = KinetiqLavender,
                                containerColor = Color.Transparent,
                                labelColor = KinetiqTextMuted
                            )
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 3. Start / Stop Recording Control
                if (!isRecording) {
                    Button(
                        onClick = { viewModel.startTraceRecording() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KinetiqGreen,
                            contentColor = KinetiqObsidian
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "START RECORDING (${currentTraceLabel})",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Tap Start, perform 1 movement, then tap Stop to save.",
                        style = MaterialTheme.typography.bodySmall,
                        color = KinetiqTextMuted
                    )
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .background(KinetiqRed, CircleShape)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "RECORDING ACTIVE",
                                style = MaterialTheme.typography.labelMedium,
                                color = KinetiqRed
                            )
                        }
                        Text(
                            text = "${String.format("%.1f", recordingElapsedMs / 1000f)}s · $recordedSampleCount samples",
                            style = MaterialTheme.typography.titleMedium,
                            color = KinetiqOffWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Button(
                        onClick = { viewModel.stopTraceRecording() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KinetiqRed,
                            contentColor = KinetiqOffWhite
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "STOP & SAVE TRACE",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }

                // 4. Last Saved Banner
                if (lastSavedTrace != null) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KinetiqGreen.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Check,
                                    contentDescription = null,
                                    tint = KinetiqGreen,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Saved: ${lastSavedTrace!!.fileName} (.json & .csv)",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = KinetiqGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Samples: ${lastSavedTrace!!.sampleCount} · Duration: ${lastSavedTrace!!.durationMs}ms",
                                style = MaterialTheme.typography.bodySmall,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Pull to PC: adb pull /storage/emulated/0/Android/data/com.ayushman.kinetiq.debug/files/sensor_traces/ .",
                                style = MaterialTheme.typography.labelSmall,
                                color = KinetiqTextMuted
                            )
                        }
                    }
                }

                // 5. Saved traces list
                if (savedTraces.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "SAVED PHYSICAL TRACES (${savedTraces.size}):",
                        style = MaterialTheme.typography.labelSmall,
                        color = KinetiqTextTertiary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    savedTraces.take(6).forEach { trace ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = trace.fileName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KinetiqOffWhite
                                )
                                Text(
                                    text = "${trace.timestampFormatted} · ${trace.fileSizeBytes / 1024} KB",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = KinetiqTextMuted
                                )
                            }
                            IconButton(onClick = { viewModel.deleteTrace(trace.fileName) }) {
                                Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Delete",
                                    tint = KinetiqTextMuted,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // RECOGNITION EVENT DISPLAY
            Text(
                text = "LAST RECOGNIZED EVENT",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            KinetiqCard {
                if (lastGesture != null) {
                    val g = lastGesture!!
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = g.type.displayName,
                            style = MaterialTheme.typography.titleLarge,
                            color = KinetiqLavender
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(KinetiqGreen, CircleShape)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Confidence: ${String.format("%.0f%%", g.confidence * 100)} · Duration: ${g.executionDurationMs}ms",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KinetiqOffWhite
                    )
                } else {
                    Text(
                        text = "No gesture recognized yet. Perform double chop or twist to test.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KinetiqTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // REAL-TIME SENSOR STREAM
            Text(
                text = "LIVE SENSOR TELEMETRY",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Linear Accel Magnitude:", style = MaterialTheme.typography.bodyMedium, color = KinetiqTextMuted)
                    Text(text = "${String.format("%.2f", liveData.linearAccelMagnitude)} m/s²", style = MaterialTheme.typography.titleMedium, color = KinetiqOffWhite)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Longitudinal (Y+Z):", style = MaterialTheme.typography.bodyMedium, color = KinetiqTextMuted)
                    Text(text = "${String.format("%.2f", liveData.linearAccelLongitudinal)} m/s²", style = MaterialTheme.typography.titleMedium, color = KinetiqOffWhite)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Gyroscope Speed:", style = MaterialTheme.typography.bodyMedium, color = KinetiqTextMuted)
                    Text(text = "${String.format("%.2f", liveData.gyroSpeed)} rad/s", style = MaterialTheme.typography.titleMedium, color = KinetiqOffWhite)
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Proximity Covered (Near):", style = MaterialTheme.typography.bodyMedium, color = KinetiqTextMuted)
                    Text(
                        text = if (liveData.isProximityNear) "COVERED (BLOCKED)" else "CLEAR",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (liveData.isProximityNear) KinetiqRed else KinetiqGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // STATE MACHINE STATUS
            Text(
                text = "GESTURE ENGINE STATE MACHINES",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Chop Detector State:", style = MaterialTheme.typography.bodyMedium, color = KinetiqTextMuted)
                    Text(text = chopState.toString(), style = MaterialTheme.typography.titleMedium, color = KinetiqLavender)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Twist Detector State:", style = MaterialTheme.typography.bodyMedium, color = KinetiqTextMuted)
                    Text(text = twistState.toString(), style = MaterialTheme.typography.titleMedium, color = KinetiqLavender)
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // HARDWARE SENSOR SPECS
            Text(
                text = "HARDWARE DISCOVERY & SPECS",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            KinetiqCard {
                SensorRow("Linear Acceleration", hardware.hasLinearAcceleration, hardware.linearAccelerationName)
                Spacer(modifier = Modifier.height(8.dp))
                SensorRow("Hardware Gyroscope", hardware.hasGyroscope, hardware.gyroscopeName)
                Spacer(modifier = Modifier.height(8.dp))
                SensorRow("Proximity Sensor", hardware.hasProximity, hardware.proximityName)
                Spacer(modifier = Modifier.height(8.dp))
                SensorRow("Rear Camera Flash", hardware.hasFlashlight, hardware.cameraFlashId?.let { "Camera ID $it" })
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun SensorRow(name: String, available: Boolean, details: String?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = name, style = MaterialTheme.typography.titleMedium, color = KinetiqOffWhite)
            if (details != null) {
                Text(text = details, style = MaterialTheme.typography.labelSmall, color = KinetiqTextTertiary)
            }
        }
        Icon(
            imageVector = if (available) Icons.Outlined.Check else Icons.Outlined.Close,
            contentDescription = null,
            tint = if (available) KinetiqGreen else KinetiqRed,
            modifier = Modifier.size(18.dp)
        )
    }
}

