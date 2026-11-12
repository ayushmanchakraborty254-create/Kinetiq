package com.ayushman.kinetiq.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.ayushman.kinetiq.data.preferences.PreferencesRepository
import com.ayushman.kinetiq.ui.calibration.CalibrationScreen
import com.ayushman.kinetiq.ui.calibration.CalibrationViewModel
import com.ayushman.kinetiq.ui.diagnostics.DiagnosticsScreen
import com.ayushman.kinetiq.ui.diagnostics.DiagnosticsViewModel
import com.ayushman.kinetiq.ui.home.HomeScreen
import com.ayushman.kinetiq.ui.home.HomeViewModel
import com.ayushman.kinetiq.ui.settings.SettingsScreen
import com.ayushman.kinetiq.ui.studio.GestureStudioScreen
import com.ayushman.kinetiq.ui.studio.GestureStudioViewModel
import com.ayushman.kinetiq.ui.studio.training.TrainingFlowScreen

@Composable
fun KinetiqNavGraph(
    preferencesRepository: PreferencesRepository,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Home.route,
        modifier = modifier
    ) {
        composable(Screen.Home.route) {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateSettings = { navController.navigate(Screen.Settings.route) },
                onNavigateCalibration = { navController.navigate(Screen.Calibration.route) },
                onNavigateDiagnostics = { navController.navigate(Screen.Diagnostics.route) },
                onNavigateGestureStudio = { navController.navigate(Screen.GestureStudio.route) }
            )
        }

        composable(Screen.Settings.route) {
            SettingsScreen(
                preferencesRepository = preferencesRepository,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Calibration.route) {
            val calibrationViewModel: CalibrationViewModel = viewModel()
            CalibrationScreen(
                viewModel = calibrationViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.Diagnostics.route) {
            val diagnosticsViewModel: DiagnosticsViewModel = viewModel()
            DiagnosticsScreen(
                viewModel = diagnosticsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.GestureStudio.route) { backStackEntry ->
            // Parent/shared ViewModel for Studio and Training Flow
            val studioViewModel: GestureStudioViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Screen.Home.route)
            )
            GestureStudioScreen(
                viewModel = studioViewModel,
                onNavigateBack = { navController.popBackStack() },
                onNavigateTraining = { navController.navigate(Screen.TrainingFlow.route) }
            )
        }

        composable(Screen.TrainingFlow.route) {
            val studioViewModel: GestureStudioViewModel = viewModel(
                viewModelStoreOwner = navController.getBackStackEntry(Screen.Home.route)
            )
            TrainingFlowScreen(
                viewModel = studioViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

