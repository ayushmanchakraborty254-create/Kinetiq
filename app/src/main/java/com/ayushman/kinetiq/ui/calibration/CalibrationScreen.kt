package com.ayushman.kinetiq.ui.calibration

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp
import com.ayushman.kinetiq.domain.model.GestureType
import com.ayushman.kinetiq.ui.components.KinetiqCard
import com.ayushman.kinetiq.ui.theme.KinetiqBorder
import com.ayushman.kinetiq.ui.theme.KinetiqCharcoalDark
import com.ayushman.kinetiq.ui.theme.KinetiqGreen
import com.ayushman.kinetiq.ui.theme.KinetiqLavender
import com.ayushman.kinetiq.ui.theme.KinetiqObsidian
import com.ayushman.kinetiq.ui.theme.KinetiqOffWhite
import com.ayushman.kinetiq.ui.theme.KinetiqTextMuted
import com.ayushman.kinetiq.ui.theme.KinetiqTextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalibrationScreen(
    viewModel: CalibrationViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step by viewModel.step.collectAsState()
    val selectedGesture by viewModel.selectedGesture.collectAsState()
    val sampleCount by viewModel.currentSampleCount.collectAsState()
    val liveSensorValue by viewModel.liveSensorValue.collectAsState()
    val profile by viewModel.calculatedProfile.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KinetiqObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Motion Calibration",
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
                    IconButton(onClick = { viewModel.resetToDefaults() }) {
                        Icon(
                            imageVector = Icons.Outlined.RestartAlt,
                            contentDescription = "Reset Defaults",
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
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            AnimatedContent(targetState = step, label = "CalibrationStep") { currentStep ->
                when (currentStep) {
                    CalibrationStep.SELECT_GESTURE -> {
                        Column {
                            Text(
                                text = "Select Gesture to Calibrate",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Record your natural movement to automatically compute optimal acceleration and velocity thresholds.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                            Spacer(modifier = Modifier.height(20.dp))

                            // Double Chop Selector
                            KinetiqCard(
                                modifier = Modifier.clickable { viewModel.selectGesture(GestureType.DOUBLE_CHOP) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Double Chop",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = KinetiqOffWhite
                                        )
                                        Text(
                                            text = "Linear acceleration · Flashlight toggle",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = KinetiqTextMuted
                                        )
                                    }
                                    Text(
                                        text = "Select",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = KinetiqLavender
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Double Twist Selector
                            KinetiqCard(
                                modifier = Modifier.clickable { viewModel.selectGesture(GestureType.DOUBLE_TWIST) }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Double Twist",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = KinetiqOffWhite
                                        )
                                        Text(
                                            text = "Gyroscope angular velocity · Open camera",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = KinetiqTextMuted
                                        )
                                    }
                                    Text(
                                        text = "Select",
                                        style = MaterialTheme.typography.labelLarge,
                                        color = KinetiqLavender
                                    )
                                }
                            }
                        }
                    }

                    CalibrationStep.PREPARING -> {
                        Column {
                            Text(
                                text = "Calibrating ${selectedGesture.displayName}",
                                style = MaterialTheme.typography.titleLarge,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (selectedGesture == GestureType.DOUBLE_CHOP) {
                                    "1. Hold the device comfortably in your hand.\n" +
                                            "2. When recording begins, perform 3 deliberate chops.\n" +
                                            "3. Return the phone to neutral between each stroke."
                                } else {
                                    "1. Hold the phone in front of you naturally.\n" +
                                            "2. When recording begins, perform 3 deliberate wrist twists.\n" +
                                            "3. Pause slightly between each twist."
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                        }
                    }

                    CalibrationStep.RECORDING -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Recording Sample ${sampleCount + 1} of 3",
                                style = MaterialTheme.typography.titleLarge,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Perform a deliberate ${selectedGesture.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqLavender
                            )

                            Spacer(modifier = Modifier.height(32.dp))

                            // Dynamic Sensor Magnitude Gauge
                            Box(
                                modifier = Modifier
                                    .size(160.dp)
                                    .clip(CircleShape)
                                    .background(KinetiqCharcoalDark)
                                    .border(2.dp, KinetiqBorder, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = String.format("%.1f", liveSensorValue),
                                        style = MaterialTheme.typography.headlineLarge,
                                        color = KinetiqOffWhite
                                    )
                                    Text(
                                        text = if (selectedGesture == GestureType.DOUBLE_CHOP) "m/s²" else "rad/s",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = KinetiqTextTertiary
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(32.dp))

                            LinearProgressIndicator(
                                progress = { (sampleCount / 3f).coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp)),
                                color = KinetiqLavender,
                                trackColor = KinetiqCharcoalDark,
                                strokeCap = StrokeCap.Round
                            )
                        }
                    }

                    CalibrationStep.REVIEW_PROFILE -> {
                        Column {
                            Text(
                                text = "Calibration Complete",
                                style = MaterialTheme.typography.titleLarge,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Here are your personalized motion parameters:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )

                            Spacer(modifier = Modifier.height(20.dp))

                            profile?.let { prof ->
                                KinetiqCard {
                                    Text(
                                        text = "Average Detected Peak: ${String.format("%.1f", prof.averagePeak)}",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = KinetiqOffWhite
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = "Optimized Trigger Threshold: ${String.format("%.1f", prof.recommendedPeakThreshold)}",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = KinetiqLavender
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Neutral Recoil Threshold: ${String.format("%.1f", prof.recommendedNeutralThreshold)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = KinetiqTextMuted
                                    )
                                }
                            }
                        }
                    }

                    CalibrationStep.COMPLETED -> {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.CheckCircle,
                                contentDescription = "Done",
                                tint = KinetiqGreen,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Thresholds Saved",
                                style = MaterialTheme.typography.titleLarge,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Your gesture engine has been updated with your personal motion profile.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                        }
                    }
                }
            }

            // Bottom Action Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when (step) {
                    CalibrationStep.SELECT_GESTURE -> {
                        OutlinedButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Back", color = KinetiqOffWhite)
                        }
                    }

                    CalibrationStep.PREPARING -> {
                        OutlinedButton(
                            onClick = { viewModel.resetFlow() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Cancel", color = KinetiqOffWhite)
                        }
                        Button(
                            onClick = { viewModel.startRecording() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = KinetiqLavender)
                        ) {
                            Text(text = "Start", color = KinetiqObsidian)
                        }
                    }

                    CalibrationStep.RECORDING -> {
                        OutlinedButton(
                            onClick = { viewModel.resetFlow() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = "Cancel Calibration", color = KinetiqOffWhite)
                        }
                    }

                    CalibrationStep.REVIEW_PROFILE -> {
                        OutlinedButton(
                            onClick = { viewModel.resetFlow() },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(text = "Discard", color = KinetiqOffWhite)
                        }
                        Button(
                            onClick = { viewModel.applyCalibration() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = KinetiqLavender)
                        ) {
                            Text(text = "Apply", color = KinetiqObsidian)
                        }
                    }

                    CalibrationStep.COMPLETED -> {
                        Button(
                            onClick = onNavigateBack,
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = KinetiqLavender)
                        ) {
                            Text(text = "Return to Home", color = KinetiqObsidian)
                        }
                    }
                }
            }
        }
    }
}

