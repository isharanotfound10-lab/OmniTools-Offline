package com.xanime.app

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    private val bg = Color.rgb(10, 7, 22)
    private val panel = Color.rgb(27, 18, 48)
    private val accent = Color.rgb(178, 91, 255)
    private val muted = Color.rgb(174, 162, 196)
    private var currentPage = 1
    private var currentQuery = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!getSharedPreferences("xanime", MODE_PRIVATE).getBoolean("adult_confirmed", false)) showAgeGate() else showHome()
    }

    private fun baseLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(20), dp(24), dp(20), dp(18))
        setBackgroundColor(bg)
        layoutParams = ViewGroup.LayoutParams(-1, -1)
    }

    private fun showAgeGate() {
        val root = baseLayout().apply { gravity = Gravity.CENTER }
        root.addView(TextView(this).apply {
            text = "X"; textSize = 54f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); gravity = Gravity.CENTER; background = rounded(accent, 28)
            layoutParams = LinearLayout.LayoutParams(dp(92), dp(92)).apply { bottomMargin = dp(24) }
        })
        root.addView(label("XANIME", 30, Color.WHITE, true))
        root.addView(label("ADULT CONTENT", 12, accent, true).apply {
            letterSpacing = 0.18f; setPadding(0, dp(8), 0, dp(24))
        })
        root.addView(label("You must be 18 years or older to continue.", 16, Color.WHITE, true))
        root.addView(label("By continuing, you confirm that you are an adult and that viewing adult material is legal where you live.", 13, muted, false).apply {
            gravity = Gravity.CENTER; setPadding(0, dp(12), 0, dp(28))
        })
        root.addView(actionButton("I AM 18 OR OLDER") {
            getSharedPreferences("xanime", MODE_PRIVATE).edit().putBoolean("adult_confirmed", true).apply()
            showHome()
        })
        root.addView(actionButton("EXIT", false) { finish() })
        setContentView(root)
    }

    private fun showHome() {
        val root = baseLayout().apply { gravity = Gravity.TOP }
        val header = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        header.addView(TextView(this).apply {
            text = "X"; textSize = 25f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER
            setTextColor(Color.WHITE); background = rounded(accent, 16)
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48))
        })
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        titleBox.addView(label("XANIME", 22, Color.WHITE, true))
        titleBox.addView(label("ADULT ANIMATION CATALOG", 10, muted, true))
        header.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(header)

        val search = EditText(this).apply {
            hint = "Search titles..."; setHintTextColor(muted); setTextColor(Color.WHITE)
            textSize = 15f; isSingleLine = true; imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(16), 0, dp(16), 0); background = rounded(panel, 16)
            setText(currentQuery)
        }
        root.addView(search, LinearLayout.LayoutParams(-1, dp(54)).apply { topMargin = dp(26) })
        root.addView(actionButton("SEARCH") {
            currentQuery = search.text.toString().trim(); currentPage = 1; showHome()
        }, LinearLayout.LayoutParams(-1, dp(48)).apply { topMargin = dp(10) })
        search.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                currentQuery = search.text.toString().trim(); currentPage = 1; showHome(); true
            } else false
        }

        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(0, dp(24), 0, dp(12)) }
        content.addView(label(if (currentQuery.isBlank()) "Discover" else "Search: $currentQuery", 21, Color.WHITE, true))
        content.addView(label("Browse verified adult-only catalogue entries.", 13, muted, false).apply {
            setPadding(0, dp(10), 0, dp(18))
        })
        content.addView(TextView(this).apply {
            text = "CATALOG NOT CONNECTED\n\nThe target site does not provide a reliable adult-only verification signal for every entry. XAnime will only display entries that a trusted source explicitly verifies as adult-only."
            textSize = 14f; setTextColor(muted); gravity = Gravity.CENTER
            setPadding(dp(20), dp(26), dp(20), dp(26)); background = rounded(panel, 20)
        }, LinearLayout.LayoutParams(-1, -2))
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))

        val pages = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        pages.addView(actionButton("‹  PREVIOUS", false) {
            if (currentPage > 1) { currentPage--; showHome() }
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) })
        pages.addView(label("PAGE $currentPage", 12, muted, true).apply { gravity = Gravity.CENTER },
            LinearLayout.LayoutParams(dp(82), dp(44)))
        pages.addView(actionButton("NEXT  ›", false) {
            currentPage++; showHome()
        }, LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) })
        root.addView(pages)
        setContentView(root)
    }

    private fun actionButton(text: String, primary: Boolean = true, action: () -> Unit) =
        Button(this).apply {
            this.text = text; textSize = 12f; isAllCaps = false; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); background = rounded(if (primary) accent else panel, 14)
            setOnClickListener { action() }
        }

    private fun label(text: String, size: Int, color: Int, bold: Boolean) = TextView(this).apply {
        this.text = text; textSize = size.toFloat(); setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color); cornerRadius = dp(radius).toFloat()
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}