package com.xanime.app

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView

/**
 * HentaiRead is intentionally hosted in a normal in-app WebView.
 * The website handles its own scripts, reader navigation, images and any
 * verification pages; this activity does not scrape or download comic pages.
 */
class HentaiReadComicsActivity : Activity() {
    private var webView: WebView? = null
    private lateinit var progress: ProgressBar
    private lateinit var pageTitle: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(2, 6, 9)
        window.navigationBarColor = Color.rgb(2, 6, 9)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(2, 6, 9))
            setPadding(dp(10), dp(10), dp(10), dp(8))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val back = Button(this).apply {
            text = "‹"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 32, 45))
            setOnClickListener { finish() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(dp(44), dp(42)))
        pageTitle = TextView(this).apply {
            text = "HENTAIREAD"
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(dp(10), 0, dp(6), 0)
        }
        toolbar.addView(pageTitle, LinearLayout.LayoutParams(0, -2, 1f))
        val refresh = Button(this).apply {
            text = "↻"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(25, 32, 45))
            setOnClickListener { webView?.reload() }
        }
        toolbar.addView(refresh, LinearLayout.LayoutParams(dp(44), dp(42)))
        root.addView(toolbar, LinearLayout.LayoutParams(-1, dp(44)))

        progress = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100
            progressTintList = android.content.res.ColorStateList.valueOf(Color.rgb(190, 75, 255))
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30, 38, 48))
        }
        root.addView(progress, LinearLayout.LayoutParams(-1, dp(3)))

        val browser = WebView(this)
        webView = browser
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.loadsImagesAutomatically = true
        browser.settings.useWideViewPort = true
        browser.settings.loadWithOverviewMode = true
        browser.settings.javaScriptCanOpenWindowsAutomatically = true
        browser.settings.setSupportMultipleWindows(false)
        browser.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progress.progress = newProgress
                progress.visibility = if (newProgress >= 100) android.view.View.GONE else android.view.View.VISIBLE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                pageTitle.text = title?.take(28)?.ifBlank { "HENTAIREAD" } ?: "HENTAIREAD"
            }
        }
        browser.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean = false
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(root)
        browser.loadUrl("https://hentairead.com/")
    }

    override fun onBackPressed() {
        val browser = webView
        if (browser?.canGoBack() == true) browser.goBack() else finish()
    }

    override fun onDestroy() {
        webView?.apply {
            stopLoading()
            webChromeClient = null
            webViewClient = WebViewClient()
            destroy()
        }
        webView = null
        super.onDestroy()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
