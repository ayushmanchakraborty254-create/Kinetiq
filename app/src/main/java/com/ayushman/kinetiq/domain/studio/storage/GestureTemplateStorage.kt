package com.ayushman.kinetiq.domain.studio.storage

import android.content.Context
import android.util.Log
import com.ayushman.kinetiq.domain.studio.model.ActionDefinition
import com.ayushman.kinetiq.domain.studio.model.GestureTemplate
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Local file storage for personalized and custom gesture profiles.
 * Files are persisted as JSON in internal app storage: `/files/trained_gestures/<gesture_id>.json`.
 * Implements crash-safe atomic writes and a concurrent in-memory cache for zero disk I/O on sensor hot paths.
 */
class GestureTemplateStorage(
    private val context: Context? = null,
    private val overrideDir: File? = null
) {

    companion object {
        private const val TAG = "KinetiqTemplateStorage"
        private const val DIRECTORY_NAME = "trained_gestures"

        const val ID_DOUBLE_CHOP = "gesture_double_chop"
        const val ID_DOUBLE_TWIST = "gesture_double_twist"
    }

    private val storageDir: File by lazy {
        val baseDir = overrideDir ?: context?.filesDir ?: File(System.getProperty("java.io.tmpdir"), "kinetiq_test_templates")
        File(baseDir, DIRECTORY_NAME).apply {
            if (!exists()) mkdirs()
        }
    }

    private val templateCache = ConcurrentHashMap<String, GestureTemplate>()
    @Volatile
    private var isCacheInitialized = false

    init {
        ensureCacheLoaded()
    }

    @Synchronized
    private fun ensureCacheLoaded() {
        if (isCacheInitialized) return
        try {
            val files = storageDir.listFiles { file -> file.extension == "json" } ?: emptyArray()
            for (f in files) {
                try {
                    val json = f.readText()
                    val t = GestureTemplate.fromJson(json)
                    val enforced = when (t.id) {
                        ID_DOUBLE_CHOP -> t.copy(assignedAction = ActionDefinition.ToggleFlashlight(isImmutable = true))
                        ID_DOUBLE_TWIST -> t.copy(assignedAction = ActionDefinition.LaunchCamera(isImmutable = true))
                        else -> t
                    }
                    templateCache[enforced.id] = enforced
                } catch (e: Exception) {
                    Log.e(TAG, "Skipping malformed or corrupted template file: ${f.name}", e)
                }
            }
            isCacheInitialized = true
        } catch (e: Exception) {
            Log.e(TAG, "Error initializing gesture template cache", e)
        }
    }

    /**
     * Saves or updates a gesture template JSON profile.
     * Enforces immutable core action mappings and performs atomic crash-safe write.
     */
    fun saveTemplate(template: GestureTemplate): Boolean {
        return try {
            val enforcedTemplate = when (template.id) {
                ID_DOUBLE_CHOP -> template.copy(
                    assignedAction = ActionDefinition.ToggleFlashlight(isImmutable = true)
                )
                ID_DOUBLE_TWIST -> template.copy(
                    assignedAction = ActionDefinition.LaunchCamera(isImmutable = true)
                )
                else -> template
            }

            val targetFile = File(storageDir, "${enforcedTemplate.id}.json")
            val tempFile = File(storageDir, "${enforcedTemplate.id}.tmp")

            // Atomic crash-safe write: write to temp file then rename
            tempFile.writeText(enforcedTemplate.toJson())
            if (targetFile.exists()) {
                targetFile.delete()
            }
            val renamed = tempFile.renameTo(targetFile)
            if (!renamed) {
                // Fallback direct write if rename fails
                targetFile.writeText(enforcedTemplate.toJson())
                tempFile.delete()
            }

            templateCache[enforcedTemplate.id] = enforcedTemplate
            Log.i(TAG, "Successfully saved gesture template '${enforcedTemplate.id}' (atomic write & cached)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save gesture template '${template.id}'", e)
            false
        }
    }

    /**
     * Loads a specific gesture template by ID from cache (zero disk I/O on sensor hot path).
     */
    fun loadTemplate(id: String): GestureTemplate? {
        ensureCacheLoaded()
        return templateCache[id]
    }

    /**
     * Returns all saved gesture templates from in-memory cache.
     */
    fun loadAllTemplates(): List<GestureTemplate> {
        ensureCacheLoaded()
        return templateCache.values.toList()
    }

    /**
     * Deletes a gesture template, resetting it back to factory defaults or removing custom gestures.
     */
    fun deleteTemplate(id: String): Boolean {
        templateCache.remove(id)
        val file = File(storageDir, "$id.json")
        val tmpFile = File(storageDir, "$id.tmp")
        tmpFile.delete()

        return if (file.exists()) {
            val deleted = file.delete()
            Log.i(TAG, "Deleted template file for '$id': $deleted")
            deleted
        } else {
            true
        }
    }

    fun hasPersonalizedTemplate(id: String): Boolean {
        ensureCacheLoaded()
        return templateCache.containsKey(id)
    }
}
