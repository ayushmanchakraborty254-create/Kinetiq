package com.ayushman.kinetiq.data.sensor

import android.content.Context
import android.hardware.SensorManager
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.ayushman.kinetiq.domain.gesture.ChopGestureDetector
import com.ayushman.kinetiq.domain.gesture.DetectorDiagnosticListener
import com.ayushman.kinetiq.domain.gesture.GestureEngine
import com.ayushman.kinetiq.domain.gesture.TwistGestureDetector
import com.ayushman.kinetiq.domain.model.SensorReading
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedQueue

enum class SensorSamplingRate(val displayName: String, val delayUs: Int) {
    GAME("GAME (~50 Hz)", SensorManager.SENSOR_DELAY_GAME),
    UI("UI (~16 Hz)", SensorManager.SENSOR_DELAY_UI),
    NORMAL("NORMAL (5 Hz)", SensorManager.SENSOR_DELAY_NORMAL)
}

data class DiagnosticDetectorEvent(
    val timestampNs: Long,
    val elapsedMs: Long,
    val detector: String,
    val eventType: String,
    val details: String,
    val metricsJson: String
)

data class TraceSample(
    val timestampNs: Long,
    val elapsedMs: Long,
    val sensorType: String,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitude: Float,
    val intervalMs: Float,
    val orientation: Int,
    val screenInteractive: Boolean,
    val chopState: String,
    val twistState: String,
    // Synchronized sensor state at this exact instant
    val syncAx: Float,
    val syncAy: Float,
    val syncAz: Float,
    val syncAccelMag: Float,
    val syncGx: Float,
    val syncGy: Float,
    val syncGz: Float,
    val syncGyroSpeed: Float
)

data class TraceFileInfo(
    val fileName: String,
    val filePath: String,
    val label: String,
    val sampleCount: Int,
    val durationMs: Long,
    val fileSizeBytes: Long,
    val timestampFormatted: String
)

class SensorTraceRecorder(
    private val context: Context,
    private val sensorRepository: SensorRepository,
    private val scope: CoroutineScope
) {
    companion object {
        private const val TAG = "KinetiqRecorder"

        val STANDARD_LABELS = listOf(
            "intentional_double_wrist_twist",
            "single_wrist_flick",
            "phone_pickup",
            "phone_placement",
            "walking",
            "casual_handling",
            "intentional_double_chop"
        )
    }

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager

    // Local gesture detectors for observation during recording (zero camera/flashlight trigger)
    val observerChopDetector = ChopGestureDetector()
    val observerTwistDetector = TwistGestureDetector()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _sampleCount = MutableStateFlow(0)
    val sampleCount: StateFlow<Int> = _sampleCount.asStateFlow()

    private val _elapsedMs = MutableStateFlow(0L)
    val elapsedMs: StateFlow<Long> = _elapsedMs.asStateFlow()

    private val _currentLabel = MutableStateFlow(STANDARD_LABELS[0])
    val currentLabel: StateFlow<String> = _currentLabel.asStateFlow()

    private val _selectedRate = MutableStateFlow(SensorSamplingRate.GAME)
    val selectedRate: StateFlow<SensorSamplingRate> = _selectedRate.asStateFlow()

    private val _lastSavedTrace = MutableStateFlow<TraceFileInfo?>(null)
    val lastSavedTrace: StateFlow<TraceFileInfo?> = _lastSavedTrace.asStateFlow()

    private val _savedTraces = MutableStateFlow<List<TraceFileInfo>>(emptyList())
    val savedTraces: StateFlow<List<TraceFileInfo>> = _savedTraces.asStateFlow()

    private var activeJob: Job? = null
    private var tickerJob: Job? = null
    private val samplesQueue = ConcurrentLinkedQueue<TraceSample>()
    private val detectorEventsQueue = ConcurrentLinkedQueue<DiagnosticDetectorEvent>()

    private var recordingStartEpochMs: Long = 0L
    private var recordingStartNs: Long = 0L

    // Last timestamps per sensor to compute actual hardware sampling intervals
    private var lastLinAccelNs: Long = 0L
    private var lastRawAccelNs: Long = 0L
    private var lastGyroNs: Long = 0L
    private var lastProxNs: Long = 0L

    // Synchronized sensor state cache
    @Volatile private var latestAx: Float = 0f
    @Volatile private var latestAy: Float = 0f
    @Volatile private var latestAz: Float = 0f
    @Volatile private var latestAccelMag: Float = 0f

    @Volatile private var latestGx: Float = 0f
    @Volatile private var latestGy: Float = 0f
    @Volatile private var latestGz: Float = 0f
    @Volatile private var latestGyroSpeed: Float = 0f

    init {
        refreshSavedTraces()
    }

    fun setLabel(label: String) {
        _currentLabel.value = label
    }

    fun setSamplingRate(rate: SensorSamplingRate) {
        if (!_isRecording.value) {
            _selectedRate.value = rate
        }
    }

    fun startRecording(label: String? = null) {
        if (_isRecording.value) return

        val activeLabel = label ?: _currentLabel.value
        _currentLabel.value = activeLabel
        samplesQueue.clear()
        detectorEventsQueue.clear()
        _sampleCount.value = 0
        _elapsedMs.value = 0L

        recordingStartEpochMs = System.currentTimeMillis()
        recordingStartNs = System.nanoTime()

        lastLinAccelNs = 0L
        lastRawAccelNs = 0L
        lastGyroNs = 0L
        lastProxNs = 0L

        observerChopDetector.reset()
        observerTwistDetector.reset()

        // Attach diagnostic event listener to capture candidate starts, peaks, reversals, rejections, recognitions
        val diagListener = object : DetectorDiagnosticListener {
            override fun onDetectorEvent(
                detector: String,
                eventType: String,
                timestampNs: Long,
                details: String,
                metrics: Map<String, Any>
            ) {
                val elapsedMs = (System.nanoTime() - recordingStartNs) / 1_000_000L
                val metricsJson = JSONObject(metrics).toString()
                detectorEventsQueue.add(
                    DiagnosticDetectorEvent(
                        timestampNs = timestampNs,
                        elapsedMs = elapsedMs,
                        detector = detector,
                        eventType = eventType,
                        details = details,
                        metricsJson = metricsJson
                    )
                )
                Log.d(TAG, "[$detector] $eventType: $details")
            }
        }
        observerChopDetector.diagnosticListener = diagListener
        observerTwistDetector.diagnosticListener = diagListener

        // Suppress any gesture actions in GestureEngine during diagnostic capture
        GestureEngine.isDiagnosticSuppressed = true

        _isRecording.value = true

        val delayUs = _selectedRate.value.delayUs

        activeJob = scope.launch(Dispatchers.Default) {
            // 1. Linear Acceleration
            launch {
                sensorRepository.linearAccelerationFlow(delayUs).collect { reading ->
                    if (!_isRecording.value) return@collect
                    observerChopDetector.processReading(reading)
                    val nowNs = reading.timestampNs
                    val intervalMs = if (lastLinAccelNs > 0) (nowNs - lastLinAccelNs) / 1_000_000f else 0f
                    lastLinAccelNs = nowNs

                    latestAx = reading.x
                    latestAy = reading.y
                    latestAz = reading.z
                    latestAccelMag = reading.magnitude

                    recordSample(
                        sensorType = "LINEAR_ACCEL",
                        x = reading.x,
                        y = reading.y,
                        z = reading.z,
                        magnitude = reading.magnitude,
                        timestampNs = nowNs,
                        intervalMs = intervalMs
                    )
                }
            }

            // 2. Raw Accelerometer
            launch {
                sensorRepository.accelerometerFlow(delayUs).collect { reading ->
                    if (!_isRecording.value) return@collect
                    val nowNs = reading.timestampNs
                    val intervalMs = if (lastRawAccelNs > 0) (nowNs - lastRawAccelNs) / 1_000_000f else 0f
                    lastRawAccelNs = nowNs

                    recordSample(
                        sensorType = "RAW_ACCEL",
                        x = reading.x,
                        y = reading.y,
                        z = reading.z,
                        magnitude = reading.magnitude,
                        timestampNs = nowNs,
                        intervalMs = intervalMs
                    )
                }
            }

            // 3. Gyroscope
            launch {
                sensorRepository.gyroscopeFlow(delayUs).collect { reading ->
                    if (!_isRecording.value) return@collect
                    observerTwistDetector.processReading(reading)
                    val nowNs = reading.timestampNs
                    val intervalMs = if (lastGyroNs > 0) (nowNs - lastGyroNs) / 1_000_000f else 0f
                    lastGyroNs = nowNs

                    latestGx = reading.x
                    latestGy = reading.y
                    latestGz = reading.z
                    latestGyroSpeed = reading.angularSpeed

                    recordSample(
                        sensorType = "GYROSCOPE",
                        x = reading.x,
                        y = reading.y,
                        z = reading.z,
                        magnitude = reading.angularSpeed,
                        timestampNs = nowNs,
                        intervalMs = intervalMs
                    )
                }
            }

            // 4. Proximity
            launch {
                sensorRepository.proximityFlow().collect { reading ->
                    if (!_isRecording.value) return@collect
                    val nowNs = reading.timestampNs
                    val intervalMs = if (lastProxNs > 0) (nowNs - lastProxNs) / 1_000_000f else 0f
                    lastProxNs = nowNs

                    recordSample(
                        sensorType = "PROXIMITY",
                        x = reading.distanceCm,
                        y = if (reading.isNear) 1.0f else 0.0f,
                        z = reading.maxRangeCm,
                        magnitude = reading.distanceCm,
                        timestampNs = nowNs,
                        intervalMs = intervalMs
                    )
                }
            }
        }

        // Timer ticker
        tickerJob = scope.launch(Dispatchers.Default) {
            while (_isRecording.value) {
                kotlinx.coroutines.delay(100)
                _elapsedMs.value = System.currentTimeMillis() - recordingStartEpochMs
                _sampleCount.value = samplesQueue.size
            }
        }

        Log.i(TAG, "Diagnostic recording started: label='$activeLabel', rate=${_selectedRate.value.displayName}")
    }

    private fun recordSample(
        sensorType: String,
        x: Float,
        y: Float,
        z: Float,
        magnitude: Float,
        timestampNs: Long,
        intervalMs: Float
    ) {
        val elapsedMs = (System.nanoTime() - recordingStartNs) / 1_000_000L
        val orientation = context.resources.configuration.orientation
        val isScreenOn = powerManager?.isInteractive ?: true

        val sample = TraceSample(
            timestampNs = timestampNs,
            elapsedMs = elapsedMs,
            sensorType = sensorType,
            x = x,
            y = y,
            z = z,
            magnitude = magnitude,
            intervalMs = intervalMs,
            orientation = orientation,
            screenInteractive = isScreenOn,
            chopState = observerChopDetector.state.value.toString(),
            twistState = observerTwistDetector.state.value.toString(),
            syncAx = latestAx,
            syncAy = latestAy,
            syncAz = latestAz,
            syncAccelMag = latestAccelMag,
            syncGx = latestGx,
            syncGy = latestGy,
            syncGz = latestGz,
            syncGyroSpeed = latestGyroSpeed
        )
        samplesQueue.add(sample)
    }

    fun stopRecording(): TraceFileInfo? {
        if (!_isRecording.value) return null

        _isRecording.value = false
        activeJob?.cancel()
        tickerJob?.cancel()
        observerChopDetector.diagnosticListener = null
        observerTwistDetector.diagnosticListener = null
        GestureEngine.isDiagnosticSuppressed = false

        val totalDurationMs = System.currentTimeMillis() - recordingStartEpochMs
        val samplesList = samplesQueue.toList()
        val eventsList = detectorEventsQueue.toList()
        _sampleCount.value = samplesList.size

        if (samplesList.isEmpty()) {
            Log.w(TAG, "No samples collected during trace session")
            return null
        }

        val info = saveTraceToDisk(samplesList, eventsList, totalDurationMs)
        _lastSavedTrace.value = info
        refreshSavedTraces()
        return info
    }

    private fun saveTraceToDisk(
        samples: List<TraceSample>,
        events: List<DiagnosticDetectorEvent>,
        durationMs: Long
    ): TraceFileInfo {
        val dir = File(context.getExternalFilesDir(null), "sensor_traces")
        if (!dir.exists()) {
            dir.mkdirs()
        }

        val dateStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date(recordingStartEpochMs))
        val baseName = "${_currentLabel.value}_$dateStamp"

        val jsonFile = File(dir, "$baseName.json")
        val csvFile = File(dir, "$baseName.csv")
        val eventsFile = File(dir, "${baseName}_events.csv")

        // 1. Write CSV with synchronized columns
        FileWriter(csvFile).use { writer ->
            writer.appendLine("timestampNs,elapsedMs,sensorType,x,y,z,magnitude,intervalMs,syncAx,syncAy,syncAz,syncAccelMag,syncGx,syncGy,syncGz,syncGyroSpeed,orientation,screenInteractive,chopState,twistState")
            for (s in samples) {
                writer.appendLine(
                    "${s.timestampNs},${s.elapsedMs},${s.sensorType},${s.x},${s.y},${s.z},${s.magnitude},${s.intervalMs}," +
                    "${s.syncAx},${s.syncAy},${s.syncAz},${s.syncAccelMag}," +
                    "${s.syncGx},${s.syncGy},${s.syncGz},${s.syncGyroSpeed}," +
                    "${s.orientation},${s.screenInteractive},\"${s.chopState}\",\"${s.twistState}\""
                )
            }
        }

        // 2. Write Events CSV (chronological log of detector transitions, candidate starts, reversals, rejections, recognitions)
        FileWriter(eventsFile).use { writer ->
            writer.appendLine("timestampNs,elapsedMs,detector,eventType,details,metrics")
            for (e in events) {
                writer.appendLine("${e.timestampNs},${e.elapsedMs},${e.detector},${e.eventType},\"${e.details.replace("\"", "\"\"")}\",\"${e.metricsJson.replace("\"", "\"\"")}\"")
            }
        }

        // 3. Write comprehensive JSON
        val root = JSONObject()
        root.put("label", _currentLabel.value)
        root.put("timestampEpochMs", recordingStartEpochMs)
        root.put("dateFormatted", dateStamp)
        root.put("durationMs", durationMs)
        root.put("sampleCount", samples.size)
        root.put("eventCount", events.size)
        root.put("requestedRate", _selectedRate.value.name)
        root.put("deviceManufacturer", Build.MANUFACTURER)
        root.put("deviceModel", Build.MODEL)
        root.put("deviceBrand", Build.BRAND)
        root.put("osVersionSdk", Build.VERSION.SDK_INT)

        val eventsArray = JSONArray()
        for (e in events) {
            val eObj = JSONObject()
            eObj.put("t_ns", e.timestampNs)
            eObj.put("elapsed_ms", e.elapsedMs)
            eObj.put("detector", e.detector)
            eObj.put("event", e.eventType)
            eObj.put("details", e.details)
            try {
                eObj.put("metrics", JSONObject(e.metricsJson))
            } catch (_: Exception) {
                eObj.put("metrics", e.metricsJson)
            }
            eventsArray.put(eObj)
        }
        root.put("detectorEvents", eventsArray)

        val samplesArray = JSONArray()
        for (s in samples) {
            val sObj = JSONObject()
            sObj.put("t_ns", s.timestampNs)
            sObj.put("elapsed_ms", s.elapsedMs)
            sObj.put("type", s.sensorType)
            sObj.put("x", s.x)
            sObj.put("y", s.y)
            sObj.put("z", s.z)
            sObj.put("mag", s.magnitude)
            sObj.put("dt_ms", s.intervalMs)
            sObj.put("sync_a", JSONArray(floatArrayOf(s.syncAx, s.syncAy, s.syncAz)))
            sObj.put("sync_a_mag", s.syncAccelMag)
            sObj.put("sync_g", JSONArray(floatArrayOf(s.syncGx, s.syncGy, s.syncGz)))
            sObj.put("sync_g_speed", s.syncGyroSpeed)
            sObj.put("orient", s.orientation)
            sObj.put("screen", s.screenInteractive)
            sObj.put("chop", s.chopState)
            sObj.put("twist", s.twistState)
            samplesArray.put(sObj)
        }
        root.put("samples", samplesArray)

        FileWriter(jsonFile).use { it.write(root.toString(2)) }

        Log.i(TAG, "Saved trace: ${jsonFile.absolutePath} (${samples.size} samples, ${events.size} events, ${durationMs}ms)")

        return TraceFileInfo(
            fileName = "$baseName.json",
            filePath = jsonFile.absolutePath,
            label = _currentLabel.value,
            sampleCount = samples.size,
            durationMs = durationMs,
            fileSizeBytes = jsonFile.length() + csvFile.length() + eventsFile.length(),
            timestampFormatted = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(recordingStartEpochMs))
        )
    }

    fun refreshSavedTraces() {
        val dir = File(context.getExternalFilesDir(null), "sensor_traces")
        if (!dir.exists()) {
            _savedTraces.value = emptyList()
            return
        }

        val jsonFiles = dir.listFiles { _, name -> name.endsWith(".json") } ?: emptyArray()
        val list = jsonFiles.sortedByDescending { it.lastModified() }.map { f ->
            val label = f.name.substringBeforeLast("_")
            TraceFileInfo(
                fileName = f.name,
                filePath = f.absolutePath,
                label = label,
                sampleCount = -1,
                durationMs = 0L,
                fileSizeBytes = f.length(),
                timestampFormatted = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault()).format(Date(f.lastModified()))
            )
        }
        _savedTraces.value = list
    }

    fun deleteTrace(fileName: String) {
        val dir = File(context.getExternalFilesDir(null), "sensor_traces")
        val baseName = fileName.removeSuffix(".json")
        File(dir, "$baseName.json").delete()
        File(dir, "$baseName.csv").delete()
        File(dir, "${baseName}_events.csv").delete()
        refreshSavedTraces()
    }
}
