package com.ayushman.kinetiq.domain.studio

import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import com.ayushman.kinetiq.domain.studio.model.GestureTemplate
import com.ayushman.kinetiq.domain.studio.model.KinematicBaselines
import com.ayushman.kinetiq.domain.studio.model.ValidationStatus
import com.ayushman.kinetiq.domain.studio.storage.GestureTemplateStorage
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class GestureTemplateStorageTest {

    private lateinit var tempDir: File
    private lateinit var storage: GestureTemplateStorage

    @Before
    fun setUp() {
        tempDir = File(System.getProperty("java.io.tmpdir"), "kinetiq_storage_test_${System.currentTimeMillis()}")
        tempDir.mkdirs()
        storage = GestureTemplateStorage(overrideDir = tempDir)
    }

    @After
    fun tearDown() {
        tempDir.deleteRecursively()
    }

    @Test
    fun testSaveAndLoadTemplate_preservesProfileAndEnforcesImmutability() {
        val medoid = StudioTestHelper.createCandidate(sampleCount = 20, strokeCount = 2)
        val template = GestureTemplate(
            id = GestureTemplate.CORE_CHOP_ID,
            displayName = "Personal Double Chop",
            isCorePersonalization = true,
            requiredStrokeCount = 2,
            assignedAction = ActionDefinition.ToggleFlashlight(isImmutable = true),
            isEnabled = true,
            validationStatus = ValidationStatus.PROVISIONALLY_VALIDATED,
            createdEpochMs = 123456789L,
            positiveDistanceThreshold = 8.0,
            positiveClusterMean = 3.5,
            positiveClusterStdDev = 1.0,
            kinematicBaselines = KinematicBaselines(
                peakAccelMean = 15.0,
                peakAccelStdDev = 1.0,
                peakGyroMean = 3.0,
                peakGyroStdDev = 0.5,
                durationMeanMs = 600.0,
                durationStdDevMs = 50.0
            ),
            medoidTemplate = medoid,
            userNegativeTemplates = listOf(StudioTestHelper.createNoiseCandidate())
        )

        val saved = storage.saveTemplate(template)
        assertTrue("Template must save successfully", saved)

        val loaded = storage.loadTemplate(GestureTemplate.CORE_CHOP_ID)
        assertNotNull("Template must be loaded from cache/storage", loaded)
        assertEquals("Personal Double Chop", loaded!!.displayName)
        assertTrue("Double chop action must be immutable ToggleFlashlight", loaded.assignedAction is ActionDefinition.ToggleFlashlight)
        assertTrue("Double chop action must have isImmutable = true", loaded.assignedAction.isImmutable)
    }

    @Test
    fun testMalformedJsonFile_doesNotCrashMonitoring() {
        // Write a valid template
        val medoid = StudioTestHelper.createCandidate(sampleCount = 20, strokeCount = 2)
        val validTemplate = GestureTemplate(
            id = "valid_custom",
            displayName = "Valid Custom",
            isCorePersonalization = false,
            requiredStrokeCount = 2,
            assignedAction = ActionDefinition.LaunchCamera(isImmutable = false),
            isEnabled = true,
            validationStatus = ValidationStatus.PROVISIONALLY_VALIDATED,
            createdEpochMs = 123456789L,
            positiveDistanceThreshold = 8.0,
            positiveClusterMean = 3.5,
            positiveClusterStdDev = 1.0,
            kinematicBaselines = KinematicBaselines(15.0, 1.0, 3.0, 0.5, 600.0, 50.0),
            medoidTemplate = medoid
        )
        storage.saveTemplate(validTemplate)

        // Directly inject a corrupted/malformed JSON file into the storage directory
        val corruptFile = File(tempDir, "trained_gestures/corrupt_gesture.json")
        corruptFile.writeText("{ this is definitely not valid JSON ::: }}}")

        // Also inject a 0-byte incomplete file
        val emptyFile = File(tempDir, "trained_gestures/empty_gesture.json")
        emptyFile.writeText("")

        // Create a new storage instance simulating app restart reading files from disk
        val reloadedStorage = GestureTemplateStorage(overrideDir = tempDir)
        val allTemplates = reloadedStorage.loadAllTemplates()

        assertEquals("Malformed files must be safely ignored without crashing, loading only the 1 valid template", 1, allTemplates.size)
        assertEquals("valid_custom", allTemplates[0].id)
    }

    @Test
    fun testDeleteTemplate_removesFromStorageAndCache() {
        val medoid = StudioTestHelper.createCandidate(sampleCount = 20, strokeCount = 2)
        val template = GestureTemplate(
            id = "to_delete",
            displayName = "Temporary",
            isCorePersonalization = false,
            requiredStrokeCount = 2,
            assignedAction = ActionDefinition.None,
            isEnabled = true,
            validationStatus = ValidationStatus.PROVISIONALLY_VALIDATED,
            createdEpochMs = 123456789L,
            positiveDistanceThreshold = 8.0,
            positiveClusterMean = 3.5,
            positiveClusterStdDev = 1.0,
            kinematicBaselines = KinematicBaselines(15.0, 1.0, 3.0, 0.5, 600.0, 50.0),
            medoidTemplate = medoid
        )
        storage.saveTemplate(template)
        assertNotNull(storage.loadTemplate("to_delete"))

        val deleted = storage.deleteTemplate("to_delete")
        assertTrue(deleted)
        assertNull(storage.loadTemplate("to_delete"))
        assertTrue(storage.loadAllTemplates().none { it.id == "to_delete" })
    }
}

