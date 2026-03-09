# 🔋 Battery Wellbeing (Dynamic Island Edition)

[![Android](https://img.shields.io/badge/Android-15%20(API%2035)-3DDC84?style=for-the-badge&logo=android)](#)
[![Framework](https://img.shields.io/badge/LSPosed%20%2F%20Zygisk-Required-blue?style=for-the-badge)](#)
[![UI](https://img.shields.io/badge/Jetpack%20Compose-Material%203%20Glassmorphism-purple?style=for-the-badge)](#)

**Battery Wellbeing** is an elite, open-source Xposed/Zygisk module designed exclusively for custom ROM ecosystems (specifically tested on CrDroid / Android 15). 

Unlike standard OEM apps that rely on software overlays, this module hooks directly into the `system_server` kernel space to enforce ironclad digital wellbeing rules, manipulate hardware power states, and synchronize telemetry with a SystemUI Dynamic Island.

---

## 📸 The AMOLED Glassmorphism UI
Designed specifically for devices like the Poco X5 Pro (Snapdragon 778G), the Jetpack Compose frontend completely disables standard Light/Dark themes. It utilizes a **True Black AMOLED** container with translucent, blurred glass panels and neon cyan `Canvas` elements. It doesn't just look like a hacker dashboard; it physically turns off your AMOLED pixels to save battery while you monitor your usage.

---

## ⚡ Core Features (The 4 Pillars)

### I. Kernel & Hardware Dominance
* **The System Executioner:** Hooks `ActivityManagerService.forceStopPackage` to violently terminate apps the millisecond their daily timer expires, completely stopping phantom background drains.
* **Per-Connection Hotspot Limits:** Bypasses legacy shell loops. Tracks connected MAC addresses via the Android 15 Tethering APEX and physically kicks data-abusing clients off your network using native `SoftApConfiguration` IEEE 802.11 Deauthentication frames.
* **Dynamic Refresh Rate Override:** Hooks `PhoneWindow.generateLayout` to intercept app initialization, forcefully dropping heavy scrolling apps (like TikTok/Instagram) to 60Hz, while preserving 120Hz for whitelisted games (like Free Fire Max).
* **Rogue App & Storage Abuse Monitors:** Directly intercepts `BatteryService` to monitor localized I/O read/write abuses and rogue CPU spin-ups.

### II. Intentional Friction (Psychological UX)
* **The "Deep Breath" Interceptor:** Hooks `ActivityTaskManagerService`. When you tap a restricted app on your launcher, the OS intercepts the intent, freezes the launch, and drops you into a transparent 5-second expanding circle animation. It stops doom-scrolling before it starts.
* **Android 15 `ZenDeviceEffects`:** Utilizes the new AOSP APIs to bypass root overlays and natively shift the physical hardware display into Grayscale, dim the wallpaper, and kill the Always-On Display during Bedtime Mode.

### III. SystemUI Dynamic Island Integration
* **Zero-Latency IPC Pipeline:** Android 15 severely restricts implicit broadcasts. We utilize a targeted `IslandDispatcher` to fire exact intent payloads directly to `com.dynamicisland.systemui`.
* **The 60-Second Grace Period:** Before the Executioner kills an app, the module fires a `WARNING_1_MINUTE_REMAINING` payload. The Dynamic Island violently springs open in a glowing red UI to display a live 60-second countdown.
* **The Reality Pill:** A persistent, translucent overlay that anchors around the camera cutout during heavy gaming sessions, silently ticking up your active session time.

### IV. Unbreakable Analytics & Backup
* **Omniscience Engine:** Utilizes the `QUERY_ALL_PACKAGES` permission and a persistent `HeartbeatTrackerService` to completely bypass the Android 15 `AppsFilter` visibility sandbox and prevent memory manager (OOM) background kills.
* **Room Database & Notification Tracker:** Stores granular app history (Screen Time, Device Unlocks, Notification Counts) permanently via SQLite, surviving Android's native 7-day `UsageStats` wipe.
* **Custom ROM Backup:** Built for flashaholics. Exports all SQLite databases and SharedPreferences to `/sdcard/WellbeingBackup/` so your timers and history survive a clean ROM flash.

---

## 🛠️ For Users: Installation Guide

### Prerequisites
1. **Root Access:** Magisk or KernelSU installed.
2. **LSPosed Framework:** Zygisk version 1.9.3+ (JingMatrix fork recommended for Android 15).
3. **Android 15 (API 35):** Tested extensively on CrDroid 11.x.

### Steps
1. Download the latest `app-debug.apk` from the [GitHub Actions Releases](#) tab. (Note: Releases are heavily optimized via R8 minification).
2. Install the APK on your device.
3. Open the **LSPosed Manager**, enable the "Battery Wellbeing" module, and ensure the **System Framework** (`android`), **Settings**, and **Tethering** scopes are checked.
4. Reboot your device.
5. **Critical Permissions:** Open the app and grant:
   * Usage Access (for Screen Time).
   * Display Over Other Apps (for the Interceptor shield).
   * Notification Access (for tracking notification spam).

---

## 💻 For Developers: Architecture & Build

### Bypassing Custom ROM Signatures
Custom ROMs frequently alter AOSP function signatures. To prevent `NoSuchMethodError` crashes, our core `system_server` hooks (like `systemReady`) use `XposedBridge.hookAllMethods` as a catch-all safety net.

### The CI/CD Pipeline
This repository uses GitHub Actions for Continuous Integration. The `build.gradle.kts` is heavily tuned for micro-APK sizes on Snapdragon devices:
* ABI Filtering: `arm64-v8a` strictly.
* Language Stripping: `en` only.
* **Note:** R8 Minification is currently disabled on the Debug build to prevent Jetpack Compose animation keyframes (`CircularProgressIndicator`) from being aggressively stripped, which causes UI crashes.

### Building Locally
```bash
git clone [https://github.com/yourusername/Battery-Wellbeing.git](https://github.com/yourusername/Battery-Wellbeing.git)
cd Battery-Wellbeing
./gradlew assembleDebug
