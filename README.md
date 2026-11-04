<p align="center">
  <img src="assets/icon.svg" width="96" height="96" alt="NATURA App Icon" />
</p>

<h1 align="center">NATURA</h1>

<p align="center">
  <strong>Capture Reality. Not Algorithms.</strong>
</p>

<p align="center">
  <a href="https://github.com/pabitrasenpai/natura/actions/workflows/build-apk.yml">
    <img src="https://github.com/pabitrasenpai/natura/actions/workflows/build-apk.yml/badge.svg" alt="Build Status" />
  </a>
  &nbsp;
  <a href="https://github.com/pabitrasenpai/natura/releases/download/latest/NATURA.apk">
    <img src="https://img.shields.io/badge/Download-APK-E9B44C?style=for-the-badge&logo=android&logoColor=black" alt="Download APK" />
  </a>
</p>

---

NATURA is a native Android camera application engineered from first principles around a core philosophy: **never apply beauty filters, artificial skin smoothing, saturation boosts, synthetic HDR, or generative AI processing.**

Using Android's low-level **Camera2 API**, NATURA queries the physical camera hardware pipeline for the least processing each device supports, honoring the optical truth of the sensor and lens.

---

## Direct Download & Installation

1. Tap the **[Download APK](https://github.com/pabitrasenpai/natura/releases/download/latest/NATURA.apk)** badge above on your Android device (Android 8.0+).
2. When prompted by Android, allow **"Install unknown apps"** for your web browser or file manager (in *Settings > Apps > Special app access > Install unknown apps*).
3. Open the downloaded `NATURA.apk` file and tap **Install**.
4. Launch NATURA and grant the camera and microphone permissions to capture natural, unadulterated reality.

---

## Key Features

- **Direct Camera2 Pipeline**:
  - Configures the ISP for minimal noise reduction (`NOISE_REDUCTION_MODE_MINIMAL` or `OFF`).
  - Disables artificial edge sharpening halos (`EDGE_MODE_OFF`).
  - Standard neutral tone curves (`TONEMAP_MODE_PRESET_CURVE` sRGB) and faithful aberration rendering.
  - Enforces all face modification, scene modes, and synthetic effects strictly off.
- **True RAW (DNG) + JPEG Capture**:
  - Leverages Android `DngCreator` with full ISP sensor metadata, color matrix, and illumination calibration.
  - Single synchronized capture request targeting both `JPEG` and `RAW_SENSOR` streams.
  - Scoped storage organization into `Pictures/NATURA` and `Movies/NATURA`.
- **Pro Exposure Manual Controls**:
  - Tabular readouts with real-time exposure feedback (ISO, Shutter speed 1/X, EV bias, Kelvin color temperature).
  - Canvas-rendered manual ruler slider with tick marks every 14dp, fixed amber needle, and momentum fling.
  - Manual ISO, manual shutter speed, EV compensation, white balance Kelvin adjustment (2500K - 9000K), and manual lens focus distance.
  - AUTO reset, AE/AF Lock, and RAW+JPG instant chip toggles.
  - Live 64-bin luminance histogram sampled directly from the viewfinder stream.
- **Viewfinder & Sensor Telemetry**:
  - Full-width viewfinder with rule-of-thirds grid.
  - Gravity horizon level with two amber indicators driven by `SensorManager` rotation vectors, snapping solid amber within 1.0 degree.
  - Multi-camera hardware zoom buttons (.5x, 1x, 2x, 5x) determined by optical lens availability, with pinch-to-zoom.
  - Responsive tap-to-focus and long-press AE/AF metering rectangle calculation.
- **Hardware Transparency**:
  - Dedicated "Natural Processing" settings screen inspecting device hardware level (`LIMITED`, `FULL`, `LEVEL_3`), supported noise reduction, tone map curves, and sensor capabilities.
  - Dimmed unavailable modes with explanatory hints: *"Not supported on this device"*.
- **In-App Gallery Viewer**:
  - Swipeable full-screen viewer for photos and videos in `Pictures/NATURA`.
  - Pinch-to-zoom, system sharing via `Intent.ACTION_SEND`, and scoped MediaStore deletion.
  - Live format badge indicator (`JPG`, `JPG + DNG`, `MP4`).

---

## Design System

- **Background**: Pure AMOLED Black (`#000000`).
- **Surfaces**: `#141414` and `#161616`.
- **Typography**: Manrope (400 Regular, 600 SemiBold, 800 ExtraBold) with tabular figures (`tnum`) for camera telemetry.
- **Accent**: Amber (`#E9B44C`) used exclusively for active and selected states.
- **System**: Edge-to-edge UI with black system bars and light status icons.

---

## Getting Started

### Prerequisites

- **Android Studio** Ladybug (2024.2.1+) or newer.
- **Android SDK**: `compileSdk 35`, `minSdk 26` (Android 8.0 Oreo or higher).
- **JDK**: Java 17.

### Opening in Android Studio

1. Open Android Studio.
2. Select **File > Open...** and navigate to the root directory of this repository.
3. Allow Gradle to synchronize the project and download required dependencies.

### Building the Debug APK

Run the Gradle assembleDebug task from the terminal:

```bash
./gradlew assembleDebug
```

The compiled APK will be generated at:
```
app/build/outputs/apk/debug/app-debug.apk
```

### Installing the APK onto a Device via ADB

Connect your Android phone via USB (with USB Debugging enabled in Developer Options) and run:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Alternatively, launch directly from Android Studio by selecting `app` and clicking the **Run (Play)** button.
