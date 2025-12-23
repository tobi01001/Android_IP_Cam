package com.example.androidipcam

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * BootReceiver - Auto-start service on device boot
 * 
 * REQ-PER-009: Restore settings from SharedPreferences
 * 
 * Starts CameraService automatically when device boots,
 * if auto-start is enabled in settings. This enables:
 * - Unattended operation after power outage
 * - Automatic service startup after device reboot
 * - Zero-touch surveillance system deployment
 * 
 * Requires RECEIVE_BOOT_COMPLETED permission in manifest.
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "BootReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED || 
            intent.action == "android.intent.action.QUICKBOOT_POWERON") {
            
            Log.d(TAG, "Boot completed, checking auto-start setting")
            
            // Load configuration
            val config = StreamingConfig.load(context)
            
            if (config.autoStart) {
                Log.d(TAG, "Auto-start enabled, starting service")
                
                val serviceIntent = Intent(context, CameraService::class.java).apply {
                    action = CameraService.ACTION_START_SERVICE
                }
                
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        context.startForegroundService(serviceIntent)
                    } else {
                        context.startService(serviceIntent)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to start service on boot", e)
                }
            } else {
                Log.d(TAG, "Auto-start disabled")
            }
        }
    }
}
