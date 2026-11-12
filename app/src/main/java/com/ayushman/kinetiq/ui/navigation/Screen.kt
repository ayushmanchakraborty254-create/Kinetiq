package com.ayushman.kinetiq.ui.navigation

sealed class Screen(val route: String) {
    object Home : Screen("home")
    object Settings : Screen("settings")
    object Calibration : Screen("calibration")
    object Diagnostics : Screen("diagnostics")
    object GestureStudio : Screen("gesture_studio")
    object TrainingFlow : Screen("training_flow")
}

