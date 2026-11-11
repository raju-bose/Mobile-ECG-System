# PulseCare Android app

Native Android implementation intended for testing on the Vivo Y100.

## Features
- Rear-camera fingertip PPG pulse estimate using CameraX image luminance samples.
- Live waveform graph.
- Local SQLite history for pulse estimates and manually entered cuff BP.
- Explicit SpO2 unavailable notice: the phone camera alone is not treated as a validated oximeter.
- Experimental camera BP research session notes only. The app does **not** calculate or show BP from camera data.

## Important limitations
This is a prototype, not a medical device. Camera PPG pulse estimates can be inaccurate due to movement, pressure, lighting, skin perfusion, camera exposure and device processing. Do not use it to diagnose, change medication, or delay care. Use a validated upper-arm cuff for blood pressure and a validated pulse oximeter for SpO2. No camera-derived BP estimate is displayed.

## Build
Open this folder in Android Studio (Hedgehog or newer) with JDK 17 and Android SDK 35, then Sync and Build > Build APK(s).

Or run:
```bash
gradle :app:assembleDebug
```
APK output:
`app/build/outputs/apk/debug/app-debug.apk`

## Install on Vivo Y100
Copy the debug APK to the phone, open it from Files, allow installation from that source, then install. Open PulseCare and grant camera permission. Keep the rear camera lens clean, place a fingertip gently over the camera and flash, and avoid pressing hard.

## GitHub Actions
The repository workflow builds a debug APK and uploads it as a downloadable workflow artifact. Debug APKs are unsigned for Play Store distribution but can be installed directly after allowing the file source.
