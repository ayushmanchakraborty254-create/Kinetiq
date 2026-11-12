package com.ayushman.kinetiq

import com.ayushman.kinetiq.domain.calibration.GestureCalibrationManager
import com.ayushman.kinetiq.domain.model.GestureType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class GestureCalibrationManagerTest {

    private lateinit var calibrationManager: GestureCalibrationManager

    @Before
    fun setUp() {
        calibrationManager = GestureCalibrationManager()
        calibrationManager.startCalibration()
    }

    @Test
    fun testDoubleChopCalibrationProfileCalculation() {
        calibrationManager.recordPeak(18.0f)
        calibrationManager.recordPeak(20.0f)
        calibrationManager.recordPeak(22.0f)

        assertEquals(3, calibrationManager.getSampleCount())

        val profile = calibrationManager.calculateProfile(GestureType.DOUBLE_CHOP)
        assertNotNull(profile)
        assertEquals(20.0f, profile!!.averagePeak, 0.01f)
        // 75% of 20.0 = 15.0
        assertEquals(15.0f, profile.recommendedPeakThreshold, 0.01f)
        assertTrue(profile.recommendedNeutralThreshold < profile.recommendedPeakThreshold)
    }

    @Test
    fun testThresholdClampingSafety() {
        // Test excessively high peaks
        calibrationManager.recordPeak(50.0f)
        calibrationManager.recordPeak(60.0f)
        val profile = calibrationManager.calculateProfile(GestureType.DOUBLE_CHOP)
        assertNotNull(profile)
        // Max clamped to 22.0
        assertEquals(22.0f, profile!!.recommendedPeakThreshold, 0.01f)

        // Test excessively low peaks
        calibrationManager.startCalibration()
        calibrationManager.recordPeak(4.0f)
        calibrationManager.recordPeak(5.0f)
        val lowProfile = calibrationManager.calculateProfile(GestureType.DOUBLE_CHOP)
        assertNotNull(lowProfile)
        // Min clamped to 9.0
        assertEquals(9.0f, lowProfile!!.recommendedPeakThreshold, 0.01f)
    }
}

