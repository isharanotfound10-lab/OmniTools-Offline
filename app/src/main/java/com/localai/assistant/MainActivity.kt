package com.localai.assistant

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.Gravity
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.text.InputType
import android.view.inputmethod.EditorInfo
import java.net.URLEncoder

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 9, 22)
    private val panel = Color.rgb(18, 22, 43)
    private val cyan = Color.rgb(63, 224, 235)
    private val violet = Color.rgb(143, 103, 255)
    private lateinit var input: EditText
    private lateinit var web: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private var activeSite = "all"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        if (!getSharedPreferences("psearch", MODE_PRIVATE).getBoolean("adult_confirmed", false)) {
            android.app.AlertDialog.Builder(this)
                .setTitle("18+ content")
                .setMessage("PSearch searches publicly indexed adult-video pages. You must be at least 18 years old to continue.")
                .setCancelable(false)
                .setNegativeButton("Exit") { _, _ -> finish() }
                .setPositiveButton("I am 18 or older") { _, _ ->
                    getSharedPreferences("psearch", MODE_PRIVATE).edit().putBoolean("adult_confirmed", true).apply()
                    showApp()
                }.show()
        } else showApp()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun rounded(color: Int, radius: Int = 18, stroke: Int = 0): GradientDrawable =
        GradientDrawable().apply { setColor(color); cornerRadius = dp(radius).toFloat(); if (stroke != 0) setStroke(dp(1), stroke) }

    private fun showApp() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(8))
            setBackgroundColor(bg)
        }
        val scroll = ScrollView(this)
        val content = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        scroll.addView(content)

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(18))
        }
        val logo = TextView(this).apply {
            text = "P"
            textSize = 28f
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
            typeface = Typeface.DEFAULT_BOLD
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(violet, cyan)).apply { cornerRadius = dp(17).toFloat() }
        }
        header.addView(logo, LinearLayout.LayoutParams(dp(54), dp(54)))
        val brand = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), 0, 0, 0) }
        brand.addView(TextView(this).apply { text = "PSearch"; textSize = 25f; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD })
        brand.addView(TextView(this).apply { text = "CODE & TITLE DISCOVERY"; textSize = 10f; letterSpacing = 1.5f; setTextColor(cyan) })
        header.addView(brand)
        content.addView(header)

        content.addView(TextView(this).apply { text = "Find what you're looking for."; textSize = 21f; typeface = Typeface.DEFAULT_BOLD; setTextColor(Color.WHITE) })
        content.addView(TextView(this).apply {
            text = "Search public results across supported sites."
            textSize = 13f; setTextColor(0xFF9AA4C2.toInt()); setPadding(0, dp(5), 0, dp(16))
        })

        input = EditText(this).apply {
            hint = "Enter a video code or title"
            setHintTextColor(0xFF78819D.toInt())
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            imeOptions = EditorInfo.IME_ACTION_SEARCH
            setPadding(dp(16), 0, dp(14), 0)
            background = rounded(panel, 16, 0xFF303958.toInt())
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_SEARCH) { search(activeSite); true } else false
            }
        }
        content.addView(input, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(12) })

        val sites = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        addSiteButton(sites, "All sites", "all")
        addSiteButton(sites, "Pornhub", "pornhub.com")
        addSiteButton(sites, "xHamster", "xhamster.com")
        addSiteButton(sites, "Eporner", "eporner.com")
        content.addView(sites)

        val searchButton = Button(this).apply {
            text = "⌕   SEARCH"
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(8, 14, 30))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(cyan, violet)).apply { cornerRadius = dp(16).toFloat() }
            setOnClickListener { search(activeSite) }
        }
        content.addView(searchButton, LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(12); bottomMargin = dp(12) })

        status = TextView(this).apply {
            text = "Choose a site and enter a code or title."
            textSize = 12f; setTextColor(0xFF9AA4C2.toInt()); setPadding(dp(2), dp(4), 0, dp(8))
        }
        content.addView(status)
        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply { isIndeterminate = true; visibility = View.GONE }
        content.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        web = WebView(this).apply {
            setBackgroundColor(bg)
            settings.javaScriptEnabled = false
            settings.domStorageEnabled = false
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.safeBrowsingEnabled = true
            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                    this@MainActivity.progress.visibility = View.VISIBLE
                    status.text = "Loading public search results…"
                }
                override fun onPageFinished(view: WebView?, url: String?) {
                    this@MainActivity.progress.visibility = View.GONE
                    status.text = "Results are provided by Google. Thumbnails and matches depend on public indexing."
                }
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return !(url?.startsWith("https://") == true || url?.startsWith("http://") == true)
                }
            }
            webChromeClient = WebChromeClient()
        }
        content.addView(web, LinearLayout.LayoutParams(-1, dp(520)))
        content.addView(TextView(this).apply {
            text = "PSearch does not host videos. Results open on their original websites; availability and thumbnails are controlled by those sites and the search provider."
            textSize = 11f; setTextColor(0xFF78819D.toInt()); setPadding(dp(2), dp(12), dp(2), dp(18))
        })
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
    }

    private fun addSiteButton(row: LinearLayout, label: String, site: String) {
        val b = TextView(this).apply {
            text = label; textSize = 11f; gravity = Gravity.CENTER
            setTextColor(if (activeSite == site) Color.WHITE else 0xFFB7C0D8.toInt())
            setPadding(dp(8), dp(11), dp(8), dp(11))
            background = rounded(if (activeSite == site) 0xFF34315D.toInt() else panel, 13, if (activeSite == site) violet else 0)
            setOnClickListener {
                activeSite = site
                row.removeAllViews()
                addSiteButton(row, "All sites", "all")
                addSiteButton(row, "Pornhub", "pornhub.com")
                addSiteButton(row, "xHamster", "xhamster.com")
                addSiteButton(row, "Eporner", "eporner.com")
            }
        }
        row.addView(b, LinearLayout.LayoutParams(0, dp(42), 1f).apply { marginEnd = dp(4) })
    }

    private fun search(site: String) {
        val q = input.text.toString().trim()
        if (q.isBlank()) {
            Toast.makeText(this, "Enter a code or title first", Toast.LENGTH_SHORT).show()
            return
        }
        val filter = when (site) {
            "pornhub.com" -> "site:pornhub.com"
            "xhamster.com" -> "site:xhamster.com"
            "eporner.com" -> "site:eporner.com"
            else -> "(site:pornhub.com OR site:xhamster.com OR site:eporner.com)"
        }
        val query = "$filter \"$q\""
        val url = "https://www.google.com/search?tbm=vid&q=" + URLEncoder.encode(query, "UTF-8")
        status.text = "Searching public pages…"
        web.loadUrl(url)
    }

    @Deprecated("Deprecated in Android")
    override fun onBackPressed() {
        if (::web.isInitialized && web.canGoBack()) web.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::web.isInitialized) { web.stopLoading(); web.destroy() }
        super.onDestroy()
    }
}