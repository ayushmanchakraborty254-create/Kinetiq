package com.ayushman.kinetiq.domain.gesture

import com.ayushman.kinetiq.domain.model.GestureEvent
import com.ayushman.kinetiq.domain.model.GestureState
import com.ayushman.kinetiq.domain.model.SensorReading
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface GestureDetector<T : SensorReading> {
    val state: StateFlow<GestureState>
    val events: Flow<GestureEvent>

    fun processReading(reading: T)
    fun reset()
}

