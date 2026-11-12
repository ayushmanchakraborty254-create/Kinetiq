package com.ayushman.kinetiq.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BatteryChargingFull
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.Vibration
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.ayushman.kinetiq.data.preferences.PreferencesRepository
import com.ayushman.kinetiq.domain.model.UserPreferences
import com.ayushman.kinetiq.ui.components.KinetiqCard
import com.ayushman.kinetiq.ui.theme.KinetiqAmber
import com.ayushman.kinetiq.ui.theme.KinetiqAmberMuted
import com.ayushman.kinetiq.ui.theme.KinetiqBorder
import com.ayushman.kinetiq.ui.theme.KinetiqCharcoalCard
import com.ayushman.kinetiq.ui.theme.KinetiqCharcoalDark
import com.ayushman.kinetiq.ui.theme.KinetiqLavender
import com.ayushman.kinetiq.ui.theme.KinetiqObsidian
import com.ayushman.kinetiq.ui.theme.KinetiqOffWhite
import com.ayushman.kinetiq.ui.theme.KinetiqTextMuted
import com.ayushman.kinetiq.ui.theme.KinetiqTextTertiary
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    preferencesRepository: PreferencesRepository,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val prefs by preferencesRepository.userPreferencesFlow.collectAsState(initial = UserPreferences())

    var showBatteryDialog by remember { mutableStateOf(false) }

    val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    val isIgnoringBatteryOptimizations = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
        powerManager?.isIgnoringBatteryOptimizations(context.packageName) ?: false
    } else true

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KinetiqObsidian,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
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

            // BEHAVIOR SECTION
            Text(
                text = "BEHAVIOR & CONTROLS",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            KinetiqCard {
                // Haptic Feedback Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Vibration,
                            contentDescription = "Haptics",
                            tint = KinetiqLavender,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Haptic Confirmation",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Text(
                                text = "Subtle tactile pulse on accepted gesture",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                        }
                    }

                    Switch(
                        checked = prefs.isHapticEnabled,
                        onCheckedChange = { scope.launch { preferencesRepository.setHapticEnabled(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KinetiqObsidian,
                            checkedTrackColor = KinetiqLavender,
                            uncheckedThumbColor = KinetiqTextMuted,
                            uncheckedTrackColor = KinetiqCharcoalDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Pocket Protection Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Security,
                            contentDescription = "Pocket Protection",
                            tint = KinetiqLavender,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Pocket Protection",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Text(
                                text = "Suppresses gestures when proximity sensor is covered",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                        }
                    }

                    Switch(
                        checked = prefs.isPocketProtectionEnabled,
                        onCheckedChange = { scope.launch { preferencesRepository.setPocketProtectionEnabled(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KinetiqObsidian,
                            checkedTrackColor = KinetiqLavender,
                            uncheckedThumbColor = KinetiqTextMuted,
                            uncheckedTrackColor = KinetiqCharcoalDark
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Screen-off Monitoring Toggle (with transparent disclaimer)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.VisibilityOff,
                            contentDescription = "Screen-Off Monitoring",
                            tint = KinetiqLavender,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Screen-Off Monitoring",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Text(
                                text = "Keeps motion processor awake when screen turns off",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                        }
                    }

                    Switch(
                        checked = prefs.isScreenOffMonitoringEnabled,
                        onCheckedChange = { scope.launch { preferencesRepository.setScreenOffMonitoringEnabled(it) } },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = KinetiqObsidian,
                            checkedTrackColor = KinetiqLavender,
                            uncheckedThumbColor = KinetiqTextMuted,
                            uncheckedTrackColor = KinetiqCharcoalDark
                        )
                    )
                }

                if (prefs.isScreenOffMonitoringEnabled) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(KinetiqAmberMuted, RoundedCornerShape(8.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            text = "Battery Note: Non-system Android apps cannot use dedicated hardware sensor co-processors. Running screen-off monitoring prevents CPU sleep and increases battery usage.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqAmber
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // BATTERY & LIFECYCLE SECTION
            Text(
                text = "BATTERY & SYSTEM OPTIMIZATION",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            KinetiqCard {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showBatteryDialog = true },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.BatteryChargingFull,
                            contentDescription = "Battery",
                            tint = KinetiqLavender,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Battery Optimization Guidance",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Text(
                                text = if (isIgnoringBatteryOptimizations) "Unrestricted background access granted" else "Standard system optimization active",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                        }
                    }

                    Text(
                        text = "Learn More",
                        style = MaterialTheme.typography.labelLarge,
                        color = KinetiqLavender
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // PRIVACY & ABOUT SECTION
            Text(
                text = "ABOUT & PRIVACY",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = "About",
                        tint = KinetiqLavender,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "KINETIQ v1.0.0",
                            style = MaterialTheme.typography.titleMedium,
                            color = KinetiqOffWhite
                        )
                        Text(
                            text = "Production Motion Gesture Automation Utility",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqTextMuted
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = "Zero Network · 100% On-Device",
                    style = MaterialTheme.typography.titleMedium,
                    color = KinetiqOffWhite
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "KINETIQ processes all sensor events locally in real-time. The application contains no internet permissions, no telemetry, no analytics, and zero advertising SDKs.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KinetiqTextMuted
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Battery Explanation Dialog
        if (showBatteryDialog) {
            AlertDialog(
                onDismissRequest = { showBatteryDialog = false },
                containerColor = KinetiqCharcoalCard,
                title = {
                    Text(
                        text = "Android Battery Restrictions",
                        style = MaterialTheme.typography.titleLarge,
                        color = KinetiqOffWhite
                    )
                },
                text = {
                    Column {
                        Text(
                            text = "Modern Android systems aggressively throttle background apps to conserve power:\n\n" +
                                    "• Doze Mode: When the device is stationary with screen off, Android suspends sensor listeners.\n" +
                                    "• Vendor Optimizations: Manufacturers (Samsung, Xiaomi, OnePlus) aggressively stop background services.\n\n" +
                                    "To ensure uninterrupted gesture recognition, you can optionally exclude KINETIQ from battery optimization in System Settings.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqTextMuted
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showBatteryDialog = false
                            try {
                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.fromParts("package", context.packageName, null)
                                }
                                context.startActivity(intent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = KinetiqLavender)
                    ) {
                        Text(text = "System Settings", color = KinetiqObsidian)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showBatteryDialog = false }) {
                        Text(text = "Close", color = KinetiqTextMuted)
                    }
                }
            )
        }
    }
}
