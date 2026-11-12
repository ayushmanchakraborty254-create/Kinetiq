package com.ayushman.kinetiq.ui.home

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FlashlightOff
import androidx.compose.material.icons.outlined.FlashlightOn
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayushman.kinetiq.R
import com.ayushman.kinetiq.ui.components.KinetiqCard
import com.ayushman.kinetiq.ui.components.KinetiqHeader
import com.ayushman.kinetiq.ui.components.SensitivitySelector
import com.ayushman.kinetiq.ui.components.StatusPill
import com.ayushman.kinetiq.ui.theme.KinetiqAmber
import com.ayushman.kinetiq.ui.theme.KinetiqAmberMuted
import com.ayushman.kinetiq.ui.theme.KinetiqBorder
import com.ayushman.kinetiq.ui.theme.KinetiqCharcoalDark
import com.ayushman.kinetiq.ui.theme.KinetiqGreen
import com.ayushman.kinetiq.ui.theme.KinetiqGreenMuted
import com.ayushman.kinetiq.ui.theme.KinetiqLavender
import com.ayushman.kinetiq.ui.theme.KinetiqObsidian
import com.ayushman.kinetiq.ui.theme.KinetiqOffWhite
import com.ayushman.kinetiq.ui.theme.KinetiqTextMuted
import com.ayushman.kinetiq.ui.theme.KinetiqTextTertiary

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateSettings: () -> Unit,
    onNavigateCalibration: () -> Unit,
    onNavigateDiagnostics: () -> Unit,
    onNavigateGestureStudio: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prefs by viewModel.userPreferences.collectAsState()
    val isTorchOn by viewModel.isTorchOn.collectAsState()
    val hardware = viewModel.hardwareStatus

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (isGranted) {
                viewModel.toggleMasterMonitoring(true)
            }
        }
    )

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KinetiqObsidian
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            KinetiqHeader(
                onNavigateSettings = onNavigateSettings,
                onNavigateCalibration = onNavigateCalibration,
                onNavigateDiagnostics = onNavigateDiagnostics
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Master Motion Monitoring Control Card
            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Motion Monitoring",
                                style = MaterialTheme.typography.titleLarge,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(
                                        if (prefs.isMonitoringEnabled) KinetiqGreen else KinetiqTextTertiary,
                                        CircleShape
                                    )
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (prefs.isMonitoringEnabled) {
                                "Sensors active in background"
                            } else {
                                "Motion recognition paused"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqTextMuted
                        )
                    }

                    Switch(
                        checked = prefs.isMonitoringEnabled,
                        onCheckedChange = { shouldEnable ->
                            if (shouldEnable && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                viewModel.toggleMasterMonitoring(shouldEnable)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KinetiqObsidian,
                            checkedTrackColor = KinetiqLavender,
                            uncheckedThumbColor = KinetiqTextMuted,
                            uncheckedTrackColor = KinetiqCharcoalDark,
                            uncheckedBorderColor = KinetiqBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatusPill(
                        text = if (prefs.isMonitoringEnabled) "BACKGROUND ACTIVE" else "MONITORING IDLE",
                        dotColor = if (prefs.isMonitoringEnabled) KinetiqGreen else KinetiqTextMuted,
                        backgroundColor = if (prefs.isMonitoringEnabled) KinetiqGreenMuted else KinetiqCharcoalDark
                    )
                    if (prefs.customChopPeakThreshold != null || prefs.customTwistPeakThreshold != null) {
                        StatusPill(
                            text = "CALIBRATED",
                            dotColor = KinetiqLavender,
                            backgroundColor = Color(0x22D0BCFF)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Gesture Cards Section Title
            Text(
                text = "GESTURES",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // FEATURE A: DOUBLE CHOP CARD
            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(KinetiqCharcoalDark, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_chop),
                                contentDescription = "Chop Icon",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Double Chop",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Text(
                                text = "Two chopping strokes to toggle flashlight",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                        }
                    }

                    Switch(
                        checked = prefs.isChopEnabled,
                        onCheckedChange = { viewModel.toggleChop(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KinetiqObsidian,
                            checkedTrackColor = KinetiqLavender,
                            uncheckedThumbColor = KinetiqTextMuted,
                            uncheckedTrackColor = KinetiqCharcoalDark
                        )
                    )
                }

                if (!hardware.hasFlashlight) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KinetiqAmberMuted, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = "Warning",
                            tint = KinetiqAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No rear camera flash found on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqAmber
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Outlined.FlashlightOn else Icons.Outlined.FlashlightOff,
                                contentDescription = "Flashlight Status",
                                tint = if (isTorchOn) KinetiqLavender else KinetiqTextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isTorchOn) "Flashlight Active" else "Flashlight Off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (isTorchOn) KinetiqLavender else KinetiqTextMuted
                            )
                        }

                        Text(
                            text = "Linear Accel ${if (prefs.customChopPeakThreshold != null) "(Calibrated)" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = KinetiqTextTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SENSITIVITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = KinetiqTextTertiary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SensitivitySelector(
                        selectedLevel = prefs.chopSensitivity,
                        onLevelSelected = { viewModel.setChopSensitivity(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // FEATURE B: DOUBLE TWIST CARD
            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(KinetiqCharcoalDark, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_twist),
                                contentDescription = "Twist Icon",
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column {
                            Text(
                                text = "Double Twist",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Text(
                                text = "Two quick wrist twists to open camera",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                        }
                    }

                    Switch(
                        checked = prefs.isTwistEnabled,
                        onCheckedChange = { viewModel.toggleTwist(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KinetiqObsidian,
                            checkedTrackColor = KinetiqLavender,
                            uncheckedThumbColor = KinetiqTextMuted,
                            uncheckedTrackColor = KinetiqCharcoalDark
                        )
                    )
                }

                if (!hardware.hasGyroscope) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KinetiqAmberMuted, RoundedCornerShape(8.dp))
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Warning,
                            contentDescription = "Warning",
                            tint = KinetiqAmber,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "No hardware gyroscope found on this device",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqAmber
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Action: Launch Default Camera",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqTextMuted
                        )
                        Text(
                            text = "Gyroscope ${if (prefs.customTwistPeakThreshold != null) "(Calibrated)" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = KinetiqTextTertiary
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "SENSITIVITY",
                        style = MaterialTheme.typography.labelSmall,
                        color = KinetiqTextTertiary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SensitivitySelector(
                        selectedLevel = prefs.twistSensitivity,
                        onLevelSelected = { viewModel.setTwistSensitivity(it) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Gesture Studio Callout Card
            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Gesture Studio",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            StatusPill(
                                text = "STUDIO",
                                dotColor = KinetiqLavender,
                                backgroundColor = Color(0x22D0BCFF)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Train personalized biometric profiles or create custom motion gestures",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqTextMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onNavigateGestureStudio,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KinetiqLavender,
                            contentColor = KinetiqObsidian
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Studio", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Calibration & Tuning Callout Card
            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Motion Calibration",
                            style = MaterialTheme.typography.titleMedium,
                            color = KinetiqOffWhite
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Fine-tune acceleration and twist thresholds to match your personal motion speed",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqTextMuted
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Button(
                        onClick = onNavigateCalibration,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = KinetiqCharcoalDark,
                            contentColor = KinetiqLavender
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(text = "Calibrate", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

