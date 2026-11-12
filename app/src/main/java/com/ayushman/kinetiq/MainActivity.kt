package com.ayushman.kinetiq

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.ayushman.kinetiq.ui.navigation.KinetiqNavGraph
import com.ayushman.kinetiq.ui.theme.KinetiqTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as KinetiqApplication
        val preferencesRepository = app.preferencesRepository

        setContent {
            KinetiqTheme {
                KinetiqNavGraph(
                    preferencesRepository = preferencesRepository
                )
            }
        }
    }
}

