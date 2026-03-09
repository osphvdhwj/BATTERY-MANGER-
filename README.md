# 🔋 Battery Wellbeing (Dynamic Island Edition)

Battery Wellbeing is an elite, open-source Xposed/Zygisk module designed exclusively for custom ROM ecosystems (specifically tested on CrDroid / Android 15).

Unlike standard OEM apps that rely on passive timers and basic software overlays, this module hooks directly into the Android `system_server` kernel space. It enforces ironclad digital wellbeing rules, manipulates hardware power states, shapes network traffic, and synchronizes real-time telemetry with a custom SystemUI Dynamic Island.

## 🚀 The Philosophy

Standard wellbeing apps politely ask you to put your phone down. Battery Wellbeing physically prevents you from picking it up. By bypassing Android 15's AppsFilter sandbox, this module establishes absolute authority over the OS—suspending processes, capping refresh rates, restricting hotspot client data, and deploying intentional psychological friction to break doom-scrolling habits.

## 📸 The AMOLED Glassmorphism UI

Designed specifically for devices with AMOLED panels (like the Poco X5 Pro / Snapdragon 778G), the Jetpack Compose frontend completely abandons standard Light/Dark themes.

It utilizes a True Black AMOLED container (`#000000`) with translucent, blurred glass panels (`Color(0xFF1E1E1E)` at 60% opacity) and neon cyan Canvas elements. It doesn't just look like a futuristic hacker dashboard; it physically turns off your AMOLED pixels to save battery while you monitor your system telemetry.

## ⚡ Core Features (The 4 Pillars)

### I. Psychological Intentional Friction
*   **The "Deep Breath" Interceptor**: Hooks `ActivityTaskManagerService.startActivityAsUser`. If you launch a restricted app, the OS intercepts the intent, freezes the launch, and projects a 5-second expanding glassmorphism circle, forcing you to wait and reflect before the app opens.
*   **The Executioner (Strict App Timers)**: Uses `forceStopPackage` via the `IActivityManager` root shell to violently terminate an app the millisecond its daily quota is reached, completely stopping phantom background drains.

### II. Hardware & Battery Dominance
*   **Per-App 60Hz Override**: Hooks `WindowManager LayoutParams` to forcefully cap the display refresh rate of heavy social media apps to 60Hz, while leaving whitelisted games running at a full 120Hz.
*   **Android 15 Native ZenDeviceEffects**: Utilizes the new API 35 Bedtime Mode to natively shift the physical hardware to grayscale, dim the wallpaper, and suppress the Always-On Display without using power-draining software overlays.
*   **Storage I/O & Rogue App Monitoring**: Directly intercepts `BatteryService` to monitor localized I/O read/write abuses and rogue CPU spin-ups, exempting whitelisted apps (like custom Archivers).

### III. Tethering Quotas
*   **Per-Connection Hotspot Limit**: Hooks the Android 15 Mainline Tethering APEX (`com.google.android.tethering`).
*   **The IEEE 802.11 Kick**: Bypasses legacy shell loops. Uses reflection on `SoftApConfiguration` to inject rogue MAC addresses into the hostapd blocklist, instantly severing the radio link of hotspot clients that exceed your defined MB limit.

### IV. The Dynamic Island Bridge & Analytics
*   **Zero-Latency IPC Pipeline**: Android 15 severely restricts implicit broadcasts. We utilize a targeted `IslandDispatcher` to fire exact intent payloads (Thermals, Storage warnings, Reality Pill ticks) directly to `com.dynamicisland.systemui`.
*   **The 60-Second Grace Period**: A `SYSTEM_ALERT_WINDOW` overlay warns you exactly 1 minute before the Executioner drops the hammer. The Dynamic Island violently springs open in a glowing red UI to display a live 60-second countdown.
*   **The Reality Pill**: A persistent, non-swipeable floating pill overlay injected near the camera cutout that ticks up your live session time during heavy usage.
*   **Omniscience & Backup Engine**: Utilizes the `QUERY_ALL_PACKAGES` permission and a persistent `HeartbeatTrackerService` to bypass the AppsFilter visibility sandbox. Backed by an internal SQLite Room Database for 7-Day historical analytics, with export/restore capabilities to `/sdcard/WellbeingBackup/` for custom ROM flashaholics.

## 🛠️ Technical Architecture

This ecosystem operates on a 3-tier architecture:

1.  **The Backend (system_server Hooks)**: Built in Kotlin using the Xposed API.
2.  **The Frontend (Jetpack Compose)**: A True AMOLED Black UI utilizing custom Canvas drawing for neon progress rings.
3.  **The Heartbeat Tracker**: A persistent Foreground Service that prevents Android's memory manager (OOM) from killing the usage tracker.

## 📦 Installation & Setup (For Users)

### Prerequisites
*   **OS**: Android 15 (Tested on CrDroid).
*   **Root**: Magisk v27+ or KernelSU.
*   **Framework**: You must use an Android 15 compatible LSPosed fork (e.g., JingMatrix LSPosed v1.11.0+ or Zygisk-Next). The legacy LSPosed repository is abandoned and will bootloop Android 15.

### Instructions
1.  Download the `app-debug.apk` from the Releases tab.
2.  Install the APK normally.
3.  Open LSPosed Manager, navigate to Modules, and enable **Battery Wellbeing**.
4.  Check the **System Framework** scope (Required for system_server hooks).
5.  Reboot your device.
6.  Open the App. You will be prompted to grant three critical permissions:
    *   **Usage Access** (For Screen Time tracking)
    *   **Display Over Other Apps** (For the Interstitial Shield and Reality Pill)
    *   **Notification Access** (For the Notification Counter DB and ZenDeviceEffects)

## 💻 Developer Guide: Building from Source

To compile this module yourself, ensure you are using JDK 17 and Android Studio Ladybug (or newer) to support API 35.

### Bypassing Custom ROM Signatures
Custom ROMs frequently alter AOSP function signatures. To prevent `NoSuchMethodError` crashes, our core `system_server` hooks (like `systemReady`) use `XposedBridge.hookAllMethods` as a catch-all safety net.

### The CI/CD Pipeline
This project is configured with a YAML pipeline to compile on GitHub Actions. The `build.gradle.kts` is heavily tuned for micro-APK sizes on Snapdragon devices:

*   **ABI Filtering**: `arm64-v8a` strictly.
*   **Language Stripping**: `en` only.

*Note on Maven Servers: If you encounter a 502 Bad Gateway error on the `androidx.room:room-compiler` dependency during the GitHub Action run, simply re-run the job (this is a known Google server timeout issue).*

*Note on R8 Minification: `isMinifyEnabled` is currently set to `false` for the debug build to protect Jetpack Compose animation keyframes (CircularProgressIndicator) from being aggressively stripped, which causes UI crashes.*

## ⚠️ Known Limitations & Disclaimers

*   **Custom ROM Volatility**: This module hooks deep internal AOSP classes. If your ROM maintainer heavily modifies `ActivityManagerService` or the `PhantomProcessList`, the device may soft-reboot.
*   **AppsFilter Sandboxing**: If the UI fails to load app icons, ensure the `QUERY_ALL_PACKAGES` permission has not been revoked by the Android Privacy Sandbox.

**Disclaimer**: This is a root-level system modification. The developer is not responsible for missed alarms, lost app data due to the Executioner hook, or bootloops. Use at your own risk.
