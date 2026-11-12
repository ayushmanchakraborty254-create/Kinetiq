package com.ayushman.kinetiq.domain.studio.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Persisted profile for a trained or personalized gesture.
 */
data class GestureTemplate(
    val id: String,
    val displayName: String,
    val isCorePersonalization: Boolean,
    val requiredStrokeCount: Int,
    val assignedAction: ActionDefinition,
    val isEnabled: Boolean,
    val validationStatus: ValidationStatus,
    val createdEpochMs: Long,
    val positiveDistanceThreshold: Double,
    val positiveClusterMean: Double,
    val positiveClusterStdDev: Double,
    val negativeMarginRatio: Double = 0.85,
    val slantedBandRadius: Int = 8,
    val kinematicWeight: Double = 0.20,
    val kinematicBaselines: KinematicBaselines,
    val medoidTemplate: MotionCandidate,
    val userNegativeTemplates: List<MotionCandidate> = emptyList()
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 11
        const val CORE_CHOP_ID = "gesture_double_chop"
        const val CORE_TWIST_ID = "gesture_double_twist"

        fun fromJson(jsonStr: String): GestureTemplate {
            val root = JSONObject(jsonStr)
            val id = root.getString("id")
            val isCore = root.optBoolean("isCorePersonalization", false)
            val displayName = root.getString("displayName")
            val requiredStrokeCount = root.getInt("requiredStrokeCount")

            // Immutable core mapping enforcement
            val assignedAction = when {
                id == "gesture_double_chop" || (isCore && displayName.contains("Chop", ignoreCase = true)) ->
                    ActionDefinition.ToggleFlashlight(isImmutable = true)
                id == "gesture_double_twist" || (isCore && displayName.contains("Twist", ignoreCase = true)) ->
                    ActionDefinition.LaunchCamera(isImmutable = true)
                else -> {
                    val actionObj = root.optJSONObject("assignedAction")
                    val type = actionObj?.optString("type") ?: "NONE"
                    when (type) {
                        "TOGGLE_FLASHLIGHT" -> ActionDefinition.ToggleFlashlight(isImmutable = false)
                        "LAUNCH_CAMERA" -> ActionDefinition.LaunchCamera(isImmutable = false)
                        "LAUNCH_APPLICATION" -> ActionDefinition.LaunchApp(
                            packageName = actionObj?.optString("targetPackage") ?: "",
                            isImmutable = false
                        )
                        else -> ActionDefinition.None
                    }
                }
            }

            val isEnabled = root.optBoolean("isEnabled", true)
            val statusStr = root.optString("validationStatus", "PROVISIONALLY_VALIDATED")
            val validationStatus = try {
                ValidationStatus.valueOf(statusStr)
            } catch (e: Exception) {
                ValidationStatus.PROVISIONALLY_VALIDATED
            }
            val createdEpochMs = root.optLong("createdEpochMs", System.currentTimeMillis())

            val tuning = root.optJSONObject("tuningParameters") ?: JSONObject()
            val posThreshold = tuning.optDouble("positiveDistanceThreshold", 8.5)
            val posMean = tuning.optDouble("positiveClusterMean", 5.0)
            val posStdDev = tuning.optDouble("positiveClusterStdDev", 1.0)
            val negMargin = tuning.optDouble("negativeMarginRatio", 0.85)
            val bandRadius = tuning.optInt("slantedBandRadius", 8)
            val kinWeight = tuning.optDouble("kinematicWeight", 0.20)

            val kinObj = root.optJSONObject("kinematicBaselines") ?: JSONObject()
            val kinBaselines = KinematicBaselines.fromJson(kinObj)

            val medoidObj = root.getJSONObject("medoidTemplate")
            val medoid = parseCandidate(medoidObj, requiredStrokeCount)

            val negsArray = root.optJSONArray("userNegativeTemplates") ?: JSONArray()
            val userNegatives = mutableListOf<MotionCandidate>()
            for (i in 0 until negsArray.length()) {
                val negObj = negsArray.getJSONObject(i)
                userNegatives.add(parseCandidate(negObj, strokeCount = 1))
            }

            return GestureTemplate(
                id = id,
                displayName = displayName,
                isCorePersonalization = isCore,
                requiredStrokeCount = requiredStrokeCount,
                assignedAction = assignedAction,
                isEnabled = isEnabled,
                validationStatus = validationStatus,
                createdEpochMs = createdEpochMs,
                positiveDistanceThreshold = posThreshold,
                positiveClusterMean = posMean,
                positiveClusterStdDev = posStdDev,
                negativeMarginRatio = negMargin,
                slantedBandRadius = bandRadius,
                kinematicWeight = kinWeight,
                kinematicBaselines = kinBaselines,
                medoidTemplate = medoid,
                userNegativeTemplates = userNegatives
            )
        }

        private fun parseCandidate(obj: JSONObject, strokeCount: Int): MotionCandidate {
            val durationMs = obj.optLong("durationMs", 700L)
            val vectorsArray = obj.getJSONArray("vectors")
            val vectors = mutableListOf<DoubleArray>()
            val timestamps = mutableListOf<Long>()

            val stepMs = if (vectorsArray.length() > 1) durationMs / (vectorsArray.length() - 1) else 20L
            for (i in 0 until vectorsArray.length()) {
                val sampleArr = vectorsArray.getJSONArray(i)
                val vec = DoubleArray(6) { idx -> sampleArr.optDouble(idx, 0.0) }
                vectors.add(vec)
                timestamps.add(i * stepMs)
            }

            return MotionCandidate(
                vectors = vectors,
                timestampsMs = timestamps,
                strokeCount = strokeCount,
                hasInterStrokePause = strokeCount > 1,
                durationMs = durationMs,
                physicalEndTimestampMs = durationMs
            )
        }
    }

    fun toJson(): String {
        val root = JSONObject()
        root.put("schemaVersion", CURRENT_SCHEMA_VERSION)
        root.put("id", id)
        root.put("displayName", displayName)
        root.put("isCorePersonalization", isCorePersonalization)
        root.put("requiredStrokeCount", requiredStrokeCount)

        val actionObj = JSONObject()
        when (assignedAction) {
            is ActionDefinition.ToggleFlashlight -> {
                actionObj.put("type", "TOGGLE_FLASHLIGHT")
                actionObj.put("isImmutable", assignedAction.isImmutable)
                actionObj.put("targetPackage", JSONObject.NULL)
            }
            is ActionDefinition.LaunchCamera -> {
                actionObj.put("type", "LAUNCH_CAMERA")
                actionObj.put("isImmutable", assignedAction.isImmutable)
                actionObj.put("targetPackage", JSONObject.NULL)
            }
            is ActionDefinition.LaunchApp -> {
                actionObj.put("type", "LAUNCH_APPLICATION")
                actionObj.put("isImmutable", false)
                actionObj.put("targetPackage", assignedAction.packageName)
            }
            ActionDefinition.None -> {
                actionObj.put("type", "NONE")
                actionObj.put("isImmutable", false)
                actionObj.put("targetPackage", JSONObject.NULL)
            }
        }
        root.put("assignedAction", actionObj)
        root.put("isEnabled", isEnabled)
        root.put("validationStatus", validationStatus.name)
        root.put("createdEpochMs", createdEpochMs)

        val tuningObj = JSONObject()
        tuningObj.put("positiveDistanceThreshold", positiveDistanceThreshold)
        tuningObj.put("positiveClusterMean", positiveClusterMean)
        tuningObj.put("positiveClusterStdDev", positiveClusterStdDev)
        tuningObj.put("negativeMarginRatio", negativeMarginRatio)
        tuningObj.put("slantedBandRadius", slantedBandRadius)
        tuningObj.put("kinematicWeight", kinematicWeight)
        root.put("tuningParameters", tuningObj)

        root.put("kinematicBaselines", kinematicBaselines.toJson())

        val medoidObj = candidateToJson(medoidTemplate)
        root.put("medoidTemplate", medoidObj)

        val negArray = JSONArray()
        for (neg in userNegativeTemplates) {
            negArray.put(candidateToJson(neg))
        }
        root.put("userNegativeTemplates", negArray)

        return root.toString(2)
    }

    private fun candidateToJson(candidate: MotionCandidate): JSONObject {
        val obj = JSONObject()
        obj.put("sampleCount", candidate.sampleCount)
        obj.put("durationMs", candidate.durationMs)
        val channels = JSONArray().apply {
            put("ax"); put("ay"); put("az"); put("gx"); put("gy"); put("gz")
        }
        obj.put("channels", channels)

        val vecArray = JSONArray()
        for (vec in candidate.vectors) {
            val sampleArr = JSONArray()
            for (v in vec) sampleArr.put(v)
            vecArray.put(sampleArr)
        }
        obj.put("vectors", vecArray)
        return obj
    }
}

data class KinematicBaselines(
    val peakAccelMean: Double,
    val peakAccelStdDev: Double,
    val peakGyroMean: Double,
    val peakGyroStdDev: Double,
    val durationMeanMs: Double,
    val durationStdDevMs: Double,
    val pauseMeanMs: Double? = null,
    val pauseStdDevMs: Double? = null
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()

        val accObj = JSONObject().apply {
            put("mean", peakAccelMean)
            put("effectiveStdDev", peakAccelStdDev)
        }
        obj.put("peakLinearAcceleration", accObj)

        val gyroObj = JSONObject().apply {
            put("mean", peakGyroMean)
            put("effectiveStdDev", peakGyroStdDev)
        }
        obj.put("peakAngularVelocity", gyroObj)

        val durObj = JSONObject().apply {
            put("mean", durationMeanMs)
            put("effectiveStdDev", durationStdDevMs)
        }
        obj.put("totalDurationMs", durObj)

        if (pauseMeanMs != null && pauseStdDevMs != null) {
            val pauseObj = JSONObject().apply {
                put("mean", pauseMeanMs)
                put("effectiveStdDev", pauseStdDevMs)
            }
            obj.put("interStrokePauseMs", pauseObj)
        }
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): KinematicBaselines {
            val acc = obj.optJSONObject("peakLinearAcceleration")
            val gyro = obj.optJSONObject("peakAngularVelocity")
            val dur = obj.optJSONObject("totalDurationMs")
            val pause = obj.optJSONObject("interStrokePauseMs")

            return KinematicBaselines(
                peakAccelMean = acc?.optDouble("mean", 15.0) ?: 15.0,
                peakAccelStdDev = acc?.optDouble("effectiveStdDev", 1.5) ?: 1.5,
                peakGyroMean = gyro?.optDouble("mean", 2.0) ?: 2.0,
                peakGyroStdDev = gyro?.optDouble("effectiveStdDev", 0.5) ?: 0.5,
                durationMeanMs = dur?.optDouble("mean", 750.0) ?: 750.0,
                durationStdDevMs = dur?.optDouble("effectiveStdDev", 50.0) ?: 50.0,
                pauseMeanMs = pause?.optDouble("mean"),
                pauseStdDevMs = pause?.optDouble("effectiveStdDev")
            )
        }
    }
}
