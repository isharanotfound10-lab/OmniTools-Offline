package com.localai.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

/**
 * First native agent tools. The app launches only explicitly supported packages
 * and Android settings pages; it never executes arbitrary model output as code.
 */
object AgentActions {
    private val appPackages = mapOf(
        "youtube" to "com.google.android.youtube",
        "whatsapp" to "com.whatsapp",
        "whatsapp business" to "com.whatsapp.w4b",
        "chrome" to "com.android.chrome",
        "google chrome" to "com.android.chrome",
        "gmail" to "com.google.android.gm",
        "google maps" to "com.google.android.apps.maps",
        "maps" to "com.google.android.apps.maps",
        "play store" to "com.android.vending",
        "google photos" to "com.google.android.apps.photos",
        "photos" to "com.google.android.apps.photos",
        "camera" to "com.android.camera2",
        "clock" to "com.google.android.deskclock",
        "calculator" to "com.google.android.calculator"
    )

    fun execute(context: Context, command: String): String? {
        val normalized = command.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return null
        val verb = listOf("open ", "launch ", "start ", "go to ").firstOrNull {
            normalized.startsWith(it)
        } ?: return null
        val target = normalized.removePrefix(verb).removeSuffix(" please").removeSuffix(" app").trim()
        if (target.isEmpty()) return "Tell me which app or settings page to open."

        val settingsAction = when {
            target in listOf("settings", "phone settings", "system settings") -> Settings.ACTION_SETTINGS
            target.contains("wi-fi") || target.contains("wifi") || target.contains("wireless settings") -> Settings.ACTION_WIFI_SETTINGS
            target.contains("bluetooth") -> Settings.ACTION_BLUETOOTH_SETTINGS
            target.contains("display") || target.contains("screen settings") -> Settings.ACTION_DISPLAY_SETTINGS
            target.contains("sound") || target.contains("volume settings") -> Settings.ACTION_SOUND_SETTINGS
            target.contains("battery") -> Settings.ACTION_BATTERY_SAVER_SETTINGS
            target.contains("app settings") || target.contains("applications") -> Settings.ACTION_APPLICATION_SETTINGS
            else -> null
        }
        if (settingsAction != null) {
            return try {
                context.startActivity(Intent(settingsAction).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                "Opening ${settingsLabel(target)}."
            } catch (_: Exception) {
                "Android couldn't open that settings page on this device."
            }
        }

        val packageName = appPackages[target] ?: return null
        return try {
            val launch = context.packageManager.getLaunchIntentForPackage(packageName)
                ?: return "I couldn't find ${displayName(target)} installed on this phone."
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            "Opening ${displayName(target)}."
        } catch (_: Exception) {
            "Android couldn't open ${displayName(target)}. Check whether the app is installed or disabled."
        }
    }

    private fun displayName(target: String): String = when (target) {
        "youtube" -> "YouTube"
        "whatsapp" -> "WhatsApp"
        "whatsapp business" -> "WhatsApp Business"
        "chrome", "google chrome" -> "Chrome"
        "gmail" -> "Gmail"
        "google maps", "maps" -> "Google Maps"
        "play store" -> "Play Store"
        "google photos", "photos" -> "Google Photos"
        "camera" -> "Camera"
        "clock" -> "Clock"
        "calculator" -> "Calculator"
        else -> target
    }

    private fun settingsLabel(target: String): String = when {
        target.contains("wifi") || target.contains("wi-fi") -> "Wi-Fi settings"
        target.contains("bluetooth") -> "Bluetooth settings"
        target.contains("display") || target.contains("screen") -> "display settings"
        target.contains("sound") || target.contains("volume") -> "sound settings"
        target.contains("battery") -> "battery settings"
        target.contains("app settings") || target.contains("applications") -> "app settings"
        else -> "Settings"
    }
}
