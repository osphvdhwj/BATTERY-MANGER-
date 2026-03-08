package com.crdroid.batterywellbeing

object ModuleConfig {
    // 🔋 Battery & Hardware
    var enableSmartCharge: Boolean = true
    var smartChargeLimitPercent: Int = 80 // Configurable slider (e.g., 70% to 90%)
    var enableThermalWarnings: Boolean = true
    var thermalWarningThresholdC: Int = 42 // Slider (e.g., 38°C to 45°C)

    // 🛡️ Background & Storage
    var enableRogueApp: Boolean = true
    var enableStorageAbuse: Boolean = true
    var storageThresholdMB: Int = 500 // Slider for write limits
    var exemptedApps: Set<String> = emptySet() // Apps that are allowed to burn battery/storage (e.g., ArchiveAll)

    // ⏱️ Digital Wellbeing
    var enableAppTimers: Boolean = false
    var appTimeLimits: Map<String, Long> = emptyMap() // PackageName -> Max Milliseconds per day
    var enforceBedtimeMode: Boolean = false // Dims screen and kills media apps

    // 📡 Hotspot Control
    var enableHotspotLimits: Boolean = true
    var hotspotDataLimitMB: Int = 500 // Configurable per-connection limit
    var dropAggressiveClients: Boolean = true // Instantly kick MACs that spike bandwidth too fast
}
