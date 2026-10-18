package com.localai.assistant

import android.app.Activity
import android.app.ActivityManager
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import com.google.ai.edge.litertlm.tool
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg = Color.rgb(3, 0, 30)          // #03001e
    private val purple = Color.rgb(115, 3, 192)   // #7303c0
    private val pink = Color.rgb(236, 56, 188)    // #ec38bc
    private val panel = Color.rgb(22, 10, 48)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var modelButton: Button
    private lateinit var modelStatus: TextView
    private lateinit var orbButton: Button
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null
    @Volatile private var busy = false

    private val modelFile: File by lazy { File(filesDir, "functiongemma-mobile-actions.litertlm") }
    // LiteRT-LM Mobile Actions FunctionGemma model (~271 MB in the official Q8 configuration).
    // This public mirror serves the LiteRT-LM .litertlm deployment format.
    private val modelUrl =
        "https://huggingface.co/Yagna1/functiongemma-270m-mobile-actions/resolve/main/mobile-actions_q8_ekv1024.litertlm"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(bg, purple, pink)
            )
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        val title = TextView(this).apply {
            text = "✦  LOCAL AI"
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "ON-DEVICE ACTION AGENT  •  TEXT ONLY"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(255, 218, 250))
            setPadding(0, dp(5), 0, dp(8))
        }
        modelStatus = TextView(this).apply {
            text = if (modelFile.exists()) "Model downloaded • tap Load offline AI" else
                "Optional agent model: about 271 MB. Performance depends on available RAM."
            textSize = 11f
            setTextColor(Color.rgb(245, 228, 255))
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        modelButton = Button(this).apply {
            text = if (modelFile.exists()) "Load offline AI" else "Download agent model (~271 MB)"
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(purple, pink)
            ).apply { cornerRadius = dp(14).toFloat() }
            setOnClickListener { onModelButton() }
        }
        header.addView(title)
        header.addView(subtitle)
        header.addView(modelStatus)
        header.addView(modelButton, LinearLayout.LayoutParams(-1, dp(44)))
        orbButton = Button(this).apply {
            text = "Enable floating bot"
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = GradientDrawable(
                GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(purple, pink)
            ).apply { cornerRadius = dp(14).toFloat() }
            setOnClickListener { enableFloatingOrb() }
        }
        val orbParams = LinearLayout.LayoutParams(-1, dp(44))
        orbParams.topMargin = dp(8)
        header.addView(orbButton, orbParams)
        root.addView(header)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
            setBackgroundColor(Color.TRANSPARENT)
        }
        messages = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(6), 0, dp(12))
        }
        scroll.addView(messages)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val composer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(6), dp(6), dp(6))
            background = rounded(panel, dp(22))
        }
        input = EditText(this).apply {
            hint = "Describe a task to execute…"
            setHintTextColor(Color.rgb(187, 162, 211))
            setTextColor(Color.WHITE)
            textSize = 15f
            maxLines = 4
            minHeight = dp(44)
            setPadding(dp(10), dp(4), dp(8), dp(4))
            background = null
            imeOptions = EditorInfo.IME_ACTION_SEND
            setOnEditorActionListener { _, actionId, _ ->
                if (actionId == EditorInfo.IME_ACTION_SEND) { sendMessage(); true } else false
            }
        }
        val send = Button(this).apply {
            text = "↑"
            textSize = 22f
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = GradientDrawable(
                GradientDrawable.Orientation.TL_BR,
                intArrayOf(purple, pink)
            ).apply { cornerRadius = dp(18).toFloat() }
            minWidth = dp(48)
            minimumWidth = dp(48)
            setOnClickListener { sendMessage() }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(composer, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        showStatus("Ready for a task. Tap the floating orb to open the quick action panel, or enter a task below. Download and load the on-device agent model to enable model-based tool selection.")
        if (modelFile.exists()) modelStatus.text = "Model file found • tap Load offline AI"
        refreshOrbStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshOrbStatus()
    }

    private fun refreshOrbStatus() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this)) {
            try {
                startService(Intent(this, FloatingOrbService::class.java))
                orbButton.text = "Floating bot enabled ✓"
            } catch (_: Exception) {
                orbButton.text = "Tap to enable floating bot"
            }
        } else {
            orbButton.text = "Enable floating bot (permission required)"
        }
    }

    private fun enableFloatingOrb() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            try {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivity(intent)
            } catch (_: Exception) {
                modelStatus.text = "Open Android Settings > Apps > Special app access > Display over other apps > Local AI, then allow it."
            }
        } else {
            refreshOrbStatus()
        }
    }

    private fun onModelButton() {
        if (busy) return
        if (!modelFile.exists()) {
            modelButton.isEnabled = false
            modelButton.text = "Downloading…"
            modelStatus.text = "Downloading model. Keep the app open and use Wi-Fi if possible."
            busy = true
            worker.execute {
                var connection: HttpURLConnection? = null
                try {
                    connection = (URL(modelUrl).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 20000
                        readTimeout = 60000
                        instanceFollowRedirects = true
                        setRequestProperty("User-Agent", "LocalAI-Android")
                    }
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        val code = connection.responseCode
                        error(
                            when (code) {
                                401, 403 -> "The model host denied access (HTTP $code). Open the model page in a browser and check its access conditions."
                                404 -> "The model file was not found (HTTP 404)."
                                else -> "Download server returned HTTP $code."
                            }
                        )
                    }
                    val temp = File(filesDir, "model.download")
                    temp.delete()
                    val totalBytes = connection.contentLengthLong
                    var downloadedBytes = 0L
                    var lastShownPercent = -1
                    connection.inputStream.buffered().use { source ->
                        temp.outputStream().buffered().use { target ->
                            val buffer = ByteArray(64 * 1024)
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                target.write(buffer, 0, count)
                                downloadedBytes += count
                                if (totalBytes > 0L) {
                                    val percent = ((downloadedBytes * 100) / totalBytes).toInt().coerceIn(0, 100)
                                    if (percent != lastShownPercent) {
                                        lastShownPercent = percent
                                        runOnUiThread {
                                            modelStatus.text = "Downloading AI model… $percent% (${downloadedBytes / 1_000_000} MB)"
                                        }
                                    }
                                } else if (downloadedBytes % (10L * 1024L * 1024L) < buffer.size) {
                                    val mb = downloadedBytes / 1_000_000
                                    runOnUiThread { modelStatus.text = "Downloading AI model… $mb MB received" }
                                }
                            }
                        }
                    }
                    if (temp.length() < 200_000_000L) {
                        val received = temp.length()
                        temp.delete()
                        error("The server returned an incomplete model file ($received bytes). Expected a model of roughly 271 MB; check access and retry.")
                    }
                    runOnUiThread { modelStatus.text = "Checking model file…" }
                    if (modelFile.exists()) modelFile.delete()
                    if (!temp.renameTo(modelFile)) {
                        temp.copyTo(modelFile, overwrite = true)
                        temp.delete()
                    }
                    runOnUiThread {
                        modelStatus.text = "Download complete. Tap Load offline AI."
                        modelButton.text = "Load offline AI"
                        modelButton.isEnabled = true
                    }
                } catch (e: Exception) {
                    File(filesDir, "model.download").delete()
                    runOnUiThread {
                        modelStatus.text = "Model download failed: ${e.message ?: "unknown error"}. Check connection and free storage, then retry."
                        modelButton.text = "Retry model download"
                        modelButton.isEnabled = true
                    }
                } finally {
                    connection?.disconnect()
                    busy = false
                }
            }
        } else {
            modelButton.isEnabled = false
            modelButton.text = "Loading model…"
            modelStatus.text = "Loading local model. On this phone it may take time or run out of memory."
            busy = true
            worker.execute {
                try {
                    val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
                    val memoryInfo = ActivityManager.MemoryInfo()
                    activityManager.getMemoryInfo(memoryInfo)
                    val availableRamMb = memoryInfo.availMem / (1024L * 1024L)
                    if (availableRamMb < 650L) {
                        runOnUiThread {
                            modelStatus.text = "Only ${availableRamMb} MB RAM is available. Close other apps before loading the ~271 MB agent model."
                            modelButton.text = "Try loading again"
                            modelButton.isEnabled = true
                        }
                        return@execute
                    }
                    val newEngine = Engine(EngineConfig(
                        modelPath = modelFile.absolutePath,
                        backend = Backend.CPU()
                    ))
                    newEngine.initialize()
                    val newConversation = newEngine.createConversation(
                        ConversationConfig(
                            systemInstruction = Contents.of(
                                "You are Local AI, an offline Android agent. Use the provided tools to open supported apps or Android settings when asked. Never claim an action succeeded unless a tool result confirms it. If a request is outside your tools, explain the limitation."
                            ),
                            tools = listOf(tool(AndroidAgentTools(this)))
                        )
                    )
                    LocalAgentRuntime.install(newEngine, newConversation)
                    engine = newEngine
                    conversation = newConversation
                    runOnUiThread {
                        modelStatus.text = "Offline agent model loaded • runs on this device"
                        modelButton.text = "Agent model loaded ✓"
                        modelButton.isEnabled = true
                        showStatus("Agent ready • model loaded on this device. Enter a task and tap GO, or use the floating orb.")
                    }
                } catch (e: Exception) {
                    runOnUiThread {
                        modelStatus.text = "Could not load model: ${e.message ?: "device/runtime error"}. This model may exceed your phone's memory."
                        modelButton.text = "Try loading again"
                        modelButton.isEnabled = true
                    }
                } finally {
                    busy = false
                }
            }
        }
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty() || busy) return
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)

        // Execute known native actions directly; use the local model for other requests.
        val actionResult = AgentActions.execute(this, text)
        if (actionResult != null) {
            showStatus(actionResult)
            return
        }

        if (!LocalAgentRuntime.isLoaded()) {
            showStatus(OfflineBrain.respond(text) + "\n\nLoad the downloaded on-device agent model to enable model-based task selection.")
            return
        }

        busy = true
        showStatus("Working on your task…")
        worker.execute {
            try {
                val response = LocalAgentRuntime.executeWithModel(text) ?: OfflineBrain.respond(text)
                runOnUiThread { showStatus(response) }
            } catch (e: Exception) {
                runOnUiThread { showStatus("Task failed: ${e.message ?: "agent error"}. Try a supported action or retry.") }
            } finally {
                busy = false
            }
        }
    }

    private fun showStatus(body: String) {
        messages.removeAllViews()
        addMessage("ACTION STATUS", body, false)
    }

    override fun onDestroy() {
        // The process-wide agent runtime is shared with the floating orb.\n        worker.shutdownNow()
        super.onDestroy()
    }

    private fun addMessage(sender: String, body: String, fromUser: Boolean) {
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = if (fromUser) Gravity.END else Gravity.START
            setPadding(0, dp(5), 0, dp(5))
        }
        val label = TextView(this).apply {
            text = sender
            textSize = 11f
            setTextColor(if (fromUser) Color.rgb(255, 202, 246) else Color.rgb(255, 218, 250))
            setPadding(dp(8), 0, dp(8), dp(4))
        }
        val bubble = TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(if (fromUser) purple else panel, dp(18))
            maxWidth = resources.displayMetrics.widthPixels - dp(80)
        }
        wrapper.addView(label)
        wrapper.addView(bubble)
        messages.addView(wrapper, LinearLayout.LayoutParams(-1, -2))
        scroll.post { scroll.fullScroll(ScrollView.FOCUS_DOWN) }
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius.toFloat()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
}
