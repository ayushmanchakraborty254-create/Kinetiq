package com.ayushman.kinetiq.ui.studio

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.ayushman.kinetiq.R
import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import com.ayushman.kinetiq.domain.studio.model.GestureTemplate
import com.ayushman.kinetiq.ui.components.KinetiqCard
import com.ayushman.kinetiq.ui.components.StatusPill
import com.ayushman.kinetiq.ui.theme.KinetiqAmber
import com.ayushman.kinetiq.ui.theme.KinetiqAmberMuted
import com.ayushman.kinetiq.ui.theme.KinetiqCharcoalDark
import com.ayushman.kinetiq.ui.theme.KinetiqGreen
import com.ayushman.kinetiq.ui.theme.KinetiqGreenMuted
import com.ayushman.kinetiq.ui.theme.KinetiqLavender
import com.ayushman.kinetiq.ui.theme.KinetiqObsidian
import com.ayushman.kinetiq.ui.theme.KinetiqOffWhite
import com.ayushman.kinetiq.ui.theme.KinetiqTextMuted
import com.ayushman.kinetiq.ui.theme.KinetiqTextTertiary
import kotlinx.coroutines.launch
import java.util.UUID

@Composable
fun GestureStudioScreen(
    viewModel: GestureStudioViewModel,
    onNavigateBack: () -> Unit,
    onNavigateTraining: () -> Unit,
    modifier: Modifier = Modifier
) {
    val templates by viewModel.templates.collectAsState()
    val rejectionMessage by viewModel.rejectionMessage.collectAsState()
    val scope = rememberCoroutineScope()

    var showCreateDialog by remember { mutableStateOf(false) }

    val chopTemplate = templates.find { it.id == GestureTemplate.CORE_CHOP_ID }
    val twistTemplate = templates.find { it.id == GestureTemplate.CORE_TWIST_ID }
    val customTemplates = templates.filter { !it.isCorePersonalization }

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

            // Header with Back Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Back",
                        tint = KinetiqOffWhite
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "GESTURE STUDIO",
                        style = MaterialTheme.typography.titleLarge,
                        color = KinetiqOffWhite
                    )
                    Text(
                        text = "Biometric Personalization & Custom Gestures",
                        style = MaterialTheme.typography.bodySmall,
                        color = KinetiqTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Rejection Banner if concurrent/exclusive acquisition was rejected
            rejectionMessage?.let { msg ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(KinetiqAmberMuted, RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = KinetiqAmber,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { viewModel.clearRejectionMessage() }) {
                        Icon(
                            imageVector = Icons.Outlined.Close,
                            contentDescription = "Dismiss",
                            tint = KinetiqAmber
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }

            // SECTION 1: MODE A - CORE GESTURE PERSONALIZATION
            Text(
                text = "MODE A: CORE GESTURE PERSONALIZATION",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqTextTertiary,
                modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
            )

            // Double Chop Card
            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(KinetiqCharcoalDark, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_chop),
                                contentDescription = "Chop",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Double Chop",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Locked Action",
                                    tint = KinetiqTextTertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Action: Toggle Flashlight (Immutable)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KinetiqTextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chopTemplate != null) {
                        StatusPill(
                            text = "PERSONALIZED (PROVISIONALLY VALIDATED)",
                            dotColor = KinetiqGreen,
                            backgroundColor = KinetiqGreenMuted
                        )
                    } else {
                        StatusPill(
                            text = "FACTORY RULE-BASED DETECTOR",
                            dotColor = KinetiqLavender,
                            backgroundColor = Color(0x22D0BCFF)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (chopTemplate != null) {
                            IconButton(
                                onClick = { viewModel.resetToFactory(GestureTemplate.CORE_CHOP_ID) }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RestartAlt,
                                    contentDescription = "Reset to Factory",
                                    tint = KinetiqTextMuted
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val success = viewModel.startTraining(
                                        gestureId = GestureTemplate.CORE_CHOP_ID,
                                        displayName = "Double Chop",
                                        isCore = true,
                                        strokeCount = 2,
                                        action = ActionDefinition.ToggleFlashlight()
                                    )
                                    if (success) onNavigateTraining()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KinetiqCharcoalDark,
                                contentColor = KinetiqLavender
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (chopTemplate != null) "Retrain" else "Personalize",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Double Twist Card
            KinetiqCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(KinetiqCharcoalDark, RoundedCornerShape(10.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.ic_twist),
                                contentDescription = "Twist",
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Double Twist",
                                style = MaterialTheme.typography.titleMedium,
                                color = KinetiqOffWhite
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Lock,
                                    contentDescription = "Locked Action",
                                    tint = KinetiqTextTertiary,
                                    modifier = Modifier.size(12.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Action: Launch Camera (Immutable)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KinetiqTextMuted
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (twistTemplate != null) {
                        StatusPill(
                            text = "PERSONALIZED (PROVISIONALLY VALIDATED)",
                            dotColor = KinetiqGreen,
                            backgroundColor = KinetiqGreenMuted
                        )
                    } else {
                        StatusPill(
                            text = "FACTORY RULE-BASED DETECTOR",
                            dotColor = KinetiqLavender,
                            backgroundColor = Color(0x22D0BCFF)
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (twistTemplate != null) {
                            IconButton(
                                onClick = { viewModel.resetToFactory(GestureTemplate.CORE_TWIST_ID) }
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.RestartAlt,
                                    contentDescription = "Reset to Factory",
                                    tint = KinetiqTextMuted
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    val success = viewModel.startTraining(
                                        gestureId = GestureTemplate.CORE_TWIST_ID,
                                        displayName = "Double Twist",
                                        isCore = true,
                                        strokeCount = 2,
                                        action = ActionDefinition.LaunchCamera()
                                    )
                                    if (success) onNavigateTraining()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KinetiqCharcoalDark,
                                contentColor = KinetiqLavender
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (twistTemplate != null) "Retrain" else "Personalize",
                                style = MaterialTheme.typography.labelMedium
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(28.dp))

            // SECTION 2: MODE B - CUSTOM GESTURES
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MODE B: CUSTOM MOTION GESTURES",
                    style = MaterialTheme.typography.labelSmall,
                    color = KinetiqTextTertiary,
                    modifier = Modifier.padding(start = 4.dp)
                )

                TextButton(onClick = { showCreateDialog = true }) {
                    Icon(
                        imageVector = Icons.Outlined.Add,
                        contentDescription = "Add",
                        tint = KinetiqLavender,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "New Gesture",
                        style = MaterialTheme.typography.labelMedium,
                        color = KinetiqLavender
                    )
                }
            }

            if (customTemplates.isEmpty()) {
                KinetiqCard {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No custom gestures recorded yet",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KinetiqTextMuted
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Train discrete single-stroke (350–700 ms) or double-stroke (500–1400 ms) movements",
                            style = MaterialTheme.typography.bodySmall,
                            color = KinetiqTextTertiary
                        )
                    }
                }
            } else {
                customTemplates.forEach { template ->
                    KinetiqCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = template.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KinetiqOffWhite
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "${if (template.requiredStrokeCount == 1) "Single-Stroke" else "Double-Stroke"} • Action: ${template.assignedAction.actionName}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = KinetiqTextMuted
                                )
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Switch(
                                    checked = template.isEnabled,
                                    onCheckedChange = { viewModel.toggleTemplateEnabled(template.id, it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = KinetiqObsidian,
                                        checkedTrackColor = KinetiqLavender,
                                        uncheckedThumbColor = KinetiqTextMuted,
                                        uncheckedTrackColor = KinetiqCharcoalDark
                                    )
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                IconButton(onClick = { viewModel.deleteTemplate(template.id) }) {
                                    Icon(
                                        imageVector = Icons.Outlined.Delete,
                                        contentDescription = "Delete",
                                        tint = KinetiqTextMuted
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        StatusPill(
                            text = "PROVISIONALLY VALIDATED",
                            dotColor = KinetiqGreen,
                            backgroundColor = KinetiqGreenMuted
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }

    // Modal Dialog to configure New Custom Gesture
    if (showCreateDialog) {
        var gestureName by remember { mutableStateOf("") }
        var strokeCount by remember { mutableIntStateOf(1) }
        var selectedActionType by remember { mutableStateOf("FLASHLIGHT") }
        var packageName by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            containerColor = KinetiqCharcoalDark,
            title = {
                Text(
                    text = "New Custom Gesture",
                    style = MaterialTheme.typography.titleMedium,
                    color = KinetiqOffWhite
                )
            },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = gestureName,
                        onValueChange = { gestureName = it },
                        label = { Text("Gesture Name", color = KinetiqTextMuted) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Motion Pattern",
                        style = MaterialTheme.typography.labelSmall,
                        color = KinetiqTextTertiary
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = strokeCount == 1,
                            onClick = { strokeCount = 1 },
                            colors = RadioButtonDefaults.colors(selectedColor = KinetiqLavender)
                        )
                        Text(
                            text = "Single-Stroke (350–700 ms)",
                            style = MaterialTheme.typography.bodySmall,
                            color = KinetiqOffWhite
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = strokeCount == 2,
                            onClick = { strokeCount = 2 },
                            colors = RadioButtonDefaults.colors(selectedColor = KinetiqLavender)
                        )
                        Text(
                            text = "Double-Stroke (500–1400 ms)",
                            style = MaterialTheme.typography.bodySmall,
                            color = KinetiqOffWhite
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Assigned Action",
                        style = MaterialTheme.typography.labelSmall,
                        color = KinetiqTextTertiary
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedActionType == "FLASHLIGHT",
                            onClick = { selectedActionType = "FLASHLIGHT" },
                            colors = RadioButtonDefaults.colors(selectedColor = KinetiqLavender)
                        )
                        Text("Toggle Flashlight", style = MaterialTheme.typography.bodySmall, color = KinetiqOffWhite)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedActionType == "CAMERA",
                            onClick = { selectedActionType = "CAMERA" },
                            colors = RadioButtonDefaults.colors(selectedColor = KinetiqLavender)
                        )
                        Text("Launch Camera", style = MaterialTheme.typography.bodySmall, color = KinetiqOffWhite)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = selectedActionType == "APP",
                            onClick = { selectedActionType = "APP" },
                            colors = RadioButtonDefaults.colors(selectedColor = KinetiqLavender)
                        )
                        Text("Launch App", style = MaterialTheme.typography.bodySmall, color = KinetiqOffWhite)
                    }

                    if (selectedActionType == "APP") {
                        OutlinedTextField(
                            value = packageName,
                            onValueChange = { packageName = it },
                            label = { Text("App Package Name (e.g. com.spotify.music)", color = KinetiqTextMuted) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val action = when (selectedActionType) {
                            "FLASHLIGHT" -> ActionDefinition.ToggleFlashlight()
                            "CAMERA" -> ActionDefinition.LaunchCamera()
                            "APP" -> ActionDefinition.LaunchApp(
                                packageName = packageName.trim(),
                                appLabel = gestureName.trim()
                            )
                            else -> ActionDefinition.None
                        }
                        showCreateDialog = false
                        scope.launch {
                            val id = "custom_${UUID.randomUUID().toString().take(8)}"
                            val success = viewModel.startTraining(
                                gestureId = id,
                                displayName = gestureName.ifBlank { "Custom Gesture" },
                                isCore = false,
                                strokeCount = strokeCount,
                                action = action
                            )
                            if (success) {
                                onNavigateTraining()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = KinetiqLavender,
                        contentColor = KinetiqObsidian
                    ),
                    enabled = gestureName.isNotBlank() && (selectedActionType != "APP" || packageName.isNotBlank())
                ) {
                    Text("Start Training")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel", color = KinetiqTextMuted)
                }
            }
        )
    }
}
