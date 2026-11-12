package com.ayushman.kinetiq.ui.studio.training

import androidx.activity.compose.BackHandler
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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MotionPhotosOn
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ayushman.kinetiq.domain.studio.model.TrainingStep
import com.ayushman.kinetiq.domain.studio.model.ValidationStatus
import com.ayushman.kinetiq.ui.components.KinetiqCard
import com.ayushman.kinetiq.ui.components.StatusPill
import com.ayushman.kinetiq.ui.studio.GestureStudioViewModel
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

@Composable
fun TrainingFlowScreen(
    viewModel: GestureStudioViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val step by viewModel.trainingStep.collectAsState()
    val activeSession by viewModel.activeSession.collectAsState()

    val gestureName = activeSession?.displayName ?: "Gesture"
    val strokeCount = activeSession?.requiredStrokeCount ?: 2

    BackHandler {
        viewModel.cancelTraining()
        onNavigateBack()
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = KinetiqObsidian
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))

            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    viewModel.cancelTraining()
                    onNavigateBack()
                }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                        contentDescription = "Abort Training",
                        tint = KinetiqOffWhite
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "TRAINING: ${gestureName.uppercase()}",
                        style = MaterialTheme.typography.titleMedium,
                        color = KinetiqOffWhite
                    )
                    Text(
                        text = "${if (strokeCount == 1) "Single-Stroke" else "Double-Stroke"} • Action Suppressed",
                        style = MaterialTheme.typography.bodySmall,
                        color = KinetiqTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(30.dp))

            // Step Indicator Card
            when (val current = step) {
                is TrainingStep.Tier1Training -> {
                    TrainingStepView(
                        tierTitle = "TIER 1 OF 5: POSITIVE REPETITIONS",
                        instruction = "Perform your intended gesture naturally. The system segments and extracts your personal motion profile.",
                        current = current.currentRepetition,
                        target = current.targetRepetitions,
                        hint = if (strokeCount == 1) "Perform 1 distinct stroke (350–700 ms)" else "Perform 2 distinct strokes with inter-stroke pause <=350 ms"
                    )
                }

                is TrainingStep.Tier2Confirmation -> {
                    TrainingStepView(
                        tierTitle = "TIER 2 OF 5: CONFIRMATION STROKES",
                        instruction = "Positive cluster calibrated! Perform 2 additional confirmation gestures to verify envelope stability.",
                        current = current.currentRepetition,
                        target = current.targetRepetitions,
                        hint = "Maintain consistent motion speed and orientation."
                    )
                }

                is TrainingStep.Tier3NegativeCapture.DeskPickup -> {
                    TrainingStepView(
                        tierTitle = "TIER 3 OF 5: MANDATORY USER NEGATIVES (1/4)",
                        instruction = "Pick up your phone naturally from a desk or flat surface.",
                        current = current.currentRepetition,
                        target = current.targetRepetitions,
                        hint = "This trains the system never to misclassify casual pickups as gestures."
                    )
                }

                is TrainingStep.Tier3NegativeCapture.DeskPlacement -> {
                    TrainingStepView(
                        tierTitle = "TIER 3 OF 5: MANDATORY USER NEGATIVES (2/4)",
                        instruction = "Place your phone down naturally on a flat surface.",
                        current = current.currentRepetition,
                        target = current.targetRepetitions,
                        hint = "Captures deceleration shock profiles to reject table placements."
                    )
                }

                is TrainingStep.Tier3NegativeCapture.Walking -> {
                    KinetiqCard {
                        Text(
                            text = "TIER 3 OF 5: MANDATORY USER NEGATIVES (3/4)",
                            style = MaterialTheme.typography.labelSmall,
                            color = KinetiqLavender
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Walk naturally with the phone in your hand or pocket.",
                            style = MaterialTheme.typography.titleMedium,
                            color = KinetiqOffWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Captures rhythmic periodic acceleration to suppress gait artifacts.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KinetiqTextMuted
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.completeContinuousNegative(isWalking = true) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KinetiqLavender,
                                contentColor = KinetiqObsidian
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done Walking (5s completed)")
                        }
                    }
                }

                is TrainingStep.Tier3NegativeCapture.CasualHandling -> {
                    KinetiqCard {
                        Text(
                            text = "TIER 3 OF 5: MANDATORY USER NEGATIVES (4/4)",
                            style = MaterialTheme.typography.labelSmall,
                            color = KinetiqLavender
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "Handle the phone casually: tilt, rotate, and transfer hands.",
                            style = MaterialTheme.typography.titleMedium,
                            color = KinetiqOffWhite
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Builds negative envelope margin to eliminate everyday false triggers.",
                            style = MaterialTheme.typography.bodySmall,
                            color = KinetiqTextMuted
                        )
                        Spacer(modifier = Modifier.height(20.dp))
                        Button(
                            onClick = { viewModel.completeContinuousNegative(isWalking = false) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KinetiqLavender,
                                contentColor = KinetiqObsidian
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Done Handling (Proceed to Validation)")
                        }
                    }
                }

                is TrainingStep.Tier4HoldOutValidation.Positives -> {
                    TrainingStepView(
                        tierTitle = "TIER 4 OF 5: HOLD-OUT VALIDATION (POSITIVES)",
                        instruction = "Perform 5 fresh positive gestures. These independent hold-out samples verify sensitivity.",
                        current = current.currentRepetition,
                        target = current.targetRepetitions,
                        hint = "Must fall within threshold and exceed 0.85 negative margin."
                    )
                }

                is TrainingStep.Tier4HoldOutValidation.Negatives -> {
                    TrainingStepView(
                        tierTitle = "TIER 4 OF 5: HOLD-OUT VALIDATION (NEGATIVES)",
                        instruction = "Perform non-gesture motion: ${current.currentMovement}.",
                        current = current.currentRepetition,
                        target = current.totalRepetitions,
                        hint = "10 fresh negative motions tested to ensure ZERO false activations."
                    )
                }

                is TrainingStep.Tier5ConflictAdvisory -> {
                    KinetiqCard {
                        if (current.advisoryWarning != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.Warning,
                                    contentDescription = "Conflict Warning",
                                    tint = KinetiqAmber,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Conflict Advisory",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KinetiqAmber
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = current.advisoryWarning,
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqOffWhite
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Notice: This advisory is heuristic-only. You may save the profile or re-record with more distinct biomechanics.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KinetiqTextMuted
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "No Conflict",
                                    tint = KinetiqGreen,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "No Conflicts Detected",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = KinetiqGreen
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "This gesture profile is well-separated from all existing gestures and user negative libraries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = KinetiqTextMuted
                            )
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                val saved = viewModel.saveGestureProfile()
                                if (saved) onNavigateBack()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = KinetiqLavender,
                                contentColor = KinetiqObsidian
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Save & Activate Gesture Profile")
                        }
                    }
                }

                is TrainingStep.Completed -> {
                    KinetiqCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(KinetiqGreenMuted, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = "Success",
                                    tint = KinetiqGreen,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Training Complete",
                                style = MaterialTheme.typography.titleLarge,
                                color = KinetiqOffWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            StatusPill(
                                text = "PROVISIONALLY VALIDATED",
                                dotColor = KinetiqGreen,
                                backgroundColor = KinetiqGreenMuted
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Gesture profile saved successfully. Production action dispatch is now active.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqTextMuted
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Button(
                                onClick = onNavigateBack,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = KinetiqLavender,
                                    contentColor = KinetiqObsidian
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Done")
                            }
                        }
                    }
                }

                is TrainingStep.Failed -> {
                    KinetiqCard {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .background(KinetiqAmberMuted, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.ErrorOutline,
                                    contentDescription = "Failed",
                                    tint = KinetiqAmber,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Training Gate Failed",
                                style = MaterialTheme.typography.titleLarge,
                                color = KinetiqOffWhite,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = current.reason,
                                style = MaterialTheme.typography.bodyMedium,
                                color = KinetiqAmber
                            )
                            Spacer(modifier = Modifier.height(24.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.cancelTraining()
                                        onNavigateBack()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Cancel", color = KinetiqTextMuted)
                                }
                                Button(
                                    onClick = {
                                        viewModel.cancelTraining()
                                        onNavigateBack()
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = KinetiqLavender,
                                        contentColor = KinetiqObsidian
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Retry")
                                }
                            }
                        }
                    }
                }

                is TrainingStep.Idle -> {
                    Text(
                        text = "Initializing training sensor listeners...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = KinetiqTextMuted
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun TrainingStepView(
    tierTitle: String,
    instruction: String,
    current: Int,
    target: Int,
    hint: String
) {
    KinetiqCard {
        Text(
            text = tierTitle,
            style = MaterialTheme.typography.labelSmall,
            color = KinetiqLavender
        )
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = instruction,
            style = MaterialTheme.typography.titleMedium,
            color = KinetiqOffWhite
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = KinetiqTextMuted
        )

        Spacer(modifier = Modifier.height(20.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Repetitions Captured",
                style = MaterialTheme.typography.bodySmall,
                color = KinetiqTextTertiary
            )
            Text(
                text = "$current / $target",
                style = MaterialTheme.typography.titleMedium,
                color = KinetiqOffWhite,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        val progress = if (target > 0) (current.toFloat() / target.toFloat()).coerceIn(0f, 1f) else 0f
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp),
            color = KinetiqLavender,
            trackColor = KinetiqCharcoalDark
        )

        Spacer(modifier = Modifier.height(14.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.MotionPhotosOn,
                contentDescription = "Listening",
                tint = KinetiqGreen,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Awaiting movement...",
                style = MaterialTheme.typography.labelSmall,
                color = KinetiqGreen
            )
        }
    }
}

