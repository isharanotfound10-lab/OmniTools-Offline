package com.localai.assistant

import android.content.Context
import com.google.ai.edge.litertlm.Tool
import com.google.ai.edge.litertlm.ToolParam
import com.google.ai.edge.litertlm.ToolSet

/**
 * Narrow, allow-listed tools exposed to the local model. The model cannot run
 * shell commands or arbitrary code; Android actions are routed through AgentActions.
 */
class AndroidAgentTools(private val context: Context) : ToolSet {
    @Tool(description = "Open an installed Android app by its common name, such as YouTube, WhatsApp, Chrome, Gmail, Maps, Camera, Clock, or Calculator.")
    fun openApp(
        @ToolParam(description = "Common app name, for example YouTube or WhatsApp") appName: String
    ): String {
        return AgentActions.execute(context, "open $appName")
            ?: "That app is not in the currently supported app allow-list."
    }

    @Tool(description = "Open a supported Android settings screen such as Wi-Fi, Bluetooth, display, sound, battery, app settings, or general settings.")
    fun openAndroidSettings(
        @ToolParam(description = "Settings area: Wi-Fi, Bluetooth, display, sound, battery, app settings, or general settings") setting: String
    ): String {
        return AgentActions.execute(context, "open $setting")
            ?: "That settings screen is not supported on this device."
    }
}
