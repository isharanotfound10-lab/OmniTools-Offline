package com.localai.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Context
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val bg = Color.rgb(13, 7, 23)
    private val panel = Color.rgb(28, 18, 43)
    private val purple = Color.rgb(166, 105, 255)
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText
    private lateinit var modelButton: Button
    private lateinit var modelStatus: TextView
    @Volatile private var llm: LlmInference? = null
    @Volatile private var busy = false

    private val modelFile: File by lazy { File(filesDir, "Gemma3-1B-IT_q4.task") }
    private val modelUrl =
        "https://huggingface.co/litert-community/Gemma3-1B-IT/resolve/main/Gemma3-1B-IT_multi-prefill-seq_q4_ekv2048.task"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        window.decorView.systemUiVisibility = 0

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            setBackgroundColor(bg)
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
            text = "ON-DEVICE AI  •  PRIVATE CHAT"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(177, 155, 205))
            setPadding(0, dp(5), 0, dp(8))
        }
        modelStatus = TextView(this).apply {
            text = if (modelFile.exists()) "Model downloaded • tap Load offline AI" else
                "Optional model: about 555 MB download. 4 GB RAM is recommended; this phone may struggle."
            textSize = 11f
            setTextColor(Color.rgb(203, 185, 226))
            setPadding(dp(2), dp(2), dp(2), dp(8))
        }
        modelButton = Button(this).apply {
            text = if (modelFile.exists()) "Load offline AI" else "Download AI model (~555 MB)"
            textSize = 12f
            isAllCaps = false
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(75, 43, 112), dp(14))
            setOnClickListener { onModelButton() }
        }
        header.addView(title)
        header.addView(subtitle)
        header.addView(modelStatus)
        header.addView(modelButton, LinearLayout.LayoutParams(-1, dp(44)))
        root.addView(header)

        scroll = ScrollView(this).apply {
            isFillViewport = true
            overScrollMode = ScrollView.OVER_SCROLL_NEVER
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
            hint = "Message Local AI…"
            setHintTextColor(Color.rgb(159, 143, 178))
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
            background = rounded(purple, dp(18))
            minWidth = dp(48)
            minimumWidth = dp(48)
            setOnClickListener { sendMessage() }
        }
        composer.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        composer.addView(send, LinearLayout.LayoutParams(dp(48), dp(48)))
        root.addView(composer, LinearLayout.LayoutParams(-1, -2))
        setContentView(root)

        addMessage("Local AI", "Chat works with offline commands now. Download and load the optional language model above to enable generative AI. The model runs on this device after download; no cloud chat is used.", false)
        if (modelFile.exists()) modelStatus.text = "Model file found • tap Load offline AI"
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
                    if (connection.responseCode !in 200..299) error("Download server returned HTTP ${connection.responseCode}")
                    val temp = File(filesDir, "model.download")
                    connection.inputStream.use { source ->
                        temp.outputStream().buffered().use { target -> source.copyTo(target) }
                    }
                    if (temp.length() < 100_000_000L) {
                        temp.delete()
                        error("Downloaded file was too small to be a valid model.")
                    }
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
                    runOnUiThread {
                        modelStatus.text = "Model download failed: ${e.message ?: "unknown error"}. Check connection and retry."
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
                    val options = LlmInference.LlmInferenceOptions.builder()
                        .setModelPath(modelFile.absolutePath)
                        .setMaxTokens(256)
                        .build()
                    val engine = LlmInference.createFromOptions(this, options)
                    llm?.close()
                    llm = engine
                    runOnUiThread {
                        modelStatus.text = "Offline language model loaded • prompts stay on this device"
                        modelButton.text = "AI model loaded ✓"
                        modelButton.isEnabled = true
                        addMessage("Local AI", "The on-device language model is loaded. Try asking me a question.", false)
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
        addMessage("You", text, true)
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)

        val engine = llm
        if (engine == null) {
            addMessage("Local AI", OfflineBrain.respond(text) + "\n\nFor open-ended AI chat, download and load the optional model above.", false)
            return
        }

        busy = true
        addMessage("Local AI", "Thinking offline…", false)
        worker.execute {
            try {
                val response = engine.generateResponse("You are Local AI, a helpful, concise assistant. Answer in plain text. User: $text\nAssistant:")
                runOnUiThread {
                    val last = messages.getChildAt(messages.childCount - 1) as? LinearLayout
                    val bubble = last?.getChildAt(1) as? TextView
                    if (bubble != null && bubble.text == "Thinking offline…") bubble.text = response.trim()
                    else addMessage("Local AI", response.trim(), false)
                }
            } catch (e: Exception) {
                runOnUiThread { addMessage("Local AI", "Generation failed: ${e.message ?: "model error"}. You can still use offline commands.", false) }
            } finally {
                busy = false
            }
        }
    }

    override fun onDestroy() {
        llm?.close()
        worker.shutdownNow()
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
            setTextColor(if (fromUser) Color.rgb(202, 174, 255) else Color.rgb(177, 155, 205))
            setPadding(dp(8), 0, dp(8), dp(4))
        }
        val bubble = TextView(this).apply {
            text = body
            textSize = 15f
            setTextColor(Color.WHITE)
            setPadding(dp(14), dp(11), dp(14), dp(11))
            background = rounded(if (fromUser) Color.rgb(75, 43, 112) else panel, dp(18))
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
