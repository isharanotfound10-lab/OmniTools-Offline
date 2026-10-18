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

class MainActivity : Activity() {
    private val bg = Color.rgb(13, 7, 23)
    private val panel = Color.rgb(28, 18, 43)
    private val purple = Color.rgb(166, 105, 255)
    private lateinit var messages: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var input: EditText

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
            setPadding(0, dp(8), 0, dp(16))
        }
        val title = TextView(this).apply {
            text = "✦  LOCAL AI"
            textSize = 23f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
        }
        val subtitle = TextView(this).apply {
            text = "PRIVATE BY DESIGN  •  TEXT CHAT"
            textSize = 10f
            letterSpacing = 0.12f
            setTextColor(Color.rgb(177, 155, 205))
            setPadding(0, dp(5), 0, 0)
        }
        header.addView(title)
        header.addView(subtitle)
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

        addMessage("Local AI", "Hey! The text-chat interface is ready. The offline AI model is not connected yet, so I can't generate real AI answers in this build.", false)
        addMessage("Setup", "Next: connect a compact on-device language model, then test memory use and response speed on your phone.", false)
    }

    private fun sendMessage() {
        val text = input.text.toString().trim()
        if (text.isEmpty()) return
        addMessage("You", text, true)
        input.setText("")
        (getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager)
            .hideSoftInputFromWindow(input.windowToken, 0)
        addMessage("Local AI", "I received your message, but the local language model is not connected in this version yet.", false)
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
