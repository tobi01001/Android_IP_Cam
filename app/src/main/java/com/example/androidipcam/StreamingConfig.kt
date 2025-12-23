package com.example.androidipcam

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration manager for IP_Cam application settings.
 * 
 * REQ-PER-008: Persist all settings to SharedPreferences immediately
 * REQ-PER-009: Restore settings on startup
 * 
 * Handles persistence and retrieval of all configurable parameters using
 * SharedPreferences. All settings changes are persisted immediately (not batched)
 * to ensure settings survive:
 * - Service crashes or system kills
 * - App force-close or swipe away
 * - Device reboots or power loss
 * - System-initiated service restarts
 * 
 * Settings are restored in CameraService.onCreate() before any camera operations.
 */
data class StreamingConfig(
    val cameraType: String = "back",
    val rotation: String = "auto",
    val resolution: String? = null,
    val serverPort: Int = 8080,
    val jpegQuality: Int = 80,
    val targetFps: Int = 10,
    val flashlightEnabled: Boolean = false,
    val keepScreenOn: Boolean = false,
    val autoStart: Boolean = false,
    val maxConnections: Int = 32
) {
    companion object {
        private const val PREFS_NAME = "android_ipcam_settings"
        private const val KEY_CAMERA_TYPE = "camera_type"
        private const val KEY_ROTATION = "rotation"
        private const val KEY_RESOLUTION = "resolution"
        private const val KEY_SERVER_PORT = "server_port"
        private const val KEY_JPEG_QUALITY = "jpeg_quality"
        private const val KEY_TARGET_FPS = "target_fps"
        private const val KEY_FLASHLIGHT = "flashlight_enabled"
        private const val KEY_KEEP_SCREEN_ON = "keep_screen_on"
        private const val KEY_AUTO_START = "auto_start"
        private const val KEY_MAX_CONNECTIONS = "max_connections"

        /**
         * Load configuration from SharedPreferences
         */
        fun load(context: Context): StreamingConfig {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return StreamingConfig(
                cameraType = prefs.getString(KEY_CAMERA_TYPE, "back") ?: "back",
                rotation = prefs.getString(KEY_ROTATION, "auto") ?: "auto",
                resolution = prefs.getString(KEY_RESOLUTION, null),
                serverPort = prefs.getInt(KEY_SERVER_PORT, 8080),
                jpegQuality = prefs.getInt(KEY_JPEG_QUALITY, 80),
                targetFps = prefs.getInt(KEY_TARGET_FPS, 10),
                flashlightEnabled = prefs.getBoolean(KEY_FLASHLIGHT, false),
                keepScreenOn = prefs.getBoolean(KEY_KEEP_SCREEN_ON, false),
                autoStart = prefs.getBoolean(KEY_AUTO_START, false),
                maxConnections = prefs.getInt(KEY_MAX_CONNECTIONS, 32)
            )
        }

        /**
         * Save configuration to SharedPreferences
         */
        fun save(context: Context, config: StreamingConfig) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit().apply {
                putString(KEY_CAMERA_TYPE, config.cameraType)
                putString(KEY_ROTATION, config.rotation)
                putString(KEY_RESOLUTION, config.resolution)
                putInt(KEY_SERVER_PORT, config.serverPort)
                putInt(KEY_JPEG_QUALITY, config.jpegQuality)
                putInt(KEY_TARGET_FPS, config.targetFps)
                putBoolean(KEY_FLASHLIGHT, config.flashlightEnabled)
                putBoolean(KEY_KEEP_SCREEN_ON, config.keepScreenOn)
                putBoolean(KEY_AUTO_START, config.autoStart)
                putInt(KEY_MAX_CONNECTIONS, config.maxConnections)
                apply()  // Synchronous write to memory, async write to disk
            }
        }
    }
}
