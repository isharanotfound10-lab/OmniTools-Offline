package com.localai.assistant

import android.content.Context
import android.content.Intent
import android.provider.Settings
import java.util.Locale

/**
 * Small, deterministic native action layer.
 * Actions are allow-listed and launched through Android intents; generated text
 * is never treated as executable code.
 */
object AgentActions {
    fun execute(context: Context, command: String): String? {
        val normalized = command.trim().lowercase(Locale.ROOT)
        if (normalized.isEmpty()) return null

        val openVerb = listOf("open ", "launch ", "start ", "go to ").firstOrNull {
            normalized.startsWith(it)
        } ?: return null
        val target = normalized.removePrefix(openVerb)
            .removeSuffix(" please")
            .removeSuffix(" app")
            .trim()
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

        // Resolve only installed apps with a launcher activity; no arbitrary intents or shell commands.
        val packageManager = context.packageManager
        val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = try {
            @Suppress("DEPRECATION")
            packageManager.queryIntentActivities(launcherIntent, 0)
        } catch (_: Exception) {
            emptyList()
        }
        val matches = apps.mapNotNull { resolve ->
            val label = resolve.loadLabel(packageManager)?.toString()?.trim().orEmpty()
            val labelLower = label.lowercase(Locale.ROOT)
            val score = when {
                labelLower == target -> 0
                labelLower.startsWith(target) -> 1
                labelLower.contains(target) -> 2
                target.contains(labelLower) && labelLower.length >= 3 -> 3
                else -> return@mapNotNull null
            }
            Triple(score, label, resolve.activityInfo.packageName)
        }.sortedWith(compareBy<Triple<Int, String, String>> { it.first }.thenBy { it.second.length })

        val match = matches.firstOrNull()
            ?: return if (target in listOf("youtube", "whatsapp", "chrome", "camera", "gallery", "photos")) {
                "I couldn't find $target installed. If it's installed, try its full app name."
            } else null

        return try {
            val launch = packageManager.getLaunchIntentForPackage(match.third)
                ?: return "I found ${match.second}, but Android doesn't provide a launch action for it."
            launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(launch)
            "Opening ${match.second}."
        } catch (_: Exception) {
            "Android couldn't open ${match.second}. Check whether the app is disabled."
        }
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
