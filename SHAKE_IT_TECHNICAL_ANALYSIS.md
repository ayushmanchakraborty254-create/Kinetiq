# Technical Analysis of "Shake It" (v2.4.2)
**Package:** `com.kalantos.utility.shake`  
**Target Device:** Nothing Phone (3a) (`00159359D000060`, Build `A059`, Android 15 / SDK 35/36)  
**Author:** Antigravity Senior Android & Platform Engineering Team  
**Date:** November 12, 2026  
**Status:** Completed Evidence-Based Reverse Engineering Report  

---

## Executive Summary

To inform the development and gesture reliability of **KINETIQ**, a complete forensic technical audit and reverse-engineering analysis was conducted on the production utility **Shake It** (`com.kalantos.utility.shake`, Version 2.4.2, versionCode 37), extracted directly from the connected Nothing Phone (3a).

### Primary Findings

1. **Hybrid Architecture:** The application combines a Flutter frontend (UI, configuration, advertising SDKs) with native Android Java components (`ShakeSensorService`, `h4.a`, `h4.d`) responsible for background sensor listening, motion classification, and action dispatch.
2. **Sub-Nyquist Sensor Sampling Rate:** Contrary to typical motion pipelines sampling at 50–200 Hz (`SENSOR_DELAY_GAME` or `FASTEST`), Shake It registers sensors using `SensorManager.SENSOR_DELAY_NORMAL` (200,000 µs = **5 Hz**). By sampling at only 5 Hz, the pipeline acts as an inherent physical low-pass filter, completely discarding high-frequency tremors, typing vibrations, and casual handling noise without computational overhead.
3. **Discrete Finite-Difference Formulas:** Shake detection operates on discrete coordinate deltas ($\Delta X, \Delta Y$) weighted $10:2$ according to device orientation, evaluated against an exponential sensitivity curve $T(s) = 1.7^{(15 - s)} + (620 - (28s + 270))$.
4. **Twist Recognition Math:** Wrist twists evaluate raw Y-axis angular velocity ($\omega_y$) against a linear threshold $\omega_{\text{thresh}}(s) = 13.0 - 0.9s$ [rad/s]. However, our decompilation revealed a critical algorithmic flaw: the implementation omits `Math.abs()`, detecting angular velocity in only **one rotational direction**.
5. **Background & Screen-Off Execution:** Background operation is sustained via a Foreground Service declared with `foregroundServiceType="specialUse"` under Android 14/15 rules, supplemented by an hourly `WorkManager` watchdog. Crucially, Shake It holds **zero wake locks** (`WakeLockRefCount 0`). When the device enters deep sleep, non-wakeup sensors cease reporting events; the app does not wake the SoC during deep suspend.
6. **Background Activity Launch (BAL):** Shake It bypasses Android 10+ background activity launch restrictions by demanding `android.permission.SYSTEM_ALERT_WINDOW` ("Display over other apps") and launching standard package intents with `FLAG_ACTIVITY_NEW_TASK`.
7. **Flawed State Synchronization & Dead Code:** Shake It does not implement `CameraManager.TorchCallback`, causing torch state desynchronization whenever the flashlight is modified externally (e.g., via Quick Settings). Furthermore, proximity sensor event handling (`Sensor.TYPE_PROXIMITY`) exists in code but is **never registered with SensorManager**, rendering pocket-protection completely inoperative in production.

---

## 1. System Architecture Overview

Shake It is architected as a Flutter client wrapping a background native Android service.

```
+-------------------------------------------------------------------------+
|                              FLUTTER UI                                 |
|  (Dart: Onboarding, Calibration Sliders, Action Selectors, Ad Mediation)|
+-------------------------------------------------------------------------+
                                     │
                     MethodChannel ("shake_service")
                                     │
                                     ▼
+-------------------------------------------------------------------------+
|                  NATIVE FLUTTER PLUGIN BRIDGE (m5.G)                    |
|       Writes settings to "FlutterSharedPreferences" (XML file)          |
+-------------------------------------------------------------------------+
                                     │
                      Direct SharedPref Key Reads
                                     │
                                     ▼
+-------------------------------------------------------------------------+
|                   NATIVE SERVICE (ShakeSensorService)                   |
|   - Foreground Service (specialUse)                                     |
|   - Registers Accelerometer (5 Hz) & Gyroscope (5 Hz)                   |
+-------------------------------------+-----------------------------------+
                                      │
                 ┌────────────────────┴───────────────────┐
                 ▼                                        ▼
   +---------------------------+            +---------------------------+
   |   Shake Detector (h4.a)   |            |   Twist Detector (h4.d)   |
   | - Orientation-weighted dx |            | - Longitudinal Gyro (w_y) |
   | - 100 ms time throttle    |            | - 150 ms time throttle    |
   | - 3-consecutive strikes   |            | - 3 strikes in 600 ms     |
   +-------------+-------------+            +-------------+-------------+
                 │                                        │
                 └────────────────────┬───────────────────┘
                                      │ Action Trigger
                                      ▼
             +-------------------------------------------------+
             |        Action Dispatcher (RunnableC3506v)       |
             |  - Flashlight Toggle (RunnableC3502q)           |
             |  - Background Activity Launch (BAL via Overlay) |
             +-------------------------------------------------+
```

### 1.1 Process & Persistence Model
- **Inter-Process Synchronization:** When settings (sensitivity, gesture enabled, selected action) change in Flutter, they are committed to `/data/data/com.kalantos.utility.shake/shared_prefs/FlutterSharedPreferences.xml`.
- **Native Readers:** The native service instances read directly from this shared preferences container using keys prefixed with `flutter.` (e.g., `flutter.shake_sensitivity_configuration`, `flutter.twist_gesture_enabled`, `flutter.shake_action`).
- **MethodChannel Dispatch:** The native bridge `m5.G` responds to four primary commands:
  - `startService`: Issues `startForegroundService` for `ShakeSensorService`.
  - `stopService`: Issues `stopService`.
  - `requestOverlayPermission`: Opens `Settings.ACTION_MANAGE_OVERLAY_PERMISSION` to obtain the BAL bypass.
  - `getCameraPackage`: Resolves the default camera application package name.

---

## 2. Sensor Configuration & Sampling Characteristics

### 2.1 Hardware Sensor Registrations
Direct telemetry extracted from the Nothing Phone (3a) hardware sensor manager (`dumpsys sensorservice`) confirms the live registration parameters:

```text
Connection Number: 6
  Operating Mode: NORMAL
  h4.d | WakeLockRefCount 0 | uid 10737 | cache size 0 | max cache size 0 | has sensor access: true
  lsm6dsv Gyroscope Non-wakeup 0x00000029 | samplingPeriod= 200000us | batchingPeriod= 0us

Connection Number: 9
  Operating Mode: NORMAL
  h4.a | WakeLockRefCount 0 | uid 10737 | cache size 0 | max cache size 0 | has sensor access: true
  lsm6dsv Accelerometer Non-wakeup 0x0000000b | samplingPeriod= 200000us | batchingPeriod= 0us
```

### 2.2 Key Configuration Parameters

| Parameter | Confirmed Value | Impact on Behavior |
| :--- | :--- | :--- |
| **Accelerometer Type** | `Sensor.TYPE_ACCELEROMETER` (1) | Reads combined gravity + dynamic acceleration. Non-wakeup variant. |
| **Gyroscope Type** | `Sensor.TYPE_GYROSCOPE` (4) | Reads raw angular velocity in radians/sec. Non-wakeup variant. |
| **Sampling Delay** | `SensorManager.SENSOR_DELAY_NORMAL` (3) | Delay interval of 200,000 µs (5 Hz). |
| **Batching Period** | `0 us` (Immediate Delivery) | No hardware FIFO batching is requested. |
| **Wake Lock Count** | `0` | No `PARTIAL_WAKE_LOCK` is requested or held during monitoring. |

### 2.3 The "Dead Proximity Code" Discovery
In `C3588a.java` and `d.java`, the following logic exists:
```java
if (sensorEvent.sensor.getType() == 8) { // TYPE_PROXIMITY
    if (shakeSensorService.getSharedPreferences("FlutterSharedPreferences", 0)
            .getBoolean("flutter.proximity_sensor_configuration", true) 
        && sensorEvent.values[0] == 0.0f) {
        z4 = true;
    }
    this.f39804f = z4; // Inhibits gesture detection when near
}
```
**Forensic Defect Identified:** Inspection of `ShakeSensorService.onCreate()` reveals:
```java
Sensor defaultSensor = sensorManager.getDefaultSensor(1);  // Accelerometer
Sensor defaultSensor2 = sensorManager.getDefaultSensor(4); // Gyroscope
this.f32559a.registerListener(this.f32561c, defaultSensor2, 3);
this.f32559a.registerListener(this.f32562d, defaultSensor, 3);
```
`SensorManager.registerListener()` is **never invoked for `Sensor.TYPE_PROXIMITY` (8)**. Furthermore, ADB `dumpsys sensorservice` confirms that UID `10737` has zero proximity sensor registrations. Consequently, **the proximity pocket guard in Shake It is completely dead code and non-functional in the field.**

---

## 3. Reverse-Engineered Gesture Recognition Algorithms

### 3.1 Shake / Chop Recognition (`h4.C3588a`)

#### Dynamic Coordinate Difference Calculation
The shake detector does not perform continuous high-frequency digital signal processing (e.g., Butterworth filtering or RMS integration). Instead, it evaluates discrete differences between sensor events throttled to a minimum interval:

```java
long currentTimeMillis = System.currentTimeMillis();
if (currentTimeMillis - this.f39800b >= 600 && currentTimeMillis - this.f39799a > 100) {
    this.f39799a = currentTimeMillis;
    float f4 = sensorEvent.values[0]; // X axis (lateral)
    float f7 = sensorEvent.values[1]; // Y axis (longitudinal)

    if (this.h.getResources().getConfiguration().orientation == 1) { // PORTRAIT
        abs = Math.abs(((f7 - this.f39802d) * 2.0f) + ((f4 - this.f39801c) * 10.0f));
    } else { // LANDSCAPE
        abs = Math.abs(((f4 - this.f39801c) * 2.0f) + ((f7 - this.f39802d) * 10.0f));
    }
    ...
    this.f39801c = f4; // Store last X
    this.f39802d = f7; // Store last Y
}
```

#### Mathematical Formulation
Let $(X_t, Y_t)$ be current accelerometer readings, and $(X_{t-1}, Y_{t-1})$ be the readings from the previous cycle $\ge 100\text{ ms}$ earlier:
$$\Delta X = X_t - X_{t-1}, \quad \Delta Y = Y_t - Y_{t-1}$$

In **Portrait Orientation**:
$$A_{\text{shake}} = \big| 2.0 \cdot \Delta Y + 10.0 \cdot \Delta X \big|$$

In **Landscape Orientation**:
$$A_{\text{shake}} = \big| 2.0 \cdot \Delta X + 10.0 \cdot \Delta Y \big|$$

**Key Mathematical Insights:**
1. **$10\times$ Lateral Weighting:** The lateral motion axis receives **500% higher weighting** than the longitudinal axis ($10.0$ vs $2.0$). 
2. **Complete Z-Axis Rejection:** The Z-axis (`values[2]`) is entirely discarded. Vertical bounces, walking impacts, or drops have zero direct mathematical weight.
3. **100 ms Windowing:** Sampling deltas at $\Delta t > 100\text{ ms}$ captures the gross displacement swing of a hand shake across a physical half-cycle ($\approx 100-250\text{ ms}$) rather than instantaneous jitter.

#### Dynamic Sensitivity Curve
The threshold $T(s)$ as a function of the user slider $s \in [1.0, 10.0]$ (stored as a string in `flutter.shake_sensitivity_configuration`, default `"7.0"`) is computed using:

$$T(s) = 1.7^{(15.0 - s)} + \Big(620.0 - \big(28.0 \cdot s + 270.0\big)\Big)$$

Evaluating across the valid range:

| Sensitivity Slider ($s$) | Exponential Term ($1.7^{15-s}$) | Linear Term ($350 - 28s$) | Threshold $T(s)$ | Physical Resistance |
| :---: | :---: | :---: | :---: | :---: |
| **1.0** (Least Sensitive) | $1.7^{14} \approx 1696.5$ | $322.0$ | **2018.5** | Extremely hard physical strike required |
| **3.0** | $1.7^{12} \approx 587.0$ | $266.0$ | **853.0** | Heavy deliberate shake |
| **5.0** | $1.7^{10} \approx 203.1$ | $210.0$ | **413.1** | Moderately strong shake |
| **7.0 (Default)** | $1.7^8 \approx 69.8$ | $154.0$ | **223.8** | Firm, intentional 2-3 stroke shake |
| **8.0** | $1.7^7 \approx 41.0$ | $126.0$ | **167.0** | Light shake |
| **10.0** (Most Sensitive) | $1.7^5 \approx 14.2$ | $70.0$ | **84.2** | Very light movement (prone to false positives) |

#### Three-Strike State Machine & Reset
```java
if (abs > threshold) {
    int i7 = this.f39803e + 1;
    this.f39803e = i7;
    if (i7 == 3) {
        this.f39803e = 0;
        Log.i("ShakeDetector", "Shake confirmed");
        // Dispatch Action
        this.f39800b = System.currentTimeMillis(); // 600 ms lockout
    }
} else {
    this.f39803e = 0; // Immediate reset if any interval falls below threshold!
}
```
**Strike Constraint:** To trigger, the user must exceed $T(s)$ for **3 consecutive sampled intervals** of $\ge 100\text{ ms}$. If even a single sample drops below $T(s)$, the counter immediately resets to 0. A valid shake gesture thus requires sustained movement for at least $3 \times 100\text{ ms} = 300\text{ ms}$.

---

### 3.2 Twist Recognition (`h4.d`)

#### Monitored Axis & Throttling
The twist detector monitors angular velocity exclusively on the device's longitudinal spine (Y-axis):
```java
float f4 = sensorEvent.values[1]; // Gyroscope Y-axis in rad/s
long currentTimeMillis = System.currentTimeMillis();

if (currentTimeMillis - this.f39814d >= 600) { // Cooldown lockout
    if (currentTimeMillis - this.f39813c > 150) { // 150 ms sample interval
        this.f39813c = currentTimeMillis;
        double parseDouble = Double.parseDouble(
            shakeSensorService.getSharedPreferences("FlutterSharedPreferences", 0)
                .getString("flutter.shake_sensitivity_configuration", "7.0")
        );
        if (f4 > 13.0d - (parseDouble * 0.9d)) {
            int i7 = this.f39812b + 1;
            this.f39812b = i7;
            this.f39811a = true;
            if (i7 == 3) {
                // Dispatch Twist Action
                this.f39811a = false;
                this.f39812b = 0;
                this.f39814d = System.currentTimeMillis(); // 600 ms lockout
            }
        }
    }
}
```

#### Angular Velocity Threshold Formula
$$\omega_{\text{thresh}}(s) = 13.0 - (0.9 \cdot s)\quad [\text{rad/s}]$$

| Sensitivity Slider ($s$) | Threshold (rad/s) | Threshold (deg/s) | Physical Description |
| :---: | :---: | :---: | :---: |
| **1.0** (Least Sensitive) | $12.1\text{ rad/s}$ | $\approx 693.3^\circ/\text{s}$ | Violent wrist snap |
| **5.0** | $8.5\text{ rad/s}$ | $\approx 487.0^\circ/\text{s}$ | Very rapid twist |
| **7.0 (Default)** | **$6.7\text{ rad/s}$** | **$\approx 383.9^\circ/\text{s}$** | Firm, rapid wrist twist |
| **10.0** (Most Sensitive) | $4.0\text{ rad/s}$ | $\approx 229.2^\circ/\text{s}$ | Moderate wrist turn |

#### Major Algorithmic Flaws in Twist Detection
1. **Unidirectional Velocity Check:** Notice the check `if (f4 > 13.0d - ...)`. There is no `Math.abs(f4)`. On Android, `Sensor.TYPE_GYROSCOPE` Y-axis velocity is positive for rotation in one direction and negative in the other. **Shake It only detects rotations in the positive direction.** When a user performs a natural back-and-forth wrist twist (flick right, flick left, flick right), only the rightward flicks register as strikes.
2. **Missing Angular Displacement Integration:** Shake It never integrates $\int \omega_y \, dt$ to estimate physical rotation angle. It only counts instantaneous velocity peaks spaced by $> 150\text{ ms}$.

---

## 4. Background Monitoring, Execution & Persistence

### 4.1 Foreground Service & Android 14/15 Compatibility
In `AndroidManifest.xml`:
```xml
<service
    android:name="com.kalantos.utility.shake.ShakeSensorService"
    android:exported="false"
    android:foregroundServiceType="specialUse">
    <property
        android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
        android:value="The app needs to run a foreground service to listen the sensors to detect shakes by user to open apps." />
</service>
```
Under Android 14 (API 34) and Android 15 (API 35/36), Google mandates explicit `foregroundServiceType` declarations. Shake It uses `specialUse` (flag `0x40000000`) with an explicit subtype property explaining sensor listening.

### 4.2 Notification Implementation
In `ShakeSensorService.onCreate()`:
```java
NotificationChannel b4 = new NotificationChannel(
    "com.kalantos.utility.shake.service",
    "Shake Service",
    NotificationManager.IMPORTANCE_LOW
);
b4.setDescription("Channel for shake detection service");
notificationManager.createNotificationChannel(b4);

NotificationCompat.Builder builder = new NotificationCompat.Builder(this, "com.kalantos.utility.shake.service")
    .setContentTitle("Shake it!")
    .setContentText("Detecting device movements")
    .setSmallIcon(R.drawable.launcher_icon);

startForeground(1, builder.build());
```
Using `IMPORTANCE_LOW` displays the persistent notification silently without audio or pop-up interruption.

### 4.3 Background Activity Launch (BAL) Bypass
Android 10+ strictly restricts launching activities from the background (`startActivity` from a Service is silently dropped or queued). Shake It circumvents this restriction:
1. In `AndroidManifest.xml`, it requests:
   ```xml
   <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
   ```
2. When the user selects an app-launching action in Flutter, the app redirects the user to Android's "Display over other apps" settings.
3. In Android's security architecture, having the `SYSTEM_ALERT_WINDOW` permission grants the application an exemption from Background Activity Launch (BAL) restrictions.
4. In `RunnableC3506v.java`:
   ```java
   Intent launchIntent = shakeSensorService.getPackageManager().getLaunchIntentForPackage(str);
   if (launchIntent != null) {
       launchIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // 268435456
       shakeSensorService.startActivity(launchIntent);
       shakeSensorService.b(); // Trigger 350 ms vibration feedback
   }
   ```

### 4.4 The WorkManager Watchdog
OEM battery managers (such as Nothing OS, MIUI, ColorOS) aggressively kill background foreground services during memory pressure. To counteract this, Shake It implements a recurring watchdog:

1. `BootCompletedReceiver.java` intercepts `android.intent.action.BOOT_COMPLETED`.
2. It enqueues a periodic WorkManager task:
   ```java
   PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(
       ShakeServiceWorker.class, 
       1, TimeUnit.HOURS
   ).addTag("shake_worker_periodic").build();
   WorkManager.getInstance(context).enqueueUniquePeriodicWork(
       "ShakeSensorWorkerHourly", 
       ExistingPeriodicWorkPolicy.KEEP, 
       workRequest
   );
   ```
3. In `ShakeServiceWorker.doWork()`:
   ```java
   ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
   for (ActivityManager.RunningServiceInfo service : am.getRunningServices(Integer.MAX_VALUE)) {
       if (ShakeSensorService.class.getName().equals(service.service.getClassName())) {
           return Result.success(); // Already active
       }
   }
   // If dead, revive:
   Intent intent = new Intent(context, ShakeSensorService.class);
   ContextCompat.startForegroundService(context, intent);
   return Result.success();
   ```

### 4.5 Battery Management & Screen-Off Reality
- **Zero Wake Locks:** Shake It holds **no `PowerManager.WakeLock`**.
- **No Hardware Sensor Batching:** Non-wakeup sensors are registered with `batchingPeriod = 0`.
- **Screen-Off Behavior on Android:**
  - While the screen is ON or in an interactive state, sensor delivery continues uninterrupted at 5 Hz.
  - When the screen turns OFF, the device eventually enters Linux kernel deep sleep (suspend). Because `lsm6dsv Accelerometer Non-wakeup` and `lsm6dsv Gyroscope Non-wakeup` are non-wakeup sensors, **the sensor hub halts event delivery to the AP (Application Processor) during suspend**.
  - As observed during device testing, gestures trigger for a short time after the screen turns off (while Android handles display power-down and audio/network tasks), but once the device enters deep sleep, gestures cease responding until the device is picked up or unlocked.

---

## 5. Comparative Analysis: Shake It vs. KINETIQ

| Architectural Dimension | "Shake It" (v2.4.2) | KINETIQ (Current Architecture) |
| :--- | :--- | :--- |
| **Primary Shake Sensor** | `Sensor.TYPE_ACCELEROMETER` (Raw, includes gravity) | `Sensor.TYPE_LINEAR_ACCELERATION` (Gravity-isolated) |
| **Primary Twist Sensor** | `Sensor.TYPE_GYROSCOPE` (Raw angular velocity $\omega$) | `Sensor.TYPE_GYROSCOPE` (Integrated angular displacement $\theta$) |
| **Sensor Sampling Rate** | **5 Hz** (`SENSOR_DELAY_NORMAL`, 200 ms) | **50–100 Hz** (`SENSOR_DELAY_GAME` or `FASTEST`) |
| **Shake Algorithm** | Discrete coordinate deltas $\Delta X, \Delta Y$ weighted $10:2$ over 100 ms | Peak-to-peak acceleration waveform analysis |
| **Twist Algorithm** | Velocity threshold $\omega_y > 6.7\text{ rad/s}$ ($> 150\text{ ms}$) | Trapezoidal numerical integration of angular displacement |
| **Directional Symmetry** | **Defective:** Checks only positive $\omega_y$ (`f4 > thresh`) | **Symmetric:** Uses absolute velocity or bidirectional reversal detection |
| **Flashlight State Sync** | **Defective:** Blindly assumes `cameraIdList[0]` and reads/writes local boolean in SharedPrefs without callback | **Robust:** Uses `CameraManager.TorchCallback` to listen to external hardware state changes and camera availability |
| **Pocket Protection** | **Broken:** Proximity code exists but is never registered with SensorManager | Integrated proximity sensor listener with hysteresis |
| **Background Launch (BAL)** | Requires high-risk `SYSTEM_ALERT_WINDOW` permission | Uses standard notification pending intents and full-screen intents |
| **Foreground Service Type** | `specialUse` (Permitted on Play Store with policy scrutiny) | Standard foreground service with configurable notification |

### Why Shake It Feels "Reliable" to Users
1. **Low Event Frequency (5 Hz):** By only receiving sensor events every 200 ms, Shake It is completely immune to high-frequency hand tremors, table vibrations, or walking bounces.
2. **Heavy Lateral Bias ($10\times$):** Discarding the Z-axis and weighting the X-axis by $10.0$ matches the exact biomechanics of a side-to-side wrist shake.
3. **Low Cognitive Threshold:** Because it only requires 3 consecutive threshold crossings of 100 ms each, any vigorous shake lasting $\approx 350\text{ ms}$ reliably triggers the action.

### Where Shake It Cuts Corners
1. **Flashlight Out-of-Sync:** Toggling the flashlight from the Nothing OS Quick Settings completely desynchronizes Shake It's state. The next shake sends `setTorchMode(!flash_state)` which fails or does the opposite of what the user expects.
2. **Camera Index Assumption:** Calling `cameraManager.getCameraIdList()[0]` fails on multi-camera devices where camera 0 is an ultrawide or macro sensor lacking flash capabilities.
3. **Dead Proximity Sensor:** Because proximity is never registered, shaking the phone while in a pocket or bag can inadvertently trigger the flashlight or open apps.

---

## 6. Actionable Implementation Takeaways for KINETIQ

From this technical analysis, we can extract several engineering principles to incorporate into KINETIQ's gesture recognition pipeline **without copying proprietary code**:

### 1. Incorporate Half-Cycle Temporal Throttling ($\approx 100-150\text{ ms}$)
In high-frequency sensor pipelines (100 Hz), a single physical arm swing produces dozens of individual sensor events. If a state machine counts "peaks" without enforcing a minimum physical separation, a single vigorous chop or twist can inadvertently satisfy multiple strike counters within 30 milliseconds.
* **Recommendation:** Enforce a strict minimum separation window between detected peak strokes (e.g., $120\text{ ms} \le \Delta t_{\text{stroke}} \le 500\text{ ms}$), reflecting the physical biomechanics of human arm oscillation.

### 2. Orientation-Aware Axis Weighting for Chop / Shake
Shake It's technique of applying an orientation-dependent weight multiplier ($10.0$ on the lateral axis in Portrait, $10.0$ on the longitudinal axis in Landscape) and discarding the Z-axis is mathematically effective at eliminating vertical noise (such as walking or jogging).
* **Recommendation:** In KINETIQ's double-chop detector, apply orientation-aware weighting to the linear acceleration vector components rather than relying solely on raw magnitude $M = \sqrt{x^2 + y^2 + z^2}$, which is susceptible to vertical drops and walking shocks.

### 3. Maintain Symmetric Bidirectional Twist Detection
Shake It's failure to use `Math.abs()` on gyroscope readings illustrates why directional awareness is vital. A natural wrist twist consists of an outward snap followed by an inward rebound.
* **Recommendation:** KINETIQ should continue evaluating angular displacement with absolute or bidirectional sign-alternating criteria ($\omega > +\omega_{\text{thresh}}$ followed by $\omega < -\omega_{\text{thresh}}$), ensuring seamless operation for both left-handed and right-handed users.

### 4. Retain KINETIQ's Superior Hardware State Management
KINETIQ's implementation of `CameraManager.TorchCallback` and dynamic camera ID enumeration with `FLASH_INFO_AVAILABLE` is architecturally superior to Shake It's hardcoded `getCameraIdList()[0]` and local preference state.
* **Recommendation:** Retain KINETIQ's reactive torch callback architecture.

### 5. Proper Proximity Pocket Protection
Unlike Shake It's dead proximity code, KINETIQ should register a dedicated `Sensor.TYPE_PROXIMITY` listener with wake-up capability (or standard proximity with proper threshold hysteresis $\text{distance} < 1.0\text{ cm}$) to reliably gate gestures while the device is inside a pocket or resting face down.

---

## 7. Limitations & Unknowns

1. **Screen-Off Deep Suspend Gating:** On stock Android and Nothing OS, non-wakeup sensors (`lsm6dsv Non-wakeup`) stop streaming once the AP enters suspend. Supporting gesture recognition while the screen is completely off over extended periods requires either:
   - Holding a `PARTIAL_WAKE_LOCK` (severe battery drain, rejected by Google Play if unneeded).
   - Utilizing hardware wake-up sensors (`Sensor.TYPE_SIGNIFICANT_MOTION` or OEM-specific wake-up gestures) which are integrated into the sensor hub.
2. **Google Play Policy on `specialUse` Foreground Services:** Shake It currently targets API 36 with `specialUse`. Google Play requires apps using `specialUse` to provide detailed video documentation justifying why no standard foreground service type (`dataSync`, `mediaPlayback`, `location`) is applicable.
3. **Background Activity Launch Restrictions on Android 14/15:** Shake It requires the user to manually grant `SYSTEM_ALERT_WINDOW` to launch apps from the background. For a utility targeting modern Play Store policies, launching camera intents via `PendingIntent` notifications or full-screen intents with `USE_FULL_SCREEN_INTENT` permission provides a safer, more compliant user experience.

---
*Report compiled based on verified decompiled bytecode from JADX v1.5.0 and live runtime telemetry from Nothing Phone (3a).*

